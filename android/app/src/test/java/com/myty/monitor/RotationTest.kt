package com.myty.monitor

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rotating the phone keeps every tile, its style and its place (right half stacked under the left). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h400dp-land", sdk = [34])
class RotationTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Before
    fun connectedBefore() {
        // Pretend the app was connected to a PC before, so it opens straight onto the tiles.
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("myty", Context.MODE_PRIVATE)
            .edit().clear().putString("host", "127.0.0.1").putInt("port", 1).commit()
    }

    private val tiles = listOf("RAM tile, style 3, small", "NETWORK tile, style 1, small", "GPU tile, style 1, wide", "CPU tile, style 2, large")

    private fun bounds(): Map<String, DpRect> = tiles.associateWith { d ->
        val n: SemanticsNodeInteraction = rule.onNodeWithContentDescription(d, useUnmergedTree = true)
        n.getBoundsInRoot()
    }

    @Test
    fun rotateKeepsLayout() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rule.waitForIdle()
            val land = bounds()
            // Landscape: RAM | NET | CPU(large) on top, GPU under RAM+NET.
            assertTrue(land.getValue(tiles[3]).left > land.getValue(tiles[1]).right)

            RuntimeEnvironment.setQualifiers("w400dp-h800dp-port")
            scenario.recreate()
            rule.mainClock.advanceTimeBy(2000)
            rule.waitForIdle()
            val port = bounds()
            val ram = port.getValue(tiles[0]); val net = port.getValue(tiles[1])
            val gpu = port.getValue(tiles[2]); val cpu = port.getValue(tiles[3])
            // Portrait: RAM | NET, then GPU, then CPU underneath; styles unchanged (found by description).
            assertTrue("net right of ram", net.left > ram.left && kotlin.math.abs(net.top.value - ram.top.value) < 1)
            assertTrue("gpu below ram", gpu.top > ram.bottom)
            assertTrue("cpu below gpu", cpu.top > gpu.bottom)
            assertEquals(4, tiles.count { rule.onAllNodesWithContentDescription(it, useUnmergedTree = true).fetchSemanticsNodes().size == 1 })

            RuntimeEnvironment.setQualifiers("w800dp-h400dp-land")
            scenario.recreate()
            rule.mainClock.advanceTimeBy(2000)
            rule.waitForIdle()
            val back = bounds()
            for (t in tiles) assertEquals(t, land.getValue(t), back.getValue(t))
        }
    }
}
