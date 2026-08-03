package com.mexadev.aura.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.mexadev.aura.R
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.databinding.FragmentNotificationSettingsBinding

class NotificationSettingsFragment : Fragment() {

    private var _binding: FragmentNotificationSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferencesManager: PreferencesManager
    private var isQrSelected = true

    private val importanceDisplayMap by lazy {
        linkedMapOf(
            "high" to getString(R.string.settings_notifications_importance_high),
            "medium" to getString(R.string.settings_notifications_importance_medium),
            "low" to getString(R.string.settings_notifications_importance_low),
            "off" to getString(R.string.settings_notifications_importance_off)
        )
    }

    private val importanceDescMap by lazy {
        mapOf(
            "high" to getString(R.string.settings_notifications_desc_high),
            "medium" to getString(R.string.settings_notifications_desc_medium),
            "low" to getString(R.string.settings_notifications_desc_low),
            "off" to getString(R.string.settings_notifications_desc_off)
        )
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            isEnabled = false
            parentFragmentManager.popBackStack()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferencesManager = PreferencesManager(requireContext())

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        setupWindowInsets()
        setupListeners()
        loadInitialState()
        applySwitchPhysics(binding.switchNotificationsMaster)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.notificationSettingsRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            val topPadding = systemBars.top
            val bottomPadding = kotlin.math.max(systemBars.bottom, ime.bottom) + (16 * resources.displayMetrics.density).toInt()
            binding.notificationSettingsRoot.setPadding(0, topPadding, 0, bottomPadding)
            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.switchNotificationsMaster.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.notificationsEnabled = isChecked
            animateMasterToggleState(isChecked)
        }

        // Setup AuraSegmentedControl reutilizable
        binding.segmentedControlAccessMethod.setTabs(
            listOf(
                getString(R.string.settings_notifications_method_qr),
                getString(R.string.settings_notifications_method_placa)
            ),
            defaultIndex = 0
        )

        binding.segmentedControlAccessMethod.setOnTabSelectedListener { position, _ ->
            isQrSelected = (position == 0)
            loadAccessImportanceForSelectedMethod()
        }

        // Setup Select Inputs estilo Aura
        binding.tvAccessImportanceSelect.setOnClickListener { showAccessImportancePicker() }
        binding.tvIncidentImportanceSelect.setOnClickListener { showIncidentImportancePicker() }
    }

    private fun showAccessImportancePicker() {
        val popup = PopupMenu(requireContext(), binding.tvAccessImportanceSelect)
        importanceDisplayMap.forEach { (key, displayValue) ->
            popup.menu.add(displayValue)
        }

        popup.setOnMenuItemClickListener { item ->
            val selectedKey = importanceDisplayMap.entries.find { it.value == item.title }?.key ?: "high"
            if (isQrSelected) {
                preferencesManager.importanceAccessQr = selectedKey
            } else {
                preferencesManager.importanceAccessPlaca = selectedKey
            }
            binding.tvAccessImportanceSelect.text = item.title
            updateAccessDescription(selectedKey)
            true
        }
        popup.show()
    }

    private fun showIncidentImportancePicker() {
        val popup = PopupMenu(requireContext(), binding.tvIncidentImportanceSelect)
        importanceDisplayMap.forEach { (_, displayValue) ->
            popup.menu.add(displayValue)
        }

        popup.setOnMenuItemClickListener { item ->
            val selectedKey = importanceDisplayMap.entries.find { it.value == item.title }?.key ?: "high"
            preferencesManager.importanceIncident = selectedKey
            binding.tvIncidentImportanceSelect.text = item.title
            updateIncidentDescription(selectedKey)
            true
        }
        popup.show()
    }

    private fun loadInitialState() {
        val isMasterEnabled = preferencesManager.notificationsEnabled
        binding.switchNotificationsMaster.isChecked = isMasterEnabled
        setDetailedSettingsState(isMasterEnabled, animate = false)

        isQrSelected = true
        loadAccessImportanceForSelectedMethod()

        val incidentImportance = preferencesManager.importanceIncident
        val incidentDisplay = importanceDisplayMap[incidentImportance] ?: importanceDisplayMap["high"]!!
        binding.tvIncidentImportanceSelect.text = incidentDisplay
        updateIncidentDescription(incidentImportance)
    }

    private fun loadAccessImportanceForSelectedMethod() {
        val importanceKey = if (isQrSelected) {
            preferencesManager.importanceAccessQr
        } else {
            preferencesManager.importanceAccessPlaca
        }

        val displayValue = importanceDisplayMap[importanceKey] ?: importanceDisplayMap["high"]!!
        binding.tvAccessImportanceSelect.text = displayValue
        updateAccessDescription(importanceKey)

        val methodLabel = if (isQrSelected) {
            getString(R.string.settings_notifications_method_qr)
        } else {
            getString(R.string.settings_notifications_method_placa)
        }
        binding.tvAccessImportanceLabel.text = "${getString(R.string.settings_notifications_importance_label)} ($methodLabel)"
    }

    private fun updateAccessDescription(importanceKey: String) {
        binding.tvAccessImportanceDesc.text = importanceDescMap[importanceKey] ?: importanceDescMap["high"]
    }

    private fun updateIncidentDescription(importanceKey: String) {
        binding.tvIncidentImportanceDesc.text = importanceDescMap[importanceKey] ?: importanceDescMap["high"]
    }

    private fun animateMasterToggleState(isEnabled: Boolean) {
        TransitionManager.beginDelayedTransition(binding.notificationSettingsRoot, AutoTransition().setDuration(250))
        setDetailedSettingsState(isEnabled, animate = true)
    }

    private fun setDetailedSettingsState(isEnabled: Boolean, animate: Boolean) {
        val targetAlpha = if (isEnabled) 1.0f else 0.4f
        if (animate) {
            binding.layoutDetailedSettings.animate().alpha(targetAlpha).setDuration(200).start()
        } else {
            binding.layoutDetailedSettings.alpha = targetAlpha
        }
        setViewGroupEnabled(binding.layoutDetailedSettings, isEnabled)
    }

    private fun setViewGroupEnabled(viewGroup: ViewGroup, enabled: Boolean) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            child.isEnabled = enabled
            if (child is ViewGroup) {
                setViewGroupEnabled(child, enabled)
            }
        }
    }

    private fun applySwitchPhysics(vararg switches: com.google.android.material.materialswitch.MaterialSwitch) {
        switches.forEach { switchView ->
            switchView.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(250)
                            .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
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
