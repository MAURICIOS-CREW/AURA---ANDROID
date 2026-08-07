package com.mexadev.aura.ui.documents

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import com.mexadev.aura.R
import com.mexadev.aura.databinding.ActivityDocumentDetailBinding
import com.mexadev.aura.ui.common.BannerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DocumentDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_doc_title"
        const val EXTRA_DESCRIPTION = "extra_doc_desc"
        const val EXTRA_CONTENT = "extra_doc_content"
        const val EXTRA_TRANSITION_NAME = "extra_transition_name"
        const val TRANSITION_NAME_DEFAULT = "shared_doc_card"
    }

    private lateinit var binding: ActivityDocumentDetailBinding
    private lateinit var successBanner: BannerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)

        val transition = android.transition.TransitionSet().apply {
            addTransition(android.transition.ChangeBounds())
            addTransition(android.transition.ChangeTransform())
            addTransition(android.transition.ChangeImageTransform())
            duration = 380
            interpolator = FastOutSlowInInterpolator()
        }
        window.sharedElementEnterTransition = transition
        window.sharedElementReturnTransition = transition

        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)
        binding = ActivityDocumentDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, binding.root).isAppearanceLightStatusBars = true

        val cardTransName = intent.getStringExtra(EXTRA_TRANSITION_NAME) ?: TRANSITION_NAME_DEFAULT
        val titleTransName = intent.getStringExtra("transition_title_name")
        val descTransName = intent.getStringExtra("transition_desc_name")
        val iconTransName = intent.getStringExtra("transition_icon_name")

        cardTransName.let { ViewCompat.setTransitionName(binding.cardDocumentDetail, it) }
        titleTransName?.let { ViewCompat.setTransitionName(binding.tvDocTitle, it) }
        descTransName?.let { ViewCompat.setTransitionName(binding.tvDocDescription, it) }
        iconTransName?.let { ViewCompat.setTransitionName(binding.ivDetailIcon, it) }

        successBanner = BannerManager(binding.successBanner, binding.tvSuccessBannerMessage)

        setupWindowInsets()
        setupListeners()
        loadDocumentData()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.detailRoot) { _, insets ->
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
            finishAfterTransition()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finishAfterTransition()
            }
        })

        binding.btnDownloadDoc.setOnClickListener {
            downloadDocument()
        }

        binding.btnShareDoc.setOnClickListener {
            Toast.makeText(this, "Enlace de documento copiado al portapapeles", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadDocumentData() {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.documents_detail_title)
        val description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: ""
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""

        binding.tvDocTitle.text = title
        binding.tvDocDescription.text = description
        binding.tvDocContent.text = content
    }

    private fun downloadDocument() {
        lifecycleScope.launch {
            binding.btnDownloadDoc.isEnabled = false
            binding.layoutDownloadContent.visibility = View.INVISIBLE
            binding.pbDownload.visibility = View.VISIBLE

            delay(1000)

            if (!isFinishing && !isDestroyed) {
                binding.pbDownload.visibility = View.GONE
                binding.layoutDownloadContent.visibility = View.VISIBLE
                binding.btnDownloadDoc.isEnabled = true
                successBanner.show(getString(R.string.documents_download_success))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        successBanner.destroy()
    }
}
