package com.myty.monitor.layout

import kotlin.math.abs
import kotlin.random.Random

/*
 * Live-tile layout, in the spirit of the Windows Phone start screen.
 *
 * The screen is a fixed grid of cells: 4 columns x 4 half-rows in landscape, 2 x 8 in portrait.
 * Tiles come in four sizes measured in cells. Gaps are allowed (closing a tile leaves one).
 * Everything here is plain Kotlin so it can be unit tested without a device.
 */

enum class TileSize(val w: Int, val h: Int) {
    Tiny(1, 1), Small(1, 2), Wide(2, 2), Large(2, 4);

    /** The resize button cycles smallest → biggest → smallest. */
    fun next(): TileSize = entries[(ordinal + 1) % entries.size]
}

enum class TileKind(val label: String, val styles: Int) {
    Cpu("CPU", 4), Gpu("GPU", 4), Ram("RAM", 4), Network("NETWORK", 1), Storage("STORAGE", 1), Processes("PROCESSES", 1),
}

data class Tile(
    val id: Long,
    val kind: TileKind,
    val size: TileSize,
    val col: Int,
    val row: Int,
    /** 1..kind.styles: ring, fill, orbit, dot grid. */
    val style: Int = 1,
) {
    val right get() = col + size.w
    val bottom get() = row + size.h

    fun overlaps(o: Tile) = col < o.right && o.col < right && row < o.bottom && o.row < bottom
    fun at(c: Int, r: Int, s: TileSize = size) = copy(col = c, row = r, size = s)
}

data class Grid(val cols: Int, val rows: Int) {
    fun contains(t: Tile) = t.col >= 0 && t.row >= 0 && t.right <= cols && t.bottom <= rows

    /** Moves [t] back inside the grid if it hangs off an edge. */
    fun clamp(t: Tile) = t.at(t.col.coerceIn(0, maxOf(0, cols - t.size.w)), t.row.coerceIn(0, maxOf(0, rows - t.size.h)))

    fun canFit(s: TileSize) = s.w <= cols && s.h <= rows

    companion object {
        val Landscape = Grid(4, 4)
        val Portrait = Grid(2, 8)
    }
}

object TileLayout {

    fun isFree(t: Tile, placed: List<Tile>, grid: Grid) = grid.contains(t) && placed.none { it.id != t.id && it.overlaps(t) }

    /**
     * Drag [id] so its top-left lands on ([col], [row]).
     * Tiles in the way first try to jump into the space the dragged tile left (keeping their size and
     * arrangement), otherwise they shrink to make room. Tiles beside the vacated space slide over to fill it.
     * Returns null if there's no way to make it fit.
     */
    fun move(tiles: List<Tile>, grid: Grid, id: Long, col: Int, row: Int): List<Tile>? {
        val old = tiles.firstOrNull { it.id == id } ?: return null
        val moved = grid.clamp(old.at(col, row))
        if (moved.col == old.col && moved.row == old.row) return tiles
        return place(tiles.filter { it.id != id }, grid, moved, vacated = old, growIntoFreedSpace = true)
    }

    /** Next size in the cycle that can be made to fit; neighbours shrink or move aside as needed. */
    fun resize(tiles: List<Tile>, grid: Grid, id: Long): List<Tile> {
        val old = tiles.firstOrNull { it.id == id } ?: return tiles
        var size = old.size.next()
        repeat(TileSize.entries.size - 1) {
            if (grid.canFit(size)) {
                val resized = grid.clamp(old.at(old.col, old.row, size))
                place(tiles.filter { it.id != id }, grid, resized, vacated = null, growIntoFreedSpace = false)
                    ?.let { return it }
            }
            size = size.next()
        }
        return tiles
    }

    fun remove(tiles: List<Tile>, id: Long) = tiles.filter { it.id != id }

    fun cycleStyle(tiles: List<Tile>, id: Long) = tiles.map {
        if (it.id == id) it.copy(style = it.style % it.kind.styles + 1) else it
    }

    /**
     * Adds a tile. It goes into an empty gap if there is one (preferring [side]); otherwise into a random
     * spot on [side], shrinking whatever is there to make room.
     * [side]: columns to prefer, e.g. the half of the screen the menu isn't covering.
     */
    fun add(tiles: List<Tile>, grid: Grid, kind: TileKind, side: IntRange, random: Random = Random.Default): List<Tile> {
        val id = (tiles.maxOfOrNull { it.id } ?: 0) + 1
        val sizes = listOf(TileSize.Wide, TileSize.Small, TileSize.Tiny).filter { grid.canFit(it) }

        // 1. An empty gap: biggest size that fits (up to Wide), on the preferred side first.
        for (preferSide in listOf(true, false)) {
            for (s in sizes) {
                val spot = positions(grid, s)
                    .filter { (c, _) -> !preferSide || c in side && c + s.w - 1 in side }
                    .map { (c, r) -> Tile(id, kind, s, c, r) }
                    .firstOrNull { isFree(it, tiles, grid) }
                if (spot != null) return tiles + spot
            }
        }

        // 2. No gap: make room on the preferred side.
        for (s in listOf(TileSize.Small, TileSize.Tiny)) {
            val spots = positions(grid, s).filter { (c, _) -> c in side && c + s.w - 1 in side }.shuffled(random)
            for ((c, r) in spots) {
                place(tiles, grid, Tile(id, kind, s, c, r), vacated = null, growIntoFreedSpace = false)?.let { return it }
            }
        }
        return tiles
    }

    // ---------------------------------------------------------------------------------------------

