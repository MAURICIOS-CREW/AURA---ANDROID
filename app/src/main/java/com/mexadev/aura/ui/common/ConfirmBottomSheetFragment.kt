package com.mexadev.aura.ui.common

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mexadev.aura.R
import com.mexadev.aura.databinding.FragmentConfirmBottomSheetBinding

class ConfirmBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentConfirmBottomSheetBinding? = null
    private val binding get() = _binding!!

    var title: String = "Confirmación"
    var message: String = "¿Estás seguro?"
    var confirmText: String = "Confirmar"
    var cancelText: String = "Cancelar"
    
    @DrawableRes var iconRes: Int = R.drawable.ic_warning
    @ColorRes var iconColorRes: Int = R.color.aura_primary
    @ColorRes var confirmTextColorRes: Int = R.color.aura_white
    @DrawableRes var confirmBgRes: Int = R.drawable.bg_btn_primary

    var onConfirm: (() -> Unit)? = null
    var onCancel: (() -> Unit)? = null

    private var blurAnimator: ValueAnimator? = null

    override fun getTheme(): Int {
        return com.google.android.material.R.style.Theme_Design_BottomSheetDialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfirmBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvTitle.text = title
        binding.tvMessage.text = message
        binding.btnConfirm.text = confirmText
        binding.btnCancel.text = cancelText

        binding.ivIcon.setImageResource(iconRes)
        binding.ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), iconColorRes))
        
        binding.btnConfirm.setTextColor(ContextCompat.getColor(requireContext(), confirmTextColorRes))
        binding.btnConfirm.setBackgroundResource(confirmBgRes)

        setupPhysicsInteractions()
        runPhysicsEntranceAnimations()
        applyBackgroundBlurAnimation(true)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.window?.let { window ->
            window.setDimAmount(0.55f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes.blurBehindRadius = 24
            }
        }
        
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            sheet.setBackgroundResource(android.R.color.transparent)
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    private fun applyBackgroundBlurAnimation(show: Boolean, onComplete: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onComplete?.invoke()
            return
        }

        val targetView = activity?.findViewById<View>(android.R.id.content) ?: run {
            onComplete?.invoke()
            return
        }

        blurAnimator?.cancel()

        val startValue = if (show) 0.1f else 16f
        val endValue = if (show) 16f else 0f

        blurAnimator = ValueAnimator.ofFloat(startValue, endValue).apply {
            duration = if (show) 320L else 220L
            interpolator = if (show) DecelerateInterpolator() else AccelerateInterpolator()
            addUpdateListener { anim ->
                val radius = anim.animatedValue as Float
                if (radius > 0.5f) {
                    targetView.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
                } else {
                    targetView.setRenderEffect(null)
                }
            }
            doOnEnd {
                if (!show) {
                    targetView.setRenderEffect(null)
                }
                onComplete?.invoke()
            }
            start()
        }
    }

    private fun dismissWithBlurAnimation(onDismissed: () -> Unit) {
        applyBackgroundBlurAnimation(false) {
            onDismissed()
            dismissAllowingStateLoss()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPhysicsInteractions() {
        attachSpringTouch(binding.btnConfirm) {
            onConfirm?.invoke()
            dismissWithBlurAnimation {}
        }

        attachSpringTouch(binding.btnCancel) {
            onCancel?.invoke()
            dismissWithBlurAnimation {}
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachSpringTouch(view: View, onClick: () -> Unit) {
        val springXCompress = SpringAnimation(view, DynamicAnimation.SCALE_X, 0.94f).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
        val springYCompress = SpringAnimation(view, DynamicAnimation.SCALE_Y, 0.94f).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }

        val springXRelease = SpringAnimation(view, DynamicAnimation.SCALE_X, 1.0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        val springYRelease = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1.0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }

        var isPressedInside = false

        view.setOnTouchListener { v, event ->
            val isInside = event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPressedInside = true
                    v.drawableHotspotChanged(event.x, event.y)
                    v.isPressed = true
                    springXRelease.cancel()
                    springYRelease.cancel()
                    springXCompress.start()
                    springYCompress.start()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isInside && !isPressedInside) {
                        isPressedInside = true
                        v.drawableHotspotChanged(event.x, event.y)
                        v.isPressed = true
                        springXRelease.cancel()
                        springYRelease.cancel()
                        springXCompress.start()
                        springYCompress.start()
                    } else if (!isInside && isPressedInside) {
                        isPressedInside = false
                        v.isPressed = false
                        springXCompress.cancel()
                        springYCompress.cancel()
                        springXRelease.start()
                        springYRelease.start()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val shouldClick = isPressedInside && isInside
                    isPressedInside = false
                    v.isPressed = false
                    springXCompress.cancel()
                    springYCompress.cancel()
                    springXRelease.start()
                    springYRelease.start()
                    if (shouldClick) {
                        v.postDelayed({ onClick() }, 50)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    isPressedInside = false
                    v.isPressed = false
                    springXCompress.cancel()
                    springYCompress.cancel()
                    springXRelease.start()
                    springYRelease.start()
                }
            }
            true
        }
    }

    private fun runPhysicsEntranceAnimations() {
        // 1. Icon Pop-in with a crisp, noticeable spring overshoot bounce (MEDIUM_BOUNCY)
        binding.ivIcon.scaleX = 0.35f
        binding.ivIcon.scaleY = 0.35f
        binding.ivIcon.alpha = 0f

        val iconSpringX = SpringAnimation(binding.ivIcon, DynamicAnimation.SCALE_X, 1.0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        val iconSpringY = SpringAnimation(binding.ivIcon, DynamicAnimation.SCALE_Y, 1.0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }

        binding.ivIcon.animate().alpha(1f).setDuration(220).start()
        iconSpringX.start()
        iconSpringY.start()

        // 2. Multi-property Physics Spring Cascade for Content (TranslationY + Elastic Scale)
        val views = arrayOf(binding.tvTitle, binding.tvMessage, binding.btnConfirm, binding.btnCancel)
        for ((index, v) in views.withIndex()) {
            v.alpha = 0f
            v.translationY = 40f
            v.scaleX = 0.94f
            v.scaleY = 0.94f

            val springY = SpringAnimation(v, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                spring.stiffness = SpringForce.STIFFNESS_LOW
                spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }
            val springScaleX = SpringAnimation(v, DynamicAnimation.SCALE_X, 1.0f).apply {
                spring.stiffness = SpringForce.STIFFNESS_LOW
                spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }
            val springScaleY = SpringAnimation(v, DynamicAnimation.SCALE_Y, 1.0f).apply {
                spring.stiffness = SpringForce.STIFFNESS_LOW
                spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }

            v.postDelayed({
                if (_binding == null) return@postDelayed
                v.animate().alpha(1f).setDuration(200).start()
                springY.start()
                springScaleX.start()
                springScaleY.start()
            }, 40L + index * 35L)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        blurAnimator?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            activity?.findViewById<View>(android.R.id.content)?.setRenderEffect(null)
        }
        _binding = null
    }

    companion object {
        fun newInstance(
            title: String,
            message: String,
            confirmText: String = "Confirmar",
            cancelText: String = "Cancelar",
            @DrawableRes iconRes: Int = R.drawable.ic_warning,
            @ColorRes iconColorRes: Int = R.color.aura_primary,
            @ColorRes confirmTextColorRes: Int = R.color.aura_white,
            @DrawableRes confirmBgRes: Int = R.drawable.bg_btn_primary,
            onConfirm: () -> Unit,
            onCancel: (() -> Unit)? = null
        ): ConfirmBottomSheetFragment {
            return ConfirmBottomSheetFragment().apply {
                this.title = title
                this.message = message
                this.confirmText = confirmText
                this.cancelText = cancelText
                this.iconRes = iconRes
                this.iconColorRes = iconColorRes
                this.confirmTextColorRes = confirmTextColorRes
                this.confirmBgRes = confirmBgRes
                this.onConfirm = onConfirm
                this.onCancel = onCancel
            }
        }
    }
}
