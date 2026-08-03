package com.mexadev.aura.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.data.local.entity.NotificationEntity
import java.util.Locale

sealed interface NotificationItemUiState {
    data class Content(val notification: NotificationEntity) : NotificationItemUiState
    data class Skeleton(val id: Int) : NotificationItemUiState
}

class NotificationsAdapter(
    private val onNotificationClick: (NotificationEntity, NotificationViewHolder) -> Unit
) : ListAdapter<NotificationItemUiState, RecyclerView.ViewHolder>(NotificationDiffCallback()) {

    fun showLoading(count: Int = 5) {
        submitList((1..count).map { NotificationItemUiState.Skeleton(it) })
    }

    fun submitNotifications(notifications: List<NotificationEntity>, commitCallback: (() -> Unit)? = null) {
        submitList(notifications.map { NotificationItemUiState.Content(it) }, commitCallback)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NotificationItemUiState.Skeleton -> R.layout.item_notification_skeleton
            is NotificationItemUiState.Content -> R.layout.item_notification
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(viewType, parent, false)
        return when (viewType) {
            R.layout.item_notification_skeleton -> SkeletonViewHolder(view)
            R.layout.item_notification -> NotificationViewHolder(view)
            else -> throw IllegalArgumentException("Unsupported viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NotificationItemUiState.Content -> (holder as NotificationViewHolder).bind(item.notification, onNotificationClick)
            is NotificationItemUiState.Skeleton -> (holder as SkeletonViewHolder).bindAnimation()
        }
    }

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvBody: TextView = itemView.findViewById(R.id.tvBody)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        val flIconContainer: View = itemView.findViewById(R.id.flIconContainer)
        val vUnreadDot: View = itemView.findViewById(R.id.vUnreadDot)
        val cvNotification: View = itemView.findViewById(R.id.cvNotification)

        fun bind(item: NotificationEntity, onClick: (NotificationEntity, NotificationViewHolder) -> Unit) {
            tvTitle.text = item.title
            tvBody.text = item.body
            tvTime.text = formatTime(item.timestamp)
            vUnreadDot.visibility = if (item.isRead) View.GONE else View.VISIBLE
            
            styleIcon(item.status, item.type, item.accessType)

            ViewCompat.setTransitionName(cvNotification, "notification_card_transition_${item.id}")
            ViewCompat.setTransitionName(tvTitle, "notification_title_transition_${item.id}")
            ViewCompat.setTransitionName(tvBody, "notification_body_transition_${item.id}")
            ViewCompat.setTransitionName(tvTime, "notification_date_transition_${item.id}")
            ViewCompat.setTransitionName(flIconContainer, "notification_icon_bg_transition_${item.id}")

            cvNotification.setOnClickListener {
                onClick(item, this)
            }
        }

        private fun styleIcon(status: String?, type: String?, accessType: String?) {
            val context = itemView.context
            when {
                type?.contains("incident", ignoreCase = true) == true -> {
                    ivIcon.setImageResource(R.drawable.ic_warning)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_warning))
                }
                accessType?.equals("vehicle", ignoreCase = true) == true || type?.contains("vehicle", ignoreCase = true) == true -> {
                    ivIcon.setImageResource(R.drawable.ic_car)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_primary))
                }
                accessType?.equals("pedestrian", ignoreCase = true) == true || type?.contains("access", ignoreCase = true) == true || status != null || accessType != null -> {
                    ivIcon.setImageResource(R.drawable.ic_door_access)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_primary))
                }
                else -> {
                    ivIcon.setImageResource(R.drawable.ic_nav_notifications_filled)
                    ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_primary))
                }
            }
        }

        private fun formatTime(utcTimestamp: String): String {
            return try {
                val cleanTimestamp = utcTimestamp.replace(" ", "T").let { 
                    if (!it.endsWith("Z") && !it.contains("+") && it.count { char -> char == '-' } <= 2) {
                        it + "Z"
                    } else {
                        it
                    }
                }
                
                val instant = java.time.Instant.parse(cleanTimestamp)
                val zonedDateTime = instant.atZone(java.time.ZoneId.systemDefault())
                
                val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())
                zonedDateTime.format(formatter)
            } catch (e: Exception) {
                utcTimestamp
            }
        }
    }

    class SkeletonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bindAnimation() {
            itemView.alpha = 0.5f
            android.animation.ObjectAnimator.ofFloat(itemView, "alpha", 0.5f, 1f).apply {
                duration = 800
                repeatCount = android.animation.ObjectAnimator.INFINITE
                repeatMode = android.animation.ObjectAnimator.REVERSE
                start()
            }
        }
    }
}

class NotificationDiffCallback : DiffUtil.ItemCallback<NotificationItemUiState>() {
    override fun areItemsTheSame(oldItem: NotificationItemUiState, newItem: NotificationItemUiState): Boolean {
        return when {
            oldItem is NotificationItemUiState.Content && newItem is NotificationItemUiState.Content ->
                oldItem.notification.id == newItem.notification.id
            oldItem is NotificationItemUiState.Skeleton && newItem is NotificationItemUiState.Skeleton ->
                oldItem.id == newItem.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: NotificationItemUiState, newItem: NotificationItemUiState): Boolean {
        return oldItem == newItem
    }
}
