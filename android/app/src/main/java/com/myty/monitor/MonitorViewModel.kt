package com.myty.monitor

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myty.monitor.data.Discovery
import com.myty.monitor.data.PcClient
import com.myty.monitor.data.Stats
import com.myty.monitor.data.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class GaugeMode { Load, Temp }
enum class ProcSort { Cpu, Gpu, Ram }

/** Highest values seen, used to scale the clock / fan bars (there's no fixed "max" for those). */
data class Peaks(
    val cpuClock: Double = 5000.0,
    val gpuClock: Double = 2500.0,
    val cpuFan: Double = 2000.0,
    val gpuFan: Double = 2000.0,
)

data class UiState(
    val target: Target? = null,
    val stats: Stats? = null,
    /** False until the first successful poll, and again after two misses in a row. */
    val live: Boolean = false,
    val everConnected: Boolean = false,
    val peaks: Peaks = Peaks(),
    val netDown: List<Float> = emptyList(),
    val netUp: List<Float> = emptyList(),
    val gaugeMode: GaugeMode = GaugeMode.Load,
    val procSort: ProcSort = ProcSort.Cpu,
)

class MonitorViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("myty", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        UiState(
            gaugeMode = GaugeMode.entries.getOrElse(prefs.getInt("gauge", 0)) { GaugeMode.Load },
            procSort = ProcSort.entries.getOrElse(prefs.getInt("sort", 0)) { ProcSort.Cpu },
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    val found: StateFlow<List<Target>> = Discovery.scan(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    private var poll: Job? = null

    init {
        // Jump straight back to the last PC.
        val host = prefs.getString("host", null)
        if (host != null) {
            connect(Target(host, prefs.getInt("port", Target.DEFAULT_PORT), prefs.getString("name", host) ?: host))
        }
    }

    fun connect(target: Target) {
        prefs.edit()
            .putString("host", target.host)
            .putInt("port", target.port)
            .putString("name", target.name)
            .apply()
        poll?.cancel()
        _state.update { it.copy(target = target, stats = null, live = false, everConnected = false, peaks = Peaks(),
            netDown = emptyList(), netUp = emptyList()) }
        poll = viewModelScope.launch(Dispatchers.IO) { pollLoop(target) }
    }

    fun disconnect() {
        poll?.cancel()
        prefs.edit().remove("host").apply()
        _state.update { it.copy(target = null, stats = null, live = false) }
    }

    fun toggleGauge() {
        val next = if (_state.value.gaugeMode == GaugeMode.Load) GaugeMode.Temp else GaugeMode.Load
        prefs.edit().putInt("gauge", next.ordinal).apply()
        _state.update { it.copy(gaugeMode = next) }
    }

    fun sortBy(sort: ProcSort) {
        prefs.edit().putInt("sort", sort.ordinal).apply()
        _state.update { it.copy(procSort = sort) }
    }

    private suspend fun kotlinx.coroutines.CoroutineScope.pollLoop(target: Target) {
        var misses = 0
        while (isActive) {
            val started = SystemClock.elapsedRealtime()
            try {
                val s = PcClient.stats(target)
                if (!isActive) return // switched PC while this request was in flight
                misses = 0
                _state.update { st ->
                    val p = st.peaks
                    st.copy(
                        target = st.target?.copy(name = s.host),
                        stats = s,
                        live = true,
                        everConnected = true,
                        peaks = Peaks(
                            cpuClock = maxOf(p.cpuClock, s.cpu.clock ?: 0.0),
                            gpuClock = maxOf(p.gpuClock, s.gpu?.clock ?: 0.0),
                            cpuFan = maxOf(p.cpuFan, s.cpu.fan ?: 0.0),
                            gpuFan = maxOf(p.gpuFan, s.gpu?.fan ?: 0.0),
                        ),
                        netDown = (st.netDown + s.net.down.toFloat()).takeLast(HISTORY),
                        netUp = (st.netUp + s.net.up.toFloat()).takeLast(HISTORY),
                    )
                }
            } catch (e: Exception) {
                if (++misses >= 2) _state.update { it.copy(live = false) }
            }
            val wait = 1000 - (SystemClock.elapsedRealtime() - started)
            delay(wait.coerceAtLeast(100))
        }
    }

    private companion object {
        const val HISTORY = 60
    }
}
