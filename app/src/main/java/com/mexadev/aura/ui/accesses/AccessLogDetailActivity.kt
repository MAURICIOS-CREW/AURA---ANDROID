package com.mexadev.aura.ui.accesses

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.gson.Gson
import com.mexadev.aura.R
import com.mexadev.aura.data.model.AccessLog
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
        super.onCreate(savedInstanceState)
        binding = ActivityAccessLogDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        binding.btnBack.setOnClickListener { finish() }

        val json = intent.getStringExtra(EXTRA_LOG_JSON)
        if (json == null) {
            finish()
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
        val guestLabel = log.accessCode?.guestName?.takeIf { it.isNotBlank() }
            ?: when (log.accessType.lowercase()) {
                "qr"      -> "Acceso QR"
                "vehicle" -> "Acceso vehicular"
                "facial"  -> "Reconocimiento facial"
                "pin"     -> "Acceso PIN"
                else      -> log.accessType.replaceFirstChar { it.uppercase() }
            }
        binding.tvHeroGuestName.text = guestLabel

        val residenceName = log.residence?.name?.takeIf { it.isNotBlank() }
            ?: "Residencia #${log.residenceId ?: "–"}"
        binding.tvHeroResidence.text = residenceName

        // Icono según status
        val iconRes = if (log.status.lowercase() == "granted") R.drawable.ic_check else R.drawable.ic_warning
        binding.ivHeroIcon.setImageResource(iconRes)
        binding.ivHeroIcon.setColorFilter(ContextCompat.getColor(ctx, textColorRes))

        // ── Info del evento ───────────────────────────────────────────
        binding.tvDetailTimestamp.text = formatFullTimestamp(log.timestamp)

        val accessTypeLabel = buildString {
            append(when (log.accessType.lowercase()) {
                "qr"      -> "Código QR"
                "vehicle" -> "Vehicular"
                "facial"  -> "Reconocimiento facial"
                "pin"     -> "PIN"
                else      -> log.accessType.replaceFirstChar { it.uppercase() }
            })
            append(" · ")
            append(when (log.method.lowercase()) {
                "scan"   -> "Escaneo"
                "manual" -> "Manual"
                "auto"   -> "Automático"
                else     -> log.method.replaceFirstChar { it.uppercase() }
            })
        }
        binding.tvDetailAccessType.text = accessTypeLabel

        binding.tvDetailDevice.text = log.deviceIdentifier?.takeIf { it.isNotBlank() } ?: "–"
        binding.tvDetailMessage.text = log.message?.takeIf { it.isNotBlank() } ?: "–"

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
}
