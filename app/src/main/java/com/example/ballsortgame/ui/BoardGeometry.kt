package com.example.ballsortgame.ui

import kotlin.math.floor

data class TubeGeometry(
    val centerX: Float, val top: Float, val bottom: Float, val width: Float, val slot: Float,
    val hitLeft: Float, val hitTop: Float, val hitWidth: Float, val hitHeight: Float,
) {
    fun pieceY(index: Int, density: Float): Float = bottom - 6f * density - slot * (index + 0.5f)
}

/** Fit a finite board into the available area; the last row is centered. All units are pixels. */
fun boardGeometry(capacities: List<Int>, width: Float, height: Float, density: Float): List<TubeGeometry> {
    require(capacities.isNotEmpty() && capacities.all { it > 0 })
    require(width > 0 && height > 0 && density > 0)
    val horizontalPadding = 12f * density
    val desired = if (width > height * 1.45f) minOf(6, capacities.size) else minOf(3, capacities.size)
    val columns = minOf(desired, floor((width - horizontalPadding * 2) / (56f * density)).toInt().coerceAtLeast(1))
    val rows = (capacities.size + columns - 1) / columns
    val cellWidth = (width - horizontalPadding * 2).coerceAtLeast(1f) / columns
    // The space above the board and between rows is travel room for stones, so it only
    // needs to scale with them. Full-size boards keep the original 52/40 dp; on a short
    // board the stones would otherwise shrink to pay for corridors far wider than they are.
    fun verticalPadding(slot: Float) = (slot * 1.6f).coerceAtMost(52f * density)
    fun gap(slot: Float) = (slot * 1.25f).coerceIn(8f * density, 40f * density)
    fun needed(slot: Float) = verticalPadding(slot) * 2 + gap(slot) * (rows - 1) +
        rows * (capacities.max() * slot + 28f * density)
    var slot = minOf(44f * density, (cellWidth - 16f * density) / 2.5f).coerceAtLeast(1f)
    if (needed(slot) > height) {
        var low = 1f
        repeat(24) {
            val middle = (low + slot) / 2
            if (needed(middle) > height) slot = middle else low = middle
        }
        slot = low
    }
    val verticalPadding = verticalPadding(slot)
    val gap = gap(slot)
    val cellHeight = ((height - verticalPadding * 2 - gap * (rows - 1)) / rows).coerceAtLeast(1f)
    return capacities.mapIndexed { index, capacity ->
        val row = index / columns
        val column = index % columns
        val countInRow = minOf(columns, capacities.size - row * columns)
        val centerX = (width - countInRow * cellWidth) / 2 + (column + 0.5f) * cellWidth
        val rowTop = verticalPadding + row * (cellHeight + gap)
        val bottom = rowTop + cellHeight - 16f * density
        val hitTop = (rowTop - 8f * density).coerceAtLeast(0f)
        TubeGeometry(
            centerX, bottom - capacity * slot - 12f * density, bottom, slot * 1.36f, slot,
            centerX - cellWidth / 2 + 2f * density, hitTop,
            (cellWidth - 4f * density).coerceAtLeast(1f),
            (rowTop + cellHeight - hitTop).coerceAtLeast(1f),
        )
    }
}
