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
        
        binding.switchFingerprint.isChecked = preferencesManager.biometricSessionRenewal
        binding.switchLockOnExit.isChecked = preferencesManager.lockOnExit
        
        binding.switchFingerprint.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.biometricSessionRenewal = isChecked
        }
        
        binding.switchLockOnExit.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.lockOnExit = isChecked
        }
        
        binding.btnChangePassword.setOnClickListener {
            Toast.makeText(requireContext(), "Solicitud de cambio de contraseña enviada (Dummy)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
