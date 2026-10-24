package com.myty.monitor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myty.monitor.GaugeMode
import com.myty.monitor.ProcSort
import com.myty.monitor.UiState
import com.myty.monitor.data.ProcStats
import com.myty.monitor.data.Stats
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val Gap = 8.dp

@Composable
fun Dashboard(
    state: UiState,
    landscape: Boolean,
    onToggleGauge: () -> Unit,
    onSort: (ProcSort) -> Unit,
    onChangePc: () -> Unit,
) {
    // Nudge everything by a couple of dp each minute so a static layout doesn't burn into OLED screens.
    var shift by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            shift = Random.nextInt(-2, 3) to Random.nextInt(-2, 3)
        }
    }

    val s = state.stats
    Box(Modifier.fillMaxSize().background(Myty.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .offset(shift.first.dp, shift.second.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            Header(state, onToggleGauge, onChangePc)
            if (landscape) {
                Row(Modifier.weight(1.25f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    CpuCard(state, Modifier.weight(1f).fillMaxHeight(), onToggleGauge)
                    GpuCard(state, Modifier.weight(1f).fillMaxHeight(), onToggleGauge)
                }
                Row(Modifier.weight(0.85f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    RamCard(s, Modifier.weight(0.8f).fillMaxHeight())
                    NetworkCard(state, Modifier.weight(0.8f).fillMaxHeight())
                    StorageCard(s, Modifier.weight(0.75f).fillMaxHeight())
                }
                ProcessCard(s, state.procSort, onSort, Modifier.weight(0.75f).fillMaxWidth())
            } else {
                CpuCard(state, Modifier.weight(1f).fillMaxWidth(), onToggleGauge)
                GpuCard(state, Modifier.weight(1f).fillMaxWidth(), onToggleGauge)
                Row(Modifier.weight(0.75f), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    RamCard(s, Modifier.weight(1f).fillMaxHeight())
                    NetworkCard(state, Modifier.weight(1f).fillMaxHeight())
                }
                StorageCard(s, Modifier.weight(0.6f).fillMaxWidth())
                ProcessCard(s, state.procSort, onSort, Modifier.weight(1f).fillMaxWidth())
            }
        }

        AnimatedVisibility(!state.live, enter = fadeIn(), exit = fadeOut()) {
            ConnectionOverlay(state, onChangePc)
        }
    }
}

@Composable
private fun Header(state: UiState, onToggleGauge: () -> Unit, onChangePc: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(Myty.Blue), contentAlignment = Alignment.Center) {
            MytyLogo(Modifier.size(12.dp))
        }
        Text("myty", color = Myty.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 7.dp))
        Text("monitor", color = Myty.Dim, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))

        Spacer(Modifier.weight(1f))

        val t = state.target
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.live) Myty.BlueBright else Myty.Warm))
        Text(
            (t?.name ?: "") + if (t?.isUsb == true) "  ·  USB" else "  ·  Wi-Fi",
            color = Myty.Dim, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.padding(start = 6.dp, end = 14.dp),
        )
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Myty.Text, fontSize = 14.sp)
        Box {
            Text("•••", color = Myty.Dim, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { menu = true }
                    .padding(horizontal = 10.dp, vertical = 2.dp))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (state.gaugeMode == GaugeMode.Load) "Gauges: show temperature" else "Gauges: show load") },
                    onClick = { menu = false; onToggleGauge() },
                )
                DropdownMenuItem(text = { Text("Change PC") }, onClick = { menu = false; onChangePc() })
            }
        }
    }
}

// ---- CPU / GPU -----------------------------------------------------------------------------------

@Composable
private fun CpuCard(state: UiState, modifier: Modifier, onToggleGauge: () -> Unit) {
    val c = state.stats?.cpu
    ChipCard(
        title = "CPU",
        name = c?.name,
        load = c?.load,
        temp = c?.temp,
        clock = c?.clock, clockMax = state.peaks.cpuClock,
        fan = c?.fan, fanMax = state.peaks.cpuFan,
        extra = c?.power?.let { "${it.toInt()} W" },
        mode = state.gaugeMode,
        onToggleGauge = onToggleGauge,
        modifier = modifier,
    )
}

@Composable
private fun GpuCard(state: UiState, modifier: Modifier, onToggleGauge: () -> Unit) {
    val g = state.stats?.gpu
    val vram = if (g?.vramUsed != null && g.vramTotal != null && g.vramTotal > 0)
        "VRAM ${gb(g.vramUsed / 1024)}/${gb(g.vramTotal / 1024)} GB" else null
    val extra = listOfNotNull(g?.hotspot?.let { "Hot spot ${it.toInt()}°" }, g?.power?.let { "${it.toInt()} W" }, vram)
        .joinToString("  ·  ").ifEmpty { null }
    ChipCard(
        title = "GPU",
        name = g?.name ?: if (state.stats != null) "No GPU sensors found" else null,
        load = g?.load,
        temp = g?.temp,
        clock = g?.clock, clockMax = state.peaks.gpuClock,
        fan = g?.fan, fanMax = state.peaks.gpuFan,
        extra = extra,
        mode = state.gaugeMode,
        onToggleGauge = onToggleGauge,
        modifier = modifier,
    )
}

