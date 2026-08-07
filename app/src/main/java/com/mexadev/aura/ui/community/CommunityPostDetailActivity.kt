package com.mexadev.aura.ui.community

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.mexadev.aura.R
import com.mexadev.aura.databinding.ActivityCommunityPostDetailBinding
import androidx.core.graphics.drawable.toDrawable

class CommunityPostDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_POST_ID = "extra_post_id"
        const val EXTRA_TRANSITION_CARD_NAME = "extra_transition_card_name"
        const val TRANSITION_NAME_DEFAULT = "shared_post_card"
    }

    private lateinit var binding: ActivityCommunityPostDetailBinding
    private lateinit var dbHelper: CommunityDatabaseHelper
    private lateinit var commentsAdapter: CommunityCommentsAdapter
    private var postId: Long = -1L
    private var currentPost: PostModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityPostDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, binding.root).isAppearanceLightStatusBars = true

        dbHelper = CommunityDatabaseHelper(this)
        postId = intent.getLongExtra(EXTRA_POST_ID, -1L)

        val cardTransName = intent.getStringExtra(EXTRA_TRANSITION_CARD_NAME) ?: TRANSITION_NAME_DEFAULT
        binding.cardPostDetail.transitionName = cardTransName

        // Configure MaterialContainerTransform for Enter and Return
        window.sharedElementEnterTransition = MaterialContainerTransform().apply {
            addTarget(cardTransName)
            duration = 380
            isElevationShadowEnabled = false
            interpolator = AnimationUtils.loadInterpolator(
                this@CommunityPostDetailActivity, android.R.interpolator.fast_out_slow_in
            )
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            excludeTarget(android.R.id.statusBarBackground, true)
            excludeTarget(android.R.id.navigationBarBackground, true)
        }

        window.sharedElementReturnTransition = MaterialContainerTransform().apply {
            addTarget(cardTransName)
            duration = 350
            isElevationShadowEnabled = false
            interpolator = AnimationUtils.loadInterpolator(
                this@CommunityPostDetailActivity, android.R.interpolator.fast_out_slow_in
            )
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
            excludeTarget(android.R.id.statusBarBackground, true)
            excludeTarget(android.R.id.navigationBarBackground, true)
        }

        postponeEnterTransition()
        binding.detailRoot.post { startPostponedEnterTransition() }

        setupWindowInsets()
        setupListeners()
        loadPostData()
        setupCommentsList()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.detailHeader.setPadding(
                binding.detailHeader.paddingLeft,
                systemBars.top,
                binding.detailHeader.paddingRight,
                binding.detailHeader.paddingBottom
            )

            val bottomPadding = if (ime.bottom > 0) ime.bottom else systemBars.bottom
            binding.layoutInputContainer.setPadding(
                binding.layoutInputContainer.paddingLeft,
                binding.layoutInputContainer.paddingTop,
                binding.layoutInputContainer.paddingRight,
                bottomPadding
            )

            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finishAfterTransition()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAfterTransition()
            }
        })

        binding.btnLike.setOnClickListener {
            if (postId != -1L) {
                val updatedPost = dbHelper.toggleLike(postId)
                if (updatedPost != null) {
                    currentPost = updatedPost
                    updateLikeUi(updatedPost.likes, updatedPost.isLiked, animate = true)
                }
            }
        }

        binding.btnSendComment.setOnClickListener {
            val content = binding.etCommentInput.text?.toString()?.trim()
            if (content.isNullOrEmpty()) {
                Toast.makeText(this, "Escribe un comentario", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (postId != -1L) {
                val newComment = CommentModel(
                    postId = postId,
                    authorName = "Tú",
                    content = content,
                    timestamp = System.currentTimeMillis().toString(),
                    isMe = true
                )
                dbHelper.addComment(newComment)
                binding.etCommentInput.text?.clear()

                // Hide keyboard
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.etCommentInput.windowToken, 0)

                // Refresh post data & comments list
                loadPostData()
                refreshComments()

                // Scroll to bottom
                binding.nestedScrollView.post {
                    binding.nestedScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }
        }
    }

    private fun loadPostData() {
        if (postId == -1L) return
        val post = dbHelper.getPostById(postId) ?: return
        currentPost = post

        binding.tvAuthorName.text = post.authorName
        binding.tvTimestamp.text = CommunityTimeUtils.formatRelativeTime(post.timestamp)
        binding.tvPostContent.text = post.content
        binding.tvCommentCountHeader.text = getString(R.string.community_comment_count_format, post.comments)

        if (post.imageResId != null) {
            binding.ivPostImage.visibility = View.VISIBLE
            binding.ivPostImage.setImageResource(post.imageResId)
        } else {
            binding.ivPostImage.visibility = View.GONE
        }

        updateLikeUi(post.likes, post.isLiked, animate = false)
    }

    private fun updateLikeUi(likesCount: Int, isLiked: Boolean, animate: Boolean) {
        binding.tvLikeCount.text = getString(R.string.community_like_count_format, likesCount)

        if (isLiked) {
            binding.ivLikeIcon.setImageResource(R.drawable.ic_heart_filled)
            binding.ivLikeIcon.setColorFilter(ContextCompat.getColor(this, R.color.aura_error))
            binding.tvLikeCount.setTextColor(ContextCompat.getColor(this, R.color.aura_error))
            binding.btnLike.setBackgroundResource(R.drawable.bg_pill_error)
        } else {
            binding.ivLikeIcon.setImageResource(R.drawable.ic_heart)
            binding.ivLikeIcon.setColorFilter(ContextCompat.getColor(this, R.color.aura_text_secondary))
            binding.tvLikeCount.setTextColor(ContextCompat.getColor(this, R.color.aura_text_secondary))
            binding.btnLike.setBackgroundResource(R.drawable.bg_rounded_gray)
        }

        if (animate) {
            val scaleX = ObjectAnimator.ofFloat(binding.ivLikeIcon, "scaleX", 0.7f, 1.3f, 1.0f)
            val scaleY = ObjectAnimator.ofFloat(binding.ivLikeIcon, "scaleY", 0.7f, 1.3f, 1.0f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                duration = 300
                interpolator = OvershootInterpolator()
                start()
            }
        }
    }

    private fun setupCommentsList() {
        val comments = if (postId != -1L) dbHelper.getCommentsForPost(postId) else emptyList()
        commentsAdapter = CommunityCommentsAdapter(comments)
        binding.rvComments.layoutManager = LinearLayoutManager(this)
        binding.rvComments.adapter = commentsAdapter
    }

    private fun refreshComments() {
        if (postId == -1L) return
        val comments = dbHelper.getCommentsForPost(postId)
        commentsAdapter.updateData(comments)
    }
}
