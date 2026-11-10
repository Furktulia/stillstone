package com.example.ballsortgame.engine

/** Authoring intent, not a measured human difficulty rating. Never used to lock levels. */
enum class LevelRole { INTRODUCTION, RELAXED, REGULAR, CHALLENGE }

data class PackWorld(val id: String, val title: String, val description: String)

data class PackLevel(
    val id: String,
    val worldId: String,
    val role: LevelRole,
    val seed: Long?,
    val shortestProven: Boolean,
    val board: Board,
    val solution: List<Move>,
) {
    init {
        require(!board.isSolved) { "Already solved level: $id" }
        require(solution.isNotEmpty() && PuzzleRules.verifiesSolution(board, solution)) {
            "Invalid solution certificate: $id"
        }
    }
}

data class LevelPack(val worlds: List<PackWorld>, val levels: List<PackLevel>) {
    init {
        require(worlds.isNotEmpty() && levels.isNotEmpty())
        require(worlds.map { it.id }.distinct().size == worlds.size) { "Duplicate world ID" }
        require(levels.map { it.id }.distinct().size == levels.size) { "Duplicate level ID" }
        val worldIds = worlds.map { it.id }.toSet()
        require(levels.all { it.worldId in worldIds }) { "Unknown world" }
    }

    /** Manifest order is level order within each world. World order stays explicit. */
    fun select(ids: List<String>): LevelPack {
        require(ids.isNotEmpty() && ids.distinct().size == ids.size) { "Empty or duplicate selection" }
        val byId = levels.associateBy { it.id }
        require(ids.all { it in byId }) { "Selection contains an unknown level ID" }
        val chosen = ids.map { byId.getValue(it) }
        val used = chosen.map { it.worldId }.toSet()
        val orderedWorlds = worlds.filter { it.id in used }
        return LevelPack(orderedWorlds, orderedWorlds.flatMap { w -> chosen.filter { it.worldId == w.id } })
    }
}

/**
 * Versioned, tab-separated plain-text interchange. No Android or serialization dependency.
 * Board contents are bottom-to-top. Certificates are replayed on BOTH encode and decode.
 * shortestProven records the author's exact solver result; replay alone proves only solvability.
 */
object LevelPackCodec {
    private const val HEADER = "BALLSORT-PACK\t1\tRULES\t1"
    private val idPattern = Regex("[a-z0-9][a-z0-9-]{0,79}")

    fun encode(pack: LevelPack): String {
        require(pack.worlds.size <= 32 && pack.levels.size <= 10_000)
        val lines = mutableListOf(HEADER)
        for (world in pack.worlds) {
            require(idPattern.matches(world.id))
            require(listOf(world.title, world.description).all { validText(it) })
            lines += listOf("WORLD", world.id, world.title, world.description).joinToString("\t")
        }
        for (level in pack.levels) {
            require(idPattern.matches(level.id))
            require(level.board.tubes.size <= 32 && level.solution.size <= 10_000)
            require(level.board.tubes.all { it.capacity in 1..64 && it.balls.all { color -> color in 0..31 } })
            require(PuzzleRules.verifiesSolution(level.board, level.solution))
            val board = level.board.tubes.joinToString("|") { "${it.capacity}:${it.balls.joinToString(",")}" }
            val moves = level.solution.joinToString(",") { "${it.source}>${it.destination}" }
            lines += listOf(
                "LEVEL", level.id, level.worldId, level.role.name, level.seed?.toString() ?: "-",
                level.shortestProven.toString(), board, moves,
            ).joinToString("\t")
        }
        return lines.joinToString("\n", postfix = "\n").also { require(it.length <= 8_000_000) }
    }

    fun decode(text: String): LevelPack {
        require(text.length <= 8_000_000) { "Level pack too large" }
        val lines = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported level-pack or rules version" }
        require(lines.size <= 10_033) { "Too many records" }
        val worlds = mutableListOf<PackWorld>()
        val levels = mutableListOf<PackLevel>()
        for (line in lines.drop(1)) {
            val f = line.split('\t')
            when (f.first()) {
                "WORLD" -> {
                    require(f.size == 4 && idPattern.matches(f[1]) && validText(f[2]) && validText(f[3])) { "Invalid world" }
                    require(worlds.size < 32)
                    worlds += PackWorld(f[1], f[2], f[3])
                }
                "LEVEL" -> {
                    require(f.size == 8 && idPattern.matches(f[1]) && idPattern.matches(f[2])) { "Invalid level record" }
                    require(levels.size < 10_000)
                    val tubes = f[6].split('|')
                    require(tubes.size in 1..32)
                    val board = Board(tubes.map { raw ->
                        val parts = raw.split(':')
                        require(parts.size == 2)
                        val capacity = parts[0].toInt()
                        require(capacity in 1..64)
                        val balls = if (parts[1].isEmpty()) emptyList() else parts[1].split(',').map(String::toInt)
                        require(balls.all { it in 0..31 })
                        Tube(capacity, balls)
                    })
                    val rawMoves = f[7].split(',')
                    require(rawMoves.size in 1..10_000)
                    val moves = rawMoves.map {
                        val parts = it.split('>')
                        require(parts.size == 2)
                        Move(parts[0].toInt(), parts[1].toInt())
                    }
                    levels += PackLevel(
                        f[1], f[2], LevelRole.valueOf(f[3]), f[4].takeUnless { it == "-" }?.toLong(),
                        f[5].toBooleanStrict(), board, moves,
                    )
                }
                else -> throw IllegalArgumentException("Unknown level-pack record")
            }
        }
        return LevelPack(worlds.toList(), levels.toList())
    }

    private fun validText(value: String) =
        value.isNotBlank() && value.length <= 200 && value.none { it == '\t' || it == '\r' || it == '\n' }
}

object BundledLevelPacks {
    fun starter(): LevelPack = read("starter-v1.txt")
    fun previousPlaytest(): LevelPack = read("playtest-v1.txt")
    fun playtest(): LevelPack = read("playtest-v2.txt")
    fun difficultyStudy(): LevelPack = read("difficulty-study-v1.txt")
    private fun read(name: String): LevelPack =
        requireNotNull(BundledLevelPacks::class.java.getResourceAsStream("/levels/$name")) {
            "Missing bundled pack: $name"
        }.bufferedReader(Charsets.UTF_8).use { LevelPackCodec.decode(it.readText()) }
}

/** Stable structural observations; none alone is a claim of human difficulty. */
data class LevelMeasurements(
    val boundaries: Int,
    val runs: Int,
    val emptyTubes: Int,
    val legalMoves: Int,
    val distinctSuccessors: Int,
    val solutionMoves: Int,
    val rearrangementMoves: Int,
    val maxRun: Int,
) {
    companion object {
        fun measure(level: PackLevel): LevelMeasurements {
            var cursor = level.board
            var rearrangements = 0
            for (move in level.solution) {
                val next = requireNotNull(PuzzleRules.transfer(cursor, move)).board
                if (PuzzleRules.boundaryCount(next) == PuzzleRules.boundaryCount(cursor)) rearrangements++
                cursor = next
            }
            val legal = PuzzleRules.legalMoves(level.board)
            return LevelMeasurements(
                PuzzleRules.boundaryCount(level.board), level.board.tubes.sumOf { it.runs },
                level.board.tubes.count { it.balls.isEmpty() }, legal.size,
                legal.map { requireNotNull(PuzzleRules.transfer(level.board, it)).board.canonicalKey() }.distinct().size,
                level.solution.size, rearrangements, level.board.tubes.maxOf { it.topRun },
            )
        }
    }
}
