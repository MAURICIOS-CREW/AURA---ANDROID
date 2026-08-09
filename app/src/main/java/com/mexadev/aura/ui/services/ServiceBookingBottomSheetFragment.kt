package com.mexadev.aura.ui.services

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mexadev.aura.BuildConfig
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.data.model.ContractedService
import com.mexadev.aura.data.model.PaymentProcessItemRequest
import com.mexadev.aura.data.model.Service
import com.mexadev.aura.data.model.ServiceContractRequest
import com.mexadev.aura.data.model.StripeCreateIntentRequest
import com.mexadev.aura.databinding.FragmentServiceBookingBottomSheetBinding
import com.mexadev.aura.ui.common.BannerManager
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ServiceBookingBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentServiceBookingBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var service: Service? = null
    private var onServiceContracted: ((ContractedService) -> Unit)? = null

    // State in memory
    private val calendar: Calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
    private var selectedDateStr: String = "" // YYYY-MM-DD
    private var isRecurrent: Boolean = false
    private val selectedDays = mutableSetOf<String>()
    private var selectedPaymentMethod: String = "stripe"
    private var receiptUploaded: Boolean = false

    private var pendingStripeIntentId: String? = null
    private var pendingResidenceId: Long = 1L
    private lateinit var paymentSheet: PaymentSheet

    private lateinit var sheetBannerManager: BannerManager

    private val timeOptions = listOf(
        "06:00", "07:00", "08:00", "09:00", "10:00", "11:00", "12:00",
        "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00"
    )

    companion object {
        private const val ARG_SERVICE_JSON = "arg_service_json"

        fun newInstance(service: Service, onContracted: (ContractedService) -> Unit): ServiceBookingBottomSheetFragment {
            val fragment = ServiceBookingBottomSheetFragment()
            val args = Bundle()
            args.putString(ARG_SERVICE_JSON, Gson().toJson(service))
            fragment.arguments = args
            fragment.onServiceContracted = onContracted
            return fragment
        }
    }

    override fun getTheme(): Int {
        return com.google.android.material.R.style.Theme_Design_BottomSheetDialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pubKey = BuildConfig.STRIPE_PUBLISHABLE_KEY.ifEmpty { "pk_test_51Pxxx" }
        PaymentConfiguration.init(requireContext().applicationContext, pubKey)
        paymentSheet = PaymentSheet(this, ::onPaymentSheetResult)

        val json = arguments?.getString(ARG_SERVICE_JSON)
        if (json != null) {
            try {
                service = Gson().fromJson(json, Service::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        dialog?.window?.setDimAmount(0.55f)
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            sheet.setBackgroundResource(android.R.color.transparent)
            val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet)
            behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServiceBookingBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sheetBannerManager = BannerManager(binding.sheetBanner, binding.tvSheetBannerMessage)

        setupStep1Data()
        setupModalityControl()
        setupDayChips()
        setupStep1TimeSpinners()
        setupStep1DatePicker()
        setupStep1NextButton()
        setupStep2PaymentTabs()
        setupStep2TransferCopyAndUpload()
        setupStep2ConfirmButton()
        setupStep3FinishButton()
    }

    private fun setupStep1Data() {
        val s = service ?: return
        val theme = ServiceIconHelper.getCategoryTheme(s.title, s.description)

        binding.ivStep1Icon.setImageResource(theme.iconRes)
        binding.ivStep1Icon.setColorFilter(ContextCompat.getColor(requireContext(), theme.iconColorRes))
        binding.flServiceIcon.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), theme.bgColorRes))

        binding.tvStep1Title.text = s.title
        binding.tvStep1Category.text = theme.categoryName
        binding.tvStep1Price.text = getString(R.string.services_price_amount_format, s.price)
        binding.tvStep1Desc.text = s.description ?: getString(R.string.coming_soon_description, s.title)

        updateDateDisplay()
    }

    private fun setupModalityControl() {
        binding.tabModalitySingle.setOnClickListener {
            isRecurrent = false
            binding.tabModalitySingle.setBackgroundResource(R.drawable.bg_button_tab_active)
            binding.tabModalitySingle.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_white))
            binding.tabModalitySingle.typeface = Typeface.DEFAULT_BOLD

            binding.tabModalityRecurrent.background = null
            binding.tabModalityRecurrent.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_text_primary))
            binding.tabModalityRecurrent.typeface = Typeface.DEFAULT

            binding.layoutDaysSelection.apply {
                animate().alpha(0f).translationY(-8f).setDuration(200)
                    .withEndAction { visibility = View.GONE; translationY = 0f }
                    .start()
            }
            updateDateDisplay()
        }

        binding.tabModalityRecurrent.setOnClickListener {
            isRecurrent = true
            binding.tabModalityRecurrent.setBackgroundResource(R.drawable.bg_button_tab_active)
            binding.tabModalityRecurrent.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_white))
            binding.tabModalityRecurrent.typeface = Typeface.DEFAULT_BOLD

            binding.tabModalitySingle.background = null
            binding.tabModalitySingle.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_text_primary))
            binding.tabModalitySingle.typeface = Typeface.DEFAULT

            binding.layoutDaysSelection.apply {
                alpha = 0f
                translationY = -12f
                visibility = View.VISIBLE
                animate().alpha(1f).translationY(0f).setDuration(280)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
            }
            updateDateDisplay()
        }
    }

    private fun setupDayChips() {
        val daysMap = mapOf(
            binding.dayMonday to "Lunes",
            binding.dayTuesday to "Martes",
            binding.dayWednesday to "Miércoles",
            binding.dayThursday to "Jueves",
            binding.dayFriday to "Viernes",
            binding.daySaturday to "Sábado",
            binding.daySunday to "Domingo"
        )

        daysMap.forEach { (view: TextView, dayName: String) ->
            view.setOnClickListener {
                if (selectedDays.contains(dayName)) {
                    selectedDays.remove(dayName)
                    view.isActivated = false
                    view.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_text_secondary))
                    view.typeface = Typeface.DEFAULT
                } else {
                    selectedDays.add(dayName)
                    view.isActivated = true
                    view.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_white))
                    view.typeface = Typeface.DEFAULT_BOLD
                    view.animate()
                        .scaleX(1.15f).scaleY(1.15f).setDuration(120)
                        .withEndAction {
                            view.animate().scaleX(1f).scaleY(1f).setDuration(150)
                                .setInterpolator(OvershootInterpolator(2.5f))
                                .start()
                        }.start()
                }
            }
        }
    }

    private fun updateDateDisplay() {
        val localeMx = Locale.forLanguageTag("es-MX")
        val apiFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormat = SimpleDateFormat("EEEE, dd 'de' MMMM, yyyy", localeMx)
        
        selectedDateStr = apiFormat.format(calendar.time)
        val rawDate = displayFormat.format(calendar.time)
        val formattedNice = rawDate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(localeMx) else it.toString() }
        
        val stringRes = if (isRecurrent) R.string.services_booking_start_date else R.string.services_booking_selected_date
        binding.tvSelectedDate.text = getString(stringRes, formattedNice)
    }

    private fun setupStep1DatePicker() {
        binding.btnSelectDate.setOnClickListener {
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    updateDateDisplay()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePicker.datePicker.minDate = System.currentTimeMillis() - 1000
            datePicker.show()
        }
    }

    private fun setupStep1TimeSpinners() {
        val adapterFrom = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, timeOptions)
        val adapterTo = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, timeOptions)

        binding.spTimeFrom.adapter = adapterFrom
        binding.spTimeTo.adapter = adapterTo

        binding.spTimeFrom.setSelection(timeOptions.indexOf("09:00").coerceAtLeast(0))
        binding.spTimeTo.setSelection(timeOptions.indexOf("13:00").coerceAtLeast(0))
    }

    private fun setupStep1NextButton() {
        binding.btnStep1Next.setOnClickListener {
            val fromTime = binding.spTimeFrom.selectedItem as String
            val toTime = binding.spTimeTo.selectedItem as String

            val fromIndex = timeOptions.indexOf(fromTime)
            val toIndex = timeOptions.indexOf(toTime)

            if (toIndex <= fromIndex) {
                sheetBannerManager.show(getString(R.string.services_booking_invalid_time))
                return@setOnClickListener
            }

            if (isRecurrent && selectedDays.isEmpty()) {
                sheetBannerManager.show(getString(R.string.services_booking_select_days_error))
                return@setOnClickListener
            }

            sheetBannerManager.hide()
            slideNext()
        }
    }

    private fun setupStep2PaymentTabs() {
        binding.btnStep2Back.setOnClickListener {
            slidePrevious()
        }

        binding.tvStep2Amount.text = getString(R.string.services_price_amount_short, service?.price ?: "0.00")

        binding.tabStripe.setOnClickListener {
            selectedPaymentMethod = "stripe"
            binding.tabStripe.setBackgroundResource(R.drawable.bg_button_tab_active)
            binding.tabStripe.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_white))
            binding.tabStripe.typeface = Typeface.DEFAULT_BOLD

            binding.tabTransfer.background = null
            binding.tabTransfer.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_text_primary))
            binding.tabTransfer.typeface = Typeface.DEFAULT

            binding.layoutStripeForm.visibility = View.VISIBLE
            binding.layoutTransferForm.visibility = View.GONE
        }

        binding.tabTransfer.setOnClickListener {
            selectedPaymentMethod = "transfer"
            binding.tabTransfer.setBackgroundResource(R.drawable.bg_button_tab_active)
            binding.tabTransfer.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_white))
            binding.tabTransfer.typeface = Typeface.DEFAULT_BOLD

            binding.tabStripe.background = null
            binding.tabStripe.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_text_primary))
            binding.tabStripe.typeface = Typeface.DEFAULT

            binding.layoutTransferForm.visibility = View.VISIBLE
            binding.layoutStripeForm.visibility = View.GONE
        }
    }

    private fun setupStep2TransferCopyAndUpload() {
        binding.btnCopyClabe.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("CLABE AURA", "012180001234567890")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(requireContext(), R.string.services_booking_clabe_copied, Toast.LENGTH_SHORT).show()
        }

        binding.btnUploadReceipt.setOnClickListener {
            receiptUploaded = true
            binding.ivUploadIcon.setImageResource(R.drawable.ic_check)
            binding.ivUploadIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.aura_success))
            binding.tvUploadStatus.text = getString(R.string.services_booking_receipt_uploaded)
            binding.tvUploadStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_success))
            Toast.makeText(requireContext(), R.string.services_booking_receipt_uploaded, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupStep2ConfirmButton() {
        binding.btnConfirmContract.setOnClickListener {
            executeContractService()
        }
    }

    private fun executeContractService() {
        if (service == null) return
        sheetBannerManager.hide()

        if (selectedPaymentMethod == "stripe") {
            val cardParams = binding.stripeCardInputWidget.cardParams
            if (cardParams != null) {
                // Direct confirmation via PaymentMethodId (pm_xxx)
                val params = binding.stripeCardInputWidget.paymentMethodCreateParams
                if (params != null) {
                    executeDirectStripeContracting(params)
                } else {
                    executeStripeContractingSheetFlow()
                }
            } else {
                // PaymentSheet flow (Recommended by Backend: POST /create-intent)
                executeStripeContractingSheetFlow()
            }
        } else {
            // Transfer / Cash flow
            confirmContractOnBackend()
        }
    }

    private fun executeStripeContractingSheetFlow() {
        val s = service ?: return
        setContractLoadingState(true)

        lifecycleScope.launch {
            try {
                android.util.Log.d("AURA_STRIPE", "Executing Service Stripe PaymentSheet flow for service ID: ${s.id}")
                val itemReq = PaymentProcessItemRequest(type = "contracted_service", id = s.id)
                val intentRequest = StripeCreateIntentRequest(items = listOf(itemReq))

                val response = ApiClient.apiService.createStripeIntent(intentRequest)
                android.util.Log.d("AURA_STRIPE", "Create Intent Service HTTP Code: ${response.code()}, body: ${response.body()}")

                if (response.isSuccessful && response.body()?.status == "success") {
                    val intentData = response.body()?.data
                    if (intentData != null && intentData.clientSecret.isNotEmpty()) {
                        val pubKey = intentData.publishableKey.takeIf { !it.isNullOrEmpty() }
                            ?: BuildConfig.STRIPE_PUBLISHABLE_KEY.ifEmpty { "pk_test_51Pxxx" }
                        
                        if (pubKey.isNotEmpty()) {
                            PaymentConfiguration.init(requireContext().applicationContext, pubKey)
                        }

                        pendingStripeIntentId = intentData.paymentIntentId

                        val configuration = PaymentSheet.Configuration(
                            merchantDisplayName = "AURA Residencial"
                        )

                        setContractLoadingState(false)
                        android.util.Log.d("AURA_STRIPE", "Presenting Service PaymentSheet with clientSecret: ${intentData.clientSecret}")
                        paymentSheet.presentWithPaymentIntent(intentData.clientSecret, configuration)
                    } else {
                        setContractLoadingState(false)
                        sheetBannerManager.show(getString(R.string.services_error_contract_failed))
                    }
                } else {
                    setContractLoadingState(false)
                    val errorMsg = parseErrorMessage(
                        response.errorBody()?.string(),
                        response.body()?.message ?: getString(R.string.services_error_contract_failed)
                    )
                    sheetBannerManager.show(errorMsg)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("AURA_STRIPE", "Exception in executeStripeContractingSheetFlow", e)
                setContractLoadingState(false)
                sheetBannerManager.show(e.localizedMessage ?: getString(R.string.services_error_no_connection))
            }
        }
    }

    private fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        when (paymentSheetResult) {
            is PaymentSheetResult.Completed -> {
                val intentId = pendingStripeIntentId
                if (intentId != null) {
                    confirmContractOnBackend(stripePaymentIntentId = intentId)
                } else {
                    setContractLoadingState(false)
                    sheetBannerManager.show(getString(R.string.services_error_contract_failed))
                }
            }
            is PaymentSheetResult.Canceled -> {
                setContractLoadingState(false)
            }
            is PaymentSheetResult.Failed -> {
                setContractLoadingState(false)
                val errorMsg = paymentSheetResult.error.localizedMessage
                    ?: getString(R.string.services_error_contract_failed)
                sheetBannerManager.show(errorMsg)
            }
        }
    }

    private fun executeDirectStripeContracting(params: PaymentMethodCreateParams) {
        setContractLoadingState(true)
        val pubKey = BuildConfig.STRIPE_PUBLISHABLE_KEY.ifEmpty { "pk_test_51Pxxx" }
        PaymentConfiguration.init(requireContext().applicationContext, pubKey)

        val stripe = Stripe(requireContext().applicationContext, pubKey)

        stripe.createPaymentMethod(params, callback = object : ApiResultCallback<PaymentMethod> {
            override fun onSuccess(result: PaymentMethod) {
                val pmId = result.id
                if (pmId != null) {
                    confirmContractOnBackend(paymentMethodId = pmId)
                } else {
                    setContractLoadingState(false)
                    sheetBannerManager.show(getString(R.string.services_error_contract_failed))
                }
            }

            override fun onError(e: Exception) {
                setContractLoadingState(false)
                sheetBannerManager.show(e.localizedMessage ?: getString(R.string.services_error_contract_failed))
            }
        })
    }

    private fun confirmContractOnBackend(
        stripePaymentIntentId: String? = null,
        paymentMethodId: String? = null
    ) {
        val s = service ?: return
        val fromTime = binding.spTimeFrom.selectedItem as String
        val toTime = binding.spTimeTo.selectedItem as String
        val notes = binding.etNotes.text.toString().trim().ifEmpty { null }

        setContractLoadingState(true)

        lifecycleScope.launch {
            try {
                val userResponse = ApiClient.apiService.getProfile()
                val residenceId = userResponse.body()?.residences?.firstOrNull()?.id ?: pendingResidenceId

                val request = ServiceContractRequest(
                    residenceId = residenceId,
                    preferredDate = selectedDateStr,
                    visitTimeFrom = fromTime,
                    visitTimeTo = toTime,
                    isRecurrent = isRecurrent,
                    suggestedSchedule = if (isRecurrent) selectedDays.toList() else null,
                    notes = notes,
                    paymentMethod = selectedPaymentMethod,
                    stripePaymentIntentId = stripePaymentIntentId,
                    paymentMethodId = paymentMethodId
                )

                val response = ApiClient.apiService.contractService(s.id, request)
                setContractLoadingState(false)

                if (response.isSuccessful && response.body()?.status == "success") {
                    val contracted = response.body()?.data
                    if (contracted != null) {
                        onServiceContracted?.invoke(contracted)
                        setupStep3Confirmation(fromTime, toTime)
                        slideNext()
                        animateStep3Check()
                    } else {
                        sheetBannerManager.show(getString(R.string.services_error_contract_failed))
                    }
                } else {
                    val errorMsg = parseErrorMessage(
                        response.errorBody()?.string(),
                        response.body()?.message ?: getString(R.string.services_error_contract_failed)
                    )
                    sheetBannerManager.show(errorMsg)
                }

            } catch (_: Exception) {
                setContractLoadingState(false)
                sheetBannerManager.show(getString(R.string.services_error_no_connection))
            }
        }
    }

    private fun parseErrorMessage(errorBodyJson: String?, defaultMessage: String): String {
        if (errorBodyJson.isNullOrEmpty()) return defaultMessage
        return try {
            val json = JsonParser.parseString(errorBodyJson).asJsonObject
            when {
                json.has("failure_reason") && !json.get("failure_reason").isJsonNull -> json.get("failure_reason").asString
                json.has("message") && !json.get("message").isJsonNull -> json.get("message").asString
                else -> defaultMessage
            }
        } catch (_: Exception) {
            defaultMessage
        }
    }

    private fun setContractLoadingState(isLoading: Boolean) {
        if (isLoading) {
            binding.btnConfirmContract.isEnabled = false
            binding.btnConfirmContract.text = ""
            binding.pbContractLoading.visibility = View.VISIBLE
        } else {
            binding.btnConfirmContract.isEnabled = true
            binding.btnConfirmContract.text = getString(R.string.services_booking_btn_confirm)
            binding.pbContractLoading.visibility = View.GONE
        }
    }

    private fun setupStep3Confirmation(fromTime: String, toTime: String) {
        val sName = service?.title ?: getString(R.string.services_title)
        binding.tvSummaryServiceTitle.text = sName
        binding.tvSummaryDateTime.text = getString(R.string.services_suggested_schedule, selectedDateStr, fromTime, toTime)
        
        val modalityText = if (isRecurrent) {
            "${getString(R.string.services_booking_modality_recurrent)} (${selectedDays.joinToString(", ")})"
        } else {
            getString(R.string.services_booking_modality_single)
        }
        binding.tvSummaryModality.text = getString(R.string.services_booking_summary_modality, modalityText)

        val payMethodName = if (selectedPaymentMethod == "stripe") {
            getString(R.string.services_booking_payment_stripe_name)
        } else {
            getString(R.string.services_booking_payment_transfer_name)
        }
        binding.tvSummaryPaymentMethod.text = getString(R.string.services_booking_summary_payment, payMethodName)
        
        val statusStr = getString(R.string.services_status_pending)
        binding.tvSummaryStatus.text = getString(R.string.services_booking_summary_status, statusStr)
    }

    private fun animateStep3Check() {
        val scaleX = ObjectAnimator.ofFloat(binding.flSuccessIcon, "scaleX", 0f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.flSuccessIcon, "scaleY", 0f, 1.2f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 500
            interpolator = OvershootInterpolator(2.0f)
            start()
        }
    }

    private fun setupStep3FinishButton() {
        binding.btnStep3Finish.setOnClickListener {
            dismiss()
        }
    }

    private fun slideNext() {
        binding.viewFlipper.setInAnimation(requireContext(), R.anim.slide_in_right)
        binding.viewFlipper.setOutAnimation(requireContext(), R.anim.slide_out_left)
        binding.viewFlipper.showNext()
    }

    private fun slidePrevious() {
        binding.viewFlipper.setInAnimation(requireContext(), R.anim.slide_in_left)
        binding.viewFlipper.setOutAnimation(requireContext(), R.anim.slide_out_right)
        binding.viewFlipper.showPrevious()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sheetBannerManager.destroy()
        _binding = null
    }
}
