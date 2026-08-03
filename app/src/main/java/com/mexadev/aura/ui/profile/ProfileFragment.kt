package com.mexadev.aura.ui.profile

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.mexadev.aura.LoginActivity
import com.mexadev.aura.R
import com.mexadev.aura.databinding.FragmentProfileBinding
import com.mexadev.aura.ui.common.BannerManager
import com.mexadev.aura.ui.common.ConfirmBottomSheetFragment
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var errorBanner: BannerManager
    private var shimmerAnimator: ValueAnimator? = null

    private var isEditMode = false

    private val onBackStackChangedListener = FragmentManager.OnBackStackChangedListener {
        if (_binding == null) return@OnBackStackChangedListener
        val detailContainer = activity?.findViewById<View>(R.id.detail_fragment_container)
        val fragment = parentFragmentManager.findFragmentById(R.id.detail_fragment_container)
        if (fragment == null) {
            detailContainer?.visibility = View.GONE
        }
    }

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

        parentFragmentManager.addOnBackStackChangedListener(onBackStackChangedListener)

        errorBanner = BannerManager(binding.errorBanner, binding.tvErrorBannerMessage)

        setupWindowInsets()
        setupListeners()
        setupObservers()
        runEntranceAnimations()

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

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        binding.cardSecurity.setOnClickListener {
            if (isEditMode) return@setOnClickListener
            openSettingsFragment(com.mexadev.aura.ui.settings.SecuritySettingsFragment())
        }

        binding.cardNotifications.setOnClickListener {
            if (isEditMode) return@setOnClickListener
            openSettingsFragment(com.mexadev.aura.ui.settings.NotificationSettingsFragment())
        }

        // Physics spring touch response for cardLogout
        val springXCompress = SpringAnimation(binding.cardLogout, DynamicAnimation.SCALE_X, 0.95f).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
        val springYCompress = SpringAnimation(binding.cardLogout, DynamicAnimation.SCALE_Y, 0.95f).apply {
            spring.stiffness = SpringForce.STIFFNESS_HIGH
            spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
        }
        val springXRelease = SpringAnimation(binding.cardLogout, DynamicAnimation.SCALE_X, 1.0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        val springYRelease = SpringAnimation(binding.cardLogout, DynamicAnimation.SCALE_Y, 1.0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }

        binding.cardLogout.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    springXRelease.cancel()
                    springYRelease.cancel()
                    springXCompress.start()
                    springYCompress.start()
                }
                MotionEvent.ACTION_UP -> {
                    springXCompress.cancel()
                    springYCompress.cancel()
                    springXRelease.start()
                    springYRelease.start()
                    v.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    springXCompress.cancel()
                    springYCompress.cancel()
                    springXRelease.start()
                    springYRelease.start()
                }
            }
            true
        }

        binding.cardLogout.setOnClickListener {
            if (isEditMode) return@setOnClickListener
            showLogoutConfirmation()
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

    private fun runEntranceAnimations() {
        binding.cardLogout.alpha = 0f
        binding.cardLogout.translationY = 40f
        binding.cardLogout.animate().alpha(1f).setDuration(250).start()
        
        val springY = SpringAnimation(binding.cardLogout, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        binding.cardLogout.postDelayed({ springY.start() }, 150)
    }

    private fun showLogoutConfirmation() {
        ConfirmBottomSheetFragment.newInstance(
            title = getString(R.string.logout_title),
            message = getString(R.string.logout_confirm_message),
            confirmText = getString(R.string.logout_btn),
            cancelText = getString(R.string.profile_btn_cancel),
            iconRes = R.drawable.ic_logout,
            iconColorRes = R.color.aura_error,
            confirmBgRes = R.drawable.bg_btn_error,
            onConfirm = { viewModel.logout() }
        ).show(childFragmentManager, "ConfirmLogoutDialog")
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { handleUiState(it) } }
                launch { viewModel.updateState.collect { handleUpdateState(it) } }
                launch { viewModel.logoutState.collect { handleLogoutState(it) } }
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

    private fun handleLogoutState(state: LogoutState) {
        when (state) {
            is LogoutState.Idle -> {}
            is LogoutState.Loading -> {}
            is LogoutState.Success -> {
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().overrideActivityTransition(
                    AppCompatActivity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.fade_in,
                    R.anim.fade_out
                )
                requireActivity().finishAffinity()
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

        if (_binding == null) return

        TransitionManager.endTransitions(binding.mainContainer)
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
        binding.cardLogout.visibility = viewVisibility

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
            binding.cardLogout.visibility = View.VISIBLE
        }
    }

    private fun openSettingsFragment(fragment: Fragment) {
        if (_binding == null) return
        val detailContainer = activity?.findViewById<View>(R.id.detail_fragment_container)
        detailContainer?.visibility = View.VISIBLE
        parentFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.nav_slide_in_right,
                R.anim.nav_slide_out_left,
                R.anim.nav_slide_in_left,
                R.anim.nav_slide_out_right
            )
            .replace(R.id.detail_fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        parentFragmentManager.removeOnBackStackChangedListener(onBackStackChangedListener)
        shimmerAnimator?.cancel()
        errorBanner.destroy()
        _binding = null
    }
}
