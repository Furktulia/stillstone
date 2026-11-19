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

    /** The creator's example E2-B-11MK1N5UER: tube 2 to 5, then at once tube 4 to 5. */
    @Test fun laterGroupHoldsShortOfAnEarlierGroupBoundForTheSameTubeAndThenArrives() {
        val width = 372f; val height = 560f
        val layout = boardGeometry(List(5) { 3 }, width, height, 1f)
        val slot = layout.first().slot
        val first = GroupRoute(requireNotNull(stoneRoute(layout, 1, 4, 2, 0, width, height, 1f)), 1, slot)
        val second = GroupRoute(requireNotNull(stoneRoute(layout, 3, 4, 2, 1, width, height, 1f)), 1, slot)
        // Without the rule the second, shorter route waits above tube 5, on the first one's way down.
        val waiting = second.at(second.releaseFraction)
        assertTrue((0..200).any { first.at(it / 200f).first().distance(waiting.first()) < slot * 0.5f })

        // Many frame rates and speeds: an earlier version let the held stone through when the
        // first stone's movement nudged it from "at the edge" to "already inside".
        for (theirSteps in listOf(90, 137, 200, 333, 611)) for (mySteps in listOf(23, 60, 97, 250)) {
            var mine = 0f
            var held = 0
            for (step in 0..theirSteps + mySteps + 5) {
                val theirs = (step / theirSteps.toFloat()).coerceAtMost(1f)
                val landed = theirs >= 1f
                val next = minOf(second.releaseFraction, mine + second.releaseFraction / mySteps)
                val blocked = !landed && entersClaimedSpace(second.at(mine), second.at(next),
                    first.claimed(theirs, Float.POSITIVE_INFINITY), slot)
                if (blocked) held++ else mine = next
                if (!landed) assertTrue("stones overlap at step $step ($theirSteps/$mySteps)",
                    first.at(theirs).first().distance(second.at(mine).first()) >= slot * 0.9f)
            }
            // A slow second stone behind a fast first one has nothing to wait for.
            if (mySteps * 2 < theirSteps) assertTrue("the later group never had to wait ($theirSteps/$mySteps)", held > 0)
            assertEquals("the later group must still arrive", second.releaseFraction, mine, 0.0001f)
        }
    }

    @Test fun aGroupAlreadyInTheWayIsNotHeldAndFarCrossingsAreNotClaimed() {
        val layout = boardGeometry(List(5) { 3 }, 372f, 560f, 1f)
        val slot = layout.first().slot
        val route = GroupRoute(requireNotNull(stoneRoute(layout, 1, 4, 2, 0, 372f, 560f, 1f)), 1, slot)
        val onPath = route.at(0.6f)
        assertFalse(entersClaimedSpace(onPath, onPath, route.claimed(0.2f, Float.POSITIVE_INFINITY), slot))
        val nearEnd = route.at(1f)
        val away = listOf(FlightPoint(nearEnd.first().x + slot * 3, nearEnd.first().y))
        assertTrue(entersClaimedSpace(away, nearEnd, route.claimed(0f, Float.POSITIVE_INFINITY), slot))
        assertFalse(entersClaimedSpace(away, nearEnd, route.claimed(0f, slot), slot))
    }

    @Test fun fallStartsAfterLastHorizontalTurnAndBounceCanClearOwnRim() {
        val path = StoneRoute(listOf(FlightPoint(0f, 200f), FlightPoint(0f, 0f),
            FlightPoint(100f, 0f), FlightPoint(100f, 100f), FlightPoint(200f, 100f), FlightPoint(200f, 300f)), 0f)
        assertEquals(200f, GroupRoute(path, 2, 40f).fallHeight, 0.01f)
        val layout = boardGeometry(List(6) { 4 }, 360f, 530f, 1f)
        val top = layout[1].pieceY(3, 1f)
        val clearance = reboundClearance(layout, 1, 3, 1f)
        assertTrue(clearance > top - layout[1].top)
        assertTrue(top - clearance >= layout[1].slot * 0.43f)
    }
}
