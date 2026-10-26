package com.myty.monitor.data

import org.json.JSONArray
import org.json.JSONObject

// Mirrors windows/MytyMonitor/Models.cs. Missing sensors arrive as null.

data class CpuStats(
    val name: String,
    val load: Double?,
    val temp: Double?,
    val clock: Double?,
    val fan: Double?,
    val power: Double?,
    val cores: List<CoreStats> = emptyList(),
)

/**
 * One CPU core. [tempSource] says where [temp] comes from: "core" (its own sensor, e.g. Intel), "ccd" (the
 * temperature of its chiplet; Ryzens don't measure each core) or "cpu" (one reading for the whole chip).
 */
data class CoreStats(val name: String, val temp: Double?, val clock: Double?, val load: Double?, val tempSource: String = "core")

data class GpuStats(
    val name: String,
    val load: Double?,
    val temp: Double?,
    val hotspot: Double?,
    val clock: Double?,
    val memClock: Double?,
    val fan: Double?,
    val power: Double?,
    val vramUsed: Double?,
    val vramTotal: Double?,
)

data class RamStats(val used: Double, val total: Double, val load: Double)
data class NetStats(val down: Double, val up: Double)
data class DriveStats(val name: String, val label: String, val used: Long, val total: Long)
data class ProcStats(val name: String, val cpu: Double, val gpu: Double, val ram: Long)

data class Stats(
    val host: String,
    val cpu: CpuStats,
    val gpu: GpuStats?,
    val ram: RamStats,
    val net: NetStats,
    val drives: List<DriveStats>,
    val procs: List<ProcStats>,
) {
    companion object {
        fun parse(text: String): Stats {
            val o = JSONObject(text)
            val c = o.getJSONObject("cpu")
            val g = o.optJSONObject("gpu")
            val r = o.getJSONObject("ram")
            val n = o.getJSONObject("net")
            return Stats(
                host = o.optString("host", "PC"),
                cpu = CpuStats(
                    name = c.optString("name", "CPU"),
                    load = c.num("load"),
                    temp = c.num("temp"),
                    clock = c.num("clock"),
                    fan = c.num("fan"),
                    power = c.num("power"),
                    cores = c.optJSONArray("cores").objects().map {
                        CoreStats(it.optString("name"), it.num("temp"), it.num("clock"), it.num("load"),
                            it.optString("tempSource", "core").ifEmpty { "core" })
                    },
                ),
                gpu = g?.let {
                    GpuStats(
                        name = it.optString("name", "GPU"),
                        load = it.num("load"),
                        temp = it.num("temp"),
                        hotspot = it.num("hotspot"),
                        clock = it.num("clock"),
                        memClock = it.num("memClock"),
                        fan = it.num("fan"),
                        power = it.num("power"),
                        vramUsed = it.num("vramUsed"),
                        vramTotal = it.num("vramTotal"),
                    )
                },
                ram = RamStats(r.optDouble("used", 0.0), r.optDouble("total", 0.0), r.optDouble("load", 0.0)),
                net = NetStats(n.optDouble("down", 0.0), n.optDouble("up", 0.0)),
                drives = o.optJSONArray("drives").objects().map {
                    DriveStats(it.optString("name"), it.optString("label"), it.optLong("used"), it.optLong("total"))
                },
                procs = o.optJSONArray("procs").objects().map {
                    ProcStats(it.optString("name"), it.optDouble("cpu", 0.0), it.optDouble("gpu", 0.0), it.optLong("ram"))
                },
            )
        }

        private fun JSONObject.num(key: String): Double? =
            if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

        private fun JSONArray?.objects(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}

/** A PC the phone can connect to. Port 5317 is the PC app's default. */
data class Target(val host: String, val port: Int = DEFAULT_PORT, val name: String = host) {
    val isUsb get() = host == USB_HOST

    companion object {
        const val DEFAULT_PORT = 5317
        /** With a USB cable, the PC app runs "adb reverse" so the PC appears on the phone's loopback. */
        const val USB_HOST = "127.0.0.1"

        /** Parses "192.168.1.20" or "192.168.1.20:5317". */
        fun parse(text: String): Target? {
            val t = text.trim().removePrefix("http://").trimEnd('/')
            if (t.isEmpty()) return null
            val colon = t.lastIndexOf(':')
            if (colon > 0) {
                val port = t.substring(colon + 1).toIntOrNull() ?: return null
                return Target(t.substring(0, colon), port)
            }
            return Target(t)
        }
    }
}
