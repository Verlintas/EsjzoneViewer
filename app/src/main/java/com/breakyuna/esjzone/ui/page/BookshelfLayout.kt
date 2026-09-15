package com.breakyuna.esjzone.ui.page

import kotlin.math.floor

/** Matches adaptive cells with a minimum column count of 3 for standard phone layouts. */
internal fun bookshelfColumnCount(
    availableWidth: Float,
    gap: Float,
    minColumns: Int = 3
): Int {
    if (!availableWidth.isFinite() || availableWidth <= 0f) return minColumns
    return floor((availableWidth + gap) / (96f + gap)).toInt().coerceAtLeast(minColumns)
}
