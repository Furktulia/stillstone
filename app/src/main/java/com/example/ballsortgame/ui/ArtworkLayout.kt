package com.example.ballsortgame.ui

import kotlin.math.roundToInt

internal data class ArtworkRect(val x: Int, val y: Int, val width: Int, val height: Int)
internal data class ArtworkSlice(val source: ArtworkRect, val destination: ArtworkRect)

/** A single scale for both axes; a badge is never expanded to match a button's shape. */
internal fun fittedArtwork(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): ArtworkRect {
    require(sourceWidth > 0 && sourceHeight > 0 && width > 0 && height > 0)
    val scale = minOf(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
    val w = (sourceWidth * scale).roundToInt().coerceIn(1, width)
    val h = (sourceHeight * scale).roundToInt().coerceIn(1, height)
    return ArtworkRect((width - w) / 2, (height - h) / 2, w, h)
}

/**
 * Eight connected perimeter pieces. Corners keep the width-derived scale; only the
 * middle jambs adapt to the available height. The empty opening is never painted.
 */
internal fun portraitWindowSlices(sourceWidth: Int, sourceHeight: Int, width: Int, height: Int): List<ArtworkSlice> {
    require(sourceWidth >= 20 && sourceHeight >= 20 && width >= 20 && height >= 20)
    val side = (sourceWidth * .12f).roundToInt()
    val top = (sourceHeight * .14f).roundToInt()
    val bottom = (sourceHeight * .28f).roundToInt()
    val scale = minOf(width.toFloat() / sourceWidth, (height - 1f) / (top + bottom))
    val sideTo = (side * scale).roundToInt().coerceIn(1, (width - 1) / 2)
    val topTo = (top * scale).roundToInt().coerceAtLeast(1)
    val bottomTo = (bottom * scale).roundToInt().coerceIn(1, height - topTo - 1)
    val sx = listOf(0, side, sourceWidth - side, sourceWidth)
    val sy = listOf(0, top, sourceHeight - bottom, sourceHeight)
    val dx = listOf(0, sideTo, width - sideTo, width)
    val dy = listOf(0, topTo, height - bottomTo, height)
    return buildList {
        for (row in 0..2) for (column in 0..2) {
            if (row == 1 && column == 1) continue
            add(ArtworkSlice(
                ArtworkRect(sx[column], sy[row], sx[column + 1] - sx[column], sy[row + 1] - sy[row]),
                ArtworkRect(dx[column], dy[row], dx[column + 1] - dx[column], dy[row + 1] - dy[row])))
        }
    }
}
