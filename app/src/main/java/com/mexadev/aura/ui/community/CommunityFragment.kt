package com.mexadev.aura.ui.community

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.SharedElementCallback
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mexadev.aura.R
import com.mexadev.aura.core.preferences.PreferencesManager
import com.mexadev.aura.databinding.FragmentCommunityBinding
import com.mexadev.aura.databinding.ItemCommunityPostBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CommunityFragment : Fragment() {

    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: CommunityDatabaseHelper
    private lateinit var adapter: CommunityAdapter
    private lateinit var detailLauncher: ActivityResultLauncher<Intent>
    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshPosts()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dbHelper = CommunityDatabaseHelper(requireContext())
        prefs = PreferencesManager(requireContext())

        setupWindowInsets()
        setupSharedElementCallback()
        setupRecyclerView()
        setupSwipeRefresh()
        setupListeners()

        showSkeletonLoaders()
        fetchPostsWithDelay()
    }

    private fun setupSharedElementCallback() {
        requireActivity().setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                val currentBinding = _binding ?: return
                if (names.isEmpty()) return
                val name = names[0]
                val layoutManager = currentBinding.rvCommunityPosts.layoutManager as? LinearLayoutManager
                    ?: return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val lastVisible  = layoutManager.findLastVisibleItemPosition()
                for (i in firstVisible..lastVisible) {
                    val holder = currentBinding.rvCommunityPosts.findViewHolderForAdapterPosition(i) as? CommunityAdapter.PostViewHolder
                    val cardView = holder?.binding?.cardPost ?: continue
                    if (cardView.transitionName == name) {
                        sharedElements[name] = cardView
                        return
                    }
                }
            }
        })
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.communityRoot) { _, insets ->
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
        val openCreateBottomSheet = {
            val dialog = CreatePostBottomSheetFragment {
                refreshPosts()
                binding.rvCommunityPosts.smoothScrollToPosition(0)
            }
            dialog.show(childFragmentManager, "CreatePostBottomSheet")
        }

        binding.cardNewPostTrigger.setOnClickListener { openCreateBottomSheet() }
        binding.tvNewPost.setOnClickListener { openCreateBottomSheet() }
        binding.fabAddPost.setOnClickListener { openCreateBottomSheet() }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.aura_primary)
        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchPostsWithDelay()
        }
    }

    private fun setupRecyclerView() {
        adapter = CommunityAdapter(
            items = emptyList(),
            onLikeClick = { post ->
                dbHelper.toggleLike(post.id)
                refreshPosts()
            },
            onCommentClick = { post, itemBinding ->
                openPostDetailWithSharedElements(post, itemBinding)
            }
        )
        binding.rvCommunityPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCommunityPosts.adapter = adapter
    }

    private fun showSkeletonLoaders() {
        val savedCount = prefs.communityCount

        binding.swipeRefreshLayout.isRefreshing = false
        binding.rvCommunityPosts.visibility = View.GONE
        binding.layoutCenterLoading.visibility = View.VISIBLE
        binding.layoutCenterLoading.removeAllViews()

        val inflater = LayoutInflater.from(requireContext())
        val count = savedCount.coerceIn(1, 10)
        repeat(count) {
            inflater.inflate(R.layout.item_community_post_skeleton, binding.layoutCenterLoading, true)
        }

        ObjectAnimator.ofFloat(binding.layoutCenterLoading, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun fetchPostsWithDelay() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Simulación de tiempo de carga asíncrono para skeleton loader
            delay(600)

            if (_binding == null) return@launch

            val posts = dbHelper.getAllPosts()
            prefs.communityCount = posts.size

            binding.layoutCenterLoading.visibility = View.GONE
            binding.swipeRefreshLayout.isRefreshing = false
            binding.rvCommunityPosts.visibility = View.VISIBLE

            adapter.updateData(posts)

            binding.rvCommunityPosts.alpha = 0f
            binding.rvCommunityPosts.translationY = -20f
            binding.rvCommunityPosts.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }
    }

    private fun openPostDetailWithSharedElements(post: PostModel, itemBinding: ItemCommunityPostBinding) {
        val cardTransName = "transition_post_card_${post.id}"
        itemBinding.cardPost.transitionName = cardTransName

        val intent = Intent(requireContext(), CommunityPostDetailActivity::class.java).apply {
            putExtra(CommunityPostDetailActivity.EXTRA_POST_ID, post.id)
            putExtra(CommunityPostDetailActivity.EXTRA_TRANSITION_CARD_NAME, cardTransName)
        }

        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(),
            Pair(itemBinding.cardPost, cardTransName)
        )

        detailLauncher.launch(intent, options)
    }

    private fun refreshPosts() {
        val posts = dbHelper.getAllPosts()
        prefs.communityCount = posts.size
        adapter.updateData(posts)
    }

    override fun onDestroyView() {
        activity?.setExitSharedElementCallback(null as SharedElementCallback?)
        super.onDestroyView()
        _binding = null
    }
}

class CommunityAdapter(
    private var items: List<PostModel>,
    private val onLikeClick: (PostModel) -> Unit,
    private val onCommentClick: (PostModel, ItemCommunityPostBinding) -> Unit
) : RecyclerView.Adapter<CommunityAdapter.PostViewHolder>() {

    class PostViewHolder(val binding: ItemCommunityPostBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val binding = ItemCommunityPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PostViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.binding.tvAuthorName.text = item.authorName
        holder.binding.tvTimestamp.text = CommunityTimeUtils.formatRelativeTime(item.timestamp)
        holder.binding.tvPostContent.text = item.content
        holder.binding.tvLikeCount.text = context.getString(R.string.community_like_count_format, item.likes)
        holder.binding.tvCommentCount.text = context.getString(R.string.community_comment_count_format, item.comments)

        if (item.imageResId != null) {
            holder.binding.ivPostImage.visibility = View.VISIBLE
            holder.binding.ivPostImage.setImageResource(item.imageResId)
        } else {
            holder.binding.ivPostImage.visibility = View.GONE
        }

        if (item.isLiked) {
            holder.binding.ivLikeIcon.setImageResource(R.drawable.ic_heart_filled)
            holder.binding.ivLikeIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_error))
            holder.binding.tvLikeCount.setTextColor(ContextCompat.getColor(context, R.color.aura_error))
        } else {
            holder.binding.ivLikeIcon.setImageResource(R.drawable.ic_heart)
            holder.binding.ivLikeIcon.setColorFilter(ContextCompat.getColor(context, R.color.aura_text_secondary))
            holder.binding.tvLikeCount.setTextColor(ContextCompat.getColor(context, R.color.aura_text_secondary))
        }

        val cardTransName = "transition_post_card_${item.id}"
        holder.binding.cardPost.transitionName = cardTransName

        holder.binding.btnLike.setOnClickListener {
            onLikeClick(item)
        }

        holder.binding.btnComment.setOnClickListener {
            onCommentClick(item, holder.binding)
        }

        holder.binding.cardPost.setOnClickListener {
            onCommentClick(item, holder.binding)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newItems: List<PostModel>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size
}
