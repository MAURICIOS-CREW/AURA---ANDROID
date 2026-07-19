package com.mexadev.aura.ui.incidents

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.data.model.Incident
import com.mexadev.aura.data.model.IncidentCreateRequest
import com.mexadev.aura.databinding.FragmentIncidentsBinding
import com.mexadev.aura.ui.common.ConfirmBottomSheetFragment
import kotlinx.coroutines.launch

/**
 * IncidentsFragment
 *
 * Pantalla de gestión de incidentes del residente.
 *
 * Flujo de animaciones:
 *  1. FAB pulsado → FAB hace shrink+fade-out → adapter.addFormCard()
 *     → card aparece con scale-up desde el punto del FAB
 *  2. Formulario enviado → card se transforma en card de incidente
 *  3. Card de incidente pulsada → abre IncidentDetailActivity con transición
 *     "sheet que sube" (no la típica horizontal de Android)
 *
 * Principios SOLID:
 *  - SRP: Fragment solo orquesta UI; lógica de negocio en coroutines/lambdas
 *  - OCP: extensible con nuevos estados sin modificar el Fragment
 *  - DIP: depende de ApiClient y PreferencesManager (abstracciones)
 */
class IncidentsFragment : Fragment() {

    private var _binding: FragmentIncidentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: IncidentsAdapter
    private var incidentsList = mutableListOf<Incident>()
    private var hasFormOpen = false

    // ─────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIncidentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (hasFormOpen) {
                closeForm(askConfirm = true)
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.transitionName = "shared_card_transition_incidents"

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets  = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.detailHeader.setPadding(
                binding.detailHeader.paddingLeft,
                systemBars.top,
                binding.detailHeader.paddingRight,
                binding.detailHeader.paddingBottom
            )

            val bottomInset = kotlin.math.max(imeInsets.bottom, systemBars.bottom)
            binding.root.setPadding(
                binding.root.paddingLeft,
                binding.root.paddingTop,
                binding.root.paddingRight,
                bottomInset
            )

            val basePadding = (16 * resources.displayMetrics.density).toInt()
            binding.errorBanner.setPadding(
                basePadding,
                systemBars.top + basePadding,
                basePadding,
                basePadding
            )

