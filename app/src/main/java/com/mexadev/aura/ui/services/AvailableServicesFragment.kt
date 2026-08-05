package com.mexadev.aura.ui.services

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.data.model.Service
import com.mexadev.aura.databinding.FragmentAvailableServicesBinding
import com.mexadev.aura.ui.common.BannerManager
import kotlinx.coroutines.launch

class AvailableServicesFragment : Fragment() {

    private var _binding: FragmentAvailableServicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: AvailableServicesAdapter
    private lateinit var successBanner: BannerManager
    private lateinit var prefsManager: PreferencesManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAvailableServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefsManager = PreferencesManager(requireContext())
        successBanner = BannerManager(binding.successBanner, binding.tvSuccessBannerMessage)

        setupRecyclerView()
        setupSwipeRefresh()
        populateDynamicSkeleton()
        startSkeletonPulse()

        fetchAvailableServices()
    }

    private fun setupRecyclerView() {
        binding.rvServices.layoutManager = LinearLayoutManager(requireContext())
        adapter = AvailableServicesAdapter { service, _ ->
            openBookingBottomSheet(service)
        }
        binding.rvServices.adapter = adapter
    }

    private fun openBookingBottomSheet(service: Service) {
        val sheet = ServiceBookingBottomSheetFragment.newInstance(service) { _ ->
            successBanner.show(getString(R.string.services_contract_success_banner))
            
            // Switch to "Mis Servicios" tab in parent viewpager cleanly
            val viewPager = activity?.findViewById<ViewPager2>(R.id.viewPager)
            viewPager?.postDelayed({
                viewPager.currentItem = 1
            }, 1200L)
        }
        sheet.show(parentFragmentManager, "BookingBottomSheet")
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchAvailableServices(isPullToRefresh = true)
        }
    }

    private fun populateDynamicSkeleton() {
        val count = prefsManager.availableServicesCount.coerceIn(2, 10)

        binding.layoutCenterLoading.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        repeat(count) {
            inflater.inflate(R.layout.item_available_service_skeleton, binding.layoutCenterLoading, true)
        }
    }

    private fun startSkeletonPulse() {
        binding.layoutCenterLoading.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(binding.layoutCenterLoading, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun fetchAvailableServices(isPullToRefresh: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getServices()
                if (_binding == null) return@launch

                binding.layoutCenterLoading.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false

                if (response.isSuccessful && response.body()?.status == "success") {
                    val servicesList = response.body()?.data ?: emptyList()
                    val activeServices = servicesList.filter { it.isActive != false }
                    handleFetchSuccess(activeServices, isPullToRefresh)
                } else {
                    if (!isPullToRefresh) {
                        Toast.makeText(requireContext(), R.string.services_error_fetch, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                if (_binding == null) return@launch
                binding.layoutCenterLoading.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false
                if (!isPullToRefresh) {
                    Toast.makeText(requireContext(), R.string.services_error_no_connection, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleFetchSuccess(list: List<Service>, isPullToRefresh: Boolean) {
        prefsManager.availableServicesCount = list.size

        if (list.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvServices.visibility = View.GONE
            return
        }

        binding.layoutEmpty.visibility = View.GONE
        binding.rvServices.visibility = View.VISIBLE

        // ListAdapter + DiffUtil ensures no flicker if data didn't change
        adapter.submitList(list) {
            if (!isPullToRefresh && binding.rvServices.alpha == 0f) {
                binding.rvServices.alpha = 0f
                binding.rvServices.translationY = 30f
                binding.rvServices.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setInterpolator(OvershootInterpolator(1.0f))
                    .start()
            } else {
                binding.rvServices.alpha = 1f
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        successBanner.destroy()
        _binding = null
    }
}
