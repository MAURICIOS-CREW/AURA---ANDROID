package com.mexadev.aura.ui.polls

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.databinding.FragmentPollsBinding
import com.mexadev.aura.databinding.ItemPollBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PollModel(
    val id: String,
    val question: String,
    val option1: String,
    val option2: String,
    var votesOption1: Int,
    var votesOption2: Int,
    var userVotedOption: Int? = null // 1 or 2, null if not voted
)

class PollsFragment : Fragment() {

    private var _binding: FragmentPollsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: SharedPreferences
    private lateinit var auraPrefs: PreferencesManager
    private lateinit var adapter: PollAdapter
    private val pollsList = mutableListOf<PollModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPollsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("PollsPrefs", Context.MODE_PRIVATE)
        auraPrefs = PreferencesManager(requireContext())

        setupWindowInsets()
        setupListeners()
        setupRecyclerView()
        setupSwipeRefresh()

        showSkeletonLoaders()
        loadPolls()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.pollsRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.detailHeader.setPadding(
                binding.detailHeader.paddingLeft,
                systemBars.top,
                binding.detailHeader.paddingRight,
                binding.detailHeader.paddingBottom
            )
            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadPolls()
        }
    }

    private fun setupRecyclerView() {
        adapter = PollAdapter(pollsList) { poll, selectedOption ->
            saveUserVote(poll.id, selectedOption)
            if (selectedOption == 1) poll.votesOption1++ else poll.votesOption2++
            poll.userVotedOption = selectedOption
        }
        binding.rvPolls.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPolls.adapter = adapter
    }

    private fun showSkeletonLoaders() {
        val savedCount = auraPrefs.pollsCount

        binding.swipeRefreshLayout.isRefreshing = false
        binding.rvPolls.visibility = View.GONE
        binding.layoutCenterLoading.visibility = View.VISIBLE
        binding.layoutCenterLoading.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val count = savedCount.coerceIn(1, 10)
        repeat(count) {
            inflater.inflate(R.layout.item_poll_skeleton, binding.layoutCenterLoading, true)
        }

        ObjectAnimator.ofFloat(binding.layoutCenterLoading, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun loadPolls() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Simulación de tiempo de carga asíncrono para skeleton loader
            delay(600)

            if (_binding == null) return@launch

            val polls = listOf(
                PollModel(
                    "poll_1",
                    "¿Qué color prefieren para la fachada de la casa club?",
                    "Blanco Mate",
                    "Gris Claro",
                    45, 30,
                    getUserVote("poll_1")
                ),
                PollModel(
                    "poll_2",
                    "¿Cambiar el horario de riego a las noches?",
                    "Sí, mejor de noche",
                    "No, mantener de día",
                    80, 20,
                    getUserVote("poll_2")
                )
            )

            auraPrefs.pollsCount = polls.size

            pollsList.clear()
            pollsList.addAll(polls)

            binding.layoutCenterLoading.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false
            binding.rvPolls.visibility = View.VISIBLE

            adapter.updateData(pollsList)

            binding.rvPolls.alpha = 0f
            binding.rvPolls.translationY = -20f
            binding.rvPolls.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }
    }

    private fun getUserVote(pollId: String): Int? {
        val vote = prefs.getInt(pollId, -1)
        return if (vote != -1) vote else null
    }

    private fun saveUserVote(pollId: String, option: Int) {
        prefs.edit { putInt(pollId, option) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class PollAdapter(
    private var items: List<PollModel>,
    private val onVoteClick: (PollModel, Int) -> Unit
) : RecyclerView.Adapter<PollAdapter.PollViewHolder>() {

    class PollViewHolder(val binding: ItemPollBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollViewHolder {
        val binding = ItemPollBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PollViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PollViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvPollQuestion.text = item.question
        holder.binding.btnOption1.text = item.option1
        holder.binding.btnOption2.text = item.option2

        updateUI(holder, item, false)

        holder.binding.btnOption1.setOnClickListener {
            if (item.userVotedOption == null) {
                onVoteClick(item, 1)
                updateUI(holder, item, true)
            }
        }

        holder.binding.btnOption2.setOnClickListener {
            if (item.userVotedOption == null) {
                onVoteClick(item, 2)
                updateUI(holder, item, true)
            }
        }
    }

    private fun updateUI(holder: PollViewHolder, item: PollModel, animate: Boolean) {
        val context = holder.itemView.context
        if (item.userVotedOption != null) {
            if (animate) {
                TransitionManager.beginDelayedTransition(
                    holder.binding.root as ViewGroup,
                    AutoTransition().apply { duration = 400 }
                )
            }

            holder.binding.layoutOptions.visibility = View.GONE
            holder.binding.layoutResults.visibility = View.VISIBLE

            val selectedText = if (item.userVotedOption == 1) item.option1 else item.option2
            holder.binding.tvPollStatusBadge.text = context.getString(R.string.polls_voted_badge, selectedText)
            holder.binding.tvPollStatusBadge.setBackgroundResource(R.drawable.bg_pill_success)
            holder.binding.tvPollStatusBadge.setTextColor(context.getColor(R.color.aura_success))

            val total = item.votesOption1 + item.votesOption2
            val pct1 = if (total > 0) (item.votesOption1 * 100) / total else 0
            val pct2 = if (total > 0) (item.votesOption2 * 100) / total else 0

            holder.binding.tvResult1.text = context.getString(R.string.polls_result_format, item.option1, pct1)
            holder.binding.tvResult2.text = context.getString(R.string.polls_result_format, item.option2, pct2)
            holder.binding.tvTotalVotes.text = context.getString(R.string.polls_total_votes_format, total)

            val colorPrimary = context.getColor(R.color.aura_primary)
            val colorWarning = context.getColor(R.color.aura_warning)

            holder.binding.pieChart.setData(
                listOf(item.votesOption1.toFloat(), item.votesOption2.toFloat()),
                listOf(colorPrimary, colorWarning)
            )
        } else {
            holder.binding.layoutOptions.visibility = View.VISIBLE
            holder.binding.layoutResults.visibility = View.GONE
            holder.binding.tvPollStatusBadge.text = context.getString(R.string.polls_status_active)
            holder.binding.tvPollStatusBadge.setBackgroundResource(R.drawable.bg_pill_info)
            holder.binding.tvPollStatusBadge.setTextColor(context.getColor(R.color.aura_primary))
            val total = item.votesOption1 + item.votesOption2
            holder.binding.tvTotalVotes.text = context.getString(R.string.polls_total_votes_format, total)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: List<PollModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size
}
