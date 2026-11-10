package com.example.ballsortgame.engine

/** Authoring-only graph matching; deliberately separate from the solver's cheap state key. */
enum class StructuralMatch { EQUIVALENT, DISTINCT, LIMIT_REACHED }
data class StructuralComparison(val match: StructuralMatch, val pairings: Int)

object PuzzleStructure {
    private fun pattern(tube: Tube): String {
        val names = LinkedHashMap<Int, Int>()
        return "${tube.capacity}:" + tube.balls.joinToString(",") { color ->
            names.getOrPut(color) { names.size }.toString()
        }
    }

    /** Necessary invariants only. Equal fingerprints are NOT a proof of equivalence. */
    fun fingerprint(board: Board): String =
        board.tubes.map(::pattern).sorted().joinToString("|") + "#" +
            board.tubes.flatMap { it.balls }.groupingBy { it }.eachCount().values.sorted().joinToString(",")

    /**
     * Checks tube permutation and a single consistent global color bijection.
     * Never reverses piece order inside a tube, nor swaps contents across unequal capacities.
     */
    fun compare(first: Board, second: Board, maxPairings: Int = 20_000): StructuralComparison {
        require(maxPairings >= 0)
        if (fingerprint(first) != fingerprint(second)) return StructuralComparison(StructuralMatch.DISTINCT, 0)
        fun colorSignatures(board: Board): Map<Int, String> =
            board.tubes.flatMap { it.balls }.distinct().associateWith { color ->
                board.tubes.mapNotNull { tube ->
                    val count = tube.balls.count { it == color }
                    if (count == 0) null else "${tube.capacity}:${tube.balls.size}:$count"
                }.sorted().joinToString("|")
            }
        val firstSignatures = colorSignatures(first)
        val secondSignatures = colorSignatures(second)
        val targetGroups = second.tubes.indices.groupBy { pattern(second.tubes[it]) }
        val order = first.tubes.indices.sortedWith(
            compareBy<Int> { targetGroups.getValue(pattern(first.tubes[it])).size }
                .thenByDescending { first.tubes[it].balls.distinct().size }
                .thenByDescending { first.tubes[it].balls.size },
        )
        val used = BooleanArray(second.tubes.size)
        var pairings = 0
        fun search(depth: Int, forward: Map<Int, Int>, backward: Map<Int, Int>): StructuralMatch {
            if (depth == order.size) return StructuralMatch.EQUIVALENT
            val tube = first.tubes[order[depth]]
            val seenTargets = HashSet<Tube>()
            for (targetIndex in targetGroups.getValue(pattern(tube))) {
                val target = second.tubes[targetIndex]
                if (used[targetIndex] || !seenTargets.add(target)) continue
                if (pairings >= maxPairings) return StructuralMatch.LIMIT_REACHED
                pairings++
                val nextForward = forward.toMutableMap()
                val nextBackward = backward.toMutableMap()
                var compatible = true
                for (index in tube.balls.indices) {
                    val a = tube.balls[index]
                    val b = target.balls[index]
                    if ((nextForward[a] != null && nextForward[a] != b) ||
                        (nextBackward[b] != null && nextBackward[b] != a) ||
                        firstSignatures[a] != secondSignatures[b]
                    ) {
                        compatible = false
                        break
                    }
                    nextForward[a] = b
                    nextBackward[b] = a
                }
                if (!compatible) continue
                used[targetIndex] = true
                val result = search(depth + 1, nextForward, nextBackward)
                used[targetIndex] = false
                if (result != StructuralMatch.DISTINCT) return result
            }
            return StructuralMatch.DISTINCT
        }
        return StructuralComparison(search(0, emptyMap(), emptyMap()), pairings)
    }
}

enum class DiversityAdmission { ADDED, DUPLICATE, INCONCLUSIVE }

/** One index per authoring batch. Not thread-safe; retains accepted boards only. */
class PuzzleDiversityIndex(private val maxPairingsPerComparison: Int = 20_000) {
    private val buckets = HashMap<String, MutableList<Board>>()
    var size: Int = 0
        private set

    init { require(maxPairingsPerComparison >= 0) }

    fun admit(board: Board): DiversityAdmission {
        val fingerprint = PuzzleStructure.fingerprint(board)
        val bucket = buckets.getOrPut(fingerprint) { ArrayList() }
        for (previous in bucket) {
            when (PuzzleStructure.compare(previous, board, maxPairingsPerComparison).match) {
                StructuralMatch.EQUIVALENT -> return DiversityAdmission.DUPLICATE
                StructuralMatch.LIMIT_REACHED -> return DiversityAdmission.INCONCLUSIVE
                StructuralMatch.DISTINCT -> Unit
            }
        }
        bucket.add(board)
        size++
        return DiversityAdmission.ADDED
    }
}
