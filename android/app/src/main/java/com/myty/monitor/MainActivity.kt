package com.myty.monitor

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myty.monitor.ui.ConnectScreen
import com.myty.monitor.ui.HomeScreen
import com.myty.monitor.ui.Myty
import com.myty.monitor.ui.MytyTheme

class MainActivity : ComponentActivity() {
    private val vm: MonitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { App(vm) }
    }
}

@Composable
private fun App(vm: MonitorViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val look by vm.look.collectAsStateWithLifecycle()

    MytyTheme(accent = look.accent) {
        if (state.target == null) {
            val found by vm.found.collectAsStateWithLifecycle()
            ConnectScreen(found, vm::connect)
            return@MytyTheme
        }

        BackHandler(onBack = vm::disconnect)
        BoxWithConstraints(Modifier.fillMaxSize().background(Myty.Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            // Laid out for a fixed design size and scaled to fill the screen,
            // so text and buttons look the same on a small phone and a big tablet.
            val landscape = maxWidth > maxHeight
            val (baseW, baseH) = if (landscape) 780f to 400f else 400f to 800f
            val scale = minOf(maxWidth.value / baseW, maxHeight.value / baseH).coerceIn(0.7f, 2.5f)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density * scale, fontScale = 1f)) {
                HomeScreen(
                    state = state,
                    portrait = !landscape,
                    tiles = look.tiles,
                    customColors = look.custom,
                    onTiles = vm::setTiles,
                    onAccent = vm::setAccent,
                    onCustomColor = vm::setCustomColor,
                    onChangePc = vm::disconnect,
                )
            }
        }
    }
}
