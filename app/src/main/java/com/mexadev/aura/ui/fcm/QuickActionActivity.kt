package com.mexadev.aura.ui.fcm

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.mexadev.aura.R
import com.mexadev.aura.SplashActivity
import com.mexadev.aura.core.session.BiometricHelper
import java.util.Locale

class QuickActionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
        
        val transition = android.transition.TransitionSet().apply {
            addTransition(android.transition.ChangeBounds())
            addTransition(android.transition.ChangeTransform())
            addTransition(android.transition.ChangeImageTransform())
            duration = 380
            interpolator = FastOutSlowInInterpolator()
        }
        window.sharedElementEnterTransition = transition
        window.sharedElementReturnTransition = transition

        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_quick_action)

        val cardTransName = intent.getStringExtra("transition_card_name") ?: intent.getStringExtra("transition_name")
        val titleTransName = intent.getStringExtra("transition_title_name")
        val bodyTransName = intent.getStringExtra("transition_body_name")
        val dateTransName = intent.getStringExtra("transition_date_name")
        val iconBgTransName = intent.getStringExtra("transition_icon_bg_name")

        cardTransName?.let { ViewCompat.setTransitionName(findViewById(R.id.cvHeroCard), it) }
        titleTransName?.let { ViewCompat.setTransitionName(findViewById(R.id.tvNotificationTitle), it) }
        bodyTransName?.let { ViewCompat.setTransitionName(findViewById(R.id.tvNotificationBody), it) }
        dateTransName?.let { ViewCompat.setTransitionName(findViewById(R.id.tvNotificationDate), it) }
        iconBgTransName?.let { ViewCompat.setTransitionName(findViewById(R.id.flIconContainer), it) }

        setupButtons()

        val fromApp = intent.getBooleanExtra("from_app", false)
        if (fromApp) {
            findViewById<View>(R.id.flBiometricOverlay).visibility = View.GONE
            findViewById<View>(R.id.btnStartApp).visibility = View.GONE
            findViewById<View>(R.id.btnExit).visibility = View.GONE

            // Adjust card bottom margin to fill space nicely when opened inside app
            val cvDetails = findViewById<View>(R.id.cvDetails)
            val params = cvDetails.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            cvDetails.layoutParams = params

            populateData()
        } else {
            requestBiometricAuth()
        }
    }

    private fun requestBiometricAuth() {
        val overlay = findViewById<View>(R.id.flBiometricOverlay)
        val tvRetry = findViewById<View>(R.id.tvRetryBiometric)

        overlay.visibility = View.VISIBLE
        tvRetry.visibility = View.GONE

        overlay.setOnClickListener {
            if (tvRetry.isVisible) {
                requestBiometricAuth()
            }
        }

        BiometricHelper.showBiometricPrompt(
            activity = this,
            title = getString(R.string.biometric_prompt_title),
            subtitle = getString(R.string.biometric_prompt_subtitle),
            onSuccess = {
                overlay.visibility = View.GONE
                populateData()
            },
            onError = {
                tvRetry.visibility = View.VISIBLE
            },
            onCancel = {
                tvRetry.visibility = View.VISIBLE
            }
        )
    }

    private fun populateData() {
        val title = intent.getStringExtra("notification_title") ?: getString(R.string.quick_action_title_fallback)
        val body = intent.getStringExtra("notification_body") ?: getString(R.string.quick_action_body_fallback)
        val status = intent.getStringExtra("status")
        val type = intent.getStringExtra("type")
        val accessType = intent.getStringExtra("access_type")

        findViewById<TextView>(R.id.tvHeaderTitle).text = getString(R.string.quick_action_header_title)
        findViewById<TextView>(R.id.tvNotificationTitle).text = title
        findViewById<TextView>(R.id.tvNotificationBody).text = body

        val timestamp = intent.getStringExtra("timestamp")
        if (!timestamp.isNullOrBlank()) {
            findViewById<TextView>(R.id.tvNotificationDate).text = formatRelativeTimestamp(timestamp)
        } else {
            findViewById<TextView>(R.id.tvNotificationDate).text = ""
        }

        // Apply visual styling to category badge & icon based on notification type/status
        styleHeaderBadgeAndIcon(status, type, accessType)

        val container = findViewById<GridLayout>(R.id.glDetailsContainer)
        container.removeAllViews()

        val extras = intent.extras ?: return

        // Organize details nicely
        val mainFields = mutableListOf<Pair<String, Pair<String, Boolean>>>()

        // Parse and format known fields
        extras.getString("guest_name")?.takeIf { it.isNotBlank() }?.let {
            mainFields.add(getString(R.string.quick_action_guest) to Pair(it, false))
        }
        extras.getString("uses")?.takeIf { it.isNotBlank() }?.let {
            mainFields.add(getString(R.string.quick_action_uses) to Pair(it, false))
        }
        extras.getString("access_type")?.takeIf { it.isNotBlank() }?.let {
            mainFields.add(getString(R.string.quick_action_access_type) to Pair(translateAccessType(it), false))
        }
        extras.getString("method")?.takeIf { it.isNotBlank() }?.let {
            mainFields.add(getString(R.string.quick_action_method) to Pair(translateMethod(it), false))
        }
        extras.getString("status")?.takeIf { it.isNotBlank() }?.let {
            mainFields.add(getString(R.string.quick_action_status) to Pair(translateStatus(it), false))
        }

        // Active days
        val activeDaysStr = extras.getString("active_days")
        if (!activeDaysStr.isNullOrBlank()) {
            val formattedDays = formatActiveDays(activeDaysStr)
            mainFields.add(getString(R.string.quick_action_days) to Pair(formattedDays, false))
        }

        // Add timestamp full format as 2 columns
        extras.getString("timestamp")?.takeIf { it.isNotBlank() }?.let {
            mainFields.add(getString(R.string.quick_action_date) to Pair(formatTimestamp(it), true))
        }

        // Technical / Internal keys to exclude (including transition_name so it NEVER shows in the details UI)
        val excludedKeys = setOf(
            "transition_name", "from_app", "notification_title", "notification_body",
            "notification_importance", "id", "residence_id", "user_id", "guest_name",
            "access_type", "method", "status", "timestamp", "active_days",
            "scanned_code", "code", "body", "type", "title", "uses", "max_uses",
            "message", "flags", "intent"
        )

        for (key in extras.keySet()) {
            val lowerKey = key.lowercase()
            if (key !in excludedKeys && !lowerKey.contains("transition") && !lowerKey.contains("intent")) {
                val value = extras.getString(key)
                if (!value.isNullOrBlank()) {
                    mainFields.add(formatKeyName(key) to Pair(value, false))
                }
            }
        }

        for ((label, pair) in mainFields) {
            addDetailView(container, label, pair.first, pair.second)
        }

        // Subtle cascade entrance animation for details card
        val cvDetails = findViewById<View>(R.id.cvDetails)
        cvDetails.alpha = 0f
        cvDetails.translationY = 40f
        cvDetails.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(450)
            .setStartDelay(150)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    private fun styleHeaderBadgeAndIcon(status: String?, type: String?, accessType: String?) {
        val tvBadge = findViewById<TextView>(R.id.tvCategoryBadge)
        val ivIcon = findViewById<ImageView>(R.id.ivHeaderIcon)

        // Status / Category Badge
        when {
            status?.equals("granted", ignoreCase = true) == true -> {
                tvBadge.text = getString(R.string.quick_action_badge_granted)
                tvBadge.setBackgroundResource(R.drawable.bg_pill_success)
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.aura_success))
            }
            status?.equals("denied", ignoreCase = true) == true -> {
                tvBadge.text = getString(R.string.quick_action_badge_denied)
                tvBadge.setBackgroundResource(R.drawable.bg_pill_error)
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.aura_error))
            }
            type?.contains("incident", ignoreCase = true) == true -> {
                tvBadge.text = getString(R.string.quick_action_badge_incident)
                tvBadge.setBackgroundResource(R.drawable.bg_pill_warning)
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.aura_warning))
            }
            else -> {
                tvBadge.text = getString(R.string.quick_action_badge_general)
                tvBadge.setBackgroundResource(R.drawable.bg_pill_info)
                tvBadge.setTextColor(ContextCompat.getColor(this, R.color.aura_primary))
            }
        }

        // Descriptive Access / Incident / Notice Icon
        when {
            type?.contains("incident", ignoreCase = true) == true -> {
                ivIcon.setImageResource(R.drawable.ic_warning)
                ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.aura_warning))
            }
            accessType?.equals("vehicle", ignoreCase = true) == true || type?.contains("vehicle", ignoreCase = true) == true -> {
                ivIcon.setImageResource(R.drawable.ic_car)
                ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.aura_primary))
            }
            accessType?.equals("pedestrian", ignoreCase = true) == true || type?.contains("access", ignoreCase = true) == true || status != null || accessType != null -> {
                ivIcon.setImageResource(R.drawable.ic_door_access)
                ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.aura_primary))
            }
            else -> {
                ivIcon.setImageResource(R.drawable.ic_nav_notifications_filled)
                ivIcon.setColorFilter(ContextCompat.getColor(this, R.color.aura_primary))
            }
        }
    }

    private fun translateAccessType(type: String): String = when (type.lowercase()) {
        "pedestrian" -> getString(R.string.access_type_pedestrian)
        "vehicle" -> getString(R.string.access_type_vehicle)
        else -> type.replaceFirstChar { it.uppercase() }
    }

    private fun translateMethod(method: String): String = when (method.lowercase()) {
        "qr" -> getString(R.string.method_qr)
        "pin" -> getString(R.string.method_pin)
        "tag" -> getString(R.string.method_tag)
        "face" -> getString(R.string.method_face)
        else -> method.replaceFirstChar { it.uppercase() }
    }

    private fun translateStatus(status: String): String = when (status.lowercase()) {
        "granted" -> getString(R.string.status_granted)
        "denied" -> getString(R.string.status_denied)
        "pending" -> getString(R.string.status_pending)
        else -> status.replaceFirstChar { it.uppercase() }
    }

    private fun formatActiveDays(daysArrayStr: String): String {
        val cleaned = daysArrayStr.replace("[", "").replace("]", "").replace(" ", "")
        if (cleaned.isEmpty()) return getString(R.string.quick_action_daily)

        val daysList = cleaned.split(",")
        if (daysList.size == 7) return getString(R.string.quick_action_daily)
        if (daysList.containsAll(listOf("1", "2", "3", "4", "5"))) {
            if (daysList.size == 5) return getString(R.string.quick_action_weekdays)
        }

        val dayNames = mapOf(
            "1" to getString(R.string.quick_action_day_mon),
            "2" to getString(R.string.quick_action_day_tue),
            "3" to getString(R.string.quick_action_day_wed),
            "4" to getString(R.string.quick_action_day_thu),
            "5" to getString(R.string.quick_action_day_fri),
            "6" to getString(R.string.quick_action_day_sat),
            "7" to getString(R.string.quick_action_day_sun)
        )
        return daysList.mapNotNull { dayNames[it] }.joinToString(", ")
    }

    private fun formatRelativeTimestamp(utcTimestamp: String): String {
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

    private fun formatTimestamp(utcTimestamp: String): String {
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
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.getDefault())
            zonedDateTime.format(formatter)
        } catch (e: Exception) {
            utcTimestamp
        }
    }

    private fun addDetailView(container: GridLayout, label: String, value: String, span2Columns: Boolean = false) {
        val context = container.context
        val dpToPx = { dp: Int -> (dp * context.resources.displayMetrics.density).toInt() }

        val card = com.google.android.material.card.MaterialCardView(context).apply {
            radius = dpToPx(12).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.aura_surface_variant))
            cardElevation = 0f
            strokeWidth = dpToPx(1)
            strokeColor = ContextCompat.getColor(context, R.color.aura_border_light)

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = if (span2Columns) GridLayout.spec(GridLayout.UNDEFINED, 2, 1f) else GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            }
            layoutParams = params
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
        }

        val labelView = TextView(context).apply {
            text = label.uppercase()
            textSize = 10.5f
            setTextColor(ContextCompat.getColor(context, R.color.aura_primary))
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.04f
        }

        val valueView = TextView(context).apply {
            text = value
            textSize = 14.5f
            setTextColor(ContextCompat.getColor(context, R.color.aura_text_primary))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dpToPx(3), 0, 0)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        contentLayout.addView(labelView)
        contentLayout.addView(valueView)
        card.addView(contentLayout)

        container.addView(card)

        card.alpha = 0f
        card.translationY = 20f
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay((container.childCount * 40).toLong())
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    private fun formatKeyName(key: String): String {
        return key.split("_")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun setupButtons() {
        findViewById<View>(R.id.btnBack).setOnClickListener {
            finishAfterTransition()
        }

        findViewById<View>(R.id.btnStartApp).setOnClickListener {
            val intent = Intent(this, SplashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        findViewById<View>(R.id.btnExit).setOnClickListener {
            finishAffinity()
        }
    }
}
