package com.lilyan_lefevre.puzzleit.feature.puzzle

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class GridOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var numRows = 1
    private var numCols = 1
    private var targetRect: RectF? = null
    
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val borderPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    fun setGrid(rows: Int, cols: Int) {
        numRows = rows
        numCols = cols
        invalidate()
    }

    fun setTargetRect(rect: RectF) {
        targetRect = rect
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val rect = targetRect ?: RectF(0f, 0f, width.toFloat(), height.toFloat())
        
        if (numRows <= 0 || numCols <= 0) return

        // Optional: draw a border around the detected image area for debug
        canvas.drawRect(rect, borderPaint)

        val cellWidth = rect.width() / numCols
        val cellHeight = rect.height() / numRows

        // Draw vertical lines
        for (i in 1 until numCols) {
            val x = rect.left + cellWidth * i
            canvas.drawLine(x, rect.top, x, rect.bottom, paint)
        }

        // Draw horizontal lines
        for (i in 1 until numRows) {
            val y = rect.top + cellHeight * i
            canvas.drawLine(rect.left, y, rect.right, y, paint)
        }
    }
}
