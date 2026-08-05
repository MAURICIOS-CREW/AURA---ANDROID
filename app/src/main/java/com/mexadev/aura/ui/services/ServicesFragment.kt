package com.mexadev.aura.ui.services

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mexadev.aura.R
import com.mexadev.aura.databinding.FragmentServicesBinding

class ServicesFragment : Fragment() {

    private var _binding: FragmentServicesBinding? = null
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
        _binding = FragmentServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.transitionName = "shared_card_transition"

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
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
        val adapter = ServicesPagerAdapter(this)
        binding.viewPager.adapter = adapter
        binding.segmentedControl.setTabs(
            listOf(
                getString(R.string.services_tab_available),
                getString(R.string.services_tab_mine)
            )
        )
        binding.segmentedControl.setupWithViewPager2(binding.viewPager)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ServicesPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> AvailableServicesFragment()
                1 -> ContractedServicesFragment()
                else -> AvailableServicesFragment()
            }
        }
    }
}
