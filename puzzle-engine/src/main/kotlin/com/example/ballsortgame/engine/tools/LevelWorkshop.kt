package com.example.ballsortgame.engine.tools

import com.example.ballsortgame.engine.*
import java.io.File

private data class Profile(
    val world: String, val role: LevelRole, val colors: Int, val capacity: Int,
    val empty: Int, val minMoves: Int, val maxMoves: Int,
)

private val profiles = listOf(
    Profile("three", LevelRole.RELAXED, 2, 3, 2, 2, 4),
    Profile("three", LevelRole.REGULAR, 4, 3, 2, 7, 10),
    Profile("three", LevelRole.CHALLENGE, 5, 3, 1, 12, 18),
    Profile("four", LevelRole.INTRODUCTION, 2, 4, 2, 3, 6),
    Profile("four", LevelRole.RELAXED, 3, 4, 2, 5, 8),
    Profile("four", LevelRole.REGULAR, 4, 4, 2, 8, 12),
    Profile("four", LevelRole.CHALLENGE, 5, 4, 1, 15, 24),
    Profile("five", LevelRole.INTRODUCTION, 2, 5, 2, 3, 6),
    Profile("five", LevelRole.RELAXED, 3, 5, 2, 6, 10),
    Profile("five", LevelRole.REGULAR, 4, 5, 2, 11, 16),
    Profile("five", LevelRole.CHALLENGE, 5, 5, 1, 18, 27),
)

/** Offline authoring only. Never runs at app startup. Every accepted candidate is solved exactly. */
internal fun runWorkshop(options: Map<String, String>) {
    val output = File(options["output"] ?: "build/puzzle-lab/workshop")
    require(!output.exists()) { "Refusing to overwrite ${output.absolutePath}; choose a fresh output directory" }
    val perProfile = options.number("per-profile", 4)
    require(perProfile in 4..30) { "per-profile must be between 4 and 30" }
    val seed = options.longNumber("seed", 20_260)
    require(seed >= 0 && seed <= Long.MAX_VALUE - 1_000_000)
    val search = SearchOptions(limits(options))
    val base = BundledLevelPacks.starter()
    val worlds = base.worlds + PackWorld("five", "Midnight", "Five-slot tubes")
    val reference = referenceChallenge()
    val budget = challengeBudget(options)
    val referenceAssessment = assessChallenge(reference, budget)
    val requirements = referenceChallengeRequirements(referenceAssessment)
    val all = (base.levels + reference).toMutableList()
    val diversity = PuzzleDiversityIndex()
    all.forEach { diversity.admit(it.board) }
    val challengeCount = options.number("challenge-count", 48)
    require(challengeCount in 1..200)
    require(output.mkdirs())
    val log = mutableListOf("profile,role,seed,outcome,attempts,evaluated,expanded,search_ms,rejections")
    val effort = HashMap<String, SearchStatistics>()
    for ((profileIndex, profile) in profiles.withIndex()) {
        val profileCount = if (profile.role == LevelRole.CHALLENGE) challengeCount else perProfile
        for (index in 0 until profileCount) {
            val currentSeed = seed + profileIndex * 1000L + index
            val request = GenerationRequest(
                PuzzleGenerator.equalCapacityGoal(profile.colors, profile.capacity, profile.empty),
                currentSeed, maxAttempts = options.number("attempts", 30),
                minSolutionMoves = profile.minMoves, maxSolutionMoves = profile.maxMoves,
                requireOptimalSolution = true, search = search, strategy = GenerationStrategy.SHUFFLE_AND_SOLVE,
            )
            val result = PuzzleGenerator().generate(request, diversity)
            val d = result.diagnostics
            val accepted = result as? GenerationResult.Generated
            val suffix = accepted?.puzzle?.board?.let { board ->
                java.security.MessageDigest.getInstance("SHA-256").digest(board.toString().toByteArray(Charsets.UTF_8))
                    .take(6).joinToString("") { "%02x".format(it.toInt() and 255) }
            }
            val id = "${profile.world}-${profile.role.name.lowercase()}-$currentSeed" + (suffix?.let { "-$it" } ?: "")
            log += listOf(
                profile.world, profile.role, currentSeed, if (accepted != null) "ACCEPTED" else "EXHAUSTED",
                d.attempts, d.evaluatedCandidates, d.expandedStates, d.searchMillis,
                d.rejections.entries.joinToString(";") { "${it.key}=${it.value}" },
            ).joinToString(",")
            if (accepted != null) {
                val p = accepted.puzzle
                all += PackLevel(id, profile.world, profile.role, p.seed, true, p.board, p.solution)
                effort[id] = p.evaluation.statistics
                File(output, "$id.json").writeText(PuzzleJson.encode(p) + "\n")
                println("$id: ${p.solution.size} shortest moves; ${d.attempts} attempts")
            } else {
                System.err.println("$id: not accepted; ${d.rejections}")
            }
            File(output, "attempts.csv").writeText(log.joinToString("\n", postfix = "\n"))
        }
    }
    val pool = LevelPack(worlds, all.toList())
    File(output, "pool.txt").writeText(LevelPackCodec.encode(pool))
    val assessments = pool.levels.filter { it.role == LevelRole.CHALLENGE }
        .associate { it.id to if (it.id == reference.id) referenceAssessment else assessChallenge(it, budget) }
    File(output, "challenge-assessment.csv").writeText(challengeCsv(pool.levels, assessments, requirements))
    File(output, "challenge-parameters.txt").writeText("protocol=1\nrequirements=$requirements\nanalysis=$budget\nreference=${reference.id}\n")
    val suggestion = runCatching { suggestPlaytest(pool, assessments, requirements) }
    val selection = suggestion.getOrDefault(emptyList())
    File(output, "selection.txt").writeText(
        (if (suggestion.isSuccess) "# Suggested playtest order. Edit IDs or download a selection from review.html.\n"
        else "# No complete draft: insufficient candidates. Choose IDs in review.html or rerun with a new seed/budget.\n") +
            "# Roles are authoring intent; difficulty needs human calibration.\n" +
            selection.joinToString("\n", postfix = "\n"),
    )
    val openings = pool.levels.associate { it.id to OpeningAnalysis.analyze(it.board, search) }
    File(output, "candidates.csv").writeText(candidateCsv(pool, effort, openings))
    File(output, "review.html").writeText(reviewHtml(pool, selection, openings, assessments, requirements))
    println("Review ${File(output, "review.html").absolutePath}")
    require(suggestion.isSuccess) {
        "${suggestion.exceptionOrNull()?.message} Candidate pool/reports were retained; no incomplete playtest pack was exported."
    }
    val selected = pool.select(selection)
    File(output, "playtest.txt").writeText(LevelPackCodec.encode(selected))
    println("Draft: ${selected.levels.size} levels. Edit selection.txt then run pack; no files are imported into Android automatically.")
}

