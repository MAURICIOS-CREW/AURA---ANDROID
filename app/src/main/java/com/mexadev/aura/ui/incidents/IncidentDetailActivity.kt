package com.mexadev.aura.ui.incidents

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.data.model.CommentCreateRequest
import com.mexadev.aura.data.model.IncidentDetail
import com.mexadev.aura.data.model.IncidentUpdateRequest
import com.mexadev.aura.databinding.ActivityIncidentDetailBinding
import com.mexadev.aura.ui.common.ConfirmBottomSheetFragment
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * IncidentDetailActivity
 *
 * Muestra el detalle completo de un incidente y su hilo de comentarios.
 *
 * Flujo:
 *  1. Recibe EXTRA_INCIDENT_ID por intent
 *  2. Llama a GET /api/mobile/incidents/{id} para obtener detalle + comentarios
 *  3. Muestra burbujas de comentarios estilo chat
 *  4. Permite añadir comentarios vía POST /api/mobile/incidents/{id}/comments
 *  5. Permite cancelar el incidente vía PATCH si está en estado 'open' o 'viewed'
 *
 * Transición:
 *  La actividad usa overridePendingTransition personalizado para una transición
 *  de "sheet que sube" en lugar de la típica slide horizontal de Android.
 *
 * Principios SOLID:
 *  - SRP: solo orquesta el detalle de UN incidente
 *  - DIP: depende de ApiClient (abstracción), no de implementaciones directas
 */
class IncidentDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INCIDENT_ID   = "extra_incident_id"
        const val EXTRA_INCIDENT_TITLE = "extra_incident_title"
        const val EXTRA_INCIDENT_JSON = "extra_incident_json"

        /** Estados en los que el residente puede cancelar su reporte */
        private val CANCELLABLE_STATUSES = setOf("open", "viewed")
    }

    private lateinit var binding: ActivityIncidentDetailBinding
    private lateinit var commentsAdapter: CommentsAdapter

    private var incidentId: Long = -1L
    private var currentUserId: Long = -1L
    private var currentIncident: IncidentDetail? = null

    private var errorBannerRunnable: Runnable? = null

    // ─────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityIncidentDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Forzar iconos oscuros en la barra de estado (porque nuestro fondo es claro)
        androidx.core.view.WindowCompat.getInsetsController(window, binding.root)
            .isAppearanceLightStatusBars = true

        setupEdgeToEdge()
        setupRecyclerView()
        setupSendButton()
        setupBackButton()
        setupImeListener()

        binding.swipeRefreshLayout.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchIncidentDetail(silent = false)
        }

        val incidentJson = intent.getStringExtra(EXTRA_INCIDENT_JSON)
        if (incidentJson != null) {
            val incident = com.google.gson.Gson().fromJson(incidentJson, com.mexadev.aura.data.model.Incident::class.java)
            incidentId = incident.id
            
            // Construimos un detalle inicial con los datos que ya tenemos
            val initialDetail = IncidentDetail(
                id = incident.id,
                reporterUserId = incident.reporterUserId,
                title = incident.title,
                description = incident.description,
                status = incident.status,
                comments = incident.comments ?: emptyList(),
                createdAt = incident.createdAt,
                updatedAt = incident.updatedAt
            )
            currentIncident = initialDetail
            renderIncidentHeader(initialDetail)
            renderComments(initialDetail)
            
            // Buscamos actualizaciones silenciosamente al fondo
            fetchIncidentDetail(silent = true)
        } else {
            incidentId = intent.getLongExtra(EXTRA_INCIDENT_ID, -1L)
            if (incidentId == -1L) {
                finish()
                return
            }
            fetchIncidentDetail(silent = false)
        }
    }

    override fun finish() {
        currentIncident?.let {
            val intent = android.content.Intent().apply {
                putExtra(EXTRA_INCIDENT_JSON, com.google.gson.Gson().toJson(it))
            }
            setResult(android.app.Activity.RESULT_OK, intent)
        }
        super.finish()
        // Animación de salida: la pantalla baja y la pantalla de fondo recupera escala
        overridePendingTransition(R.anim.anim_scale_fade_in, R.anim.anim_slide_down_exit)
    }

    // ─────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            // Padding superior para la toolbar
            binding.toolbar.setPadding(
                binding.toolbar.paddingLeft,
                systemBars.top,
                binding.toolbar.paddingRight,
                binding.toolbar.paddingBottom
            )

            // Desplazar la barra de input encima del teclado
            val bottomOffset = maxOf(ime.bottom, systemBars.bottom)
            binding.commentInputBar.translationY = -ime.bottom.toFloat()
            
            // Damos suficiente padding para que el último comentario no se tape con la barra de input (aprox 80dp) + un margen extra
            val inputBarHeightApproximation = 96.dp
            binding.rvComments.setPadding(
                binding.rvComments.paddingLeft,
                binding.rvComments.paddingTop,
                binding.rvComments.paddingRight,
                bottomOffset + inputBarHeightApproximation
            )

            // Error banner padding
            binding.errorBanner.setPadding(
                16.dp, systemBars.top + 16.dp, 16.dp, 16.dp
            )

            insets
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true  // Los mensajes nuevos aparecen desde abajo
        }
        binding.rvComments.layoutManager = layoutManager

        // El userId se obtiene de preferencias; si no está disponible se usa -1
        val prefs = PreferencesManager(this)
        currentUserId = prefs.userId

        commentsAdapter = CommentsAdapter(mutableListOf(), currentUserId)
        binding.rvComments.adapter = commentsAdapter
    }

    private fun setupSendButton() {
        binding.btnSendComment.setOnClickListener {
            val content = binding.etComment.text.toString().trim()
            if (content.isEmpty()) {
                binding.etComment.error = "Escribe algo primero"
                return@setOnClickListener
            }
            postComment(content)
        }

        // También permite enviar con la acción del teclado
        binding.etComment.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                val content = binding.etComment.text.toString().trim()
                if (content.isNotEmpty()) postComment(content)
                true
            } else false
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupImeListener() {
        // No es necesario hacer nada extra — el EdgeToEdge listener ya maneja el teclado
    }

    // ─────────────────────────────────────────────────────────────────
    // Network — Detalle
    // ─────────────────────────────────────────────────────────────────

    private fun fetchIncidentDetail(silent: Boolean = false) {
        if (!silent) {
            binding.swipeRefreshLayout.isRefreshing = true
        } else {
            binding.layoutUpdating.visibility = View.VISIBLE
            // Mostrar shimmer de actualización en el estado?
            binding.tvToolbarStatus.animate().alpha(0.5f).setDuration(400).withEndAction {
                binding.tvToolbarStatus.animate().alpha(1f).setDuration(400).start()
            }.start()
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getIncidentDetail(incidentId)
                if (response.isSuccessful) {
                    val detail = response.body()
                    if (detail != null) {
                        currentIncident = detail
                        renderIncidentHeader(detail)
                        renderComments(detail)
                    }
                } else {
                    handleApiError(response.code(), response.errorBody()?.string())
                }
            } catch (e: Exception) {
                if (!silent) {
                    showErrorBanner("Sin conexión a internet o servidor inaccesible.")
                }
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
                binding.layoutUpdating.visibility = View.GONE
            }
        }
    }

    private fun renderIncidentHeader(detail: IncidentDetail) {
        // Garantizamos que el adaptador sepa exactamente cuál es el ID del usuario
        // actual (el residente/reportero) basado en la respuesta del backend
        currentUserId = detail.reporterUserId
        commentsAdapter.currentUserId = currentUserId

        binding.tvToolbarTitle.text = "Reporte #${detail.id}"
        binding.tvIncidentTitle.text = detail.title

        if (!detail.description.isNullOrBlank()) {
            binding.tvIncidentDescription.text = detail.description
            binding.tvIncidentDescription.visibility = View.VISIBLE
        } else {
            binding.tvIncidentDescription.visibility = View.GONE
        }

        binding.tvCreatedAt.text = formatDate(detail.createdAt)

        applyStatusChip(detail.status)

        // Mostrar botón cancelar solo si el estado lo permite
        if (detail.status in CANCELLABLE_STATUSES) {
            binding.btnCancelIncident.visibility = View.VISIBLE
            binding.btnCancelIncident.setOnClickListener {
                ConfirmBottomSheetFragment.newInstance(
                    title = "Cancelar reporte",
                    message = "¿Estás seguro que deseas cancelar este reporte? Esta acción no se puede deshacer.",
                    confirmText = "Sí, cancelar",
                    onConfirm = { cancelIncident() }
                ).show(supportFragmentManager, "CancelIncidentConfirm")
            }
        } else {
            binding.btnCancelIncident.visibility = View.GONE
        }
    }

    private fun renderComments(detail: IncidentDetail) {
        binding.rvComments.visibility = View.VISIBLE

        if (detail.comments.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            commentsAdapter.updateData(detail.comments)
            // Scroll al último comentario
            binding.rvComments.post {
                binding.rvComments.scrollToPosition(commentsAdapter.itemCount - 1)
            }
        }
    }

    /**
     * Aplica colores y texto al chip de status en la toolbar y al encabezado.
     */
    private fun applyStatusChip(status: String) {
        val (label, textColor, bgColor) = statusStyle(status)

        // Chip en toolbar
        binding.tvToolbarStatus.text = label
        binding.tvToolbarStatus.setTextColor(textColor)
        val bgDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24 * resources.displayMetrics.density
            setColor(bgColor)
        }
        binding.tvToolbarStatus.background = bgDrawable
    }

    /**
     * Devuelve (label, textColor, bgColor) para cada estado del incidente.
     */
    private fun statusStyle(status: String): Triple<String, Int, Int> = when (status) {
        "open"       -> Triple("Abierto",      getColor(R.color.aura_warning),      getColor(R.color.aura_warning_light))
        "viewed"     -> Triple("Visto",        getColor(R.color.aura_info),         getColor(R.color.aura_info_light))
        "in_progress"-> Triple("En proceso",   getColor(R.color.aura_primary),      getColor(R.color.aura_primary_surface))
        "attended"   -> Triple("Atendido",     getColor(R.color.aura_success),      getColor(R.color.aura_success_light))
        "cancelled"  -> Triple("Cancelado",    getColor(R.color.aura_text_tertiary),getColor(R.color.aura_surface_variant))
        else         -> Triple(status.replaceFirstChar { it.uppercase() },
                               getColor(R.color.aura_text_secondary),
                               getColor(R.color.aura_surface_variant))
    }

    // ─────────────────────────────────────────────────────────────────
    // Network — Comentarios
    // ─────────────────────────────────────────────────────────────────

    private fun postComment(content: String) {
        setSendLoading(true)
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.addIncidentComment(
                    incidentId, CommentCreateRequest(content)
                )
                if (response.isSuccessful) {
                    val newComment = response.body()
                    if (newComment != null) {
                        binding.etComment.setText("")
                        hideKeyboard()

                        // Ocultar estado vacío si era el primer comentario
                        if (binding.layoutEmpty.visibility == View.VISIBLE) {
                            binding.layoutEmpty.visibility = View.GONE
                        }

                        commentsAdapter.addComment(newComment)
                        binding.rvComments.smoothScrollToPosition(commentsAdapter.itemCount - 1)
                        
                        currentIncident = currentIncident?.copy(
                            comments = currentIncident?.comments.orEmpty() + newComment
                        )
                    }
                } else {
                    handleApiError(response.code(), response.errorBody()?.string())
                }
            } catch (e: Exception) {
                showErrorBanner("Sin conexión a internet o servidor inaccesible.")
            } finally {
                setSendLoading(false)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Network — Cancelar incidente
    // ─────────────────────────────────────────────────────────────────

    private fun cancelIncident() {
        binding.btnCancelIncident.isEnabled = false
        binding.pbCancel.visibility = View.VISIBLE
        binding.tvCancelText.setTextColor(getColor(R.color.aura_text_tertiary))

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.updateIncident(
                    incidentId, IncidentUpdateRequest("cancelled")
                )
                if (response.isSuccessful) {
                    // Actualizar chip de estado
                    currentIncident = currentIncident?.copy(status = "cancelled")
                    applyStatusChip("cancelled")
                    binding.btnCancelIncident.visibility = View.GONE
                } else {
                    handleApiError(response.code(), response.errorBody()?.string())
                    resetCancelButton()
                }
            } catch (e: Exception) {
                showErrorBanner("Sin conexión a internet o servidor inaccesible.")
                resetCancelButton()
            }
        }
    }

    private fun resetCancelButton() {
        binding.btnCancelIncident.isEnabled = true
        binding.pbCancel.visibility = View.GONE
        binding.tvCancelText.setTextColor(getColor(R.color.aura_error))
    }

    // ─────────────────────────────────────────────────────────────────
    // UI helpers
    // ─────────────────────────────────────────────────────────────────

    private fun setSendLoading(loading: Boolean) {
        binding.ivSendIcon.visibility = if (loading) View.GONE else View.VISIBLE
        binding.pbSend.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSendComment.isEnabled = !loading
        binding.etComment.isEnabled = !loading
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun formatDate(isoDate: String?): String {
        if (isoDate == null) return ""
        return try {
            val inputFormat  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
            val outputFormat = SimpleDateFormat("d 'de' MMMM, yyyy", Locale("es", "MX"))
            val date = inputFormat.parse(isoDate) ?: return ""
            "Reportado el ${outputFormat.format(date)}"
        } catch (e: Exception) { "" }
    }

    // ─────────────────────────────────────────────────────────────────
    // Error Handling & Banner (mismo patrón que VehiclesFragment)
    // ─────────────────────────────────────────────────────────────────

    private fun handleApiError(code: Int, errorBody: String?) {
        var customMessage: String? = null
        try {
            if (!errorBody.isNullOrEmpty()) {
                val json = org.json.JSONObject(errorBody)
                customMessage = json.optString("message").ifEmpty { null }
                    ?: json.optString("error").ifEmpty { null }
            }
        } catch (_: Exception) {}

        val message = when (code) {
            400  -> customMessage ?: "El incidente no se puede cancelar en su estado actual."
            401  -> "Tu sesión ha expirado o no tienes acceso."
            403  -> customMessage ?: "No tienes permiso para realizar esta acción."
            404  -> customMessage ?: "El incidente no fue encontrado."
            422  -> customMessage ?: "Datos inválidos. Verifica e intenta de nuevo."
            500  -> "Error interno en el servidor."
            else -> customMessage ?: "Ocurrió un error inesperado ($code)."
        }
        showErrorBanner(message)
    }

    private fun showErrorBanner(message: String) {
        binding.tvErrorBannerMessage.text = message

        errorBannerRunnable?.let { binding.errorBanner.removeCallbacks(it) }

        if (binding.errorBanner.visibility != View.VISIBLE) {
            binding.errorBanner.alpha = 0f
            binding.errorBanner.visibility = View.VISIBLE
            binding.errorBanner.post {
                val height = binding.errorBanner.height.toFloat()
                binding.errorBanner.translationY = -height
                binding.errorBanner.alpha = 1f
                binding.errorBanner.animate()
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(1.0f))
                    .withEndAction { scheduleHideBanner() }
                    .start()
            }
        } else {
            binding.errorBanner.animate().cancel()
            binding.errorBanner.translationY = 0f
            scheduleHideBanner()
        }
    }

    private fun scheduleHideBanner() {
        errorBannerRunnable = Runnable { hideErrorBanner() }
        binding.errorBanner.postDelayed(errorBannerRunnable, 4000)
    }

    private fun hideErrorBanner() {
        val height = binding.errorBanner.height.toFloat()
        binding.errorBanner.animate()
            .translationY(-height)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { binding.errorBanner.visibility = View.GONE }
            .start()
    }
}
