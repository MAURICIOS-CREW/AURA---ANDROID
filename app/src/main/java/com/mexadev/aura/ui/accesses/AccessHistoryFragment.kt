package com.mexadev.aura.ui.accesses

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.data.model.AccessLog
import com.mexadev.aura.databinding.FragmentAccessHistoryBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.core.content.edit
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AccessHistoryFragment
 *
 * Pestaña de historial de accesos. Características clave:
 *
 *  1. Skeleton loader: al entrar se muestran N skeletons basados en el
 *     último conteo cacheado → transición fluida sin "pantalla en blanco".
 *
 *  2. Precarga silenciosa: la primera llamada a la API ocurre en una
 *     corrutina independiente que NO bloquea la inicialización del QR
 *     ni de AccessListFragment (se lanza desde onViewCreated,
 *     pero el Fragment coexiste en un ViewPager2 que gestiona el ciclo de vida).
 *
 *  3. Polling atómico: cada POLL_INTERVAL_MS se compara el ID del primer
 *     elemento nuevo con el ID en memoria. Si son iguales, no se toca el
 *     adapter → cero parpadeos. Si hay datos nuevos, se actualiza con DiffUtil.
 *
 *  4. Paginación: al llegar al final del RecyclerView se carga la siguiente
 *     página y se anexa (append) sin reemplazar la lista.
 *
 *  5. Pull-to-refresh: resetea a página 1 y anima la entrada.
 */
class AccessHistoryFragment : Fragment() {

    // ── Constantes ─────────────────────────────────────────────────────
    companion object {
        private const val PREFS_NAME          = "aura_cache"
        private const val PREFS_KEY_LOG_COUNT = "last_log_count"
        private const val POLL_INTERVAL_MS    = 10_000L  // 10 segundos
        private const val PER_PAGE            = 20
    }

    private var _binding: FragmentAccessHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AccessLogAdapter

    // Estado de paginación
    private var currentPage  = 1
    private var lastPage     = 1
    private var isLoadingPage = false

    // Lista acumulada de todos los logs cargados
    private val allLogs = mutableListOf<AccessLog>()

    // El ID del primer elemento al momento del último poll
    // Se usa para detectar nuevos accesos sin reemplazar lista completa
    private var lastKnownTopId: Long = -1L

    // Jobs de corrutinas para poder cancelarlos en onDestroyView
    private var pollJob:  Job? = null
    private var fetchJob: Job? = null

    // ── Lifecycle ──────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccessHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSharedElementCallback()
        setupRecyclerView()
        setupSwipeRefresh()

        // Mostrar skeletons basados en último conteo cacheado
        showInitialSkeleton()

        // Cargar primera página
        fetchPage(page = 1, firstLoad = true)

