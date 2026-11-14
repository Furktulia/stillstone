package com.example.ballsortgame.ui

import com.example.ballsortgame.engine.BundledLevelPacks
import org.junit.Assert.*
import org.junit.Test

/** A level with more colours than the palette would show two different colours as the same stone. */
class PieceColorsTest {
    @Test fun everyBundledLevelUsesOnlyColoursThePaletteCanShow() {
        assertEquals(PieceColors.size, PieceNames.size)
        assertEquals("every colour must look different", PieceColors.size, PieceColors.distinct().size)
        for (level in BundledLevelPacks.playtest().levels)
            for (colour in level.board.tubes.flatMap { it.balls } + level.rules.target?.tubes.orEmpty().flatMap { it.balls })
                assertTrue("${level.id}: colour index $colour has no stone", colour in PieceColors.indices)
    }
}