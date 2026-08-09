package com.mexadev.aura.ui.services

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.SharedElementCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.gson.Gson
import com.mexadev.aura.R
import com.mexadev.aura.core.network.ApiClient
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.data.model.ContractedService
import com.mexadev.aura.databinding.FragmentContractedServicesBinding
import com.mexadev.aura.databinding.ItemContractedServiceBinding
import com.mexadev.aura.ui.common.BannerManager
import kotlinx.coroutines.launch
import java.util.Locale

class ContractedServicesFragment : Fragment() {

    private var _binding: FragmentContractedServicesBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ContractedServicesAdapter
    private lateinit var successBanner: BannerManager
    private lateinit var prefsManager: PreferencesManager

    private var contractedList = mutableListOf<ContractedService>()

    // Filter Island States (Default: Completed and Cancelled are hidden)
    private var filterIncludeFinished: Boolean = false
    private var selectedChipFilter: String = CHIP_ACTIVE
    private var searchQuery: String = ""
    private var sortNewestFirst: Boolean = true
    private var isIslandExpanded: Boolean = false

    companion object {
        private const val CHIP_ACTIVE = "active"
        private const val CHIP_SCHEDULED = "scheduled"
        private const val CHIP_RECURRENT = "recurrent"
        private const val CHIP_FINISHED = "finished"
        private const val CHIP_ALL = "all"
    }