        // Iniciar polling después del primer fetch
        startPolling()
    }

    private fun setupSharedElementCallback() {
        requireActivity().setExitSharedElementCallback(object : androidx.core.app.SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                val currentBinding = _binding ?: return
                if (names.isEmpty()) return
                val name = names[0]
                val layoutManager = currentBinding.rvAccessLogs.layoutManager as? LinearLayoutManager
                    ?: return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible  = layoutManager.findLastVisibleItemPosition()
                for (i in firstVisible..lastVisible) {
                    val holder = currentBinding.rvAccessLogs.findViewHolderForAdapterPosition(i) as? AccessLogAdapter.ItemViewHolder
                    val cardView = holder?.b?.cardAccessLog ?: continue
                    if (cardView.transitionName == name) {
                        sharedElements[name] = cardView
                        return
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        activity?.setExitSharedElementCallback(null as androidx.core.app.SharedElementCallback?)
        super.onDestroyView()
        pollJob?.cancel()
        fetchJob?.cancel()
        _binding = null
    }

    // ── Setup ──────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        adapter = AccessLogAdapter(onItemClick = { log, itemBinding ->
            val cardTransName = "access_log_card_${log.id}"
            val nameTransName = "access_log_name_${log.id}"
            val iconTransName = "access_log_icon_${log.id}"
            val statusTransName = "access_log_status_${log.id}"

            itemBinding.cardAccessLog.transitionName = cardTransName
            itemBinding.tvGuestName.transitionName = nameTransName
            itemBinding.ivAccessLogIcon.transitionName = iconTransName
            itemBinding.tvStatus.transitionName = statusTransName

            val intent = Intent(requireContext(), AccessLogDetailActivity::class.java).apply {
                putExtra(AccessLogDetailActivity.EXTRA_LOG_JSON, Gson().toJson(log))
                putExtra("transition_card_name", cardTransName)
                putExtra("transition_name_name", nameTransName)
                putExtra("transition_icon_name", iconTransName)
                putExtra("transition_status_name", statusTransName)
            }

            val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                androidx.core.util.Pair(itemBinding.cardAccessLog, cardTransName),
                androidx.core.util.Pair(itemBinding.tvGuestName, nameTransName),
                androidx.core.util.Pair(itemBinding.ivAccessLogIcon, iconTransName),
                androidx.core.util.Pair(itemBinding.tvStatus, statusTransName)
            )
            startActivity(intent, options.toBundle())
        })

        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvAccessLogs.layoutManager = layoutManager
        binding.rvAccessLogs.adapter = adapter

        // Paginación: detectar scroll al final
        binding.rvAccessLogs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return  // Solo si se scrollea hacia abajo
                val total   = layoutManager.itemCount
                val visible = layoutManager.childCount
                val first   = layoutManager.findFirstVisibleItemPosition()
                val threshold = 4  // cargar antes de llegar al último item
                if (!isLoadingPage && currentPage < lastPage && (visible + first + threshold >= total)) {
                    fetchPage(page = currentPage + 1, firstLoad = false)
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(
            com.mexadev.aura.R.color.aura_primary
        )
        binding.swipeRefreshLayout.setOnRefreshListener {
            resetAndReload()
        }
    }

    // ── Skeleton inicial ───────────────────────────────────────────────

    private fun showInitialSkeleton() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val lastCount = prefs.getInt(PREFS_KEY_LOG_COUNT, 5).coerceIn(3, 12)
        adapter.showSkeletons(lastCount)
        binding.layoutEmpty.visibility = View.GONE
    }

    // ── Fetch de página ────────────────────────────────────────────────

    /**
     * Carga [page] de la API.
     * - [firstLoad] = true: reemplaza toda la lista y anima entrada.
     * - [firstLoad] = false: anexa al final (paginación).
     */
    private fun fetchPage(page: Int, firstLoad: Boolean) {
        if (isLoadingPage) return
        isLoadingPage = true

        if (!firstLoad) {
            adapter.setLoadingFooter(true)
        }

        fetchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getAccessLogs(page = page, perPage = PER_PAGE)
                if (_binding == null) return@launch

                if (!response.isSuccessful) {
                    handleFetchError(firstLoad)
                    return@launch
                }

                val pagedData = response.body()?.data ?: run {
                    handleFetchError(firstLoad)
                    return@launch
                }

                currentPage = pagedData.currentPage
                lastPage    = pagedData.lastPage
                val newItems = pagedData.data

                if (firstLoad) {
                    handleFirstLoad(newItems, hasMore = currentPage < lastPage)
                } else {
                    handleAppendPage(newItems, hasMore = currentPage < lastPage)
                }

                // Cachear conteo para la próxima vez
                saveLogCount(allLogs.size)

            } catch (_: Exception) {
                if (_binding == null) return@launch
                handleFetchError(firstLoad)
            } finally {
                isLoadingPage = false
                _binding?.swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

    private fun handleFirstLoad(items: List<AccessLog>, hasMore: Boolean) {
        if (items.isEmpty()) {
            adapter.updateData(emptyList(), showFooter = false, animate = false)
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvAccessLogs.visibility = View.GONE
            return
        }

        binding.layoutEmpty.visibility = View.GONE
        binding.rvAccessLogs.visibility = View.VISIBLE

        allLogs.clear()
        allLogs.addAll(items)
        lastKnownTopId = allLogs.firstOrNull()?.id ?: -1L

        adapter.updateData(allLogs.toList(), showFooter = hasMore, animate = false)

        // Animación de entrada sutil (solo en primer load, no en refresh silencioso)
        binding.rvAccessLogs.alpha = 0f
        binding.rvAccessLogs.translationY = -20f
        binding.rvAccessLogs.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()
    }

    private fun handleAppendPage(items: List<AccessLog>, hasMore: Boolean) {
        allLogs.addAll(items)
        adapter.appendPage(items, hasMore = hasMore)
    }

    private fun handleFetchError(firstLoad: Boolean) {
        if (firstLoad && allLogs.isEmpty()) {
            // Quitar skeleton y mostrar vacío con mensaje
            adapter.updateData(emptyList(), showFooter = false, animate = false)
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvAccessLogs.visibility = View.GONE
        } else {
            adapter.setLoadingFooter(false)
        }
        _binding?.swipeRefreshLayout?.isRefreshing = false
    }

    // ── Pull-to-refresh ────────────────────────────────────────────────

    private fun resetAndReload() {
        pollJob?.cancel()
        fetchJob?.cancel()
        currentPage  = 1
        lastPage     = 1
        allLogs.clear()
        lastKnownTopId = -1L
        fetchPage(page = 1, firstLoad = true)
        startPolling()
    }

    // ── Polling atómico en background ─────────────────────────────────

    /**
     * Cada POLL_INTERVAL_MS consulta sólo la primera página.
     * Compara el ID del primer elemento nuevo con [lastKnownTopId].
     * Si son iguales → NO toca el adapter (0 parpadeos).
     * Si hay cambios → actualiza con DiffUtil (0 parpadeos).
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (_binding == null) break
                pollNewEntries()
            }
        }
    }

    private suspend fun pollNewEntries() {
        try {
            // Indicador sutil de "buscando" (no intrusivo)
            showUpdatingIndicator(true)

            val response = ApiClient.apiService.getAccessLogs(page = 1, perPage = PER_PAGE)
            if (_binding == null) return

            showUpdatingIndicator(false)

            if (!response.isSuccessful) return

            val pagedData = response.body()?.data ?: return
            val freshPage1 = pagedData.data

            if (freshPage1.isEmpty()) return

            val freshTopId = freshPage1.firstOrNull()?.id ?: return

            // Si el top no cambió → no hay nada nuevo → salir sin tocar el adapter
            if (freshTopId == lastKnownTopId && freshPage1.size == allLogs.size.coerceAtMost(PER_PAGE)) return

            // Hay datos nuevos: reconstruir la lista visible
            // Tomamos los nuevos items y los que ya teníamos más allá de la página 1
            val beyondPage1 = if (allLogs.size > PER_PAGE) allLogs.drop(PER_PAGE) else emptyList()
            val merged = freshPage1 + beyondPage1

            lastPage       = pagedData.lastPage
            lastKnownTopId = freshTopId

            allLogs.clear()
            allLogs.addAll(merged)

            // DiffUtil garantiza actualización atómica (0 parpadeos)
            adapter.updateData(
                allLogs.toList(),
                showFooter = currentPage < lastPage,
                animate = true
            )

            binding.layoutEmpty.visibility  = View.GONE
            binding.rvAccessLogs.visibility = View.VISIBLE

            saveLogCount(allLogs.size)

        } catch (_: Exception) {
            // Error silencioso: no mostrar nada al usuario
            if (_binding != null) showUpdatingIndicator(false)
        }
    }

    /** Muestra/oculta el pequeño indicador de actualización en fondo */
    private fun showUpdatingIndicator(show: Boolean) {
        val target = if (show) View.VISIBLE else View.GONE
        if (binding.layoutUpdating.visibility != target) {
            binding.layoutUpdating.visibility = target
        }
    }

    // ── Util ───────────────────────────────────────────────────────────

    private fun saveLogCount(count: Int) {
        requireContext()
            .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit { putInt(PREFS_KEY_LOG_COUNT, count) }
    }
}
