package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.feature.recognition.Candidate
import com.lilyan_lefevre.puzzleit.feature.recognition.Grid
import kotlin.math.max
import kotlin.math.min

/**
 * The box image, pannable / zoomable, with an animated spotlight on the suggested spot.
 * Everything is drawn here (no child views) so camera moves and highlight stay perfectly in sync.
 */
class PuzzleMapView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var bmp: Bitmap? = null
    private var grid = Grid(1, 1)
    private var s = 1f; private var tx = 0f; private var ty = 0f
    private var fitS = 1f
    private var insetTop = 0f; private var insetBottom = 0f

    private var region: RectF? = null          // selected lead's cell, image space
    private var leadCells: List<RectF> = emptyList()           // every lead's cell, image space
    private var selected = 0

    /** Called when a lead's square is tapped on the map. */
    var onLeadTapped: ((Int) -> Unit)? = null
    private var spot = 0f                      // 0..1 spotlight intensity
    private var gridAlpha = 0f
    private var scanning = false
    private var phase = 0f                     // 0..1 looping clock

    private val dp = resources.displayMetrics.density
    private val accent = ContextCompat.getColor(ctx, R.color.puzzle_primary)
    private val bg = ContextCompat.getColor(ctx, R.color.puzzle_background)

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE }
    private val scrimPaint = Paint().apply { color = bg }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; textAlign = Paint.Align.CENTER; isFakeBoldText = true; textSize = 11 * dp }
    private val scanPaint = Paint()

    private val clock = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    private var camAnim: ValueAnimator? = null
    private var spotAnim: ValueAnimator? = null
    private var gridAnim: ValueAnimator? = null

    // ------------------------------------------------------------------ API

    fun setImage(b: Bitmap, g: Grid) {
        bmp = b; grid = g; region = null; spot = 0f; gridAlpha = 0f
        resetView(false)
    }

    /** Area left visible between the top bar and the sheet; the camera re-frames when it changes. */
    fun setViewportInsets(top: Int, bottom: Int) {
        if (top.toFloat() == insetTop && bottom.toFloat() == insetBottom) return
        insetTop = top.toFloat(); insetBottom = bottom.toFloat()
        computeFit()
        region?.let(::focusOn) ?: resetView(false)
    }

    fun setScanning(on: Boolean) { scanning = on; updateClock(); invalidate() }

    /**
     * Shows each lead as a square the size of one piece, numbered; [selected] gets the spotlight and the camera.
     * Never a bigger area: a piece is one cell, so is every suggestion.
     */
    fun showLeads(leads: List<Candidate>, selected: Int) {
        val b = bmp ?: return
        val cw = b.width / grid.cols.toFloat(); val ch = b.height / grid.rows.toFloat()
        leadCells = leads.map { c ->
            val cx = (c.col * cw).coerceIn(cw / 2, b.width - cw / 2); val cy = (c.row * ch).coerceIn(ch / 2, b.height - ch / 2)
            RectF(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2)
        }
        this.selected = selected.coerceIn(0, leadCells.lastIndex)
        region = leadCells[this.selected]
        animateSpot(1f)
        animateGrid(0.18f)
        focusOn(region!!)
        updateClock()
    }

    fun clearMatch() {
        region = null; leadCells = emptyList()
        animateSpot(0f); animateGrid(0f); resetView(true); updateClock()
    }

    // --------------------------------------------------------------- camera

    private fun vw() = width.toFloat()
    private fun vh() = (height - insetTop - insetBottom).coerceAtLeast(1f)

    private fun computeFit() {
        val b = bmp ?: return
        fitS = min(vw() / b.width, vh() / b.height)
    }

    fun resetView(animated: Boolean) {
        val b = bmp ?: return
        if (width == 0) return
        computeFit()
        val ts = fitS
        animateCamera(ts, (vw() - b.width * ts) / 2, insetTop + (vh() - b.height * ts) / 2, animated)
    }

    private fun focusOn(r: RectF) {
        // The piece's cell takes about a fifth of the view: big enough to see, small enough to keep the surroundings
        // that let you find it on the table.
        val target = (0.2f * min(vw(), vh()) / max(r.width(), r.height())).coerceIn(fitS, fitS * 14f)
        val ts = max(target, fitS)
        animateCamera(ts, vw() / 2 - r.centerX() * ts, insetTop + vh() / 2 - r.centerY() * ts, true)
    }

    private fun animateCamera(ts: Float, tx2: Float, ty2: Float, animated: Boolean) {
        camAnim?.cancel()
        val (cx, cy) = clampT(ts, tx2, ty2)
        if (!animated) { s = ts; tx = cx; ty = cy; invalidate(); return }
        val s0 = s; val x0 = tx; val y0 = ty
        camAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900; interpolator = OvershootInterpolator(0.7f)
            addUpdateListener {
                val t = it.animatedValue as Float
                s = s0 + (ts - s0) * t; tx = x0 + (cx - x0) * t; ty = y0 + (cy - y0) * t; invalidate()
            }
            start()
        }
    }

    private fun clampT(sc: Float, x: Float, y: Float): Pair<Float, Float> {
        val b = bmp ?: return x to y
        val iw = b.width * sc; val ih = b.height * sc
        val nx = if (iw <= vw()) (vw() - iw) / 2 else x.coerceIn(vw() - iw, 0f)
        val ny = if (ih <= vh()) insetTop + (vh() - ih) / 2 else y.coerceIn(insetTop + vh() - ih, insetTop)
        return nx to ny
    }

    private fun animateSpot(to: Float) {
        spotAnim?.cancel()
        spotAnim = ValueAnimator.ofFloat(spot, to).apply {
            duration = 650; interpolator = DecelerateInterpolator()
            addUpdateListener { spot = it.animatedValue as Float; invalidate() }; start()
        }
    }

    private fun animateGrid(to: Float) {
        gridAnim?.cancel()
        gridAnim = ValueAnimator.ofFloat(gridAlpha, to).apply {
            duration = 900
            addUpdateListener { gridAlpha = it.animatedValue as Float; invalidate() }; start()
        }
    }

    private fun updateClock() {
        val need = isAttachedToWindow && (scanning || region != null)
        if (need && !clock.isRunning) clock.start() else if (!need && clock.isRunning) clock.cancel()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateClock() }
    override fun onDetachedFromWindow() { clock.cancel(); camAnim?.cancel(); spotAnim?.cancel(); gridAnim?.cancel(); super.onDetachedFromWindow() }
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { region?.let { computeFit(); focusOn(it) } ?: resetView(false) }

    // -------------------------------------------------------------- drawing

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        c.save(); c.translate(tx, ty); c.scale(s, s)
        c.drawBitmap(b, 0f, 0f, bmpPaint)
        if (gridAlpha > 0.01f) {
            gridPaint.alpha = (gridAlpha * 255).toInt(); gridPaint.strokeWidth = 1.2f * dp / s
            for (i in 1 until grid.cols) { val x = i * b.width / grid.cols.toFloat(); c.drawLine(x, 0f, x, b.height.toFloat(), gridPaint) }
            for (j in 1 until grid.rows) { val y = j * b.height / grid.rows.toFloat(); c.drawLine(0f, y, b.width.toFloat(), y, gridPaint) }
        }
        c.restore()

        region?.let { r ->
            val vr = RectF(r.left * s + tx, r.top * s + ty, r.right * s + tx, r.bottom * s + ty)
            val rad = min(14 * dp, vr.width() / 4)
            val hole = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
                addRoundRect(vr, rad, rad, Path.Direction.CW)
            }
            scrimPaint.alpha = (spot * 0.68f * 255).toInt()
            c.drawPath(hole, scrimPaint)

            stroke.strokeWidth = 7 * dp; stroke.alpha = (spot * 50).toInt(); c.drawRoundRect(vr, rad, rad, stroke)
            stroke.strokeWidth = 2.5f * dp; stroke.alpha = (spot * 255).toInt(); c.drawRoundRect(vr, rad, rad, stroke)

            for (k in 0..1) {
                val p = (phase + k * 0.5f) % 1f
                val e = p * 26 * dp
                stroke.strokeWidth = 2 * dp; stroke.alpha = ((1f - p) * 0.8f * spot * 255).toInt()
                c.drawRoundRect(RectF(vr.left - e, vr.top - e, vr.right + e, vr.bottom + e), rad + e, rad + e, stroke)
            }
            // Every lead: a piece-sized square with its number; the selected one is drawn above in citron.
            // Only inside the visible map, never under the top bar or the sheet.
            c.save(); c.clipRect(0f, insetTop, width.toFloat(), height - insetBottom)
            leadCells.forEachIndexed { i, cell ->
                val lr = RectF(cell.left * s + tx, cell.top * s + ty, cell.right * s + tx, cell.bottom * s + ty)
                val lrad = min(10 * dp, lr.width() / 4)
                if (i != selected) {
                    stroke.color = 0xFFFFFFFF.toInt(); stroke.strokeWidth = 2 * dp; stroke.alpha = (spot * 220).toInt()
                    c.drawRoundRect(lr, lrad, lrad, stroke)
                    stroke.color = accent
                }
                val bx = lr.left; val by = lr.top
                fill.color = if (i == selected) accent else 0xFFFFFFFF.toInt(); fill.alpha = (spot * 255).toInt()
                c.drawCircle(bx, by, 11 * dp, fill)
                text.alpha = (spot * 255).toInt(); c.drawText("${i + 1}", bx, by + 4 * dp, text)
            }
            c.restore()
        }

        if (scanning) {
            val top = ty; val bottom = ty + b.height * s
            val e = (0.5f - 0.5f * kotlin.math.cos(phase * 2 * Math.PI)).toFloat()
            val y = top + (bottom - top) * e
            val band = 70 * dp
            val from = max(top, y - band)
            scanPaint.shader = LinearGradient(0f, y - band, 0f, y, 0x00D6FF45, (accent and 0x00FFFFFF) or 0x66000000, Shader.TileMode.CLAMP)
            c.drawRect(tx, from, tx + b.width * s, y, scanPaint)
            stroke.strokeWidth = 2 * dp; stroke.alpha = 255; c.drawLine(tx, y, tx + b.width * s, y, stroke)
        }
    }

    // ------------------------------------------------------------- gestures

    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val ns = (s * d.scaleFactor).coerceIn(fitS * 0.9f, fitS * 16f)
            val k = ns / s
            val (x, y) = clampT(ns, d.focusX - (d.focusX - tx) * k, d.focusY - (d.focusY - ty) * k)
            s = ns; tx = x; ty = y; invalidate(); return true
        }
    })

    private val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // Tap on a lead's square (with a finger-sized margin) selects it.
            val slop = 16 * dp
            val i = leadCells.indexOfFirst { cell ->
                e.x in cell.left * s + tx - slop..cell.right * s + tx + slop && e.y in cell.top * s + ty - slop..cell.bottom * s + ty + slop
            }
            if (i >= 0) { onLeadTapped?.invoke(i); return true }
            return false
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scaler.isInProgress) return false
            val (x, y) = clampT(s, tx - dx, ty - dy); tx = x; ty = y; invalidate(); return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val ns = if (s > fitS * 1.5f) fitS else fitS * 3f
            val k = ns / s
            animateCamera(ns, e.x - (e.x - tx) * k, e.y - (e.y - ty) * k, true); return true
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { camAnim?.cancel(); parent?.requestDisallowInterceptTouchEvent(true) }
        scaler.onTouchEvent(e); detector.onTouchEvent(e)
        return true
    }
}