            insets
        }

        binding.btnBack.setOnClickListener { handleBackPress() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()

        showSkeletonLoaders()
        fetchIncidents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        binding.rvIncidents.layoutManager = LinearLayoutManager(requireContext())

        adapter = IncidentsAdapter(
            items = mutableListOf(),
            recyclerView = binding.rvIncidents,
            onSubmit = { title, description, setLoading ->
                createIncident(title, description, setLoading)
            },
            onFormMinimize = { hasContent ->
                closeForm(askConfirm = hasContent)
            },
            onIncidentClick = { incident, cardView ->
                openIncidentDetail(incident, cardView)
            }
        )

        binding.rvIncidents.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefreshLayout.setOnRefreshListener { fetchIncidents() }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            if (hasFormOpen) return@setOnClickListener

            val fabLoc = IntArray(2)
            binding.fabAdd.getLocationInWindow(fabLoc)
            val fabCenterX = fabLoc[0] + binding.fabAdd.width  / 2
            val fabCenterY = fabLoc[1] + binding.fabAdd.height / 2

            animateFabOut {
                hasFormOpen = true
                adapter.addFormCard(fabCenterX, fabCenterY)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Form
    // ─────────────────────────────────────────────────────────────────

    private fun closeForm(askConfirm: Boolean) {
        if (askConfirm) {
            ConfirmBottomSheetFragment.newInstance(
                title       = "Cambios sin guardar",
                message     = "¿Deseas salir y descartar el reporte?",
                confirmText = "Descartar",
                onConfirm   = {
                    hasFormOpen = false
                    adapter.removeFormCard()
                    showFab()
                }
            ).show(childFragmentManager, "DiscardIncidentConfirm")
        } else {
            hasFormOpen = false
            adapter.removeFormCard()
            showFab()
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────

    private val detailLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val json = result.data?.getStringExtra(IncidentDetailActivity.EXTRA_INCIDENT_JSON)
            if (json != null) {
                try {
                    val updatedIncident = com.google.gson.Gson().fromJson(json, Incident::class.java)
                    val indexInList = incidentsList.indexOfFirst { it.id == updatedIncident.id }
                    if (indexInList != -1) {
                        incidentsList[indexInList] = updatedIncident
                    }
                    adapter.updateIncident(updatedIncident)
                } catch (e: Exception) {
                    // Ignorar error de parsing
                }
            }
        }
        // Al regresar del detalle, actualizamos silenciosamente la lista
        fetchIncidentsSilent()
    }

    private fun openIncidentDetail(incident: Incident, cardView: View) {
        val intent = Intent(requireContext(), IncidentDetailActivity::class.java).apply {
            putExtra(IncidentDetailActivity.EXTRA_INCIDENT_JSON, com.google.gson.Gson().toJson(incident))
        }
        detailLauncher.launch(intent)
        requireActivity().overridePendingTransition(
            R.anim.anim_slide_up_enter,
            R.anim.anim_scale_fade_out
        )
    }

    private fun fetchIncidentsSilent() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getIncidents()
                if (response.isSuccessful) {
                    val incidents = response.body() ?: emptyList()
                    updateList(incidents)
                }
            } catch (e: Exception) {
                // Silencioso
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // FAB animations (idénticas a VehiclesFragment)
    // ─────────────────────────────────────────────────────────────────

    private fun animateFabOut(onEnd: () -> Unit) {
        binding.fabAdd.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                binding.fabAdd.visibility = View.INVISIBLE
                onEnd()
            }
            .start()
    }

    private fun showFab() {
        if (binding.fabAdd.visibility == View.VISIBLE &&
            binding.fabAdd.scaleX == 1f) return

        binding.fabAdd.pivotX = binding.fabAdd.width  / 2f
        binding.fabAdd.pivotY = binding.fabAdd.height / 2f
        binding.fabAdd.scaleX = 0f
        binding.fabAdd.scaleY = 0f
        binding.fabAdd.alpha  = 0f
        binding.fabAdd.visibility = View.VISIBLE
        binding.fabAdd.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }

    // ─────────────────────────────────────────────────────────────────
    // Back press
    // ─────────────────────────────────────────────────────────────────

    private fun handleBackPress() {
        backCallback.handleOnBackPressed()
    }

    // ─────────────────────────────────────────────────────────────────
    // Loading states
    // ─────────────────────────────────────────────────────────────────

    private fun showSkeletonLoaders() {
        val prefs = PreferencesManager(requireContext())
        val savedCount = prefs.incidentsCount

        binding.swipeRefreshLayout.isRefreshing = false
        if (savedCount > 0) {
            adapter.showSkeletons(savedCount)
            binding.layoutCenterLoading.visibility = View.GONE
            binding.rvIncidents.visibility = View.VISIBLE
        } else {
            binding.layoutCenterLoading.visibility = View.VISIBLE
            binding.rvIncidents.visibility = View.GONE
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Network
    // ─────────────────────────────────────────────────────────────────

    private fun fetchIncidents() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getIncidents()
                if (response.isSuccessful) {
                    binding.layoutCenterLoading.visibility = View.GONE
                    binding.rvIncidents.visibility = View.VISIBLE

                    val incidents = response.body() ?: emptyList()
                    val prefs = PreferencesManager(requireContext())
                    prefs.incidentsCount = incidents.size

                    updateList(incidents)
                } else {
                    handleApiError(response.code(), response.errorBody()?.string())
                }
            } catch (e: Exception) {
                showErrorBanner("Sin conexión a internet o servidor inaccesible.")
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
                if (!hasFormOpen) showFab()
            }
        }
    }

    private fun updateList(incidents: List<Incident>) {
        incidentsList.clear()
        incidentsList.addAll(incidents)
        adapter.updateData(incidentsList)
        if (!hasFormOpen) showFab()
    }

    // ─────────────────────────────────────────────────────────────────
    // CRUD — Crear incidente
    // ─────────────────────────────────────────────────────────────────

    private fun createIncident(
        title: String,
        description: String?,
        setLoading: (Boolean) -> Unit
    ) {
        if (title.isEmpty()) {
            showErrorBanner("El título del reporte es obligatorio.")
            setLoading(false)
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                val req = IncidentCreateRequest(title, description)
                val res = ApiClient.apiService.createIncident(req)
                if (res.isSuccessful) {
                    val newIncident = res.body()
                    if (newIncident != null) {
                        hasFormOpen = false
                        adapter.incidentCreated(newIncident)
                        incidentsList.add(0, newIncident)
                        val prefs = PreferencesManager(requireContext())
                        prefs.incidentsCount = incidentsList.size
                        showFab()
                    } else {
                        fetchIncidents()
                    }
                } else {
                    handleApiError(res.code(), res.errorBody()?.string())
                    setLoading(false)
                }
            } catch (e: Exception) {
                showErrorBanner("Sin conexión a internet o servidor inaccesible.")
                setLoading(false)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Error Handling & Banner (mismo patrón que VehiclesFragment)
    // ─────────────────────────────────────────────────────────────────

    private var errorBannerRunnable: Runnable? = null

    private fun handleApiError(code: Int, errorBody: String?) {
        var customMessage: String? = null
        try {
            if (!errorBody.isNullOrEmpty()) {
                val jsonObject = org.json.JSONObject(errorBody)
                customMessage = jsonObject.optString("message").ifEmpty { null }
                    ?: jsonObject.optString("error").ifEmpty { null }
            }
        } catch (_: Exception) {}

        val message = when (code) {
            400  -> customMessage ?: "Petición inválida. Verifica los datos."
            401  -> "Tu sesión ha expirado o no tienes acceso."
            403  -> customMessage ?: "No tienes permiso para realizar esta acción."
            404  -> customMessage ?: "El recurso no fue encontrado."
            422  -> customMessage ?: "Datos inválidos. Verifica el título e intenta de nuevo."
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
