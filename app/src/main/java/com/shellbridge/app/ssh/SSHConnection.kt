package com.shellbridge.app.ssh

import android.util.Log
import com.shellbridge.app.terminal.TerminalBuffer
import kotlinx.coroutines.*
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

data class SSHConnectionParams(
    val host: String,
    val port: Int = 22,
    val username: String = "runner",
    val useCloudflare: Boolean = false
)

class SSHConnection {
    companion object {
        private const val TAG = "SSHConnection"

        private val BLOCKED_KEX = setOf(
            "curve25519-sha256",
            "curve25519-sha256@libssh.org",
        )
    }

    private var sshClient: SSHClient? = null
    private var session: Session? = null
    private var shell: Session.Shell? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private val isConnected = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readJob: Job? = null
    private var cloudflareTunnel: CloudflareTunnel? = null

    var onOutput: ((String) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onTunnelReady: ((Int) -> Unit)? = null
    var onTunnelOutput: ((String) -> Unit)? = null
    var onPasswordRequired: (() -> Unit)? = null
    private var passwordDeferred: CompletableDeferred<String>? = null

    fun connect(params: SSHConnectionParams, terminal: TerminalBuffer) {
        Log.d(TAG, "=== CONNECT START === host=${params.host} port=${params.port} cloudflare=${params.useCloudflare}")
        scope.launch {
            try {
                if (params.useCloudflare) {
                    connectViaCloudflare(params, terminal)
                } else {
                    connectDirect(params, terminal)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Connection failed: ${e.message}")
                }
            }
        }
    }

    private fun createSSHClient(): SSHClient {
        Log.d(TAG, "Creating SSHClient (DH-only kex)...")
        val config = DefaultConfig()

        val allKex = config.keyExchangeFactories
        val filtered = allKex.filter { it.name !in BLOCKED_KEX }
        Log.d(TAG, "Key exchange: ${allKex.size} → ${filtered.size} factories")
        for (f in filtered) {
            Log.d(TAG, "  kex: ${f.name}")
        }
        config.keyExchangeFactories = filtered

        val client = SSHClient(config)
        client.setConnectTimeout(15000)
        client.setTimeout(30000)
        client.addHostKeyVerifier(PromiscuousVerifier())
        Log.d(TAG, "SSHClient created (timeout=30s)")
        return client
    }

    private suspend fun connectViaCloudflare(
        params: SSHConnectionParams,
        terminal: TerminalBuffer
    ) {
        Log.d(TAG, "Starting Cloudflare tunnel to ${params.host}")
        val tunnel = CloudflareTunnel(params.host)
        cloudflareTunnel = tunnel

        val portReady = CompletableDeferred<Int>()

        tunnel.onReady = { port ->
            Log.d(TAG, "Tunnel ready on port $port")
            portReady.complete(port)
        }
        tunnel.onError = { error ->
            Log.e(TAG, "Tunnel error: $error")
            portReady.completeExceptionally(Exception(error))
        }
        tunnel.onOutput = { line ->
            onTunnelOutput?.invoke(line)
        }

        tunnel.start()

        val port = withTimeout(60000) { portReady.await() }
        onTunnelReady?.invoke(port)

        Log.d(TAG, "Tunnel established on port $port, creating SSH client...")

        sshClient = createSSHClient()

        Log.d(TAG, "Connecting SSHJ to 127.0.0.1:$port ...")
        withContext(Dispatchers.IO) {
            connectWithSocket(sshClient!!, "127.0.0.1", port)
        }
        Log.d(TAG, "SSHJ connected!")

        setupShell(terminal, params.username, params.useCloudflare)
    }

    private suspend fun connectDirect(params: SSHConnectionParams, terminal: TerminalBuffer) {
        Log.d(TAG, "Direct SSH to ${params.host}:${params.port}")
        sshClient = createSSHClient()

        withContext(Dispatchers.IO) {
            connectWithSocket(sshClient!!, params.host, params.port)
        }
        Log.d(TAG, "SSHJ connected to ${params.host}:${params.port}!")

        setupShell(terminal, params.username, false)
    }

    private fun connectWithSocket(client: SSHClient, host: String, port: Int) {
        Log.d(TAG, "Step 1: TCP+SSH connect to $host:$port ...")
        client.connect(host, port)
        Log.d(TAG, "Step 1: Connected!")
    }

    private suspend fun setupShell(terminal: TerminalBuffer, username: String = "runner", useCloudflare: Boolean = false) {
        Log.d(TAG, "Step 3: Authenticating as '$username' (cloudflare=$useCloudflare)...")
        withContext(Dispatchers.IO) {
            try {
                sshClient!!.auth(username)
                Log.d(TAG, "Step 3a: Auth succeeded (none)")
            } catch (e: net.schmizz.sshj.userauth.UserAuthException) {
                if (useCloudflare) {
                    Log.e(TAG, "Cloudflare none auth failed: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onError?.invoke("Cloudflare auth failed. The tunnel server rejected passwordless login. Try authenticating with a key or check your Cloudflare Access config.")
                    }
                    throw Exception("Cloudflare auth failed")
                }
                Log.d(TAG, "None auth failed, trying password: ${e.message}")
                val deferred = CompletableDeferred<String>()
                passwordDeferred = deferred
                withContext(Dispatchers.Main) {
                    onPasswordRequired?.invoke()
                }
                val password = withTimeout(120000) { deferred.await() }
                passwordDeferred = null
                sshClient!!.authPassword(username, password)
                Log.d(TAG, "Step 3a: Auth succeeded (password)")
            } catch (e: Exception) {
                Log.e(TAG, "Auth error: ${e.message}")
                throw e
            }
        }

        Log.d(TAG, "Step 4: Starting SSH session...")
        withContext(Dispatchers.IO) {
            session = sshClient!!.startSession()
            Log.d(TAG, "Step 4a: Session opened, allocating PTY...")
            session!!.allocateDefaultPTY()
            Log.d(TAG, "Step 4b: PTY allocated, starting shell...")
            shell = session!!.startShell()
            inputStream = shell!!.inputStream
            outputStream = shell!!.outputStream
            Log.d(TAG, "Step 4c: Shell ready!")
        }

        isConnected.set(true)
        Log.d(TAG, "=== CONNECT COMPLETE ===")

        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try {
                while (isActive && isConnected.get()) {
                    val len = inputStream?.read(buffer) ?: -1
                    if (len == -1) {
                        Log.d(TAG, "Input stream EOF")
                        break
                    }
                    val text = String(buffer, 0, len)
                    withContext(Dispatchers.Main) {
                        terminal.write(text)
                        onOutput?.invoke(text)
                    }
                }
            } catch (e: Exception) {
                if (isConnected.get()) {
                    Log.e(TAG, "Read error: ${e.message}")
                    withContext(Dispatchers.Main) {
                        onError?.invoke("Read error: ${e.message}")
                    }
                }
            } finally {
                disconnect()
            }
        }
    }

