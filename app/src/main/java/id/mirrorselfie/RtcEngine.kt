package id.mirrorselfie

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import org.json.JSONObject
import org.webrtc.*

/** All mutations run on Session's serial executor. Video sink callbacks use WebRTC threads. */
class RtcEngine(
    private val context: Context,
    private val dispatch: (() -> Unit) -> Unit,
    private val send: (JSONObject) -> Unit,
    private val status: (String) -> Unit,
    private val track: (VideoTrack) -> Unit,
    private val projectionStopped: () -> Unit,
    private val networkRtt: (Double) -> Unit
) : AutoCloseable {
    val egl: EglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    private val peer: PeerConnection
    private var source: VideoSource? = null
    private var video: VideoTrack? = null
    private var helper: SurfaceTextureHelper? = null
    private var capture: ProjectionCapturer? = null
    private var remoteReady = false
    private val candidates = mutableListOf<IceCandidate>()
    @Volatile private var closed = false

    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options().apply {
                // An offline SoftAP is not a ConnectivityManager internet network.
                // Enumerate local interfaces natively, including ap0, instead of dropping it.
                disableNetworkMonitor = true
                networkIgnoreMask = PeerConnectionFactory.Options.ADAPTER_TYPE_CELLULAR or
                    PeerConnectionFactory.Options.ADAPTER_TYPE_VPN or PeerConnectionFactory.Options.ADAPTER_TYPE_LOOPBACK
            })
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext)).createPeerConnectionFactory()
        val configuration = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = requireNotNull(factory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                dispatch { if (!closed) status(when (state) {
                    PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> "Terhubung"
                    PeerConnection.IceConnectionState.FAILED -> "Belum bisa terhubung. Cek Wi-Fi atau hotspot kedua HP."
                    PeerConnection.IceConnectionState.DISCONNECTED -> "Koneksi terputus. Cek Wi-Fi atau hotspot kedua HP."
                    else -> "Menghubungkan…"
                }) }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidate(candidate: IceCandidate) = dispatch {
                if (!closed) send(JSONObject().put("type", "ice").put("mid", candidate.sdpMid)
                    .put("index", candidate.sdpMLineIndex).put("candidate", candidate.sdp))
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(channel: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                (receiver.track() as? VideoTrack)?.let { videoTrack -> dispatch { if (!closed) track(videoTrack) } }
            }
        }))
    }

    fun startCapture(permission: Intent, width: Int, height: Int) {
        val videoSource = factory.createVideoSource(true)
        source = videoSource
        helper = SurfaceTextureHelper.create("MirrorCapture", egl.eglBaseContext)
        capture = ProjectionCapturer(permission, object : MediaProjection.Callback() {
            override fun onStop() = dispatch { if (!closed) projectionStopped() }
            override fun onCapturedContentResize(width: Int, height: Int) = dispatch { resize(width, height) }
        })
        requireNotNull(capture).also {
            it.initialize(requireNotNull(helper), context, videoSource.capturerObserver)
            val size = scaledSize(width, height)
            videoSource.adaptOutputFormat(size.first, size.second, 24)
            it.startCapture(size.first, size.second, 24)
        }
        video = factory.createVideoTrack("screen", videoSource).also { peer.addTrack(it, listOf("mirror")) }
    }

    fun resize(width: Int, height: Int) {
        if (closed || width <= 0 || height <= 0) return
        val size = scaledSize(width, height)
        source?.adaptOutputFormat(size.first, size.second, 24)
        capture?.changeCaptureFormat(size.first, size.second, 24)
    }

    fun offer() = peer.createOffer(object : SdpAdapter() {
        override fun onCreateSuccess(description: SessionDescription) = dispatch {
            if (!closed) peer.setLocalDescription(object : SdpAdapter() {
                override fun onSetSuccess() = dispatch {
                    if (!closed) {
                        peer.senders.forEach { sender ->
                            val parameters = sender.parameters
                            parameters.encodings.forEach { it.maxBitrateBps = 2_000_000; it.maxFramerate = 24 }
                            sender.parameters = parameters
                        }
                        send(JSONObject().put("type", "offer").put("sdp", description.description))
                    }
                }
            }, description)
        }
        override fun onCreateFailure(error: String) { status("Layar belum bisa dibagikan. Coba buat koneksi baru.") }
    }, MediaConstraints())

    fun receive(message: JSONObject) {
        if (closed) return
        when (message.getString("type")) {
            "ice" -> {
                val candidate = IceCandidate(message.optString("mid"), message.getInt("index"), message.getString("candidate"))
                if (remoteReady) peer.addIceCandidate(candidate) else candidates.add(candidate)
            }
            "offer", "answer" -> {
                val isOffer = message.getString("type") == "offer"
                val description = SessionDescription(if (isOffer) SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER, message.getString("sdp"))
                peer.setRemoteDescription(object : SdpAdapter() {
                    override fun onSetSuccess() = dispatch {
                        if (!closed) {
                            remoteReady = true
                            candidates.forEach { peer.addIceCandidate(it) }; candidates.clear()
                            if (isOffer) peer.createAnswer(object : SdpAdapter() {
                                override fun onCreateSuccess(answer: SessionDescription) = dispatch {
                                    if (!closed) peer.setLocalDescription(object : SdpAdapter() {
                                        override fun onSetSuccess() = dispatch { if (!closed) send(JSONObject().put("type", "answer").put("sdp", answer.description)) }
                                    }, answer)
                                }
                            }, MediaConstraints())
                        }
                    }
                    override fun onSetFailure(error: String) { status("Layar belum bisa dibagikan. Coba buat koneksi baru.") }
                }, description)
            }
        }
    }

    fun stats() {
        if (closed) return
        peer.getStats { report ->
            if (closed) return@getStats
            val pairId = report.statsMap.values.firstOrNull { it.type == "transport" }?.members?.get("selectedCandidatePairId") as? String
            val roundTrip = pairId?.let { report.statsMap[it]?.members?.get("currentRoundTripTime") } as? Number
            roundTrip?.toDouble()?.times(1000)?.let {
                networkRtt(it)
                Log.i("MirrorP1", "NETWORK rttMs=$it")
            }
            report.statsMap.values.filter { it.type in listOf("inbound-rtp", "outbound-rtp", "codec") }.forEach { stat ->
                val safe = stat.members.filterKeys { it in setOf("mimeType", "framesEncoded", "framesDecoded", "framesPerSecond", "bytesSent", "bytesReceived", "frameWidth", "frameHeight", "codecId", "kind") }
                Log.i("MirrorP1", "RTC ${stat.type} $safe")
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        capture?.stopCapture(); capture?.dispose(); capture = null
        peer.close(); peer.dispose()
        video?.dispose(); source?.dispose(); helper?.dispose()
        factory.dispose(); egl.release()
    }

    private open inner class SdpAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String) { status("Negosiasi gagal") }
        override fun onSetFailure(error: String) { status("Negosiasi gagal") }
    }

    companion object {
        fun scaledSize(width: Int, height: Int): Pair<Int, Int> {
            val scale = minOf(1.0, 1280.0 / maxOf(width, height), 720.0 / minOf(width, height))
            return (maxOf(2, (width * scale).toInt() / 2 * 2) to maxOf(2, (height * scale).toInt() / 2 * 2))
        }
    }
}
