package com.mexadev.aura.ui.fcm

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.mexadev.aura.MainActivity
import com.mexadev.aura.R
import com.mexadev.aura.SplashActivity
import com.mexadev.aura.core.session.BiometricHelper
import androidx.core.view.isVisible

class QuickActionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_quick_action)

        setupButtons()
        requestBiometricAuth()
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

        findViewById<TextView>(R.id.tvNotificationTitle).text = title
        findViewById<TextView>(R.id.tvNotificationBody).text = body

        val container = findViewById<GridLayout>(R.id.glDetailsContainer)
        container.removeAllViews()

        val extras = intent.extras ?: return
        
        // We organize the information intelligently
        val mainFields = mutableListOf<Pair<String, Pair<String, Boolean>>>()
        
        // Parse and format known fields
        extras.getString("guest_name")?.takeIf { it.isNotBlank() }?.let { mainFields.add(getString(R.string.quick_action_guest) to Pair(it, false)) }
        extras.getString("uses")?.takeIf { it.isNotBlank() }?.let { mainFields.add("USOS" to Pair(it, false)) }
        
        extras.getString("access_type")?.takeIf { it.isNotBlank() }?.let { mainFields.add(getString(R.string.quick_action_access_type) to Pair(translateAccessType(it), false)) }
        extras.getString("method")?.takeIf { it.isNotBlank() }?.let { mainFields.add(getString(R.string.quick_action_method) to Pair(translateMethod(it), false)) }
        extras.getString("status")?.takeIf { it.isNotBlank() }?.let { mainFields.add(getString(R.string.quick_action_status) to Pair(translateStatus(it), false)) }
        
        // Active days
        val activeDaysStr = extras.getString("active_days")
        if (!activeDaysStr.isNullOrBlank()) {
            val formattedDays = formatActiveDays(activeDaysStr)
            mainFields.add(getString(R.string.quick_action_days) to Pair(formattedDays, false))
        }

        // Add timestamp as 2 columns
        extras.getString("timestamp")?.takeIf { it.isNotBlank() }?.let { 
            mainFields.add(getString(R.string.quick_action_date) to Pair(formatTimestamp(it), true)) 
        }

        // Add remaining unknown or extra fields gracefully (excluding technical/ID fields)
        val excludedKeys = listOf("notification_title", "notification_body", "notification_importance", 
                                  "id", "residence_id", "user_id", "guest_name", "access_type", 
                                  "method", "status", "timestamp", "active_days",
                                  "scanned_code", "code", "body", "type", "title", "uses", "message")
        
        for (key in extras.keySet()) {
            if (key !in excludedKeys) {
                val value = extras.getString(key)
                if (!value.isNullOrBlank()) {
                    mainFields.add(formatKeyName(key) to Pair(value, false))
                }
            }
        }
        
        for ((label, pair) in mainFields) {
            addDetailView(container, label, pair.first, pair.second)
        }

        // Add a nice cascade entrance animation for the card
        val cvDetails = findViewById<View>(R.id.cvDetails)
        cvDetails.alpha = 0f
        cvDetails.translationY = 100f
        cvDetails.scaleX = 0.95f
        cvDetails.scaleY = 0.95f
        cvDetails.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
            .start()

        val ivHeaderIcon = findViewById<View>(R.id.ivHeaderIcon)
        ivHeaderIcon.scaleX = 0f
        ivHeaderIcon.scaleY = 0f
        ivHeaderIcon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setStartDelay(200)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
            .start()

        val clockView = findViewById<View>(R.id.tcHeaderTime)
        clockView.alpha = 0f
        clockView.translationX = 50f
        clockView.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(500)
            .setStartDelay(300)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.0f))
            .start()
    }

    private fun translateAccessType(type: String): String = when(type.lowercase()) {
        "pedestrian" -> getString(R.string.access_type_pedestrian)
        "vehicle" -> getString(R.string.access_type_vehicle)
        else -> type.replaceFirstChar { it.uppercase() }
    }

    private fun translateMethod(method: String): String = when(method.lowercase()) {
        "qr" -> getString(R.string.method_qr)
        "pin" -> getString(R.string.method_pin)
        "tag" -> getString(R.string.method_tag)
        "face" -> getString(R.string.method_face)
        else -> method.replaceFirstChar { it.uppercase() }
    }
    
    private fun translateStatus(status: String): String = when(status.lowercase()) {
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
        if (daysList.containsAll(listOf("1","2","3","4","5"))) {
            if (daysList.size == 5) return getString(R.string.quick_action_weekdays)
        }
        
        val dayNames = mapOf(
            "1" to "Lun", "2" to "Mar", "3" to "Mié", 
            "4" to "Jue", "5" to "Vie", "6" to "Sáb", "7" to "Dom"
        )
        return daysList.mapNotNull { dayNames[it] }.joinToString(", ")
    }

    private fun formatTimestamp(utcTimestamp: String): String {
        return try {
            // Normalizar el timestamp para que sea compatible con ISO-8601
            val cleanTimestamp = utcTimestamp.replace(" ", "T").let { 
                if (!it.endsWith("Z") && !it.contains("+") && it.count { char -> char == '-' } <= 2) {
                    it + "Z" // Aseguramos que termine en Z (UTC) si no trae zona horaria
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
            radius = dpToPx(16).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.aura_white))
            cardElevation = dpToPx(2).toFloat()
            strokeWidth = dpToPx(1)
            strokeColor = ContextCompat.getColor(context, R.color.aura_border_light)
            
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = if (span2Columns) GridLayout.spec(GridLayout.UNDEFINED, 2, 1f) else GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            }
            layoutParams = params
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
        }

        val labelView = TextView(context).apply {
            text = label.uppercase()
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.aura_primary))
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.05f
        }

        val valueView = TextView(context).apply {
            text = value
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.aura_text_primary))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dpToPx(4), 0, 0)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        contentLayout.addView(labelView)
        contentLayout.addView(valueView)
        card.addView(contentLayout)
        
        container.addView(card)

        card.alpha = 0f
        card.scaleX = 0.8f
        card.scaleY = 0.8f
        card.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setStartDelay((container.childCount * 50).toLong())
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .start()
    }

    private fun formatKeyName(key: String): String {
        return key.split("_")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    private fun setupButtons() {
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
