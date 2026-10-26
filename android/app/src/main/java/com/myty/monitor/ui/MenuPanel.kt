package com.myty.monitor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myty.monitor.UiState
import com.myty.monitor.layout.TileKind

sealed interface MenuPage {
    data object Root : MenuPage
    data object Tiles : MenuPage
    data object Colours : MenuPage
    data class Picker(val slot: Int) : MenuPage
}

private val Ink = Color.Black

@Composable
fun MenuPanel(
    page: MenuPage,
    state: UiState,
    customColors: List<Color?>,
    modifier: Modifier,
    onPage: (MenuPage) -> Unit,
    onClose: () -> Unit,
    onAddTile: (TileKind) -> Unit,
    onAccent: (Color) -> Unit,
    onCustomColor: (Int, Color) -> Unit,
    onChangePc: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(Myty.Radius))
            .background(Myty.White)
            // Swallow taps/swipes so they don't reach the tiles underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(10.dp),
    ) {
        AnimatedContent(page, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "menu") { p ->
            val title = when (p) {
                MenuPage.Root -> "MENU"
                MenuPage.Tiles -> "TILES"
                MenuPage.Colours -> "COLOURS"
                is MenuPage.Picker -> "CUSTOM COLOUR"
            }
            // X closes the menu from its first page, and goes back a page from the others.
            val back = { if (p == MenuPage.Root) onClose() else onPage(if (p is MenuPage.Picker) MenuPage.Colours else MenuPage.Root) }
            Box(Modifier.fillMaxSize()) {
                Text(title, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                CircleIcon(Glyph.Close, Ink, 28.dp, Modifier.align(Alignment.TopEnd).clickable(onClick = back))
                Box(Modifier.fillMaxSize().padding(top = 40.dp)) {
                    when (p) {
                        MenuPage.Root -> RootPage(state, onPage, onChangePc)
                        MenuPage.Tiles -> TilesPage(onAddTile)
                        MenuPage.Colours -> ColoursPage(customColors, onAccent, onPage, onClose)
                        is MenuPage.Picker -> PickerPage(customColors.getOrNull(p.slot) ?: LocalAccent.current) {
                            onCustomColor(p.slot, it)
                            onAccent(it)
                            onPage(MenuPage.Colours)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootPage(state: UiState, onPage: (MenuPage) -> Unit, onChangePc: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        val t = state.target
        Text("CONNECTED TO", color = Ink.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        Text(
            (t?.name ?: "--").uppercase() + if (t?.isUsb == true) " · USB" else " · WI-FI",
            color = Ink, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 10.dp),
        )
        Pill("CHANGE PC", onChangePc, filled = false, modifier = Modifier.padding(start = 2.dp))
        Spacer(Modifier.weight(1f))
        val context = LocalContext.current
        val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
        if (version != null) {
            Text("VERSION $version", color = Ink.copy(alpha = 0.4f), fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight(0.62f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuCard("TILES", Glyph.Plus, Modifier.weight(1f)) { onPage(MenuPage.Tiles) }
            MenuCard("COLOURS", Glyph.Dropper, Modifier.weight(1f)) { onPage(MenuPage.Colours) }
        }
    }
}

@Composable
private fun MenuCard(title: String, glyph: Glyph, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.fillMaxHeight().clip(shape).border(1.dp, Ink, shape).clickable(onClick = onClick).padding(10.dp)) {
        Text(title, color = Ink, fontSize = 12.sp)
        CircleIcon(glyph, Ink, 26.dp, Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun TilesPage(onAddTile: (TileKind) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        TileKind.entries.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { kind ->
                    val shape = RoundedCornerShape(50)
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(shape).border(1.dp, Ink, shape)
                            .clickable { onAddTile(kind) },
                        contentAlignment = Alignment.Center,
                    ) { Text(kind.label, color = Ink, fontSize = 12.sp, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun ColoursPage(
    customColors: List<Color?>,
    onAccent: (Color) -> Unit,
    onPage: (MenuPage) -> Unit,
    onClose: () -> Unit,
) {
    val accent = LocalAccent.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = 6
        val gap = 8.dp
        val cell = minOf((maxWidth - gap * (cols - 1)) / cols, (maxHeight - gap * 2) / 3)
        // 14 swatches, 3 custom slots, then the tick.
        val items: List<Any> = Myty.Palette + customColors.indices.map { CustomSlot(it) } + Done
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap, Alignment.Bottom)) {
            items.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
                    row.forEach { item ->
                        when (item) {
                            is Color -> Swatch(item, item.toArgb() == accent.toArgb(), cell) { onAccent(item) }
                            is CustomSlot -> {
                                val c = customColors[item.index]
                                if (c == null) {
                                    CircleIcon(Glyph.Plus, Ink, cell, Modifier.clickable { onPage(MenuPage.Picker(item.index)) })
                                } else {
                                    // Tap to use it, long-press to edit it.
                                    Swatch(c, c.toArgb() == accent.toArgb(), cell,
                                        onLongClick = { onPage(MenuPage.Picker(item.index)) }) { onAccent(c) }
                                }
                            }
                            else -> CircleIcon(Glyph.Check, Ink, cell, Modifier.clickable(onClick = onClose))
                        }
                    }
                }
            }
        }
    }
}

private data class CustomSlot(val index: Int)
private data object Done

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Swatch(c: Color, chosen: Boolean, size: androidx.compose.ui.unit.Dp, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(c)
            .border(1.dp, if (c.toArgb() == Color.White.toArgb()) Ink.copy(alpha = 0.6f) else Color.Transparent, CircleShape)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (chosen) CircleIcon(Glyph.Check, onColor(c), size * 0.6f)
    }
}

/** Hue strip plus saturation/brightness square. */
@Composable
private fun PickerPage(initial: Color, onSave: (Color) -> Unit) {
    val hsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color.hsv(hue, sat, value)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    fun set(o: Offset) {
                        sat = (o.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                    }
                    detectDragGestures(onDragStart = ::set) { change, _ -> set(change.position) }
                }
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        sat = (o.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val p = Offset(sat * size.width, (1 - value) * size.height)
                drawCircle(Color.White, 9.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                drawCircle(Color.Black, 10.dp.toPx(), p, style = Stroke(1.dp.toPx()))
            }
        }
        Box(
            Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(50))
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { hue = (it.x / size.width).coerceIn(0f, 1f) * 360f }) { change, _ ->
                        hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                    }
                }
                .pointerInput(Unit) { detectTapGestures { hue = (it.x / size.width).coerceIn(0f, 1f) * 360f } },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                val x = hue / 360f * size.width
                drawCircle(Color.White, size.height / 2 - 2.dp.toPx(), Offset(x.coerceIn(size.height / 2, size.width - size.height / 2), center.y), style = Stroke(2.dp.toPx()))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).aspectRatio(1f).clip(CircleShape).background(color).border(1.dp, Ink.copy(alpha = 0.3f), CircleShape))
            Spacer(Modifier.width(10.dp))
            Text("#%06X".format(color.toArgb() and 0xFFFFFF), color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Pill("SAVE", { onSave(color) })
        }
    }
}
