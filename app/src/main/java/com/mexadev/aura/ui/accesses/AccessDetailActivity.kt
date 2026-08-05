package com.mexadev.aura.ui.accesses

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.data.model.AccessCode
import com.mexadev.aura.data.model.AccessCodeCreateRequest
import com.mexadev.aura.data.model.AccessCodeUpdateRequest
import com.mexadev.aura.data.model.evaluateValidity
import com.mexadev.aura.databinding.ActivityAccessDetailBinding
import com.mexadev.aura.ui.common.ConfirmBottomSheetFragment
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

class AccessDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCESS_ID = "extra_access_id"
    }

    private lateinit var binding: ActivityAccessDetailBinding
    private var accessId: Long = -1L
    private var isEditMode = false
    private var currentAccess: AccessCode? = null
    
    private lateinit var errorBanner: com.mexadev.aura.ui.common.BannerManager
    private lateinit var successBanner: com.mexadev.aura.ui.common.BannerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must request FEATURE_ACTIVITY_TRANSITIONS BEFORE setContentView / super.onCreate
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
        // Register the shared element callback so the framework knows how to map elements
        setEnterSharedElementCallback(com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback())
        // Make the window background transparent during the transition so there is no
        // "flash" of the activity background before the container morphs into place
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        binding = ActivityAccessDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Assign the transition name to the root CoordinatorLayout so that
        // MaterialContainerTransform can find the matching element in this Activity.
        // Using the root view (not android.R.id.content) prevents the flicker caused
        // by the framework trying to animate the raw DecorView.
        val tName = intent.getStringExtra("extra_transition_name") ?: "shared_element_container"
        binding.detailRoot.transitionName = tName

        window.sharedElementEnterTransition = com.google.android.material.transition.platform.MaterialContainerTransform().apply {
            addTarget(tName)
            duration = 400
            isElevationShadowEnabled = false
            interpolator = android.view.animation.AnimationUtils.loadInterpolator(
                this@AccessDetailActivity, android.R.interpolator.fast_out_slow_in
            )
            fadeMode = com.google.android.material.transition.platform.MaterialContainerTransform.FADE_MODE_CROSS
            // Exclude the status and navigation bar backgrounds from the morph so they
            // don't get captured into the animated snapshot and cause a flicker
            excludeTarget(android.R.id.statusBarBackground, true)
            excludeTarget(android.R.id.navigationBarBackground, true)
        }
        window.sharedElementReturnTransition = com.google.android.material.transition.platform.MaterialContainerTransform().apply {
            addTarget(tName)
            duration = 350
            isElevationShadowEnabled = false
            interpolator = android.view.animation.AnimationUtils.loadInterpolator(
                this@AccessDetailActivity, android.R.interpolator.fast_out_slow_in
            )
            fadeMode = com.google.android.material.transition.platform.MaterialContainerTransform.FADE_MODE_CROSS
            excludeTarget(android.R.id.statusBarBackground, true)
            excludeTarget(android.R.id.navigationBarBackground, true)
        }

        // Postpone the enter transition until the layout is fully measured so that
        // the shared element dimensions are known before the animation starts.
        // This is the primary fix for the flicker on entry.
        postponeEnterTransition()
        binding.detailRoot.post { startPostponedEnterTransition() }

        errorBanner = com.mexadev.aura.ui.common.BannerManager(binding.errorBanner, binding.tvErrorBannerMessage)
        successBanner = com.mexadev.aura.ui.common.BannerManager(binding.successBanner, binding.tvSuccessBannerMessage)

        androidx.core.view.WindowCompat.getInsetsController(window, binding.root)
            .isAppearanceLightStatusBars = true

        accessId = intent.getLongExtra(EXTRA_ACCESS_ID, -1L)
        isEditMode = (accessId == -1L) // Start in edit mode if creating

        setupEdgeToEdge()
        setupListeners()
        setupInfoModals()
        updateUIMode()

        if (accessId != -1L) {
            fetchAccessCode()
        } else {
            // New Code
            binding.tvToolbarTitle.setText(R.string.access_detail_title_new)
            binding.tvQrStatus.visibility = View.GONE
            binding.ivQrCode.setImageResource(R.drawable.ic_lock)
            binding.ivQrCode.setColorFilter(getColor(R.color.aura_text_tertiary))
            binding.cardQrPreview.alpha = 0.5f
        }
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                systemBars.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
            )

            val bottomOffset = maxOf(ime.bottom, systemBars.bottom)
            binding.scrollView.setPadding(
                binding.scrollView.paddingLeft,
                binding.scrollView.paddingTop,
                binding.scrollView.paddingRight,
                bottomOffset + 100
            )

            binding.errorBanner.setPadding(
                16, systemBars.top + 16, 16, 16
            )
            binding.successBanner.setPadding(
                16, systemBars.top + 16, 16, 16
            )
            insets
        }
    }

    private fun animateLayoutChange() {
        val transition = android.transition.AutoTransition().apply {
            duration = 200
            interpolator = android.view.animation.AnimationUtils.loadInterpolator(this@AccessDetailActivity, android.R.interpolator.fast_out_slow_in)
        }
        android.transition.TransitionManager.beginDelayedTransition(binding.detailRoot, transition)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finishAfterTransition() }
        
        binding.switchUses.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) animateLayoutChange()
            binding.layoutUsesInput.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.switchDates.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) animateLayoutChange()
            binding.layoutDatesInput.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.switchDays.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) animateLayoutChange()
            binding.layoutDaysInput.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.switchHours.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) animateLayoutChange()
            binding.layoutHoursInput.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnEditMode.setOnClickListener {
            isEditMode = true
            updateUIMode()
        }

        binding.btnShare.setOnClickListener {
            shareQrCode()
        }

        binding.btnDelete.setOnClickListener {
            ConfirmBottomSheetFragment.newInstance(
                title = "Eliminar Código",
                message = "¿Estás seguro que deseas inhabilitar y eliminar este código permanentemente?",
                confirmText = "Eliminar",
                iconRes = R.drawable.ic_delete,
                onConfirm = { deleteAccessCode() }
            ).show(supportFragmentManager, "DeleteConfirm")
        }

        binding.fabSave.setOnClickListener {
            saveAccessCode()
        }

        // Time and Date pickers
        binding.tvStartTime.setOnClickListener { if (isEditMode) showTimePicker { time -> binding.tvStartTime.text = time } }
        binding.tvEndTime.setOnClickListener { if (isEditMode) showTimePicker { time -> binding.tvEndTime.text = time } }
        binding.tvValidFrom.setOnClickListener { if (isEditMode) showDatePicker { date -> binding.tvValidFrom.text = date } }
        binding.tvValidUntil.setOnClickListener { if (isEditMode) showDatePicker { date -> binding.tvValidUntil.text = date } }

        setupDayAnimations()
        applySwitchPhysics(binding.switchIsActive, binding.switchUses, binding.switchDates, binding.switchDays, binding.switchHours)
    }

    private fun setupDayAnimations() {
        val days = listOf(binding.btnDay1, binding.btnDay2, binding.btnDay3, binding.btnDay4, binding.btnDay5, binding.btnDay6, binding.btnDay7)
        val activeTextColor = ContextCompat.getColor(this, R.color.aura_white)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.aura_text_primary)
        
        days.forEach { btn ->
            btn.setTextColor(if (btn.isChecked) activeTextColor else inactiveTextColor)
            btn.setOnCheckedChangeListener { buttonView, isChecked ->
                buttonView.animate()
                    .scaleX(0.7f)
                    .scaleY(0.7f)
                    .setDuration(100)
                    .withEndAction {
                        buttonView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(400)
                            .setInterpolator(android.view.animation.OvershootInterpolator(4f))
                            .start()
                    }
                    .start()
                buttonView.setTextColor(if (isChecked) activeTextColor else inactiveTextColor)
            }
        }
    }

    private fun applySwitchPhysics(vararg switches: com.google.android.material.materialswitch.MaterialSwitch) {
        switches.forEach { switchView ->
            switchView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
                        v.performClick()
                    }
                }
                false
            }
        }
    }

    private fun setupInfoModals() {
        val showModal = { title: String, msg: String ->
            ConfirmBottomSheetFragment.newInstance(
                title = title,
                message = msg,
                confirmText = "Entendido",
                iconRes = R.drawable.ic_info,
                onConfirm = {}
            ).show(supportFragmentManager, "InfoModal")
        }
        binding.ivUsesInfo.setOnClickListener { showModal("Límite de usos", "Define cuántas veces puede ser escaneado este código antes de expirar. Si lo desactivas, será de uso ilimitado.") }
        binding.ivDatesInfo.setOnClickListener { showModal("Rango de Fechas", "Permite que el código solo funcione entre las fechas especificadas. Fuera de este rango, el acceso será denegado.") }
        binding.ivDaysInfo.setOnClickListener { showModal("Días Específicos", "Selecciona qué días de la semana (Lunes a Domingo) el código es válido.") }
        binding.ivHoursInfo.setOnClickListener { showModal("Horario de Acceso", "Limita las horas del día en que se permite el acceso. Útil para personal de servicio.") }
    }

    private fun updateUIMode() {
        val enabled = isEditMode
        
        binding.etGuestName.isEnabled = enabled
        binding.switchIsActive.isEnabled = enabled
        binding.switchUses.isEnabled = enabled
        binding.etMaxUses.isEnabled = enabled
        binding.switchDates.isEnabled = enabled
        binding.switchDays.isEnabled = enabled
        binding.switchHours.isEnabled = enabled
        
        val alphaVal = if (enabled) 1f else 0.5f
        binding.etGuestName.alpha = alphaVal
        binding.layoutIsActive.alpha = alphaVal
        binding.switchUses.alpha = alphaVal
        binding.etMaxUses.alpha = alphaVal
        binding.switchDates.alpha = alphaVal
        binding.tvValidFrom.alpha = alphaVal
        binding.tvValidUntil.alpha = alphaVal
        binding.switchDays.alpha = alphaVal
        binding.layoutDaysInput.alpha = alphaVal
        binding.switchHours.alpha = alphaVal
        binding.tvStartTime.alpha = alphaVal
        binding.tvEndTime.alpha = alphaVal

        // Toggles for days
        binding.btnDay1.isEnabled = enabled
        binding.btnDay2.isEnabled = enabled
        binding.btnDay3.isEnabled = enabled
        binding.btnDay4.isEnabled = enabled
        binding.btnDay5.isEnabled = enabled
        binding.btnDay6.isEnabled = enabled
        binding.btnDay7.isEnabled = enabled
        
        if (isEditMode) {
            binding.fabSave.show()
            binding.layoutActionButtons.visibility = View.GONE
            if (accessId != -1L) {
                binding.tvToolbarTitle.setText(R.string.access_detail_title_edit)
                binding.cardQrPreview.visibility = View.VISIBLE
                binding.layoutCreateBanner.visibility = View.GONE
            } else {
                binding.tvToolbarTitle.setText(R.string.access_detail_title_new)
                binding.cardQrPreview.visibility = View.GONE
                binding.layoutCreateBanner.visibility = View.VISIBLE
            }
        } else {
            binding.fabSave.hide()
            binding.layoutActionButtons.visibility = View.VISIBLE
            binding.tvToolbarTitle.setText(R.string.access_detail_title_view)
            binding.cardQrPreview.visibility = View.VISIBLE
            binding.layoutCreateBanner.visibility = View.GONE
        }
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            onTimeSelected(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        android.app.DatePickerDialog(this, { _, year, month, dayOfMonth ->
            onDateSelected(String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, dayOfMonth))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun fetchAccessCode() {
        setSkeletonLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getAccessCode(accessId)
                if (response.isSuccessful) {
                    val access = response.body()?.data
                    if (access != null) {
                        currentAccess = access
                        populateData(access)
                        generateQrImage(access.code)
                    } else {
                        errorBanner.show("Código no encontrado.")
                    }
                } else {
                    handleApiError(response.code(), response.errorBody()?.string())
                }
            } catch (e: Exception) {
                errorBanner.show("Sin conexión a internet o servidor inaccesible.")
            } finally {
                setSkeletonLoading(false)
            }
        }
    }

    private fun populateData(access: AccessCode) {
        // Code text removed per user request
        binding.cardQrPreview.alpha = 1f
        binding.btnDelete.visibility = View.VISIBLE

        val validity = access.evaluateValidity()

        binding.etGuestName.setText(access.guestName)
        binding.switchIsActive.isChecked = access.isActive
        
        if (access.isActive) {
            binding.tvQrStatus.setText(R.string.access_status_active)
            binding.tvQrStatus.setTextColor(ContextCompat.getColor(this, R.color.aura_success))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * resources.displayMetrics.density
                setColor(ContextCompat.getColor(this@AccessDetailActivity, R.color.aura_success_light))
            }
            binding.tvQrStatus.background = bg
        } else {
            binding.tvQrStatus.setText(R.string.access_status_inactive)
            binding.tvQrStatus.setTextColor(ContextCompat.getColor(this, R.color.aura_text_tertiary))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * resources.displayMetrics.density
                setColor(ContextCompat.getColor(this@AccessDetailActivity, R.color.aura_surface_variant))
            }
            binding.tvQrStatus.background = bg
        }

        // Render QR validity notice banner
        binding.tvNoticeText.text = validity.detailMessage
        binding.ivNoticeIcon.setImageResource(validity.iconRes)
        binding.ivNoticeIcon.setColorFilter(ContextCompat.getColor(this, validity.badgeTextColorRes))

        val noticeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12 * resources.displayMetrics.density
            setColor(ContextCompat.getColor(this@AccessDetailActivity, validity.badgeBgColorRes))
        }
        binding.layoutQrNotice.background = noticeBg

        // Dim QR image if not valid right now
        binding.ivQrCode.alpha = if (validity.isValidNow) 1.0f else 0.4f

        if (access.maxUses != null) {
            binding.switchUses.isChecked = true
            binding.etMaxUses.setText(access.maxUses.toString())
        } else {
            binding.switchUses.isChecked = false
            binding.etMaxUses.setText("")
        }

        if (access.validFrom != null || access.validUntil != null) {
            binding.switchDates.isChecked = true
            binding.tvValidFrom.text = access.validFrom ?: "Fecha Inicio"
            binding.tvValidUntil.text = access.validUntil ?: "Fecha Fin"
        } else {
            binding.switchDates.isChecked = false
        }

        if (!access.activeDays.isNullOrEmpty()) {
            binding.switchDays.isChecked = true
            binding.btnDay1.isChecked = access.activeDays.contains(1)
            binding.btnDay2.isChecked = access.activeDays.contains(2)
            binding.btnDay3.isChecked = access.activeDays.contains(3)
            binding.btnDay4.isChecked = access.activeDays.contains(4)
            binding.btnDay5.isChecked = access.activeDays.contains(5)
            binding.btnDay6.isChecked = access.activeDays.contains(6)
            binding.btnDay7.isChecked = access.activeDays.contains(7)
        } else {
            binding.switchDays.isChecked = false
            listOf(binding.btnDay1, binding.btnDay2, binding.btnDay3, binding.btnDay4, binding.btnDay5, binding.btnDay6, binding.btnDay7).forEach { it.isChecked = false }
        }

        if (access.startTime != null || access.endTime != null) {
            binding.switchHours.isChecked = true
            binding.tvStartTime.text = access.startTime?.take(5) ?: "Hora de Entrada"
            binding.tvEndTime.text = access.endTime?.take(5) ?: "Hora de Salida"
        } else {
            binding.switchHours.isChecked = false
        }
    }

    private fun generateQrImage(content: String) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap[x, y] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                }
            }
            binding.ivQrCode.clearColorFilter()
            binding.ivQrCode.setImageBitmap(bitmap)
            binding.ivQrCode.tag = bitmap // save for sharing
        } catch (_: Exception) {
        }
    }

    private fun shareQrCode() {
        val bitmap = binding.ivQrCode.tag as? Bitmap
        if (bitmap == null) {
            Toast.makeText(this, "QR no disponible para compartir", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val stream = FileOutputStream("$cachePath/qr_code.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val imagePath = File(cacheDir, "images")
            val newFile = File(imagePath, "qr_code.png")
            val contentUri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", newFile)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "Aquí tienes tu código de acceso: ${currentAccess?.guestName}")
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir Código QR"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al compartir", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAccessCode() {
        val guestName = binding.etGuestName.text.toString()
        if (guestName.isEmpty()) {
            binding.etGuestName.error = "Campo obligatorio"
            return
        }
        
        val isActive = binding.switchIsActive.isChecked
        val maxUses = if (binding.switchUses.isChecked) binding.etMaxUses.text.toString().toIntOrNull() else null
        val validFrom = if (binding.switchDates.isChecked && binding.tvValidFrom.text != "Fecha Inicio") binding.tvValidFrom.text.toString() else null
        val validUntil = if (binding.switchDates.isChecked && binding.tvValidUntil.text != "Fecha Fin") binding.tvValidUntil.text.toString() else null
        val startTime = if (binding.switchHours.isChecked && binding.tvStartTime.text != "Hora de Entrada") binding.tvStartTime.text.toString().take(5) else null
        val endTime = if (binding.switchHours.isChecked && binding.tvEndTime.text != "Hora de Salida") binding.tvEndTime.text.toString().take(5) else null
        
        var activeDays: List<Int>? = null
        if (binding.switchDays.isChecked) {
            val days = mutableListOf<Int>()
            if (binding.btnDay1.isChecked) days.add(1)
            if (binding.btnDay2.isChecked) days.add(2)
            if (binding.btnDay3.isChecked) days.add(3)
            if (binding.btnDay4.isChecked) days.add(4)
            if (binding.btnDay5.isChecked) days.add(5)
            if (binding.btnDay6.isChecked) days.add(6)
            if (binding.btnDay7.isChecked) days.add(7)
            activeDays = days
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                if (accessId == -1L) {
                    val profileRes  = ApiClient.apiService.getProfile()
                    val residenceId = profileRes.body()?.residences?.firstOrNull()?.id ?: 1L
                    
                    val req = AccessCodeCreateRequest(
                        residenceId = residenceId,
                        guestName = guestName,
                        validFrom = validFrom,
                        validUntil = validUntil,
                        maxUses = maxUses,
                        activeDays = activeDays,
                        startTime = startTime,
                        endTime = endTime
                    )
                    val res = ApiClient.apiService.createAccessCode(req)
                    if (res.isSuccessful) {
                        Toast.makeText(this@AccessDetailActivity, getString(R.string.access_created_success), Toast.LENGTH_SHORT).show()
                        val access = res.body()?.data
                        val intent = Intent().apply {
                            putExtra("access_data", Gson().toJson(access))
                        }
                        setResult(android.app.Activity.RESULT_OK, intent)
                        finishAfterTransition()
                    } else {
                        handleApiError(res.code(), res.errorBody()?.string())
                    }
                } else {
                    val req = AccessCodeUpdateRequest(
                        isActive = isActive,
                        guestName = guestName,
                        validFrom = validFrom,
                        validUntil = validUntil,
                        maxUses = maxUses,
                        activeDays = activeDays,
                        startTime = startTime,
                        endTime = endTime
                    )
                    val res = ApiClient.apiService.updateAccessCode(accessId, req)
                    if (res.isSuccessful) {
                        successBanner.show("Código actualizado")
                        val access = res.body()?.data
                        val intent = Intent().apply {
                            putExtra("access_data", Gson().toJson(access))
                        }
                        setResult(android.app.Activity.RESULT_OK, intent)
                        if (access != null) {
                            currentAccess = access
                            populateData(access)
                        }
                        isEditMode = false
                        updateUIMode()
                    } else {
                        handleApiError(res.code(), res.errorBody()?.string())
                    }
                }
            } catch (_: Exception) {
                errorBanner.show("Sin conexión a internet o servidor inaccesible.")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun deleteAccessCode() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val res = ApiClient.apiService.deleteAccessCode(accessId)
                if (res.isSuccessful) {
                    Toast.makeText(this@AccessDetailActivity, "Código eliminado", Toast.LENGTH_SHORT).show()
                    finishAfterTransition()
                } else {
                    handleApiError(res.code(), res.errorBody()?.string())
                }
            } catch (_: Exception) {
                errorBanner.show("Sin conexión a internet o servidor inaccesible.")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.viewInputBlocker.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            binding.fabSave.text = ""
            binding.fabSave.icon = null
            binding.fabSave.isEnabled = false
            binding.progressFab.visibility = View.VISIBLE
        } else {
            binding.fabSave.setText(R.string.access_detail_btn_save)
            binding.fabSave.setIconResource(R.drawable.ic_check)
            binding.fabSave.isEnabled = true
            binding.progressFab.visibility = View.GONE
        }
    }

    private fun setSkeletonLoading(loading: Boolean) {
        if (loading) {
            binding.layoutForm.visibility = View.GONE
            binding.layoutDetailSkeleton.root.visibility = View.VISIBLE
            android.animation.ObjectAnimator.ofFloat(binding.layoutDetailSkeleton.root, "alpha", 1f, 0.4f, 1f).apply {
                duration = 1200
                repeatCount = android.animation.ValueAnimator.INFINITE
                start()
            }
        } else {
            binding.layoutDetailSkeleton.root.visibility = View.GONE
            binding.layoutForm.visibility = View.VISIBLE
        }
    }

    private fun handleApiError(code: Int, @Suppress("UNUSED_PARAMETER") errorBody: String?) {
        val message = when (code) {
            401 -> "Sesión expirada"
            403 -> "Sin permiso"
            404 -> "No encontrado"
            else -> "Ocurrió un error ($code)"
        }
        errorBanner.show(message)
    }

    override fun onDestroy() {
        super.onDestroy()
        errorBanner.destroy()
        successBanner.destroy()
    }
}