internal fun suggestPlaytest(pool: LevelPack, assessments: Map<String, ChallengeAssessment>, requirements: ChallengeRequirements): List<String> {
    val picked = mutableListOf<PackLevel>()
    val generated = pool.levels.filter { it.seed != null }.toMutableList()
    fun old(id: String) { picked += pool.levels.first { it.id == id } }
    fun pick(world: String, role: LevelRole) {
        val candidates = generated.filter { it.worldId == world && it.role == role }.filter {
            role != LevelRole.CHALLENGE || assessments[it.id]?.let { a -> requirements.rejectionReasons(a).isEmpty() } == true
        }
        require(candidates.isNotEmpty()) { "Not enough $world $role candidates. Inspect attempts.csv and increase the budget or change seed." }
        val previous = picked.filter { it.worldId == world && it.role == role && it.seed != null }
        fun distance(a: PackLevel, b: PackLevel): Int {
            val x = LevelMeasurements.measure(a)
            val y = LevelMeasurements.measure(b)
            return kotlin.math.abs(x.solutionMoves - y.solutionMoves) +
                kotlin.math.abs(x.rearrangementMoves - y.rearrangementMoves) * 2 +
                kotlin.math.abs(x.distinctSuccessors - y.distinctSuccessors) +
                kotlin.math.abs(x.maxRun - y.maxRun)
        }
        val chosen = if (role == LevelRole.CHALLENGE) {
            candidates.map { it to assessments.getValue(it.id) }.sortedWith(challengeOrder).first().first
        } else if (previous.isEmpty()) candidates.minWith(compareBy<PackLevel> { it.solution.size }.thenBy { it.id })
            else candidates.sortedBy { it.id }.maxBy { c -> previous.minOf { distance(c, it) } }
        picked += chosen
        generated.remove(chosen)
    }
    val intro = LevelRole.INTRODUCTION
    val calm = LevelRole.RELAXED
    val regular = LevelRole.REGULAR
    val hard = LevelRole.CHALLENGE
    old("three-01"); old("three-02"); old("three-03"); old("three-04")
    pick("three", calm); old("three-05"); pick("three", regular); old("three-06")
    pick("three", regular); old(referenceChallenge().id)

    pick("four", intro); old("four-01"); old("four-02"); pick("four", regular)
    old("four-03"); old("four-04"); pick("four", calm); old("four-05"); old("four-06"); pick("four", hard)

    for (role in listOf(intro, calm, regular, regular, calm, regular, hard, calm, regular, hard)) pick("five", role)
    return picked.map { it.id }
}

