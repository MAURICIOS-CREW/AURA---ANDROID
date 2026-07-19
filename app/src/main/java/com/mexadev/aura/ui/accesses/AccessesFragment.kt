package com.mexadev.aura.ui.accesses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.core.widget.TextViewCompat
import android.animation.ArgbEvaluator
import androidx.core.content.ContextCompat
import android.graphics.Typeface
import com.mexadev.aura.R
import com.mexadev.aura.databinding.FragmentAccessesBinding

class AccessesFragment : Fragment() {

    private var _binding: FragmentAccessesBinding? = null
    private val binding get() = _binding!!

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccessesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.transitionName = "shared_card_transition"

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            binding.detailHeader.setPadding(
                binding.detailHeader.paddingLeft, 
                systemBars.top, 
                binding.detailHeader.paddingRight, 
                binding.detailHeader.paddingBottom
            )
            
            insets
        }

        binding.btnBack.setOnClickListener { backCallback.handleOnBackPressed() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = AccessesPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // Post para calcular ancho del indicador igual al ancho del tab y configurarlo
        binding.tabList.post {
            val params = binding.activeIndicator.layoutParams
            params.width = binding.tabList.width
            binding.activeIndicator.layoutParams = params
        }

        val evaluator = ArgbEvaluator()
        val activeColor = ContextCompat.getColor(requireContext(), R.color.aura_text_on_primary)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.aura_text_primary)

        fun updateTabState(progress: Float) {
            // Animación física de la píldora azul
            val maxDistance = binding.tabHistory.x - binding.tabList.x
            if (maxDistance > 0) {
                binding.activeIndicator.translationX = maxDistance * progress
            }

            // Interpolación de colores
            val colorList = evaluator.evaluate(progress, activeColor, inactiveColor) as Int
            val colorHistory = evaluator.evaluate(progress, inactiveColor, activeColor) as Int
            
            binding.tabList.setTextColor(colorList)
            binding.tabHistory.setTextColor(colorHistory)

            binding.tabList.typeface = if (progress < 0.5f) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            binding.tabHistory.typeface = if (progress >= 0.5f) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        // Setup manual tab clicks
        binding.tabList.setOnClickListener { binding.viewPager.currentItem = 0 }
        binding.tabHistory.setOnClickListener { binding.viewPager.currentItem = 1 }

        // Update tabs physics smoothly on page change
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                val progress = position + positionOffset
                if (progress in 0f..1f) {
                    updateTabState(progress)
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class AccessesPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> AccessListFragment()
                1 -> AccessHistoryFragment()
                else -> AccessListFragment()
            }
        }
    }
}
