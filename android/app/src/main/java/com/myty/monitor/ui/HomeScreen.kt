package com.myty.monitor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.myty.monitor.UiState
import com.myty.monitor.layout.Grid
import com.myty.monitor.layout.Tile
import com.myty.monitor.layout.TileLayout
import com.myty.monitor.layout.TileSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Where the menu slides in from: left/right in landscape, top/bottom in portrait. */
enum class MenuSide { Left, Right, Top, Bottom }

private class DragState(val id: Long, val start: List<Tile>, val origin: Offset) {
    var delta by mutableStateOf(Offset.Zero)
    var moving by mutableStateOf(false)
    var preview by mutableStateOf(start)
    var cell: Pair<Int, Int>? = null
}

private val tileSpring = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

@Composable
fun HomeScreen(
    state: UiState,
    portrait: Boolean,
    tiles: List<Tile>,
    customColors: List<Color?>,
    onTiles: (List<Tile>) -> Unit,
    onAccent: (Color) -> Unit,
    onCustomColor: (Int, Color) -> Unit,
    onChangePc: () -> Unit,
    /** Space around the grid; more than the usual gap on phones with big rounded screen corners. */
    edge: Dp = Myty.Gap,
) {
    val haptics = LocalHapticFeedback.current
    val grid = Grid.Main
    var selected by remember { mutableStateOf<Long?>(null) }
    var drag by remember { mutableStateOf<DragState?>(null) }
    var closing by remember { mutableStateOf(setOf<Long>()) }
    var menu by remember { mutableStateOf<MenuSide?>(null) }
    var page by remember { mutableStateOf<MenuPage>(MenuPage.Root) }
    val currentTiles by rememberUpdatedState(tiles)
    // Tiles already on screen at launch just appear; only ones added later animate in.
    val initialIds = remember { tiles.map { it.id }.toSet() }
    val currentOnTiles by rememberUpdatedState(onTiles)

    // Nudge everything a couple of dp each minute so a static layout doesn't burn into OLED screens.
    var shift by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            shift = IntOffset(Random.nextInt(-2, 3), Random.nextInt(-2, 3))
        }
    }

    // Finish close animations, then drop the tile (its space stays empty).
    LaunchedEffect(closing) {
        if (closing.isEmpty()) return@LaunchedEffect
        delay(220)
        var t = currentTiles
        closing.forEach { t = TileLayout.remove(t, grid, it) }
        closing = emptySet()
        currentOnTiles(t)
    }

    fun openMenu(side: MenuSide, p: MenuPage = MenuPage.Root) {
        menu = side
        page = p
        selected = null
    }

    fun closeMenu() {
        menu = null
    }

    // Swipe away from an edge to open the menu from that edge; swipe back towards it to close the menu.
    fun swiped(forward: Boolean, vertical: Boolean) {
        val opens = if (vertical) (if (forward) MenuSide.Top else MenuSide.Bottom) else (if (forward) MenuSide.Left else MenuSide.Right)
        val closes = if (vertical) (if (forward) MenuSide.Bottom else MenuSide.Top) else (if (forward) MenuSide.Right else MenuSide.Left)
        if (menu == closes) closeMenu() else openMenu(opens)
    }

    val data = TileData(state.stats, state.netDown, state.netUp)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Myty.Bg)
            .pointerInput(Unit) { detectTapGestures { selected = null } }
            .pointerInput(portrait) {
                var total = 0f
                val threshold = 60.dp.toPx()
                if (portrait) {
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { change, amount -> change.consume(); total += amount },
                        onDragEnd = { if (abs(total) > threshold) swiped(forward = total > 0, vertical = true) },
                    )
                } else {
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { change, amount -> change.consume(); total += amount },
                        onDragEnd = { if (abs(total) > threshold) swiped(forward = total > 0, vertical = false) },
                    )
                }
            }
            .padding(edge)
            .offset(shift.x.dp, shift.y.dp),
    ) {
        val density = LocalDensity.current
        val gapPx = with(density) { Myty.Gap.toPx() }
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val viewCols = grid.viewCols(portrait)
        val viewRows = grid.viewRows(portrait)
        val cellW = (wPx - gapPx * (viewCols - 1)) / viewCols
        val cellH = (hPx - gapPx * (viewRows - 1)) / viewRows

        fun rectOf(t: Tile): Rect {
            val (c, r) = grid.viewCell(t, portrait)
            return Rect(
                left = c * (cellW + gapPx),
                top = r * (cellH + gapPx),
                right = c * (cellW + gapPx) + t.size.w * cellW + (t.size.w - 1) * gapPx,
                bottom = r * (cellH + gapPx) + t.size.h * cellH + (t.size.h - 1) * gapPx,
            )
        }

        /** Tiles in the half of the screen the menu on [side] leaves visible (portrait's top half is the left half). */
        fun visibleBeside(side: MenuSide): (Tile) -> Boolean = when (side) {
            MenuSide.Right, MenuSide.Bottom -> { t -> t.right <= grid.half }
            MenuSide.Left, MenuSide.Top -> { t -> t.col >= grid.half }
        }

        val d = drag
        val shown = if (d != null && d.moving) d.preview else tiles

        for (tile in shown) key(tile.id) {
            val dragged = d?.moving == true && d.id == tile.id
            // The dragged tile keeps its size and follows the finger; everyone else animates to the preview.
            val startTile = if (dragged) d!!.start.first { it.id == tile.id } else tile
            val target = if (dragged) rectOf(startTile).translate(d!!.origin - rectOf(startTile).topLeft + d.delta) else rectOf(tile)
            TileHost(
                tile = if (dragged) startTile else tile,
                rect = target,
                follow = dragged,
                selected = selected == tile.id && !(d?.moving ?: false),
                closing = tile.id in closing,
                animateIn = tile.id !in initialIds,
                data = data,
                onStyle = { onTiles(TileLayout.cycleStyle(tiles, tile.id)) },
                onTap = {
                    // A tap shows the tile's other reading; if a tile is being edited it just stops editing.
                    if (selected != null) selected = null else onTiles(TileLayout.toggleAlt(tiles, tile.id))
                },
                onClose = { selected = null; closing = closing + tile.id },
                onResize = { onTiles(TileLayout.resize(tiles, grid, tile.id)) },
                onDone = { selected = null },
                // Restart the detector on rotation so it never uses stale cell sizes.
                gestures = Modifier.pointerInput(tile.id, portrait, cellW, cellH) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selected = tile.id
                            val now = currentTiles.first { it.id == tile.id }
                            drag = DragState(tile.id, currentTiles, rectOf(now).topLeft)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val s = drag ?: return@detectDragGesturesAfterLongPress
                            s.delta += amount
                            if (!s.moving && s.delta.getDistance() > 12.dp.toPx()) {
                                s.moving = true
                                selected = null // back to the normal tile look while moving
                            }
                            if (s.moving) {
                                val p = s.origin + s.delta
                                val cell = grid.modelCell(
                                    (p.x / (cellW + gapPx)).roundToInt(), (p.y / (cellH + gapPx)).roundToInt(), portrait,
                                )
                                if (cell != s.cell) {
                                    s.cell = cell
                                    TileLayout.move(s.start, grid, s.id, cell.first, cell.second)?.let { s.preview = it }
                                }
                            }
                        },
                        onDragEnd = {
                            val s = drag
                            if (s != null && s.moving) currentOnTiles(s.preview)
                            drag = null
                        },
                        onDragCancel = { drag = null },
                    )
                },
            )
        }

        // ---- Swipe-in menu ------------------------------------------------------------------------
        val side = menu
        var lastSide by remember { mutableStateOf(MenuSide.Right) }
        if (side != null) lastSide = side
        // The menu covers half the screen: one side in landscape, the top or bottom half in portrait.
        val vertical = lastSide == MenuSide.Top || lastSide == MenuSide.Bottom
        val panelSize = if (vertical) Modifier.size(maxWidth, maxHeight / 2 - Myty.Gap / 2)
            else Modifier.size(maxWidth / 2 - Myty.Gap / 2, maxHeight)
        val enterSpec = spring<IntOffset>(stiffness = Spring.StiffnessMediumLow)
        val exitSpec = tween<IntOffset>(220)
        AnimatedVisibility(
            visible = side != null,
            modifier = Modifier.align(
                when (lastSide) {
                    MenuSide.Left -> Alignment.CenterStart
                    MenuSide.Right -> Alignment.CenterEnd
                    MenuSide.Top -> Alignment.TopCenter
                    MenuSide.Bottom -> Alignment.BottomCenter
                }
            ).zIndex(10f),
            enter = fadeIn() + when (lastSide) {
                MenuSide.Left -> slideInHorizontally(enterSpec) { -it }
                MenuSide.Right -> slideInHorizontally(enterSpec) { it }
                MenuSide.Top -> slideInVertically(enterSpec) { -it }
                MenuSide.Bottom -> slideInVertically(enterSpec) { it }
            },
            exit = fadeOut(tween(220)) + when (lastSide) {
                MenuSide.Left -> slideOutHorizontally(exitSpec) { -it }
                MenuSide.Right -> slideOutHorizontally(exitSpec) { it }
                MenuSide.Top -> slideOutVertically(exitSpec) { -it }
                MenuSide.Bottom -> slideOutVertically(exitSpec) { it }
            },
        ) {
            MenuPanel(
                page = page,
                state = state,
                customColors = customColors,
                modifier = panelSize,
                onPage = { page = it },
                onClose = ::closeMenu,
                onAddTile = { kind ->
                    // Put the new tile on the half of the screen the menu isn't covering.
                    onTiles(TileLayout.add(tiles, grid, kind, visibleBeside(lastSide)))
                },
                onAccent = onAccent,
                onCustomColor = onCustomColor,
                onChangePc = onChangePc,
            )
        }

        AnimatedVisibility(!state.live, Modifier.zIndex(20f), enter = fadeIn(), exit = fadeOut()) {
            ConnectionOverlay(state, onChangePc)
        }
    }
}