    fun providePassword(password: String) {
        passwordDeferred?.complete(password)
    }

    fun sendInput(data: String) {
        if (!isConnected.get()) return
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(data.toByteArray())
                outputStream?.flush()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError?.invoke("Send error: ${e.message}")
                }
            }
        }
    }

    fun sendBytes(data: ByteArray) {
        if (!isConnected.get()) return
        scope.launch(Dispatchers.IO) {
            try {
                outputStream?.write(data)
                outputStream?.flush()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError?.invoke("Send error: ${e.message}")
                }
            }
        }
    }

    fun resize(rows: Int, cols: Int) {
        if (!isConnected.get()) return
        scope.launch(Dispatchers.IO) {
            try {
                shell?.changeWindowDimensions(cols, rows, 0, 0)
            } catch (_: Exception) {}
        }
    }

    fun disconnect() {
        if (!isConnected.compareAndSet(true, false)) return
        Log.d(TAG, "Disconnecting")
        readJob?.cancel()
        try {
            shell?.close()
            session?.close()
            sshClient?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        }
        sshClient = null
        session = null
        shell = null
        inputStream = null
        outputStream = null

        cloudflareTunnel?.stop()
        cloudflareTunnel = null

        scope.launch(Dispatchers.Main) {
            onDisconnect?.invoke()
        }
    }

    fun isConnected(): Boolean = isConnected.get()
}
