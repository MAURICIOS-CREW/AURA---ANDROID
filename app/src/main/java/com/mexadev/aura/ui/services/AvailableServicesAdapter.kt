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
import com.mexadev.aura.data.model.Service
import com.mexadev.aura.databinding.ItemAvailableServiceBinding

class AvailableServicesAdapter(
    private val onServiceClick: (Service, View) -> Unit
) : ListAdapter<Service, AvailableServicesAdapter.ServiceViewHolder>(ServiceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val binding = ItemAvailableServiceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        holder.bind(getItem(position), onServiceClick)
    }

    class ServiceViewHolder(private val binding: ItemAvailableServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service, onClick: (Service, View) -> Unit) {
            val context = binding.root.context
            val theme = ServiceIconHelper.getCategoryTheme(service.title, service.description)

            binding.tvTitle.text = service.title
            binding.tvCategory.text = theme.categoryName
            binding.tvDescription.text = service.description ?: context.getString(R.string.coming_soon_description, service.title)
            binding.tvPrice.text = context.getString(R.string.services_price_amount_short, service.price)

            binding.ivServiceIcon.setImageResource(theme.iconRes)
            binding.ivServiceIcon.setColorFilter(ContextCompat.getColor(context, theme.iconColorRes))
            binding.flIconContainer.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, theme.bgColorRes))

            // Ensure transition name is strictly unique per element
            binding.root.transitionName = "transition_available_service_${service.id}"

            binding.root.setOnClickListener {
                onClick(service, binding.root)
            }
        }
    }
}

class ServiceDiffCallback : DiffUtil.ItemCallback<Service>() {
    override fun areItemsTheSame(oldItem: Service, newItem: Service): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Service, newItem: Service): Boolean {
        return oldItem == newItem
    }
}
