package com.mobilerun.portal.ui.taskprompt

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.mobilerun.portal.R
import kotlin.math.abs

class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var counts: List<Int> = emptyList()
    private var labels: List<String> = emptyList()
    private var selectedIndex: Int = -1

    private val accentColor = ContextCompat.getColor(context, R.color.task_prompt_accent)
    private val mutedColor = ContextCompat.getColor(context, R.color.text_gray)
    private val cardColor = ContextCompat.getColor(context, R.color.background_card)
    private val whiteColor = ContextCompat.getColor(context, R.color.text_white)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = accentColor
        strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.task_prompt_stroke)
        strokeWidth = 1f * resources.displayMetrics.density
    }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = accentColor
        strokeWidth = 1f * resources.displayMetrics.density
        alpha = 80
        pathEffect = DashPathEffect(
            floatArrayOf(6f * resources.displayMetrics.density, 4f * resources.displayMetrics.density),
            0f,
        )
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedColor
        textSize = 10f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = accentColor
    }

    private val activeDotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tooltipStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.task_prompt_stroke)
        strokeWidth = 1f * resources.displayMetrics.density
    }

    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = whiteColor
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val tooltipSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textSize = 11f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val linePath = Path()
    private val fillPath = Path()
    private val tooltipRect = RectF()
    private val density = resources.displayMetrics.density

    private var cachedXs: FloatArray = FloatArray(0)
    private var cachedYs: FloatArray = FloatArray(0)
    private var cachedGradientTop = -1f
    private var cachedGradientBottom = -1f

    fun setData(counts: List<Int>, labels: List<String>) {
        this.counts = counts
        this.labels = labels
        selectedIndex = -1
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (170 * density).toInt()
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    private fun setAllParentsDisallowIntercept(disallow: Boolean) {
        var current = parent
        while (current != null) {
            current.requestDisallowInterceptTouchEvent(disallow)
            if (current is SwipeRefreshLayout) {
                current.isEnabled = !disallow
            }
            current = if (current is View) (current as View).parent else null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (cachedXs.isEmpty()) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                setAllParentsDisallowIntercept(true)
                val touchX = event.x
                var nearest = 0
                var minDist = Float.MAX_VALUE
                for (i in cachedXs.indices) {
                    val dist = abs(cachedXs[i] - touchX)
                    if (dist < minDist) {
                        minDist = dist
                        nearest = i
                    }
                }
                if (nearest != selectedIndex) {
                    selectedIndex = nearest
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                setAllParentsDisallowIntercept(false)
                selectedIndex = -1
                invalidate()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                setAllParentsDisallowIntercept(false)
                selectedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (counts.isEmpty()) return

        val count = counts.size
        val labelHeight = 20f * density
        val tooltipSpace = 52f * density
        val chartLeft = paddingLeft.toFloat()
        val chartRight = (width - paddingRight).toFloat()
        val chartTop = paddingTop + tooltipSpace
        val chartBottom = height - paddingBottom - labelHeight
        val chartWidth = chartRight - chartLeft
        val chartHeight = chartBottom - chartTop

        if (chartWidth <= 0f || chartHeight <= 0f) return

        val maxCount = counts.max().coerceAtLeast(1)
        val stepX = if (count > 1) chartWidth / (count - 1) else chartWidth

        val xs = FloatArray(count)
        val ys = FloatArray(count)
        for (i in 0 until count) {
            xs[i] = chartLeft + i * stepX
            ys[i] = chartTop + chartHeight - (counts[i].toFloat() / maxCount * chartHeight)
        }
        cachedXs = xs
        cachedYs = ys

        canvas.drawLine(chartLeft, chartBottom, chartRight, chartBottom, gridPaint)

        linePath.reset()
        fillPath.reset()
        linePath.moveTo(xs[0], ys[0])
        fillPath.moveTo(xs[0], chartBottom)
        fillPath.lineTo(xs[0], ys[0])

        for (i in 1 until count) {
            val cx = (xs[i - 1] + xs[i]) / 2f
            linePath.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
            fillPath.cubicTo(cx, ys[i - 1], cx, ys[i], xs[i], ys[i])
        }

        fillPath.lineTo(xs[count - 1], chartBottom)
        fillPath.close()

        if (chartTop != cachedGradientTop || chartBottom != cachedGradientBottom) {
            cachedGradientTop = chartTop
            cachedGradientBottom = chartBottom
            fillPaint.shader = LinearGradient(
                0f, chartTop, 0f, chartBottom,
                (accentColor and 0x00FFFFFF) or 0x59000000,
                (accentColor and 0x00FFFFFF) or 0x00000000,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)

        val dotRadius = 3f * density
        for (i in 0 until count) {
            if (i == selectedIndex) continue
            if (counts[i] > 0) {
                canvas.drawCircle(xs[i], ys[i], dotRadius, dotPaint)
            }
        }

        if (labels.size >= count && count >= 3) {
            val labelY = height - paddingBottom.toFloat()
            val indicesToDraw = listOf(0, count / 2, count - 1)
            for (i in indicesToDraw) {
                if (i == selectedIndex) continue
                labelPaint.textAlign = when (i) {
                    0 -> Paint.Align.LEFT
                    count - 1 -> Paint.Align.RIGHT
                    else -> Paint.Align.CENTER
                }
                canvas.drawText(labels[i], xs[i], labelY, labelPaint)
            }
        }

        if (selectedIndex in 0 until count) {
            drawTooltip(canvas, selectedIndex, xs, ys, chartTop, chartBottom)
        }
    }

    private fun drawTooltip(
        canvas: Canvas,
        index: Int,
        xs: FloatArray,
        ys: FloatArray,
        chartTop: Float,
        chartBottom: Float,
    ) {
        val x = xs[index]
        val y = ys[index]
        val taskCount = counts[index]
        val dateLabel = if (index < labels.size) labels[index] else ""

        canvas.drawLine(x, chartTop, x, chartBottom, cursorPaint)

        val activeDotRadius = 5f * density
        activeDotStrokePaint.color = cardColor
        canvas.drawCircle(x, y, activeDotRadius, dotPaint)
        canvas.drawCircle(x, y, activeDotRadius, activeDotStrokePaint)

        val tooltipText = context.getString(R.string.dashboard_sparkline_tooltip, taskCount)
        val tooltipW = tooltipTextPaint.measureText(tooltipText)
            .coerceAtLeast(tooltipSubPaint.measureText(dateLabel)) + 32f * density
        val tooltipH = 44f * density
        val tooltipPadding = 8f * density
        val cornerRadius = 10f * density

        var tooltipX = x - tooltipW / 2f
        if (tooltipX < paddingLeft) tooltipX = paddingLeft.toFloat()
        if (tooltipX + tooltipW > width - paddingRight) tooltipX = width - paddingRight - tooltipW

        val tooltipTop = (chartTop - tooltipH - tooltipPadding).coerceAtLeast(0f)
        tooltipRect.set(tooltipX, tooltipTop, tooltipX + tooltipW, tooltipTop + tooltipH)

        tooltipBgPaint.color = cardColor
        canvas.drawRoundRect(tooltipRect, cornerRadius, cornerRadius, tooltipBgPaint)
        canvas.drawRoundRect(tooltipRect, cornerRadius, cornerRadius, tooltipStrokePaint)

        val centerX = tooltipRect.centerX()
        canvas.drawText(dateLabel, centerX, tooltipRect.top + 17f * density, tooltipTextPaint)
        canvas.drawText(tooltipText, centerX, tooltipRect.bottom - 8f * density, tooltipSubPaint)
    }
}