internal fun selectionIds(text: String): List<String> = text.lineSequence()
    .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toList()

internal fun packSelection(options: Map<String, String>) {
    val input = File(requireNotNull(options["input"]) { "pack requires --input pool.txt" })
    val manifest = File(requireNotNull(options["selection"]) { "pack requires --selection selection.txt" })
    val output = File(requireNotNull(options["output"]) { "pack requires --output selected.txt" })
    require(!output.exists()) { "Refusing to overwrite ${output.absolutePath}" }
    val selected = LevelPackCodec.decode(input.readText()).select(selectionIds(manifest.readText()))
    // Authoring artifacts are exported separately; updating the bundled source resource is an explicit step.
    output.parentFile?.let { require(it.isDirectory || it.mkdirs()) }
    output.writeText(LevelPackCodec.encode(selected))
    println("Exported ${selected.levels.size} replay-verified levels in ${selected.worlds.size} worlds to ${output.absolutePath}")
}

internal fun verifyPack(options: Map<String, String>) {
    val pack = LevelPackCodec.decode(File(requireNotNull(options["input"]) { "verify-pack requires --input" }).readText())
    for (level in pack.levels) {
        val result = PuzzleSolver().solve(level.board, SearchOptions(limits(options)))
        require(result is SolveResult.Solved && result.optimal) { "${level.id}: could not prove shortest path within budget: ${resultStatus(result)}" }
        require(!level.shortestProven || level.solution.size == result.moves.size) { "Incorrect shortest-path claim: ${level.id}" }
        println("${level.id}: verified; shortest ${result.moves.size}; certificate ${level.solution.size}")
    }
    println("Verified ${pack.levels.size} certificates and shortest-path claims.")
}

private fun candidateCsv(pack: LevelPack, effort: Map<String, SearchStatistics>, openings: Map<String, OpeningChoices>): String {
    val rows = mutableListOf(
        "id,world,intended_role,seed,colors,capacities,empty_tubes,shortest_proven,solution_moves,boundaries,runs,rearrangements,legal_moves,distinct_successors,max_top_run,expanded,search_ms,solvable_openings,proven_dead_openings,unresolved_openings,playtest_feeling,playtest_notes",
    )
    for (level in pack.levels) {
        val m = LevelMeasurements.measure(level)
        val e = effort[level.id]
        val opening = openings.getValue(level.id)
        rows += listOf(
            level.id, level.worldId, level.role, level.seed ?: "",
            level.board.tubes.flatMap { it.balls }.distinct().size,
            level.board.tubes.map { it.capacity }.distinct().joinToString("/"), m.emptyTubes,
            level.shortestProven, m.solutionMoves, m.boundaries, m.runs, m.rearrangementMoves,
            m.legalMoves, m.distinctSuccessors, m.maxRun, e?.expandedStates ?: "", e?.elapsedMillis ?: "", opening.solvable, opening.provenDead, opening.unresolved, "", "",
        ).joinToString(",")
    }
    return rows.joinToString("\n", postfix = "\n")
}

