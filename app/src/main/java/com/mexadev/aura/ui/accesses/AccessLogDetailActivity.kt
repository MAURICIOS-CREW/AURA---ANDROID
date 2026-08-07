package com.mexadev.aura.ui.accesses

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.toColorInt
import com.google.gson.Gson
import com.mexadev.aura.R
import com.mexadev.aura.data.model.*
import com.mexadev.aura.databinding.ActivityAccessLogDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * AccessLogDetailActivity
 *
 * Muestra el detalle completo de un registro de acceso (AccessLog).
 * Recibe el objeto serializado como JSON en el extra EXTRA_LOG_JSON.
 * No hace llamadas de red adicionales: toda la info se pasa desde la lista.
 */
class AccessLogDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_JSON = "extra_log_json"
    }

    private lateinit var binding: ActivityAccessLogDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)

        val transition = android.transition.TransitionSet().apply {
            addTransition(android.transition.ChangeBounds())
            addTransition(android.transition.ChangeTransform())
            addTransition(android.transition.ChangeImageTransform())
            duration = 380
            interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
        }
        window.sharedElementEnterTransition = transition
        window.sharedElementReturnTransition = transition

        super.onCreate(savedInstanceState)
        binding = ActivityAccessLogDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val cardTransName = intent.getStringExtra("transition_card_name")
        val nameTransName = intent.getStringExtra("transition_name_name")
        val iconTransName = intent.getStringExtra("transition_icon_name")
        val statusTransName = intent.getStringExtra("transition_status_name")

        cardTransName?.let { ViewCompat.setTransitionName(binding.cardHero, it) }
        nameTransName?.let { ViewCompat.setTransitionName(binding.tvHeroGuestName, it) }
        iconTransName?.let { ViewCompat.setTransitionName(binding.ivHeroIcon, it) }
        statusTransName?.let { ViewCompat.setTransitionName(binding.tvToolbarStatus, it) }

        // Edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                sys.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
            )
            insets
        }

        binding.btnBack.setOnClickListener { finishAfterTransition() }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAfterTransition()
            }
        })

        val json = intent.getStringExtra(EXTRA_LOG_JSON)
        if (json == null) {
            finishAfterTransition()
            return
        }

        val log = try {
            Gson().fromJson(json, AccessLog::class.java)
        } catch (_: Exception) {
            finish()
            return
        }

        // Ocultar skeleton y mostrar contenido
        binding.layoutDetailSkeleton.visibility = View.GONE
        binding.scrollView.visibility = View.VISIBLE

        bindLog(log)
    }

    private fun bindLog(log: AccessLog) {
        val ctx = this

        // ── Toolbar ──────────────────────────────────────────────────
        val (statusLabel, textColorRes, bgColorRes) = when (log.status.lowercase()) {
            "granted" -> Triple(
                getString(R.string.access_log_status_granted),
                R.color.aura_success,
                R.color.aura_success_light
            )
            "denied" -> Triple(
                getString(R.string.access_log_status_denied),
                R.color.aura_error,
                R.color.aura_error_light
            )
            else -> Triple(
                log.status.replaceFirstChar { it.uppercase() },
                R.color.aura_text_tertiary,
                R.color.aura_surface_variant
            )
        }

        binding.tvToolbarStatus.text = statusLabel
        binding.tvToolbarStatus.setTextColor(ContextCompat.getColor(ctx, textColorRes))
        binding.tvToolbarStatus.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24 * resources.displayMetrics.density
            setColor(ContextCompat.getColor(ctx, bgColorRes))
        }

        // ── Hero card ─────────────────────────────────────────────────
        binding.tvHeroGuestName.text = log.getDisplayTitle()
        binding.tvHeroResidence.text = log.getFormattedResidence()

        // Icono según tipo de acceso (vehículo -> ic_car, QR -> ic_qr_code)
        val iconRes = when {
            log.isVehicleAccess() -> R.drawable.ic_car
            log.isQrAccess() -> R.drawable.ic_qr_code
            log.accessType.equals("facial", ignoreCase = true) -> R.drawable.ic_profile
            else -> R.drawable.ic_qr_code
        }
        binding.ivHeroIcon.setImageResource(iconRes)
        binding.ivHeroIcon.setColorFilter(ContextCompat.getColor(ctx, textColorRes))
        binding.heroIconContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20 * resources.displayMetrics.density
            setColor(ContextCompat.getColor(ctx, bgColorRes))
        }

        // ── Info del evento ───────────────────────────────────────────
        binding.tvDetailTimestamp.text = formatFullTimestamp(log.timestamp)

        val accessTypeLabel = buildString {
            append(when {
                log.isVehicleAccess() -> "Acceso Vehicular"
                log.isQrAccess() -> "Código QR"
                log.accessType.equals("facial", ignoreCase = true) -> "Reconocimiento Facial"
                log.accessType.equals("pin", ignoreCase = true) -> "PIN"
                else -> log.accessType.replaceFirstChar { it.uppercase() }
            })
            append(" · ")
            append(when (log.method.lowercase()) {
                "scan" -> "Escaneo"
                "manual" -> "Manual"
                "auto", "license_plate" -> "Automático (LPR)"
                else -> log.method.replaceFirstChar { it.uppercase() }
            })
        }
        binding.tvDetailAccessType.text = accessTypeLabel

        binding.tvDetailDevice.text = log.deviceIdentifier?.takeIf { it.isNotBlank() } ?: "–"
        binding.tvDetailMessage.text = log.message?.takeIf { it.isNotBlank() } ?: "–"

        // ── Información del Vehículo (si aplica) ─────────────────────
        val veh = log.vehicle
        if (veh != null || log.isVehicleAccess()) {
            binding.tvSectionVehicle.visibility = View.VISIBLE
            binding.cardInfoVehicle.visibility  = View.VISIBLE

            // 1. Placas
            val plate = veh?.plate?.takeIf { it.isNotBlank() }
                ?: log.scannedCode?.takeIf { it.isNotBlank() }
                ?: "No especificada"
            binding.tvDetailVehiclePlate.text = plate

            // 2. Marca
            val brand = veh?.brand?.takeIf { it.isNotBlank() } ?: "Sin marca especificada"
            binding.tvDetailVehicleBrand.text = brand

            // 3. Color procesado
            val rawColor = veh?.color?.trim()
            if (!rawColor.isNullOrEmpty()) {
                val (colorLabel, parsedColorInt) = parseVehicleColor(rawColor)
                binding.tvDetailVehicleColor.text = colorLabel

                if (parsedColorInt != null) {
                    binding.vVehicleColorDot.visibility = View.VISIBLE
                    binding.vVehicleColorDot.backgroundTintList = android.content.res.ColorStateList.valueOf(parsedColorInt)
                } else {
                    binding.vVehicleColorDot.visibility = View.GONE
                }
            } else {
                binding.tvDetailVehicleColor.text = getString(R.string.vehicle_no_color)
                binding.vVehicleColorDot.visibility = View.GONE
            }
        }

        // ── Código de acceso (opcional) ───────────────────────────────
        val code = log.accessCode
        if (code != null) {
            binding.tvSectionCode.visibility = View.VISIBLE
            binding.cardInfoCode.visibility  = View.VISIBLE

            binding.tvDetailCodeGuest.text = code.guestName?.takeIf { it.isNotBlank() } ?: "Sin nombre"

            val codeTypeLabel = when (code.type?.lowercase()) {
                "custom"    -> "Personalizado"
                "permanent" -> "Permanente"
                "temporary" -> "Temporal"
                else        -> code.type?.replaceFirstChar { it.uppercase() } ?: "–"
            }
            binding.tvDetailCodeType.text = codeTypeLabel

            val isActive = code.isActive ?: false
            binding.tvDetailCodeActive.text = if (isActive) "Activo" else "Inactivo"
            val activeTxt = if (isActive) R.color.aura_success else R.color.aura_text_tertiary
            val activeBg  = if (isActive) R.color.aura_success_light else R.color.aura_surface_variant
            binding.tvDetailCodeActive.setTextColor(ContextCompat.getColor(ctx, activeTxt))
            binding.tvDetailCodeActive.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24 * resources.displayMetrics.density
                setColor(ContextCompat.getColor(ctx, activeBg))
            }
        }
    }

    /**
     * Formatea "yyyy-MM-dd HH:mm:ss" → "dd de MMM de yyyy, HH:mm"
     */
    private fun formatFullTimestamp(raw: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(raw) ?: return raw
            val outSdf = SimpleDateFormat("dd 'de' MMM 'de' yyyy, HH:mm", Locale.Builder().setLanguage("es").setRegion("MX").build())
            outSdf.format(date)
        } catch (_: Exception) {
            raw
        }
    }

    /**
     * Procesa la cadena de color (soporta hexadecimal ej. "#FF0000", "000000" o texto ej. "Negro").
     * Devuelve Pair(TextoProcesado, ColorIntOpcional)
     */
    private fun parseVehicleColor(raw: String): Pair<String, Int?> {
        val clean = raw.trim()

        val hexCandidate = if (clean.startsWith("#")) clean else "#$clean"
        val parsedInt = try {
            hexCandidate.toColorInt()
        } catch (_: Exception) {
            null
        }

        if (parsedInt != null) {
            val knownName = when (hexCandidate.lowercase()) {
                "#000000", "#000" -> "Negro"
                "#ffffff", "#fff" -> "Blanco"
                "#f44336", "#ff0000", "#e53935", "#d32f2f" -> "Rojo"
                "#2196f3", "#1976d2", "#0000ff", "#0d47a1" -> "Azul"
                "#4caf50", "#388e3c", "#008000" -> "Verde"
                "#ff9800", "#f57c00" -> "Naranja"
                "#9c27b0", "#7b1fa2" -> "Morado"
                "#c0c0c0", "#9e9e9e", "#757575" -> "Gris"
                "#ffd700", "#ffeb3b", "#fbc02d" -> "Dorado / Amarillo"
                else -> "Color personalizado"
            }
            return Pair(knownName, parsedInt)
        }

        val colorFromText = when (clean.lowercase()) {
            "negro" -> android.graphics.Color.BLACK
            "blanco" -> android.graphics.Color.WHITE
            "rojo" -> "#F44336".toColorInt()
            "azul" -> "#2196F3".toColorInt()
            "verde" -> "#4CAF50".toColorInt()
            "gris", "plateado", "plata" -> "#9E9E9E".toColorInt()
            "amarillo", "dorado" -> "#FFD700".toColorInt()
            "naranja" -> "#FF9800".toColorInt()
            "morado", "púrpura", "purpura" -> "#9C27B0".toColorInt()
            "café", "cafe", "marrón", "marron" -> "#795548".toColorInt()
            else -> null
        }

        return Pair(clean.replaceFirstChar { it.uppercase() }, colorFromText)
    }
}
