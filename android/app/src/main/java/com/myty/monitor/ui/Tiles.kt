package com.myty.monitor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myty.monitor.data.Stats
import com.myty.monitor.layout.Tile
import com.myty.monitor.layout.TileKind
import com.myty.monitor.layout.TileSize
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** What a tile needs to draw itself. */
data class TileData(val stats: Stats?, val netDown: List<Float>, val netUp: List<Float>)

/** One value shown by the CPU / GPU / RAM tiles. */
private data class Gauge(
    val label: String,
    val clock: String?,
    val load: String?,
    val name: String?,
    val value: String,
    val fraction: Float,
)

private fun gaugeFor(kind: TileKind, s: Stats?): Gauge = when (kind) {
    TileKind.Gpu -> {
        val g = s?.gpu
        Gauge("GPU", g?.clock?.let { "${it.toInt()}MHZ" }, g?.load?.let { "${it.toInt()}% LOAD" },
            g?.name?.uppercase(), deg(g?.temp), frac(g?.temp))
    }
    TileKind.Ram -> {
        val r = s?.ram
        Gauge("RAM", null, r?.let { "${gb(it.used)}/${gb(it.total)}GB" }, r?.let { "MEMORY IN USE" },
            r?.let { "${it.load.toInt()}%" } ?: "--%", frac(r?.load))
    }
    else -> {
        val c = s?.cpu
        Gauge("CPU", c?.clock?.let { "${it.toInt()}MHZ" }, c?.load?.let { "${it.toInt()}% LOAD" },
            c?.name?.uppercase(), deg(c?.temp), frac(c?.temp))
    }
}

private fun deg(v: Double?) = v?.let { "${Math.round(it)}°" } ?: "--°"
private fun frac(v: Double?) = ((v ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
private fun gb(v: Double) = if (v >= 100) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

private val springy = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)

/** Tile body. [chrome] = false hides the text labels (while the tile shows its corner buttons). */
@Composable
fun TileContent(tile: Tile, data: TileData, chrome: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(Myty.Radius)).background(Myty.Tile)) {
        when (tile.kind) {
            TileKind.Cpu, TileKind.Gpu, TileKind.Ram -> {
                val g = gaugeFor(tile.kind, data.stats)
                when (tile.style) {
                    2 -> FillTile(tile.size, g, chrome)
                    3 -> OrbitTile(tile.size, g, chrome)
                    4 -> DotsTile(tile.size, g, chrome)
                    else -> RingTile(tile.size, g, chrome)
                }
            }
            TileKind.Network -> NetworkTile(tile.size, data, chrome)
            TileKind.Storage -> StorageTile(tile.size, data.stats, chrome)
            TileKind.Processes -> ProcessTile(tile.size, data.stats, chrome)
        }
    }
}

// ---- Shared pieces -------------------------------------------------------------------------------

@Suppress("UNUSED_PARAMETER")
private fun labelSize(size: TileSize) = 10.sp
private fun pad(size: TileSize): Dp = if (size == TileSize.Tiny) 4.dp else 6.dp

/**
 * Corner labels: name top-left, load top-right, model bottom-left.
 * [circle] is the width of a centred gauge; the model name wraps onto two lines only if it would reach it.
 */
@Composable
private fun BoxScope.Labels(size: TileSize, g: Gauge, chrome: Boolean, color: Color = Myty.White, circle: Dp? = null) {
    AnimatedVisibility(chrome, Modifier.matchParentSize(), enter = fadeIn(), exit = fadeOut()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad(size))) {
            // On a large tile the gauge sits above the name, so only wide tiles need to dodge it.
            val nameWidth = if (circle != null && size == TileSize.Wide) {
                ((maxWidth + pad(size) * 2 - circle) / 2 - pad(size) - 6.dp).coerceAtLeast(40.dp)
            } else maxWidth
            val fs = labelSize(size)
            val top = if (size == TileSize.Large && g.clock != null) "${g.label}  ${g.clock}" else g.label
            Text(top, color = color, fontSize = fs, maxLines = 1, modifier = Modifier.align(Alignment.TopStart))
            if (size.w == 2 && g.load != null) {
                Text(g.load, color = color, fontSize = fs, maxLines = 1, modifier = Modifier.align(Alignment.TopEnd))
            }
            if (size.w == 2 && g.name != null) {
                Text(
                    g.name, color = color, fontSize = fs, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).widthIn(max = nameWidth),
                )
            }
        }
    }
}

