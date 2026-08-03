package com.mexadev.aura.ui.common

import android.annotation.SuppressLint
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.mexadev.aura.R
import androidx.core.content.withStyledAttributes

/**
 * [AuraCardBtn]
 * Estandarización de tarjetas y tarjetas-botón para la aplicación AURA.
 * 
 * Cumple con principios SOLID:
 * - SRP: Manejo de presentación de tarjetas, protección de color blanco e interacciones táctiles con física.
 * - OCP: Personalizable vía attrs.xml y estilos sin alterar la clase.
 * - LSP: Hereda de MaterialCardView, totalmente compatible en cualquier XML.
 * - ISP / DIP: Integra interfaces limpias de Android framework.
 */
class AuraCardBtn @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle
) : MaterialCardView(context, attrs, defStyleAttr) {

    var clickAnimationEnabled: Boolean = true
    var clickScale: Float = 0.97f
    var isPureWhite: Boolean = true

    private var touchDownAnimator: AnimatorSet? = null
    private var touchUpAnimator: AnimatorSet? = null
    private val viewRect = Rect()
    private var isTouchCancelled = false

    init {
        val density = resources.displayMetrics.density

        // Verificar si se especificaron atributos explícitos en XML antes de asignar valores por defecto
        val hasCustomCorner = attrs?.getAttributeValue("http://schemas.android.com/apk/res-auto", "cardCornerRadius") != null
        val hasCustomElevation = attrs?.getAttributeValue("http://schemas.android.com/apk/res-auto", "cardElevation") != null || 
                                 attrs?.getAttributeValue("http://schemas.android.com/apk/res/android", "elevation") != null
        val hasCustomStrokeWidth = attrs?.getAttributeValue("http://schemas.android.com/apk/res-auto", "strokeWidth") != null
        val hasCustomStrokeColor = attrs?.getAttributeValue("http://schemas.android.com/apk/res-auto", "strokeColor") != null

        if (!hasCustomCorner) radius = 16f * density
        if (!hasCustomElevation) cardElevation = 1f * density
        if (!hasCustomStrokeWidth) strokeWidth = (1f * density).toInt()
        if (!hasCustomStrokeColor) strokeColor = ContextCompat.getColor(context, R.color.aura_border_light)

        // Configuración de atributos desde XML si están presentes
        attrs?.let { attributeSet ->
            context.withStyledAttributes(attributeSet, R.styleable.AuraCardBtn, defStyleAttr, 0) {
                clickAnimationEnabled =
                    getBoolean(R.styleable.AuraCardBtn_cardClickAnimationEnabled, true)
                clickScale = getFloat(R.styleable.AuraCardBtn_cardClickScale, 0.97f)
                isPureWhite = getBoolean(R.styleable.AuraCardBtn_isPureWhite, true)
            }
        }

        if (isPureWhite) {
            val whiteColor = ContextCompat.getColor(context, R.color.aura_white)
            setCardBackgroundColor(ColorStateList.valueOf(whiteColor))
        }

        setRippleColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.aura_ripple)))

        // Aseguramos que por defecto sea interactivo si se comporta como botón
        isClickable = true
        isFocusable = true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !isClickable || !clickAnimationEnabled) {
            return super.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTouchCancelled = false
                getHitRect(viewRect)
                animateTouchDown()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isTouchCancelled) {
                    val localX = event.x.toInt()
                    val localY = event.y.toInt()
                    if (localX < 0 || localX > width || localY < 0 || localY > height) {
                        isTouchCancelled = true
                        animateTouchUp()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isTouchCancelled) {
                    animateTouchUp()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                isTouchCancelled = true
                animateTouchUp()
            }
        }

        return super.onTouchEvent(event)
    }

    private fun animateTouchDown() {
        touchUpAnimator?.cancel()
        touchDownAnimator?.cancel()

        val scaleX = ObjectAnimator.ofFloat(this, "scaleX", clickScale)
        val scaleY = ObjectAnimator.ofFloat(this, "scaleY", clickScale)

        touchDownAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 100
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun animateTouchUp() {
        touchDownAnimator?.cancel()
        touchUpAnimator?.cancel()

        val scaleX = ObjectAnimator.ofFloat(this, "scaleX", 1.0f)
        val scaleY = ObjectAnimator.ofFloat(this, "scaleY", 1.0f)

        touchUpAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 180
            interpolator = OvershootInterpolator(1.4f)
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        touchDownAnimator?.cancel()
        touchUpAnimator?.cancel()
    }
}
