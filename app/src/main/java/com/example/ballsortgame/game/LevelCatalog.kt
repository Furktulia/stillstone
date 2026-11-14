package com.example.ballsortgame.game

import com.example.ballsortgame.engine.BundledLevelPacks

/** Offline workshop export. All worlds are available during playtesting. */
object LevelCatalog {
    private val pack = BundledLevelPacks.playtest()
    val levels: List<PlayableLevel> = pack.worlds.flatMap { world ->
        pack.levels.filter { it.worldId == world.id }.map { level ->
            PlayableLevel(
                level.id, world.title, level.board, level.solution, world.id, world.description, level.rules,
                level.role == com.example.ballsortgame.engine.LevelRole.CHALLENGE,
            )
        }
    }
}
