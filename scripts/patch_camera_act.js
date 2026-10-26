
const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', 'utf8');

// Add variables in CameraActivity
code = code.replace(
  'private var audio=true',
  'private var audio=true\n    private var torchEnabled=false\n    private var gridEnabled=false\n    private var currentZoom=1.0f\n    private var maxZoom=5.0f\n    private var timerSeconds=0\n    private var countdownRunning=false\n    private var gridView:GridOverlayView?=null\n    private lateinit var torchButton:TextView\n    private lateinit var timerButton:TextView\n    private lateinit var gridButton:TextView'
);

// Add preview tap to focus
const oldOnCreate = 'showControls()';
const newOnCreate = `// Grid overlay in preview frame
        val previewParent = preview.parent as? android.widget.FrameLayout ?: preview
        gridView = GridOverlayView(this).apply { visibility = android.view.View.GONE }
        // Preview tap to focus
        preview.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP && ready) {
                val factory = preview.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = androidx.camera.core.FocusMeteringAction.Builder(point).build()
                camera?.cameraControl?.startFocusAndMetering(action)
                feedback.setText(R.string.camera_focused)
            }
            true
        }
        showControls()`;
code = code.replace(oldOnCreate, newOnCreate);

// In bindCamera, query zoom
const oldBind = 'ready=true; feedback.setText(R.string.camera_ready); refresh()';
const newBind = `camera?.cameraInfo?.zoomState?.value?.let { zs ->
                currentZoom = zs.zoomRatio
                maxZoom = zs.maxZoomRatio.coerceAtMost(8.0f)
            }
            camera?.cameraControl?.enableTorch(torchEnabled)
            ready=true; feedback.setText(R.string.camera_ready); refresh()`;
code = code.replace(oldBind, newBind);

// Add remote handling for new actions
const oldRemoteWhen = 'when(action) {';
const newRemoteWhen = `when(action) {
            "zoomIn" -> {
                val next = (currentZoom + 0.5f).coerceAtMost(maxZoom)
                camera?.cameraControl?.setZoomRatio(next)
                currentZoom = next
                refresh("Zoom %.1f×".format(currentZoom))
                return "Zoom %.1f×".format(currentZoom)
            }
            "zoomOut" -> {
                val next = (currentZoom - 0.5f).coerceAtLeast(1.0f)
                camera?.cameraControl?.setZoomRatio(next)
                currentZoom = next
                refresh("Zoom %.1f×".format(currentZoom))
                return "Zoom %.1f×".format(currentZoom)
            }
            "torch" -> {
                torchEnabled = !torchEnabled
                camera?.cameraControl?.enableTorch(torchEnabled)
                refresh(if (torchEnabled) "Lampu aktif" else "Lampu mati")
                return if (torchEnabled) "Lampu aktif" else "Lampu mati"
            }
            "grid" -> {
                gridEnabled = !gridEnabled
                gridView?.visibility = if (gridEnabled) android.view.View.VISIBLE else android.view.View.GONE
                return if (gridEnabled) "Garis pandu aktif" else "Garis pandu mati"
            }
            "timer" -> {
                timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
                return if (timerSeconds > 0) "Timer %ds".format(timerSeconds) else "Timer mati"
            }`;
code = code.replace(oldRemoteWhen, newRemoteWhen);

// Intercept photo action with timer
const oldPhotoCall = 'takePhoto(); return getString(R.string.photo_working)';
const newPhotoCall = `if (timerSeconds > 0 && !countdownRunning) {
                    startCountdown { takePhoto() }
                    return "Hitung mundur %ds".format(timerSeconds)
                }
                takePhoto(); return getString(R.string.photo_working)`;
code = code.replace(oldPhotoCall, newPhotoCall);

// Add startCountdown helper and refresh update
const oldRefresh = 'val state=CameraSnapshot(screenActive && ready,recordingStarted,busy)';
const newRefresh = `val state=CameraSnapshot(screenActive && ready,recordingStarted,busy,currentZoom,maxZoom,torchEnabled)`;
code = code.replace(oldRefresh, newRefresh);

const helperCountdown = `
    private fun startCountdown(onFinished: () -> Unit) {
        countdownRunning = true
        var left = timerSeconds
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (!screenActive || !ready) { countdownRunning = false; return }
                if (left > 0) {
                    feedback.text = "Foto dalam %d...".format(left)
                    Session.updateCamera(CameraSnapshot(screenActive && ready, recordingStarted, true, currentZoom, maxZoom, torchEnabled), "Foto dalam %d...".format(left))
                    left--
                    handler.postDelayed(this, 1000)
                } else {
                    countdownRunning = false
                    onFinished()
                }
            }
        }
        handler.post(runnable)
    }
`;

code = code.replace('private fun result(text:Int)', helperCountdown + '\n    private fun result(text:Int)');

fs.writeFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', code, 'utf8');
console.log('CameraActivity patched');

