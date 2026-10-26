package id.mirrorselfie

import java.lang.ref.WeakReference
import org.json.JSONArray
import org.json.JSONObject

data class CameraSnapshot(
    val available: Boolean = false, val recording: Boolean = false, val busy: Boolean = false,
    val zoom: Float = 1f, val maxZoom: Float = 5f, val torch: Boolean = false,
    val mode: String = "photo", val videoMode: String = "", val videoModes: List<String> = emptyList(),
    val iso: Int = 0, val isoOptions: List<Int> = emptyList(),
    val shutterNs: Long = 0, val shutterOptions: List<Long> = emptyList(),
    val kelvin: Int = 0, val manualWb: Boolean = false, val focusSupported: Boolean = false,
    val focusLocked: Boolean = false, val exposureLockSupported: Boolean = false, val exposureLocked: Boolean = false,
    val recordedMs: Long = 0, val rotation: Int = 0
) {
    val canTakePhoto: Boolean get() = available && !busy && !recording
    val canToggleVideo: Boolean get() = available && !busy
    val canConfigure: Boolean get() = available && !busy && !recording
    fun json() = JSONObject().apply {
        put("available", available); put("recording", recording); put("busy", busy)
        put("zoom", zoom.toDouble()); put("maxZoom", maxZoom.toDouble()); put("torch", torch)
        put("mode", mode); put("videoMode", videoMode); put("videoModes", JSONArray(videoModes))
        put("iso", iso); put("isoOptions", JSONArray(isoOptions)); put("shutterNs", shutterNs)
        put("shutterOptions", JSONArray(shutterOptions)); put("kelvin", kelvin); put("manualWb", manualWb)
        put("focusSupported", focusSupported); put("focusLocked", focusLocked)
        put("exposureLockSupported", exposureLockSupported); put("exposureLocked", exposureLocked)
        put("recordedMs", recordedMs); put("rotation", rotation)
    }
    companion object {
        fun fromJson(j: JSONObject): CameraSnapshot {
            fun strings(key: String): List<String> = j.optJSONArray(key)?.let { a -> (0 until minOf(a.length(), 64)).map { a.optString(it) } } ?: emptyList()
            return CameraSnapshot(j.optBoolean("available"), j.optBoolean("recording"), j.optBoolean("busy"),
                j.optDouble("zoom", 1.0).toFloat(), j.optDouble("maxZoom", 5.0).toFloat(), j.optBoolean("torch"),
                j.optString("mode", "photo"), j.optString("videoMode"), strings("videoModes"),
                j.optInt("iso"), strings("isoOptions").mapNotNull { it.toIntOrNull() },
                j.optLong("shutterNs"), strings("shutterOptions").mapNotNull { it.toLongOrNull() },
                j.optInt("kelvin"), j.optBoolean("manualWb"), j.optBoolean("focusSupported"),
                j.optBoolean("focusLocked"), j.optBoolean("exposureLockSupported"), j.optBoolean("exposureLocked"),
                j.optLong("recordedMs").coerceAtLeast(0), j.optInt("rotation"))
        }
    }
}

object CameraControl {
    val actions = setOf("photo", "video", "zoomIn", "zoomOut", "torch", "grid", "timer",
        "focus", "focusLock", "unlockFocus", "exposureLock", "mode", "quality", "iso", "shutter", "wb")
    private var active = WeakReference<CameraActivity>(null)
    fun attach(camera: CameraActivity) { active = WeakReference(camera) }
    fun detach(camera: CameraActivity) {
        if (active.get() === camera) { active.clear(); Session.updateCamera(CameraSnapshot()) }
    }
    fun perform(action: String, value: String = "", x: Float = Float.NaN, y: Float = Float.NaN, rotation: Int = -1): String {
        if (Session.state.mode != "host") return "Berbagi layar belum aktif."
        if (action !in actions) return "Tombol ini belum didukung."
        return active.get()?.remote(action, value, x, y, rotation) ?: "Buka kamera di Mirror Selfie pada HP pengirim."
    }
}
