package com.mexadev.aura

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.fragment.app.Fragment
import android.graphics.drawable.LayerDrawable
import android.graphics.Color
import androidx.activity.OnBackPressedCallback
import androidx.transition.TransitionManager
import androidx.core.view.WindowInsetsControllerCompat
import com.mexadev.aura.ui.home.HomeFragment
import com.mexadev.aura.ui.home.DashboardNavigator
import com.mexadev.aura.ui.home.DashboardItem
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.Hold
import com.mexadev.aura.ui.notifications.NotificationsFragment
import com.mexadev.aura.ui.messages.MessagesFragment
import com.mexadev.aura.ui.profile.ProfileFragment
import kotlinx.coroutines.launch
import com.mexadev.aura.databinding.ActivityMainBinding
import com.mexadev.aura.core.session.SessionManager
import com.mexadev.aura.core.session.BiometricHelper

class MainActivity : AppCompatActivity(), DashboardNavigator {

    private lateinit var binding: ActivityMainBinding
    private var activeCardView: View? = null
    private var activeItem: DashboardItem? = null
    private lateinit var backCallback: OnBackPressedCallback

    private val tabIds = listOf(
        R.id.nav_item_home,
        R.id.nav_item_notifications,
        R.id.nav_item_messages,
        R.id.nav_item_profile
    )

    // Par icono/label para cada tab
    private data class TabViews(val icon: ImageView, val label: TextView)

    private lateinit var tabViews: Map<Int, TabViews>

    // Íconos activo/inactivo para cada tab
    private val iconActive = mapOf(
        R.id.nav_item_home to R.drawable.ic_nav_home_filled,
        R.id.nav_item_notifications to R.drawable.ic_nav_notifications_filled,
        R.id.nav_item_messages to R.drawable.ic_nav_messages_filled,
        R.id.nav_item_profile to R.drawable.ic_nav_profile_filled
    )
    private val iconInactive = mapOf(
        R.id.nav_item_home to R.drawable.ic_nav_home,
        R.id.nav_item_notifications to R.drawable.ic_nav_notifications,
        R.id.nav_item_messages to R.drawable.ic_nav_messages,
        R.id.nav_item_profile to R.drawable.ic_nav_profile
    )

    private var currentTabId = R.id.nav_item_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar callback para interceptar botón de regreso cuando el detalle esté visible
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                closeDetail()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        // Configurar el padding superior del encabezado del detalle para Edge-to-Edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailView.detailHeader) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Listener del botón de regreso del detalle
        binding.detailView.btnBack.setOnClickListener {
            closeDetail()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Do not pad top here to allow Edge-to-Edge layouts to render behind the status bar.
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            
            // Dispatch insets to the fragment container so it can handle IME/Keyboard insets
            ViewCompat.dispatchApplyWindowInsets(binding.detailFragmentContainer, insets)
            
            insets
        }

        // Configurar ViewPager2
        binding.viewPager.adapter = MainPagerAdapter(this)
        
        // Mapear las vistas de cada tab
        tabViews = mapOf(
            R.id.nav_item_home to TabViews(binding.iconHome, binding.labelHome),
            R.id.nav_item_notifications to TabViews(binding.iconNotifications, binding.labelNotifications),
            R.id.nav_item_messages to TabViews(binding.iconMessages, binding.labelMessages),
            R.id.nav_item_profile to TabViews(binding.iconProfile, binding.labelProfile)
        )

        // Configurar íconos con LayerDrawable para el efecto de llenado
        tabViews.forEach { (tabId, view) ->
            val inactiveRes = iconInactive[tabId]!!
            val activeRes = iconActive[tabId]!!
            
            val inactiveDrawable = ContextCompat.getDrawable(this, inactiveRes)!!.mutate()
            val activeDrawable = ContextCompat.getDrawable(this, activeRes)!!.mutate()
            activeDrawable.alpha = if (tabId == currentTabId) 255 else 0
            
            val layerDrawable = LayerDrawable(arrayOf(inactiveDrawable, activeDrawable))
            view.icon.setImageDrawable(layerDrawable)
        }
        