    private val detailLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(450)
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val json = result.data?.getStringExtra(ContractedServiceDetailActivity.EXTRA_CONTRACTED_SERVICE_JSON)
                if (json != null) {
                    try {
                        val updated = Gson().fromJson(json, ContractedService::class.java)
                        val index = contractedList.indexOfFirst { it.id == updated.id }
                        if (index != -1) {
                            contractedList[index] = updated
                            applyFilters()
                        } else {
                            fetchContractedServices(isPullToRefresh = true)
                        }
                    } catch (_: Exception) {
                        fetchContractedServices(isPullToRefresh = true)
                    }
                }
            }
            fetchContractedServices(isPullToRefresh = true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContractedServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefsManager = PreferencesManager(requireContext())
        successBanner = BannerManager(binding.successBanner, binding.tvSuccessBannerMessage)

        setupSharedElementCallback()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFilterIsland()
        populateDynamicSkeleton()
        startSkeletonPulse()

        fetchContractedServices()
    }

    private fun setupSharedElementCallback() {
        requireActivity().setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                val currentBinding = _binding ?: return
                if (names.isEmpty()) return
                val layoutManager = currentBinding.rvContractedServices.layoutManager as? LinearLayoutManager
                    ?: return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible  = layoutManager.findLastVisibleItemPosition()
                for (i in firstVisible..lastVisible) {
                    val holder = currentBinding.rvContractedServices.findViewHolderForAdapterPosition(i) as? ContractedServicesAdapter.ViewHolder
                        ?: continue
                    for (name in names) {
                        if (holder.binding.flIconContainer.transitionName == name) {
                            sharedElements[name] = holder.binding.flIconContainer
                        }
                        if (holder.binding.tvTitle.transitionName == name) {
                            sharedElements[name] = holder.binding.tvTitle
                        }
                        if (holder.binding.tvStatus.transitionName == name) {
                            sharedElements[name] = holder.binding.tvStatus
                        }
                        if (holder.binding.root.transitionName == name) {
                            sharedElements[name] = holder.binding.root
                        }
                    }
                }
            }
        })
    }

    private fun setupRecyclerView() {
        binding.rvContractedServices.layoutManager = LinearLayoutManager(requireContext())
        adapter = ContractedServicesAdapter(
            onItemClick = { item, itemBinding ->
                openServiceDetail(item, itemBinding)
            }
        )
        binding.rvContractedServices.adapter = adapter
    }

    private fun setupFilterIsland() {
        // Toggle switch button for finished services (Completados / Cancelados)
        binding.btnToggleFinished.setOnClickListener {
            filterIncludeFinished = !filterIncludeFinished
            if (filterIncludeFinished && selectedChipFilter == CHIP_ACTIVE) {
                selectedChipFilter = CHIP_ALL
            } else if (!filterIncludeFinished && selectedChipFilter == CHIP_ALL) {
                selectedChipFilter = CHIP_ACTIVE
            }
            applyFilters(animate = true)
        }

        binding.switchIncludeFinished.setOnCheckedChangeListener { _, isChecked ->
            if (filterIncludeFinished != isChecked) {
                filterIncludeFinished = isChecked
                if (filterIncludeFinished && selectedChipFilter == CHIP_ACTIVE) {
                    selectedChipFilter = CHIP_ALL
                } else if (!filterIncludeFinished && selectedChipFilter == CHIP_ALL) {
                    selectedChipFilter = CHIP_ACTIVE
                }
                applyFilters(animate = true)
            }
        }

        // Expand / Collapse Drawer with Physics Animation
        binding.btnExpandFilters.setOnClickListener {
            toggleIslandExpandState()
        }

        // Search Input Watcher
        binding.etFilterSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString() ?: ""
                binding.btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters(animate = true)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etFilterSearch.setText("")
        }

        // Chips Selection
        binding.chipFilterActive.setOnClickListener { selectChip(CHIP_ACTIVE) }
        binding.chipFilterScheduled.setOnClickListener { selectChip(CHIP_SCHEDULED) }
        binding.chipFilterRecurrent.setOnClickListener { selectChip(CHIP_RECURRENT) }
        binding.chipFilterFinished.setOnClickListener { selectChip(CHIP_FINISHED) }
        binding.chipFilterAll.setOnClickListener { selectChip(CHIP_ALL) }

        // Sorting Toggle
        binding.btnSortToggle.setOnClickListener {
            sortNewestFirst = !sortNewestFirst
            binding.btnSortToggle.text = if (sortNewestFirst) {
                getString(R.string.services_filter_sort_recent)
            } else {
                getString(R.string.services_filter_sort_oldest)
            }
            applyFilters(animate = true)
        }

        // Empty state quick toggle action
        binding.btnEmptyShowFinished.setOnClickListener {
            filterIncludeFinished = true
            selectedChipFilter = CHIP_ALL
            applyFilters(animate = true)
        }
    }

    private fun selectChip(chipKey: String) {
        selectedChipFilter = chipKey
        if (chipKey == CHIP_FINISHED || chipKey == CHIP_ALL) {
            filterIncludeFinished = true
        } else if (chipKey == CHIP_ACTIVE) {
            filterIncludeFinished = false
        }
        applyFilters(animate = true)
    }

    private fun toggleIslandExpandState() {
        isIslandExpanded = !isIslandExpanded

        // Transition animation for expanding/collapsing island
        val transition = AutoTransition().apply {
            duration = 280
            interpolator = OvershootInterpolator(0.9f)
        }
        TransitionManager.beginDelayedTransition(binding.islandFilterCard, transition)
        binding.layoutExpandableFilters.visibility = if (isIslandExpanded) View.VISIBLE else View.GONE

        // Physics spring-like rotation for arrow
        binding.btnExpandFilters.animate()
            .rotation(if (isIslandExpanded) 180f else 0f)
            .setDuration(280)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    private fun isFinishedStatus(status: String): Boolean {
        val s = status.lowercase(Locale.getDefault())
        return s == "completed" || s == "cancelled" || s == "canceled"
    }

    private fun applyFilters(@Suppress("UNUSED_PARAMETER") animate: Boolean = false) {
        if (_binding == null) return

        var filtered = contractedList.toList()

        // 1. Status / Modality Chip Filtering
        filtered = when (selectedChipFilter) {
            CHIP_ACTIVE -> filtered.filter { !isFinishedStatus(it.status) }
            CHIP_SCHEDULED -> filtered.filter { it.status.lowercase(Locale.getDefault()) == "scheduled" }
            CHIP_RECURRENT -> filtered.filter { it.isRecurrent == true }
            CHIP_FINISHED -> filtered.filter { isFinishedStatus(it.status) }
            CHIP_ALL -> filtered
            else -> {
                if (!filterIncludeFinished) {
                    filtered.filter { !isFinishedStatus(it.status) }
                } else {
                    filtered
                }
            }
        }

        // 2. Strict default rule: if filterIncludeFinished is false, filter out finished
        if (!filterIncludeFinished && (selectedChipFilter == CHIP_ACTIVE || selectedChipFilter == CHIP_SCHEDULED)) {
            filtered = filtered.filter { !isFinishedStatus(it.status) }
        }

        // 3. Search Query Filter
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            filtered = filtered.filter { item ->
                val titleMatch = item.service?.title?.lowercase(Locale.getDefault())?.contains(q) == true
                val notesMatch = item.notes?.lowercase(Locale.getDefault())?.contains(q) == true
                val dateMatch = item.preferredDate?.lowercase(Locale.getDefault())?.contains(q) == true
                val statusMatch = item.status.lowercase(Locale.getDefault()).contains(q)
                titleMatch || notesMatch || dateMatch || statusMatch
            }
        }

        // 4. Sort Order
        filtered = if (sortNewestFirst) {
            filtered.sortedByDescending { it.id }
        } else {
            filtered.sortedBy { it.id }
        }

        // 5. Update UI Badges & Visual States
        updateFilterIslandUI(filtered.size)

        // 6. List vs Empty State
        if (contractedList.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvContractedServices.visibility = View.GONE
            binding.tvEmptyTitle.setText(R.string.services_empty_contracted_title)
            binding.tvEmptyDesc.setText(R.string.services_empty_contracted_desc)
            binding.btnEmptyShowFinished.visibility = View.GONE
        } else if (filtered.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvContractedServices.visibility = View.GONE

            val finishedCount = contractedList.count { isFinishedStatus(it.status) }
            if (finishedCount > 0 && !filterIncludeFinished) {
                binding.tvEmptyTitle.setText(R.string.services_filter_empty_active_title)
                binding.tvEmptyDesc.setText(R.string.services_filter_empty_active_desc)
                binding.btnEmptyShowFinished.visibility = View.VISIBLE
            } else {
                binding.tvEmptyTitle.setText(R.string.services_empty_contracted_title)
                binding.tvEmptyDesc.setText(R.string.services_empty_contracted_desc)
                binding.btnEmptyShowFinished.visibility = View.GONE
            }
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvContractedServices.visibility = View.VISIBLE
        }

        adapter.submitList(filtered)
    }

    private fun updateFilterIslandUI(visibleCount: Int) {
        binding.tvIslandSubtitle.text = if (filterIncludeFinished || selectedChipFilter == CHIP_ALL || selectedChipFilter == CHIP_FINISHED) {
            getString(R.string.services_filter_count_total, visibleCount)
        } else {
            getString(R.string.services_filter_count_active, visibleCount)
        }

        // Visual state of the Toggle Switch Button inside Island Header
        val toggleBg = if (filterIncludeFinished) R.drawable.bg_filter_chip_active else R.drawable.bg_filter_chip_inactive
        val toggleTextColor = ContextCompat.getColor(requireContext(), if (filterIncludeFinished) R.color.aura_white else R.color.aura_text_secondary)
        binding.btnToggleFinished.setBackgroundResource(toggleBg)
        binding.tvToggleFinishedLabel.setTextColor(toggleTextColor)

        // Silent switch update to avoid listener loops
        binding.switchIncludeFinished.setOnCheckedChangeListener(null)
        binding.switchIncludeFinished.isChecked = filterIncludeFinished
        binding.switchIncludeFinished.setOnCheckedChangeListener { _, isChecked ->
            if (filterIncludeFinished != isChecked) {
                filterIncludeFinished = isChecked
                if (filterIncludeFinished && selectedChipFilter == CHIP_ACTIVE) {
                    selectedChipFilter = CHIP_ALL
                } else if (!filterIncludeFinished && selectedChipFilter == CHIP_ALL) {
                    selectedChipFilter = CHIP_ACTIVE
                }
                applyFilters(animate = true)
            }
        }

        // Update Chip Styles
        updateChipStyle(binding.chipFilterActive, selectedChipFilter == CHIP_ACTIVE)
        updateChipStyle(binding.chipFilterScheduled, selectedChipFilter == CHIP_SCHEDULED)
        updateChipStyle(binding.chipFilterRecurrent, selectedChipFilter == CHIP_RECURRENT)
        updateChipStyle(binding.chipFilterFinished, selectedChipFilter == CHIP_FINISHED)
        updateChipStyle(binding.chipFilterAll, selectedChipFilter == CHIP_ALL)
    }

    private fun updateChipStyle(chip: TextView, isSelected: Boolean) {
        val bgRes = if (isSelected) R.drawable.bg_filter_chip_active else R.drawable.bg_filter_chip_inactive
        val textColor = ContextCompat.getColor(requireContext(), if (isSelected) R.color.aura_white else R.color.aura_text_secondary)
        chip.setBackgroundResource(bgRes)
        chip.setTextColor(textColor)
        chip.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun openServiceDetail(item: ContractedService, itemBinding: ItemContractedServiceBinding) {
        val id = item.id
        val iconName = "trans_icon_$id"
        val titleName = "trans_title_$id"
        val statusName = "trans_status_$id"
        val cardName = "transition_contracted_service_$id"

        itemBinding.flIconContainer.transitionName = iconName
        itemBinding.tvTitle.transitionName = titleName
        itemBinding.tvStatus.transitionName = statusName
        itemBinding.root.transitionName = cardName

        val intent = Intent(requireContext(), ContractedServiceDetailActivity::class.java).apply {
            putExtra(ContractedServiceDetailActivity.EXTRA_CONTRACTED_SERVICE_ID, item.id)
            putExtra(ContractedServiceDetailActivity.EXTRA_CONTRACTED_SERVICE_JSON, Gson().toJson(item))
            putExtra(ContractedServiceDetailActivity.EXTRA_TRANSITION_NAME, cardName)
            putExtra(ContractedServiceDetailActivity.EXTRA_TRANSITION_ICON_NAME, iconName)
            putExtra(ContractedServiceDetailActivity.EXTRA_TRANSITION_TITLE_NAME, titleName)
            putExtra(ContractedServiceDetailActivity.EXTRA_TRANSITION_STATUS_NAME, statusName)
        }

        val pairs = arrayOf(
            androidx.core.util.Pair(itemBinding.root as View, cardName),
            androidx.core.util.Pair(itemBinding.flIconContainer as View, iconName),
            androidx.core.util.Pair(itemBinding.tvTitle as View, titleName),
            androidx.core.util.Pair(itemBinding.tvStatus as View, statusName)
        )

        val options = androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(),
            *pairs
        )
        detailLauncher.launch(intent, options)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchContractedServices(isPullToRefresh = true)
        }
    }

    private fun populateDynamicSkeleton() {
        val count = prefsManager.contractedServicesCount.coerceIn(1, 10)

        binding.layoutCenterLoading.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        repeat(count) {
            inflater.inflate(R.layout.item_contracted_service_skeleton, binding.layoutCenterLoading, true)
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

    private fun fetchContractedServices(isPullToRefresh: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getContractedServices()
                if (_binding == null) return@launch

                binding.layoutCenterLoading.visibility = View.GONE
                binding.swipeRefreshLayout.isRefreshing = false

                if (response.isSuccessful && response.body()?.status == "success") {
                    val list = response.body()?.data ?: emptyList()
                    handleFetchSuccess(list, isPullToRefresh)
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

    private fun handleFetchSuccess(list: List<ContractedService>, isPullToRefresh: Boolean) {
        prefsManager.contractedServicesCount = list.size

        contractedList.clear()
        contractedList.addAll(list)

        applyFilters(animate = !isPullToRefresh)
    }

    override fun onResume() {
        super.onResume()
        fetchContractedServices(isPullToRefresh = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.setExitSharedElementCallback(null as SharedElementCallback?)
        successBanner.destroy()
        _binding = null
    }
}