/** Draws [content] twice: [inside] colours left of [splitPx], [outside] colours right of it. */
@Composable
private fun TwoTone(splitPx: Float, content: @Composable (inside: Boolean) -> Unit) {
    Box(Modifier.fillMaxSize().drawWithContent { clipRect(right = splitPx) { this@drawWithContent.drawContent() } }) {
        content(true)
    }
    Box(Modifier.fillMaxSize().drawWithContent { clipRect(left = splitPx) { this@drawWithContent.drawContent() } }) {
        content(false)
    }
}

/** Big reading, e.g. "68°". The font is monospaced, so pull the degree sign in against the digits. */
@Composable
private fun Value(text: String, fontSize: Float, color: Color = Myty.White, modifier: Modifier = Modifier) {
    val styled = buildAnnotatedString {
        if (text.endsWith("°") && text.length >= 2) {
            append(text.dropLast(2))
            withStyle(SpanStyle(letterSpacing = (-0.3).em)) { append(text[text.length - 2]) }
            append("°")
        } else append(text)
    }
    Text(styled, color = color, fontSize = fontSize.sp, maxLines = 1, softWrap = false, modifier = modifier)
}

// ---- Style 1: ring -------------------------------------------------------------------------------

@Composable
private fun RingTile(size: TileSize, g: Gauge, chrome: Boolean) {
    val accent = LocalAccent.current
    val f by animateFloatAsState(g.fraction, springy, label = "ring")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val d = circleSize(size, maxWidth, maxHeight)
        Canvas(Modifier.size(d)) {
            val sw = this.size.minDimension * 0.09f
            val arc = Size(this.size.width - sw, this.size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(Myty.White, 0f, 360f, false, tl, arc, style = Stroke(sw))
            // Starts at the bottom and runs clockwise as the temperature rises.
            if (f > 0.002f) drawArc(accent, 90f, 360f * f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Value(g.value, d.value * 0.25f)
        Labels(size, g, chrome, circle = d)
    }
}

private fun circleSize(size: TileSize, w: Dp, h: Dp): Dp =
    if (size == TileSize.Tiny) h * 0.72f else minOf(w, h) * 0.66f

// ---- Style 2: fill -------------------------------------------------------------------------------

@Composable
private fun FillTile(size: TileSize, g: Gauge, chrome: Boolean) {
    val accent = LocalAccent.current
    val on = onColor(accent)
    val f by animateFloatAsState(g.fraction, springy, label = "fill")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fillW = maxWidth * f
        Box(Modifier.fillMaxHeight().width(fillW).clip(RoundedCornerShape(Myty.Radius)).background(accent))
        val split = with(androidx.compose.ui.platform.LocalDensity.current) { fillW.toPx() }
        // Same size on every tile width (it's set by one column), so tiles read alike side by side.
        val big = minOf(maxWidth.value / size.w * 0.25f, maxHeight.value * 0.55f)
        TwoTone(split) { inside ->
            Box(Modifier.fillMaxSize()) {
                Value(g.value, big, color = if (inside) on else accent, modifier = Modifier.align(Alignment.Center))
                Labels(size, g, chrome, color = if (inside) on else Myty.White)
            }
        }
    }
}

// ---- Style 3: orbit ------------------------------------------------------------------------------

@Composable
private fun OrbitTile(size: TileSize, g: Gauge, chrome: Boolean) {
    val accent = LocalAccent.current
    val f by animateFloatAsState(g.fraction, springy, label = "orbit")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val d = circleSize(size, maxWidth, maxHeight)
        Canvas(Modifier.size(d)) {
            val r = this.size.minDimension / 2
            drawCircle(Myty.White, r)
            val a = Math.toRadians(90.0 + 360.0 * f)
            val ball = center + Offset((r * cos(a)).toFloat(), (r * sin(a)).toFloat())
            drawCircle(accent, r * 0.16f, ball)
        }
        Value(g.value, d.value * 0.22f, color = Color.Black)
        Labels(size, g, chrome, circle = d)
    }
}

