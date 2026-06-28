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
import com.mexadev.aura.ui.home.HomeFragment
import com.mexadev.aura.ui.notifications.NotificationsFragment
import com.mexadev.aura.ui.messages.MessagesFragment
import com.mexadev.aura.ui.profile.ProfileFragment
import kotlinx.coroutines.launch
import com.mexadev.aura.databinding.ActivityMainBinding
import com.mexadev.aura.core.session.SessionManager
import com.mexadev.aura.core.session.BiometricHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
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
            }
        })

        // Botón FAB central — vuelve a Home
        binding.fabCenter.setOnClickListener {
            animateFabPress()
            if (binding.viewPager.currentItem != 0) {
                binding.viewPager.currentItem = 0
            }
        }

        // Biometría para renovación de sesión
        val sessionManager = SessionManager(this)
        lifecycleScope.launch {
            sessionManager.biometricRequestFlow.collect { callback ->
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
}