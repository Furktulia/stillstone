package com.example.ballsortgame.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.ballsortgame.engine.LevelMode
import com.example.ballsortgame.game.PlayableLevel
import com.example.ballsortgame.ui.theme.GameDisplayFont

/** What makes a level differ from plain sorting, in the player's words. */
internal enum class LevelTrait(val title: String, val explanation: String) {
    MIXED("Mixed sizes", "Some tubes are shorter than others. A color may finish in more than one full tube."),
    SEALED("Sealed tubes", "A sealed tube opens once enough other tubes are complete. The stones inside still need sorting."),
    CONNECTED("Connected boards", "Two boards and one tube between them. Stones cross from side to side only through that middle tube."),
    PATTERN("Restore a pattern", "Move one stone at a time, onto any color, until the tubes match the small arrangement at the top."),
    HIDDEN("Hidden colors", "Only the top stone of each tube shows its color. The ones beneath appear as you uncover them."),
    CHALLENGE("Challenge", "Harder than the levels around it. It is optional: skip it and come back whenever you like."),
}

internal fun traitsOf(level: PlayableLevel): List<LevelTrait> = buildList {
    when (level.mode) {
        LevelMode.MIXED -> add(LevelTrait.MIXED)
        LevelMode.SEALED -> add(LevelTrait.SEALED)
        LevelMode.CONNECTED -> add(LevelTrait.CONNECTED)
        LevelMode.PATTERN -> add(LevelTrait.PATTERN)
        LevelMode.CLASSIC -> Unit
    }
    if (level.rules.hidden) add(LevelTrait.HIDDEN)
    if (level.challenge) add(LevelTrait.CHALLENGE)
}

