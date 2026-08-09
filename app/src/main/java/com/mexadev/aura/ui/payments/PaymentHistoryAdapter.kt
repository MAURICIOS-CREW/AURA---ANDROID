package com.mexadev.aura.ui.payments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.data.model.PaymentHistoryItem
import com.mexadev.aura.databinding.ItemPaymentHistoryBinding

/**
 * Adapter para el historial de pagos.
 * Cumple con principios SOLID y Estándares AURA:
 * - Herencia de ListAdapter + DiffUtil para actualizaciones atómicas del RecyclerView sin parpadeos.
 * - Subdivisión en ViewHolder enfocado únicamente al binding visual (SRP).
 */
class PaymentHistoryAdapter(
    private val onItemClick: (PaymentHistoryItem) -> Unit
) : ListAdapter<PaymentHistoryItem, PaymentHistoryAdapter.PaymentHistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentHistoryViewHolder {
        val binding = ItemPaymentHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PaymentHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PaymentHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PaymentHistoryViewHolder(
        private val binding: ItemPaymentHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PaymentHistoryItem) {
            val context = binding.root.context

            binding.tvPaymentTitle.text = item.title

            val payMethodStr = when (item.paymentMethod?.lowercase()) {
                "stripe" -> "Stripe"
                "transfer" -> "Transferencia SPEI"
                "card" -> "Tarjeta"
                else -> item.paymentMethod ?: "Digital"
            }

            val statusText = when (item.status.lowercase()) {
                "paid", "approved" -> context.getString(R.string.payments_status_paid)
                "pending" -> context.getString(R.string.payments_status_pending)
                else -> item.status.replaceFirstChar { it.uppercase() }
            }

            binding.tvPaymentSub.text = context.getString(R.string.payments_history_sub_format, statusText, payMethodStr)
            binding.tvPaymentAmount.text = context.getString(R.string.payments_balance_format, item.amount)

            // Setup Badge & Colors
            when (item.status.lowercase()) {
                "paid", "approved" -> {
                    binding.tvStatusBadge.text = context.getString(R.string.payments_status_paid)
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_success)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.aura_success))
                    binding.ivCheckIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_success))
                }
                "pending" -> {
                    binding.tvStatusBadge.text = context.getString(R.string.payments_status_pending)
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_warning)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.aura_warning))
                    binding.ivCheckIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_warning))
                }
                else -> {
                    binding.tvStatusBadge.text = item.status
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_pill_info)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.aura_info))
                    binding.ivCheckIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_info))
                }
            }

            binding.cardPaymentHistory.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PaymentHistoryItem>() {
        override fun areItemsTheSame(oldItem: PaymentHistoryItem, newItem: PaymentHistoryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PaymentHistoryItem, newItem: PaymentHistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
