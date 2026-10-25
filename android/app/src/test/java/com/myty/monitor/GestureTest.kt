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

    private var tiles by mutableStateOf(TileLayout.defaultLayout(Grid.Landscape))
    private var accent: Color? = null

    private fun show() = rule.setContent {
        val s = Stats.parse(File("src/test/sample.json").readText())
        MytyTheme {
            HomeScreen(UiState(Target("1.2.3.4", name = "PC"), s, live = true, everConnected = true), Grid.Landscape, tiles,
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
        // Resize is bottom-right of the RAM tile; find it by position: tile spans x 0..~25%, y 0..~50%.
        rule.onRoot().at(0.215f, 0.43f) { click(it) }
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
        rule.onRoot().at(0.47f, 0.07f) { click(it) } // its top-right X
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        assertEquals(3, tiles.size)
        assertTrue(tiles.none { it.kind == TileKind.Network })
        assertEquals(TileLayout.defaultLayout(Grid.Landscape).filter { it.kind != TileKind.Network }, tiles)
    }
}
