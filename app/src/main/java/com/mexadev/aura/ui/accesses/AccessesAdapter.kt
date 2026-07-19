package com.mexadev.aura.ui.accesses

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.data.model.AccessCode
import com.mexadev.aura.databinding.ItemAccessBinding

class AccessesAdapter(
    private var items: MutableList<AccessCode>,
    private val onItemClick: (AccessCode, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SKELETON = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    private var skeletonCount = 0

    fun updateData(newItems: List<AccessCode>) {
        this.skeletonCount = 0
        this.items.clear()
        this.items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updateItem(index: Int, item: AccessCode) {
        if (index in 0 until items.size) {
            items[index] = item
            notifyItemChanged(index)
        }
    }

    fun addItem(index: Int, item: AccessCode) {
        items.add(index, item)
        notifyItemInserted(index)
    }

    fun showSkeletons(count: Int) {
        this.items.clear()
        this.skeletonCount = count
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (skeletonCount > 0) VIEW_TYPE_SKELETON else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SKELETON) {
            val view = inflater.inflate(R.layout.item_access_skeleton, parent, false)
            SkeletonViewHolder(view)
        } else {
            val binding = ItemAccessBinding.inflate(inflater, parent, false)
            AccessViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AccessViewHolder) {
            holder.bind(items[position], onItemClick)
        }
    }

    override fun getItemCount(): Int {
        return if (skeletonCount > 0) skeletonCount else items.size
    }

    class SkeletonViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class AccessViewHolder(private val binding: ItemAccessBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(accessCode: AccessCode, onClick: (AccessCode, View) -> Unit) {
            binding.tvGuestName.text = accessCode.guestName ?: "Invitado"
            // Code text removed per user request
            
            val usesText = if (accessCode.maxUses != null) {
                "${accessCode.uses}/${accessCode.maxUses} usos"
            } else {
                "${accessCode.uses} usos"
            }
            binding.tvUsesCount.text = usesText

            val context = binding.root.context
            
            if (accessCode.isActive) {
                binding.tvStatus.text = "Activo"
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.aura_success))
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24 * context.resources.displayMetrics.density
                    setColor(ContextCompat.getColor(context, R.color.aura_success_light))
                }
                binding.tvStatus.background = bg
                binding.ivAccessIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_primary))
            } else {
                binding.tvStatus.text = "Inactivo"
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.aura_text_tertiary))
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24 * context.resources.displayMetrics.density
                    setColor(ContextCompat.getColor(context, R.color.aura_surface_variant))
                }
                binding.tvStatus.background = bg
                binding.ivAccessIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_text_tertiary))
            }

            binding.root.transitionName = "transition_access_${accessCode.id}"

            binding.root.setOnClickListener {
                onClick(accessCode, binding.root)
            }
        }
    }
}
