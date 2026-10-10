package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.lilyan_lefevre.puzzleit.R
import com.lilyan_lefevre.puzzleit.feature.recognition.Candidate
import com.lilyan_lefevre.puzzleit.feature.recognition.Grid
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * The box around a lead with the piece blinking over it, at the box's scale and turned the way the lead says:
 * if the lead is right, the picture continues across the piece. A tap freezes or resumes the blinking.
 */
class LeadComparisonView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var box: Bitmap? = null
    private var piece: PieceSprite? = null
    private var cw = 1f
    private var ch = 1f
    private var leadX = 0f
    private var leadY = 0f
    private var turn = 0f
    private var phase = 0f
    private var frozen = false

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(ctx, R.color.puzzle_primary); style = Paint.Style.STROKE; strokeWidth = 2 * dp
    }
    private val clock = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; if (!frozen) invalidate() }
    }

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, 12 * dp)
        }
        setOnClickListener { frozen = !frozen; invalidate() }
    }

    /** [piece] = the cut-out piece (transparent outside) as photographed; the lead says how far to turn it. */
    fun bind(box: Bitmap, grid: Grid, lead: Candidate, piece: Bitmap) {
        this.box = box; this.piece = PieceSprite(piece)
        cw = box.width / grid.cols.toFloat(); ch = box.height / grid.rows.toFloat()
        leadX = lead.col * cw; leadY = lead.row * ch
        turn = lead.rotationDeg.toFloat()
        contentDescription = context.getString(R.string.compare_description)
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); clock.start() }
    override fun onDetachedFromWindow() { clock.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val b = box ?: return
        val p = piece ?: return
        c.drawColor(ContextCompat.getColor(context, R.color.puzzle_background))
        // A window 2.4 cells tall, centred on the lead, scaled to the view.
        val k = height / (ch * 2.4f)
        c.save()
        c.translate(width / 2f, height / 2f); c.scale(k, k); c.translate(-leadX, -leadY)
        c.drawBitmap(b, 0f, 0f, paint)
        c.drawRect(leadX - cw / 2, leadY - ch / 2, leadX + cw / 2, leadY + ch / 2, ring.apply { strokeWidth = 2 * dp / k })
        val alpha = if (frozen) 0.6f else 0.5f - 0.5f * cos(2 * PI.toFloat() * phase)
        paint.alpha = (alpha * 240).toInt()
        c.translate(leadX, leadY); c.rotate(turn); val s = sqrt(cw * ch) / p.side; c.scale(s, s); c.translate(-p.cx, -p.cy)
        c.drawBitmap(p.bitmap, 0f, 0f, paint)
        paint.alpha = 255
        c.restore()
    }
}
