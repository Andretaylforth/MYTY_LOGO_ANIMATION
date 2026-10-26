package com.myty.monitor.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Scenarios from the design brief, on the grid (4 columns x 4 half-rows, split into two halves). */
class TileLayoutTest {
    private val grid = Grid.Main

    // Screenshot 1: two small tiles top-left, a wide tile under them, a large tile on the right.
    private val topLeft = Tile(1, TileKind.Cpu, TileSize.Small, 0, 0)
    private val topMid = Tile(2, TileKind.Cpu, TileSize.Small, 1, 0)
    private val bottomLeft = Tile(3, TileKind.Cpu, TileSize.Wide, 0, 2)
    private val big = Tile(4, TileKind.Cpu, TileSize.Large, 2, 0)
    private val home = listOf(topLeft, topMid, bottomLeft, big)

    private fun List<Tile>.byId(id: Long) = first { it.id == id }

    private fun assertValid(tiles: List<Tile>) {
        tiles.forEach { assertTrue("$it off grid", grid.contains(it)) }
        for (a in tiles) for (b in tiles) if (a.id != b.id) assertTrue("$a overlaps $b", !a.overlaps(b))
    }

    private fun assertFull(tiles: List<Tile>) {
        assertValid(tiles)
        assertTrue("not full: $tiles", TileLayout.isFull(tiles, grid))
    }

    @Test
    fun bigTileDraggedLeft_leftTilesJumpRight() {
        val out = TileLayout.move(home, grid, big.id, 0, 0)!!
        assertFull(out)
        assertEquals(big.at(0, 0), out.byId(4))
        assertEquals(topLeft.at(2, 0), out.byId(1))
        assertEquals(topMid.at(3, 0), out.byId(2))
        assertEquals(bottomLeft.at(2, 2), out.byId(3))
    }

    @Test
    fun bottomTileDraggedRight_bigShrinks_leftTilesGrowIntoTheSpace() {
        val out = TileLayout.move(home, grid, bottomLeft.id, 2, 2)!!
        assertFull(out)
        assertEquals(bottomLeft.at(2, 2), out.byId(3))
        assertEquals(big.at(2, 0, TileSize.Wide), out.byId(4))
        // The two small tiles grow to fill the left half instead of leaving a hole.
        assertEquals(listOf(TileSize.Wide, TileSize.Wide), listOf(out.byId(1).size, out.byId(2).size))
    }

    @Test
    fun topLeftDraggedRight_bigShrinks_draggedGrows_neighbourFillsVoid() {
        val out = TileLayout.move(home, grid, topLeft.id, 2, 2)!!
        assertFull(out)
        assertEquals(big.at(2, 0, TileSize.Wide), out.byId(4))
        assertEquals(topLeft.at(2, 2, TileSize.Wide), out.byId(1))
        assertEquals(0 to 0, out.byId(2).let { it.col to it.row })
    }

    @Test
    fun droppedOnTopHalf_bigShrinksToBottomHalf() {
        val out = TileLayout.move(home, grid, topLeft.id, 3, 0)!!
        assertFull(out)
        assertEquals(TileSize.Wide, out.byId(4).size)
        assertEquals(2, out.byId(4).row)
        assertEquals(topLeft.at(2, 0, TileSize.Wide), out.byId(1))
    }

    @Test
    fun smallTilesSwap() {
        val out = TileLayout.move(home, grid, topLeft.id, 1, 0)!!
        assertFull(out)
        assertEquals(topLeft.at(1, 0), out.byId(1))
        assertEquals(topMid.at(0, 0), out.byId(2))
    }

    @Test
    fun resizeCyclesSmallestToBiggestAndKeepsTheScreenFull() {
        var t = home
        val sizes = mutableListOf(t.byId(1).size)
        repeat(4) { t = TileLayout.resize(t, grid, 1); assertFull(t); sizes += t.byId(1).size }
        // Sizes that can't leave a full screen are skipped (here tiny: four tiles can't tile it).
        assertEquals(listOf(TileSize.Small, TileSize.Wide, TileSize.Large, TileSize.Small, TileSize.Wide), sizes)
    }

