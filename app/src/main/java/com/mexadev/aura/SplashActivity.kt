package com.mexadev.aura

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_splash)

        val logoContainer = findViewById<View>(R.id.splash_logo_container)
        val icon = findViewById<View>(R.id.splash_icon)
        val tagline = findViewById<View>(R.id.splash_tagline)
        val dot1 = findViewById<View>(R.id.splash_dot_1)
        val dot2 = findViewById<View>(R.id.splash_dot_2)
        val dot3 = findViewById<View>(R.id.splash_dot_3)

        // Initially hide all elements
        logoContainer.alpha = 0f
        logoContainer.scaleX = 0.3f
        logoContainer.scaleY = 0.3f
        tagline.alpha = 0f
        tagline.translationY = 25f
        dot1.alpha = 0f
        dot2.alpha = 0f
        dot3.alpha = 0f

        // 1) Logo container: scale in with overshoot
        val logoAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logoContainer, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(logoContainer, "scaleX", 0.3f, 1f),
                ObjectAnimator.ofFloat(logoContainer, "scaleY", 0.3f, 1f)
            )
            duration = 900
            interpolator = OvershootInterpolator(1.3f)
        }

        // 3) Tagline: slide up + fade
        val taglineAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(tagline, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(tagline, "translationY", 25f, 0f)
            )
            duration = 600
            startDelay = 800
            interpolator = DecelerateInterpolator()
        }

        // 4) Dots: staggered fade in
        val d1 = ObjectAnimator.ofFloat(dot1, "alpha", 0f, 1f).apply {
            duration = 350
            startDelay = 1100
        }

        val d2 = ObjectAnimator.ofFloat(dot2, "alpha", 0f, 1f).apply {
            duration = 350
            startDelay = 1250
        }

        val d3 = ObjectAnimator.ofFloat(dot3, "alpha", 0f, 1f).apply {
            duration = 350
            startDelay = 1400
        }

        // Play all together
        AnimatorSet().apply {
            playTogether(logoAnim, taglineAnim, d1, d2, d3)
            start()
        }

        // Start dot pulse after initial animation
        handler.postDelayed({
            startDotPulse(dot1, 0)
            startDotPulse(dot2, 250)
            startDotPulse(dot3, 500)
        }, 1800)

        // Gentle floating animation on the logo icon
        handler.postDelayed({
            ObjectAnimator.ofFloat(icon, "translationY", 0f, -12f, 0f).apply {
                duration = 2500
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }, 1000)

        // Navigate to LoginActivity
        handler.postDelayed({
            startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 3200)
    }

    private fun startDotPulse(dot: View, delay: Long) {
        val pulse = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(dot, "scaleX", 1f, 1.6f, 1f),
                ObjectAnimator.ofFloat(dot, "scaleY", 1f, 1.6f, 1f),
                ObjectAnimator.ofFloat(dot, "alpha", 1f, 0.4f, 1f)
            )
            duration = 1100
            startDelay = delay
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        pulse.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (!isFinishing) {
                    pulse.startDelay = delay
                    pulse.start()
                }
            }
        })
        pulse.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
