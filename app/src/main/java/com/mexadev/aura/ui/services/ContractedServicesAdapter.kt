package com.mexadev.aura.ui.services

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.data.model.ContractedService
import com.mexadev.aura.databinding.ItemContractedServiceBinding
import java.util.Locale

class ContractedServicesAdapter(
    private val onItemClick: (ContractedService, ItemContractedServiceBinding) -> Unit,
    private val onCompleteClick: ((ContractedService, ItemContractedServiceBinding) -> Unit)? = null
) : ListAdapter<ContractedService, ContractedServicesAdapter.ViewHolder>(ContractedServiceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContractedServiceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick, onCompleteClick)
    }

    class ViewHolder(val binding: ItemContractedServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: ContractedService,
            onItemClick: (ContractedService, ItemContractedServiceBinding) -> Unit,
            @Suppress("UNUSED_PARAMETER") onComplete: ((ContractedService, ItemContractedServiceBinding) -> Unit)?
        ) {
            val context = binding.root.context
            val sTitle = item.service?.title ?: context.getString(R.string.services_title)
            val theme = ServiceIconHelper.getCategoryTheme(sTitle)

            binding.tvTitle.text = sTitle
            binding.ivServiceIcon.setImageResource(theme.iconRes)
            binding.ivServiceIcon.setColorFilter(ContextCompat.getColor(context, theme.iconColorRes))
            binding.flIconContainer.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, theme.bgColorRes))

            // Status Badge
            when (item.status.lowercase(Locale.getDefault())) {
                "completed" -> {
                    binding.tvStatus.text = context.getString(R.string.services_status_completed)
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_success)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.aura_success))
                }
                "scheduled" -> {
                    binding.tvStatus.text = context.getString(R.string.services_status_scheduled)
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_info)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.aura_primary))
                }
                "cancelled", "canceled" -> {
                    binding.tvStatus.text = context.getString(R.string.services_status_cancelled)
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_error)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.aura_error))
                }
                else -> { // "created" / pending
                    binding.tvStatus.text = context.getString(R.string.services_status_pending)
                    binding.tvStatus.setBackgroundResource(R.drawable.bg_pill_warning)
                    binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.aura_warning))
                }
            }

            // Recurrent Badge
            if (item.isRecurrent == true) {
                binding.tvRecurrentBadge.visibility = View.VISIBLE
                val sched = item.suggestedSchedule
                if (!sched.isNullOrEmpty()) {
                    binding.tvRecurrentBadge.text = context.getString(
                        R.string.services_recurrent_schedule_format,
                        sched.joinToString(", ")
                    )
                } else {
                    binding.tvRecurrentBadge.text = context.getString(R.string.services_recurrent_badge)
                }
            } else {
                binding.tvRecurrentBadge.visibility = View.GONE
            }

            // Dates & Times
            val dateStr = item.preferredDate ?: ""
            val fromTime = item.visitTimeFrom?.take(5) ?: ""
            val toTime = item.visitTimeTo?.take(5) ?: ""
            binding.tvDateTime.text = context.getString(R.string.services_suggested_schedule, dateStr, fromTime, toTime)

            // Exact scheduled timestamp
            if (!item.exactScheduledAt.isNullOrBlank()) {
                val formatted = formatExactScheduled(item.exactScheduledAt)
                binding.tvExactScheduled.text = context.getString(R.string.services_confirmed_schedule, formatted)
                binding.tvExactScheduled.visibility = View.VISIBLE
            } else {
                binding.tvExactScheduled.visibility = View.GONE
            }

            // Access Code
            if (item.accessCode != null) {
                val codeShort = item.accessCode.code.take(8)
                binding.tvAccessCodeInfo.text = context.getString(R.string.services_active_access_code, codeShort)
                binding.layoutAccessCode.visibility = View.VISIBLE
            } else {
                binding.layoutAccessCode.visibility = View.GONE
            }

            // Shared Element Transition Names per item
            val id = item.id
            binding.flIconContainer.transitionName = "trans_icon_$id"
            binding.tvTitle.transitionName = "trans_title_$id"
            binding.tvStatus.transitionName = "trans_status_$id"
            binding.root.transitionName = "transition_contracted_service_$id"

            binding.root.setOnClickListener {
                onItemClick(item, binding)
            }
        }

        private fun formatExactScheduled(utcStr: String?): String {
            if (utcStr == null) return ""
            return try {
                val clean = utcStr.replace(" ", "T")
                val instant = java.time.Instant.parse(clean)
                val zdt = instant.atZone(java.time.ZoneId.systemDefault())
                val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())
                zdt.format(formatter)
            } catch (_: Exception) {
                utcStr
            }
        }
    }
}

class ContractedServiceDiffCallback : DiffUtil.ItemCallback<ContractedService>() {
    override fun areItemsTheSame(oldItem: ContractedService, newItem: ContractedService): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ContractedService, newItem: ContractedService): Boolean {
        return oldItem == newItem
    }
}
