
const fs = require('fs');

const ca = `package id.mirrorselfie

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
import androidx.camera.video.*
import java.util.Locale

class CameraActivity : CameraScreen() {
    override val titleRes = R.string.camera_title
    private var lens = CameraSelector.DEFAULT_BACK_CAMERA
    private var mode = "photo"
    private var targetRatio = AspectRatio.RATIO_16_9
    private var camera: Camera? = null
    private var image: ImageCapture? = null
    private var capture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
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
        topBar.addView(ratioButton, LinearLayout.LayoutParams(-2, dp(38)))

        val topParams = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        overlayContainer.addView(topBar, topParams)

        // 3. Bottom Camera Controls (Shutter, Switch, Video Toggle)
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(32))
        }

        flipButton = iconBtn("camera", R.string.camera_switch) {
            val next = if (lens == CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if (provider?.hasCamera(next) == true) { lens = next; bindCamera() } else feedback.setText(R.string.camera_switch_failed)
        }

        // Circular Shutter Button
        photoButton = circularShutter(Studio.mint) { remote("photo") }
        videoButton = circularShutter(Studio.coral) { remote("video") }

        bottomBar.addView(flipButton, LinearLayout.LayoutParams(dp(54), dp(54)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        bottomBar.addView(photoButton, LinearLayout.LayoutParams(dp(76), dp(76)).apply { marginEnd = dp(16) })
        bottomBar.addView(videoButton, LinearLayout.LayoutParams(dp(76), dp(76)))
        bottomBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        
        val modeToggle = pillBtn("Mode") {
            mode = if (mode == "photo") "video" else "photo"
            bindCamera()
        }
        bottomBar.addView(modeToggle, LinearLayout.LayoutParams(-2, dp(44)))

        val bottomParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        overlayContainer.addView(bottomBar, bottomParams)

        // Tap to focus & metering
        preview.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && ready) {
                val factory = preview.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point).build()
                camera?.cameraControl?.startFocusAndMetering(action)
                feedback.setText(R.string.camera_focused)
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
        recording?.stop()
        CameraControl.detach(this)
        super.onStop()
    }

    @SuppressLint("MissingPermission")
    override fun bindCamera() {
        val p = provider ?: return
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
            val previewUseCase = Preview.Builder().setTargetAspectRatio(ratio).setTargetRotation(rotation()).build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            val useCase = if (mode == "photo") {
                ImageCapture.Builder().setTargetAspectRatio(ratio).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetRotation(rotation()).build().also { image = it }
            } else {
                val selected = requireNotNull(videoMode)
                val recorder = VideoModes.recorder(selected)
                VideoCapture.Builder(recorder).apply {
                    setVideoStabilizationEnabled(true)
                    if (selected.fps > 0) setTargetFrameRate(android.util.Range(selected.fps, selected.fps))
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
            ready = true; feedback.setText(R.string.camera_ready); refresh()
        }.onFailure {
            ready = false; busy = false
            if (mode == "video") feedback.setText(R.string.video_mode_unavailable) else showCameraError()
            refresh()
        }
    }

    fun remote(action: String): String {
        if (!screenActive || !ready) return getString(R.string.camera_loading)
        if (busy) return getString(R.string.camera_busy)
        if (action == "photo" && recording != null) return getString(R.string.camera_recording_photo)
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            feedback.setText(R.string.storage_permission); return getString(R.string.storage_permission)
        }
        when (action) {
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
                return if (gridEnabled) "Garis pandu aktif" else "Garis pandu mati"
            }
            "timer" -> {
                timerSeconds = when (timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
                timerButton.text = if (timerSeconds > 0) "Timer %ds".format(timerSeconds) else "Timer"
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
        busy = true; feedback.setText(R.string.video_starting); refresh()
        val output = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values(true)).build()
        runCatching {
            var pending = current.output.prepareRecording(this, output)
            if (audio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled()
            recording = pending.start(mainExecutorCompat()) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> { recordingStarted = true; busy = false; result(R.string.control_recording) }
                    is VideoRecordEvent.Status -> {
                        val seconds = event.recordingStats.recordedDurationNanos / 1_000_000_000
                        feedback.text = getString(R.string.recording_time, String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60))
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = null; recordingStarted = false; busy = false
                        result(if (event.hasError()) R.string.video_failed else R.string.video_saved)
                    }
                }
            }
        }.onFailure { recording = null; busy = false; result(R.string.video_failed) }
    }

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

    private fun result(text: Int) { feedback.setText(text); refresh(getString(text)) }
    private fun refresh(message: String = "") {
        val state = CameraSnapshot(screenActive && ready, recordingStarted, busy, currentZoom, maxZoom, torchEnabled)
        Session.updateCamera(state, message)
    }

    @Suppress("DEPRECATION")
    private fun rotation() = windowManager.defaultDisplay.rotation
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        image?.targetRotation = rotation()
        if (recording == null) capture?.targetRotation = rotation()
        if (Session.state.mode == "host") Session.resize(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
    }
}
`;

fs.writeFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', ca, 'utf8');
console.log('CameraActivity written');

