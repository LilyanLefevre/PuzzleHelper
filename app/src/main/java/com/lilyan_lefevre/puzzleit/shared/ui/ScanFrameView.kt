package com.lilyan_lefevre.puzzleit.shared.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.lilyan_lefevre.puzzleit.R
import kotlin.math.cos

/** Dimmed viewfinder with a breathing square cut-out and corner brackets: where to put the piece. */
class ScanFrameView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private val dp = resources.displayMetrics.density
    private val accent = ContextCompat.getColor(ctx, R.color.puzzle_primary)
    private val scrim = Paint().apply { color = 0xCC0B0E13.toInt() }
    private val bracket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent; style = Paint.Style.STROKE; strokeWidth = 4 * dp; strokeCap = Paint.Cap.ROUND
    }
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2200; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val breathe = 0.5f - 0.5f * cos(t * 2 * Math.PI).toFloat()
        val side = minOf(width, height) * (0.62f + 0.02f * breathe)
        val cx = width / 2f; val cy = height * 0.44f
        val r = RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2)
        val rad = 28 * dp
        val hole = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addRoundRect(r, rad, rad, Path.Direction.CW)
        }
        c.drawPath(hole, scrim)

        val len = 36 * dp
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val p = Path().apply { moveTo(x, y + dy * len); lineTo(x, y + dy * rad * 0.2f); quadTo(x, y, x + dx * rad * 0.2f, y); lineTo(x + dx * len, y) }
            c.drawPath(p, bracket)
        }
        bracket.alpha = (200 + 55 * breathe).toInt()
        corner(r.left, r.top, 1f, 1f); corner(r.right, r.top, -1f, 1f)
        corner(r.left, r.bottom, 1f, -1f); corner(r.right, r.bottom, -1f, -1f)
    }
}
