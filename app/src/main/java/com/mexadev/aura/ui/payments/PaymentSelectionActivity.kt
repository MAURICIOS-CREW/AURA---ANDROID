package com.mexadev.aura.ui.payments

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Transition
import android.transition.TransitionSet
import android.view.View
import android.view.Window
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.SharedElementCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.children
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mexadev.aura.R
import com.mexadev.aura.data.model.PaymentProcessItemRequest
import com.mexadev.aura.data.model.PendingPaymentItem
import com.mexadev.aura.databinding.ActivityPaymentSelectionBinding
import com.mexadev.aura.ui.common.BannerManager
import java.util.Locale

/**
 * Pantalla de Selección de Conceptos a Pagar.
 * Participa en Shared Element Transitions y recalcula el monto final en tiempo real.
 */
class PaymentSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentSelectionBinding
    private lateinit var bannerManager: BannerManager
    private lateinit var adapter: PendingConceptAdapter

    private var pendingItems: List<PendingPaymentItem> = emptyList()
    private var transitionMode: String = MODE_CARD
    private var conceptAttachListener: RecyclerView.OnChildAttachStateChangeListener? = null

    companion object {
        const val EXTRA_PENDING_ITEMS_JSON = "extra_pending_items_json"
        const val EXTRA_TRANSITION_MODE = "extra_transition_mode"

        /** Origin was the balance card / wallet icon: card + amount + label morph together. */
        const val MODE_CARD = "mode_card"

        /** Origin was the "Pagar ahora" button: only the button morphs, the rest springs in. */
        const val MODE_BUTTON = "mode_button"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called BEFORE super.onCreate for transitions to work
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)

        transitionMode = intent.getStringExtra(EXTRA_TRANSITION_MODE) ?: MODE_CARD

        // Exit callback on the destination so the return transition re-maps correctly
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())

        // Transparent until the shared element morph draws the real background
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        window.sharedElementEnterTransition = buildEnterTransition()
        window.sharedElementReturnTransition = buildReturnTransition()

        super.onCreate(savedInstanceState)
        binding = ActivityPaymentSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Delay the transition until the RecyclerView has laid out its children
        postponeEnterTransition()

        bannerManager = BannerManager(binding.selectionBannerContainer, binding.tvSelectionBannerMessage)

        val json = intent.getStringExtra(EXTRA_PENDING_ITEMS_JSON)
        if (json != null) {
            try {
                val type = object : TypeToken<List<PendingPaymentItem>>() {}.type
                pendingItems = Gson().fromJson(json, type)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setupUI()

        // Once the enter transition ends, make the background solid so
        // the activity is no longer translucent during normal interaction
        window.sharedElementEnterTransition.addListener(object : Transition.TransitionListener {
            override fun onTransitionEnd(transition: Transition) {
                window.setBackgroundDrawable(
                    ContextCompat.getColor(this@PaymentSelectionActivity, R.color.aura_background).toDrawable()
                )
            }
            override fun onTransitionStart(transition: Transition) {}
            override fun onTransitionCancel(transition: Transition) {}
            override fun onTransitionPause(transition: Transition) {}
            override fun onTransitionResume(transition: Transition) {}
        })

        // On the way back, re-map so each shared element finds its correct origin view
        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                sharedElements["transition_card_balance"] = binding.cardSelectionHeader
                sharedElements["transition_tv_balance"] = binding.tvTotalCalculatedAmount
                sharedElements["transition_tv_label"] = binding.tvSelectionSubtitle
                sharedElements["transition_btn_pay"] = binding.btnProceedToPay
            }
        })

        // Start transition only after the first frame is fully drawn
        binding.root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                binding.root.viewTreeObserver.removeOnPreDrawListener(this)
                startPostponedEnterTransition()
                if (transitionMode == MODE_BUTTON) {
                    runPhysicsEntranceForRest()
                }
                return true
            }
        })
    }

    /**
     * MODE_CARD: the card container-morphs while the label/amount text repositions alongside it.
     * MODE_BUTTON: only the button container-morphs; [runPhysicsEntranceForRest] handles the rest.
     */
    private fun buildEnterTransition(): Transition = when (transitionMode) {
        MODE_BUTTON -> buttonContainerTransform(duration = 340L)
        else -> TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(cardContainerTransform(duration = 380L))
            addTransition(textRepositionTransition(duration = 380L))
        }
    }

    private fun buildReturnTransition(): Transition = when (transitionMode) {
        MODE_BUTTON -> buttonContainerTransform(duration = 300L)
        else -> TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(cardContainerTransform(duration = 320L))
            addTransition(textRepositionTransition(duration = 320L))
        }
    }

    private fun cardContainerTransform(duration: Long) = MaterialContainerTransform().apply {
        // Target the specific shared view by transitionName, NOT the whole window content
        addTarget("transition_card_balance")
        this.duration = duration
        // Prevent the scrim from flashing during the morph
        scrimColor = Color.TRANSPARENT
        // Use the card's real background color so there is no flash
        containerColor = Color.WHITE
        fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
        isElevationShadowEnabled = true
    }

    private fun buttonContainerTransform(duration: Long) = MaterialContainerTransform().apply {
        addTarget("transition_btn_pay")
        this.duration = duration
        scrimColor = Color.TRANSPARENT
        // Use the button's real background color so there is no flash mid-morph
        containerColor = ContextCompat.getColor(this@PaymentSelectionActivity, R.color.aura_primary)
        fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
        isElevationShadowEnabled = true
    }

    private fun textRepositionTransition(duration: Long) = ChangeBounds().apply {
        addTarget("transition_tv_balance")
        addTarget("transition_tv_label")
        this.duration = duration
        interpolator = FastOutSlowInInterpolator()
    }

    /**
     * Everything that ISN'T the shared button gets its own entrance here: a staggered
     * spring cascade (translation + scale) matching the physics used elsewhere in the app
     * (see ConfirmBottomSheetFragment) instead of riding along the shared element transition.
     */
    private fun runPhysicsEntranceForRest() {
        conceptAttachListener?.let { binding.rvPendingConcepts.removeOnChildAttachStateChangeListener(it) }
        conceptAttachListener = null

        animateInWithSpring(binding.cardSelectionHeader, delay = 40L)
        binding.rvPendingConcepts.children.forEachIndexed { index, child ->
            animateInWithSpring(child, delay = 90L + index * 45L)
        }
    }

    private fun animateInWithSpring(view: View, delay: Long) {
        val springY = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        }
        val springScaleX = SpringAnimation(view, DynamicAnimation.SCALE_X, 1.0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        }
        val springScaleY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1.0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_LOW
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        }
        view.postDelayed({
            view.animate().alpha(1f).setDuration(200).start()
            springY.start()
            springScaleX.start()
            springScaleY.start()
        }, delay)
    }

    private fun setupUI() {
        binding.btnBackSelection.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter = PendingConceptAdapter(pendingItems) {
            recalculateTotal()
        }

        binding.rvPendingConcepts.layoutManager = LinearLayoutManager(this)
        binding.rvPendingConcepts.adapter = adapter

        if (transitionMode == MODE_BUTTON) {
            // Hide the card + list up-front so runPhysicsEntranceForRest() can spring them
            // in without a pre-transition flash of the final, static layout.
            binding.cardSelectionHeader.alpha = 0f
            binding.cardSelectionHeader.translationY = 40f
            binding.cardSelectionHeader.scaleX = 0.94f
            binding.cardSelectionHeader.scaleY = 0.94f

            conceptAttachListener = object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    view.alpha = 0f
                    view.translationY = 40f
                    view.scaleX = 0.94f
                    view.scaleY = 0.94f
                }
                override fun onChildViewDetachedFromWindow(view: View) {}
            }
            binding.rvPendingConcepts.addOnChildAttachStateChangeListener(conceptAttachListener!!)
        }

        recalculateTotal()

        binding.btnProceedToPay.setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) {
                bannerManager.show(getString(R.string.payments_select_at_least_one))
                return@setOnClickListener
            }

            val itemsToPay = selected.map { item ->
                PaymentProcessItemRequest(
                    type = item.type,
                    id = item.id,
                    residenceId = null
                )
            }

            val totalStr = calculateTotalSum(selected)

            val bottomSheet = PaymentFormBottomSheetFragment.newInstance(
                items = itemsToPay,
                totalAmount = totalStr,
                onSuccess = {
                    setResult(RESULT_OK)
                    finish()
                }
            )
            bottomSheet.show(supportFragmentManager, "PaymentFormBottomSheet")
        }
    }

    private fun recalculateTotal() {
        val selected = adapter.getSelectedItems()
        val totalStr = calculateTotalSum(selected)
        binding.tvTotalCalculatedAmount.text = getString(R.string.payments_balance_format, totalStr)
    }

    private fun calculateTotalSum(selected: List<PendingPaymentItem>): String {
        var total = 0.0
        selected.forEach { item ->
            val amt = item.amount.toDoubleOrNull() ?: 0.0
            total += amt
        }
        return String.format(Locale.US, "%.2f", total)
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerManager.destroy()
    }
}
