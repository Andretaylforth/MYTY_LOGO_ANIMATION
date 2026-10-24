package com.myty.monitor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

private val CardShape = RoundedCornerShape(14.dp)

@Composable
fun BentoCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(Myty.Card)
            .border(1.dp, Myty.Border, CardShape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Myty.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = Myty.Faint,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
                )
            }
            Spacer(Modifier.weight(1f))
            trailing()
        }
        content()
    }
}

private const val GAUGE_START = 120f
private const val GAUGE_SWEEP = 300f

/** Open ring with the value in the middle and a caption sitting in the gap at the bottom. */
@Composable
fun RingGauge(
    fraction: Float,
    value: String,
    unit: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "gauge")
    val tint by animateColorAsState(color, tween(700), label = "gaugeColor")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val d = min(maxWidth.value, maxHeight.value)
        Canvas(Modifier.size(d.dp)) {
            val sw = size.minDimension * 0.085f
            val arc = Size(size.minDimension - sw, size.minDimension - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(Myty.Track, GAUGE_START, GAUGE_SWEEP, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            if (anim > 0.003f) {
                drawArc(tint, GAUGE_START, GAUGE_SWEEP * anim, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Myty.Text, fontSize = (d * 0.27f).sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(
                unit,
                color = Myty.Text,
                fontSize = (d * 0.12f).sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = (d * 0.045f).dp),
            )
        }
        Text(
            caption,
            color = Myty.Dim,
            fontSize = (d * 0.11f).sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (d * 0.01f).dp),
        )
    }
}

/** Flat bar on top, label and value underneath (as in the Myty bento design). */
@Composable
fun StatBar(label: String, value: String, unit: String, fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "bar")
    val tint by animateColorAsState(color, tween(700), label = "barColor")
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Myty.Track)
        ) {
            Box(Modifier.fillMaxWidth(anim).fillMaxHeight().background(tint))
        }
        Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.Bottom) {
            Text(label, color = Myty.Text, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text(value, color = Myty.Text, fontSize = 13.sp, maxLines = 1)
            Text(unit, color = Myty.Text, fontSize = 9.sp, modifier = Modifier.padding(start = 1.dp, bottom = 1.dp))
        }
    }
}

enum class LoadLevel(val label: String, val color: Color) {
    Minimal("Minimal Load", Myty.Faint),
    Light("Light Load", Myty.Bar),
    Moderate("Moderate Load", Myty.BlueBright),
    Heavy("Heavy Load", Myty.Warm),
    Max("Maxed Out", Myty.Hot);

    companion object {
        fun ofPercent(p: Double?): LoadLevel = when {
            p == null || p < 10 -> Minimal
            p < 35 -> Light
            p < 70 -> Moderate
            p < 92 -> Heavy
            else -> Max
        }
    }
}

@Composable
fun LoadStatus(level: LoadLevel, modifier: Modifier = Modifier, extra: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(1.5.dp)).background(level.color))
        Text(
            level.label,
            color = if (level == LoadLevel.Minimal) Myty.Faint else level.color,
            fontSize = 11.sp,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(start = 6.dp),
        )
        if (extra != null) {
            Spacer(Modifier.weight(1f))
            Text(extra, color = Myty.Faint, fontSize = 10.sp, maxLines = 1)
        }
    }
}

/** Filled line chart that scales to its own max. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier, max: Float? = null) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val top = (max ?: values.max()).coerceAtLeast(1f)
        val step = size.width / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / top).coerceIn(0f, 1f) * size.height
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
    }
}

/** Arrow used on the Network card; lit when there's traffic in that direction. */
@Composable
fun Arrow(up: Boolean, lit: Boolean, height: Dp) {
    val color = if (lit) Myty.Text else Myty.Faint
    Canvas(Modifier.width(height * 0.45f).height(height)) {
        val w = size.width
        val h = size.height
        val head = w * 0.9f
        val stem = w * 0.22f
        val p = Path()
        if (up) {
            p.moveTo(w / 2, 0f); p.lineTo(w, head); p.lineTo(w / 2 + stem, head); p.lineTo(w / 2 + stem, h)
            p.lineTo(w / 2 - stem, h); p.lineTo(w / 2 - stem, head); p.lineTo(0f, head)
        } else {
            p.moveTo(w / 2, h); p.lineTo(w, h - head); p.lineTo(w / 2 + stem, h - head); p.lineTo(w / 2 + stem, 0f)
            p.lineTo(w / 2 - stem, 0f); p.lineTo(w / 2 - stem, h - head); p.lineTo(0f, h - head)
        }
        p.close()
        drawPath(p, color)
    }
}

@Composable
fun ValueUnit(value: String, unit: String, size: Float, modifier: Modifier = Modifier, color: Color = Myty.Text) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, color = color, fontSize = size.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(unit, color = color, fontSize = (size * 0.5f).sp, maxLines = 1,
            modifier = Modifier.padding(start = 1.dp, bottom = (size * 0.12f).dp))
    }
}
