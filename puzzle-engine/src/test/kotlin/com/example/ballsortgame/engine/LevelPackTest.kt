package com.example.ballsortgame.engine

import com.example.ballsortgame.engine.tools.PuzzleCorpus
import com.example.ballsortgame.engine.tools.packSelection
import com.example.ballsortgame.engine.tools.selectionIds
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LevelPackTest {
    @get:Rule val temporary = TemporaryFolder()
    private val limits = SearchOptions(SearchLimits(150_000, 150_000, 150_000, 5_000))

    @Test fun roundTripPreservesBoardsCertificatesMetadataAndOrder() {
        val pack = BundledLevelPacks.playtest()
        assertEquals(pack, LevelPackCodec.decode(LevelPackCodec.encode(pack)))
        assertEquals(32, pack.levels.size)
        assertEquals(listOf("three", "four", "five"), pack.worlds.map { it.id })
        assertEquals(listOf(10, 11, 11), pack.worlds.map { w -> pack.levels.count { it.worldId == w.id } })
    }

    @Test fun everyBundledCertificateHasTheClaimedShortestLength() {
        for (level in BundledLevelPacks.playtest().levels) {
            val solved = PuzzleSolver().solve(level.board, limits) as? SolveResult.Solved
            assertNotNull(level.id, solved)
            assertTrue(level.id, level.shortestProven)
            assertTrue(requireNotNull(solved).optimal)
            assertEquals(level.id, level.solution.size, solved.moves.size)
        }
    }

    @Test fun starterBoardsIdsAndCertificatesAreUnchangedInTheLargerPack() {
        val current = BundledLevelPacks.playtest().levels.associateBy { it.id }
        for (old in BundledLevelPacks.starter().levels) assertEquals(old, current[old.id])
    }

    @Test fun everyPreviousPlaytestLevelRetainsItsBoardCertificateAndId() {
        val current = BundledLevelPacks.playtest().levels.associateBy { it.id }
        for (old in BundledLevelPacks.previousPlaytest().levels) assertEquals(old, current[old.id])
        assertEquals(
            BundledLevelPacks.previousPlaytest().levels.filter { it.worldId == "three" },
            BundledLevelPacks.playtest().levels.filter { it.worldId == "three" },
        )
    }

    @Test fun bundledCollectionIsStructurallyDistinct() {
        val index = PuzzleDiversityIndex()
        for (level in BundledLevelPacks.playtest().levels) {
            assertEquals(level.id, DiversityAdmission.ADDED, index.admit(level.board))
        }
    }

    @Test fun largerCapacityStartsGentlyAndEachWorldIncludesBreathingPuzzles() {
        val pack = BundledLevelPacks.playtest()
        for (world in pack.worlds) {
            val levels = pack.levels.filter { it.worldId == world.id }
            assertEquals(LevelRole.INTRODUCTION, levels.first().role)
            assertTrue(levels.any { it.role == LevelRole.RELAXED })
            assertTrue(levels.any { it.role == LevelRole.CHALLENGE })
            assertTrue(levels.first().solution.size < levels.last().solution.size)
        }
        assertEquals(4, pack.levels.first { it.worldId == "five" }.solution.size)
    }

    @Test fun selectionRejectsUnknownOrRepeatedIdsAndKeepsManifestOrderWithinWorlds() {
        val pack = BundledLevelPacks.playtest()
        val a = pack.levels.first { it.worldId == "five" }
        val b = pack.levels.first()
        val c = pack.levels[1]
        assertEquals(listOf(c.id, b.id, a.id), pack.select(listOf(a.id, c.id, b.id)).levels.map { it.id })
        assertThrows(IllegalArgumentException::class.java) { pack.select(listOf(b.id, b.id)) }
        assertThrows(IllegalArgumentException::class.java) { pack.select(listOf("missing")) }
        assertThrows(IllegalArgumentException::class.java) { pack.select(emptyList()) }
    }

    @Test fun malformedVersionsCertificatesWorldsAndDuplicateIdsAreRejected() {
        val text = LevelPackCodec.encode(BundledLevelPacks.starter())
        assertThrows(IllegalArgumentException::class.java) { LevelPackCodec.decode(text.replace("RULES\t1", "RULES\t2")) }
        assertThrows(IllegalArgumentException::class.java) { LevelPackCodec.decode(text.replace("BALLSORT-PACK\t1", "BALLSORT-PACK\t2")) }
        assertThrows(IllegalArgumentException::class.java) { LevelPackCodec.decode(text.replace("0>1\n", "99>1\n")) }
        assertThrows(IllegalArgumentException::class.java) { LevelPackCodec.decode(text.replace("LEVEL\tthree-01\tthree", "LEVEL\tthree-01\tmissing")) }
        assertThrows(IllegalArgumentException::class.java) { LevelPackCodec.decode(text + text.lines().first { it.startsWith("LEVEL") }) }
        assertThrows(IllegalArgumentException::class.java) { LevelPackCodec.decode(text + "UNKNOWN\tthing\n") }
    }

    @Test fun packCommandReadsEditedManifestAndRefusesToOverwrite() {
        val pack = BundledLevelPacks.starter()
        val input = temporary.newFile("pool.txt").apply { writeText(LevelPackCodec.encode(pack)) }
        val manifest = temporary.newFile("selection.txt").apply { writeText("# Review\nthree-02\n\nthree-01 # Keep tutorial\n") }
        val output = java.io.File(temporary.root, "selected.txt")
        val options = mapOf("input" to input.path, "selection" to manifest.path, "output" to output.path)
        packSelection(options)
        assertEquals(listOf("three-02", "three-01"), LevelPackCodec.decode(output.readText()).levels.map { it.id })
        assertEquals(listOf("three-02", "three-01"), selectionIds(manifest.readText()))
        assertThrows(IllegalArgumentException::class.java) { packSelection(options) }
    }

    @Test fun openingAnalysisDistinguishesSolvedDeadAndUnresolvedChoices() {
        val simple = BundledLevelPacks.starter().levels[1]
        val solved = OpeningAnalysis.analyze(simple.board, limits)
        assertEquals(LevelMeasurements.measure(simple).distinctSuccessors, solved.total)
        assertTrue(solved.solvable > 0)
        assertEquals(0, solved.unresolved)
        val dead = OpeningAnalysis.analyze(PuzzleCorpus.trappedPartials, limits)
        assertTrue(dead.provenDead > 0)
        assertEquals(0, dead.solvable)
        val limited = OpeningAnalysis.analyze(simple.board, SearchOptions(SearchLimits(0, 10, 10, 5_000)))
        assertEquals(0, limited.provenDead)
        assertEquals(limited.total, limited.unresolved)
    }

    @Test fun exhaustedWorkshopKeepsReviewableCandidatesWithoutInventingADraft() {
        val output = java.io.File(temporary.root, "workshop")
        assertThrows(IllegalArgumentException::class.java) {
            com.example.ballsortgame.engine.tools.runWorkshop(mapOf(
                "output" to output.path, "max-ms" to "0", "max-states" to "1", "attempts" to "1",
            ))
        }
        assertTrue(java.io.File(output, "attempts.csv").isFile)
        assertTrue(java.io.File(output, "review.html").isFile)
        assertTrue(java.io.File(output, "candidates.csv").isFile)
        assertEquals(13, LevelPackCodec.decode(java.io.File(output, "pool.txt").readText()).levels.size)
        assertFalse(java.io.File(output, "playtest.txt").exists())
        assertTrue(selectionIds(java.io.File(output, "selection.txt").readText()).isEmpty())
    }

    @Test fun mixedCapacityPartialContentsSurviveTheInterchangeFormat() {
        val board = Board(listOf(Tube(1, listOf(0)), Tube(2, listOf(0, 1)), Tube(2)))
        val solution = requireNotNull(PuzzleSolver().solve(board, limits) as? SolveResult.Solved).moves
        val pack = LevelPack(listOf(PackWorld("mixed", "Experiment", "Mixed capacities")), listOf(
            PackLevel("mixed-example", "mixed", LevelRole.REGULAR, 9, true, board, solution),
        ))
        assertEquals(pack, LevelPackCodec.decode(LevelPackCodec.encode(pack)))
    }
}
