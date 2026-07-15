package com.mexadev.aura.ui.vehicles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.data.model.Vehicle
import com.mexadev.aura.data.model.VehicleCreateRequest
import com.mexadev.aura.data.model.VehicleUpdateRequest
import com.mexadev.aura.databinding.FragmentVehiclesBinding
import com.mexadev.aura.ui.common.ConfirmBottomSheetFragment
import kotlinx.coroutines.launch

/**
 * VehiclesFragment
 *
 * Pantalla de gestión de vehículos del residente.
 *
 * Flujo de animaciones:
 *  1. FAB pulsado → FAB hace shrink + fade-out  → adapter.addEmptyVehicle()
 *     → card aparece con scale-up + slide-up (ver VehiclesAdapter)
 *  2. Card existente pulsada → TransitionSet(ChangeBounds+Fade) desplaza
 *     las demás cards mientras la seleccionada crece mostrando el formulario
 *  3. Minimize/Guardar/Eliminar → colapsa con misma transición inversa
 *     → FAB regresa con fade-in + scale-up
 *
 * Principios SOLID:
 *  - SRP : Fragment solo orquesta UI y ciclo de vida; negocio en lambdas del adapter
 *  - DIP : Depende de abstracciones (ApiClient, PreferencesManager) no instancias
 */
class VehiclesFragment : Fragment() {

    private var _binding: FragmentVehiclesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: VehiclesAdapter
    private var vehiclesList = mutableListOf<Vehicle>()

    // ─────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVehiclesBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (adapter.expandedPosition != -1) {
                adapter.collapseCurrent()
                showFab()
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.transitionName = "shared_card_transition"

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            
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
        fetchVehicles()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        binding.rvVehicles.layoutManager = LinearLayoutManager(requireContext())

        adapter = VehiclesAdapter(
            items         = mutableListOf(),
            recyclerView  = binding.rvVehicles,
            onSave        = { vehicle, plate, brand, color, setLoading ->
                saveVehicle(vehicle, plate, brand, color, setLoading)
            },
            onDelete      = { vehicle, setLoading ->
                confirmDelete(vehicle, setLoading)
            },
            onFormMinimize = { hasChanges ->
                if (hasChanges) {
                    ConfirmBottomSheetFragment.newInstance(
                        title       = "Cambios sin guardar",
                        message     = "¿Deseas salir y perder los cambios realizados?",
                        confirmText = "Salir sin guardar",
                        onConfirm   = {
                            adapter.collapseCurrent()
                            showFab()
                        }
                    ).show(childFragmentManager, "DiscardConfirm")
                } else {
                    adapter.collapseCurrent()
                    showFab()
                }
            }
        )

        binding.rvVehicles.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefreshLayout.setOnRefreshListener { fetchVehicles() }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            // Capturar el centro del FAB en coordenadas de ventana ANTES de animarlo
            val fabLoc = IntArray(2)
            binding.fabAdd.getLocationInWindow(fabLoc)
            val fabCenterX = fabLoc[0] + binding.fabAdd.width  / 2
            val fabCenterY = fabLoc[1] + binding.fabAdd.height / 2

            animateFabOut {
                // Pasar coordenadas del FAB para que la card "nazca" desde ese punto
                adapter.addEmptyVehicle(fabCenterX, fabCenterY)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // FAB animations
    // ─────────────────────────────────────────────────────────────────

    /**
     * Hace shrink + fade-out del FAB y llama [onEnd] al terminar.
     */
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

    /**
     * Hace aparecer el FAB con scale-up + fade-in.
     */
    private fun showFab() {
        if (binding.fabAdd.visibility == View.VISIBLE &&
            binding.fabAdd.scaleX == 1f) return
        // Restaurar pivote al centro para que el scale-in sea simétrico
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
        val savedCount = prefs.vehiclesCount

        binding.swipeRefreshLayout.isRefreshing = false
        if (savedCount > 0) {
            adapter.showSkeletons(savedCount)
            binding.layoutCenterLoading.visibility = View.GONE
            binding.rvVehicles.visibility = View.VISIBLE
        } else {
            binding.layoutCenterLoading.visibility = View.VISIBLE
            binding.rvVehicles.visibility = View.GONE
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Network
    // ─────────────────────────────────────────────────────────────────

    private fun fetchVehicles() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getVehicles()
                if (response.isSuccessful) {
                    binding.layoutCenterLoading.visibility = View.GONE
                    binding.rvVehicles.visibility = View.VISIBLE

                    val vehicles = response.body() ?: emptyList()
                    val prefs    = PreferencesManager(requireContext())
                    val oldCount = prefs.vehiclesCount
                    prefs.vehiclesCount = vehicles.size

                    if (vehicles.size > oldCount && oldCount > 0) {
                        updateListWithAnimation(vehicles)
                    } else {
                        updateList(vehicles)
                    }
                } else {
                    handleApiError(response.code(), response.errorBody()?.string())
                }
            } catch (e: Exception) {
                showErrorBanner("Sin conexión a internet o servidor inaccesible.")
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
                showFab()
            }
        }
    }

