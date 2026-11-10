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
    val verticalPadding = 52f * density
    val gap = 40f * density
    val desired = if (width > height * 1.45f) minOf(6, capacities.size) else minOf(3, capacities.size)
    val columns = minOf(desired, floor((width - horizontalPadding * 2) / (56f * density)).toInt().coerceAtLeast(1))
    val rows = (capacities.size + columns - 1) / columns
    val cellWidth = (width - horizontalPadding * 2).coerceAtLeast(1f) / columns
    val cellHeight = ((height - verticalPadding * 2 - gap * (rows - 1)) / rows).coerceAtLeast(1f)
    val slot = minOf(
        44f * density,
        (cellWidth - 16f * density) / 2.5f,
        (cellHeight - 28f * density) / capacities.max(),
    ).coerceAtLeast(1f)
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
