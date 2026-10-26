package com.myty.monitor.layout

import kotlin.math.abs
import kotlin.random.Random

/*
 * Live-tile layout, in the spirit of the Windows Phone start screen.
 *
 * There's one layout: a grid of 4 columns x 4 half-rows, split down the middle into two 2-column halves.
 * Landscape shows the halves side by side; portrait stacks the right half under the left one, so every
 * tile keeps its place when the phone rotates. That's why no tile may straddle the middle line.
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

data class Grid(val cols: Int, val rows: Int, val half: Int = cols) {
    private fun straddles(col: Int, w: Int) = col < half && col + w > half

    fun contains(t: Tile) =
        t.col >= 0 && t.row >= 0 && t.right <= cols && t.bottom <= rows && !straddles(t.col, t.size.w)

    /** Moves [t] back inside the grid (and off the middle line) if it hangs over an edge. */
    fun clamp(t: Tile): Tile {
        var col = t.col.coerceIn(0, maxOf(0, cols - t.size.w))
        val row = t.row.coerceIn(0, maxOf(0, rows - t.size.h))
        if (straddles(col, t.size.w)) col = if (col + t.size.w / 2.0 <= half) half - t.size.w else half
        return t.at(col, row)
    }

    fun canFit(s: TileSize) = s.w <= minOf(half, cols) && s.h <= rows

    /** Where [t] sits on screen: unchanged in landscape; in portrait the right half moves below the left. */
    fun viewCell(t: Tile, portrait: Boolean): Pair<Int, Int> =
        if (!portrait) t.col to t.row else t.col % half to t.row + (t.col / half) * rows

    /** Inverse of [viewCell] for a drop target. */
    fun modelCell(viewCol: Int, viewRow: Int, portrait: Boolean): Pair<Int, Int> =
        if (!portrait) viewCol to viewRow
        else viewCol.coerceIn(0, half - 1) + (if (viewRow >= rows) half else 0) to viewRow.mod(rows)

    fun viewCols(portrait: Boolean) = if (portrait) half else cols
    fun viewRows(portrait: Boolean) = if (portrait) rows * (cols / half) else rows

    companion object {
        val Main = Grid(4, 4, half = 2)
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
    fun add(tiles: List<Tile>, grid: Grid, kind: TileKind, side: (Tile) -> Boolean, random: Random = Random.Default): List<Tile> {
        val id = (tiles.maxOfOrNull { it.id } ?: 0) + 1
        val sizes = listOf(TileSize.Wide, TileSize.Small, TileSize.Tiny).filter { grid.canFit(it) }

        // 1. An empty gap: biggest size that fits (up to Wide), on the preferred side first.
        for (preferSide in listOf(true, false)) {
            for (s in sizes) {
                val spot = positions(grid, s)
                    .map { (c, r) -> Tile(id, kind, s, c, r) }
                    .firstOrNull { (!preferSide || side(it)) && isFree(it, tiles, grid) }
                if (spot != null) return tiles + spot
            }
        }

        // 2. No gap: make room on the preferred side.
        for (s in listOf(TileSize.Small, TileSize.Tiny)) {
            val spots = positions(grid, s).map { (c, r) -> Tile(id, kind, s, c, r) }
                .filter { grid.contains(it) && side(it) }.shuffled(random)
            for (t in spots) {
                place(tiles, grid, t, vacated = null, growIntoFreedSpace = false)?.let { return it }
            }
        }
        return tiles
    }

    /** Fills an empty space shown as a "+" tile with a new tile of that size. */
    fun fill(tiles: List<Tile>, grid: Grid, kind: TileKind, gap: Tile): List<Tile> {
        val id = (tiles.maxOfOrNull { it.id } ?: 0) + 1
        val t = Tile(id, kind, gap.size, gap.col, gap.row)
        return if (isFree(t, tiles, grid)) tiles + t else add(tiles, grid, kind, side = { true })
    }

    /**
     * Empty space, covered with as few placeholder tiles as possible (biggest first, top-left first).
     * Placeholder ids are negative and stable for a given spot and size.
     */
    fun gaps(tiles: List<Tile>, grid: Grid): List<Tile> {
        val taken = tiles.toMutableList()
        val out = mutableListOf<Tile>()
        for (r in 0 until grid.rows) for (c in 0 until grid.cols) {
            val cell = Tile(0, TileKind.Cpu, TileSize.Tiny, c, r)
            if (!isFree(cell, taken, grid)) continue
            val s = TileSize.entries.reversed().first { isFree(cell.at(c, r, it), taken, grid) }
            val gap = Tile(-(1L + c + r * grid.cols + s.ordinal * 100L), TileKind.Cpu, s, c, r)
            out += gap
            taken += gap
        }
        return out
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

    fun defaultLayout(): List<Tile> = listOf(
        Tile(1, TileKind.Ram, TileSize.Small, 0, 0, style = 3),
        Tile(2, TileKind.Network, TileSize.Small, 1, 0),
        Tile(3, TileKind.Gpu, TileSize.Wide, 0, 2, style = 1),
        Tile(4, TileKind.Cpu, TileSize.Large, 2, 0, style = 2),
    )
}