        // Estado inicial de texto
        tabViews[currentTabId]?.let {
            it.label.setTextColor(ContextCompat.getColor(this, R.color.aura_primary))
            it.label.setTypeface(null, android.graphics.Typeface.BOLD)
        }

        // Listeners para cada tab
        tabIds.forEachIndexed { index, tabViewId ->
            val tabContainer = binding.root.findViewById<View>(tabViewId)
            tabContainer.setOnClickListener {
                if (currentTabId != tabViewId) {
                    binding.viewPager.currentItem = index
                }
            }
        }

        // Configurar listener del ViewPager2 para animación de llenado y rebote
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // position es el tab actual, position + 1 es el siguiente tab a la derecha
                updateIconFill(position, 1f - positionOffset)
                if (position + 1 < tabIds.size) {
                    updateIconFill(position + 1, positionOffset)
                }
                // Asegurar que los demás estén en 0
                for (i in tabIds.indices) {
                    if (i != position && i != position + 1) {
                        updateIconFill(i, 0f)
                    }
                }
            }

            override fun onPageSelected(position: Int) {
                val newTabId = tabIds[position]
                if (currentTabId != newTabId) {
                    val oldTabId = currentTabId
                    currentTabId = newTabId
                    updateTabUI(oldTabId, newTabId)
                }
                restoreStatusBarTheme()
            }
        })

        // Botón FAB central — abre el BottomSheet con QR
        binding.fabCenter.setOnClickListener {
            animateFabPress()
            val qrBottomSheet = com.mexadev.aura.ui.qr.QrBottomSheetFragment()
            qrBottomSheet.show(supportFragmentManager, "QrBottomSheet")
        }

        // Biometría para renovación de sesión
        lifecycleScope.launch {
            SessionManager.biometricRequestFlow.collect { callback ->
                BiometricHelper.showBiometricPrompt(
                    activity = this@MainActivity,
                    title = "Renovación de Sesión",
                    subtitle = "Tu sesión expiró. Verifica tu identidad para renovarla automáticamente.",
                    onSuccess = { callback(true) },
                    onError = { callback(false) },
                    onCancel = { callback(false) }
                )
            }
        }
    }

    private fun updateIconFill(tabIndex: Int, fillPercentage: Float) {
        if (tabIndex !in tabIds.indices) return
        val tabId = tabIds[tabIndex]
        val view = tabViews[tabId] ?: return
        val layerDrawable = view.icon.drawable as? LayerDrawable ?: return
        val activeDrawable = layerDrawable.getDrawable(1)
        activeDrawable.alpha = (fillPercentage * 255).toInt()
    }

    /**
     * Actualiza textos y aplica animación de bounce al tab seleccionado tras confirmarse el swipe.
     */
    private fun updateTabUI(oldTabId: Int, newTabId: Int) {
        // Desactivar tab anterior
        tabViews[oldTabId]?.let { old ->
            old.label.setTextColor(ContextCompat.getColor(this, R.color.aura_nav_inactive))
            old.label.typeface = android.graphics.Typeface.DEFAULT
            animateTabDeselect(old.icon)
        }

        // Activar nuevo tab
        tabViews[newTabId]?.let { new ->
            new.label.setTextColor(ContextCompat.getColor(this, R.color.aura_primary))
            new.label.setTypeface(null, android.graphics.Typeface.BOLD)
            animateTabSelect(new.icon)
        }
    }

    /**
     * Animación bounce/spring al seleccionar un tab (ícono sube y regresa).
     */
    private fun animateTabSelect(iconView: View) {
        val scaleX = ObjectAnimator.ofFloat(iconView, "scaleX", 1f, 1.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(iconView, "scaleY", 1f, 1.3f, 1f)
        val translateY = ObjectAnimator.ofFloat(iconView, "translationY", 0f, -6f, 0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, translateY)
            duration = 350
            interpolator = OvershootInterpolator(2.5f)
            start()
        }
    }

    /**
     * Animación suave al deseleccionar un tab.
     */
    private fun animateTabDeselect(iconView: View) {
        val scaleX = ObjectAnimator.ofFloat(iconView, "scaleX", iconView.scaleX, 1f)
        val scaleY = ObjectAnimator.ofFloat(iconView, "scaleY", iconView.scaleY, 1f)
        val translateY = ObjectAnimator.ofFloat(iconView, "translationY", iconView.translationY, 0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, translateY)
            duration = 200
            start()
        }
    }

    /**
     * Animación de pulso al presionar el FAB central.
     */
    private fun animateFabPress() {
        val scaleX = ObjectAnimator.ofFloat(binding.fabCenter, "scaleX", 1f, 0.88f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.fabCenter, "scaleY", 1f, 0.88f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 300
            interpolator = OvershootInterpolator(3f)
            start()
        }
    }

    // Implementación de DashboardNavigator (SOLID Navigation)
    override fun navigateToTab(tabIndex: Int) {
        if (tabIndex in tabIds.indices) {
            binding.viewPager.currentItem = tabIndex
        }
    }

    override fun navigateToDetail(cardView: View, item: DashboardItem) {
        val fragment = item.createFragment()
        val tag = item.fragmentTag

        if (fragment != null && tag != null) {

            supportFragmentManager.beginTransaction()
                .replace(R.id.detail_fragment_container, fragment, tag)
                .commit()

            val wic = WindowInsetsControllerCompat(window, window.decorView)
            wic.isAppearanceLightStatusBars = true

            val transform = MaterialContainerTransform().apply {
                startView = cardView
                endView = binding.detailFragmentContainer
                addTarget(binding.detailFragmentContainer)
                duration = 450L
                scrimColor = Color.TRANSPARENT
                setAllContainerColors(getColor(R.color.aura_white))
            }

            TransitionManager.beginDelayedTransition(binding.main, transform)
            binding.detailFragmentContainer.visibility = View.VISIBLE

            activeCardView = cardView
            activeItem = item
            backCallback.isEnabled = true
            return
        }

        activeCardView = cardView
        activeItem = item

        val title = getString(item.titleRes)

        // 1. Configurar los contenidos del detalle antes de la transición
        binding.detailView.tvDetailTitle.text = title
        binding.detailView.ivDetailIcon.setImageResource(item.iconRes)
        binding.detailView.ivBigIcon.setImageResource(item.iconRes)
        binding.detailView.tvComingSoonDesc.text = getString(R.string.coming_soon_description, title)

        // 2. Configurar iconos oscuros en la barra de estado para el fondo blanco del detalle
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.isAppearanceLightStatusBars = true

        // 3. Crear y configurar el Material Container Transform para la vista a vista
        val transform = MaterialContainerTransform().apply {
            startView = cardView
            endView = binding.detailView.root
            addTarget(binding.detailView.root)
            duration = 450L
            scrimColor = Color.TRANSPARENT
            setAllContainerColors(getColor(R.color.aura_white))
        }

        // 4. Iniciar la transición
        TransitionManager.beginDelayedTransition(binding.main, transform)
        binding.detailView.root.visibility = View.VISIBLE

        // 5. Animar el contenido del detalle (fade-in) para una visualización fluida
        binding.detailView.detailContent.alpha = 0f
        binding.detailView.detailContent.animate()
            .alpha(1f)
            .setDuration(250)
            .setStartDelay(200)
            .start()

        // 6. Habilitar callback de botón físico atrás
        backCallback.isEnabled = true
    }

    override fun navigateToDetailWithFade(title: String, iconRes: Int) {
        activeCardView = null
        activeItem = null

        // 1. Configurar los contenidos del detalle
        binding.detailView.tvDetailTitle.text = title
        binding.detailView.ivDetailIcon.setImageResource(iconRes)
        binding.detailView.ivBigIcon.setImageResource(iconRes)
        binding.detailView.tvComingSoonDesc.text = getString(R.string.coming_soon_description, title)

        // 2. Configurar barra de estado
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        wic.isAppearanceLightStatusBars = true

        // 3. Mostrar la vista con animación de fade/alpha
        binding.detailView.root.alpha = 0f
        binding.detailView.root.visibility = View.VISIBLE
        binding.detailView.detailContent.alpha = 1f // El contenido interno se ve directamente con el padre
        
        binding.detailView.root.animate()
            .alpha(1f)
            .setDuration(300L)
            .start()

        // 4. Habilitar callback de botón físico atrás
        backCallback.isEnabled = true
    }

    private fun closeDetail() {
        backCallback.isEnabled = false
        val card = activeCardView
        
        val isFragmentDetail = binding.detailFragmentContainer.visibility == View.VISIBLE
        val targetViewToHide = if (isFragmentDetail) binding.detailFragmentContainer else binding.detailView.root

        if (card == null) {
            targetViewToHide.animate()
                .alpha(0f)
                .setDuration(250L)
                .withEndAction {
                    targetViewToHide.visibility = View.GONE
                    restoreStatusBarTheme()
                }
                .start()
            return
        }

        val transform = MaterialContainerTransform().apply {
            startView = targetViewToHide
            endView = card
            addTarget(card)
            duration = 350L
            scrimColor = Color.TRANSPARENT
            setAllContainerColors(getColor(R.color.aura_white))
            addListener(object : androidx.transition.Transition.TransitionListener {
                override fun onTransitionEnd(transition: androidx.transition.Transition) {
                    if (isFragmentDetail) {
                        // Limpiar el fragment container genéricamente
                        val fragment = supportFragmentManager.findFragmentById(R.id.detail_fragment_container)
                        if (fragment != null) {
                            supportFragmentManager.beginTransaction()
                                .remove(fragment)
                                .commitAllowingStateLoss()
                        }
                    }
                }
                override fun onTransitionStart(transition: androidx.transition.Transition) {}
                override fun onTransitionCancel(transition: androidx.transition.Transition) {}
                override fun onTransitionPause(transition: androidx.transition.Transition) {}
                override fun onTransitionResume(transition: androidx.transition.Transition) {}
            })
        }


        TransitionManager.beginDelayedTransition(binding.main, transform)
        targetViewToHide.visibility = View.GONE

        if (!isFragmentDetail) {
            binding.detailView.detailContent.animate()
                .alpha(0f)
                .setDuration(180)
                .start()
        }

        restoreStatusBarTheme()
        activeCardView = null
        activeItem = null
    }

    private fun restoreStatusBarTheme() {
        val currentTab = binding.viewPager.currentItem
        val wic = WindowInsetsControllerCompat(window, window.decorView)
        if (currentTab == 0) {
            // Obtener el fragmento de Dashboard si es que está disponible para consultar su scroll
            val homeFragment = supportFragmentManager.findFragmentByTag("f0") as? HomeFragment
            if (homeFragment != null && homeFragment.isAdded && homeFragment.view != null) {
                // Si el fragmento está disponible, restaurar su tema según el scroll actual
                homeFragment.updateStatusBarTheme()
            } else {
                // Fallback seguro: restaurar según el scroll que tendría (generalmente light icons si no se ha scrolled)
                wic.isAppearanceLightStatusBars = false
            }
        } else {
            // Para cualquier otra pestaña, los iconos de la barra de estado deben ser negros (fondo claro)
            wic.isAppearanceLightStatusBars = true
        }
    }

    private class MainPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> HomeFragment()
                1 -> NotificationsFragment()
                2 -> MessagesFragment()
                3 -> ProfileFragment()
                else -> HomeFragment()
            }
        }
    }

    /**
     * Muestra una pantalla de error que cubre TODA la actividad (incluyendo la barra de navegación).
     */
    fun showGlobalError(title: String, message: String, onRetry: () -> Unit) {
        binding.tvErrorTitle.text = title
        binding.tvErrorMessage.text = message
        
        binding.btnErrorRetry.setOnClickListener {
            binding.layoutErrorOverlay.visibility = View.GONE
            onRetry()
        }
        
        // Hacemos visible el overlay global
        binding.layoutErrorOverlay.visibility = View.VISIBLE
    }
}