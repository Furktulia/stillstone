package com.example.ballsortgame.ui

import androidx.compose.ui.graphics.drawscope.translate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ballsortgame.engine.Board
/**
 * The arrangement to restore, drawn small in the same columns as the board below it, with
 * the board's own tube and stone shapes. A tube that already matches is outlined like a
 * finished tube, so progress can be read without comparing stone by stone.
 */
@Composable
internal fun PatternTarget(board: Board, labels: Boolean, current: Board? = null) {
    val look = LocalWorldLook.current
    // As large as five tubes side by side and a short screen allow: this is what the player works from.
    val slotDp = when { board.tubes.maxOf { it.capacity } >= 5 -> 26; board.tubes.size >= 5 -> 28; else -> 32 }
    // New players took these small tubes for playable ones and tapped them without effect: a tap
    // on the picture says what it is.
    var explain by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "What is this?") { explain = !explain }) {
            board.tubes.forEachIndexed { index, tube ->
                val matched = current != null && tube.balls.isNotEmpty() && current.tubes.getOrNull(index) == tube
                Canvas(Modifier.weight(1f).height((slotDp * tube.capacity + 12).dp).semantics {
                    contentDescription = "Target tube ${index + 1}: " +
                        (if (tube.balls.isEmpty()) "empty" else tube.balls.joinToString(", ", prefix = "bottom to top ") { PieceNames[it] }) +
                        if (matched) ". Matched." else "."
                }) {
                    val slot = slotDp.dp.toPx()
                    val w = slot * 1.5f; val left = (size.width - w) / 2; val right = left + w
                    val bottom = size.height - 1.dp.toPx(); val r = w * .38f
                    val outline = androidx.compose.ui.graphics.Path().apply {
                        moveTo(left, 0f); lineTo(left, bottom - r)
                        quadraticTo(left, bottom, left + r, bottom); lineTo(right - r, bottom)
                        quadraticTo(right, bottom, right, bottom - r); lineTo(right, 0f)
                    }
                    drawPath(outline, look.light.copy(alpha = .03f))
                    drawPath(outline, if (matched) androidx.compose.ui.graphics.Color(0xFF8CA99F) else look.tube.copy(alpha = .8f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    tube.balls.forEachIndexed { place, color ->
                        val d = slot * .82f
                        // Beads rest touching in the tubes below, so they touch here too.
                        val pitch = if (PlayOptions.waterBeads) d else slot
                        val center = Offset(size.width / 2, bottom - 4.dp.toPx() - d / 2 - (slot - d) / 2 - place * pitch)
                        if (PlayOptions.waterBeads) {
                            translate(center.x - d / 2, center.y - d / 2) {
                                drawWaterBead(center, d, PieceColors[color % PieceColors.size], look, 0f)
                            }
                        } else {
                            drawRoundRect(PieceColors[color % PieceColors.size], Offset(center.x - d / 2, center.y - d / 2),
                                androidx.compose.ui.geometry.Size(d, d), androidx.compose.ui.geometry.CornerRadius(d * .46f))
                        }
                        if (labels) {
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign = android.graphics.Paint.Align.CENTER; textSize = d * .62f
                                this.color = android.graphics.Color.rgb(29, 22, 39)
                            }
                            drawContext.canvas.nativeCanvas.drawText((color + 1).toString(), center.x,
                                center.y - (paint.ascent() + paint.descent()) / 2, paint)
                        }
                    }
                }
            }
        }
        if (explain) ExplanationNote("The pattern to make",
            "Move the stones in the big tubes below, one at a time, until they look the same.",
            (slotDp * board.tubes.maxOf { it.capacity } + 12 + 6).dp, Modifier.testTag("pattern_note")) { explain = false }
    }
}