// ---- Style 4: dot grid ---------------------------------------------------------------------------

@Composable
private fun DotsTile(size: TileSize, g: Gauge, chrome: Boolean) {
    val accent = LocalAccent.current
    val f by animateFloatAsState(g.fraction, springy, label = "dots")
    BoxWithConstraints(Modifier.fillMaxSize().padding(pad(size))) {
        // Dot size follows the tile's height, as in the mockups: about 9 rows on large, 4 on wide, 7 on small.
        val targetRows = when (size) {
            TileSize.Large -> 9
            TileSize.Wide -> 4
            TileSize.Small -> 7
            TileSize.Tiny -> 3
        }
        val pitch = maxHeight / targetRows
        val cols = floor(maxWidth / pitch).toInt().coerceAtLeast(1)
        val rows = targetRows
        // The label takes the first cell, the reading the start of the last row.
        val valueCells = if (g.value.length > 3) 2 else 1
        val skip = setOf(0) + (0 until valueCells).map { (rows - 1) * cols + it }
        val total = rows * cols - skip.size
        Canvas(Modifier.fillMaxSize()) {
            val p = pitch.toPx()
            val r = p * 0.36f
            val filled = Math.round(f * total)
            var n = 0
            val left = (this.size.width - cols * p) / 2
            for (i in 0 until rows * cols) {
                if (i in skip) continue
                val c = center.copy(x = left + (i % cols) * p + p / 2, y = (i / cols) * p + p / 2)
                if (n < filled) drawCircle(accent, r, c) else drawCircle(Myty.White, r - 0.6.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                n++
            }
        }
        val fs = labelSize(size)
        AnimatedVisibility(chrome, Modifier.align(Alignment.TopStart), enter = fadeIn(), exit = fadeOut()) {
            Text(g.label, fontSize = fs)
        }
        Value(g.value, fs.value, modifier = Modifier.align(Alignment.BottomStart))
    }
}

// ---- Network -------------------------------------------------------------------------------------

@Composable
private fun NetworkTile(size: TileSize, data: TileData, chrome: Boolean) {
    val accent = LocalAccent.current
    val n = data.stats?.net
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (size != TileSize.Tiny && data.netDown.size > 1) {
            val top = ((data.netDown + data.netUp).maxOrNull() ?: 0f).coerceAtLeast(64 * 1024f)
            Graph(data.netUp, Myty.Dim, top, Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.BottomCenter))
            Graph(data.netDown, accent, top, Modifier.fillMaxWidth().fillMaxHeight(0.45f).align(Alignment.BottomCenter))
        }
        val big = when (size) {
            TileSize.Tiny -> minOf(maxWidth.value * 0.13f, 16f)
            else -> minOf(maxWidth.value * 0.12f, maxHeight.value * 0.11f, 40f)
        }
        Column(
            Modifier.padding(pad(size)).align(if (size == TileSize.Tiny) Alignment.BottomStart else Alignment.CenterStart)
                .offset(y = if (size == TileSize.Tiny) 0.dp else -maxHeight * 0.08f),
        ) {
            if (size != TileSize.Tiny) Text("↑ " + rate(n?.up), fontSize = big.sp, maxLines = 1)
            Text("↓ " + rate(n?.down), fontSize = big.sp, maxLines = 1, color = if (size == TileSize.Tiny) Myty.White else accent)
        }
        AnimatedVisibility(chrome, Modifier.padding(pad(size)).align(Alignment.TopStart), enter = fadeIn(), exit = fadeOut()) {
            Text(if (size == TileSize.Tiny) "NET" else "NETWORK", fontSize = labelSize(size))
        }
    }
}

@Composable
private fun Graph(values: List<Float>, color: Color, max: Float, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v ->
            val y = size.height - (v / max).coerceIn(0f, 1f) * size.height * 0.9f
            if (i == 0) line.moveTo(0f, y) else line.lineTo(i * step, y)
        }
        val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(1.5.dp.toPx()))
    }
}