/** A small mark for each trait: on the level stones of the list and in the bubble above the board. */
internal fun DrawScope.drawTraitMark(trait: LevelTrait, center: Offset, size: Float, color: Color) {
    val stroke = Stroke(size * 0.13f)
    val r = size / 2
    when (trait) {
        LevelTrait.MIXED -> { // A tall tube beside a short one.
            drawLine(color, center + Offset(-r * .45f, -r * .85f), center + Offset(-r * .45f, r * .85f), size * .22f)
            drawLine(color, center + Offset(r * .45f, -r * .05f), center + Offset(r * .45f, r * .85f), size * .22f)
        }
        LevelTrait.SEALED -> { // A tube with a cork pressed into it.
            val tube = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - r * .46f, center.y - r * .30f); lineTo(center.x - r * .46f, center.y + r * .42f)
                cubicTo(center.x - r * .46f, center.y + r, center.x + r * .46f, center.y + r, center.x + r * .46f, center.y + r * .42f)
                lineTo(center.x + r * .46f, center.y - r * .30f)
            }
            drawPath(tube, color, style = Stroke(size * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawRoundRect(color, center + Offset(-r * .66f, -r * .98f), Size(r * 1.32f, r * .56f),
                androidx.compose.ui.geometry.CornerRadius(r * .20f))
        }
        LevelTrait.CONNECTED -> { // Two sides joined.
            drawCircle(color, r * .32f, center + Offset(-r * .68f, 0f))
            drawCircle(color, r * .32f, center + Offset(r * .68f, 0f))
            drawLine(color, center + Offset(-r * .4f, 0f), center + Offset(r * .4f, 0f), size * .11f)
        }
        LevelTrait.PATTERN -> { // A small column to rebuild: the same line drawing as the other marks.
            drawRoundRect(color, center + Offset(-r * .50f, -r * 1.02f), Size(r, r * 2.04f),
                androidx.compose.ui.geometry.CornerRadius(r * .5f), style = Stroke(size * 0.10f))
            drawCircle(color, r * .21f, center + Offset(0f, -r * .58f))
            drawCircle(color, r * .17f, center, style = Stroke(size * 0.085f))
            drawCircle(color, r * .21f, center + Offset(0f, r * .58f))
        }
        LevelTrait.CHALLENGE -> { // A peak.
            val peak = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - r * .9f, center.y + r * .7f); lineTo(center.x - r * .15f, center.y - r * .75f)
                lineTo(center.x + r * .25f, center.y - r * .05f); lineTo(center.x + r * .5f, center.y - r * .4f)
                lineTo(center.x + r * .95f, center.y + r * .7f); close()
            }
            drawPath(peak, color, style = Stroke(size * 0.12f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        LevelTrait.HIDDEN -> { // A stone you cannot see yet.
            drawCircle(color, r * .92f, center, style = Stroke(size * 0.10f))
            val ask = androidx.compose.ui.graphics.Path().apply {
                moveTo(center.x - r * .30f, center.y - r * .22f)
                cubicTo(center.x - r * .30f, center.y - r * .66f, center.x + r * .32f, center.y - r * .66f, center.x + r * .32f, center.y - r * .24f)
                cubicTo(center.x + r * .32f, center.y + r * .02f, center.x, center.y, center.x, center.y + r * .24f)
            }
            drawPath(ask, color, style = Stroke(size * 0.10f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawCircle(color, r * .095f, center + Offset(0f, r * .50f))
        }
    }
}

/** Remembers which traits have been explained once, so the first meeting opens the explanation by itself. */
private fun firstMeeting(context: Context, trait: LevelTrait): Boolean {
    val prefs = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    val key = "met_" + trait.name.lowercase()
    if (prefs.getBoolean(key, false)) return false
    prefs.edit().putBoolean(key, true).apply()
    return true
}

/**
 * The level's traits as quiet bubbles. Tapping one opens a small note that grows out of it
 * and closes on the next tap anywhere. A trait the player has never met opens its note once
 * without being asked.
 */
@Composable
internal fun TraitBubbles(level: PlayableLevel, modifier: Modifier = Modifier) {
    val traits = remember(level.id) { traitsOf(level) }
    if (traits.isEmpty()) return
    val context = LocalContext.current.applicationContext
    val look = LocalWorldLook.current
    var open by remember(level.id) { mutableStateOf<LevelTrait?>(null) }
    LaunchedEffect(level.id) { open = traits.firstOrNull { firstMeeting(context, it) } }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (trait in traits) Box {
            val shape = RoundedCornerShape(50)
            Row(Modifier.clip(shape)
                .background(lerp(look.base, look.light, if (open == trait) .16f else .08f))
                .border(1.dp, look.accent.copy(alpha = if (open == trait) .5f else .22f), shape)
                .clickable(role = Role.Button, onClickLabel = "What is ${trait.title}?") { open = if (open == trait) null else trait }
                .testTag("trait_" + trait.name.lowercase())
                .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Canvas(Modifier.size(13.dp)) { drawTraitMark(trait, Offset(size.width / 2, size.height / 2), size.minDimension, look.light.copy(alpha = .8f)) }
                Text(trait.title, style = MaterialTheme.typography.labelMedium, color = look.light.copy(alpha = .86f), maxLines = 1)
            }
            if (open == trait) Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, with(androidx.compose.ui.platform.LocalDensity.current) { 38.dp.roundToPx() }),
                onDismissRequest = { open = null }, properties = PopupProperties(focusable = true)) {
                val card = RoundedCornerShape(18.dp)
                Column(Modifier.widthIn(max = 260.dp).clip(card)
                    .background(lerp(look.base, look.light, .17f))
                    .border(1.dp, look.accent.copy(alpha = .32f), card)
                    .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { open = null }
                    .testTag("trait_note")
                    .padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(trait.title, fontFamily = GameDisplayFont, style = MaterialTheme.typography.titleMedium, color = look.light)
                    Spacer(Modifier.height(4.dp))
                    Text(trait.explanation, style = MaterialTheme.typography.bodySmall, color = lerp(look.light, look.tube, .2f))
                }
            }
        }
    }
}
