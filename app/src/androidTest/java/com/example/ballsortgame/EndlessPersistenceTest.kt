package com.example.ballsortgame
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.*
import com.example.ballsortgame.game.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EndlessPersistenceTest {
    @Test fun seededPlayBookmarksAndResumeUsePrivateIsolatedStorage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        val folder = File(base.cacheDir, "endless-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir(): File = folder }
        var owner = ViewModelStore()
        lateinit var model: EndlessViewModel
        fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
        fun create() { main {
            model = ViewModelProvider(owner, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = EndlessViewModel(context) as T
            })[EndlessViewModel::class.java]
        } }
        fun await(test: () -> Boolean) {
            val end = System.currentTimeMillis() + 30000
            while (!test() && System.currentTimeMillis() < end) Thread.sleep(25)
            assertTrue("Timed out: ${model.state.value.error}", test())
        }
        try {
            create()
            await { !model.state.value.busy }
            main { model.next(false, "E1-B-1") }
            await { model.state.value.game?.state?.value?.loading == false && !model.state.value.busy }
            val game = requireNotNull(model.state.value.game)
            val move = game.levels.single().solution.first()
            main { game.settings(true, true); game.tapTube(move.source); game.tapTube(move.destination) }
            val board = game.state.value.board
            val latch = CountDownLatch(1)
            main { model.bookmark("good", "offline sample") { assertEquals("Rating saved.", it); latch.countDown() } }
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            val example = model.examples()
            assertTrue(example.contains("offline sample"))
            main { owner.clear() }
            owner = ViewModelStore()
            create()
            await { model.state.value.game?.state?.value?.loading == false }
            assertEquals("E1-B-1", model.state.value.code)
            assertEquals(board, model.state.value.game!!.state.value.board)
            main { model.state.value.game!!.undo() }
            assertNotEquals(board, model.state.value.game!!.state.value.board)
            main { model.next(true, "E1-C-2") }
            await { model.state.value.code == "E1-C-2" && model.state.value.game?.state?.value?.loading == false }
            assertTrue(model.state.value.game!!.state.value.colorLabels)
            assertTrue(model.state.value.game!!.state.value.reducedMotion)
            main { model.loadSaved(example) }
            await { model.state.value.code == "E1-B-1" && model.state.value.game?.state?.value?.loading == false }
            assertEquals(board, model.state.value.game!!.state.value.board)
        } finally { main { owner.clear() } }
    }
}
