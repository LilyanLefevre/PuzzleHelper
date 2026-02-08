package com.lilyan_lefevre.puzzleit.shared.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class QuadSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnQuadChangedListener {
        fun onQuadChanged(points: List<PointF>)
    }

    private var onQuadChangedListener: OnQuadChangedListener? = null

    fun setOnQuadChangedListener(listener: OnQuadChangedListener?) {
        onQuadChangedListener = listener
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.RED
        strokeWidth = 6f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 255, 0, 0)
    }

    private val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.GREEN
        strokeWidth = 4f
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.RED
        strokeWidth = 4f
    }

    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0

    private var editable: Boolean = true

    private var p = arrayOf(
        PointF(0.1f, 0.1f),
        PointF(0.9f, 0.1f),
        PointF(0.9f, 0.9f),
        PointF(0.1f, 0.9f)
    )

    private var normalizedContour: List<PointF>? = null

    private var activeHandle: Int? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var draggingWhole = false

    private val handleRadiusPx = 22f
    private val handleHitSlopPx = 60f

    private val reusedPath = Path()
    private val contourPath = Path()

    fun setSourceSize(width: Int, height: Int) {
        sourceWidth = width
        sourceHeight = height
        invalidate()
    }

    fun setEditable(editable: Boolean) {
        this.editable = editable
        invalidate()
    }

    fun setNormalizedQuad(pointsClockwise: List<PointF>) {
        if (pointsClockwise.size != 4) return
        for (i in 0 until 4) {
            p[i].x = pointsClockwise[i].x.coerceIn(0f, 1f)
            p[i].y = pointsClockwise[i].y.coerceIn(0f, 1f)
        }
        invalidate()
    }

    fun setNormalizedContour(contour: List<PointF>?) {
        normalizedContour = contour
        invalidate()
    }

    fun getNormalizedQuad(): List<PointF> {
        return p.map { PointF(it.x, it.y) }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sourceWidth <= 0 || sourceHeight <= 0) return

        normalizedContour?.let { contour ->
            if (contour.isNotEmpty()) {
                contourPath.reset()
                val first = mapSourceNormToView(contour[0].x, contour[0].y)
                contourPath.moveTo(first.x, first.y)
                for (i in 1 until contour.size) {
                    val pt = mapSourceNormToView(contour[i].x, contour[i].y)
                    contourPath.lineTo(pt.x, pt.y)
                }
                contourPath.close()
                canvas.drawPath(contourPath, contourPaint)
            }
        }

        val mapped = p.map { mapSourceNormToView(it.x, it.y) }
        reusedPath.apply {
            reset()
            moveTo(mapped[0].x, mapped[0].y)
            lineTo(mapped[1].x, mapped[1].y)
            lineTo(mapped[2].x, mapped[2].y)
            lineTo(mapped[3].x, mapped[3].y)
            close()
        }

        canvas.drawPath(reusedPath, fillPaint)
        canvas.drawPath(reusedPath, strokePaint)

        if (editable) {
            for (pt in mapped) {
                canvas.drawCircle(pt.x, pt.y, handleRadiusPx, handlePaint)
                canvas.drawCircle(pt.x, pt.y, handleRadiusPx, handleStrokePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editable) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastTouchX = event.x
                lastTouchY = event.y

                val handle = findHandleAt(event.x, event.y)
                if (handle != null) {
                    activeHandle = handle
                    draggingWhole = false
                    return true
                }

                if (isInsideQuad(event.x, event.y)) {
                    activeHandle = null
                    draggingWhole = true
                    return true
                }

                return false
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y

                val mapped = p.map { mapSourceNormToView(it.x, it.y) }

                activeHandle?.let { idx ->
                    val newX = mapped[idx].x + dx
                    val newY = mapped[idx].y + dy
                    val src = mapViewToSourceNorm(newX, newY)
                    p[idx].x = src.x
                    p[idx].y = src.y
                    invalidate()
                    return true
                }

                if (draggingWhole) {
                    val srcDelta = mapViewDeltaToSourceNormDelta(dx, dy)
                    for (i in 0 until 4) {
                        p[i].x = (p[i].x + srcDelta.x).coerceIn(0f, 1f)
                        p[i].y = (p[i].y + srcDelta.y).coerceIn(0f, 1f)
                    }
                    invalidate()
                    return true
                }

                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeHandle != null || draggingWhole) {
                    onQuadChangedListener?.onQuadChanged(getNormalizedQuad())
                }
                activeHandle = null
                draggingWhole = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun findHandleAt(x: Float, y: Float): Int? {
        val mapped = p.map { mapSourceNormToView(it.x, it.y) }
        for (i in 0 until 4) {
            val d = hypot((mapped[i].x - x).toDouble(), (mapped[i].y - y).toDouble()).toFloat()
            if (d <= handleHitSlopPx) return i
        }
        return null
    }

    private fun isInsideQuad(x: Float, y: Float): Boolean {
        val mapped = p.map { mapSourceNormToView(it.x, it.y) }
        val minX = mapped.minOf { it.x }
        val maxX = mapped.maxOf { it.x }
        val minY = mapped.minOf { it.y }
        val maxY = mapped.maxOf { it.y }
        return x in minX..maxX && y in minY..maxY
    }

    private fun mapSourceNormToView(nx: Float, ny: Float): PointF {
        val (scale, dx, dy) = computeFitCenterTransform()
        val sx = nx * max(1, sourceWidth)
        val sy = ny * max(1, sourceHeight)
        return PointF(dx + sx * scale, dy + sy * scale)
    }

    private fun mapViewToSourceNorm(x: Float, y: Float): PointF {
        val (scale, dx, dy) = computeFitCenterTransform()
        val srcX = ((x - dx) / scale).coerceIn(0f, max(1, sourceWidth).toFloat())
        val srcY = ((y - dy) / scale).coerceIn(0f, max(1, sourceHeight).toFloat())
        return PointF(srcX / max(1, sourceWidth), srcY / max(1, sourceHeight))
    }

    private fun mapViewDeltaToSourceNormDelta(dx: Float, dy: Float): PointF {
        val (scale, _, _) = computeFitCenterTransform()
        if (scale == 0f) return PointF(0f, 0f)
        val sx = dx / scale
        val sy = dy / scale
        return PointF(sx / max(1, sourceWidth), sy / max(1, sourceHeight))
    }

    private fun computeFitCenterTransform(): Triple<Float, Float, Float> {
        val vw = width.toFloat()
        val vh = height.toFloat()
        val sw = max(1, sourceWidth).toFloat()
        val sh = max(1, sourceHeight).toFloat()

        if (vw <= 0f || vh <= 0f) return Triple(1f, 0f, 0f)

        val scale = min(vw / sw, vh / sh)
        val drawnW = sw * scale
        val drawnH = sh * scale
        val dx = (vw - drawnW) / 2f
        val dy = (vh - drawnH) / 2f
        return Triple(scale, dx, dy)
    }
}
