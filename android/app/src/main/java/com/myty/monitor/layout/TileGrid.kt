package com.myty.monitor.layout

import kotlin.math.abs
import kotlin.random.Random

/*
 * Live-tile layout, in the spirit of the Windows Phone start screen.
 *
 * There's one layout: a grid of 4 columns x 4 half-rows, split down the middle into two 2-column halves.
 * Landscape shows the halves side by side; portrait stacks the right half under the left one, so every
 * tile keeps its place when the phone rotates. That's why no tile may straddle the middle line.
 * Tiles come in four sizes measured in cells, and the screen is always kept full: whenever a tile is
 * closed, shrunk, moved or added, the others grow, shrink or shift to fill the space (see [TileLayout.fill]).
 * Everything here is plain Kotlin so it can be unit tested without a device.
 */

enum class TileSize(val w: Int, val h: Int) {
    Tiny(1, 1), Small(1, 2), Wide(2, 2), Large(2, 4);

    /** The resize button cycles smallest → biggest → smallest. */
    fun next(): TileSize = entries[(ordinal + 1) % entries.size]
}

enum class TileKind(val label: String, val styles: Int) {
    Cpu("CPU", 6), Gpu("GPU", 5), Ram("RAM", 4), Network("NETWORK", 1), Storage("STORAGE", 1), Processes("PROCESSES", 1),
}

