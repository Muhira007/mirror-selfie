package id.mirrorselfie

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.server.DefaultSSLWebSocketServerFactory
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import org.webrtc.EglBase
import org.webrtc.VideoSink
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class SessionState(
    val mode: String = "idle", val status: String = "Siap", val code: String = "", val frames: Long = 0,
    val rttMs: Double? = null, val controlText: String = "", val camera: CameraSnapshot = CameraSnapshot(),
    val live: Boolean = false, val reconnecting: Boolean = false,
    val localBattery: Int = -1, val remoteBattery: Int = -1, val freeBytes: Long = -1
)

object Session {
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var state = SessionState(); private set
    @Volatile var listener: ((SessionState) -> Unit)? = null
    @Volatile var renderer: VideoSink? = null
    @Volatile private var engine: RtcEngine? = null
    private var server: WebSocketServer? = null
    private var client: WebSocketClient? = null
    @Volatile private var peerSocket: WebSocket? = null
    private var identity: SessionIdentity? = null
    private var app: Context? = null
    private var sessionId = ""
    @Volatile private var generation = 0
    private val frameCount = AtomicLong()
    private val lastFrame = AtomicLong()
    @Volatile private var rttMs: Double? = null
    private var controlText = ""
    private var cameraSnapshot = CameraSnapshot()
    private var cameraStateAt = 0L
    private var remoteBattery = -1
    private var freeBytes = -1L
    private var lastControlAt = 0L
    private var lastShutterAt = 0L
    private val controlRequests = LinkedHashSet<String>()
    private var pairing: Pairing? = null
    private var resumeSecret = ""
    private var resumeGate: ResumeGate? = null
    private var authenticated = false
    private var reconnectSince = 0L
    private var nextRetryAt = 0L
    private var retryNumber = 0
    private var lastRecoveryAt = 0L
    private var lastTick = 0L
    val eglContext: EglBase.Context? get() = engine?.egl?.eglBaseContext