private fun html(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun reviewHtml(pack: LevelPack, selected: List<String>, openings: Map<String, OpeningChoices>, assessments: Map<String, ChallengeAssessment>, requirements: ChallengeRequirements): String {
    val colors = listOf("#eb9791", "#86cdb6", "#e7c36d", "#8cb7e8", "#b7a0de")
    val cards = pack.worlds.joinToString("") { world ->
        "<h2>${html(world.title)} · ${html(world.description)}</h2><div class='grid'>" +
            pack.levels.filter { it.worldId == world.id }.joinToString("") { level ->
                val m = LevelMeasurements.measure(level)
                val opening = openings.getValue(level.id)
                val analysis = assessments[level.id]
                val screening = analysis?.let {
                    val reasons = requirements.rejectionReasons(it)
                    "<p>Best simple bot: ${it.easiestBotSuccesses}/${it.trials} solved<br>" +
                        "Required decisions: ${it.minDecisions ?: "unknown"} · misleading checkpoints: ${it.minTemptations ?: "unknown"}<br>" +
                        html(if (reasons.isEmpty()) "Passes provisional challenge screen" else reasons.joinToString("; ")) + "</p>"
                } ?: ""
                val tubes = level.board.tubes.joinToString("") { tube ->
                    "<div class='tube' style='height:${tube.capacity * 21}px'>" +
                        tube.balls.asReversed().joinToString("") { "<span class='piece' style='background:${colors[it % colors.size]}'>${it + 1}</span>" } + "</div>"
                }
                "<article><label><input type='checkbox' value='${html(level.id)}' ${if (level.id in selected) "checked" else ""}> ${html(level.id)}</label>" +
                    "<p>${level.role} · ${m.solutionMoves} ${if (level.shortestProven) "shortest" else "certificate"} moves</p>" +
                    "<div class='board'>$tubes</div><p>${m.boundaries} boundaries · ${m.rearrangementMoves} rearrangements<br>" +
                    "${m.emptyTubes} empty tubes · ${m.distinctSuccessors} distinct opening choices<br>${opening.solvable} solvable · ${opening.provenDead} proven dead · ${opening.unresolved} unresolved</p>$screening</article>"
            } + "</div>"
    }
    return """<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Ball Sort · Level workshop</title><style>
body{margin:0;padding:24px;background:#12101b;color:#e9e2f0;font:16px system-ui;line-height:1.5}
header{max-width:900px}h1{font-weight:500}h2{margin-top:36px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(290px,1fr));gap:16px}
article{border:1px solid #4b4358;border-radius:12px;padding:16px}label{overflow-wrap:anywhere}p{color:#bfb4ca;font-size:14px}
.board{display:flex;gap:12px;align-items:flex-end;justify-content:center;min-height:125px;margin:20px 0}
.tube{width:28px;display:flex;flex-direction:column;justify-content:flex-end;align-items:center;border:2px solid #8b8099;border-top:0;border-radius:0 0 10px 10px;padding:3px;gap:3px}
.piece{width:18px;height:18px;line-height:18px;flex-shrink:0;border-radius:7px;text-align:center;color:#211828;font-size:11px}
button{padding:12px 18px;border:0;border-radius:8px;background:#c6b4ea;color:#20182e;cursor:pointer}
</style><header><h1>Level workshop</h1><p>Choose puzzles, then download a manifest. Cards follow pool order. Downloads preserve the suggested playtest order, with new choices appended; edit the manifest to set your own order within each world. Nothing is sent online.</p>
<p>Roles are design intent, not calibrated human difficulty. Rearrangements are certificate moves that remove no boundary; opening choices exclude tube-permutation duplicates. Search timing is recorded in candidates.csv, not treated as a difficulty score. The twelve original puzzles and the Dusk 10 reference are preserved. Challenge screening tests four simple strategies across tube orders and minimum decision requirements across all shortest routes; it is a provisional machine screen, not a human rating.</p>
<button id="download">Download selection.txt</button><p id="count"></p></header>$cards
<script>
const boxes = [...document.querySelectorAll('input')];
const suggestedOrder = [${selected.joinToString(",") { "\"$it\"" }}];
function update(){document.getElementById('count').textContent=boxes.filter(b=>b.checked).length+' puzzles selected';}
boxes.forEach(b=>b.addEventListener('change',update));update();
document.getElementById('download').onclick=()=>{
 const checked=new Set(boxes.filter(b=>b.checked).map(b=>b.value));
 const ids=[...new Set([...suggestedOrder,...boxes.map(b=>b.value)])].filter(id=>checked.has(id));
 if(!ids.length){alert('Choose at least one puzzle.');return;}
 const content='# Selected from the level workshop. Edit line order before packing.\n'+ids.join('\n')+'\n';
 const url=URL.createObjectURL(new Blob([content],{type:'text/plain'}));
 const a=document.createElement('a');a.href=url;a.download='selection.txt';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
};
</script></html>"""
}