data class Tile(
    val id: Long,
    val kind: TileKind,
    val size: TileSize,
    val col: Int,
    val row: Int,
    /** 1..kind.styles: ring, fill, orbit, dot grid, block, per-core bars. */
    val style: Int = 1,
    /** Showing the alternate reading (e.g. load instead of temperature); a single tap toggles it. */
    val alt: Boolean = false,
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
        val placed = place(tiles.filter { it.id != id }, grid, moved, vacated = old, growIntoFreedSpace = true) ?: return null
        val dropped = placed.first { it.id == id }
        // A drop spot that can't be part of a full screen is refused (the preview stays where it was).
        return fill(placed, grid, pinned = dropped)
    }

    /** Next size in the cycle that the screen can be rearranged around; neighbours shrink, grow or move as needed. */
    fun resize(tiles: List<Tile>, grid: Grid, id: Long): List<Tile> {
        val old = tiles.firstOrNull { it.id == id } ?: return tiles
        var size = old.size.next()
        repeat(TileSize.entries.size - 1) {
            if (grid.canFit(size)) {
                val resized = grid.clamp(old.at(old.col, old.row, size))
                val placed = place(tiles.filter { it.id != id }, grid, resized, vacated = null, growIntoFreedSpace = false)
                if (placed != null) fill(placed, grid, pinned = resized)?.let { return it }
            }
            size = size.next()
        }
        return tiles
    }

    /** Removes a tile; the others spread out to fill its space. */
    fun remove(tiles: List<Tile>, grid: Grid, id: Long): List<Tile> {
        val rest = tiles.filter { it.id != id }
        return fill(rest, grid) ?: rest
    }

    fun cycleStyle(tiles: List<Tile>, id: Long) = tiles.map {
        if (it.id == id) it.copy(style = it.style % it.kind.styles + 1) else it
    }

    fun toggleAlt(tiles: List<Tile>, id: Long) = tiles.map { if (it.id == id) it.copy(alt = !it.alt) else it }

    /**
     * Adds a tile at a random spot on [side] (e.g. the half of the screen the menu isn't covering);
     * the other tiles shrink or move to make room.
     */
    fun add(tiles: List<Tile>, grid: Grid, kind: TileKind, side: (Tile) -> Boolean, random: Random = Random.Default): List<Tile> {
        val id = (tiles.maxOfOrNull { it.id } ?: 0) + 1
        for (s in listOf(TileSize.Small, TileSize.Tiny)) {
            val spots = positions(grid, s).map { (c, r) -> Tile(id, kind, s, c, r) }
                .filter { grid.contains(it) && side(it) }.shuffled(random)
            for (t in spots) {
                val placed = place(tiles, grid, t, vacated = null, growIntoFreedSpace = false) ?: continue
                fill(placed, grid, pinned = t)?.let { return it }
            }
        }
        return tiles
    }

    /** True if every cell is covered. */
    fun isFull(tiles: List<Tile>, grid: Grid) = tiles.sumOf { it.size.w * it.size.h } == grid.cols * grid.rows &&
        tiles.all { grid.contains(it) } && tiles.none { a -> tiles.any { b -> a.id != b.id && a.overlaps(b) } }

    /**
     * Rearranges [tiles] so they cover the whole screen, changing as little as possible: tiles keep their
     * size and place where they can, otherwise grow (preferred), shrink, or move — even to the other half.
     * [pinned] keeps its exact size and position (the tile the user just moved, resized or added).
     * Returns null if no arrangement exists (e.g. more tiles than cells).
     */
    fun fill(tiles: List<Tile>, grid: Grid, pinned: Tile? = null): List<Tile>? {
        if (isFull(tiles, grid)) return tiles
        val free = tiles.filter { it.id != pinned?.id }
        if (free.size > 20) return null
        val cells = grid.cols * grid.rows
        // Cells in reading order: the left half row by row, then the right half (also portrait's order).
        val order = (0 until cells / grid.half / grid.rows).flatMap { h ->
            (0 until grid.rows).flatMap { r -> (0 until grid.half).map { c -> h * grid.half + c to r } }
        }
        fun bit(c: Int, r: Int) = 1L shl (r * grid.cols + c)
        fun mask(t: Tile): Long {
            var m = 0L
            for (r in t.row until t.bottom) for (c in t.col until t.right) m = m or bit(c, r)
            return m
        }
        val full = (1L shl cells) - 1
        val start = pinned?.let { if (grid.contains(it)) mask(it) else return null } ?: 0L

        // Cost of turning tile t into t2: growing is cheap, shrinking dearer, moving a little, changing halves more.
        fun cost(t: Tile, t2: Tile): Int {
            val da = t2.size.w * t2.size.h - t.size.w * t.size.h
            val sizeCost = if (da >= 0) da * 3 else -da * 5
            val halfCost = if (t.col / grid.half != t2.col / grid.half) 6 else 0
            return sizeCost + abs(t.col - t2.col) + abs(t.row - t2.row) + halfCost
        }

        val seen = HashMap<Long, Int>()
        var best: List<Tile>? = null
        var bestCost = Int.MAX_VALUE
        var nodes = 0
        val chosen = arrayOfNulls<Tile>(free.size)

        fun search(occ: Long, remaining: Int, spent: Int) {
            if (spent >= bestCost || ++nodes > 400_000) return
            val key = occ * 1_048_576 + remaining
            if ((seen[key] ?: Int.MAX_VALUE) <= spent) return
            seen[key] = spent
            if (occ == full) {
                if (remaining == 0) { best = chosen.filterNotNull() + listOfNotNull(pinned); bestCost = spent }
                return
            }
            if (remaining == 0) return
            val (c, r) = order.first { (c, r) -> occ and bit(c, r) == 0L }
            for (i in free.indices) {
                if (remaining and (1 shl i) == 0) continue
                val t = free[i]
                for (size in TileSize.entries) {
                    val t2 = t.at(c, r, size)
                    if (!grid.contains(t2)) continue
                    val m = mask(t2)
                    if (occ and m != 0L) continue
                    chosen[i] = t2
                    search(occ or m, remaining and (1 shl i).inv(), spent + cost(t, t2))
                    chosen[i] = null
                }
            }
        }
        search(start, (1 shl free.size) - 1, 0)
        // Keep the original list order so ids/keys stay stable for the UI.
        val result = best ?: return null
        return tiles.map { t -> result.first { it.id == t.id } }
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