@Composable
private fun TileHost(
    tile: Tile,
    rect: Rect,
    follow: Boolean,
    selected: Boolean,
    closing: Boolean,
    animateIn: Boolean,
    data: TileData,
    onStyle: () -> Unit,
    onTap: () -> Unit,
    onClose: () -> Unit,
    onResize: () -> Unit,
    onDone: () -> Unit,
    gestures: Modifier,
) {
    val x = remember { Animatable(rect.left) }
    val y = remember { Animatable(rect.top) }
    val w = remember { Animatable(rect.width) }
    val h = remember { Animatable(rect.height) }
    val appear = remember { Animatable(if (animateIn) 0f else 1f) }

    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
    LaunchedEffect(rect, follow) {
        if (follow) {
            x.snapTo(rect.left); y.snapTo(rect.top); w.snapTo(rect.width); h.snapTo(rect.height)
        } else {
            launch { x.animateTo(rect.left, tileSpring) }
            launch { y.animateTo(rect.top, tileSpring) }
            launch { w.animateTo(rect.width, tileSpring) }
            launch { h.animateTo(rect.height, tileSpring) }
        }
    }
    val onTapState = rememberUpdatedState(onTap)
    val pressScale by animateFloatAsState(if (selected) 0.95f else 1f, tileSpring, label = "press")
    val gone by animateFloatAsState(if (closing) 0f else 1f, tween(200), label = "close")

    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
            .size(with(density) { w.value.toDp() }, with(density) { h.value.toDp() })
            .zIndex(if (follow) 5f else if (selected) 2f else 0f)
            .graphicsLayer {
                val s = pressScale * gone * (0.6f + 0.4f * appear.value)
                scaleX = s; scaleY = s
                alpha = gone * appear.value.coerceIn(0f, 1f)
                shadowElevation = if (follow) 24f else 0f
                shape = RoundedCornerShape(Myty.Radius)
                clip = false
            }
            .then(gestures)
            .pointerInput(Unit) { detectQuickTap { onTapState.value() } }
            .semantics { contentDescription = "${tile.kind.label} tile, style ${tile.style}, ${tile.size.name.lowercase()}" },
    ) {
        TileContent(tile, data, chrome = !selected, modifier = Modifier.fillMaxSize())
        AnimatedVisibility(selected, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
            CornerButtons(tile, onStyle, onClose, onResize, onDone)
        }
    }
}

