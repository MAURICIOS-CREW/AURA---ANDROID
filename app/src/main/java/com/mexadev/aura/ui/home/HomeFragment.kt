package com.mexadev.aura.ui.home

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.SharedElementCallback
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.mexadev.aura.LoginActivity
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.data.model.PendingPaymentItem
import com.mexadev.aura.databinding.FragmentHomeBinding
import com.mexadev.aura.ui.payments.PaymentSelectionActivity
import kotlinx.coroutines.launch
import java.io.IOException

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var shimmerAnimator: ValueAnimator? = null
    private var navigator: DashboardNavigator? = null
    
    // Pull to Refresh state
    private var isRefreshing = false
    private var isNavigating = false
    private var pendingPaymentItems: List<PendingPaymentItem> = emptyList()

    private val selectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            fetchDashboardData()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is DashboardNavigator) {
            navigator = context
        } else {
            throw RuntimeException("$context must implement DashboardNavigator")
        }
    }

    override fun onDetach() {
        super.onDetach()
        navigator = null
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupStatusBarAndMargins()
        setupScrollBehavior()
        setupPullToRefresh()
        setupGridClickListeners()
        
        startSkeletonAnimation()
        fetchDashboardData()
    }

    private fun setupStatusBarAndMargins() {
        // Initialize status bar icons to light (white icons for dark image background)
        activity?.window?.let { window ->
            val wic = WindowInsetsControllerCompat(window, window.decorView)
            wic.isAppearanceLightStatusBars = false
        }

        // Apply status bar height to top header bar padding and top spacer height
        ViewCompat.setOnApplyWindowInsetsListener(binding.topHeaderBar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val statusBarHeight = systemBars.top
            
            // Set padding top of topHeaderBar equal to status bar height
            v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
            
            // Set height of top spacer to push contents down below header icons
            val density = resources.displayMetrics.density
            val extraMargin = (24 * density).toInt()
            val params = binding.vTopSpacer.layoutParams
            params.height = statusBarHeight + extraMargin
            binding.vTopSpacer.layoutParams = params
            
            insets
        }
    }

    private fun setupScrollBehavior() {
        binding.nestedScrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // Prevent scroll updates while refreshing
            if (isRefreshing) return@setOnScrollChangeListener
            
            val density = resources.displayMetrics.density
            val maxScrollDistance = 140 * density // distance in dp to complete transition
            val progress = (scrollY.toFloat() / maxScrollDistance).coerceIn(0f, 1f)
            
            // Update sticky header background opacity (solid white when scrolled down)
            binding.vHeaderBackground.alpha = progress
            
            // Update solid white content background opacity
            binding.vSolidWhiteBackground.alpha = progress
            
            // Adjust sticky header elevation
            binding.topHeaderBar.elevation = if (progress > 0.1f) 6 * density else 0f
            
            // Fade out logo, welcome text, residential name and top header icons
            val fadeOutAlpha = (1f - progress * 1.5f).coerceIn(0f, 1f)
            binding.ivLogo.alpha = fadeOutAlpha
            binding.tvWelcomeName.alpha = fadeOutAlpha
            binding.tvResidentialName.alpha = fadeOutAlpha
            
            // Fade in compact sticky header content (logo + user name)
            binding.layoutHeaderUser.alpha = progress
            
            // Apply dynamic blur to the background image
            val maxBlurRadius = 20f
            val currentBlur = progress * maxBlurRadius
            if (currentBlur > 0.5f) {
                val blurEffect = RenderEffect.createBlurEffect(currentBlur, currentBlur, Shader.TileMode.CLAMP)
                binding.ivBackground.setRenderEffect(blurEffect)
            } else {
                binding.ivBackground.setRenderEffect(null)
            }
            
            // Dynamic status bar icon theme transition
            activity?.window?.let { window ->
                val wic = WindowInsetsControllerCompat(window, window.decorView)
                // Use dark status bar icons (light mode status bar) when background header is white
                wic.isAppearanceLightStatusBars = progress > 0.5f
            }
        }
    }

    private fun setupPullToRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.aura_white)
        
        // Calculate offset so the indicator pulls down below the sticky header bar
        binding.topHeaderBar.post {
            if (!isAdded) return@post
            val density = resources.displayMetrics.density
            val headerHeight = binding.topHeaderBar.height
            val startOffset = headerHeight - (24 * density).toInt()
            val endOffset = headerHeight + (48 * density).toInt()
            binding.swipeRefreshLayout.setProgressViewOffset(true, startOffset, endOffset)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            triggerRefreshState()
        }
    }

    private fun triggerRefreshState() {
        isRefreshing = true
        fetchDashboardData()
    }

    private fun startSkeletonAnimation() {
        setPayButtonsEnabled(false)
        binding.tvWelcomeName.visibility = View.INVISIBLE
        binding.tvResidentialName.visibility = View.INVISIBLE
        binding.tvSaldoValue.visibility = View.INVISIBLE
        binding.tvCuotaMantenimiento.visibility = View.INVISIBLE
        binding.tvProximoPagoValue.visibility = View.INVISIBLE

        // Initialize and make skeletons visible
        binding.skeletonWelcomeName.visibility = View.VISIBLE
        binding.skeletonResidential.visibility = View.VISIBLE
        binding.skeletonSaldo.visibility = View.VISIBLE
        binding.skeletonProximoPago.visibility = View.VISIBLE

        shimmerAnimator?.cancel()
        shimmerAnimator = ValueAnimator.ofFloat(0.4f, 1.0f, 0.4f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                if (_binding == null) return@addUpdateListener
                val alphaVal = animator.animatedValue as Float
                binding.skeletonWelcomeName.alpha = alphaVal
                binding.skeletonResidential.alpha = alphaVal
                binding.skeletonSaldo.alpha = alphaVal
                binding.skeletonProximoPago.alpha = alphaVal
            }
            start()
        }
    }

    private fun fetchDashboardData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isPull = isRefreshing
            if (_binding == null) return@launch
            if (!isPull) {
                startSkeletonAnimation()
            }
            
            try {
                val response = ApiClient.apiService.getProfile()
                if (_binding == null) return@launch
                
                if (response.isSuccessful) {
                    val user = response.body()
                    val userName = user?.name ?: "Usuario"
                    
                    // Cancel animators
                    shimmerAnimator?.cancel()
                    
                    // Hide skeletons
                    binding.skeletonWelcomeName.visibility = View.GONE
                    binding.skeletonResidential.visibility = View.GONE
                    binding.skeletonSaldo.visibility = View.GONE
                    binding.skeletonProximoPago.visibility = View.GONE
                    
                    // Show and set real data
                    binding.tvWelcomeName.visibility = View.VISIBLE
                    binding.tvWelcomeName.text = getString(R.string.home_welcome_name, userName)
                    
                    val residenceName = user?.residences?.firstOrNull()?.let { res ->
                        val parts = listOfNotNull(
                            res.address?.name?.takeIf { it.isNotBlank() },
                            res.block?.let { "Mza $it" },
                            res.number?.takeIf { it.isNotBlank() }?.let { "Lte $it" }
                        )
                        if (parts.isNotEmpty()) parts.joinToString(", ") else null
                    } ?: getString(R.string.home_residential_name)

                    binding.tvResidentialName.visibility = View.VISIBLE
                    binding.tvResidentialName.text = residenceName
                    
                    // Fetch real payments data for quick summary section
                    try {
                        val paymentsResponse = ApiClient.apiService.getPaymentsSummary(1)
                        if (paymentsResponse.isSuccessful && paymentsResponse.body()?.status == "success") {
                            val data = paymentsResponse.body()?.data
                            val balance = data?.saldoPendiente ?: "0.00"
                            binding.tvSaldoValue.text = getString(R.string.payments_balance_format, balance)

                            pendingPaymentItems = data?.pagosPendientes.orEmpty()
                            if (pendingPaymentItems.isNotEmpty()) {
                                binding.tvCuotaMantenimiento.text = pendingPaymentItems.first().title
                                binding.tvProximoPagoValue.text = pendingPaymentItems.first().title
                            } else {
                                binding.tvCuotaMantenimiento.text = getString(R.string.payments_no_pending)
                                binding.tvProximoPagoValue.text = getString(R.string.payments_no_pending)
                            }
                            setPayButtonsEnabled(true)
                        } else {
                            pendingPaymentItems = emptyList()
                            binding.tvSaldoValue.text = getString(R.string.payments_balance_format, "0.00")
                            binding.tvProximoPagoValue.text = getString(R.string.payments_no_pending)
                            setPayButtonsEnabled(false)
                        }
                    } catch (_: Exception) {
                        pendingPaymentItems = emptyList()
                        binding.tvSaldoValue.text = getString(R.string.payments_balance_format, "0.00")
                        setPayButtonsEnabled(false)
                    }

                    binding.tvSaldoValue.visibility = View.VISIBLE
                    binding.tvCuotaMantenimiento.visibility = View.VISIBLE
                    binding.tvProximoPagoValue.visibility = View.VISIBLE
                    
                    // Set header user name
                    binding.tvHeaderUserName.text = userName
                    
                    if (isPull) {
                        binding.swipeRefreshLayout.isRefreshing = false
                        isRefreshing = false
                        
                        // Cascade card reveal transition
                        val cards = listOf(
                            binding.gridContainer,
                            binding.cardSaldo,
                            binding.cardProximoPago,
                            binding.btnPagarAhora
                        )
                        cards.forEachIndexed { index, card ->
                            card.alpha = 0f
                            card.translationY = 60f
                            card.animate()
                                .alpha(1f)
                                .translationY(0f)
                                .setDuration(500)
                                .setStartDelay(index * 90L)
                                .setInterpolator(DecelerateInterpolator())
                                .start()
                        }
                    } else {
                        binding.gridContainer.visibility = View.VISIBLE
                        binding.cardSaldo.visibility = View.VISIBLE
                        binding.cardProximoPago.visibility = View.VISIBLE
                        binding.btnPagarAhora.visibility = View.VISIBLE
                    }
                } else {
                    setPayButtonsEnabled(false)
                    val code = response.code()
                    if (code == 401 || code == 403) {
                        // Token expiró y refresh falló (TokenAuthenticator limpió la sesión)
                        // Manda a login
                        requireActivity().startActivity(Intent(requireContext(), LoginActivity::class.java))
                        requireActivity().finishAffinity()
                    } else if (code >= 500) {
                        showErrorOverlay("Mantenimiento", "El servidor se encuentra en mantenimiento o presentó un problema.\nIntenta más tarde.")
                    } else {
                        showErrorOverlay("Error", "Ocurrió un error inesperado (Código: $code).")
                    }
                }
            } catch (_: IOException) {
                if (_binding == null) return@launch
                setPayButtonsEnabled(false)
                showErrorOverlay("Sin Conexión", "No hay conexión al servidor.\nVerifica tu red y vuelve a intentarlo.")
            } catch (_: Exception) {
                if (_binding == null) return@launch
                setPayButtonsEnabled(false)
                showErrorOverlay("Error", "Ha ocurrido un error en la aplicación.")
            } finally {
                if (_binding != null && isRefreshing) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    isRefreshing = false
                }
            }
        }
    }

    private fun setPayButtonsEnabled(enabled: Boolean) {
        if (_binding == null) return
        binding.btnPagarAhora.isEnabled = enabled
        binding.btnPagarAhora.alpha = if (enabled) 1.0f else 0.5f
        binding.ivWalletBtn.isEnabled = enabled
        binding.ivWalletBtn.isClickable = enabled
        binding.ivWalletBtn.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun launchPaymentSelection(mode: String) {
        if (isNavigating) return
        isNavigating = true

        val json = Gson().toJson(pendingPaymentItems)

        val intent = Intent(requireContext(), PaymentSelectionActivity::class.java).apply {
            putExtra(PaymentSelectionActivity.EXTRA_PENDING_ITEMS_JSON, json)
            putExtra(PaymentSelectionActivity.EXTRA_TRANSITION_MODE, mode)
        }

        // Re-map shared elements on return so each transition reverses to the right origin view
        requireActivity().setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                _binding?.let { b ->
                    sharedElements["transition_card_balance"] = b.cardSaldo
                    sharedElements["transition_tv_balance"] = b.tvSaldoValue
                    sharedElements["transition_tv_label"] = b.tvSaldoLabel
                    sharedElements["transition_btn_pay"] = b.btnPagarAhora
                }
            }
        })

        val pairs = if (mode == PaymentSelectionActivity.MODE_BUTTON) {
            // Only the button morphs; the rest of the destination screen springs in on its own
            arrayOf(Pair.create(binding.btnPagarAhora as View, "transition_btn_pay"))
        } else {
            arrayOf(
                Pair.create(binding.cardSaldo as View, "transition_card_balance"),
                Pair.create(binding.tvSaldoValue as View, "transition_tv_balance"),
                Pair.create(binding.tvSaldoLabel as View, "transition_tv_label")
            )
        }

        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(), *pairs
        )

        selectionLauncher.launch(intent, options)

        binding.root.postDelayed({ isNavigating = false }, 500L)
    }

    private fun showErrorOverlay(title: String, message: String) {
        val mainActivity = requireActivity() as? com.mexadev.aura.MainActivity
        mainActivity?.showGlobalError(title, message) {
            fetchDashboardData()
        }
    }

    private fun setupGridClickListeners() {
        val clickListener = View.OnClickListener { view ->
            if (isNavigating) return@OnClickListener
            val item = DashboardItem.fromId(view.id) ?: return@OnClickListener
            isNavigating = true

            // Un delay muy corto (100ms) permite que la onda del ripple se dibuje y sea visible
            // antes de que inicie la transición de pantalla completa o el cambio de tab.
            view.postDelayed({
                if (!isAdded) {
                    isNavigating = false
                    return@postDelayed
                }
                // Asignar transitionName dinámicamente si no está establecido
                val transitionName = "transition_${item.javaClass.simpleName.lowercase()}"
                view.transitionName = transitionName
                navigator?.navigateToDetail(view, item)
                // Liberar el flag de navegación después de que termine la transición de entrada
                view.postDelayed({ isNavigating = false }, 500L)
            }, 100L)
        }

        binding.btnAccesos.setOnClickListener(clickListener)
        binding.btnVehiculos.setOnClickListener(clickListener)
        binding.btnIncidencias.setOnClickListener(clickListener)

        binding.btnServicios.setOnClickListener(clickListener)
        binding.btnDocumentos.setOnClickListener(clickListener)
        binding.btnComunidad.setOnClickListener(clickListener)
        binding.btnEncuestas.setOnClickListener(clickListener)

        // Configurar clicks para elementos del Resumen Rápido
        val navToPaymentsListener = View.OnClickListener { _ ->
            if (isNavigating) return@OnClickListener
            isNavigating = true

            binding.root.postDelayed({
                if (!isAdded) {
                    isNavigating = false
                    return@postDelayed
                }
                navigator?.navigateToTab(2)
                binding.root.postDelayed({ isNavigating = false }, 500L)
            }, 100L)
        }

        binding.btnPagos.setOnClickListener(navToPaymentsListener)
        binding.cardProximoPago.setOnClickListener(navToPaymentsListener)

        val payNowFromButtonListener = View.OnClickListener { _ ->
            launchPaymentSelection(PaymentSelectionActivity.MODE_BUTTON)
        }
        val payNowFromCardListener = View.OnClickListener { _ ->
            launchPaymentSelection(PaymentSelectionActivity.MODE_CARD)
        }

        binding.btnPagarAhora.setOnClickListener(payNowFromButtonListener)
        binding.ivWalletBtn.setOnClickListener(payNowFromCardListener)
        binding.cardSaldo.setOnClickListener(payNowFromCardListener)

        binding.tvSeeAll.setOnClickListener { view ->
            if (isNavigating) return@setOnClickListener
            isNavigating = true
            view.postDelayed({
                if (isAdded) {
                    navigator?.navigateToTab(2)
                }
                view.postDelayed({ isNavigating = false }, 500L)
            }, 100L)
        }

        // Configurar clicks para iconos del Header con delay para que el ripple sea visible
        binding.ivNotifications.setOnClickListener { view ->
            view.postDelayed({
                if (isAdded) navigator?.navigateToTab(1)
            }, 100L)
        }
        binding.ivProfile.setOnClickListener { view ->
            view.postDelayed({
                if (isAdded) navigator?.navigateToTab(3)
            }, 100L)
        }
    }

    fun updateStatusBarTheme() {
        if (!isAdded) return
        val scrollY = binding.nestedScrollView.scrollY
        val density = resources.displayMetrics.density
        val maxScrollDistance = 140 * density
        val progress = (scrollY.toFloat() / maxScrollDistance).coerceIn(0f, 1f)
        activity?.window?.let { window ->
            val wic = WindowInsetsControllerCompat(window, window.decorView)
            wic.isAppearanceLightStatusBars = progress > 0.5f
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusBarTheme()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shimmerAnimator?.cancel()
        _binding = null
    }
}
