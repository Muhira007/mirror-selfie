package id.mirrorselfie

import android.app.AlertDialog

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.*
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CaptureRequest
import androidx.camera.video.*
import java.util.Locale

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
class CameraActivity : CameraScreen() {
    override val titleRes = R.string.camera_title
    private var lens = CameraSelector.DEFAULT_BACK_CAMERA
    private var mode = "photo"
    private var targetRatio = AspectRatio.RATIO_16_9
    private var camera: Camera? = null
    private var image: ImageCapture? = null
    private var capture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var recordedMs = 0L
    private var lastStatusSecond = -1L
    private var focusLocked = false
    private var exposureLocked = false
    private var exposureLockSupported = false
    private var focusSupported = false
    private var manualWb = false
    private var isoOptions = emptyList<Int>()
    private var shutterOptions = emptyList<Long>()
    @Volatile private var meteredIso = 100
    @Volatile private var meteredExposure = 16_666_667L
    private var focusGeneration = 0
    private var recordingStarted = false
    private var busy = false
    private var ready = false
    private var audio = true
    private var torchEnabled = false
    private var gridEnabled = false
    private var currentZoom = 1.0f
    private var maxZoom = 5.0f
    private var timerSeconds = 0
    private var countdownRunning = false
    private var countdownGeneration = 0
    private var proPanelVisible = false
    private var proIso = 0 // 0 = Auto
    private var proShutterMs = 0.0 // 0.0 = Auto
    private var proKelvin = 0 // 0 = Auto
    private lateinit var proPanel: LinearLayout
    private lateinit var isoButton: TextView
    private lateinit var shutterButton: TextView
    private lateinit var wbButton: TextView

    private lateinit var gridView: GridOverlayView
    private lateinit var photoButton: View
    private lateinit var videoButton: View
    private lateinit var flipButton: View
    private lateinit var timerButton: TextView
    private lateinit var ratioButton: TextView
    private lateinit var torchButton: View
    private var videoModes = emptyList<VideoMode>()
    private var videoMode: VideoMode? = null
    private var modesForLens: CameraSelector? = null

