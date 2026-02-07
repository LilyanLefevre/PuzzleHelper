package com.lilyan_lefevre.puzzleit.shared.util

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Utility class to calculate the best grid configuration (rows x cols)
 * for a puzzle based on total pieces and image aspect ratio.
 */
object GridProcessor {

    data class GridConfig(val rows: Int, val cols: Int)

    /**
     * Finds the best grid configuration by trying divisors of [totalPieces]
     * or finding the closest pair that matches the image [aspectRatio].
     */
    fun calculateBestGrid(totalPieces: Int, imageWidth: Int, imageHeight: Int): GridConfig {
        if (totalPieces <= 0 || imageWidth <= 0 || imageHeight <= 0) {
            return GridConfig(1, 1)
        }

        val aspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
        
        // Strategy: find rows/cols such that (cols/rows) is closest to aspectRatio
        // and rows * cols is closest to totalPieces.
        
        var bestConfig = GridConfig(1, totalPieces)
        var minDiff = Float.MAX_VALUE

        // We search for 'rows' such that rows is around sqrt(totalPieces / aspectRatio)
        val idealRows = sqrt(totalPieces / aspectRatio)
        
        // Check divisors around the ideal number of rows
        val range = (idealRows.toInt() - 10).coerceAtLeast(1)..(idealRows.toInt() + 10).coerceAtMost(totalPieces)
        
        for (rows in range) {
            val cols = (totalPieces.toFloat() / rows).roundToInt()
            if (cols <= 0) continue
            
            val currentPieces = rows * cols
            val currentRatio = cols.toFloat() / rows.toFloat()
            
            // Score based on how close we are to the target ratio and piece count
            // We prioritize piece count accuracy but allow slight variations
            val ratioDiff = abs(currentRatio - aspectRatio)
            val pieceCountDiff = abs(currentPieces - totalPieces).toFloat() / totalPieces
            
            val score = ratioDiff + pieceCountDiff * 5 // Weighting piece count diff
            
            if (score < minDiff) {
                minDiff = score
                bestConfig = GridConfig(rows, cols)
            }
        }

        return bestConfig
    }

    /**
     * Calculates the bounding box of a virtual piece at (row, col)
     * coordinates are normalized (0.0 to 1.0)
     */
    fun getVirtualPieceBounds(row: Int, col: Int, config: GridConfig): RectF {
        val width = 1.0f / config.cols
        val height = 1.0f / config.rows
        return RectF(
            col * width,
            row * height,
            (col + 1) * width,
            (row + 1) * height
        )
    }
}
