package com.myty.monitor.data

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * Finds PCs running Myty Monitor: listens for their UDP beacons on 5318, sends "MYTY?" queries,
 * and checks whether a PC is reachable over USB (adb reverse → 127.0.0.1).
 */
object Discovery {
    private const val UDP_PORT = 5318
    private const val EXPIRE_MS = 8000L

    fun scan(context: Context): Flow<List<Target>> = callbackFlow {
        val found = LinkedHashMap<String, Pair<Target, Long>>()
        var usb: Target? = null

        // The receive, query and USB-probe coroutines run on different IO threads.
        fun publish() = synchronized(found) {
            val now = System.currentTimeMillis()
            found.entries.removeAll { now - it.value.second > EXPIRE_MS }
            trySend(listOfNotNull(usb) + found.values.map { it.first })
        }

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("myty-discovery").apply { setReferenceCounted(false); acquire() }

        val socket = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = 1000
                bind(InetSocketAddress(UDP_PORT))
            }
        }.getOrNull()

        if (socket != null) {
            launch {
                val buf = ByteArray(1024)
                while (isActive) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        publish()
                        continue
                    } catch (_: Exception) {
                        break
                    }
                    val o = runCatching { JSONObject(String(packet.data, 0, packet.length)) }.getOrNull() ?: continue
                    if (o.optString("app") != "myty-monitor") continue
                    val host = packet.address.hostAddress ?: continue
                    val t = Target(host, o.optInt("port", Target.DEFAULT_PORT), o.optString("name", host))
                    synchronized(found) { found[t.host] = t to System.currentTimeMillis() }
                    publish()
                }
            }
            launch {
                val query = "MYTY?".toByteArray()
                val broadcast = InetAddress.getByName("255.255.255.255")
                while (isActive) {
                    runCatching { socket.send(DatagramPacket(query, query.size, broadcast, UDP_PORT)) }
                    delay(2000)
                }
            }
        }

        launch {
            while (isActive) {
                val probe = Target(Target.USB_HOST)
                val reachable = PcClient.hello(probe)?.let { probe.copy(name = it) }
                synchronized(found) { usb = reachable }
                publish()
                delay(2000)
            }
        }

        awaitClose {
            socket?.close()
            lock.release()
        }
    }.flowOn(Dispatchers.IO)
}
