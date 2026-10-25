package com.myty.monitor.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Scenarios from the design brief, on the landscape grid (4 columns x 4 half-rows). */
class TileLayoutTest {
    private val grid = Grid.Landscape

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

    @Test
    fun bigTileDraggedLeft_leftTilesJumpRight() {
        val out = TileLayout.move(home, grid, big.id, 0, 0)!!
        assertValid(out)
        assertEquals(big.at(0, 0), out.byId(4))
        assertEquals(topLeft.at(2, 0), out.byId(1))
        assertEquals(topMid.at(3, 0), out.byId(2))
        assertEquals(bottomLeft.at(2, 2), out.byId(3))
    }

    @Test
    fun bottomTileDraggedRight_bigTileShrinksToTopHalf() {
        val out = TileLayout.move(home, grid, bottomLeft.id, 2, 2)!!
        assertValid(out)
        assertEquals(bottomLeft.at(2, 2), out.byId(3))
        assertEquals(big.at(2, 0, TileSize.Wide), out.byId(4))
        // The bottom-left space is left empty.
        assertEquals(topLeft, out.byId(1))
        assertEquals(topMid, out.byId(2))
    }

    @Test
    fun topLeftDraggedRight_bigShrinks_draggedGrows_neighbourFillsVoid() {
        val out = TileLayout.move(home, grid, topLeft.id, 2, 2)!!
        assertValid(out)
        assertEquals(big.at(2, 0, TileSize.Wide), out.byId(4))
        assertEquals(topLeft.at(2, 2, TileSize.Wide), out.byId(1))
        assertEquals(topMid.at(0, 0), out.byId(2))
    }

    @Test
    fun droppedOnTopHalf_bigShrinksToBottomHalf() {
        val out = TileLayout.move(home, grid, topLeft.id, 3, 0)!!
        assertValid(out)
        assertEquals(TileSize.Wide, out.byId(4).size)
        assertEquals(2, out.byId(4).row)
        assertEquals(topLeft.at(2, 0, TileSize.Wide), out.byId(1))
    }

    @Test
    fun smallTilesSwap() {
        val out = TileLayout.move(home, grid, topLeft.id, 1, 0)!!
        assertValid(out)
        assertEquals(topLeft.at(1, 0), out.byId(1))
        assertEquals(topMid.at(0, 0), out.byId(2))
    }

    @Test
    fun resizeCyclesSmallestToBiggestAndWraps() {
        val single = listOf(Tile(1, TileKind.Gpu, TileSize.Tiny, 0, 0))
        val sizes = generateSequence(single) { TileLayout.resize(it, grid, 1) }.take(5).map { it[0].size }.toList()
        assertEquals(listOf(TileSize.Tiny, TileSize.Small, TileSize.Wide, TileSize.Large, TileSize.Tiny), sizes)
    }

    @Test
    fun growingPushesNeighboursSmaller() {
        val out = TileLayout.resize(home, grid, topLeft.id) // Small -> Wide, over topMid
        assertValid(out)
        assertEquals(TileSize.Wide, out.byId(1).size)
        assertEquals(4, out.size)
    }

    @Test
    fun closeLeavesGap_addFillsIt() {
        val closed = TileLayout.remove(home, topMid.id)
        assertEquals(3, closed.size)
        val added = TileLayout.add(closed, grid, TileKind.Gpu, side = 0..1)
        assertValid(added)
        val new = added.first { it.kind == TileKind.Gpu }
        assertEquals(1 to 0, new.col to new.row)
    }

    @Test
    fun addWhenFull_makesRoomOnPreferredSide() {
        repeat(20) { seed ->
            val added = TileLayout.add(home, grid, TileKind.Network, side = 2..3, random = Random(seed))
            assertValid(added)
            assertEquals(5, added.size)
            val new = added.first { it.kind == TileKind.Network }
            assertTrue(new.col >= 2)
        }
    }

    @Test
    fun cycleStyleWraps() {
        var t = listOf(topLeft)
        val styles = (1..5).map { t[0].style.also { _ -> t = TileLayout.cycleStyle(t, 1) } }
        assertEquals(listOf(1, 2, 3, 4, 1), styles)
    }

    @Test
    fun everyDragTargetGivesAValidLayout() {
        for (id in 1L..4L) for (c in -1..4) for (r in -1..4) {
            TileLayout.move(home, grid, id, c, r)?.let(::assertValid)
        }
        assertNotNull(TileLayout.move(home, grid, 4, 0, 0))
    }
}