    override fun requestedPermissions(): Array<String> = buildList {
        add(Manifest.permission.CAMERA); add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Grid overlay full screen
        gridView = GridOverlayView(this).apply { visibility = View.GONE }
        overlayContainer.addView(gridView, FrameLayout.LayoutParams(-1, -1))

        // 2. Top Bar (Back button, Flash/Torch, Timer, Ratio)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val backBtn = iconBtn("back", R.string.back) { finish() }
        torchButton = iconBtn("torch", R.string.torch) { remote("torch") }
        timerButton = pillBtn("Timer") { remote("timer") }
        ratioButton = pillBtn("9:16") {
            if (recording != null || busy || countdownRunning) return@pillBtn
            targetRatio = if (targetRatio == AspectRatio.RATIO_16_9) AspectRatio.RATIO_4_3 else AspectRatio.RATIO_16_9
            ratioButton.text = if (targetRatio == AspectRatio.RATIO_16_9) "9:16" else "3:4"
            bindCamera()
        }
        val gridBtn = iconBtn("grid", R.string.grid) { remote("grid") }

        topBar.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        topBar.addView(torchButton, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(6) })
        topBar.addView(gridBtn, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(6) })
        topBar.addView(timerButton, LinearLayout.LayoutParams(-2, dp(38)).apply { marginEnd = dp(6) })
        topBar.addView(ratioButton, LinearLayout.LayoutParams(-2, dp(38)).apply { marginEnd = dp(6) })
        val proToggle = pillBtn("PRO") {
            proPanelVisible = !proPanelVisible
            proPanel.visibility = if (proPanelVisible) View.VISIBLE else View.GONE
        }
        topBar.addView(proToggle, LinearLayout.LayoutParams(-2, dp(38)))

        val topParams = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        overlayContainer.addView(topBar, topParams)

        // 3. Bottom Camera Controls (Shutter, Switch, Video Toggle)
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(32))
        }

        flipButton = iconBtn("camera", R.string.camera_switch) {
            if (recording != null || busy || countdownRunning) return@iconBtn
            val next = if (lens == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if (provider?.hasCamera(next) == true) { lens = next; bindCamera() } else feedback.setText(R.string.camera_switch_failed)
        }

        // Circular Shutter Button
        photoButton = circularShutter(Studio.mint) { remote("photo") }.apply { contentDescription = getString(R.string.photo) }
        videoButton = circularShutter(Studio.coral) { remote("video") }.apply { contentDescription = getString(R.string.record_stop) }

        bottomBar.addView(flipButton, LinearLayout.LayoutParams(dp(54), dp(54)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bottomBar.addView(photoButton, LinearLayout.LayoutParams(dp(76), dp(76)).apply { marginEnd = dp(16) })
        bottomBar.addView(videoButton, LinearLayout.LayoutParams(dp(76), dp(76)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        
        val modeToggle = pillBtn("Mode") {
            if (recording != null || busy || countdownRunning) return@pillBtn
            mode = if (mode == "photo") "video" else "photo"
            bindCamera()
        }
        bottomBar.addView(modeToggle, LinearLayout.LayoutParams(-2, dp(44)))

        // PRO controls panel (ISO, Shutter, Kelvin)
        proPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = Studio.shape(this@CameraActivity, 0x88000000.toInt(), 20)
            visibility = View.GONE
        }
        isoButton = pillBtn("ISO: Auto") { showIsoDialog() }
        shutterButton = pillBtn("S: Auto") { showShutterDialog() }
        wbButton = pillBtn("WB: Auto") { showWbDialog() }
        proPanel.addView(isoButton, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
        proPanel.addView(shutterButton, LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginEnd = dp(6) })
        proPanel.addView(wbButton, LinearLayout.LayoutParams(0, dp(38), 1.2f))

        val proParamsLayout = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            bottomMargin = dp(112)
            leftMargin = dp(16)
            rightMargin = dp(16)
        }
        overlayContainer.addView(proPanel, proParamsLayout)

        val bottomParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        overlayContainer.addView(bottomBar, bottomParams)

        // Tap to focus & metering
        preview.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && ready) {
                focusPreview(event.x, event.y, false)
            }
            true
        }
    }

    private fun iconBtn(icon: String, desc: Int, onClick: () -> Unit) = FrameLayout(this).apply {
        contentDescription = getString(desc)
        background = Studio.touch(this@CameraActivity, 0x44000000, 22)
        addView(StudioIcon(this@CameraActivity, icon, Color.WHITE), FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        setOnClickListener { onClick() }
    }

    private fun pillBtn(text: String, onClick: () -> Unit) = TextView(this).apply {
        setText(text); textSize = 13f; setTextColor(Color.WHITE)
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        gravity = Gravity.CENTER; setPadding(dp(14), 0, dp(14), 0)
        background = Studio.touch(this@CameraActivity, 0x44000000, 19)
        setOnClickListener { onClick() }
    }

    private fun circularShutter(color: Int, onClick: () -> Unit) = FrameLayout(this).apply {
        val outer = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(3), Color.WHITE)
        }
        val inner = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(color)
        }
        background = outer
        val innerView = View(context).apply { background = inner }
        addView(innerView, FrameLayout.LayoutParams(dp(62), dp(62), Gravity.CENTER))
        setOnClickListener { onClick() }
    }

    override fun onCameraPermissions() {
        audio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        refresh()
    }
    override fun onStart() { super.onStart(); CameraControl.attach(this) }
    override fun onStop() {
        ready = false
        focusGeneration++; countdownGeneration++; countdownRunning = false; busy = false
        recording?.stop()
        CameraControl.detach(this)
        super.onStop()
    }

    @SuppressLint("MissingPermission")
    override fun bindCamera() {
        val p = provider ?: return
        if (recording != null || countdownRunning) return
        focusGeneration++; focusLocked = false; exposureLocked = false
        ready = false; camera = null; image = null; capture = null; refresh()
        runCatching {
            releaseUseCases()
            if (!p.hasCamera(lens)) lens = if (p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            if (modesForLens != lens) {
                val info = lens.filter(p.availableCameraInfos).first()
                videoModes = VideoModes.supported(info)
                videoMode = videoModes.firstOrNull { it.quality == Quality.FHD && it.fps == 30 } ?: videoModes.firstOrNull()
                modesForLens = lens
            }
            val ratio = if (mode == "video") AspectRatio.RATIO_16_9 else targetRatio
            readCapabilities(lens.filter(p.availableCameraInfos).first())
            val previewBuilder = Preview.Builder().setTargetAspectRatio(ratio).setTargetRotation(rotation())
            Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    result.get(CaptureResult.SENSOR_SENSITIVITY)?.let { meteredIso = it }
                    result.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { meteredExposure = it }
                }
            })
            val previewUseCase = previewBuilder.build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val useCase = if (mode == "photo") {
                ImageCapture.Builder().setTargetAspectRatio(ratio).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetRotation(rotation()).build().also { image = it }
            } else {
                val selected = requireNotNull(videoMode)
                val recorder = VideoModes.recorder(selected)
                VideoCapture.Builder(recorder).apply {
                    setVideoStabilizationEnabled(true)
                }.build().also { it.targetRotation = rotation(); capture = it }
            }
            ownedUseCases.add(previewUseCase); ownedUseCases.add(useCase)
            camera = if (mode == "video") p.bindToLifecycle(this, lens, VideoModes.session(previewUseCase, requireNotNull(capture), requireNotNull(videoMode)))
                else p.bindToLifecycle(this, lens, previewUseCase, useCase)

            camera?.cameraInfo?.zoomState?.value?.let { zs ->
                currentZoom = zs.zoomRatio
                maxZoom = zs.maxZoomRatio.coerceAtMost(8.0f)
            }
            camera?.cameraControl?.enableTorch(torchEnabled)
            applyProSettings()
            ready = true; feedback.setText(R.string.camera_ready); refresh()
        }.onFailure {
            android.util.Log.e("MirrorCamera", "Bind $mode failed", it)
            ready = false; busy = false
            if (mode == "video") feedback.setText(R.string.video_mode_unavailable) else showCameraError()
            refresh()
        }
    }

    fun remote(action: String, value: String = "", x: Float = Float.NaN, y: Float = Float.NaN, sourceRotation: Int = -1): String {
        if (!screenActive || !ready) return getString(R.string.camera_loading)
        if (busy || countdownRunning) return getString(R.string.camera_busy)
        if (action == "photo" && recording != null) return getString(R.string.camera_recording_photo)
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            feedback.setText(R.string.storage_permission); return getString(R.string.storage_permission)
        }
        when (action) {
            "focus", "focusLock" -> {
                if (sourceRotation != rotation()) return "Posisi layar berubah. Ketuk lagi."
                val metrics = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                val location = IntArray(2); preview.getLocationOnScreen(location)
                val point = FocusCoordinates.intoPreview(x, y, metrics.widthPixels, metrics.heightPixels,
                    location[0], location[1], preview.width, preview.height) ?: return "Ketuk bagian gambar kamera."
                focusPreview(point.first, point.second, action == "focusLock")
                return "Mengatur fokus…"
            }
            "unlockFocus" -> {
                focusGeneration++; focusLocked = false; exposureLocked = false
                camera?.cameraControl?.cancelFocusAndMetering()
                applyProSettings()
                refresh("Fokus otomatis aktif")
                return "Fokus otomatis aktif"
            }
            "exposureLock" -> {
                if (!exposureLockSupported || proIso != 0 || proShutterMs != 0.0) return "Kunci pencahayaan tersedia saat ISO dan shutter Auto."
                val previous = exposureLocked
                exposureLocked = value == "true"
                applyProSettings { ok -> if (!ok) exposureLocked = previous; refresh(if (ok) (if (exposureLocked) "Pencahayaan dikunci" else "Pencahayaan otomatis") else "Gagal mengatur pencahayaan") }
                return "Mengatur pencahayaan…"
            }
            "mode", "quality", "iso", "shutter", "wb" -> return configure(action, value)
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
                gridView.visibility = if (gridEnabled) View.VISIBLE else View.GONE
                refresh(if (gridEnabled) "Garis pandu aktif" else "Garis pandu mati")
                return if (gridEnabled) "Garis pandu aktif" else "Garis pandu mati"
            }
            "timer" -> {
                timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
                timerButton.text = if (timerSeconds > 0) "Timer %ds".format(timerSeconds) else "Timer"
                refresh(if (timerSeconds > 0) "Timer %ds".format(timerSeconds) else "Timer mati")
                return if (timerSeconds > 0) "Timer %ds".format(timerSeconds) else "Timer mati"
            }
            "photo" -> {
                if (mode != "photo") { mode = "photo"; bindCamera() }
                if (!ready) return getString(R.string.camera_error)
                if (timerSeconds > 0 && !countdownRunning) {
                    startCountdown { takePhoto() }
                    return "Hitung mundur %ds".format(timerSeconds)
                }
                takePhoto(); return getString(R.string.photo_working)
            }
            "video" -> {
                if (recording != null) {
                    busy = true; feedback.setText(R.string.video_stopping); refresh(); recording?.stop()
                    return getString(R.string.video_stopping)
                }
                if (mode != "video") { mode = "video"; bindCamera() }
                if (!ready) return getString(R.string.camera_error)
                startVideo(); return getString(R.string.video_starting)
            }
            else -> return getString(R.string.camera_busy)
        }
    }

    private fun values(video: Boolean) = ContentValues().apply {
        val ext = if (video) "mp4" else "jpg"
        put(MediaStore.MediaColumns.DISPLAY_NAME, "MirrorSelfie_" + System.currentTimeMillis() + "." + ext)
        put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/MirrorSelfie" else "Pictures/MirrorSelfie")
    }

    private fun takePhoto() {
        val current = image ?: return
        busy = true; feedback.setText(R.string.photo_working); refresh()
        runCatching {
            val output = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values(false)).build()
            current.takePicture(output, mainExecutorCompat(), object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { busy = false; result(R.string.photo_saved) }
                override fun onError(exception: ImageCaptureException) { busy = false; result(R.string.photo_failed) }
            })
        }.onFailure { busy = false; result(R.string.photo_failed) }
    }

    @SuppressLint("MissingPermission")
    private fun startVideo() {
        val current = capture ?: return
        recordedMs = 0; lastStatusSecond = -1
        busy = true; feedback.setText(R.string.video_starting); refresh()
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values(true)).build()
        runCatching {
            var pending = current.output.prepareRecording(this, output)
            if (audio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
            recording = pending.start(mainExecutorCompat()) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> { recordingStarted = true; busy = false; result(R.string.control_recording) }
                    is VideoRecordEvent.Status -> {
                        recordedMs = event.recordingStats.recordedDurationNanos / 1_000_000
                        val seconds = recordedMs / 1000
                        if (seconds != lastStatusSecond) { lastStatusSecond = seconds; refresh() }
                        feedback.text = getString(R.string.recording_time, String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60))
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = null; recordingStarted = false; busy = false
                        if (screenActive && !ready) bindCamera()
                        result(if (event.hasError()) R.string.video_failed else R.string.video_saved)
                    }
                }
            }
        }.onFailure { recording = null; busy = false; result(R.string.video_failed) }
    }

    private fun startCountdown(onFinished: () -> Unit) {
        countdownRunning = true
        val stamp = ++countdownGeneration
        refresh()
        var left = timerSeconds
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (stamp != countdownGeneration || !screenActive || !ready) { countdownRunning = false; return }
                if (left > 0) {
                    feedback.text = "Foto dalam %d...".format(left)
                    refresh("Foto dalam %d...".format(left))
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

    
    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun applyProSettings(onResult: ((Boolean) -> Unit)? = null) {
        val cam = camera ?: return
        val control = Camera2CameraControl.from(cam.cameraControl)
        val builder = CaptureRequestOptions.Builder()

        // 1. White Balance / Kelvin
        if (proKelvin > 0) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX)
            val gains = ProCameraHelper.kelvinToRggb(proKelvin)
            builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }

        // 2. ISO & Shutter Speed
        if (proIso > 0 || proShutterMs > 0.0) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            if (proIso > 0) builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, proIso)
            if (proShutterMs > 0.0) {
                val nanos = (proShutterMs * 1_000_000.0).toLong()
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, nanos)
            }
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        if (exposureLockSupported && proIso == 0 && proShutterMs == 0.0)
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, exposureLocked)
        val current = camera
        val future = control.setCaptureRequestOptions(builder.build())
        future.addListener({
            if (current !== camera || !screenActive) return@addListener
            val ok = runCatching { future.get() }.isSuccess
            onResult?.invoke(ok)
        }, mainExecutorCompat())
    }

    private fun readCapabilities(info: CameraInfo) {
        val c = Camera2CameraInfo.from(info)
        val caps = c.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty()
        val manual = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps
        val isoRange = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val timeRange = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        isoOptions = if (manual && isoRange != null) (listOf(0, isoRange.lower) + listOf(100, 200, 400, 800, 1600, 3200, 6400).filter { isoRange.contains(it) } + isoRange.upper).distinct().sorted() else emptyList()
        val frameTime = if (mode == "video") 1_000_000_000L / (videoMode?.fps?.takeIf { it > 0 } ?: 30) else Long.MAX_VALUE
        shutterOptions = if (manual && timeRange != null) listOf(0L, 1_000_000L, 2_000_000L, 4_000_000L, 8_000_000L, 16_666_666L, 33_333_333L, 66_666_666L, 250_000_000L, 1_000_000_000L)
            .filter { it == 0L || (timeRange.contains(it) && it <= frameTime) } else emptyList()
        manualWb = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING in caps &&
            c.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.contains(CaptureRequest.CONTROL_AWB_MODE_OFF) == true
        exposureLockSupported = c.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
        focusSupported = c.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.any {
            it == CaptureRequest.CONTROL_AF_MODE_AUTO || it == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE || it == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
        } == true
        if (proIso !in isoOptions || (proShutterMs * 1_000_000).toLong() !in shutterOptions) { proIso = 0; proShutterMs = 0.0 }
        if (!manualWb) proKelvin = 0
        updateProLabels()
    }

    private fun configure(action: String, value: String): String {
        if (busy || recording != null || countdownRunning) return "Hentikan rekaman atau tunggu proses selesai."
        when (action) {
            "mode" -> {
                if (value !in setOf("photo", "video")) return "Mode tidak tersedia."
                val previous = mode
                mode = value; bindCamera()
                if (!ready) { mode = previous; bindCamera(); return "Mode gagal dibuka. Pengaturan sebelumnya dipulihkan." }
                refresh(); return "Mode ${if (mode == "photo") "Foto" else "Video"}"
            }
            "quality" -> {
                val selected = videoModes.firstOrNull { it.label == value } ?: return "Format tidak tersedia."
                val old = videoMode
                videoMode = selected
                if (mode == "video") {
                    bindCamera()
                    if (!ready) { videoMode = old; bindCamera(); return "Format gagal dibuka. Pengaturan sebelumnya dipulihkan." }
                }
                refresh(); return selected.label
            }
        }
        val oldIso = proIso; val oldShutter = proShutterMs; val oldWb = proKelvin
        when (action) {
            "iso" -> {
                val selected = value.toIntOrNull()?.takeIf { it in isoOptions } ?: return "ISO tidak tersedia."
                proIso = selected
                if (selected == 0) proShutterMs = 0.0
                else if (proShutterMs == 0.0) proShutterMs = (shutterOptions.filter { it > 0 }.minByOrNull { kotlin.math.abs(it - meteredExposure) } ?: return "Shutter manual tidak tersedia.") / 1_000_000.0
            }
            "shutter" -> {
                val selected = value.toLongOrNull()?.takeIf { it in shutterOptions } ?: return "Shutter tidak tersedia."
                proShutterMs = selected / 1_000_000.0
                if (selected == 0L) proIso = 0
                else if (proIso == 0) proIso = isoOptions.filter { it > 0 }.minByOrNull { kotlin.math.abs(it - meteredIso) } ?: return "ISO manual tidak tersedia."
            }
            "wb" -> {
                val selected = value.toIntOrNull()?.takeIf { it == 0 || it in 2000..8000 } ?: return "Gunakan 2000–8000 K, atau Auto."
                if (!manualWb && selected != 0) return "WB manual tidak tersedia."
                proKelvin = selected
            }
        }
        busy = true; refresh()
        applyProSettings { ok ->
            busy = false
            if (!ok) { proIso = oldIso; proShutterMs = oldShutter; proKelvin = oldWb; applyProSettings() }
            updateProLabels()
            refresh(if (ok) "Pengaturan diterapkan" else "Pengaturan gagal diterapkan")
        }
        return "Menerapkan pengaturan…"
    }

    private fun focusPreview(x: Float, y: Float, lock: Boolean) {
        val cam = camera ?: return
        if (!focusSupported) { refresh("Lensa ini memakai fokus tetap."); return }
        val stamp = ++focusGeneration
        focusLocked = false; exposureLocked = false
        applyProSettings {
            if (cam !== camera || stamp != focusGeneration) return@applyProSettings
            val point = preview.meteringPointFactory.createPoint(x, y)
            val builder = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            if (lock) builder.disableAutoCancel()
            val action = builder.build()
            if (!cam.cameraInfo.isFocusMeteringSupported(action)) { refresh("Fokus sentuh tidak tersedia."); return@applyProSettings }
            val future = cam.cameraControl.startFocusAndMetering(action)
            future.addListener({
                if (cam !== camera || stamp != focusGeneration || !screenActive) return@addListener
                val success = runCatching { future.get().isFocusSuccessful }.getOrDefault(false)
                focusLocked = lock && success
                exposureLocked = focusLocked && exposureLockSupported && proIso == 0 && proShutterMs == 0.0
                applyProSettings { ok ->
                    if (!ok) exposureLocked = false
                    refresh(if (!success) "Fokus belum didapat. Coba ketuk lagi." else if (focusLocked) "Fokus dikunci" else "Fokus diatur")
                }
            }, mainExecutorCompat())
        }
    }

    private fun updateProLabels() {
        if (!::isoButton.isInitialized) return
        isoButton.text = if (proIso == 0) "ISO: Auto" else "ISO $proIso"
        shutterButton.text = "S: ${shutterLabel((proShutterMs * 1_000_000).toLong())}"
        wbButton.text = if (proKelvin == 0) "WB: Auto" else "${proKelvin}K"
    }
    private fun showIsoDialog() {
        AlertDialog.Builder(this).setTitle("ISO").setItems(isoOptions.map { if (it == 0) "Auto" else "$it" }.toTypedArray()) { _, i -> configure("iso", isoOptions[i].toString()) }.show()
    }
    private fun showShutterDialog() {
        AlertDialog.Builder(this).setTitle("Shutter").setItems(shutterOptions.map { shutterLabel(it) }.toTypedArray()) { _, i -> configure("shutter", shutterOptions[i].toString()) }.show()
    }
    private fun showWbDialog() {
        val values = if (manualWb) listOf(0, 2800, 3200, 4000, 5000, 5600, 6500, 7500) else listOf(0)
        AlertDialog.Builder(this).setTitle("WB (Kelvin)").setItems(values.map { if (it == 0) "Auto" else "${it}K" }.toTypedArray()) { _, i -> configure("wb", values[i].toString()) }.show()
    }

    private fun result(text: Int) { feedback.setText(text); refresh(getString(text)) }
    private fun refresh(message: String = "") {
        val state = CameraSnapshot(screenActive && ready, recordingStarted, busy || countdownRunning, currentZoom, maxZoom, torchEnabled,
            mode, videoMode?.label.orEmpty(), videoModes.map { it.label }, proIso, isoOptions,
            (proShutterMs * 1_000_000).toLong(), shutterOptions, proKelvin, manualWb,
            focusSupported, focusLocked, exposureLockSupported, exposureLocked, recordedMs, rotation())
        if (::photoButton.isInitialized) {
            photoButton.isEnabled = state.canTakePhoto
            videoButton.isEnabled = state.canToggleVideo
            videoButton.contentDescription = getString(if (recordingStarted) R.string.stop_recording else R.string.record_stop)
            flipButton.isEnabled = state.canConfigure
        }
        Session.updateCamera(state, message)
    }

    @Suppress("DEPRECATION")
    private fun rotation() = windowManager.defaultDisplay.rotation
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        image?.targetRotation = rotation()
        if (recording == null) capture?.targetRotation = rotation()
        if (Session.state.mode == "host") Session.resize(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        focusGeneration++; focusLocked = false; exposureLocked = false
        camera?.cameraControl?.cancelFocusAndMetering(); applyProSettings(); refresh()
    }
}
