package com.mexadev.aura.ui.accesses

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.data.model.AccessCode
import com.mexadev.aura.data.model.evaluateValidity
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
        val oldCount = itemCount
        this.skeletonCount = 0
        this.items.clear()
        this.items.addAll(newItems)
        val newCount = itemCount
        if (oldCount > 0) notifyItemRangeRemoved(0, oldCount)
        if (newCount > 0) notifyItemRangeInserted(0, newCount)
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

    @Suppress("unused")
    fun showSkeletons(count: Int) {
        val oldCount = itemCount
        this.items.clear()
        this.skeletonCount = count
        val newCount = itemCount
        when {
            newCount > oldCount -> {
                notifyItemRangeChanged(0, oldCount)
                notifyItemRangeInserted(oldCount, newCount - oldCount)
            }
            newCount < oldCount -> {
                notifyItemRangeChanged(0, newCount)
                notifyItemRangeRemoved(newCount, oldCount - newCount)
            }
            else -> notifyItemRangeChanged(0, newCount)
        }
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
        } else if (holder is SkeletonViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int {
        return if (skeletonCount > 0) skeletonCount else items.size
    }

    class SkeletonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            ObjectAnimator.ofFloat(itemView, "alpha", 1f, 0.4f, 1f).apply {
                duration     = 1200
                repeatCount  = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }.start()
        }
    }

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
            val validity = accessCode.evaluateValidity()

            fun createBadgeBg(colorRes: Int): GradientDrawable {
                return GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24 * context.resources.displayMetrics.density
                    setColor(ContextCompat.getColor(context, colorRes))
                }
            }

            if (accessCode.isActive) {
                binding.tvStatus.setText(R.string.access_status_active)
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.aura_success))
                binding.tvStatus.background = createBadgeBg(R.color.aura_success_light)
                binding.ivAccessIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_primary))
            } else {
                binding.tvStatus.setText(R.string.access_status_inactive)
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.aura_text_tertiary))
                binding.tvStatus.background = createBadgeBg(R.color.aura_surface_variant)
                binding.ivAccessIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_text_tertiary))
            }

            binding.tvReasonBadge.text = validity.shortBadgeText
            binding.tvReasonBadge.setTextColor(ContextCompat.getColor(context, validity.badgeTextColorRes))
            binding.tvReasonBadge.background = createBadgeBg(validity.badgeBgColorRes)
            binding.tvReasonBadge.visibility = View.VISIBLE

            binding.root.transitionName = "transition_access_${accessCode.id}"

            binding.root.setOnClickListener {
                onClick(accessCode, binding.root)
            }
        }
    }
}
