package com.shellbridge.app.ssh

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

class CloudflareTunnel(private val hostname: String) {

    companion object {
        private const val TAG = "CloudflareTunnel"
    }

    private var webSocket: WebSocket? = null
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    var localPort: Int = 0
        private set

    var onReady: ((Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onOutput: ((String) -> Unit)? = null

    private var clientSocket: Socket? = null
    private var clientIn: InputStream? = null
    private var clientOut: OutputStream? = null
    private val wsReady = AtomicBoolean(false)

    fun start() {
        if (isRunning.get()) return

        scope.launch {
            try {
                serverSocket = ServerSocket(0)
                serverSocket!!.reuseAddress = true
                localPort = serverSocket!!.localPort
                isRunning.set(true)

                Log.d(TAG, "Local server socket listening on port $localPort")
                onOutput?.invoke("Connecting to $hostname via WebSocket...")

                // Open WebSocket FIRST so the server banner is ready before SSHJ connects
                openWebSocket()

                onReady?.invoke(localPort)

                acceptAndPipe()
            } catch (e: Exception) {
                Log.e(TAG, "Tunnel start failed: ${e.message}", e)
                isRunning.set(false)
                onError?.invoke("Tunnel error: ${e.message}")
            }
        }
    }

    private fun acceptAndPipe() {
        scope.launch {
            try {
                val socket = withContext(Dispatchers.IO) {
                    serverSocket?.accept()
                } ?: return@launch

                Log.d(TAG, "SSH client connected, WebSocket already open")
                clientSocket = socket
                socket.tcpNoDelay = true
                socket.soTimeout = 0

                clientIn = socket.getInputStream()
                clientOut = socket.getOutputStream()

                val buffer = ByteArray(32768)
                val pendingBuffer = ByteArrayOutputStream()
                while (isRunning.get()) {
                    val read = withContext(Dispatchers.IO) {
                        try {
                            clientIn?.read(buffer) ?: -1
                        } catch (e: SocketException) {
                            -1
                        }
                    }
                    if (read == -1) break

                    if (!wsReady.get()) {
                        Log.w(TAG, "WS not ready, buffering ${read} bytes...")
                        pendingBuffer.write(buffer, 0, read)
                        continue
                    }

                    if (pendingBuffer.size() > 0) {
                        Log.d(TAG, "Sending pending data first: ${pendingBuffer.size()} bytes")
                        val pendingBytes = pendingBuffer.toByteArray().toByteString()
                        webSocket?.send(pendingBytes)
                        pendingBuffer.reset()
                    }

                    val sent = webSocket?.send(buffer.copyOfRange(0, read).toByteString(0, read)) ?: false
                    if (!sent) {
                        Log.e(TAG, "WS send failed")
                        break
                    }
                    val first32 = buffer.take(minOf(read, 32)).toByteArray()
                    val hex = first32.joinToString(" ") { String.format("%02x", it) }
                    val ascii = first32.map { b ->
                        val v = b.toInt() and 0xFF
                        if (v in 0x20..0x7E) v.toChar() else '.'
                    }.joinToString("")
                    Log.d(TAG, "WS sent: $read bytes  HEX[0..31]: $hex  ASCII: $ascii")
                }
                Log.d(TAG, "Read loop ended")
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Pipe error: ${e.message}")
                }
            } finally {
                cleanup()
            }
        }
    }

    private fun openWebSocket() {
        val request = Request.Builder()
            .url("wss://$hostname/")
            .header("Host", hostname)
            .header("User-Agent", "Go-http-client/1.1")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened to $hostname")
                wsReady.set(true)
                onOutput?.invoke("Tunnel ready")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val data = bytes.toByteArray()
                Log.d(TAG, "WS recv: ${data.size} bytes")
                if (data.size <= 64) {
                    val hex = data.joinToString(" ") { String.format("%02x", it) }
                    val ascii = data.map { b ->
                        val v = b.toInt() and 0xFF
                        if (v in 0x20..0x7E) v.toChar() else '.'
                    }.joinToString("")
                    Log.d(TAG, "  HEX: $hex")
                    Log.d(TAG, "  ASCII: $ascii")
                } else {
                    val first64 = data.take(64).toByteArray()
                    val hex = first64.joinToString(" ") { String.format("%02x", it) }
                    val ascii = first64.map { b ->
                        val v = b.toInt() and 0xFF
                        if (v in 0x20..0x7E) v.toChar() else '.'
                    }.joinToString("")
                    Log.d(TAG, "  HEX (first 64): $hex ...")
                    Log.d(TAG, "  ASCII (first 64): $ascii ...")
                }
                writeToClient(data)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.w(TAG, "Unexpected text: ${text.take(100)}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WS closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WS closed: $code $reason")
                wsReady.set(false)
                cleanup()
                onError?.invoke("Tunnel closed: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WS failure: ${t.message}", t)
                wsReady.set(false)
                onError?.invoke("Tunnel error: ${t.message}")
            }
        })
    }

    private fun writeToClient(data: ByteArray) {
        try {
            val out = clientOut
            if (out != null && clientSocket?.isClosed == false) {
                out.write(data)
                out.flush()
                Log.d(TAG, "Client write: ${data.size} bytes")
            } else {
                Log.w(TAG, "Client not connected, dropping ${data.size} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client write error: ${e.message}")
            cleanup()
        }
    }

    private fun cleanup() {
        try {
            clientIn?.close()
            clientOut?.close()
            clientSocket?.close()
        } catch (_: Exception) {}
        clientIn = null
        clientOut = null
        clientSocket = null
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        Log.d(TAG, "Stopping tunnel")
        try {
            webSocket?.close(1000, "bye")
            serverSocket?.close()
            cleanup()
        } catch (_: Exception) {}
        webSocket = null
        serverSocket = null
        scope.cancel()
    }

    fun isRunning(): Boolean = isRunning.get()
}
