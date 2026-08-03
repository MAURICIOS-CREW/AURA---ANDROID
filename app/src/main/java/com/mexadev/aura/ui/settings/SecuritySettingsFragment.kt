package com.mexadev.aura.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.databinding.FragmentSecuritySettingsBinding

class SecuritySettingsFragment : Fragment() {

    private var _binding: FragmentSecuritySettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferencesManager: PreferencesManager

    private val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            isEnabled = false
            parentFragmentManager.popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecuritySettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        preferencesManager = PreferencesManager(requireContext())
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        setupWindowInsets()

        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        
        binding.switchFingerprint.isChecked = preferencesManager.biometricSessionRenewal
        binding.switchLockOnExit.isChecked = preferencesManager.lockOnExit
        
        binding.switchFingerprint.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.biometricSessionRenewal = isChecked
        }
        
        binding.switchLockOnExit.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.lockOnExit = isChecked
        }
        
        applySwitchPhysics(binding.switchFingerprint, binding.switchLockOnExit)
        
        binding.btnChangePassword.setOnClickListener {
            Toast.makeText(requireContext(), "Solicitud de cambio de contraseña enviada (Dummy)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupWindowInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.securitySettingsRoot) { _, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())

            val topPadding = systemBars.top
            val bottomPadding = kotlin.math.max(systemBars.bottom, ime.bottom) + (16 * resources.displayMetrics.density).toInt()
            binding.securitySettingsRoot.setPadding(0, topPadding, 0, bottomPadding)
            insets
        }
    }

    private fun applySwitchPhysics(vararg switches: com.google.android.material.materialswitch.MaterialSwitch) {
        switches.forEach { switchView ->
            switchView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
                    }
                }
                false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
