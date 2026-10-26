package com.myty.monitor

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myty.monitor.data.Discovery
import com.myty.monitor.data.PcClient
import com.myty.monitor.data.Stats
import com.myty.monitor.data.Target
import com.myty.monitor.layout.Grid
import com.myty.monitor.layout.Tile
import com.myty.monitor.layout.TileKind
import com.myty.monitor.layout.TileLayout
import com.myty.monitor.layout.TileSize
import com.myty.monitor.ui.Myty
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
import org.json.JSONArray
import org.json.JSONObject

data class UiState(
    val target: Target? = null,
    val stats: Stats? = null,
    /** False until the first successful poll, and again after two misses in a row. */
    val live: Boolean = false,
    val everConnected: Boolean = false,
    val netDown: List<Float> = emptyList(),
    val netUp: List<Float> = emptyList(),
)

/** Home screen look: the tile layout (shared by both orientations) and colours. */
data class Look(
    val tiles: List<Tile>,
    val accent: Color,
    val custom: List<Color?>,
)

class MonitorViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("myty", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _look = MutableStateFlow(loadLook())
    val look: StateFlow<Look> = _look.asStateFlow()

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
        _state.update { UiState(target = target) }
        poll = viewModelScope.launch(Dispatchers.IO) { pollLoop(target) }
    }

    fun disconnect() {
        poll?.cancel()
        prefs.edit().remove("host").apply()
        _state.update { UiState() }
    }

    fun setTiles(tiles: List<Tile>) {
        _look.update { it.copy(tiles = tiles) }
        prefs.edit().putString("tiles", tilesToJson(tiles)).apply()
    }

    fun setAccent(c: Color) {
        _look.update { it.copy(accent = c) }
        prefs.edit().putInt("accent", c.toArgb()).apply()
    }

    fun setCustomColor(slot: Int, c: Color) {
        _look.update { it.copy(custom = it.custom.toMutableList().also { l -> l[slot] = c }) }
        prefs.edit().putString("custom", JSONArray(_look.value.custom.map { it?.toArgb() ?: JSONObject.NULL }).toString()).apply()
    }

    private fun loadLook(): Look {
        // The first custom slot starts out holding the default blue, so there's always a way back to it.
        val custom = MutableList<Color?>(CUSTOM_SLOTS) { if (it == 0) Myty.Blue else null }
        runCatching {
            val a = JSONArray(prefs.getString("custom", "[]"))
            for (i in 0 until minOf(a.length(), CUSTOM_SLOTS)) if (!a.isNull(i)) custom[i] = Color(a.getInt(i))
        }
        return Look(
            // Older versions kept a separate layout per orientation; carry the landscape one over.
            tiles = tilesFromJson(prefs.getString("tiles", null) ?: prefs.getString("tiles_land", null), Grid.Main),
            accent = if (prefs.contains("accent")) Color(prefs.getInt("accent", 0)) else Myty.Blue,
            custom = custom,
        )
    }

    private fun tilesToJson(tiles: List<Tile>) = JSONArray(tiles.map {
        JSONObject().put("id", it.id).put("kind", it.kind.name).put("size", it.size.name)
            .put("col", it.col).put("row", it.row).put("style", it.style)
    }).toString()

    private fun tilesFromJson(text: String?, grid: Grid): List<Tile> {
        if (text == null) return TileLayout.defaultLayout()
        return runCatching {
            val a = JSONArray(text)
            val placed = mutableListOf<Tile>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val t = grid.clamp(Tile(
                    id = o.getLong("id"),
                    kind = TileKind.valueOf(o.getString("kind")),
                    size = TileSize.valueOf(o.getString("size")),
                    col = o.getInt("col"),
                    row = o.getInt("row"),
                    style = o.optInt("style", 1),
                ))
                if (TileLayout.isFree(t, placed, grid)) placed += t // drop anything corrupt
            }
            placed
        }.getOrElse { TileLayout.defaultLayout() }
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
                    st.copy(
                        target = st.target?.copy(name = s.host),
                        stats = s,
                        live = true,
                        everConnected = true,
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
        const val CUSTOM_SLOTS = 3
    }
}
