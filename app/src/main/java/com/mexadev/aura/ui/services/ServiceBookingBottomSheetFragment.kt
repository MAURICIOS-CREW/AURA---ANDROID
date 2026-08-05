package com.mexadev.aura.ui.services

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.data.model.ContractedService
import com.mexadev.aura.data.model.Service
import com.mexadev.aura.data.model.ServiceContractRequest
import com.mexadev.aura.databinding.FragmentServiceBookingBottomSheetBinding
import com.mexadev.aura.ui.common.BannerManager
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
    private var selectedPaymentMethod: String = "stripe"
    private var receiptUploaded: Boolean = false

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

    private fun updateDateDisplay() {
        val localeMx = Locale.forLanguageTag("es-MX")
        val apiFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormat = SimpleDateFormat("EEEE, dd 'de' MMMM, yyyy", localeMx)
        
        selectedDateStr = apiFormat.format(calendar.time)
        val rawDate = displayFormat.format(calendar.time)
        val formattedNice = rawDate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(localeMx) else it.toString() }
        binding.tvSelectedDate.text = getString(R.string.services_booking_selected_date, formattedNice)
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

        // Defaults: 09:00 -> 13:00
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
            binding.tabStripe.typeface = android.graphics.Typeface.DEFAULT_BOLD

            binding.tabTransfer.background = null
            binding.tabTransfer.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_text_primary))
            binding.tabTransfer.typeface = android.graphics.Typeface.DEFAULT

            binding.layoutStripeForm.visibility = View.VISIBLE
            binding.layoutTransferForm.visibility = View.GONE
        }

        binding.tabTransfer.setOnClickListener {
            selectedPaymentMethod = "transfer"
            binding.tabTransfer.setBackgroundResource(R.drawable.bg_button_tab_active)
            binding.tabTransfer.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_white))
            binding.tabTransfer.typeface = android.graphics.Typeface.DEFAULT_BOLD

            binding.tabStripe.background = null
            binding.tabStripe.setTextColor(ContextCompat.getColor(requireContext(), R.color.aura_text_primary))
            binding.tabStripe.typeface = android.graphics.Typeface.DEFAULT

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
        val s = service ?: return
        val fromTime = binding.spTimeFrom.selectedItem as String
        val toTime = binding.spTimeTo.selectedItem as String
        val notes = binding.etNotes.text.toString().trim().ifEmpty { null }

        // Start loading state
        setContractLoadingState(true)

        lifecycleScope.launch {
            try {
                // Fetch current user residence_id dynamically
                val userResponse = ApiClient.apiService.getProfile()
                val residenceId = userResponse.body()?.residences?.firstOrNull()?.id ?: 1L

                val request = ServiceContractRequest(
                    residenceId = residenceId,
                    preferredDate = selectedDateStr,
                    visitTimeFrom = fromTime,
                    visitTimeTo = toTime,
                    notes = notes,
                    paymentMethod = selectedPaymentMethod
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
                    val errorMsg = response.errorBody()?.string()?.let { parseErrorMessage(it) }
                        ?: response.body()?.message
                        ?: getString(R.string.services_error_contract_failed)
                    sheetBannerManager.show(errorMsg)
                }

            } catch (_: Exception) {
                setContractLoadingState(false)
                sheetBannerManager.show(getString(R.string.services_error_no_connection))
            }
        }
    }

    private fun parseErrorMessage(errorBodyJson: String): String? {
        return try {
            val jsonObj = com.google.gson.JsonParser.parseString(errorBodyJson).asJsonObject
            if (jsonObj.has("message")) jsonObj.get("message").asString else null
        } catch (_: Exception) {
            null
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
