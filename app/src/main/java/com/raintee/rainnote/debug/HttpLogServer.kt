package com.raintee.rainnote.debug

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

object HttpLogServer {
    const val PORT = 48621
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        Thread {
            try {
                ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0")).use { server ->
                    AppLog.d("HttpLogServer", "listening on http://0.0.0.0:$PORT")
                    while (started) {
                        val socket = server.accept()
                        Thread { handle(socket) }.start()
                    }
                }
            } catch (error: Throwable) {
                started = false
                AppLog.e("HttpLogServer", "server stopped", error)
            }
        }.start()
    }

    private fun handle(socket: Socket) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine().orEmpty()
            while (!reader.readLine().isNullOrEmpty()) {
                // Drain headers.
            }
            val path = requestLine.split(" ").getOrNull(1).orEmpty()
            val body = when (path) {
                "/health" -> "ok"
                "/clear" -> {
                    AppLog.clear()
                    "cleared"
                }
                "/logs" -> AppLog.snapshot()
                else -> "RainNote log server\nGET /health\nGET /logs\nGET /clear"
            }
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            val status = if (path == "/" || path == "/health" || path == "/logs" || path == "/clear") "200 OK" else "404 Not Found"
            val headers = "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            it.getOutputStream().write(headers.toByteArray(StandardCharsets.UTF_8))
            it.getOutputStream().write(bytes)
            it.getOutputStream().flush()
        }
    }
}
