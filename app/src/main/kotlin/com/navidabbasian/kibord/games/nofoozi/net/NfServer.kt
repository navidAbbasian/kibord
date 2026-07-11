package com.navidabbasian.kibord.games.nofoozi.net

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
 * سرور میزبان نفوذی: هر گوشی مهمان یک سوکت دارد. پیام‌ها خط‌به‌خط JSON هستند.
 * همه‌ی رویدادها روی نخ اصلی تحویل داده می‌شوند تا منطق بازی ساده بماند.
 */
class NfServer(
    private val scope: CoroutineScope,
    /** بررسی ورود: خطای فارسی برگردانید تا رد شود، تهی یعنی خوش آمدی */
    private val onClientJoin: (name: String) -> String?,
    private val onCommand: (playerName: String, msg: NfMessage) -> Unit,
    private val onClientDisconnected: (playerName: String) -> Unit,
    /** وضعیت فعلی برای ارسال مستقیم به مهمان تازه‌وصل‌شده */
    private val latestState: () -> NfMessage?,
) {

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

    /** راه‌اندازی روی اولین پورت آزاد از بازه‌ی ثابت؛ ناموفق → تهی */
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
                val hello = decodeNfMessage(reader.readLine() ?: return@launch) as? NfMessage.Hello
                    ?: return@launch
                val error = withContext(Dispatchers.Main) { onClientJoin(hello.name) }
                writer.println(NfMessage.Welcome(ok = error == null, error = error ?: "").encode())
                if (error != null) {
                    socket.close()
                    return@launch
                }

                name = hello.name
                conn = Conn(socket, writer)
                clients.put(hello.name, conn)?.close()

                // عکس فعلی وضعیت مستقیم برای همین مهمان — پخشِ لحظه‌ی پیوستن به او نرسیده
                withContext(Dispatchers.Main) { latestState() }?.let { state ->
                    synchronized(writer) { writer.println(state.encode()) }
                }

                while (isActive) {
                    val line = reader.readLine() ?: break
                    val msg = decodeNfMessage(line) ?: continue
                    withContext(Dispatchers.Main) { onCommand(hello.name, msg) }
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
    fun broadcast(msg: NfMessage) {
        val encoded = msg.encode()
        clients.values.forEach { conn ->
            scope.launch(Dispatchers.IO) {
                try {
                    synchronized(conn.writer) { conn.writer.println(encoded) }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        clients.values.forEach { it.close() }
        clients.clear()
    }

    companion object {
        const val BASE_PORT = 52545
    }
}
