package com.myty.monitor.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Plain HTTP to the PC app. Blocking; call from Dispatchers.IO. */
object PcClient {
    fun stats(t: Target): Stats = Stats.parse(get(t, "/api/stats", 1500))

    /** Returns the PC's name if a Myty Monitor server answers at [t], else null. */
    fun hello(t: Target, timeoutMs: Int = 800): String? = runCatching {
        val o = JSONObject(get(t, "/api/hello", timeoutMs))
        if (o.optString("app") == "myty-monitor") o.optString("name", t.host) else null
    }.getOrNull()

    private fun get(t: Target, path: String, timeoutMs: Int): String {
        val conn = URL("http", t.host, t.port, path).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.useCaches = false
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