    private fun positions(grid: Grid, s: TileSize) =
        (0..grid.rows - s.h).flatMap { r -> (0..grid.cols - s.w).map { c -> c to r } }

    private fun place(others: List<Tile>, grid: Grid, d: Tile, vacated: Tile?, growIntoFreedSpace: Boolean): List<Tile>? {
        val displaced = others.filter { it.overlaps(d) }
            .sortedWith(compareByDescending<Tile> { it.size.w * it.size.h }.thenBy { it.row }.thenBy { it.col })
        val placed = (others - displaced.toSet()).toMutableList().apply { add(d) }
        // Displaced tiles shift the opposite way to the drag, into the space it left.
        val dx = if (vacated != null) vacated.col - d.col else 0
        val dy = if (vacated != null) vacated.row - d.row else 0
        var shrunk = false

        for (t in displaced) {
            val goal = t.col + dx to t.row + dy
            val spot = sameSizeSpot(t, goal, placed, grid)
                ?: shrinkInPlace(t, placed, grid)?.also { shrunk = true }
                ?: shrinkAnywhere(t, goal, placed, grid)?.also { shrunk = true }
                ?: squeeze(t, goal, placed, grid, d.id)?.also { shrunk = true }
                ?: return null
            placed += spot
        }

        // A small tile dropped where a big one shrank grows sideways to fill the space.
        if (growIntoFreedSpace && shrunk && d.size == TileSize.Small) {
            val wide = listOf(d.at(d.col, d.row, TileSize.Wide), d.at(d.col - 1, d.row, TileSize.Wide))
                .firstOrNull { isFree(it, placed, grid) }
            if (wide != null) placed[placed.indexOfFirst { it.id == d.id }] = wide
        }

        if (vacated != null) fillVoid(placed, grid, vacated, d.id)
        return placed
    }

    private fun sameSizeSpot(t: Tile, goal: Pair<Int, Int>, placed: List<Tile>, grid: Grid): Tile? {
        val exact = t.at(goal.first, goal.second)
        if (isFree(exact, placed, grid)) return exact
        return positions(grid, t.size)
            .map { (c, r) -> t.at(c, r) }
            .filter { isFree(it, placed, grid) }
            .minByOrNull { abs(it.col - goal.first) + abs(it.row - goal.second) }
    }

    /** Keep a corner of the tile where it was and shrink towards it (e.g. a Large tile halves vertically). */
    private fun shrinkInPlace(t: Tile, placed: List<Tile>, grid: Grid): Tile? {
        for (s in TileSize.entries.filter { it.ordinal < t.size.ordinal }.reversed()) {
            val anchors = listOf(
                t.col to t.row,
                t.col to t.bottom - s.h,
                t.right - s.w to t.row,
                t.right - s.w to t.bottom - s.h,
            )
            anchors.map { (c, r) -> t.at(c, r, s) }.firstOrNull { isFree(it, placed, grid) }?.let { return it }
        }
        return null
    }

    private fun shrinkAnywhere(t: Tile, goal: Pair<Int, Int>, placed: List<Tile>, grid: Grid): Tile? {
        for (s in TileSize.entries.filter { it.ordinal < t.size.ordinal }.reversed()) {
            sameSizeSpot(t.copy(size = s), goal, placed, grid)?.let { return it }
        }
        return null
    }

    /** Last resort when the grid is full: shrink another tile (biggest first) so [t] fits somewhere. */
    private fun squeeze(t: Tile, goal: Pair<Int, Int>, placed: MutableList<Tile>, grid: Grid, keepId: Long): Tile? {
        val victims = placed.filter { it.id != keepId && it.size != TileSize.Tiny }.sortedByDescending { it.size.w * it.size.h }
        for (v in victims) {
            val i = placed.indexOf(v)
            for (s in TileSize.entries.filter { it.ordinal < v.size.ordinal }.reversed()) {
                placed[i] = v.at(v.col, v.row, s)
                (sameSizeSpot(t, goal, placed, grid) ?: shrinkAnywhere(t, goal, placed, grid))?.let { return it }
            }
            placed[i] = v
        }
        return null
    }

    /** Tiles beside the space a dragged tile left slide over to close it up. */
    private fun fillVoid(placed: MutableList<Tile>, grid: Grid, vacated: Tile, draggedId: Long) {
        val candidates = placed
            .filter { it.id != draggedId && it.col >= vacated.right && it.row < vacated.bottom && vacated.row < it.bottom }
            .sortedBy { it.col }
        for (t in candidates) {
            var slid = t
            while (true) {
                val next = slid.at(slid.col - 1, slid.row)
                if (!isFree(next, placed, grid)) break
                slid = next
            }
            if (slid != t && slid.overlaps(vacated)) placed[placed.indexOfFirst { it.id == t.id }] = slid
        }
    }

    fun defaultLayout(grid: Grid): List<Tile> = if (grid.cols >= 4) listOf(
        Tile(1, TileKind.Ram, TileSize.Small, 0, 0, style = 3),
        Tile(2, TileKind.Network, TileSize.Small, 1, 0),
        Tile(3, TileKind.Gpu, TileSize.Wide, 0, 2, style = 1),
        Tile(4, TileKind.Cpu, TileSize.Large, 2, 0, style = 2),
    ) else listOf(
        Tile(1, TileKind.Cpu, TileSize.Large, 0, 0, style = 2),
        Tile(2, TileKind.Gpu, TileSize.Wide, 0, 4, style = 1),
        Tile(3, TileKind.Ram, TileSize.Small, 0, 6, style = 3),
        Tile(4, TileKind.Network, TileSize.Small, 1, 6),
    )
}
