package com.mexadev.aura.ui.profile

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.mexadev.aura.R
import com.mexadev.aura.databinding.FragmentProfileBinding
import com.mexadev.aura.ui.common.BannerManager
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var errorBanner: BannerManager
    private var shimmerAnimator: ValueAnimator? = null

    private var isEditMode = false

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isEditMode) {
                toggleEditMode(false)
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        errorBanner = BannerManager(binding.errorBanner, binding.tvErrorBannerMessage)

        setupWindowInsets()
        setupListeners()
        setupObservers()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        viewModel.fetchProfile()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.profileRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            val bottomPadding = kotlin.math.max(systemBars.bottom, ime.bottom)
            binding.profileRoot.setPadding(0, 0, 0, bottomPadding)

            val basePadding = (16 * resources.displayMetrics.density).toInt()
            binding.errorBanner.setPadding(
                basePadding,
                systemBars.top + basePadding,
                basePadding,
                basePadding
            )
            insets
        }
    }

    private fun setupListeners() {
        binding.cardSecurity.setOnClickListener {
            if (isEditMode) return@setOnClickListener
            requireActivity().supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.nav_slide_in_right,
                    R.anim.nav_slide_out_left,
                    R.anim.nav_slide_in_left,
                    R.anim.nav_slide_out_right
                )
                .add(R.id.main, com.mexadev.aura.ui.settings.SecuritySettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnEditProfile.setOnClickListener { toggleEditMode(true) }
        binding.btnCancelEdit.setOnClickListener { toggleEditMode(false) }

        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()

            viewModel.updateProfile(name, username, email, phone)
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { handleUiState(it) } }
                launch { viewModel.updateState.collect { handleUpdateState(it) } }
            }
        }
    }

    private fun handleUiState(state: ProfileUiState) {
        when (state) {
            is ProfileUiState.Loading -> startSkeletonAnimation()
            is ProfileUiState.Success -> {
                stopSkeletonAnimation()
                val user = state.user
                binding.tvName.text = user.name
                binding.tvEmail.text = user.email

                if (!isEditMode) {
                    binding.etName.setText(user.name)
                    binding.etUsername.setText(user.username)
                    binding.etEmail.setText(user.email)
                    binding.etPhone.setText(user.phone ?: "")
                }
            }
            is ProfileUiState.Error -> {
                stopSkeletonAnimation()
                errorBanner.show(state.message)
            }
        }
    }

    private fun handleUpdateState(state: ProfileUpdateState) {
        when (state) {
            is ProfileUpdateState.Idle -> setLoadingSave(false)
            is ProfileUpdateState.Loading -> setLoadingSave(true)
            is ProfileUpdateState.Success -> {
                setLoadingSave(false)
                Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                toggleEditMode(false)
                viewModel.resetUpdateState()
            }
            is ProfileUpdateState.Error -> {
                setLoadingSave(false)
                errorBanner.show(state.message)
                viewModel.resetUpdateState()
            }
        }
    }

    private fun setLoadingSave(isLoading: Boolean) {
        binding.btnSaveProfile.isEnabled = !isLoading
        binding.btnCancelEdit.isEnabled = !isLoading
        binding.btnSaveProfile.text = if (isLoading) "" else getString(R.string.profile_btn_save)
        binding.pbSaveProfile.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun toggleEditMode(enable: Boolean) {
        if (isEditMode == enable) return
        isEditMode = enable

        TransitionManager.beginDelayedTransition(
            binding.mainContainer,
            AutoTransition().setDuration(350).setInterpolator(DecelerateInterpolator())
        )

        binding.tvTitle.text = getString(if (enable) R.string.profile_edit_title else R.string.nav_profile)

        val cardBgColor = ContextCompat.getColor(requireContext(), if (enable) R.color.aura_white else R.color.aura_primary)
        binding.cardProfile.setCardBackgroundColor(cardBgColor)
        binding.layoutProfileContent.setBackgroundColor(cardBgColor)

        val viewVisibility = if (enable) View.GONE else View.VISIBLE
        val editVisibility = if (enable) View.VISIBLE else View.GONE

        binding.tvName.visibility = viewVisibility
        binding.tvEmail.visibility = viewVisibility
        binding.btnEditProfile.visibility = viewVisibility
        binding.layoutEditForm.visibility = editVisibility

        if (enable) {
            binding.etName.requestFocus()
        } else {
            binding.root.clearFocus()
        }
    }

    private fun startSkeletonAnimation() {
        binding.tvName.visibility = View.GONE
        binding.tvEmail.visibility = View.GONE
        binding.btnEditProfile.visibility = View.GONE
        binding.layoutEditForm.visibility = View.GONE

        binding.skeletonName.visibility = View.VISIBLE
        binding.skeletonEmail.visibility = View.VISIBLE

        shimmerAnimator?.cancel()
        shimmerAnimator = ValueAnimator.ofFloat(0.4f, 1.0f, 0.4f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                if (_binding == null) return@addUpdateListener
                val alphaVal = animator.animatedValue as Float
                binding.skeletonName.alpha = alphaVal
                binding.skeletonEmail.alpha = alphaVal
            }
            start()
        }
    }

    private fun stopSkeletonAnimation() {
        shimmerAnimator?.cancel()
        binding.skeletonName.visibility = View.GONE
        binding.skeletonEmail.visibility = View.GONE

        if (!isEditMode) {
            binding.tvName.visibility = View.VISIBLE
            binding.tvEmail.visibility = View.VISIBLE
            binding.btnEditProfile.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shimmerAnimator?.cancel()
        errorBanner.destroy()
        _binding = null
    }
}
