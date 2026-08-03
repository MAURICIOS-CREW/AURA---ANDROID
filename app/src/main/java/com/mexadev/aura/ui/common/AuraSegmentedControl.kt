package com.mexadev.aura.ui.common

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.mexadev.aura.R

class AuraSegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val activeIndicator = View(context)
    private val tabsContainer = LinearLayout(context)

    private val tabViews = mutableListOf<TextView>()
    private var tabsList = listOf<String>()
    private var selectedIndex = 0

    private var onTabSelectedListener: ((position: Int, title: String) -> Unit)? = null
    private var isViewPagerBinding = false

    private val evaluator = ArgbEvaluator()
    private val activeColor = ContextCompat.getColor(context, R.color.aura_text_on_primary)
    private val inactiveColor = ContextCompat.getColor(context, R.color.aura_text_primary)

    private var slideAnimator: ValueAnimator? = null

    init {
        setBackgroundResource(R.drawable.bg_segmented_control)
        val paddingPx = (4 * resources.displayMetrics.density).toInt()
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

        // Setup active indicator pill
        val pillParams = LayoutParams(0, LayoutParams.MATCH_PARENT)
        activeIndicator.layoutParams = pillParams
        activeIndicator.setBackgroundResource(R.drawable.bg_button_tab_active)
        addView(activeIndicator)

        // Setup tabs container
        tabsContainer.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        tabsContainer.orientation = LinearLayout.HORIZONTAL
        addView(tabsContainer)
    }

    fun setTabs(tabs: List<String>, defaultIndex: Int = 0) {
        tabsList = tabs
        tabsContainer.removeAllViews()
        tabViews.clear()

        val weightSum = tabs.size.toFloat()
        tabsContainer.weightSum = weightSum

        tabs.forEachIndexed { index, title ->
            val tv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                gravity = Gravity.CENTER
                text = title
                textSize = if (tabs.size > 2) 13f else 14f
                setTextColor(if (index == defaultIndex) activeColor else inactiveColor)
                typeface = if (index == defaultIndex) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setOnClickListener {
                    if (!isViewPagerBinding) {
                        setSelectedIndex(index, animate = true)
                    }
                    onTabSelectedListener?.invoke(index, title)
                }
            }
            tabViews.add(tv)
            tabsContainer.addView(tv)
        }

        selectedIndex = defaultIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))

        post {
            updateIndicatorPosition(selectedIndex.toFloat(), animate = false)
        }
    }

    fun setOnTabSelectedListener(listener: (position: Int, title: String) -> Unit) {
        onTabSelectedListener = listener
    }

    fun setSelectedIndex(index: Int, animate: Boolean = true) {
        if (index !in tabViews.indices || (index == selectedIndex && animate && slideAnimator?.isRunning == true)) return

        val previousIndex = selectedIndex
        selectedIndex = index

        if (animate) {
            slideAnimator?.cancel()
            slideAnimator = ValueAnimator.ofFloat(previousIndex.toFloat(), index.toFloat()).apply {
                duration = 250
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    updateIndicatorPosition(progress, animate = true)
                }
                start()
            }
        } else {
            updateIndicatorPosition(index.toFloat(), animate = false)
        }
    }

    fun getSelectedIndex(): Int = selectedIndex

    fun setupWithViewPager2(viewPager: ViewPager2) {
        isViewPagerBinding = true
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                val currentProgress = position + positionOffset
                updateIndicatorPosition(currentProgress, animate = true)
            }

            override fun onPageSelected(position: Int) {
                selectedIndex = position
                onTabSelectedListener?.invoke(position, tabsList.getOrNull(position) ?: "")
            }
        })

        setOnTabSelectedListener { position, _ ->
            viewPager.currentItem = position
        }
    }

    private fun updateIndicatorPosition(progress: Float, animate: Boolean) {
        if (tabViews.isEmpty() || width == 0) return

        val totalUsableWidth = width - paddingLeft - paddingRight
        val tabWidth = totalUsableWidth / tabViews.size

        val params = activeIndicator.layoutParams
        if (params.width != tabWidth) {
            params.width = tabWidth
            activeIndicator.layoutParams = params
        }

        activeIndicator.translationX = progress * tabWidth

        // Interpolate colors across tabs
        tabViews.forEachIndexed { i, tv ->
            val distance = kotlin.math.abs(progress - i)
            val factor = (1f - distance).coerceIn(0f, 1f)
            val textColor = evaluator.evaluate(factor, inactiveColor, activeColor) as Int
            tv.setTextColor(textColor)
            tv.typeface = if (factor > 0.5f) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post {
            updateIndicatorPosition(selectedIndex.toFloat(), animate = false)
        }
    }
}
