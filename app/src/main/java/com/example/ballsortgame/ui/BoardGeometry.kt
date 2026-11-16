package com.example.ballsortgame.ui

import kotlin.math.floor

data class TubeGeometry(
    val centerX: Float, val top: Float, val bottom: Float, val width: Float, val slot: Float,
    val hitLeft: Float, val hitTop: Float, val hitWidth: Float, val hitHeight: Float,
) {
    fun pieceY(index: Int, density: Float): Float = bottom - 6f * density - slot * (index + 0.5f)
}

/**
 * Every tube in one row, in the same columns as the target arrangement shown above the
 * board, so a stone's place in the pattern can be read straight down.
 */
fun rowGeometry(capacities: List<Int>, width: Float, height: Float, density: Float): List<TubeGeometry> {
    require(capacities.isNotEmpty() && width > 0 && height > 0 && density > 0)
    val padding = 12f * density
    val cell = (width - padding * 2) / capacities.size
    val tallest = capacities.max()
    val slot = minOf(44f * density, (cell - 12f * density) / 1.6f, (height - 76f * density) / tallest).coerceAtLeast(1f)
    // Sits in the upper part of its area, close under the target it is compared with.
    val bottom = minOf(height - 30f * density, height * .30f + (tallest * slot + 12f * density) / 2 + slot)
    return capacities.mapIndexed { index, capacity ->
        val center = padding + (index + 0.5f) * cell
        val top = bottom - capacity * slot - 12f * density
        val hitTop = (top - 2.2f * slot).coerceAtLeast(0f)
        TubeGeometry(center, top, bottom, slot * 1.36f, slot, center - cell / 2 + 1f * density, hitTop,
            (cell - 2f * density).coerceAtLeast(1f), (bottom + 26f * density - hitTop).coerceAtMost(height - hitTop))
    }
}

/**
 * Two shores with the shared tube between them: the left shore's tubes in a column on the
 * left, the others on the right, the shared tube in the middle. Same stone size throughout.
 */
fun connectedGeometry(capacities: List<Int>, left: Set<Int>, shared: Int, width: Float, height: Float, density: Float): List<TubeGeometry> {
    require(shared in capacities.indices && left.all { it in capacities.indices })
    val leftTubes = capacities.indices.filter { it in left }
    val rightTubes = capacities.indices.filter { it !in left && it != shared }
    val rows = maxOf(leftTubes.size, rightTubes.size, 1)
    val tallest = capacities.max()
    // Each tube needs a stone's height of clear air above it for stones to travel through, and room for its label below.
    val slot = minOf(38f * density, width / 9f, (height / rows - 36f * density) / (tallest + 1.2f)).coerceAtLeast(1f)
    val result = arrayOfNulls<TubeGeometry>(capacities.size)
    fun place(tube: Int, x: Float, y: Float) {
        val tubeHeight = capacities[tube] * slot + 12f * density
        val center = width * x
        val bottom = (height * y + (tubeHeight + slot * 1.2f) / 2 - 9f * density).coerceAtMost(height - 24f * density)
        val hitTop = (bottom - tubeHeight - slot * .35f).coerceAtLeast(0f)
        result[tube] = TubeGeometry(center, bottom - tubeHeight, bottom, slot * 1.36f, slot,
            center - width * .13f, hitTop, width * .26f, (tubeHeight + slot * .85f).coerceAtMost(height - hitTop))
    }
    leftTubes.forEachIndexed { index, tube -> place(tube, .20f, (index + 0.5f) / leftTubes.size) }
    rightTubes.forEachIndexed { index, tube -> place(tube, .80f, (index + 0.5f) / rightTubes.size) }
    place(shared, .50f, .50f)
    return result.map { requireNotNull(it) }
}

/**
 * Fit a finite board into the available area; the last row is centered. All units are pixels.
 *
 * On a [large] screen (unfolded foldable, tablet) the number of columns is whichever lets the
 * stones grow largest, and they may grow a little past the phone size. Phones (large = false)
 * keep the original choice: three columns, or up to six on a very wide area.
 */
fun boardGeometry(capacities: List<Int>, width: Float, height: Float, density: Float, large: Boolean = false): List<TubeGeometry> {
    require(capacities.isNotEmpty() && capacities.all { it > 0 })
    require(width > 0 && height > 0 && density > 0)
    val horizontalPadding = 12f * density
    val fitting = floor((width - horizontalPadding * 2) / (56f * density)).toInt().coerceAtLeast(1)
    val largest = if (large) 52f * density else 44f * density
    // The space above the board and between rows is travel room for stones, so it only
    // needs to scale with them. Full-size boards keep the original 52/40 dp; on a short
    // board the stones would otherwise shrink to pay for corridors far wider than they are.
    fun verticalPadding(slot: Float) = (slot * 1.6f).coerceAtMost(52f * density)
    fun gap(slot: Float) = (slot * 1.25f).coerceIn(8f * density, 40f * density)
    fun slotFor(columns: Int): Float {
        val rows = (capacities.size + columns - 1) / columns
        val cellWidth = (width - horizontalPadding * 2).coerceAtLeast(1f) / columns
        fun needed(slot: Float) = verticalPadding(slot) * 2 + gap(slot) * (rows - 1) +
            rows * (capacities.max() * slot + 28f * density)
        var slot = minOf(largest, (cellWidth - 16f * density) / 2.5f).coerceAtLeast(1f)
        if (needed(slot) > height) {
            var low = 1f
            repeat(24) {
                val middle = (low + slot) / 2
                if (needed(middle) > height) slot = middle else low = middle
            }
            slot = low
        }
        return slot
    }
    val columns = if (large) {
        // Fewest columns among those that give (nearly) the biggest stones: fewer, fuller rows read calmer.
        val options = (minOf(3, capacities.size, fitting)..minOf(6, capacities.size, fitting)).associateWith(::slotFor)
        val best = options.values.max()
        options.entries.first { it.value >= best * .97f }.key
    } else {
        val desired = if (width > height * 1.45f) minOf(6, capacities.size) else minOf(3, capacities.size)
        minOf(desired, fitting)
    }
    val rows = (capacities.size + columns - 1) / columns
    val cellWidth = (width - horizontalPadding * 2).coerceAtLeast(1f) / columns
    val slot = slotFor(columns)
    val verticalPadding = verticalPadding(slot)
    val gap = gap(slot)
    // Tubes rest at the bottom of their rows on purpose: on a tall phone the lower half of the
    // screen is where a thumb reaches (creator, 2026-11-13). Do not centre the board.
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