    init {
        worker.scheduleWithFixedDelay({ runCatching { tick() }.onFailure { Log.e("MirrorP1", "Session tick: ${it.javaClass.simpleName}") } }, 1, 1, TimeUnit.SECONDS)
    }
    private fun tick() {
        if (engine == null) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTick >= 5000) { engine?.stats(); lastTick = now }
        if (state.mode == "host") {
            if (resumeGate?.expired(now) == true) { stop("Monitor belum tersambung kembali. Buat kode baru untuk melanjutkan."); return }
            if (peerSocket?.isOpen == true) sendCameraState()
        } else if (state.mode == "viewer") {
            if (reconnectSince > 0 && now - reconnectSince >= 120_000) {
                stop("Belum bisa tersambung kembali. Cek Wi-Fi lalu pindai kode baru."); return
            }
            if (!authenticated && (nextRetryAt == 0L || now >= nextRetryAt)) connectViewer()
            if (authenticated && now - lastFrame.get() > 3000 && now - lastRecoveryAt > 8000) {
                lastRecoveryAt = now
                send(JSONObject().put("type", "recover"))
            }
            publish()
        }
    }
    private fun publish(status: String = state.status, code: String = state.code, mode: String = state.mode) {
        val now = SystemClock.elapsedRealtime()
        val live = mode == "viewer" && authenticated && lastFrame.get() > 0 && now - lastFrame.get() < 2500 && now - cameraStateAt < 4000
        val next = SessionState(mode, status, code, frameCount.get(), rttMs, controlText, cameraSnapshot, live,
            mode == "viewer" && ((resumeSecret.isNotEmpty() && reconnectSince > 0) || retryNumber > 0), app?.let { DeviceStatus.battery(it) } ?: -1, remoteBattery, freeBytes)
        if (mode != state.mode || status != state.status) Log.i("MirrorP1", "$mode: $status")
        state = next
        main.post { listener?.invoke(next) }
    }
    private fun dispatch(block: () -> Unit) { worker.execute { runCatching(block).onFailure { fail(it) } } }
    private fun fail(error: Throwable) {
        Log.e("MirrorP1", "Session failure: ${error.javaClass.simpleName}")
        Log.e("MirrorP1", error.stackTrace.filter { it.className.startsWith("id.mirrorselfie") }.take(6).joinToString(" <- "))
        closeInternal()
        publish(mode = "idle", code = "", status = "Belum bisa terhubung. Cek Wi-Fi atau hotspot, lalu buat kode baru.")
        app?.stopService(Intent(app, ProjectionService::class.java))
    }
    private fun createEngine(context: Context): RtcEngine {
        val current = generation
        return RtcEngine(context, ::dispatch, { if (current == generation) send(it) },
            { if (current == generation) publish(status = it) }, { track ->
                track.addSink { frame ->
                    if (current == generation) {
                        val count = frameCount.incrementAndGet(); lastFrame.set(SystemClock.elapsedRealtime())
                        if (count == 1L) dispatch { if (current == generation) publish(status = "Terhubung") }
                        renderer?.onFrame(frame)
                    }
                }
            }, { if (current == generation) stop("Berbagi layar sudah dihentikan. Buat kode baru untuk menyambung lagi.") },
            { if (current == generation) rttMs = it })
    }

    fun host(context: Context, permission: Intent, width: Int, height: Int, address: String) = dispatch {
        closeInternal(); app = context.applicationContext
        val current = generation
        publish(mode = "host", code = "", status = "Menyiapkan koneksi…")
        val tls = SessionIdentity().also { identity = it }
        sessionId = UUID.randomUUID().toString()
        val token = SessionIdentity.randomToken()
        val gate = PairingGate(token, SystemClock.elapsedRealtime() + 120_000)
        val resume = SessionIdentity.randomToken()
        resumeGate = ResumeGate(resume)
        engine = createEngine(context)
        engine!!.startCapture(permission, width, height)
        val localServer = object : WebSocketServer(InetSocketAddress(address, 0)) {
            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                if (connections.size > 3) conn.close(1008, "Busy")
                worker.schedule({ if (current == generation && conn !== peerSocket) conn.close(1008, "Authentication timeout") }, 8, TimeUnit.SECONDS)
            }
            override fun onMessage(conn: WebSocket, message: String) = dispatch {
                if (current != generation) return@dispatch
                if (message.length > 65536) { conn.close(1009, "Too large"); return@dispatch }
                val json = runCatching { JSONObject(message) }.getOrNull()
                if (json == null || json.optString("session") != sessionId || json.optInt("v") != 1) { conn.close(1008, "Invalid session"); return@dispatch }
                if (conn !== peerSocket) {
                    val isResume = json.optString("type") == "resume" && resumeGate?.accept(json.optString("token"), SystemClock.elapsedRealtime()) == true
                    val first = json.optString("type") == "auth" && peerSocket == null && gate.accept(json.optString("token"), SystemClock.elapsedRealtime())
                    if (!isResume && !first) { conn.close(1008, "Pairing rejected"); return@dispatch }
                    val old = peerSocket
                    peerSocket = conn; old?.close()
                    resumeGate?.connected()
                    send(JSONObject().put("type", "authenticated").put("resume", resume))
                    publish(status = "Monitor terhubung", code = "")
                    sendCameraState()
                    engine?.offer(restart = isResume)
                    return@dispatch
                }
                when (json.optString("type")) {
                    "bye" -> stop("Monitor selesai terhubung")
                    "recover" -> {
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastRecoveryAt > 6000) { lastRecoveryAt = now; engine?.offer(restart = true) }
                    }
                    "control" -> {
                        val request = json.optString("request")
                        val now = SystemClock.elapsedRealtime()
                        if (request.length != 36 || request in controlRequests || now - lastControlAt < 200) return@dispatch
                        controlRequests.add(request)
                        if (controlRequests.size > 128) controlRequests.remove(controlRequests.first())
                        lastControlAt = now
                        val action = json.optString("action")
                        if (action in setOf("photo", "video")) {
                            if (now - lastShutterAt < 800) return@dispatch
                            lastShutterAt = now
                        }
                        val value = json.optString("value").take(100)
                        val x = json.optDouble("x", Double.NaN).toFloat(); val y = json.optDouble("y", Double.NaN).toFloat()
                        val rotation = json.optInt("rotation", -1)
                        main.post {
                            if (current != generation || conn !== peerSocket) return@post
                            val result = CameraControl.perform(action, value, x, y, rotation)
                            dispatch { if (current == generation && conn === peerSocket) send(JSONObject().put("type", "controlResult").put("result", result)) }
                        }
                    }
                    else -> engine?.receive(json)
                }
            }
            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) = dispatch {
                if (current == generation && conn === peerSocket) {
                    peerSocket = null; rttMs = null
                    resumeGate?.disconnected(SystemClock.elapsedRealtime())
                    publish(status = "Menunggu monitor tersambung kembali…")
                }
            }
            override fun onError(conn: WebSocket?, ex: Exception) = dispatch { if (current == generation && conn == null) fail(ex) }
            override fun onStart() = dispatch {
                if (current == generation) publish(status = "Menunggu HP monitor", code = Pairing(sessionId, address, port, tls.fingerprint, token).code())
            }
        }
        localServer.setWebSocketFactory(DefaultSSLWebSocketServerFactory(tls.context))
        localServer.connectionLostTimeout = 10
        server = localServer; localServer.start()
        worker.schedule({
            if (current == generation && peerSocket == null && state.code.isNotEmpty()) stop("Kode sudah habis waktunya. Mulai berbagi lagi untuk membuat kode baru.")
        }, 120, TimeUnit.SECONDS)
    }

    fun join(context: Context, connectionCode: String) = dispatch {
        val parsed = Pairing.parse(connectionCode.trim())
        closeInternal(); app = context.applicationContext; pairing = parsed; sessionId = parsed.session
        reconnectSince = SystemClock.elapsedRealtime()
        publish(mode = "viewer", code = "", status = "Menghubungkan…")
        engine = createEngine(context)
        connectViewer()
    }
    private fun bindWifi() {
        val connectivity = app?.getSystemService(ConnectivityManager::class.java) ?: return
        val wifi = connectivity.allNetworks.firstOrNull { connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        connectivity.bindProcessToNetwork(wifi)
    }
    private fun connectViewer() {
        val p = pairing ?: return
        val current = generation
        // Keep the PeerConnection and the host's single MediaProjection grant alive.
        client?.let { client = null; it.close() }
        bindWifi()
        nextRetryAt = SystemClock.elapsedRealtime() + 7000
        val socket = object : WebSocketClient(URI("wss://${p.host}:${p.port}"), Draft_6455(), null, 5000) {
            override fun onSetSSLParameters(parameters: javax.net.ssl.SSLParameters) { parameters.endpointIdentificationAlgorithm = null }
            override fun onOpen(handshake: ServerHandshake) = dispatch {
                if (current == generation && client === this) send(JSONObject().put("type", if (resumeSecret.isEmpty()) "auth" else "resume").put("token", resumeSecret.ifEmpty { p.token }))
                else close()
            }
            override fun onMessage(message: String) = dispatch {
                if (current != generation || client !== this) return@dispatch
                require(message.length <= 65536)
                val json = JSONObject(message)
                require(json.getInt("v") == 1 && json.getString("session") == sessionId)
                when (json.optString("type")) {
                    "authenticated" -> {
                        val secret = json.optString("resume")
                        require(secret.matches(Regex("[a-f0-9]{64}")))
                        resumeSecret = secret; authenticated = true; reconnectSince = 0; retryNumber = 0; lastFrame.set(0)
                        controlText = ""; publish(status = "Menunggu gambar terbaru…")
                    }
                    "ended" -> stop("Berbagi layar dihentikan oleh HP pengirim")
                    "controlResult" -> { controlText = json.optString("result").take(180); publish() }
                    "cameraState" -> {
                        cameraSnapshot = CameraSnapshot.fromJson(json)
                        cameraStateAt = SystemClock.elapsedRealtime()
                        remoteBattery = json.optInt("battery", -1); freeBytes = json.optLong("freeBytes", -1)
                        json.optString("message").takeIf { it.isNotEmpty() }?.let { controlText = it.take(180) }
                        publish()
                    }
                    else -> engine?.receive(json)
                }
            }
            override fun onClose(code: Int, reason: String, remote: Boolean) = dispatch {
                if (current == generation && client === this) {
                    if (code == 1008 || code == 1000) stop("Sesi sudah berakhir. Pindai kode baru untuk menyambung lagi.")
                    else retryViewer()
                }
            }
            override fun onError(ex: Exception) = dispatch {
                if (current == generation && client === this) {
                    if (ex is javax.net.ssl.SSLException || ex.cause is java.security.cert.CertificateException) stop("Identitas pengirim berubah. Pindai kode baru.")
                    else retryViewer()
                }
            }
        }
        socket.setSocketFactory(SessionIdentity.clientContext(p.fingerprint).socketFactory)
        socket.connectionLostTimeout = 10
        client = socket; socket.connect()
    }
    private fun retryViewer() {
        authenticated = false; rttMs = null
        if (reconnectSince == 0L) reconnectSince = SystemClock.elapsedRealtime()
        val old = client; client = null; old?.close()
        retryNumber++
        nextRetryAt = SystemClock.elapsedRealtime() + minOf(5000L, 1000L * retryNumber)
        publish(status = "Menyambung ulang…")
    }
    private fun send(message: JSONObject) {
        message.put("v", 1).put("session", sessionId)
        val socket = peerSocket?.takeIf { it.isOpen } ?: client?.takeIf { it.isOpen }
        runCatching { socket?.send(message.toString()) }
    }
    fun resize(width: Int, height: Int) = dispatch { engine?.resize(width, height) }
    fun controlCamera(action: String, value: String = "", x: Float = Float.NaN, y: Float = Float.NaN, rotation: Int = -1) = dispatch {
        if (state.mode != "viewer" || !state.live || client?.isOpen != true || !authenticated || action !in CameraControl.actions) return@dispatch
        if (action == "photo" && !cameraSnapshot.canTakePhoto || action == "video" && !cameraSnapshot.canToggleVideo) return@dispatch
        controlText = "Tunggu sebentar…"; publish()
        val command = JSONObject().put("type", "control").put("action", action).put("value", value).put("request", UUID.randomUUID().toString())
        if (x.isFinite() && y.isFinite()) command.put("x", x.toDouble()).put("y", y.toDouble()).put("rotation", rotation)
        send(command)
    }
    fun updateCamera(snapshot: CameraSnapshot, message: String = "") = dispatch {
        if (state.mode != "host") return@dispatch
        cameraSnapshot = snapshot; controlText = message
        publish(); sendCameraState()
    }
    private fun sendCameraState() {
        if (state.mode != "host") return
        send(cameraSnapshot.json().put("type", "cameraState").put("message", controlText)
            .put("battery", app?.let { DeviceStatus.battery(it) } ?: -1).put("freeBytes", DeviceStatus.freeBytes()))
    }
    fun stop(reason: String = "Berbagi layar selesai") = dispatch {
        send(JSONObject().put("type", if (state.mode == "host") "ended" else "bye"))
        closeInternal()
        app?.stopService(Intent(app, ProjectionService::class.java))
        publish(mode = "idle", code = "", status = reason)
    }
    private fun closeInternal() {
        generation++; renderer = null
        peerSocket?.close(); peerSocket = null
        client?.close(); client = null
        server?.stop(300); server = null
        engine?.close(); engine = null
        identity?.close(); identity = null
        app?.getSystemService(ConnectivityManager::class.java)?.bindProcessToNetwork(null)
        frameCount.set(0); lastFrame.set(0); rttMs = null; controlText = ""; lastControlAt = 0; lastShutterAt = 0
        controlRequests.clear(); cameraSnapshot = CameraSnapshot(); cameraStateAt = 0
        pairing = null; resumeSecret = ""; resumeGate = null; authenticated = false
        reconnectSince = 0; nextRetryAt = 0; retryNumber = 0; lastRecoveryAt = 0
        remoteBattery = -1; freeBytes = -1
    }
}
