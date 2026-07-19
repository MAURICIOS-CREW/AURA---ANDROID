package com.mexadev.aura.ui.common

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/**
 * BannerManager
 * 
 * Clase reutilizable para manejar la visualización de banners de notificación 
 * (éxito, error, advertencia) en la aplicación, siguiendo principios SOLID.
 * Encapsula la lógica de animaciones y el ciclo de vida del Runnable para
 * ocultar automáticamente el banner sin dejar Memory Leaks.
 */
class BannerManager(
    private val bannerView: View,
    private val textView: TextView,
    private val autoHideDelayMillis: Long = 4000L
) {
    private var hideRunnable: Runnable? = null

    /**
     * Muestra el banner con el mensaje especificado.
     * Si el banner ya es visible, actualiza el mensaje y reinicia el temporizador.
     */
    fun show(message: String) {
        textView.text = message

        cancelHideRunnable()

        if (bannerView.visibility != View.VISIBLE) {
            bannerView.alpha = 0f
            bannerView.visibility = View.VISIBLE
            bannerView.post {
                val height = bannerView.height.toFloat()
                bannerView.translationY = -height
                bannerView.alpha = 1f
                bannerView.animate()
                    .translationY(0f)
                    .setDuration(300)
                    .setInterpolator(OvershootInterpolator(1.0f))
                    .withEndAction { scheduleHide() }
                    .start()
            }
        } else {
            // Si ya está visible, cancelamos animaciones anteriores y reiniciamos temporizador
            bannerView.animate().cancel()
            bannerView.translationY = 0f
            scheduleHide()
        }
    }

    /**
     * Oculta el banner con animación.
     */
    fun hide() {
        cancelHideRunnable()
        if (bannerView.visibility == View.VISIBLE) {
            val height = bannerView.height.toFloat()
            bannerView.animate()
                .translationY(-height)
                .setDuration(300)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { bannerView.visibility = View.GONE }
                .start()
        }
    }

    /**
     * Limpia los recursos y callbacks pendientes.
     * Debe llamarse en el onDestroy() o onDestroyView() del ciclo de vida.
     */
    fun destroy() {
        cancelHideRunnable()
        bannerView.animate().cancel()
    }

    private fun scheduleHide() {
        hideRunnable = Runnable { hide() }
        bannerView.postDelayed(hideRunnable, autoHideDelayMillis)
    }

    private fun cancelHideRunnable() {
        hideRunnable?.let { bannerView.removeCallbacks(it) }
        hideRunnable = null
    }
}
