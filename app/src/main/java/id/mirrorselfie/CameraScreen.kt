package id.mirrorselfie

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/** Shared permission and lifecycle handling for our camera and offline QR scanner. */
abstract class CameraScreen : Activity(), LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = registry
    protected lateinit var layout: LinearLayout
    protected lateinit var preview: PreviewView
    protected lateinit var feedback: TextView
    protected lateinit var controls:LinearLayout
    private lateinit var stage:LinearLayout
    private lateinit var controlScroll:ScrollView
    protected var provider: ProcessCameraProvider? = null
    protected val ownedUseCases=mutableListOf<androidx.camera.core.UseCase>()
    protected var screenActive = false
    protected abstract val titleRes: Int
    protected open fun requestedPermissions() = arrayOf(Manifest.permission.CAMERA)
    protected abstract fun bindCamera()
    protected open fun onCameraPermissions() {}
    private var loadingProvider = false
    private var permissionPanel:LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Studio.background); fitsSystemWindows = true
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(cameraButton(R.string.back) { finish() }, LinearLayout.LayoutParams(dp(92), dp(48)))
        header.addView(cameraText(titleRes, 18), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart=dp(16) })
        layout.addView(header)
        feedback = cameraText(R.string.camera_loading, 13).apply {
            gravity=Gravity.CENTER; setPadding(dp(8),dp(12),dp(8),dp(12)); accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        layout.addView(feedback)
        preview = PreviewView(this).apply {
            implementationMode=PreviewView.ImplementationMode.COMPATIBLE
            scaleType=PreviewView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        controls=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,dp(8),0,0) }
        controlScroll=ScrollView(this).apply { isVerticalScrollBarEnabled=false; addView(controls); visibility=android.view.View.GONE }
        stage=LinearLayout(this)
        layout.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        arrange()
        setContentView(layout)
    }

    override fun onStart() {
        super.onStart(); screenActive=true; registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) {
            onCameraPermissions(); loadProvider()
        } else requestPermissions(requestedPermissions(),40)
    }
    override fun onResume() { super.onResume(); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    override fun onPause() { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); super.onPause() }
    override fun onStop() { screenActive=false; registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP); releaseUseCases(); super.onStop() }
    override fun onDestroy() { registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); super.onDestroy() }

    private fun loadProvider() {
        permissionPanel?.let { preview.removeView(it) }; permissionPanel=null
        if(provider!=null) { bindCamera(); return }
        if(loadingProvider) return
        loadingProvider=true
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            loadingProvider=false
            runCatching {
                provider=future.get()
                if(screenActive && !isFinishing) bindCamera()
            }.onFailure { showCameraError() }
        },mainExecutorCompat())
    }
    @Deprecated("Platform permission callback")
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode!=40) return
        if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) {
            onCameraPermissions(); if(screenActive) loadProvider()
        } else {
            feedback.setText(R.string.camera_permission_body)
            val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),dp(24),dp(12),dp(24)) }
            panel.addView(cameraText(R.string.camera_permission_title,20))
            panel.addView(cameraButton(R.string.camera_permission_action) { requestPermissions(requestedPermissions(),40) },LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(18)})
            panel.addView(cameraButton(R.string.app_settings) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))) },LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(10)})
            // Replace the empty camera area, keeping navigation available.
            permissionPanel?.let { preview.removeView(it) }; permissionPanel=panel
            preview.addView(panel,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
        }
    }
    protected fun showCameraError() { feedback.setText(R.string.camera_error) }
    protected fun showControls() { controlScroll.visibility=android.view.View.VISIBLE; arrange() }
    private fun arrange() {
        stage.removeAllViews()
        val landscape=resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE
        stage.orientation=if(landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        stage.addView(preview,if(landscape) LinearLayout.LayoutParams(0,-1,1f) else LinearLayout.LayoutParams(-1,0,1f))
        stage.addView(controlScroll,if(landscape) LinearLayout.LayoutParams(dp(256),-1).apply{marginStart=dp(10)} else LinearLayout.LayoutParams(-1,-2))
    }
    override fun onConfigurationChanged(newConfig:Configuration) { super.onConfigurationChanged(newConfig); arrange() }
    protected fun releaseUseCases() { provider?.unbind(*ownedUseCases.toTypedArray()); ownedUseCases.clear() }
    protected fun mainExecutorCompat() = java.util.concurrent.Executor { command -> runOnUiThread(command) }
    protected fun cameraText(text:Int,size:Int=14)=TextView(this).apply {
        setText(text); textSize=size.toFloat(); setTextColor(Studio.text); includeFontPadding=false
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
    }
    protected fun cameraButton(text:Int,primary:Boolean=false,action:()->Unit)=cameraText(text).apply {
        gravity=Gravity.CENTER; minHeight=dp(52); setPadding(dp(12),dp(12),dp(12),dp(12))
        setTextColor(if(primary) Studio.ink else Studio.text)
        background=Studio.touch(this@CameraScreen,if(primary) Studio.mint else Studio.raised,16)
        setOnClickListener { action() }; isFocusable=true
    }
    protected fun dp(value:Int)=(value*resources.displayMetrics.density+.5f).toInt()
}
