package com.mexadev.aura.ui.notifications

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mexadev.aura.data.local.AuraDatabase
import com.mexadev.aura.data.local.entity.NotificationEntity
import com.mexadev.aura.databinding.FragmentNotificationsBinding
import com.mexadev.aura.ui.fcm.QuickActionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: NotificationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = NotificationsAdapter { notification, viewHolder ->
            openNotificationDetails(notification, viewHolder)
        }
        binding.rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotifications.adapter = adapter
        binding.rvNotifications.itemAnimator = NotificationItemAnimator()
        
        // Initial skeleton state
        adapter.showLoading(5)
    }

    private fun loadData() {
        val dao = AuraDatabase.getDatabase(requireContext()).notificationDao()
        
        viewLifecycleOwner.lifecycleScope.launch {
            // Get count for skeletons first
            try {
                val count = withContext(Dispatchers.IO) {
                    dao.getUnreadCount().first()
                }
                if (count > 0) {
                    adapter.showLoading(count.coerceAtMost(10)) // Limit to 10 skeletons
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Observe actual data
            dao.getAllNotifications().collectLatest { notifications ->
                if (notifications.isEmpty()) {
                    binding.rvNotifications.visibility = View.GONE
                    binding.llEmptyState.visibility = View.VISIBLE
                } else {
                    binding.rvNotifications.visibility = View.VISIBLE
                    binding.llEmptyState.visibility = View.GONE
                    adapter.submitNotifications(notifications) {
                        // Smooth scroll to top if a new item is inserted at the top
                        if (notifications.isNotEmpty() && adapter.itemCount > 0) {
                            binding.rvNotifications.scrollToPosition(0)
                        }
                    }
                }
            }
        }
    }

    private fun openNotificationDetails(notification: NotificationEntity, viewHolder: NotificationsAdapter.NotificationViewHolder) {
        // Mark as read asynchronously
        val dao = AuraDatabase.getDatabase(requireContext()).notificationDao()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            dao.markAsRead(notification.id)
        }
        
        val cardTransitionName = "notification_card_transition_${notification.id}"
        val titleTransitionName = "notification_title_transition_${notification.id}"
        val bodyTransitionName = "notification_body_transition_${notification.id}"
        val dateTransitionName = "notification_date_transition_${notification.id}"
        val iconBgTransitionName = "notification_icon_bg_transition_${notification.id}"
        
        val intent = Intent(requireContext(), QuickActionActivity::class.java).apply {
            putExtra("from_app", true)
            putExtra("transition_card_name", cardTransitionName)
            putExtra("transition_title_name", titleTransitionName)
            putExtra("transition_body_name", bodyTransitionName)
            putExtra("transition_date_name", dateTransitionName)
            putExtra("transition_icon_bg_name", iconBgTransitionName)
            putExtra("transition_name", cardTransitionName)
            putExtra("notification_title", notification.title)
            putExtra("notification_body", notification.body)
            putExtra("timestamp", notification.timestamp)
            
            notification.guestName?.let { putExtra("guest_name", it) }
            notification.type?.let { putExtra("type", it) }
            notification.status?.let { putExtra("status", it) }
            notification.accessType?.let { putExtra("access_type", it) }
            notification.method?.let { putExtra("method", it) }
            notification.uses?.let { putExtra("uses", it) }
            notification.maxUses?.let { putExtra("max_uses", it) }
            notification.activeDays?.let { putExtra("active_days", it) }
            
            // Add extra fields from payload json if needed
            try {
                if (notification.payloadJson.isNotBlank()) {
                    val json = JSONObject(notification.payloadJson)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (!hasExtra(key)) {
                            putExtra(key, json.getString(key))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(),
            androidx.core.util.Pair(viewHolder.cvNotification, cardTransitionName),
            androidx.core.util.Pair(viewHolder.tvTitle, titleTransitionName),
            androidx.core.util.Pair(viewHolder.tvBody, bodyTransitionName),
            androidx.core.util.Pair(viewHolder.tvTime, dateTransitionName),
            androidx.core.util.Pair(viewHolder.flIconContainer, iconBgTransitionName)
        )
        startActivity(intent, options.toBundle())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvNotifications.adapter = null
        _binding = null
    }
}