@Composable
private fun ChipCard(
    title: String,
    name: String?,
    load: Double?,
    temp: Double?,
    clock: Double?, clockMax: Double,
    fan: Double?, fanMax: Double,
    extra: String?,
    mode: GaugeMode,
    onToggleGauge: () -> Unit,
    modifier: Modifier,
) {
    BentoCard(title, modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onToggleGauge),
        subtitle = name) {
        Row(Modifier.weight(1f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (mode == GaugeMode.Load) {
                RingGauge((load ?: 0.0).toFloat() / 100f, num(load), "%", "Load", Myty.BlueBright,
                    Modifier.weight(0.42f).fillMaxHeight())
            } else {
                RingGauge((temp ?: 0.0).toFloat() / 100f, num(temp), "°", "Temp", Myty.heat(temp),
                    Modifier.weight(0.42f).fillMaxHeight())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                if (mode == GaugeMode.Load) {
                    StatBar("Temperature", num(temp), "°", (temp ?: 0.0).toFloat() / 100f, Myty.heat(temp))
                } else {
                    StatBar("Load", num(load), "%", (load ?: 0.0).toFloat() / 100f, Myty.BlueBright)
                }
                StatBar("Clock", num(clock), "MHz", ((clock ?: 0.0) / clockMax).toFloat(), Myty.Bar)
                StatBar("Fan", num(fan), "RPM", ((fan ?: 0.0) / fanMax).toFloat(), Myty.Bar)
            }
        }
        LoadStatus(LoadLevel.ofPercent(load), extra = extra)
    }
}

// ---- RAM / Network / Storage ---------------------------------------------------------------------

@Composable
private fun RamCard(s: Stats?, modifier: Modifier) {
    val r = s?.ram
    BentoCard("RAM", modifier, trailing = {
        if (r != null) Text("${gb(r.used)} / ${gb(r.total)} GB", color = Myty.Dim, fontSize = 11.sp)
    }) {
        RingGauge(
            ((r?.load ?: 0.0) / 100).toFloat(), num(r?.load), "%", "Load",
            if ((r?.load ?: 0.0) > 90) Myty.Warm else Myty.BlueBright,
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun NetworkCard(state: UiState, modifier: Modifier) {
    val n = state.stats?.net
    BentoCard("Network", modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val size = (maxHeight.value * 0.3f).coerceIn(12f, 34f)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight().padding(top = 6.dp, bottom = 4.dp, end = 10.dp)) {
                    val top = ((state.netDown + state.netUp).maxOrNull() ?: 0f).coerceAtLeast(64 * 1024f)
                    Sparkline(state.netDown, Myty.BlueBright, Modifier.fillMaxSize(), max = top)
                    Sparkline(state.netUp, Myty.Bar, Modifier.fillMaxSize(), max = top)
                }
                Column(horizontalAlignment = Alignment.End) {
                    NetRow(n?.up, up = true, size = size)
                    NetRow(n?.down, up = false, size = size)
                }
            }
        }
        val total = (n?.down ?: 0.0) + (n?.up ?: 0.0)
        LoadStatus(
            when {
                total < 100_000 -> LoadLevel.Minimal
                total < 2_000_000 -> LoadLevel.Light
                total < 20_000_000 -> LoadLevel.Moderate
                else -> LoadLevel.Heavy
            }
        )
    }
}

@Composable
private fun NetRow(bytesPerSec: Double?, up: Boolean, size: Float) {
    val (v, u) = rate(bytesPerSec)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ValueUnit(v, u, size)
        Spacer(Modifier.width(6.dp))
        Arrow(up, lit = (bytesPerSec ?: 0.0) > 10_000, height = (size * 0.85f).dp)
    }
}

