package com.example.ballsortgame

import com.example.ballsortgame.ui.*
import com.example.ballsortgame.game.LevelCatalog
import com.example.ballsortgame.engine.PuzzleRules
import org.junit.Assert.*
import org.junit.Test

class GroupRouteTest {
    private fun check(route: GroupRoute, label: String) {
        for (sample in 0..600) {
            val points = route.at(sample / 600f)
            for (a in points.indices) for (b in a + 1 until points.size) {
                assertTrue("$label overlap at $sample: $a / $b",
                    points[a].distance(points[b]) >= route.slot * 0.89f)
            }
        }
    }
    @Test fun fourAndFiveBallGroupsKeepTheirSpacingAroundEveryTurn() {
        for ((w, h) in listOf(360f to 530f, 360f to 300f, 760f to 280f)) {
            val layout = boardGeometry(List(6) { 5 }, w, h, 1f)
            for (count in 4..5) for (a in layout.indices) for (b in layout.indices) {
                if (a == b) continue
                val spine = requireNotNull(stoneRoute(layout, a, b, 5 - count, 0, w, h, 1f))
                val group = GroupRoute(spine, count, layout[a].slot)
                val initial = group.at(0f); val end = group.at(1f)
                for (index in 0 until count) {
                    assertEquals(layout[a].pieceY(4 - index, 1f), initial[index].y, 0.05f)
                    assertEquals(layout[b].pieceY(index, 1f), end[index].y, 0.05f)
                }
                check(group, "${w}x$h $count $a>$b")
            }
        }
    }
    @Test fun thirdWorldFirstLevelSolutionAndLegalLargeTransfersHaveNoSelfIntersection() {
        val level = LevelCatalog.levels.first { it.worldId == "five" }
        var board = level.initial
        val layout = boardGeometry(board.tubes.map { it.capacity }, 412f, 620f, 1f)
        var covered = 0
        for (move in level.solution) {
            for (candidate in PuzzleRules.legalMoves(board)) {
                val result = requireNotNull(PuzzleRules.transfer(board, candidate))
                if (result.count < 4) continue
                val spine = requireNotNull(stoneRoute(layout, candidate.source, candidate.destination,
                    board.tubes[candidate.source].balls.size - result.count,
                    board.tubes[candidate.destination].balls.size, 412f, 620f, 1f))
                check(GroupRoute(spine, result.count, layout.first().slot), "world five level one $candidate")
                covered++
            }
            board = requireNotNull(PuzzleRules.transfer(board, move)).board
        }
        assertTrue("Fixture did not exercise large groups", covered > 0)
    }
    @Test fun midnightSecondLevelFirstToFifthAlignsBeforeDescending() {
        val level = LevelCatalog.levels.filter { it.worldId == "five" }[1]
        for ((width, height) in listOf(360f to 530f, 412f to 620f)) {
            val layout = boardGeometry(level.initial.tubes.map { it.capacity }, width, height, 1f)
            val route = requireNotNull(stoneRoute(layout, 0, 4, 1, 0, width, height, 1f))
            val group = GroupRoute(route, 4, layout.first().slot)
            check(group, "Midnight level 2, 1→5")
            val released = route.at(route.releaseFraction)
            assertEquals(layout[4].centerX, released.x, 0.01f)
            for (step in 0..100) {
                val point = route.at(route.releaseFraction + (1 - route.releaseFraction) * step / 100f)
                assertEquals("No sideways correction during final descent", layout[4].centerX, point.x, 0.01f)
            }
        }
    }

    @Test fun descentIncludesAisleHeightAcrossHorizontalTurnsAndBounceCanClearOwnRim() {
        val path = StoneRoute(listOf(FlightPoint(0f, 200f), FlightPoint(0f, 0f),
            FlightPoint(100f, 0f), FlightPoint(100f, 100f), FlightPoint(200f, 100f), FlightPoint(200f, 300f)), 0f)
        assertEquals(300f, path.fallHeight, 0.01f)
        val layout = boardGeometry(List(6) { 4 }, 360f, 530f, 1f)
        val top = layout[1].pieceY(3, 1f)
        val clearance = reboundClearance(layout, 1, 3, 1f)
        assertTrue(clearance > top - layout[1].top)
        assertTrue(top - clearance >= layout[1].slot * 0.43f)
    }
}
