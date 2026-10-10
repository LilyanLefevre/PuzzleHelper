package com.lilyan_lefevre.puzzleit.feature.puzzle

import androidx.annotation.StringRes
import com.lilyan_lefevre.puzzleit.R

/** The name of the ninth of the box that the point ([x], [y]), as fractions of its width and height, falls in. */
@StringRes
fun zoneRes(x: Float, y: Float): Int {
    val zones = arrayOf(
        intArrayOf(R.string.zone_top_left, R.string.zone_top, R.string.zone_top_right),
        intArrayOf(R.string.zone_left, R.string.zone_centre, R.string.zone_right),
        intArrayOf(R.string.zone_bottom_left, R.string.zone_bottom, R.string.zone_bottom_right),
    )
    return zones[(y.coerceIn(0f, 0.999f) * 3).toInt()][(x.coerceIn(0f, 0.999f) * 3).toInt()]
}
