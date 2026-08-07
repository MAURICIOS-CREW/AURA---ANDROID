package com.mexadev.aura.ui.community

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.databinding.ItemCommentBinding

class CommunityCommentsAdapter(
    private var items: List<CommentModel>
) : RecyclerView.Adapter<CommunityCommentsAdapter.CommentViewHolder>() {

    class CommentViewHolder(val binding: ItemCommentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.binding.tvCommentContent.text = item.content
        holder.binding.tvCommentTime.text = CommunityTimeUtils.formatRelativeTime(item.timestamp)

        val rootParams = holder.binding.commentRoot.layoutParams as? ViewGroup.MarginLayoutParams
        val bubbleParams = holder.binding.bubbleContainer.layoutParams as? LinearLayout.LayoutParams

        if (item.isMe) {
            // Align right for current user's comments
            holder.binding.tvAuthorName.visibility = View.GONE
            if (rootParams != null) {
                holder.binding.commentRoot.gravity = Gravity.END
            }
            if (bubbleParams != null) {
                bubbleParams.gravity = Gravity.END
            }
            holder.binding.bubbleContainer.setBackgroundResource(R.drawable.bg_btn_primary)
            holder.binding.tvCommentContent.setTextColor(ContextCompat.getColor(context, R.color.aura_white))
            holder.binding.tvCommentTime.setTextColor(ContextCompat.getColor(context, R.color.aura_primary_surface))
        } else {
            // Align left for other users
            holder.binding.tvAuthorName.visibility = View.VISIBLE
            holder.binding.tvAuthorName.text = item.authorName
            if (rootParams != null) {
                holder.binding.commentRoot.gravity = Gravity.START
            }
            if (bubbleParams != null) {
                bubbleParams.gravity = Gravity.START
            }
            holder.binding.bubbleContainer.setBackgroundResource(R.drawable.bg_dashboard_card)
            holder.binding.tvCommentContent.setTextColor(ContextCompat.getColor(context, R.color.aura_text_primary))
            holder.binding.tvCommentTime.setTextColor(ContextCompat.getColor(context, R.color.aura_text_tertiary))
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: List<CommentModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size
}