/** A selected tile's buttons: style (top-left), remove (top-right), resize (bottom-left), done (bottom-right). */
@Composable
internal fun CornerButtons(tile: Tile, onStyle: () -> Unit, onClose: () -> Unit, onResize: () -> Unit, onDone: () -> Unit) {
    val accent = LocalAccent.current
    val s = buttonSize(tile.size)
    // On the fill style the left-hand buttons sit on the highlight colour.
    val leftColor = if (tile.style == 2 && tile.kind.styles > 1) onColor(accent) else Myty.White
    Box(Modifier.fillMaxSize().padding(buttonPad(tile.size))) {
        if (tile.kind.styles > 1) {
            CircleIcon(Glyph.None, leftColor, s, Modifier.align(Alignment.TopStart).clickable(onClick = onStyle),
                label = tile.style.toString(), description = "Change style")
        }
        CircleIcon(Glyph.Close, Myty.White, s, Modifier.align(Alignment.TopEnd).clickable(onClick = onClose),
            description = "Remove tile")
        CircleIcon(Glyph.Resize, leftColor, s, Modifier.align(Alignment.BottomStart).clickable(onClick = onResize),
            description = "Resize tile")
        CircleIcon(Glyph.Check, Myty.White, s, Modifier.align(Alignment.BottomEnd).clickable(onClick = onDone),
            description = "Done")
    }
}

@Composable
private fun ConnectionOverlay(state: UiState, onChangePc: () -> Unit) {
    val accent = LocalAccent.current
    val name = state.target?.name?.uppercase() ?: "PC"
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(Myty.Radius)).background(Myty.White).padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (state.everConnected) "LOST CONNECTION" else "CONNECTING…", color = Color.Black, fontSize = 18.sp)
            Text(name, color = accent.takeIf { abs(it.red - 1f) + abs(it.green - 1f) + abs(it.blue - 1f) > 0.3f } ?: Color.Black,
                fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            Text(
                if (state.target?.isUsb == true) "CHECK THE CABLE AND USB DEBUGGING"
                else "TEST: OPEN HTTP://${state.target?.host}:${state.target?.port}\nIN THIS PHONE'S BROWSER",
                color = Color.Black.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 16.dp),
            )
            Pill("CHANGE PC", onChangePc)
        }
    }
}

@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape)
            .then(if (filled) Modifier.background(Color.Black) else Modifier.border(1.dp, Color.Black, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) Color.White else Color.Black, fontSize = 12.sp, maxLines = 1)
    }
}

/**
 * A tap that's released before the long-press timeout. Unlike detectTapGestures it never consumes the
 * events of a long press, so long-press-and-drag on the same tile keeps working.
 */
private suspend fun PointerInputScope.detectQuickTap(onTap: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
        if (up != null) {
            up.consume()
            onTap()
        }
    }
}
