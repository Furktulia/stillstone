package com.example.ballsortgame.game

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import androidx.lifecycle.*
import com.example.ballsortgame.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

data class EndlessUi(val game: GameViewModel? = null, val code: String = "", val busy: Boolean = true, val error: String = "")
class EndlessViewModel(context: Context) : ViewModel() {
    private val root = File(context.filesDir, "endless").apply { mkdirs() }
    private val file = AtomicFile(File(root, "current.json"))
    private val lock = Any()
    private var record: JSONObject? = null
    private var owner = ViewModelStore()
    private var request: Job? = null
    private var prefetch: Job? = null
    private var active = false
    private var closed = false
    private val mutable = MutableStateFlow(EndlessUi())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            val restored = withContext(Dispatchers.IO) {
                try { JSONObject(String(file.readFully(), Charsets.UTF_8)) } catch (_: Exception) { null }
            }
            try {
                if (restored != null) { install(restored); prime() } else { mutable.value = EndlessUi(busy = false); if (active) next(false) }
            } catch (_: Exception) { mutable.value = EndlessUi(busy = false, error = "Saved endless puzzle could not be restored. Start a new puzzle.") }
        }
    }
    private fun write(value: JSONObject) {
        val out = file.startWrite()
        try { out.write(value.toString().toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    private fun install(value: JSONObject) {
        require(value.getInt("version") == 1)
        if (closed) return
        val level = LevelPackCodec.decode(value.getString("pack")).levels.single()
        val playable = PlayableLevel(level.id, "Endless", level.board, level.solution, "endless")
        synchronized(lock) { record = value }
        owner.clear()
        owner = ViewModelStore()
        val adapter = object : GameSaveStore {
            override fun load(): StoredProgress = synchronized(lock) {
                val progress = value.optString("progress")
                StoredProgress(if (progress.isEmpty()) null else ProgressCodec.decode(Base64.decode(progress, Base64.NO_WRAP)))
            }
            override fun save(progress: GameProgress) = synchronized(lock) {
                if (record === value) {
                    value.put("progress", Base64.encodeToString(ProgressCodec.encode(progress), Base64.NO_WRAP))
                    write(value)
                }
            }
        }
        val game = ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>) = GameViewModel(listOf(playable), adapter) as T
        })[GameViewModel::class.java]
        mutable.value = EndlessUi(game, value.getString("code"), false)
    }
    fun setActive(value: Boolean) {
        active = value
        if (!value) {
            request?.cancel(); prefetch?.cancel()
        } else if (state.value.game == null && !state.value.busy) next(false) else prime()
    }
    /** Recipe letter of a code: R relaxed, B balanced, C challenge. Unknown codes fall back to balanced. */
    private fun recipeOf(code: String): Char = code.split("-").getOrNull(1)?.singleOrNull()?.takeIf { it in "RBC" } ?: 'B'
    /** H1 codes are screened for play with hidden colours; E2 for open play. */
    private fun version(hidden: Boolean) = if (hidden) "H1" else "E2"
    private suspend fun construct(recipe: Char, entered: String? = null, hidden: Boolean = false): JSONObject = withContext(Dispatchers.Default) {
        var chosen: Pair<String, PackLevel>? = null
        repeat(if (entered == null) 12 else 1) {
            ensureActive()
            if (chosen == null) {
                val code = entered?.trim()?.uppercase() ?: SeededPuzzle.code(java.security.SecureRandom().nextLong().ushr(16), recipe, hidden)
                val context = currentCoroutineContext()
                try { chosen = code to SeededPuzzle.create(code, cancelled = { !context.isActive }) }
                catch (e: IllegalArgumentException) { if (entered != null) throw e }
            }
        }
        val (code, level) = requireNotNull(chosen) { "No puzzle found within budget. Try again." }
        JSONObject().put("version", 1).put("code", code).put("pack", LevelPackCodec.encode(
            LevelPack(listOf(PackWorld("endless", "Endless", "Seeded puzzles")), listOf(level))))
    }
    private fun prime() {
        prefetch?.cancel()
        if (!active || state.value.game == null) return
        val current = synchronized(lock) { record } ?: return
        prefetch = viewModelScope.launch {
            try {
            repeat(2) {
                val full = synchronized(lock) { (current.optJSONArray("queue")?.length() ?: 0) >= 2 }
                if (full) return@launch
                // Queue more of the kind being played; next() discards them if the choice changes.
                val candidate = construct(recipeOf(current.getString("code")), hidden = current.getString("code").startsWith("H1-"))
                withContext(Dispatchers.IO) {
                    synchronized(lock) {
                        if (record === current) {
                            val queue = current.optJSONArray("queue") ?: org.json.JSONArray()
                            queue.put(candidate); current.put("queue", queue); write(current)
                        }
                    }
                }
            }
            } catch(e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "Next puzzle preparation paused. Use new puzzle to retry.") }
        }
    }
    fun next(challenge: Boolean, entered: String? = null) = next(if (challenge) 'C' else 'B', entered)
    /** New puzzles always use the current quality-screened recipe; an entered code may be older. */
    fun next(recipe: Char, entered: String? = null, hidden: Boolean = false) {
        if (request?.isCompleted == false) return
        prefetch?.cancel()
        request?.cancel()
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, error = "")
            try {
                val queued = synchronized(lock) {
                    val queue = record?.optJSONArray("queue")
                    if (entered == null && queue != null && queue.length() > 0 && queue.getJSONObject(0).getString("code").startsWith("${version(hidden)}-$recipe-")) {
                        val first = queue.getJSONObject(0)
                        val remaining = org.json.JSONArray()
                        for (i in 1 until queue.length()) remaining.put(queue.getJSONObject(i))
                        first.put("queue", remaining)
                    } else null
                }
                val value = queued ?: construct(recipe, entered, hidden)
                val settings = state.value.game?.snapshotProgress()
                if (settings != null) {
                    val id = LevelPackCodec.decode(value.getString("pack")).levels.single().id
                    val progress = GameProgress(id, colorLabels = settings.colorLabels, reducedMotion = settings.reducedMotion)
                    value.put("progress", Base64.encodeToString(ProgressCodec.encode(progress), Base64.NO_WRAP))
                }
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { synchronized(lock) { record = null; write(value) } }
                    install(value)
                }
                prime()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message ?: "Generation failed. Try again.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun bookmark(quality: String, note: String, done: (String) -> Unit) {
        val progress = state.value.game?.snapshotProgress()
        val snapshot = synchronized(lock) { record?.toString() }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    synchronized(lock) {
                        val value = JSONObject(requireNotNull(snapshot)).also { it.remove("queue") }
                        if (progress != null) value.put("progress", Base64.encodeToString(ProgressCodec.encode(progress), Base64.NO_WRAP))
                        value.put("quality", quality).put("note", note)
                        val target = AtomicFile(File(root, "example-${System.currentTimeMillis()}.json"))
                        val out = target.startWrite()
                        try { out.write(value.toString().toByteArray()); target.finishWrite(out) }
                        catch(e: Exception) { target.failWrite(out); throw e }
                    }
                    "Example saved offline."
                } catch (_: Exception) { "Example could not be saved." }
            }
            done(result)
        }
    }
    fun loadSaved(text: String) {
        if (request?.isCompleted == false) return
        prefetch?.cancel()
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(busy = true, error = "")
            try {
                val value = JSONObject(text)
                require(value.getInt("version") == 1)
                require(LevelPackCodec.decode(value.getString("pack")).levels.size == 1)
                withContext(NonCancellable) {
                    withContext(Dispatchers.IO) { synchronized(lock) { record = null; write(value) } }
                    install(value)
                }; prime()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "Example could not be loaded.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun examples(): String = synchronized(lock) {
        root.listFiles().orEmpty().filter { it.name.startsWith("example-") && it.extension == "json" }
            .sortedBy { it.name }.joinToString("\n") { it.readText() }
    }
    override fun onCleared() { closed = true; request?.cancel(); prefetch?.cancel(); owner.clear(); super.onCleared() }
}
