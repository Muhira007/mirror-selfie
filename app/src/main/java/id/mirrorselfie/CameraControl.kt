package id.mirrorselfie

import java.lang.ref.WeakReference

data class CameraSnapshot(val available: Boolean = false, val recording: Boolean = false, val busy: Boolean = false) {
    val canTakePhoto: Boolean get() = available && !busy && !recording
    val canToggleVideo: Boolean get() = available && !busy
}

/** Remote commands operate only our foreground camera, never another application's UI. */
object CameraControl {
    private var active = WeakReference<CameraActivity>(null)
    fun attach(camera: CameraActivity) { active = WeakReference(camera) }
    fun detach(camera: CameraActivity) {
        if (active.get() === camera) { active.clear(); Session.updateCamera(CameraSnapshot()) }
    }
    fun perform(action: String): String {
        if (Session.state.mode != "host") return "Berbagi layar belum aktif."
        if (action != "photo" && action != "video") return "Tombol ini belum didukung."
        return active.get()?.remote(action) ?: "Buka kamera di Mirror Selfie pada HP pengirim."
    }
}
