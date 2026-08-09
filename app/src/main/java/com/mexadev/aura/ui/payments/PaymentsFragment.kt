package com.mexadev.aura.ui.payments

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.SharedElementCallback
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.gson.Gson
import com.mexadev.aura.R
import com.mexadev.aura.data.model.PendingPaymentItem
import com.mexadev.aura.databinding.FragmentPaymentsBinding
import com.mexadev.aura.databinding.ItemPendingConceptSummaryBinding
import com.mexadev.aura.ui.common.BannerManager

/**
 * Fragment de Pagos.
 * Rebranding completo conforme a maqueta.jpeg y PROJECT_STANDARDS.md.
 * Soporta paginación progresiva, skeleton loaders, RecyclerView atómico
 * y transiciones compartidas (Pairs).
 */
class PaymentsFragment : Fragment() {

    private var _binding: FragmentPaymentsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PaymentsViewModel by viewModels()
    private lateinit var historyAdapter: PaymentHistoryAdapter
    private lateinit var bannerManager: BannerManager

    private var skeletonAnimator: ValueAnimator? = null
    private var isConceptsExpanded = false

    private val selectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.fetchPayments(isPullToRefresh = true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bannerManager = BannerManager(binding.errorBanner, binding.tvErrorBannerMessage)

        // Re-map shared elements on return so each transition reverses to the right origin view
        requireActivity().setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                sharedElements["transition_card_balance"] = binding.cardBalance
                sharedElements["transition_tv_balance"] = binding.tvBalance
                sharedElements["transition_tv_label"] = binding.tvBalanceLabel
                sharedElements["transition_btn_pay"] = binding.btnPayNow
            }
        })

        setupRecyclerView()
        setupSwipeRefresh()
        setupListeners()
        observeViewModel()

        viewModel.fetchPayments()
    }

    private fun setupRecyclerView() {
        historyAdapter = PaymentHistoryAdapter { historyItem ->
            val detailSheet = PaymentDetailBottomSheetFragment.newInstance(historyItem)
            detailSheet.show(childFragmentManager, "PaymentDetailBottomSheet")
        }

        binding.rvPaymentHistory.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.fetchPayments(isPullToRefresh = true)
        }
    }

    private fun setupListeners() {
        binding.cardBalance.setOnClickListener {
            toggleConceptsExpansion()
        }

        binding.btnLoadMore.setOnClickListener {
            viewModel.loadNextPage()
        }

        binding.btnPayNow.setOnClickListener {
            launchPaymentSelection(PaymentSelectionActivity.MODE_BUTTON)
        }

        binding.flWalletIcon.setOnClickListener {
            launchPaymentSelection(PaymentSelectionActivity.MODE_CARD)
        }

        binding.btnPaymentSettings.setOnClickListener {
            bannerManager.show(getString(R.string.coming_soon))
        }
    }

    private fun toggleConceptsExpansion() {
        isConceptsExpanded = !isConceptsExpanded

        val transition = AutoTransition().apply {
            duration = 350
            interpolator = FastOutSlowInInterpolator()
        }

        TransitionManager.beginDelayedTransition(binding.paymentsRoot, transition)

        binding.layoutConceptsSummary.visibility = if (isConceptsExpanded) View.VISIBLE else View.GONE

        binding.ivExpandArrow.animate()
            .rotation(if (isConceptsExpanded) 180f else 0f)
            .setDuration(300)
            .start()
    }

    private fun launchPaymentSelection(mode: String) {
        val pendingList = viewModel.pendingItems.value.orEmpty()
        val json = Gson().toJson(pendingList)

        val intent = Intent(requireContext(), PaymentSelectionActivity::class.java).apply {
            putExtra(PaymentSelectionActivity.EXTRA_PENDING_ITEMS_JSON, json)
            putExtra(PaymentSelectionActivity.EXTRA_TRANSITION_MODE, mode)
        }

        val pairs = if (mode == PaymentSelectionActivity.MODE_BUTTON) {
            // Only the button morphs; the rest of the destination screen springs in on its own
            arrayOf(Pair.create(binding.btnPayNow as View, "transition_btn_pay"))
        } else {
            // Disable the card press animation so it doesn't jitter during the shared element morph
            binding.cardBalance.clickAnimationEnabled = false
            binding.cardBalance.scaleX = 1f
            binding.cardBalance.scaleY = 1f

            arrayOf(
                Pair.create(binding.cardBalance as View, "transition_card_balance"),
                Pair.create(binding.tvBalance as View, "transition_tv_balance"),
                Pair.create(binding.tvBalanceLabel as View, "transition_tv_label")
            )
        }

        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(), *pairs
        )

        selectionLauncher.launch(intent, options)

        // Re-enable the click animation after the activity is launched
        binding.cardBalance.postDelayed({
            binding.cardBalance.clickAnimationEnabled = true
        }, 500)
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PaymentsUiState.Loading -> {
                    showSkeletonLoading()
                    setPayButtonsEnabled(false)
                }
                is PaymentsUiState.Success -> {
                    hideSkeletonLoading()
                    setPayButtonsEnabled(true)
                    binding.swipeRefresh.isRefreshing = false
                    updateHistoryVisibility()
                }
                is PaymentsUiState.Error -> {
                    hideSkeletonLoading()
                    setPayButtonsEnabled(false)
                    binding.swipeRefresh.isRefreshing = false
                    bannerManager.show(state.message)
                    updateHistoryVisibility()
                }
            }
        }

        viewModel.currentBalance.observe(viewLifecycleOwner) { balance ->
            binding.tvBalance.text = getString(R.string.payments_balance_format, balance)
        }

        viewModel.pendingItems.observe(viewLifecycleOwner) { pendingList ->
            if (pendingList.isNotEmpty()) {
                val count = pendingList.size
                val firstConcept = pendingList.firstOrNull()?.title ?: ""
                binding.tvBalanceSubtitle.text = if (count == 1) firstConcept else getString(R.string.payments_pending_concepts_count, count)
                binding.ivExpandArrow.visibility = View.VISIBLE
            } else {
                binding.tvBalanceSubtitle.text = getString(R.string.payments_no_pending)
                binding.ivExpandArrow.visibility = View.GONE
            }
            populateConceptsSummary(pendingList)
        }

        viewModel.historyItems.observe(viewLifecycleOwner) { items ->
            historyAdapter.submitList(items)
            updateHistoryVisibility()
        }

        viewModel.hasMorePages.observe(viewLifecycleOwner) { hasMore ->
            binding.btnLoadMore.visibility = if (hasMore) View.VISIBLE else View.GONE
        }

        viewModel.isLoadingMore.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                binding.pbLoadMore.visibility = View.VISIBLE
                binding.btnLoadMore.visibility = View.GONE
            } else {
                binding.pbLoadMore.visibility = View.GONE
                if (viewModel.hasMorePages.value == true) {
                    binding.btnLoadMore.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun populateConceptsSummary(pendingList: List<PendingPaymentItem>) {
        binding.llConceptsContainer.removeAllViews()

        if (pendingList.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = getString(R.string.payments_no_pending)
                textSize = 13f
                setTextColor(requireContext().getColor(R.color.aura_text_secondary))
                setPadding(0, 8, 0, 8)
            }
            binding.llConceptsContainer.addView(emptyTv)
            return
        }

        pendingList.forEachIndexed { index, item ->
            val conceptBinding = ItemPendingConceptSummaryBinding.inflate(
                layoutInflater, binding.llConceptsContainer, false
            )
            conceptBinding.tvConceptTitle.text = item.title
            conceptBinding.tvConceptAmount.text = getString(R.string.payments_balance_format, item.amount)

            binding.llConceptsContainer.addView(conceptBinding.root)

            if (index < pendingList.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply {
                        setMargins(0, 8, 0, 8)
                    }
                    setBackgroundColor(requireContext().getColor(R.color.aura_border))
                }
                binding.llConceptsContainer.addView(divider)
            }
        }
    }

    private fun updateHistoryVisibility() {
        val state = viewModel.uiState.value
        val items = viewModel.historyItems.value

        when (state) {
            is PaymentsUiState.Loading -> {
                binding.layoutSkeleton.visibility = View.VISIBLE
                binding.rvPaymentHistory.visibility = View.GONE
                binding.layoutEmptyState.visibility = View.GONE
            }
            is PaymentsUiState.Success -> {
                binding.layoutSkeleton.visibility = View.GONE
                if (items.isNullOrEmpty()) {
                    binding.layoutEmptyState.visibility = View.VISIBLE
                    binding.rvPaymentHistory.visibility = View.GONE
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.rvPaymentHistory.visibility = View.VISIBLE
                }
            }
            is PaymentsUiState.Error -> {
                binding.layoutSkeleton.visibility = View.GONE
                if (items.isNullOrEmpty()) {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.rvPaymentHistory.visibility = View.GONE
                } else {
                    binding.layoutEmptyState.visibility = View.GONE
                    binding.rvPaymentHistory.visibility = View.VISIBLE
                }
            }
            null -> {
                binding.layoutEmptyState.visibility = View.GONE
            }
        }
    }

    private fun showSkeletonLoading() {
        binding.layoutSkeleton.visibility = View.VISIBLE
        binding.rvPaymentHistory.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.GONE
        
        binding.skBalance.visibility = View.VISIBLE
        binding.tvBalance.visibility = View.INVISIBLE

        skeletonAnimator?.cancel()
        skeletonAnimator = ValueAnimator.ofFloat(1f, 0.4f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val alphaVal = animator.animatedValue as Float
                binding.layoutSkeleton.alpha = alphaVal
                binding.skBalance.alpha = alphaVal
            }
            start()
        }
    }

    private fun hideSkeletonLoading() {
        skeletonAnimator?.cancel()
        binding.layoutSkeleton.visibility = View.GONE
        binding.skBalance.visibility = View.GONE
        binding.tvBalance.visibility = View.VISIBLE
    }

    private fun setPayButtonsEnabled(enabled: Boolean) {
        binding.btnPayNow.isEnabled = enabled
        binding.btnPayNow.alpha = if (enabled) 1.0f else 0.5f
        binding.flWalletIcon.isEnabled = enabled
        binding.flWalletIcon.isClickable = enabled
        binding.flWalletIcon.alpha = if (enabled) 1.0f else 0.5f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        skeletonAnimator?.cancel()
        bannerManager.destroy()
        _binding = null
    }
}
