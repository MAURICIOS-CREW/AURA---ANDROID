package com.mexadev.aura.ui.home

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.content.Context
import com.google.android.material.transition.Hold
import com.mexadev.aura.R
import com.mexadev.aura.databinding.FragmentHomeBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Intent
import com.mexadev.aura.LoginActivity
import com.mexadev.aura.core.network.ApiClient
import java.io.IOException

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var shimmerAnimator: ValueAnimator? = null
    private var navigator: DashboardNavigator? = null
    
    // Pull to Refresh state
    private var isRefreshing = false

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
                    
                    binding.tvSaldoValue.visibility = View.VISIBLE
                    binding.tvSaldoValue.text = "$1,250.00 MXN"
                    
                    binding.tvCuotaMantenimiento.visibility = View.VISIBLE
                    
                    binding.tvProximoPagoValue.visibility = View.VISIBLE
                    binding.tvProximoPagoValue.text = "15 de junio, 2024"
                    
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
            } catch (e: IOException) {
                if (_binding == null) return@launch
                showErrorOverlay("Sin Conexión", "No hay conexión al servidor.\nVerifica tu red y vuelve a intentarlo.")
            } catch (e: Exception) {
                if (_binding == null) return@launch
                showErrorOverlay("Error", "Ha ocurrido un error en la aplicación.")
            } finally {
                if (_binding != null && isRefreshing) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    isRefreshing = false
                }
            }
        }
    }

    private fun showErrorOverlay(title: String, message: String) {
        val mainActivity = requireActivity() as? com.mexadev.aura.MainActivity
        mainActivity?.showGlobalError(title, message) {
            fetchDashboardData()
        }
    }

    private fun setupGridClickListeners() {
        var isNavigating = false
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
        binding.btnPagos.setOnClickListener(clickListener)
        binding.btnReservas.setOnClickListener(clickListener)
        binding.btnDocumentos.setOnClickListener(clickListener)
        binding.btnComunidad.setOnClickListener(clickListener)
        binding.btnEncuestas.setOnClickListener(clickListener)

        // Configurar clicks para elementos del Resumen Rápido (Saldo, Próximo Pago y Botones asociados)
        val paymentsClickListener = View.OnClickListener { view ->
            if (isNavigating) return@OnClickListener
            isNavigating = true

            view.postDelayed({
                if (!isAdded) {
                    isNavigating = false
                    return@postDelayed
                }
                val item = DashboardItem.Pagos
                val transitionName = "transition_pagos_${view.id}"
                view.transitionName = transitionName
                navigator?.navigateToDetail(view, item)
                view.postDelayed({ isNavigating = false }, 500L)
            }, 100L)
        }

        binding.cardSaldo.setOnClickListener(paymentsClickListener)
        binding.cardProximoPago.setOnClickListener(paymentsClickListener)
        binding.btnPagarAhora.setOnClickListener(paymentsClickListener)
        binding.ivWalletBtn.setOnClickListener(paymentsClickListener)

        binding.tvSeeAll.setOnClickListener { view ->
            if (isNavigating) return@setOnClickListener
            isNavigating = true
            view.postDelayed({
                if (isAdded) {
                    navigator?.navigateToDetailWithFade("Resumen Rápido", R.drawable.ic_document)
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
