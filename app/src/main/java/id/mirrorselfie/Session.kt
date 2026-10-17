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

data class SessionState(val mode: String = "idle", val status: String = "Siap", val code: String = "", val frames: Long = 0, val rttMs: Double? = null, val controlText: String = "", val camera:CameraSnapshot=CameraSnapshot())

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
    private var cameraSnapshot=CameraSnapshot()
    private var lastControlAt = 0L
    private val controlRequests = LinkedHashSet<String>()
    val eglContext: EglBase.Context? get() = engine?.egl?.eglBaseContext

    init {
        worker.scheduleWithFixedDelay({
            if (engine != null) {
                engine?.stats()
                if (state.mode == "viewer" && frameCount.get() > 0) {
                    publish()
                }
            }
        }, 5, 5, TimeUnit.SECONDS)
    }

    private fun publish(status: String = state.status, code: String = state.code, mode: String = state.mode) {
        state = SessionState(mode, status, code, frameCount.get(), rttMs, controlText,cameraSnapshot)
        // Status only: no pairing credentials, SDP, IP addresses or screen frames in logs.
        Log.i("MirrorP1", "$mode: $status")
        val snapshot = state
        main.post { listener?.invoke(snapshot) }
    }

    private fun dispatch(block: () -> Unit) { worker.execute { runCatching(block).onFailure { fail(it) } } }
    private fun fail(error: Throwable) {
        Log.e("MirrorP1", "Session failure: ${error.javaClass.simpleName}")
        Log.e("MirrorP1", error.stackTrace.filter { it.className.startsWith("id.mirrorselfie") }.take(6).joinToString(" <- "))
        if (error is javax.net.ssl.SSLException) logTls(error)
        closeInternal()
        publish(mode = "idle", code = "", status = "Belum bisa terhubung. Cek Wi-Fi atau hotspot, lalu buat kode baru.")
        app?.stopService(Intent(app, ProjectionService::class.java))
    }

    private fun createEngine(context: Context): RtcEngine = RtcEngine(context, ::dispatch, ::send,
        { publish(status = it) }, { video ->
            video.addSink { frame ->
                val received = frameCount.incrementAndGet(); lastFrame.set(SystemClock.elapsedRealtime())
                if (received == 1L) dispatch { if (state.mode == "viewer") publish(status = "Terhubung") }
                renderer?.onFrame(frame)
            }
            publish(status = "Menunggu layar HP pengirim…")
        }, { stop("Berbagi layar sudah dihentikan. Buat kode baru untuk menyambung lagi.") }, { rttMs = it })

    fun host(context: Context, permission: Intent, width: Int, height: Int, address: String) = dispatch {
        closeInternal()
        app = context.applicationContext
        val current = generation
        frameCount.set(0)
        publish(mode = "host", code = "", status = "Menyiapkan koneksi…")
        val tls = SessionIdentity().also { identity = it }
        sessionId = UUID.randomUUID().toString()
        val token = SessionIdentity.randomToken()
        val gate = PairingGate(token, SystemClock.elapsedRealtime() + 120000)
        engine = createEngine(context)
        engine!!.startCapture(permission, width, height)
        val localServer = object : WebSocketServer(InetSocketAddress(address, 0)) {
            override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
                // Limit unauthenticated clients; a silent peer must not occupy a slot forever.
                if (connections.size > 3) conn.close(1008, "Busy")
                worker.schedule({ if (current == generation && conn !== peerSocket) conn.close(1008, "Authentication timeout") }, 8, TimeUnit.SECONDS)
            }
            override fun onMessage(conn: WebSocket, message: String) = dispatch {
                if (current != generation) return@dispatch
                if (message.length > 65536) { conn.close(1009, "Too large"); return@dispatch }
                val json = runCatching { JSONObject(message) }.getOrNull()
                if (json == null || json.optString("session") != sessionId || json.optInt("v") != 1) {
                    conn.close(1008, "Invalid session"); return@dispatch
                }
                if (conn !== peerSocket) {
                    if (peerSocket != null || json.optString("type") != "auth" || !gate.accept(json.optString("token"), SystemClock.elapsedRealtime())) {
                        conn.close(1008, "Pairing rejected"); return@dispatch
                    }
                    peerSocket = conn
                    publish(status = "Monitor terhubung", code = "")
                    sendCameraState()
                    engine?.offer()
                } else if (json.optString("type") == "control") {
                    val request = json.optString("request")
                    val now = SystemClock.elapsedRealtime()
                    if (request.length != 36 || request in controlRequests || now - lastControlAt < 800) return@dispatch
                    controlRequests.add(request)
                    if (controlRequests.size > 50) controlRequests.remove(controlRequests.first())
                    lastControlAt = now
                    val action = json.optString("action")
                    main.post {
                        if (current != generation || conn !== peerSocket) return@post
                        val result = CameraControl.perform(action)
                        dispatch { if (current == generation && conn === peerSocket) send(JSONObject().put("type", "controlResult").put("result", result)) }
                    }
                } else engine?.receive(json)
            }
            override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) = dispatch {
                if (current == generation && conn === peerSocket) stop("Monitor terputus. Buat kode baru untuk menyambung lagi.")
            }
            override fun onError(conn: WebSocket?, ex: Exception) = dispatch {
                if (ex is javax.net.ssl.SSLException) logTls(ex)
                if (current == generation && conn == null) fail(ex)
            }
            override fun onStart() = dispatch {
                if (current == generation) publish(status = "Menunggu HP monitor", code = Pairing(sessionId, address, port, tls.fingerprint, token).code())
            }
        }
        localServer.setWebSocketFactory(DefaultSSLWebSocketServerFactory(tls.context))
        localServer.connectionLostTimeout = 10
        server = localServer
        localServer.start()
        worker.schedule({
            if (current == generation && peerSocket == null) stop("Kode sudah habis waktunya. Mulai berbagi lagi untuk membuat kode baru.")
        }, 120, TimeUnit.SECONDS)
    }

    fun join(context: Context, connectionCode: String) = dispatch {
        val pairing = Pairing.parse(connectionCode.trim())
        closeInternal()
        app = context.applicationContext
        val current = generation
        frameCount.set(0)
        sessionId = pairing.session
        publish(mode = "viewer", code = "", status = "Menghubungkan…")
        // Prefer the selected Wi-Fi even when Android prefers mobile data for internet.
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }?.let { check(connectivity.bindProcessToNetwork(it)) { "Tidak dapat memilih Wi-Fi" } }
        engine = createEngine(context)
        publish(status = "Menghubungkan…")
        val socket = object : WebSocketClient(URI("wss://${pairing.host}:${pairing.port}")) {
            override fun onSetSSLParameters(parameters: javax.net.ssl.SSLParameters) {
                // Identity is the exact certificate pin transferred out of band, not a DNS name.
                // The trust manager still verifies the certificate and never accepts arbitrary certs.
                parameters.endpointIdentificationAlgorithm = null
            }
            override fun onOpen(handshake: ServerHandshake) = dispatch {
                if (current == generation) send(JSONObject().put("type", "auth").put("token", pairing.token))
            }
            override fun onMessage(message: String) = dispatch {
                if (current != generation) return@dispatch
                require(message.length <= 65536)
                val json = JSONObject(message)
                require(json.getInt("v") == 1 && json.getString("session") == sessionId)
                if (json.optString("type") == "controlResult") {
                    controlText = json.optString("result").take(180)
                    publish()
                } else if(json.optString("type")=="cameraState") {
                    cameraSnapshot=CameraSnapshot(json.optBoolean("available"),json.optBoolean("recording"),json.optBoolean("busy"))
                    controlText=json.optString("message").take(180)
                    publish()
                } else engine?.receive(json)
            }
            override fun onClose(code: Int, reason: String, remote: Boolean) = dispatch {
                if (current == generation) stop("Koneksi terputus. Pindai kode baru untuk menyambung lagi.")
            }
            override fun onError(ex: Exception) = dispatch { if (current == generation) fail(ex) }
        }
        socket.setSocketFactory(SessionIdentity.clientContext(pairing.fingerprint).socketFactory)
        socket.connectionLostTimeout = 10
        client = socket
        socket.connect()
        worker.schedule({ if (current == generation && frameCount.get() == 0L) stop("Layar belum muncul. Cek koneksi kedua HP, lalu coba lagi.") }, 30, TimeUnit.SECONDS)
    }

    private fun send(message: JSONObject) {
        message.put("v", 1).put("session", sessionId)
        peerSocket?.takeIf { it.isOpen }?.send(message.toString())
            ?: client?.takeIf { it.isOpen }?.send(message.toString())
    }

    private fun logTls(error: Throwable) {
        // TLS diagnostics never include application credentials; redact endpoint addresses too.
        val detail = error.message.orEmpty().replace(Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"), "[address]")
        Log.e("MirrorP1", "TLS ${error.javaClass.simpleName}: ${detail.take(240)}")
    }

    fun resize(width: Int, height: Int) = dispatch { engine?.resize(width, height) }
    fun controlCamera(action: String) = dispatch {
        if (state.mode != "viewer" || client?.isOpen != true) return@dispatch
        if(action!="photo" && action!="video") return@dispatch
        if(action=="photo" && !cameraSnapshot.canTakePhoto || action=="video" && !cameraSnapshot.canToggleVideo) return@dispatch
        controlText = "Tunggu sebentar…"
        publish()
        send(JSONObject().put("type", "control").put("action", action).put("request", UUID.randomUUID().toString()))
    }
    fun updateCamera(snapshot:CameraSnapshot,message:String="") = dispatch {
        if(state.mode!="host") return@dispatch
        cameraSnapshot=snapshot
        controlText=message; publish(); sendCameraState()
    }
    private fun sendCameraState() {
        if(state.mode!="host") return
        send(JSONObject().put("type","cameraState").put("available",cameraSnapshot.available).put("recording",cameraSnapshot.recording).put("busy",cameraSnapshot.busy).put("message",controlText))
    }
    fun stop(reason: String = "Berbagi layar selesai") = dispatch {
        closeInternal()
        app?.stopService(Intent(app, ProjectionService::class.java))
        publish(mode = "idle", code = "", status = reason)
    }

    private fun closeInternal() {
        generation++
        renderer = null
        peerSocket?.close(); peerSocket = null
        client?.close(); client = null
        server?.stop(300); server = null
        engine?.close(); engine = null
        identity?.close(); identity = null
        app?.getSystemService(ConnectivityManager::class.java)?.bindProcessToNetwork(null)
        frameCount.set(0); lastFrame.set(0)
        rttMs = null
        controlText = ""; lastControlAt = 0L; controlRequests.clear()
        cameraSnapshot=CameraSnapshot()
    }
}
