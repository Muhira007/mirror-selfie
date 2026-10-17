package id.mirrorselfie

import android.Manifest
import android.app.AlertDialog
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.video.*
import java.util.Locale

class CameraActivity : CameraScreen() {
    override val titleRes=R.string.camera_title
    private var lens=CameraSelector.DEFAULT_BACK_CAMERA
    private var mode="photo"
    private var camera:Camera?=null
    private var image:ImageCapture?=null
    private var capture:VideoCapture<Recorder>?=null
    private var recording:Recording?=null
    private var recordingStarted=false
    private var busy=false
    private var ready=false
    private var audio=true
    private lateinit var photoButton:TextView
    private lateinit var videoButton:TextView
    private lateinit var flipButton:TextView
    private lateinit var audioButton:TextView
    private lateinit var qualityButton:TextView
    private var videoModes=emptyList<VideoMode>()
    private var videoMode:VideoMode?=null
    private var modesForLens:CameraSelector?=null

    override fun requestedPermissions():Array<String> = buildList {
        add(Manifest.permission.CAMERA); add(Manifest.permission.RECORD_AUDIO)
        if(Build.VERSION.SDK_INT<=28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray()

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val captureRow=LinearLayout(this)
        photoButton=cameraButton(R.string.photo) { remote("photo") }
        videoButton=cameraButton(R.string.record_stop,true) { remote("video") }
        captureRow.addView(photoButton,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(5)})
        captureRow.addView(videoButton,LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(5)})
        controls.addView(captureRow,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4)})
        val options=LinearLayout(this)
        flipButton=cameraButton(R.string.camera_switch) {
            val next=if(lens==CameraSelector.DEFAULT_BACK_CAMERA) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if(provider?.hasCamera(next)==true) { lens=next; bindCamera() } else feedback.setText(R.string.camera_switch_failed)
        }
        audioButton=cameraButton(R.string.audio_on) {
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),42)
            else { audio=!audio; refresh() }
        }
        options.addView(flipButton,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(5)})
        options.addView(audioButton,LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=dp(5)})
        controls.addView(options,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        qualityButton=cameraButton(R.string.video_quality) {
            if(videoModes.isEmpty()) return@cameraButton
            AlertDialog.Builder(this).setTitle(R.string.video_quality)
                .setSingleChoiceItems(videoModes.map{it.label}.toTypedArray(),videoModes.indexOf(videoMode)) { dialog,position ->
                    videoMode=videoModes[position]; mode="video"; bindCamera(); dialog.dismiss()
                }.setNegativeButton(R.string.cancel,null).show()
        }
        controls.addView(qualityButton,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})
        showControls()
    }
    override fun onCameraPermissions() { audio=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED; refresh() }
    override fun onStart() { super.onStart(); CameraControl.attach(this) }
    override fun onStop() {
        ready=false
        recording?.stop()
        CameraControl.detach(this)
        super.onStop()
    }

    @SuppressLint("MissingPermission") // CameraScreen checks CAMERA before calling bindCamera.
    override fun bindCamera() {
        val p=provider ?: return
        ready=false; camera=null; image=null; capture=null; refresh()
        runCatching {
            releaseUseCases()
            if(!p.hasCamera(lens)) lens=if(p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            if(modesForLens!=lens) {
                val info=lens.filter(p.availableCameraInfos).first()
                videoModes=VideoModes.supported(info)
                videoMode=videoModes.firstOrNull{it.quality==Quality.FHD && it.fps==30} ?: videoModes.firstOrNull()
                modesForLens=lens
                if(BuildConfig.DEBUG) android.util.Log.i("MirrorCamera","Video modes: "+videoModes.joinToString{it.label})
            }
            val previewUseCase=Preview.Builder().setTargetAspectRatio(if(mode=="photo") androidx.camera.core.AspectRatio.RATIO_4_3 else androidx.camera.core.AspectRatio.RATIO_16_9).setTargetRotation(rotation()).build().also{it.setSurfaceProvider(preview.surfaceProvider)}
            val useCase=if(mode=="photo") {
                ImageCapture.Builder().setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3).setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetRotation(rotation()).build().also{image=it}
            } else {
                val selected=requireNotNull(videoMode)
                val recorder=VideoModes.recorder(selected)
                VideoCapture.Builder(recorder).apply { if(selected.fps>0) setTargetFrameRate(android.util.Range(selected.fps,selected.fps)) }.build().also{it.targetRotation=rotation(); capture=it}
            }
            ownedUseCases.add(previewUseCase); ownedUseCases.add(useCase)
            camera=if(mode=="video") p.bindToLifecycle(this,lens,VideoModes.session(previewUseCase,requireNotNull(capture),requireNotNull(videoMode)))
                else p.bindToLifecycle(this,lens,previewUseCase,useCase)
            ready=true; feedback.setText(R.string.camera_ready); refresh()
        }.onFailure {
            ready=false; busy=false
            if(mode=="video") feedback.setText(R.string.video_mode_unavailable) else showCameraError()
            if(BuildConfig.DEBUG) android.util.Log.e("MirrorCamera","Camera setup failed",it)
            refresh()
        }
    }

    fun remote(action:String):String {
        if(!screenActive || !ready) return getString(R.string.camera_loading)
        if(busy) return getString(R.string.camera_busy)
        if(action=="photo" && recording!=null) return getString(R.string.camera_recording_photo)
        if(Build.VERSION.SDK_INT<=28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) {
            feedback.setText(R.string.storage_permission); return getString(R.string.storage_permission)
        }
        when(action) {
            "photo" -> {
                if(mode!="photo") { mode="photo"; bindCamera() }
                if(!ready) return getString(R.string.camera_error)
                takePhoto(); return getString(R.string.photo_working)
            }
            "video" -> {
                if(recording!=null) {
                    busy=true; feedback.setText(R.string.video_stopping); refresh(); recording?.stop()
                    return getString(R.string.video_stopping)
                }
                if(mode!="video") { mode="video"; bindCamera() }
                if(!ready) return getString(R.string.camera_error)
                startVideo(); return getString(R.string.video_starting)
            }
            else -> return getString(R.string.camera_busy)
        }
    }

    private fun values(video:Boolean)=ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME,"MirrorSelfie_${System.currentTimeMillis()}.${if(video) "mp4" else "jpg"}")
        put(MediaStore.MediaColumns.MIME_TYPE,if(video) "video/mp4" else "image/jpeg")
        if(Build.VERSION.SDK_INT>=29) put(MediaStore.MediaColumns.RELATIVE_PATH,if(video) "Movies/MirrorSelfie" else "Pictures/MirrorSelfie")
    }
    private fun takePhoto() {
        val current=image ?: return
        busy=true; feedback.setText(R.string.photo_working); refresh()
        runCatching {
            val output=ImageCapture.OutputFileOptions.Builder(contentResolver,MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values(false)).build()
            current.takePicture(output,mainExecutorCompat(),object:ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults:ImageCapture.OutputFileResults) { busy=false; result(R.string.photo_saved) }
                override fun onError(exception:ImageCaptureException) { busy=false; result(R.string.photo_failed) }
            })
        }.onFailure { busy=false; result(R.string.photo_failed) }
    }
    @SuppressLint("MissingPermission") // Audio is enabled only after checking RECORD_AUDIO below.
    private fun startVideo() {
        val current=capture ?: return
        busy=true; feedback.setText(R.string.video_starting); refresh()
        val output=MediaStoreOutputOptions.Builder(contentResolver,MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values(true)).build()
        runCatching {
            var pending=current.output.prepareRecording(this,output)
            if(audio && checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) pending=pending.withAudioEnabled()
            recording=pending.start(mainExecutorCompat()) { event ->
                when(event) {
                    is VideoRecordEvent.Start -> { recordingStarted=true; busy=false; result(R.string.control_recording) }
                    is VideoRecordEvent.Status -> {
                        val seconds=event.recordingStats.recordedDurationNanos/1_000_000_000
                        feedback.text=getString(R.string.recording_time,String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60))
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording=null; recordingStarted=false; busy=false
                        result(if(event.hasError()) R.string.video_failed else R.string.video_saved)
                    }
                }
            }
        }.onFailure { recording=null; busy=false; result(R.string.video_failed) }
    }
    private fun result(text:Int) { feedback.setText(text); refresh(getString(text)) }
    private fun refresh(message:String="") {
        val state=CameraSnapshot(screenActive && ready,recordingStarted,busy)
        if(::photoButton.isInitialized) {
            photoButton.isEnabled=state.canTakePhoto; photoButton.alpha=if(photoButton.isEnabled) 1f else .4f
            videoButton.isEnabled=state.canToggleVideo; videoButton.alpha=if(videoButton.isEnabled) 1f else .4f
            videoButton.setText(if(recordingStarted) R.string.stop_recording else R.string.record_stop)
            flipButton.isEnabled=ready && recording==null && !busy; flipButton.alpha=if(flipButton.isEnabled) 1f else .4f
            audioButton.isEnabled=recording==null && !busy; audioButton.setText(if(audio) R.string.audio_on else R.string.audio_off)
            qualityButton.isEnabled=recording==null && !busy && videoModes.isNotEmpty()
            qualityButton.text=videoMode?.label ?: getString(R.string.video_quality)
        }
        Session.updateCamera(state,message)
    }
    @Deprecated("Platform permission callback")
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        if(requestCode==42) {
            audio=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
            if(!audio) feedback.setText(R.string.audio_denied)
            refresh(); return
        }
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
    }
    @Suppress("DEPRECATION")
    private fun rotation()=windowManager.defaultDisplay.rotation
    override fun onConfigurationChanged(newConfig:Configuration) {
        super.onConfigurationChanged(newConfig)
        image?.targetRotation=rotation()
        // Do not rebind or rotate an in-progress recording.
        if(recording==null) capture?.targetRotation=rotation()
        if(Session.state.mode=="host") Session.resize(resources.displayMetrics.widthPixels,resources.displayMetrics.heightPixels)
    }
}
