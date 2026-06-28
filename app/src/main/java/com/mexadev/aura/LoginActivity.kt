package com.mexadev.aura

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.mexadev.aura.core.session.SessionManager
import com.mexadev.aura.ui.login.LoginState
import com.mexadev.aura.ui.login.LoginViewModel
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private var passwordVisible = false
    private val handler = Handler(Looper.getMainLooper())
    
    // Inject SessionManager through a factory
    private val viewModel: LoginViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LoginViewModel(SessionManager(this@LoginActivity)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setContentView(R.layout.activity_login)

        setupPasswordToggle()
        runEntranceAnimations()
        startLogoFloat()
        
        val bg = findViewById<ImageView>(R.id.login_background)
        bg?.setRenderEffect(RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP))


        setupLoginFlow()
    }

    private fun setupLoginFlow() {
        val loginButton = findViewById<View>(R.id.btn_login)
        val emailInput = findViewById<EditText>(R.id.input_email)
        val passwordInput = findViewById<EditText>(R.id.input_password)

        loginButton?.setOnClickListener {
            val email = emailInput?.text.toString()
            val password = passwordInput?.text.toString()
            viewModel.login(email, password)
        }

        lifecycleScope.launch {
            viewModel.loginState.collect { state ->
                when (state) {
                    is LoginState.Loading -> {
                        // Podríamos deshabilitar el botón y mostrar un loading
                        loginButton?.alpha = 0.5f
                        loginButton?.isEnabled = false
                    }
                    is LoginState.Success -> {
                        loginButton?.alpha = 1f
                        loginButton?.isEnabled = true
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                    is LoginState.Error -> {
                        loginButton?.alpha = 1f
                        loginButton?.isEnabled = true
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }
    }

    private fun setupPasswordToggle() {
        val passwordInput = findViewById<EditText>(R.id.input_password) ?: return
        val toggleIcon = findViewById<ImageView>(R.id.toggle_password) ?: return

        toggleIcon.setOnClickListener {
            passwordVisible = !passwordVisible

            if (passwordVisible) {
                passwordInput.transformationMethod = HideReturnsTransformationMethod.getInstance()
                toggleIcon.setImageResource(R.drawable.ic_eye_off)
            } else {
                passwordInput.transformationMethod = PasswordTransformationMethod.getInstance()
                toggleIcon.setImageResource(R.drawable.ic_eye)
            }

            // Keep cursor at end of text
            passwordInput.setSelection(passwordInput.text.length)

            // Subtle icon animation
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(toggleIcon, "scaleX", 0.7f, 1f),
                    ObjectAnimator.ofFloat(toggleIcon, "scaleY", 0.7f, 1f),
                    ObjectAnimator.ofFloat(toggleIcon, "alpha", 0.5f, 1f)
                )
                duration = 250
                interpolator = OvershootInterpolator()
                start()
            }
        }
    }

    private fun runEntranceAnimations() {
        // Get all animated views
        val logo = findViewById<View>(R.id.login_logo_container)
        val welcome = findViewById<View>(R.id.login_welcome)
        val subtitle = findViewById<View>(R.id.login_subtitle)
        val emailInput = findViewById<View>(R.id.input_email_container)
        val passwordInput = findViewById<View>(R.id.input_password_container)
        val forgotPassword = findViewById<View>(R.id.link_forgot_password)
        val loginButton = findViewById<View>(R.id.btn_login)
        val socialButtons = findViewById<View>(R.id.social_buttons_container)
        val registerLink = findViewById<View>(R.id.link_register_container)

        val views = arrayOf(logo, welcome, subtitle, emailInput, passwordInput,
            forgotPassword, loginButton, socialButtons, registerLink)

        // Hide all initially
        for (v in views) {
            v?.alpha = 0f
            v?.translationY = 35f
        }

        // Logo: special scale animation
        logo?.scaleX = 0.5f
        logo?.scaleY = 0.5f
        logo?.translationY = 0f

        val decelerate = DecelerateInterpolator(1.5f)
        val overshoot = OvershootInterpolator(1.1f)

        // Stagger delays (ms)
        val delays = intArrayOf(0, 200, 320, 500, 650, 780, 920, 1080, 1180, 1300)

        // 0 - Logo: scale + fade
        val logoAnim = AnimatorSet().apply {
            if (logo != null) {
                playTogether(
                    ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(logo, "scaleX", 0.5f, 1f),
                    ObjectAnimator.ofFloat(logo, "scaleY", 0.5f, 1f)
                )
            }
            duration = 700
            startDelay = delays[0].toLong()
            interpolator = overshoot
        }

        // Create slide-up + fade for remaining views
        val masterAnim = AnimatorSet()
        masterAnim.play(logoAnim)

        for (i in 1 until views.size) {
            val view = views[i] ?: continue
            val anim = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                    ObjectAnimator.ofFloat(view, "translationY", 35f, 0f)
                )
                duration = 550
                startDelay = delays[i].toLong()
                interpolator = if (i == 6) overshoot else decelerate
            }
            masterAnim.play(anim)
        }

        masterAnim.start()
    }

    private fun startLogoFloat() {
        val logo = findViewById<View>(R.id.login_logo_container) ?: return

        handler.postDelayed({
            ObjectAnimator.ofFloat(logo, "translationY", 0f, -8f, 0f, 4f, 0f).apply {
                duration = 3500
                repeatCount = ObjectAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }, 1500)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
