package com.navidabbasian.kibord.core.net.lan

import com.navidabbasian.kibord.core.net.TargetedHostLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * سرور میزبان روی شبکه‌ی محلی، مشترک بین همه‌ی بازی‌ها: هر مهمان یک سوکت
 * دارد و پیام‌ها خط‌به‌خط (هر خط یک JSON از نوع [T]) رد و بدل می‌شوند.
 * همه‌ی رویدادها روی نخ اصلی تحویل داده می‌شوند تا منطق بازی ساده بماند.
 */
class LanServer<T>(
    private val scope: CoroutineScope,
    private val encode: (T) -> String,
    private val decode: (String) -> T?,
    /** بررسی ورود: خطای فارسی برگردانید تا رد شود، تهی یعنی خوش آمدی */
    private val onClientJoin: (name: String) -> String?,
    private val onCommand: (playerName: String, msg: T) -> Unit,
    private val onClientDisconnected: (playerName: String) -> Unit,
    /** وضعیت فعلی برای ارسال مستقیم به مهمانِ تازه‌وصل‌شده (از دیدِ همان مهمان) */
    private val latestStateFor: (playerName: String) -> T?,
) : TargetedHostLink<T> {

    private class Conn(val socket: Socket, val writer: PrintWriter) {
        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private val clients = ConcurrentHashMap<String, Conn>()
    private var serverSocket: ServerSocket? = null

    var port: Int = 0
        private set

    /** راه‌اندازی روی اولین پورت آزاد از بازه‌ی ثابت؛ ناموفق → false */
    fun start(): Boolean {
        for (p in BASE_PORT..BASE_PORT + 10) {
            try {
                serverSocket = ServerSocket(p)
                port = p
                break
            } catch (_: Exception) {
            }
        }
        val ss = serverSocket ?: return false
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val socket = try {
                    ss.accept()
                } catch (_: Exception) {
                    break
                }
                handleClient(socket)
            }
        }
        return true
    }

    private fun handleClient(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            var name: String? = null
            var conn: Conn? = null
            try {
                socket.tcpNoDelay = true
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)

                // اولین خط باید معرفی باشد
                val hello = runCatching {
                    lanJson.decodeFromString(LanHello.serializer(), reader.readLine() ?: return@launch)
                }.getOrNull() ?: return@launch
                val error = withContext(Dispatchers.Main) { onClientJoin(hello.hello) }
                writer.println(lanJson.encodeToString(LanWelcome.serializer(), LanWelcome(ok = error == null, error = error ?: "")))
                if (error != null) {
                    socket.close()
                    return@launch
                }

                name = hello.hello
                conn = Conn(socket, writer)
                clients.put(hello.hello, conn)?.close()

                // عکس فعلی وضعیت مستقیم برای همین مهمان — پخشِ لحظه‌ی پیوستن به او نرسیده
                withContext(Dispatchers.Main) { latestStateFor(hello.hello) }?.let { state ->
                    val line = encode(state)
                    synchronized(writer) { writer.println(line) }
                }

                while (isActive) {
                    val line = reader.readLine() ?: break
                    val msg = decode(line) ?: continue
                    withContext(Dispatchers.Main) { onCommand(hello.hello, msg) }
                }
            } catch (_: Exception) {
            } finally {
                val n = name
                if (n != null && conn != null && clients.remove(n, conn)) {
                    conn.close()
                    withContext(Dispatchers.Main) { onClientDisconnected(n) }
                }
            }
        }
    }

    /** ارسال یک پیام به همه‌ی مهمان‌های متصل */
    override fun broadcast(msg: T) {
        val encoded = encode(msg)
        clients.values.forEach { conn -> write(conn, encoded) }
    }

    /** ارسال به یک مهمانِ مشخص — اگر وصل نیست، بی‌سروصدا نادیده گرفته می‌شود */
    override fun sendTo(playerName: String, msg: T) {
        val conn = clients[playerName] ?: return
        write(conn, encode(msg))
    }

    private fun write(conn: Conn, encoded: String) {
        scope.launch(Dispatchers.IO) {
            try {
                synchronized(conn.writer) { conn.writer.println(encoded) }
            } catch (_: Exception) {
            }
        }
    }

    override fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        clients.values.forEach { it.close() }
        clients.clear()
    }

    companion object {
        const val BASE_PORT = 52580
    }
}