private fun rate(b: Double?): String {
    val v = b ?: return "--"
    fun f(x: Double) = if (x < 10) String.format(Locale.US, "%.1f", x).removeSuffix(".0") else x.toInt().toString()
    return when {
        v < 1024 * 1024 -> f(v / 1024) + "KB/S"
        v < 1024.0 * 1024 * 1024 -> f(v / (1024 * 1024)) + "MB/S"
        else -> f(v / (1024.0 * 1024 * 1024)) + "GB/S"
    }
}

// ---- Storage -------------------------------------------------------------------------------------

@Composable
private fun StorageTile(size: TileSize, s: Stats?, chrome: Boolean) {
    val accent = LocalAccent.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(pad(size))) {
        val drives = s?.drives.orEmpty()
        val head = if (chrome) 22.dp else 0.dp
        val rowH = 30.dp
        val fit = ((maxHeight - head) / rowH).toInt().coerceAtLeast(1)
        Column(Modifier.fillMaxSize().padding(top = head), verticalArrangement = Arrangement.SpaceEvenly) {
            drives.take(fit).forEach { d ->
                val frac = if (d.total > 0) d.used.toFloat() / d.total else 0f
                val shown by animateFloatAsState(frac, springy, label = "drive")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name.trimEnd('\\', ':'), fontSize = labelSize(size), modifier = Modifier.width(22.dp))
                    Box(Modifier.weight(1f).height(if (size == TileSize.Tiny) 8.dp else 14.dp).clip(RoundedCornerShape(4.dp)).background(Myty.Track)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(if (frac > 0.92f) Color(0xFFFF0C00) else accent))
                    }
                    if (size.w == 2) {
                        Text("${tb(d.used)}/${tb(d.total)}", fontSize = labelSize(size), color = Myty.Dim,
                            modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        AnimatedVisibility(chrome, Modifier.align(Alignment.TopStart), enter = fadeIn(), exit = fadeOut()) {
            Text(if (size == TileSize.Tiny) "DISK" else "STORAGE", fontSize = labelSize(size))
        }
    }
}

private fun tb(bytes: Long): String {
    val t = bytes / 1e12
    return if (t >= 1) String.format(Locale.US, "%.1fTB", t) else String.format(Locale.US, "%.0fGB", bytes / 1e9)
}

// ---- Processes -----------------------------------------------------------------------------------

@Composable
private fun ProcessTile(size: TileSize, s: Stats?, chrome: Boolean) {
    val accent = LocalAccent.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(pad(size))) {
        val head = if (chrome) 22.dp else 0.dp
        val rowH = if (size == TileSize.Tiny) 16.dp else 22.dp
        val fit = ((maxHeight - head) / rowH).toInt().coerceAtLeast(1)
        val procs = s?.procs.orEmpty().sortedByDescending { it.cpu }.take(fit)
        val top = procs.maxOfOrNull { it.cpu }?.coerceAtLeast(1.0) ?: 1.0
        Column(Modifier.fillMaxSize().padding(top = head)) {
            procs.forEach { p ->
                Row(Modifier.height(rowH), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name.uppercase(), fontSize = labelSize(size), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    if (size.w == 2) {
                        Box(Modifier.width(48.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Myty.Track)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth((p.cpu / top).toFloat()).background(accent))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("${Math.round(p.cpu)}%", fontSize = labelSize(size), modifier = Modifier.width(36.dp),
                        maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    if (size.w == 2) {
                        val mb = p.ram / (1024.0 * 1024)
                        Text(if (mb >= 1000) "${gb(mb / 1024)}GB" else "${mb.toInt()}MB", fontSize = labelSize(size),
                            color = Myty.Dim, modifier = Modifier.width(64.dp), maxLines = 1,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
        }
        AnimatedVisibility(chrome, Modifier.align(Alignment.TopStart), enter = fadeIn(), exit = fadeOut()) {
            Text(if (size.w == 2) "TOP PROCESSES · CPU" else "PROCESSES", fontSize = labelSize(size))
        }
    }
}
