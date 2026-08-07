package com.mexadev.aura.ui.polls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

import androidx.core.graphics.toColorInt

class PieChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    
    private val rectF = RectF()
    private var data: List<Float> = listOf(50f, 30f, 20f)
    private var colors: List<Int> = listOf(
        "#4CAF50".toColorInt(), // Green
        "#FF9800".toColorInt(), // Orange
        "#F44336".toColorInt()  // Red
    )

    fun setData(newData: List<Float>, newColors: List<Int>) {
        data = newData
        colors = newColors
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val size = Math.min(width, height)
        val cx = width / 2f
        val cy = height / 2f
        
        rectF.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        
        val total = data.sum()
        if (total == 0f) return
        
        var startAngle = -90f
        
        for (i in data.indices) {
            val sweepAngle = (data[i] / total) * 360f
            paint.color = colors.getOrElse(i) { Color.GRAY }
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            startAngle += sweepAngle
        }
    }
}
