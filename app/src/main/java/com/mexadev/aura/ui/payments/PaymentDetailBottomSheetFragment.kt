package com.mexadev.aura.ui.payments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.mexadev.aura.R
import com.mexadev.aura.data.model.PaymentHistoryItem
import com.mexadev.aura.databinding.FragmentPaymentDetailBottomSheetBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * BottomSheetFragment para mostrar los detalles completos de un pago histórico.
 * Sigue principios SOLID.
 */
class PaymentDetailBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentPaymentDetailBottomSheetBinding? = null
    private val binding get() = _binding!!
    private var paymentItem: PaymentHistoryItem? = null

    companion object {
        private const val ARG_PAYMENT_JSON = "arg_payment_json"

        fun newInstance(item: PaymentHistoryItem): PaymentDetailBottomSheetFragment {
            val fragment = PaymentDetailBottomSheetFragment()
            val args = Bundle()
            args.putString(ARG_PAYMENT_JSON, Gson().toJson(item))
            fragment.arguments = args
            return fragment
        }
    }

    override fun getTheme(): Int {
        return com.google.android.material.R.style.Theme_Design_BottomSheetDialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = arguments?.getString(ARG_PAYMENT_JSON)
        if (json != null) {
            try {
                paymentItem = Gson().fromJson(json, PaymentHistoryItem::class.java)
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
        _binding = FragmentPaymentDetailBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindData()
    }

    private fun bindData() {
        val item = paymentItem ?: return
        val context = requireContext()

        val idStr = item.paymentId?.toString() ?: item.id.toString()
        binding.tvDetailTransactionId.text = getString(R.string.payments_detail_id, idStr)
        binding.tvDetailAmount.text = getString(R.string.payments_balance_format, item.amount)
        binding.tvDetailConceptTitle.text = item.title

        binding.tvDetailDate.text = formatUtcToLocalDate(item.date)

        val methodStr = when (item.paymentMethod?.lowercase()) {
            "stripe" -> "Tarjeta (Stripe)"
            "transfer" -> "Transferencia SPEI"
            "card" -> "Tarjeta"
            else -> item.paymentMethod ?: "Digital"
        }
        binding.tvDetailMethod.text = methodStr

        when (item.status.lowercase()) {
            "paid", "approved" -> {
                binding.tvDetailStatusBadge.text = getString(R.string.payments_status_paid)
                binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_pill_success)
                binding.tvDetailStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.aura_success))
            }
            "pending" -> {
                binding.tvDetailStatusBadge.text = getString(R.string.payments_status_pending)
                binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_pill_warning)
                binding.tvDetailStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.aura_warning))
            }
            else -> {
                binding.tvDetailStatusBadge.text = item.status
                binding.tvDetailStatusBadge.setBackgroundResource(R.drawable.bg_pill_info)
                binding.tvDetailStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.aura_info))
            }
        }

        if (!item.receiptUrl.isNullResourceOrBlank()) {
            binding.btnViewReceipt.visibility = View.VISIBLE
            binding.btnViewReceipt.setOnClickListener {
                val receiptUrl = item.receiptUrl ?: return@setOnClickListener
                try {
                    val intent = Intent(Intent.ACTION_VIEW, receiptUrl.toUri())
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            binding.btnViewReceipt.visibility = View.GONE
        }

        binding.btnCloseDetail.setOnClickListener {
            dismiss()
        }
    }

    private fun formatUtcToLocalDate(rawDate: String?): String {
        if (rawDate.isNullOrBlank()) return getString(R.string.payments_one_month_ago)

        val inputPatterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )

        val localeMx = Locale.Builder().setLanguage("es").setRegion("MX").build()
        val outputFormat = SimpleDateFormat("dd 'de' MMMM, yyyy hh:mm a", localeMx).apply {
            timeZone = TimeZone.getDefault()
        }

        for (pattern in inputPatterns) {
            try {
                val inputFormat = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsedDate = inputFormat.parse(rawDate)
                if (parsedDate != null) {
                    val formatted = outputFormat.format(parsedDate)
                    return formatted
                        .replace("a. m.", "AM")
                        .replace("p. m.", "PM")
                        .replace("a.m.", "AM")
                        .replace("p.m.", "PM")
                        .replace("am", "AM")
                        .replace("pm", "PM")
                }
            } catch (_: Exception) {
                // Probar el siguiente patrón en la lista
            }
        }

        return rawDate
    }

    private fun String?.isNullResourceOrBlank(): Boolean {
        return this.isNullOrBlank() || this == "null"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
