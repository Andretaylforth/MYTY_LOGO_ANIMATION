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
        val added = TileLayout.add(closed, grid, TileKind.Gpu, side = { it.right <= 2 })
        assertValid(added)
        val new = added.first { it.kind == TileKind.Gpu }
        assertEquals(1 to 0, new.col to new.row)
    }

    @Test
    fun addWhenFull_makesRoomOnPreferredSide() {
        repeat(20) { seed ->
            val added = TileLayout.add(home, grid, TileKind.Network, side = { it.col >= 2 }, random = Random(seed))
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

    @Test
    fun noTileStraddlesTheMiddle() {
        assertTrue(!grid.contains(Tile(9, TileKind.Cpu, TileSize.Wide, 1, 0)))
        // Growing a small tile in column 1 to wide keeps it in the left half.
        val single = listOf(Tile(1, TileKind.Cpu, TileSize.Small, 1, 0))
        val grown = TileLayout.resize(single, grid, 1)[0]
        assertEquals(TileSize.Wide to 0, grown.size to grown.col)
        // Dragging a wide tile to column 1 snaps it to a half.
        for (c in 0..3) TileLayout.move(home, grid, bottomLeft.id, c, 2)?.let(::assertValid)
    }

    @Test
    fun gapsCoverExactlyTheEmptySpace() {
        assertEquals(emptyList<Tile>(), TileLayout.gaps(home, grid))
        val closed = TileLayout.remove(home, big.id)
        assertEquals(listOf(TileSize.Large to (2 to 0)), TileLayout.gaps(closed, grid).map { it.size to (it.col to it.row) })
        val two = TileLayout.remove(home, topMid.id)
        val gaps = TileLayout.gaps(two, grid)
        assertEquals(listOf(Tile(gaps[0].id, TileKind.Cpu, TileSize.Small, 1, 0)), gaps)
        assertTrue(gaps.all { it.id < 0 })
        // Filling a gap puts a tile of that size exactly there.
        val filled = TileLayout.fill(two, grid, TileKind.Storage, gaps[0])
        assertEquals(TileSize.Small to (1 to 0), filled.first { it.kind == TileKind.Storage }.let { it.size to (it.col to it.row) })
        assertEquals(emptyList<Tile>(), TileLayout.gaps(filled, grid))
        // An empty screen is two large gaps, one per half.
        assertEquals(2, TileLayout.gaps(emptyList(), grid).size)
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
