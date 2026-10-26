package com.myty.monitor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.myty.monitor.R

object Myty {
    val Bg = Color(0xFF000000)
    val Tile = Color(0xFF191919)
    val White = Color(0xFFFFFFFF)
    val Dim = Color(0xFF8F8F8F)
    val Track = Color(0xFF2E2E2E)

    /** Default highlight colour; the user can change it from the Colours menu. */
    val Blue = Color(0xFF001FFF)

    /** The brand blue from the logo animation (connect screen and app icon). */
    val Brand = Color(0xFF002A8C)

    /** Colour swatches on the Colours page, in order. */
    val Palette = listOf(
        0xFFFFFFFF, 0xFFE5E1C3, 0xFFCCCCCC, 0xFF8F8F8F, 0xFF3D3D3D, 0xFF000000,
        0xFF01FE98, 0xFF2E7D32, 0xFFE0A870, 0xFFFF6700, 0xFFFF0C00, 0xFF0A0192,
        0xFFFAFF01, 0xFFCCFF7B,
    ).map { Color(it) }

    val Font = FontFamily(Font(R.font.departure_mono))

    val Radius = 16.dp
    val Gap = 10.dp
}

/** The user's highlight colour. Everything blue in the design uses this. */
val LocalAccent = staticCompositionLocalOf { Myty.Blue }

/** Readable text/icon colour on top of [c]. */
fun onColor(c: Color) = if (c.luminance() > 0.5f) Color.Black else Color.White

@Composable
fun MytyTheme(accent: Color = Myty.Blue, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = onColor(accent),
            background = Myty.Bg,
            surface = Myty.Tile,
            onSurface = Myty.White,
            onBackground = Myty.White,
        ),
    ) {
        CompositionLocalProvider(
            LocalAccent provides accent,
            LocalTextStyle provides TextStyle(
                fontFamily = Myty.Font,
                color = Myty.White,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            content = content,
        )
    }
}

/** The Myty mark: rounded square with four round holes. [holes] should match whatever is behind it. */
@Composable
fun MytyLogo(modifier: Modifier, color: Color = Myty.White, holes: Color = Myty.Brand) {
    Canvas(modifier) {
        val s = size.minDimension
        val o = Offset((size.width - s) / 2, (size.height - s) / 2)
        drawRoundRect(color, o, Size(s, s), CornerRadius(s * 0.19f))
        val r = s * 0.19f
        val inset = s * 0.27f
        for (x in floatArrayOf(inset, s - inset)) for (y in floatArrayOf(inset, s - inset)) {
            drawCircle(holes, r, o + Offset(x, y))
        }
    }
}
