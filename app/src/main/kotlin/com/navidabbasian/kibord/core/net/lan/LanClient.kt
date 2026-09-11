package com.navidabbasian.kibord.core.net.lan

import com.navidabbasian.kibord.core.net.ClientLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * کلاینت مهمان روی شبکه‌ی محلی، مشترک بین بازی‌ها: به سوکت میزبان وصل
 * می‌شود، معرفی می‌کند و پیام‌های وضعیت را روی نخ اصلی تحویل می‌دهد.
 */
class LanClient<T>(
    private val scope: CoroutineScope,
    private val encode: (T) -> String,
    private val decode: (String) -> T?,
    private val onMessage: (T) -> Unit,
    /** بعد از یک اتصال موفق، قطع شدن ارتباط با میزبان */
    private val onDisconnected: () -> Unit,
) : ClientLink<T> {

    private var socket: Socket? = null
    private var writer: PrintWriter? = null

    /** نتیجه از راه callback: تهی یعنی وصل شد، در غیر این صورت پیام خطای فارسی */
    fun connect(host: String, port: Int, name: String, onResult: (String?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val s = Socket()
            var handshakeDone = false
            try {
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                s.tcpNoDelay = true
                val w = PrintWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8), true)
                val reader = s.getInputStream().bufferedReader(Charsets.UTF_8)

                w.println(lanJson.encodeToString(LanHello.serializer(), LanHello(name)))
                val welcome = runCatching {
                    lanJson.decodeFromString(LanWelcome.serializer(), reader.readLine() ?: "")
                }.getOrNull()
                if (welcome == null || !welcome.ok) {
                    s.close()
                    withContext(Dispatchers.Main) {
                        onResult(welcome?.error?.ifBlank { null } ?: "اتصال برقرار نشد")
                    }
                    return@launch
                }

                socket = s
                writer = w
                handshakeDone = true
                withContext(Dispatchers.Main) { onResult(null) }

                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val msg = decode(line) ?: continue
                        withContext(Dispatchers.Main) { onMessage(msg) }
                    }
                } finally {
                    close()
                    withContext(Dispatchers.Main) { onDisconnected() }
                }
            } catch (_: Exception) {
                try {
                    s.close()
                } catch (_: Exception) {
                }
                if (!handshakeDone) {
                    withContext(Dispatchers.Main) { onResult("اتصال برقرار نشد — آدرس و وای‌فای را بررسی کنید") }
                }
            }
        }
    }

    override fun send(msg: T) {
        val w = writer ?: return
        scope.launch(Dispatchers.IO) {
            try {
                synchronized(w) { w.println(encode(msg)) }
            } catch (_: Exception) {
            }
        }
    }

    override fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        writer = null
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 4000
    }
}
