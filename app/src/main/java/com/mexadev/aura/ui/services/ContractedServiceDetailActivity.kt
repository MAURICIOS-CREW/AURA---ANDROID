package com.mexadev.aura.ui.services

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.data.model.ContractedService
import com.mexadev.aura.databinding.ActivityContractedServiceDetailBinding
import com.mexadev.aura.ui.common.BannerManager
import com.mexadev.aura.ui.common.ConfirmBottomSheetFragment
import kotlinx.coroutines.launch
import java.util.Locale

class ContractedServiceDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONTRACTED_SERVICE_ID = "extra_contracted_service_id"
        const val EXTRA_CONTRACTED_SERVICE_JSON = "extra_contracted_service_json"
        const val EXTRA_TRANSITION_NAME = "extra_transition_name"
        const val EXTRA_TRANSITION_ICON_NAME = "extra_transition_icon_name"
        const val EXTRA_TRANSITION_TITLE_NAME = "extra_transition_title_name"
        const val EXTRA_TRANSITION_STATUS_NAME = "extra_transition_status_name"
        const val DEFAULT_TRANSITION_NAME = "transition_contracted_service_default"
    }

    private lateinit var binding: ActivityContractedServiceDetailBinding
    private var serviceId: Long = -1L
    private var currentService: ContractedService? = null

    private lateinit var errorBanner: BannerManager
    private lateinit var successBanner: BannerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        binding = ActivityContractedServiceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val iconName = intent.getStringExtra(EXTRA_TRANSITION_ICON_NAME)
        val titleName = intent.getStringExtra(EXTRA_TRANSITION_TITLE_NAME)
        val statusName = intent.getStringExtra(EXTRA_TRANSITION_STATUS_NAME)
        val legacyName = intent.getStringExtra(EXTRA_TRANSITION_NAME) ?: DEFAULT_TRANSITION_NAME

        if (iconName != null && titleName != null && statusName != null) {
            binding.cardHero.transitionName = legacyName
            binding.flIconContainer.transitionName = iconName
            binding.tvTitle.transitionName = titleName
            binding.tvStatus.transitionName = statusName

            val transitionSet = android.transition.TransitionSet().apply {
                addTransition(android.transition.ChangeBounds())
                addTransition(android.transition.ChangeTransform())
                addTransition(android.transition.ChangeImageTransform())
                duration = 350
                interpolator = android.view.animation.AnimationUtils.loadInterpolator(
                    this@ContractedServiceDetailActivity, android.R.interpolator.fast_out_slow_in
                )
            }
            window.sharedElementEnterTransition = transitionSet
            window.sharedElementReturnTransition = transitionSet
        } else {
            binding.detailRoot.transitionName = legacyName
            window.sharedElementEnterTransition = MaterialContainerTransform().apply {
                addTarget(legacyName)
                duration = 400
                isElevationShadowEnabled = false
                interpolator = android.view.animation.AnimationUtils.loadInterpolator(
                    this@ContractedServiceDetailActivity, android.R.interpolator.fast_out_slow_in
                )
                fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
                excludeTarget(android.R.id.statusBarBackground, true)
                excludeTarget(android.R.id.navigationBarBackground, true)
            }
            window.sharedElementReturnTransition = MaterialContainerTransform().apply {
                addTarget(legacyName)
                duration = 350
                isElevationShadowEnabled = false
                interpolator = android.view.animation.AnimationUtils.loadInterpolator(
                    this@ContractedServiceDetailActivity, android.R.interpolator.fast_out_slow_in
                )
                fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
                excludeTarget(android.R.id.statusBarBackground, true)
                excludeTarget(android.R.id.navigationBarBackground, true)
            }
        }

        postponeEnterTransition()
        binding.detailRoot.post { startPostponedEnterTransition() }

        errorBanner = BannerManager(binding.errorBanner, binding.tvErrorBannerMessage)
        successBanner = BannerManager(binding.successBanner, binding.tvSuccessBannerMessage)

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        setupEdgeToEdge()
        setupListeners()

        val json = intent.getStringExtra(EXTRA_CONTRACTED_SERVICE_JSON)
        if (json != null) {
            try {
                val service = Gson().fromJson(json, ContractedService::class.java)
                serviceId = service.id
                currentService = service
                populateData(service)
                fetchDetail(silent = true)
            } catch (_: Exception) {
                fetchDetail(silent = false)
            }
        } else {
            serviceId = intent.getLongExtra(EXTRA_CONTRACTED_SERVICE_ID, -1L)
            if (serviceId == -1L) {
                finish()
                return
            }
            fetchDetail(silent = false)
        }
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                systemBars.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
            )

            binding.scrollView.setPadding(
                binding.scrollView.paddingLeft,
                binding.scrollView.paddingTop,
                binding.scrollView.paddingRight,
                systemBars.bottom + 24
            )

            binding.errorBanner.setPadding(16, systemBars.top + 16, 16, 16)
            binding.successBanner.setPadding(16, systemBars.top + 16, 16, 16)

            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finishWithResult() }

        binding.btnComplete.setOnClickListener {
            val service = currentService ?: return@setOnClickListener
            ConfirmBottomSheetFragment.newInstance(
                title = getString(R.string.services_detail_complete_confirm_title),
                message = getString(R.string.services_detail_complete_confirm_msg),
                confirmText = getString(R.string.services_btn_complete),
                iconRes = R.drawable.ic_check,
                onConfirm = { completeService(service.id) }
            ).show(supportFragmentManager, "CompleteConfirm")
        }
    }

    private fun fetchDetail(silent: Boolean = false) {
        if (!silent) setSkeletonLoading(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getContractedServices()
                if (response.isSuccessful && response.body()?.status == "success") {
                    val list = response.body()?.data ?: emptyList()
                    val item = list.find { it.id == serviceId }
                    if (item != null) {
                        currentService = item
                        populateData(item)
                    } else if (!silent) {
                        errorBanner.show(getString(R.string.services_error_fetch))
                    }
                } else if (!silent) {
                    errorBanner.show(getString(R.string.services_error_fetch))
                }
            } catch (_: Exception) {
                if (!silent) {
                    errorBanner.show(getString(R.string.services_error_no_connection))
                }
            } finally {
                if (!silent) setSkeletonLoading(false)
            }
        }
    }

    private fun populateData(item: ContractedService) {
        val sTitle = item.service?.title ?: getString(R.string.services_title)
        val theme = ServiceIconHelper.getCategoryTheme(sTitle)

        binding.tvTitle.text = sTitle
        binding.tvCategory.text = item.service?.description ?: theme.categoryName
        binding.ivServiceIcon.setImageResource(theme.iconRes)
        binding.ivServiceIcon.setColorFilter(ContextCompat.getColor(this, theme.iconColorRes))
        binding.flIconContainer.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, theme.bgColorRes))

        // Status Badge & Toolbar Status
        when (item.status.lowercase(Locale.getDefault())) {
            "completed" -> {
                val text = getString(R.string.services_status_completed)
                binding.tvStatus.text = text
                binding.tvToolbarStatus.text = text
                binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_success)
                binding.tvToolbarStatus.setBackgroundResource(R.drawable.bg_pill_success)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.aura_success))
                binding.tvToolbarStatus.setTextColor(ContextCompat.getColor(this, R.color.aura_success))

                binding.btnCompleteContainer.visibility = View.GONE
            }
            "scheduled" -> {
                val text = getString(R.string.services_status_scheduled)
                binding.tvStatus.text = text
                binding.tvToolbarStatus.text = text
                binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_info)
                binding.tvToolbarStatus.setBackgroundResource(R.drawable.bg_pill_info)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.aura_primary))
                binding.tvToolbarStatus.setTextColor(ContextCompat.getColor(this, R.color.aura_primary))

                binding.btnCompleteContainer.visibility = View.VISIBLE
            }
            "cancelled" -> {
                val text = getString(R.string.services_status_cancelled)
                binding.tvStatus.text = text
                binding.tvToolbarStatus.text = text
                binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_error)
                binding.tvToolbarStatus.setBackgroundResource(R.drawable.bg_pill_error)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.aura_error))
                binding.tvToolbarStatus.setTextColor(ContextCompat.getColor(this, R.color.aura_error))

                binding.btnCompleteContainer.visibility = View.GONE
            }
            else -> { // pending / created
                val text = getString(R.string.services_status_pending)
                binding.tvStatus.text = text
                binding.tvToolbarStatus.text = text
                binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_warning)
                binding.tvToolbarStatus.setBackgroundResource(R.drawable.bg_pill_warning)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.aura_warning))
                binding.tvToolbarStatus.setTextColor(ContextCompat.getColor(this, R.color.aura_warning))

                binding.btnCompleteContainer.visibility = View.VISIBLE
            }
        }

        // Suggested Dates
        val formattedPrefDate = formatDateFormatted(item.preferredDate)
        val fromTime = formatTimeFormatted(item.visitTimeFrom)
        val toTime = formatTimeFormatted(item.visitTimeTo)

        if (formattedPrefDate.isNotBlank()) {
            if (fromTime.isNotBlank() && toTime.isNotBlank()) {
                binding.tvDateTime.text = getString(R.string.services_detail_schedule_range, formattedPrefDate, fromTime, toTime)
            } else if (fromTime.isNotBlank()) {
                binding.tvDateTime.text = getString(R.string.services_detail_schedule_single, formattedPrefDate, fromTime)
            } else {
                binding.tvDateTime.text = formattedPrefDate
            }
        } else {
            val rawDate = item.preferredDate ?: ""
            val rawFrom = item.visitTimeFrom?.take(5) ?: ""
            val rawTo = item.visitTimeTo?.take(5) ?: ""
            binding.tvDateTime.text = getString(R.string.services_suggested_schedule, rawDate, rawFrom, rawTo)
        }

        // Exact Confirmed Schedule
        if (!item.exactScheduledAt.isNullOrBlank()) {
            binding.tvExactScheduled.text = formatExactScheduled(item.exactScheduledAt)
            binding.cardExactScheduled.visibility = View.VISIBLE
        } else {
            binding.cardExactScheduled.visibility = View.GONE
        }

        // Access Code QR
        val accessCode = item.accessCode?.code
        if (!accessCode.isNullOrBlank()) {
            generateAccessQr(accessCode)
            binding.tvAccessCodeInfo.text = getString(R.string.services_detail_access_code_active)
            binding.cardAccessCode.visibility = View.VISIBLE
        } else {
            binding.cardAccessCode.visibility = View.GONE
        }

        // Price & Payment Method
        val priceVal = item.amount ?: item.service?.price ?: "0.00"
        binding.tvPrice.text = getString(R.string.services_price_amount_format, priceVal)
        val pMethod = when (item.paymentMethod?.lowercase(Locale.getDefault())) {
            "transfer", "bank_transfer" -> getString(R.string.services_booking_payment_transfer_name)
            else -> getString(R.string.services_booking_payment_stripe_name)
        }
        binding.tvPaymentMethod.text = pMethod

        // Notes
        if (!item.notes.isNullOrBlank()) {
            binding.tvNotes.text = item.notes
            binding.cardNotes.visibility = View.VISIBLE
        } else {
            binding.cardNotes.visibility = View.GONE
        }

        // Description
        if (!item.service?.description.isNullOrBlank()) {
            binding.tvDescription.text = item.service.description
            binding.cardDescription.visibility = View.VISIBLE
        } else {
            binding.cardDescription.visibility = View.GONE
        }
    }

    private fun completeService(id: Long) {
        setCompleteLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.completeContractedService(id)
                if (response.isSuccessful && response.body()?.status == "success") {
                    val updated = currentService?.copy(status = "completed") ?: response.body()?.data
                    if (updated != null) {
                        currentService = updated
                        populateData(updated)
                    }
                    successBanner.show(response.body()?.message ?: getString(R.string.services_complete_success))
                } else {
                    errorBanner.show(getString(R.string.services_error_contract_failed))
                }
            } catch (_: Exception) {
                errorBanner.show(getString(R.string.services_error_no_connection))
            } finally {
                setCompleteLoading(false)
            }
        }
    }

    private fun setCompleteLoading(loading: Boolean) {
        if (loading) {
            binding.btnComplete.isEnabled = false
            binding.btnComplete.text = ""
            binding.pbCompleteLoading.visibility = View.VISIBLE
        } else {
            binding.btnComplete.isEnabled = true
            binding.btnComplete.text = getString(R.string.services_btn_complete)
            binding.pbCompleteLoading.visibility = View.GONE
        }
    }

    private fun setSkeletonLoading(loading: Boolean) {
        if (loading) {
            binding.scrollView.visibility = View.GONE
            binding.layoutDetailSkeleton.root.visibility = View.VISIBLE
        } else {
            binding.layoutDetailSkeleton.root.visibility = View.GONE
            binding.scrollView.visibility = View.VISIBLE
        }
    }

    private fun finishWithResult() {
        currentService?.let {
            val intent = Intent().apply {
                putExtra(EXTRA_CONTRACTED_SERVICE_JSON, Gson().toJson(it))
            }
            setResult(RESULT_OK, intent)
        }
        finishAfterTransition()
    }

    private fun formatDateFormatted(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        return try {
            val clean = dateStr.split("T")[0]
            val localDate = java.time.LocalDate.parse(clean)
            val esLocale = Locale.forLanguageTag("es-ES")
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd 'de' MMMM, yyyy", esLocale)
            localDate.format(formatter).replaceFirstChar { if (it.isLowerCase()) it.titlecase(esLocale) else it.toString() }
        } catch (_: Exception) {
            dateStr
        }
    }

    private fun formatTimeFormatted(timeStr: String?): String {
        if (timeStr.isNullOrBlank()) return ""
        return try {
            val clean = timeStr.take(5)
            val localTime = java.time.LocalTime.parse(clean)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())
            localTime.format(formatter).uppercase(Locale.getDefault())
        } catch (_: Exception) {
            timeStr
        }
    }

    private fun formatExactScheduled(utcStr: String?): String {
        if (utcStr.isNullOrBlank()) return ""
        return try {
            val clean = utcStr.replace(" ", "T")
            val instant = if (clean.endsWith("Z")) {
                java.time.Instant.parse(clean)
            } else {
                java.time.LocalDateTime.parse(clean).atZone(java.time.ZoneId.of("UTC")).toInstant()
            }
            val zdt = instant.atZone(java.time.ZoneId.systemDefault())
            val esLocale = Locale.forLanguageTag("es-ES")
            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM '•' hh:mm a", esLocale)
            val formatted = zdt.format(formatter)
            formatted.replaceFirstChar { if (it.isLowerCase()) it.titlecase(esLocale) else it.toString() }
        } catch (_: Exception) {
            utcStr
        }
    }

    private fun generateAccessQr(content: String) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 400, 400)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap[x, y] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            binding.ivAccessQrCode.clearColorFilter()
            binding.ivAccessQrCode.setImageBitmap(bitmap)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        errorBanner.destroy()
        successBanner.destroy()
    }
}
