package com.mexadev.aura.ui.payments

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.mexadev.aura.BuildConfig
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.data.model.PaymentProcessData
import com.mexadev.aura.data.model.PaymentProcessItemRequest
import com.mexadev.aura.data.model.PaymentProcessRequest
import com.mexadev.aura.databinding.FragmentPaymentFormBottomSheetBinding
import com.mexadev.aura.ui.common.BannerManager
import com.stripe.android.ApiResultCallback
import com.stripe.android.PaymentConfiguration
import com.stripe.android.Stripe
import com.stripe.android.model.PaymentMethod
import com.stripe.android.model.PaymentMethodCreateParams
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

/**
 * Formulario de pago modular (SOLID) con integración real de Stripe.
 * - Confirmación Directa vía CardInputWidget embebido en el propio formulario (Flujo pm_xxx).
 * - ViewFlipper para Step1 ↔ Step2 Success.
 * - Paneles Stripe/Transfer animados manualmente con slide horizontal.
 * - AuraSegmentedControl sincronizado con los paneles vía setOnTabSelectedListener.
 */
class PaymentFormBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentPaymentFormBottomSheetBinding? = null
    private val binding get() = _binding!!

    private var itemsToPay: List<PaymentProcessItemRequest> = emptyList()
    private var totalAmountStr: String = "0.00"
    private var selectedPaymentMethod: String = "stripe"
    private var selectedReceiptUri: Uri? = null
    private var currentPanelIndex: Int = 0   // 0 = Stripe, 1 = Transfer

    private var onPaymentSuccessCallback: (() -> Unit)? = null
    private var paymentSucceeded: Boolean = false
    private lateinit var bannerManager: BannerManager

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedReceiptUri = uri
            displayReceiptPreview(uri)
        }
    }

    companion object {
        private const val ARG_ITEMS_JSON = "arg_items_json"
        private const val ARG_TOTAL_AMOUNT = "arg_total_amount"

        fun newInstance(
            items: List<PaymentProcessItemRequest>,
            totalAmount: String,
            onSuccess: () -> Unit
        ): PaymentFormBottomSheetFragment {
            val fragment = PaymentFormBottomSheetFragment()
            val args = Bundle()
            args.putString(ARG_ITEMS_JSON, Gson().toJson(items))
            args.putString(ARG_TOTAL_AMOUNT, totalAmount)
            fragment.arguments = args
            fragment.onPaymentSuccessCallback = onSuccess
            return fragment
        }
    }

    override fun getTheme(): Int =
        com.google.android.material.R.style.Theme_Design_BottomSheetDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pubKey = BuildConfig.STRIPE_PUBLISHABLE_KEY.ifEmpty { "pk_test_51Pxxx" }
        PaymentConfiguration.init(requireContext().applicationContext, pubKey)

        val json = arguments?.getString(ARG_ITEMS_JSON)
        totalAmountStr = arguments?.getString(ARG_TOTAL_AMOUNT) ?: "0.00"
        if (json != null) {
            try {
                val type = object : TypeToken<List<PaymentProcessItemRequest>>() {}.type
                itemsToPay = Gson().fromJson(json, type)
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
            val cornerRadius = resources.getDimension(R.dimen.radius_xl)
            val shapeModel = com.google.android.material.shape.ShapeAppearanceModel.builder()
                .setTopLeftCorner(com.google.android.material.shape.CornerFamily.ROUNDED, cornerRadius)
                .setTopRightCorner(com.google.android.material.shape.CornerFamily.ROUNDED, cornerRadius)
                .build()
            sheet.background = com.google.android.material.shape.MaterialShapeDrawable(shapeModel).apply {
                fillColor = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.aura_white)
                )
            }
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
        _binding = FragmentPaymentFormBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bannerManager = BannerManager(binding.formBannerContainer, binding.tvFormBannerMessage)

        setupAmountHeader()
        setupPaymentTabs()
        setupTransferPanel()
        setupSubmitButton()
        setupFinishButton()

        binding.panelTransfer.post {
            binding.panelTransfer.translationX = binding.panelContainer.width.toFloat()
        }
    }

    private fun setupAmountHeader() {
        binding.tvFormAmountHeader.text = getString(R.string.payments_balance_format, totalAmountStr)
        binding.btnSubmitPayment.text = getString(R.string.payments_form_confirm_pay)
    }

    private fun setupPaymentTabs() {
        binding.segmentedControlPayment.setTabs(
            listOf(
                getString(R.string.payments_form_stripe_tab),
                getString(R.string.payments_form_transfer_tab)
            )
        )

        binding.segmentedControlPayment.setOnTabSelectedListener { position, _ ->
            if (position == currentPanelIndex) return@setOnTabSelectedListener

            val goingRight = position > currentPanelIndex
            currentPanelIndex = position
            selectedPaymentMethod = if (position == 0) "stripe" else "transfer"

            slidePanels(
                outgoing = if (goingRight) binding.panelStripe else binding.panelTransfer,
                incoming = if (goingRight) binding.panelTransfer else binding.panelStripe,
                toLeft = goingRight
            )
        }
    }

    private fun slidePanels(outgoing: View, incoming: View, toLeft: Boolean) {
        val containerWidth = binding.panelContainer.width.toFloat()
        val slideDistance = containerWidth * 1.05f
        val duration = 280L
        val interpolatorOut = DecelerateInterpolator(1.5f)
        val interpolatorIn  = DecelerateInterpolator(1.5f)

        incoming.translationX = if (toLeft) slideDistance else -slideDistance
        incoming.alpha = 0f
        incoming.visibility = View.VISIBLE

        val outTX = ObjectAnimator.ofFloat(outgoing, "translationX", if (toLeft) -slideDistance else slideDistance)
        val outAlpha = ObjectAnimator.ofFloat(outgoing, "alpha", 1f, 0f)
        outTX.interpolator = interpolatorOut
        outAlpha.interpolator = interpolatorOut

        val inTX = ObjectAnimator.ofFloat(incoming, "translationX", 0f)
        val inAlpha = ObjectAnimator.ofFloat(incoming, "alpha", 0f, 1f)
        inTX.interpolator = interpolatorIn
        inAlpha.interpolator = interpolatorIn

        AnimatorSet().apply {
            playTogether(outTX, outAlpha, inTX, inAlpha)
            this.duration = duration
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    outgoing.visibility = View.INVISIBLE
                    outgoing.alpha = 1f
                    outgoing.translationX = if (toLeft) slideDistance else -slideDistance
                }
            })
            start()
        }
    }

    private fun setupTransferPanel() {
        binding.btnCopyClabeForm.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("CLABE AURA", "012180001234567890"))
            Toast.makeText(requireContext(), R.string.services_booking_clabe_copied, Toast.LENGTH_SHORT).show()
        }

        binding.btnUploadRealReceipt.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun displayReceiptPreview(uri: Uri) {
        val fileName = getFileName(uri) ?: "comprobante.jpg"
        binding.tvReceiptFileName.text = fileName
        binding.ivReceiptThumbnail.setImageURI(uri)
        binding.layoutReceiptPreview.visibility = View.VISIBLE
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) result = it.getString(idx)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    private fun setupSubmitButton() {
        binding.btnSubmitPayment.setOnClickListener { executePaymentProcess() }
    }

    private fun setupFinishButton() {
        binding.btnFinishForm.setOnClickListener { dismiss() }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (paymentSucceeded) {
            onPaymentSuccessCallback?.invoke()
        }
    }

    private fun executePaymentProcess() {
        if (itemsToPay.isEmpty()) {
            bannerManager.show(getString(R.string.payments_select_at_least_one))
            return
        }

        bannerManager.hide()

        if (selectedPaymentMethod == "stripe") {
            // El pago siempre se confirma con la tarjeta capturada en el propio
            // formulario (CardInputWidget embebido) — nunca se navega a una
            // pantalla externa de Stripe.
            val params = binding.stripeCardInputWidget.paymentMethodCreateParams
            if (params != null) {
                executeDirectStripePayment(params)
            } else {
                bannerManager.show(getString(R.string.payments_form_card_incomplete))
            }
        } else {
            // Pago vía Transferencia o Efectivo
            executeNonStripePayment()
        }
    }

    private fun executeDirectStripePayment(params: PaymentMethodCreateParams) {
        setLoadingState(true)
        val pubKey = BuildConfig.STRIPE_PUBLISHABLE_KEY.ifEmpty { "pk_test_51Pxxx" }
        PaymentConfiguration.init(requireContext().applicationContext, pubKey)

        val stripe = Stripe(requireContext().applicationContext, pubKey)

        stripe.createPaymentMethod(params, callback = object : ApiResultCallback<PaymentMethod> {
            override fun onSuccess(result: PaymentMethod) {
                val pmId = result.id
                if (pmId != null) {
                    confirmBackendPayment(paymentMethodId = pmId)
                } else {
                    setLoadingState(false)
                    bannerManager.show(getString(R.string.payments_error_process))
                }
            }

            override fun onError(e: Exception) {
                setLoadingState(false)
                bannerManager.show(e.localizedMessage ?: getString(R.string.payments_error_process))
            }
        })
    }

    private fun confirmBackendPayment(
        stripePaymentIntentId: String? = null,
        paymentMethodId: String? = null
    ) {
        setLoadingState(true)
        lifecycleScope.launch {
            try {
                val request = PaymentProcessRequest(
                    items = itemsToPay,
                    paymentMethod = "stripe",
                    stripePaymentIntentId = stripePaymentIntentId,
                    paymentMethodId = paymentMethodId
                )
                val response = ApiClient.apiService.payPendingItemsJson(request)
                setLoadingState(false)

                if (response.isSuccessful && response.body()?.status == "success") {
                    showPaymentSuccess(response.body()?.data)
                } else {
                    val errorMsg = parsePaymentErrorMsg(
                        response.errorBody()?.string(),
                        response.body()?.message ?: getString(R.string.payments_error_process)
                    )
                    bannerManager.show(errorMsg)
                }

            } catch (_: Exception) {
                setLoadingState(false)
                bannerManager.show(getString(R.string.services_error_no_connection))
            }
        }
    }

    private fun executeNonStripePayment() {
        setLoadingState(true)
        lifecycleScope.launch {
            try {
                val receiptPart = selectedReceiptUri?.let { uriToMultipart(it) }

                val response = if (receiptPart != null) {
                    val itemsJson = Gson().toJson(itemsToPay)
                    val itemsBody = itemsJson.toRequestBody("application/json".toMediaTypeOrNull())
                    val methodBody = selectedPaymentMethod.toRequestBody("text/plain".toMediaTypeOrNull())
                    ApiClient.apiService.payPendingItemsMultipart(
                        items = itemsBody,
                        paymentMethod = methodBody,
                        receipt = receiptPart
                    )
                } else {
                    val request = PaymentProcessRequest(
                        items = itemsToPay,
                        paymentMethod = selectedPaymentMethod
                    )
                    ApiClient.apiService.payPendingItemsJson(request)
                }

                setLoadingState(false)

                if (response.isSuccessful && response.body()?.status == "success") {
                    showPaymentSuccess(response.body()?.data)
                } else {
                    val errorMsg = parsePaymentErrorMsg(
                        response.errorBody()?.string(),
                        response.body()?.message ?: getString(R.string.payments_error_process)
                    )
                    bannerManager.show(errorMsg)
                }

            } catch (_: Exception) {
                setLoadingState(false)
                bannerManager.show(getString(R.string.services_error_no_connection))
            }
        }
    }

    private fun showPaymentSuccess(data: PaymentProcessData?) {
        paymentSucceeded = true
        val paidTotal = data?.totalPaid ?: totalAmountStr
        binding.tvFormTotalPaidSuccess.text = getString(R.string.payments_balance_format, paidTotal)

        val methodName = when (data?.paymentMethod ?: selectedPaymentMethod) {
            "stripe" -> getString(R.string.services_booking_payment_stripe_name)
            else -> getString(R.string.services_booking_payment_transfer_name)
        }
        binding.tvFormPaymentMethodSuccess.text = getString(R.string.payments_form_success_method, methodName)

        val itemsCount = data?.payments?.size?.takeIf { it > 0 } ?: itemsToPay.size
        binding.tvFormItemsCountSuccess.text = getString(R.string.payments_form_success_items_count, itemsCount)

        slideToSuccess()
        animateSuccessIcon()
    }

    private fun parsePaymentErrorMsg(errorBodyJson: String?, defaultMessage: String): String {
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

    private fun uriToMultipart(uri: Uri): MultipartBody.Part? {
        return try {
            val resolver = requireContext().contentResolver
            val inputStream = resolver.openInputStream(uri) ?: return null
            val dir = File(requireContext().cacheDir, "receipts").also { if (!it.exists()) it.mkdirs() }
            val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> inputStream.use { it.copyTo(out) } }
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("receipt", file.name, requestFile)
        } catch (_: Exception) { null }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnSubmitPayment.isEnabled = !isLoading
        binding.btnSubmitPayment.text = if (isLoading) "" else getString(R.string.payments_form_confirm_pay)
        binding.pbSubmitLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun slideToSuccess() {
        binding.viewFlipperForm.setInAnimation(requireContext(), R.anim.slide_in_right)
        binding.viewFlipperForm.setOutAnimation(requireContext(), R.anim.slide_out_left)
        binding.viewFlipperForm.showNext()
    }

    private fun animateSuccessIcon() {
        val scaleX = ObjectAnimator.ofFloat(binding.flFormSuccessIcon, "scaleX", 0f, 1.2f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.flFormSuccessIcon, "scaleY", 0f, 1.2f, 1f)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 500
            interpolator = OvershootInterpolator(2.0f)
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bannerManager.destroy()
        _binding = null
    }
}
