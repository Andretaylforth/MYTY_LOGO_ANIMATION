package com.myty.monitor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myty.monitor.data.Stats
import com.myty.monitor.data.Target
import com.myty.monitor.layout.*
import com.myty.monitor.ui.HomeScreen
import com.myty.monitor.ui.MytyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h400dp-land", sdk = [34])
class GestureTest {
    @get:Rule val rule = createComposeRule()

    private var tiles by mutableStateOf(TileLayout.defaultLayout())
    private var accent: Color? = null

    private fun show(portrait: Boolean = false) = rule.setContent {
        val s = Stats.parse(File("src/test/sample.json").readText())
        MytyTheme {
            HomeScreen(UiState(Target("1.2.3.4", name = "PC"), s, live = true, everConnected = true), portrait, tiles,
                listOf(Color.Blue, null, null), { tiles = it }, { accent = it }, { _, _ -> }, {})
        }
    }

    // Default layout: RAM small (0,0) style 3, NET small (1,0), GPU wide (0,2), CPU large (2,0) style 2.
    private fun SemanticsNodeInteraction.at(fx: Float, fy: Float, block: TouchInjectionScope.(Offset) -> Unit) =
        performTouchInput { block(Offset(width * fx, height * fy)) }

    @Test
    fun longPressShowsCornerButtons_tapElsewhereHides() {
        show()
        rule.onRoot().at(0.75f, 0.5f) { longClick(it) }
        rule.waitForIdle()
        rule.onNodeWithText("2").assertExists() // CPU tile's style number
        rule.onRoot().at(0.1f, 0.1f) { click(it) }
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("2").assertDoesNotExist()
    }

    @Test
    fun styleButtonCycles() {
        show()
        rule.onRoot().at(0.75f, 0.5f) { longClick(it) }
        rule.waitForIdle()
        rule.onNodeWithText("2").performClick()
        rule.waitForIdle()
        assertEquals(3, tiles.first { it.kind == TileKind.Cpu }.style)
    }

    @Test
    fun dragBigTileLeft_swapsSides() {
        show()
        rule.onRoot().at(0.75f, 0.5f) { start ->
            down(start)
            advanceEventTime(1000) // long press
            moveBy(Offset(-20f, 0f))
            repeat(20) { moveBy(Offset(-width * 0.5f / 20, 0f)) }
            up()
        }
        rule.waitForIdle()
        val cpu = tiles.first { it.kind == TileKind.Cpu }
        assertEquals(0 to 0, cpu.col to cpu.row)
        assertEquals(TileSize.Large, cpu.size)
        assertTrue(tiles.first { it.kind == TileKind.Gpu }.col == 2)
        // Normal mode after letting go.
        rule.onNodeWithText("2").assertDoesNotExist()
    }

    @Test
    fun resizeButtonCycles() {
        show()
        rule.onRoot().at(0.12f, 0.25f) { longClick(it) } // RAM small
        rule.waitForIdle()
        rule.onNodeWithText("3").assertExists()
        rule.onNodeWithContentDescription("Resize tile").performClick()
        rule.waitForIdle()
        assertEquals(TileSize.Wide, tiles.first { it.kind == TileKind.Ram }.size)
    }

    @Test
    fun swipeLeftOpensRightMenu_addTile() {
        show()
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithText("MENU").assertExists()
        rule.onNodeWithText("TILES").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("STORAGE").performClick()
        rule.waitForIdle()
        val added = tiles.first { it.kind == TileKind.Storage }
        assertTrue("added on the left, got $added", added.col + added.size.w <= 2)
        assertEquals(5, tiles.size)
    }

    @Test
    fun closeButtonRemovesTile_leavesGap() {
        show()
        rule.onRoot().at(0.37f, 0.25f) { longClick(it) } // NETWORK small at col 1
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Remove tile").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(3, tiles.size)
        assertTrue(tiles.none { it.kind == TileKind.Network })
        assertEquals(TileLayout.defaultLayout().filter { it.kind != TileKind.Network }, tiles)
    }

    @Test
    fun tickButtonFinishesEditing() {
        show()
        rule.onRoot().at(0.75f, 0.5f) { longClick(it) }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Done").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("2").assertDoesNotExist()
    }

    @Test
    fun plusTileFillsTheEmptySpace() {
        tiles = tiles.filter { it.kind != TileKind.Network } // leaves a small gap at column 1
        show()
        rule.onNodeWithContentDescription("Add a tile here").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("PROCESSES").performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        val added = tiles.first { it.kind == TileKind.Processes }
        assertEquals(Tile(added.id, TileKind.Processes, TileSize.Small, 1, 0), added)
        rule.onNodeWithContentDescription("Add a tile here").assertDoesNotExist()
        rule.onNodeWithText("TILES").assertDoesNotExist() // menu closed
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h800dp-port", sdk = [34])
class PortraitGestureTest {
    @get:Rule val rule = createComposeRule()
    private var tiles by mutableStateOf(TileLayout.defaultLayout())

    @Test
    fun rightHalfSitsBelowLeftHalf() {
        rule.setContent {
            val s = Stats.parse(File("src/test/sample.json").readText())
            MytyTheme {
                HomeScreen(UiState(Target("1.2.3.4", name = "PC"), s, live = true, everConnected = true), true, tiles,
                    listOf(null, null, null), { tiles = it }, {}, { _, _ -> }, {})
            }
        }
        // The large CPU tile (right half in landscape) fills the bottom half in portrait.
        rule.onRoot().performTouchInput { longClick(Offset(width * 0.5f, height * 0.75f)) }
        rule.waitForIdle()
        rule.onNodeWithText("2").assertExists()
        // Dragging it to the top swaps the halves, just like dragging it left in landscape.
        rule.onNodeWithContentDescription("Done").performClick()
        rule.onRoot().performTouchInput {
            down(Offset(width * 0.5f, height * 0.75f))
            advanceEventTime(1000)
            moveBy(Offset(0f, -20f))
            repeat(20) { moveBy(Offset(0f, -height * 0.5f / 20)) }
            up()
        }
        rule.waitForIdle()
        val cpu = tiles.first { it.kind == TileKind.Cpu }
        assertEquals(0 to 0, cpu.col to cpu.row)
    }
}