    @Test
    fun shrinkingALoneTilePullsOthersIn() {
        // The large tile is alone in its half; shrinking it brings tiles over from the other half.
        val out = TileLayout.resize(home, grid, big.id) // Large -> Tiny if possible, else the next size that works
        assertFull(out)
        assertTrue(out.byId(4).size != TileSize.Large)
    }

    @Test
    fun growingPushesNeighboursSmaller() {
        val out = TileLayout.resize(home, grid, topLeft.id) // Small -> Wide, over topMid
        assertFull(out)
        assertEquals(TileSize.Wide, out.byId(1).size)
        assertEquals(4, out.size)
    }

    @Test
    fun closingATileFillsItsSpace() {
        for (t in home) {
            val out = TileLayout.remove(home, grid, t.id)
            assertEquals(3, out.size)
            assertFull(out)
        }
    }

    @Test
    fun addWhenFull_makesRoomOnPreferredSide() {
        repeat(20) { seed ->
            val added = TileLayout.add(home, grid, TileKind.Network, side = { it.col >= 2 }, random = Random(seed))
            assertFull(added)
            assertEquals(5, added.size)
            val new = added.first { it.id == 5L }
            assertTrue(new.col >= 2)
        }
    }

    @Test
    fun cycleStyleWraps() {
        var t = listOf(topLeft)
        val styles = (1..7).map { t[0].style.also { _ -> t = TileLayout.cycleStyle(t, 1) } }
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 1), styles) // CPU has six styles
    }

    @Test
    fun everyDragTargetGivesAFullLayout() {
        for (id in 1L..4L) for (c in -1..4) for (r in -1..4) {
            TileLayout.move(home, grid, id, c, r)?.let(::assertFull)
        }
        assertNotNull(TileLayout.move(home, grid, 4, 0, 0))
    }

    @Test
    fun randomEditsAlwaysLeaveTheScreenFull() {
        val rnd = Random(42)
        var tiles = home
        repeat(3000) {
            val t = tiles.random(rnd)
            val op = rnd.nextInt(4)
            val before = tiles
            tiles = when (op) {
                0 -> TileLayout.move(tiles, grid, t.id, rnd.nextInt(4), rnd.nextInt(4)) ?: tiles
                1 -> TileLayout.resize(tiles, grid, t.id)
                2 -> if (tiles.size > 2) TileLayout.remove(tiles, grid, t.id) else tiles
                else -> if (tiles.size < 9) TileLayout.add(tiles, grid, TileKind.entries.random(rnd), { true }, rnd) else tiles
            }
            assertTrue("op $op on ${t.id} from $before", TileLayout.isFull(tiles, grid))
        }
    }

    @Test
    fun noTileStraddlesTheMiddle() {
        assertTrue(!grid.contains(Tile(9, TileKind.Cpu, TileSize.Wide, 1, 0)))
        // Growing the small tile in column 1 to wide keeps it in the left half.
        val grown = TileLayout.resize(home, grid, topMid.id).byId(2)
        assertEquals(TileSize.Wide to 0, grown.size to grown.col)
        for (c in 0..3) TileLayout.move(home, grid, bottomLeft.id, c, 2)?.let(::assertFull)
    }

    @Test
    fun toggleAltFlipsOnlyThatTile() {
        val out = TileLayout.toggleAlt(home, big.id)
        assertTrue(out.byId(4).alt)
        assertTrue(out.filter { it.id != 4L }.none { it.alt })
    }

    @Test
    fun portraitStacksTheRightHalfUnderTheLeft() {
        assertEquals(0 to 0, grid.viewCell(topLeft, portrait = true))
        assertEquals(1 to 0, grid.viewCell(topMid, portrait = true))
        assertEquals(0 to 2, grid.viewCell(bottomLeft, portrait = true))
        assertEquals(0 to 4, grid.viewCell(big, portrait = true))
        for (t in home) {
            val (vc, vr) = grid.viewCell(t, portrait = true)
            assertEquals(t.col to t.row, grid.modelCell(vc, vr, portrait = true))
        }
        assertEquals(2 to 4, grid.viewCols(true) to grid.viewRows(false))
        assertEquals(8, grid.viewRows(true))
    }
}
