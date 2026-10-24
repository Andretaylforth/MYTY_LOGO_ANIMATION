package com.myty.monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

object Myty {
    val Blue = Color(0xFF002A8C)      // brand blue from the Myty logo
    val BlueBright = Color(0xFF4D7CFF) // brand blue lifted for use on dark backgrounds
    val Logo = Color(0xFFF0F0F0)

    val Bg = Color(0xFF111215)
    val Card = Color(0xFF1B1C20)
    val Border = Color(0xFF2A2C32)
    val Track = Color(0xFF2B2D34)
    val Bar = Color(0xFF6C7080)
    val Text = Color(0xFFE8E9EE)
    val Dim = Color(0xFF8A8D96)
    val Faint = Color(0xFF55585F)

    val Warm = Color(0xFFFFC857)
    val Orange = Color(0xFFFF8A3D)
    val Hot = Color(0xFFFF5A5F)

    /** Cool blue → amber → red as a component heats up. */
    fun heat(tempC: Double?): Color {
        val t = tempC ?: return Bar
        return when {
            t < 60 -> BlueBright
            t < 80 -> lerp(Warm, Orange, ((t - 60) / 20).toFloat())
            t < 92 -> lerp(Orange, Hot, ((t - 80) / 12).toFloat())
            else -> Hot
        }
    }
}

@Composable
fun MytyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Myty.BlueBright,
            onPrimary = Color.White,
            background = Myty.Bg,
            surface = Myty.Card,
            onSurface = Myty.Text,
            onBackground = Myty.Text,
        ),
    ) {
        // Tight line heights: the bento cards are dense and Material's 24sp default wastes space.
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            content = content,
        )
    }
}

/** The Myty mark: rounded square with four round holes. [holes] should match whatever is behind it. */
@Composable
fun MytyLogo(modifier: Modifier, color: Color = Myty.Logo, holes: Color = Myty.Blue) {
    Canvas(modifier) {
        val s = size.minDimension
        val o = Offset((size.width - s) / 2, (size.height - s) / 2)
        drawRoundRect(color, o, androidx.compose.ui.geometry.Size(s, s), CornerRadius(s * 0.19f))
        val r = s * 0.19f
        val inset = s * 0.27f
        for (x in floatArrayOf(inset, s - inset)) for (y in floatArrayOf(inset, s - inset)) {
            drawCircle(holes, r, o + Offset(x, y))
        }
    }
}
