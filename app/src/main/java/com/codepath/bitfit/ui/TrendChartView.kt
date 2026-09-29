package com.codepath.bitfit.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.codepath.bitfit.R
import com.codepath.bitfit.util.ChartPoint
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.max

/**
 * A small hand-drawn line chart (no chart library needed).
 * - gaps in the line for days with no data
 * - gradient fill, dashed goal line
 * - animated reveal and tap/drag to inspect a value
 */
class TrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var points: List<ChartPoint> = emptyList()
    private var goal: Float? = null
    private var fixedMax: Float? = null
    private var valueFormatter: (Float) -> String = { it.toInt().toString() }
    private var lineColor: Int = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private var selected: Int = -1
    private var reveal = 1f

    private val density = resources.displayMetrics.density

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    private val textColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val gridColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
    private val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh)
    private val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val goalColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorTertiary)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gridColor
        strokeWidth = 1f * density
    }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = goalColor
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = DashPathEffect(floatArrayOf(6f * density, 5f * density), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = sp(11f)
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = surfaceColor }
    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onSurface
        textSize = sp(12f)
        isFakeBoldText = true
    }

    private val chartRect = RectF()
    private val path = Path()
    private val fillPath = Path()
    private var animator: ValueAnimator? = null

    fun setData(
        points: List<ChartPoint>,
        color: Int,
        goal: Float?,
        fixedMax: Float? = null,
        formatter: (Float) -> String = { it.toInt().toString() },
        animate: Boolean = true,
    ) {
        this.points = points
        this.lineColor = color
        this.goal = goal
        this.fixedMax = fixedMax
        this.valueFormatter = formatter
        this.selected = -1
        val withValues = points.filter { it.value != null }
        contentDescription = if (withValues.isEmpty()) {
            context.getString(R.string.chart_empty)
        } else {
            context.getString(
                R.string.chart_content_description,
                withValues.size,
                formatter(withValues.minOf { it.value!! }),
                formatter(withValues.maxOf { it.value!! })
            )
        }
        animator?.cancel()
        if (animate) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 650
                interpolator = DecelerateInterpolator()
                addUpdateListener { reveal = it.animatedValue as Float; invalidate() }
                start()
            }
        } else {
            reveal = 1f
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (200 * density).toInt()
        val h = resolveSize(desiredHeight, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }

    /** Number of horizontal grid intervals. */
    private fun gridSteps(): Int = if (fixedMax != null && fixedMax!! <= 10f && fixedMax!! % 1f == 0f) fixedMax!!.toInt() else 4

    private fun maxValue(): Float {
        fixedMax?.let { return it }
        val dataMax = points.mapNotNull { it.value }.maxOrNull() ?: 0f
        val top = max(dataMax, goal ?: 0f)
        if (top <= 0f) return 4f
        // Round the axis up to a "nice" step so labels read 0 / 750 / 1500 … or 0 / 2.5 / 5 …
        val rawStep = top * 1.1f / 4f
        val magnitude = Math.pow(10.0, Math.floor(Math.log10(rawStep.toDouble()))).toFloat()
        val normalized = rawStep / magnitude
        val nice = NICE_STEPS.first { it >= normalized }
        return nice * magnitude * 4f
    }

    private fun xFor(index: Int): Float {
        if (points.size <= 1) return chartRect.centerX()
        return chartRect.left + chartRect.width() * index / (points.size - 1)
    }

    private fun yFor(value: Float, max: Float): Float =
        chartRect.bottom - chartRect.height() * (value / max).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val yLabelWidth = labelPaint.measureText("0000") + 8 * density
        chartRect.set(
            paddingLeft + yLabelWidth,
            paddingTop + 28 * density,
            width - paddingRight - 14 * density,
            height - paddingBottom - 22 * density
        )

        val max = maxValue()

        // Horizontal grid + y labels
        labelPaint.textAlign = Paint.Align.RIGHT
        val steps = gridSteps()
        for (i in 0..steps) {
            val v = max * i / steps
            val y = yFor(v, max)
            canvas.drawLine(chartRect.left, y, chartRect.right, y, gridPaint)
            canvas.drawText(valueFormatter(v), chartRect.left - 6 * density, y + 4 * density, labelPaint)
        }

        if (points.none { it.value != null }) {
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.chart_empty), chartRect.centerX(), chartRect.centerY(), labelPaint)
            drawXLabels(canvas)
            return
        }

        // Goal line
        goal?.let { g ->
            if (g in 0f..max) {
                val y = yFor(g, max)
                canvas.drawLine(chartRect.left, y, chartRect.right, y, goalPaint)
                labelPaint.textAlign = Paint.Align.LEFT
                labelPaint.color = goalColor
                canvas.drawText(
                    context.getString(R.string.chart_goal_value, valueFormatter(g)),
                    chartRect.left + 4 * density, y - 5 * density, labelPaint
                )
                labelPaint.color = textColor
            }
        }

        // Clip for the reveal animation
        canvas.save()
        canvas.clipRect(0f, 0f, chartRect.left + (chartRect.width() + 8 * density) * reveal, height.toFloat())

        linePaint.color = lineColor
        fillPaint.shader = LinearGradient(
            0f, chartRect.top, 0f, chartRect.bottom,
            ColorUtils.setAlphaComponent(lineColor, 90), ColorUtils.setAlphaComponent(lineColor, 0),
            Shader.TileMode.CLAMP
        )

        // Build contiguous segments (a null value breaks the line)
        var segment = mutableListOf<Int>()
        val segments = mutableListOf<List<Int>>()
        points.forEachIndexed { i, p ->
            if (p.value != null) segment.add(i) else if (segment.isNotEmpty()) {
                segments += segment; segment = mutableListOf()
            }
        }
        if (segment.isNotEmpty()) segments += segment

        for (seg in segments) {
            path.reset(); fillPath.reset()
            seg.forEachIndexed { k, i ->
                val x = xFor(i)
                val y = yFor(points[i].value!!, max)
                if (k == 0) {
                    path.moveTo(x, y); fillPath.moveTo(x, chartRect.bottom); fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y); fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(xFor(seg.last()), chartRect.bottom)
            fillPath.close()
            if (seg.size > 1) {
                canvas.drawPath(fillPath, fillPaint)
                canvas.drawPath(path, linePaint)
            }
        }

        // Dots (skip if very dense)
        val dotRadius = if (points.size > 45) 0f else 3.5f * density
        if (dotRadius > 0f) {
            points.forEachIndexed { i, p ->
                val v = p.value ?: return@forEachIndexed
                dotPaint.color = lineColor
                canvas.drawCircle(xFor(i), yFor(v, max), dotRadius, dotPaint)
            }
        }
        canvas.restore()

        drawXLabels(canvas)
        drawSelection(canvas, max)
    }

    private fun drawXLabels(canvas: Canvas) {
        if (points.isEmpty()) return
        val indices = when {
            points.size <= 7 -> points.indices.toList()
            else -> listOf(0, points.size / 3, 2 * points.size / 3, points.size - 1)
        }
        labelPaint.textAlign = Paint.Align.CENTER
        for (i in indices.distinct()) {
            val half = labelPaint.measureText(points[i].label) / 2f
            // Keep the first/last labels fully on screen
            val x = xFor(i).coerceIn(half, width - paddingRight - half)
            canvas.drawText(points[i].label, x, chartRect.bottom + 16 * density, labelPaint)
        }
    }

    private fun drawSelection(canvas: Canvas, max: Float) {
        val i = selected
        if (i !in points.indices) return
        val v = points[i].value ?: return
        val x = xFor(i)
        val y = yFor(v, max)
        canvas.drawLine(x, chartRect.top, x, chartRect.bottom, gridPaint)
        dotPaint.color = lineColor
        canvas.drawCircle(x, y, 6f * density, dotPaint)
        dotPaint.color = surfaceColor
        canvas.drawCircle(x, y, 3f * density, dotPaint)

        val text = "${points[i].label}: ${valueFormatter(v)}"
        val w = bubbleText.measureText(text) + 16 * density
        val h = 24 * density
        val left = (x - w / 2).coerceIn(0f, width - w)
        val top = paddingTop.toFloat()
        canvas.drawRoundRect(left, top, left + w, top + h, 12 * density, 12 * density, bubblePaint)
        bubbleText.textAlign = Paint.Align.CENTER
        canvas.drawText(text, left + w / 2, top + h / 2 + 4.5f * density, bubbleText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (points.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val nearest = points.indices
                    .filter { points[it].value != null }
                    .minByOrNull { abs(xFor(it) - event.x) } ?: -1
                if (nearest != selected) {
                    selected = nearest
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    companion object {
        private val NICE_STEPS = floatArrayOf(1f, 1.5f, 2f, 2.5f, 3f, 4f, 5f, 6f, 7.5f, 8f, 10f)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
