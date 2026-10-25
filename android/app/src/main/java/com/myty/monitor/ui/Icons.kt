package com.myty.monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Thin line icons used on tiles and in the menu, drawn to match the mockups. */
enum class Glyph { Plus, Close, Resize, Check, Dropper, None }

/** Outlined circle with a glyph (or [label]) inside, like the corner buttons on a selected tile. */
@Composable
fun CircleIcon(
    glyph: Glyph,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    label: String? = null,
    fill: Color? = null,
) {
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = (size.toPx() * 0.05f).coerceAtLeast(1.2f)
            if (fill != null) drawCircle(fill, radius = this.size.minDimension / 2)
            else drawCircle(color, radius = this.size.minDimension / 2 - sw / 2, style = Stroke(sw))
            drawGlyph(glyph, color, sw)
        }
        if (label != null) Text(label, color = color, fontSize = (size.value * 0.5f).sp)
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color, sw: Float) {
    val c = center
    val r = size.minDimension * 0.24f
    fun line(a: Offset, b: Offset) = drawLine(color, a, b, sw, StrokeCap.Round)
    when (glyph) {
        Glyph.Plus -> {
            line(c + Offset(-r, 0f), c + Offset(r, 0f))
            line(c + Offset(0f, -r), c + Offset(0f, r))
        }
        Glyph.Close -> {
            val d = r * 0.8f
            line(c + Offset(-d, -d), c + Offset(d, d))
            line(c + Offset(-d, d), c + Offset(d, -d))
        }
        Glyph.Resize -> {
            // Diagonal double-headed arrow, bottom-left to top-right.
            val d = r * 0.85f
            val a = c + Offset(-d, d)
            val b = c + Offset(d, -d)
            val h = r * 0.55f
            line(a, b)
            line(b, b + Offset(-h, 0f)); line(b, b + Offset(0f, h))
            line(a, a + Offset(h, 0f)); line(a, a + Offset(0f, -h))
        }
        Glyph.Check -> {
            val p = Path().apply {
                moveTo(c.x - r * 0.9f, c.y + r * 0.05f)
                lineTo(c.x - r * 0.25f, c.y + r * 0.7f)
                lineTo(c.x + r * 0.95f, c.y - r * 0.75f)
            }
            drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Glyph.Dropper -> {
            val d = r * 0.9f
            line(c + Offset(-d, d), c + Offset(d * 0.45f, -d * 0.45f))
            drawCircle(color, r * 0.32f, c + Offset(d * 0.6f, -d * 0.6f))
        }
        Glyph.None -> Unit
    }
}