    private fun updateList(vehicles: List<Vehicle>) {
        vehiclesList.clear()
        vehiclesList.addAll(vehicles)
        adapter.updateData(vehiclesList)
        showFab()
    }

    private fun updateListWithAnimation(vehicles: List<Vehicle>) {
        vehiclesList.clear()
        vehiclesList.addAll(vehicles)
        adapter.updateData(vehiclesList)
        showFab()

        binding.rvVehicles.alpha = 0f
        binding.rvVehicles.translationY = -30f
        binding.rvVehicles.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator(1.0f))
            .start()
    }

    // ─────────────────────────────────────────────────────────────────
    // CRUD operations
    // ─────────────────────────────────────────────────────────────────

    private fun saveVehicle(vehicle: Vehicle, plate: String, brand: String, color: String, setLoading: (Boolean) -> Unit) {
        if (plate.isEmpty()) {
            Toast.makeText(requireContext(), "Las placas son obligatorias", Toast.LENGTH_SHORT).show()
            setLoading(false)
            return
        }

        lifecycleScope.launch {
            setLoading(true)
            try {
                if (vehicle.id == -1L) {
                    // ── Crear nuevo vehículo ──
                    val profileRes  = ApiClient.apiService.getProfile()
                    val residenceId = profileRes.body()?.residences?.firstOrNull()?.id ?: 1L

                    val req = VehicleCreateRequest(residenceId, plate, brand, color)
                    val res = ApiClient.apiService.createVehicle(req)
                    if (res.isSuccessful) {
                        Toast.makeText(requireContext(), "Vehículo registrado", Toast.LENGTH_SHORT).show()
                        val newVehicle = res.body()
                        if (newVehicle != null) {
                            adapter.vehicleCreated(newVehicle)
                            showFab()
                        } else {
                            fetchVehicles()
                        }
                    } else {
                        handleApiError(res.code(), res.errorBody()?.string())
                        setLoading(false)
                    }
                } else {
                    // ── Actualizar vehículo existente ──
                    val req = VehicleUpdateRequest(plate, brand, color)
                    val res = ApiClient.apiService.updateVehicle(vehicle.id, req)
                    if (res.isSuccessful) {
                        Toast.makeText(requireContext(), "Vehículo actualizado", Toast.LENGTH_SHORT).show()
                        val updatedVehicle = res.body()
                        if (updatedVehicle != null) {
                            adapter.vehicleUpdated(updatedVehicle)
                            showFab()
                        } else {
                            fetchVehicles()
                        }
                    } else {
                        handleApiError(res.code(), res.errorBody()?.string())
                        setLoading(false)
                    }
                }
            } catch (e: Exception) {
                showErrorBanner("Sin conexión a internet o servidor inaccesible.")
                setLoading(false)
            }
        }
    }

    private fun confirmDelete(vehicle: Vehicle, setLoading: (Boolean) -> Unit) {
        ConfirmBottomSheetFragment.newInstance(
            title       = "Eliminar Vehículo",
            message     = "¿Estás seguro que deseas eliminar este vehículo? Esta acción no se puede deshacer.",
            confirmText = "Eliminar",
            iconRes     = R.drawable.ic_delete,
            onConfirm   = { deleteVehicle(vehicle, setLoading) }
        ).show(childFragmentManager, "DeleteConfirm")
    }

    private fun deleteVehicle(vehicle: Vehicle, setLoading: (Boolean) -> Unit) {
        lifecycleScope.launch {
            setLoading(true)
            try {
                val res = ApiClient.apiService.deleteVehicle(vehicle.id)
                if (res.isSuccessful) {
                    Toast.makeText(requireContext(), "Vehículo eliminado", Toast.LENGTH_SHORT).show()
                    adapter.vehicleDeleted(vehicle)
                    showFab()
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
    // Error Handling & Banner
    // ─────────────────────────────────────────────────────────────────

    private var errorBannerRunnable: Runnable? = null

    private fun handleApiError(code: Int, errorBody: String?) {
        var customMessage: String? = null
        try {
            if (!errorBody.isNullOrEmpty()) {
                val jsonObject = org.json.JSONObject(errorBody)
                if (jsonObject.has("message")) {
                    customMessage = jsonObject.getString("message")
                } else if (jsonObject.has("error")) {
                    customMessage = jsonObject.getString("error")
                }
            }
        } catch (e: Exception) {
            // Ignore JSON parse error
        }

        val message = when (code) {
            500 -> "Error interno en el servidor."
            400 -> customMessage ?: "Petición inválida. Verifica los datos."
            401 -> "Tu sesión ha expirado o no tienes acceso."
            403 -> customMessage ?: "No tienes permiso para realizar esta acción."
            404 -> customMessage ?: "El vehículo o recurso no fue encontrado."
            422 -> customMessage ?: "Datos inválidos. Es posible que las placas ya estén registradas."
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
                    .withEndAction {
                        scheduleHideBanner()
                    }
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
            .withEndAction {
                binding.errorBanner.visibility = View.GONE
            }
            .start()
    }
}