@Composable
private fun StorageCard(s: Stats?, modifier: Modifier) {
    BentoCard("Storage", modifier) {
        val drives = s?.drives.orEmpty().take(6)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
            // Rows shrink to fit however many drives the PC has.
            val rowH = if (drives.isEmpty()) 0f else (maxHeight.value / drives.size).coerceAtMost(26f)
            val barH = (rowH * 0.68f).coerceIn(6f, 16f)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                drives.forEach { d ->
                    val frac = if (d.total > 0) d.used.toFloat() / d.total else 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.name.trimEnd('\\') + "\\", color = Myty.Dim, fontSize = (barH + 1).sp,
                            fontWeight = FontWeight.Light, modifier = Modifier.width(36.dp))
                        Box(
                            Modifier.weight(1f).height(barH.dp).clip(RoundedCornerShape(2.dp)).background(Myty.Bg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.align(Alignment.CenterStart).fillMaxWidth(frac).fillMaxHeight()
                                    .background(if (frac > 0.92f) Myty.Hot.copy(alpha = 0.75f) else Myty.Bar)
                            )
                            if (barH >= 11) {
                                Text("${size(d.used)} / ${size(d.total)}", color = Color.White,
                                    fontSize = (barH * 0.58f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Processes -----------------------------------------------------------------------------------

private val ColW = 64.dp

@Composable
private fun ProcessCard(s: Stats?, sort: ProcSort, onSort: (ProcSort) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(Myty.Card).border(1.dp, Myty.Border, shape)
    ) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Top Processes", color = Myty.Text, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            HeaderCell("CPU", sort == ProcSort.Cpu) { onSort(ProcSort.Cpu) }
            HeaderCell("GPU", sort == ProcSort.Gpu) { onSort(ProcSort.Gpu) }
            HeaderCell("RAM", sort == ProcSort.Ram) { onSort(ProcSort.Ram) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Myty.Border))
        val procs = s?.procs.orEmpty().sortedByDescending {
            when (sort) {
                ProcSort.Cpu -> it.cpu
                ProcSort.Gpu -> it.gpu
                ProcSort.Ram -> it.ram.toDouble()
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val rowH = 22f
            val fit = (maxHeight.value / rowH).toInt().coerceAtLeast(0)
            Column(Modifier.fillMaxSize().padding(top = 2.dp)) {
                procs.take(fit).forEach { ProcRow(it) }
            }
        }
    }
}

@Composable
private fun HeaderCell(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.width(ColW).fillMaxHeight().clickable(onClick = onClick).padding(end = 10.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (active) Myty.Text else Myty.Dim, fontSize = 12.sp)
        if (active) Text(" ⌄", color = Myty.Text, fontSize = 12.sp)
    }
}

@Composable
private fun ProcRow(p: ProcStats) {
    Row(Modifier.fillMaxWidth().heightIn(min = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(p.name, color = Myty.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 12.dp))
        Cell(num(p.cpu), "%")
        Cell(num(p.gpu), "%")
        val mb = p.ram / (1024.0 * 1024)
        if (mb >= 10_000) Cell(gb(mb / 1024), "GB") else Cell(mb.toInt().toString(), "MB")
    }
}

@Composable
private fun Cell(value: String, unit: String) {
    Row(Modifier.width(ColW).padding(end = 10.dp), horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom) {
        Text(value, color = Myty.Text, fontSize = 12.sp, maxLines = 1)
        Text(unit, color = Myty.Dim, fontSize = 8.sp, modifier = Modifier.padding(start = 1.dp, bottom = 1.dp))
    }
}

// ---- Overlay ---------------------------------------------------------------------------------------

@Composable
private fun ConnectionOverlay(state: UiState, onChangePc: () -> Unit) {
    val name = state.target?.name ?: "PC"
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).background(Myty.Blue).padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MytyLogo(Modifier.size(36.dp))
            Text(
                if (state.everConnected) "Lost connection to $name" else "Connecting to $name…",
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                if (state.target?.isUsb == true) "Check the cable, USB debugging, and that Myty Monitor is running on the PC."
                else "Check Myty Monitor is running on the PC and both are on the same network.",
                color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            Button(
                onClick = onChangePc,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Myty.Blue),
            ) { Text("Change PC") }
        }
    }
}

// ---- Formatting ------------------------------------------------------------------------------------

private fun num(v: Double?): String = v?.let { Math.round(it).toString() } ?: "--"

private fun gb(v: Double): String = if (v >= 100) v.toInt().toString() else String.format(Locale.US, "%.1f", v)

private fun size(bytes: Long): String {
    val tb = bytes / 1e12
    return if (tb >= 1) String.format(Locale.US, "%.1fTB", tb)
    else String.format(Locale.US, "%.0fGB", bytes / 1e9)
}

private fun rate(bytesPerSec: Double?): Pair<String, String> {
    val b = bytesPerSec ?: return "--" to "KB/s"
    fun f(v: Double) = if (v < 10) String.format(Locale.US, "%.1f", v).removeSuffix(".0") else v.toInt().toString()
    return when {
        b < 1024 * 1024 -> f(b / 1024) to "KB/s"
        b < 1024.0 * 1024 * 1024 -> f(b / (1024 * 1024)) to "MB/s"
        else -> f(b / (1024.0 * 1024 * 1024)) to "GB/s"
    }
}
