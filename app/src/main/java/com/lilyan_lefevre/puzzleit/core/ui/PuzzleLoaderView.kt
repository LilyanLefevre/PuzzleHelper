package com.lilyan_lefevre.puzzleit.core.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.lilyan_lefevre.puzzleit.R
import kotlin.math.PI
import kotlin.math.sin

/** Four tiles that take turns lifting up: the app's "thinking" indicator. */
class PuzzleLoaderView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var t = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colors = intArrayOf(R.color.puzzle_primary, R.color.puzzle_secondary, R.color.puzzle_secondary, R.color.puzzle_primary)
        .map { ContextCompat.getColor(ctx, it) }
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val side = minOf(width, height) / 2f
        val gap = side * 0.08f
        for (i in 0..3) {
            val col = i % 2; val row = i / 2
            val order = intArrayOf(0, 1, 3, 2)[i]                      // clockwise turn order
            val p = ((t - order * 0.25f) % 1f + 1f) % 1f
            val lift = if (p < 0.25f) sin(p / 0.25f * PI).toFloat() else 0f
            val sc = 1f + 0.12f * lift
            val cx = col * side + side / 2 + (width - 2 * side) / 2
            val cy = row * side + side / 2 + (height - 2 * side) / 2 - lift * side * 0.12f
            val half = (side - gap) / 2 * sc
            paint.color = colors[i]; paint.alpha = (150 + 105 * lift).toInt()
            c.drawRoundRect(RectF(cx - half, cy - half, cx + half, cy + half), half * 0.38f, half * 0.38f, paint)
        }
    }
}
