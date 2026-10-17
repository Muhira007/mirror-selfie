package id.mirrorselfie

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.Surface
import org.webrtc.CapturerObserver
import org.webrtc.SurfaceTextureHelper
import org.webrtc.ThreadUtils
import org.webrtc.VideoCapturer

/** Owns one projection grant. Modern Android must resize its display, never reuse the grant. */
class ProjectionCapturer(private val permission: Intent, private val callback: MediaProjection.Callback) : VideoCapturer {
    private lateinit var helper: SurfaceTextureHelper
    private lateinit var observer: CapturerObserver
    private lateinit var manager: MediaProjectionManager
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var surface: Surface? = null

    override fun initialize(helper: SurfaceTextureHelper, context: Context, observer: CapturerObserver) {
        this.helper = helper; this.observer = observer
        manager = context.getSystemService(MediaProjectionManager::class.java)
    }

    override fun startCapture(width: Int, height: Int, fps: Int) {
        check(projection == null)
        projection = manager.getMediaProjection(Activity.RESULT_OK, permission)
        projection!!.registerCallback(callback, helper.handler)
        ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
            helper.setTextureSize(width, height)
            surface = Surface(helper.surfaceTexture)
            display = projection!!.createVirtualDisplay("MirrorSelfie", width, height, 240,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, helper.handler)
            observer.onCapturerStarted(true)
            helper.startListening { observer.onFrameCaptured(it) }
        }
    }

    override fun changeCaptureFormat(width: Int, height: Int, fps: Int) {
        if (display == null) return
        ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
            helper.setTextureSize(width, height)
            display?.resize(width, height, 240)
            display?.surface = surface
        }
    }

    override fun stopCapture() {
        if (projection == null) return
        ThreadUtils.invokeAtFrontUninterruptibly(helper.handler) {
            helper.stopListening()
            observer.onCapturerStopped()
            display?.release(); display = null
            surface?.release(); surface = null
            projection?.unregisterCallback(callback)
            projection?.stop(); projection = null
        }
    }

    override fun dispose() { stopCapture() }
    override fun isScreencast() = true
}
