package id.mirrorselfie

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/** Edge-to-edge frameless camera surface with floating transparent HUD. */
abstract class CameraScreen : Activity(), LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override fun getLifecycle(): Lifecycle = registry
    protected lateinit var rootFrame: FrameLayout
    protected lateinit var preview: PreviewView
    protected lateinit var feedback: TextView
    protected lateinit var overlayContainer: FrameLayout
    protected var provider: ProcessCameraProvider? = null
    protected val ownedUseCases = mutableListOf<androidx.camera.core.UseCase>()
    protected var screenActive = false
    protected abstract val titleRes: Int
    protected open fun requestedPermissions() = arrayOf(Manifest.permission.CAMERA)
    protected abstract fun bindCamera()
    protected open fun onCameraPermissions() {}
    private var loadingProvider = false
    private var permissionPanel: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )

        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // Fullscreen camera viewfinder (FILL_CENTER for frameless look)
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.BLACK)
        }
        rootFrame.addView(preview, FrameLayout.LayoutParams(-1, -1))

        // Floating Overlay container
        overlayContainer = FrameLayout(this)
        rootFrame.addView(overlayContainer, FrameLayout.LayoutParams(-1, -1))

        feedback = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.WHITE)
            setShadowLayer(6f, 0f, 2f, 0xCC000000.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(10), dp(16), dp(10))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val feedbackParams = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(48)
        }
        overlayContainer.addView(feedback, feedbackParams)

        setContentView(rootFrame)
    }

    override fun onStart() {
        super.onStart(); screenActive = true; registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onCameraPermissions(); loadProvider()
        } else requestPermissions(requestedPermissions(), 40)
    }
    override fun onResume() { super.onResume(); registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    override fun onPause() { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); super.onPause() }
    override fun onStop() { screenActive = false; registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP); releaseUseCases(); super.onStop() }
    override fun onDestroy() { registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); super.onDestroy() }

    private fun loadProvider() {
        permissionPanel?.let { overlayContainer.removeView(it) }; permissionPanel = null
        if (provider != null) { bindCamera(); return }
        if (loadingProvider) return
        loadingProvider = true
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            loadingProvider = false
            runCatching {
                provider = future.get()
                if (screenActive && !isFinishing) bindCamera()
            }.onFailure { showCameraError() }
        }, mainExecutorCompat())
    }

    @Deprecated("Platform permission callback")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 40) return
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onCameraPermissions(); if (screenActive) loadProvider()
        } else {
            feedback.setText(R.string.camera_permission_body)
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(24), dp(24), dp(24))
                background = Studio.shape(this@CameraScreen, Studio.surface, 24)
            }
            panel.addView(cameraText(R.string.camera_permission_title, 20))
            panel.addView(cameraButton(R.string.camera_permission_action) { requestPermissions(requestedPermissions(), 40) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
            panel.addView(cameraButton(R.string.app_settings) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            permissionPanel = panel
            overlayContainer.addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { setMargins(dp(20), 0, dp(20), 0) })
        }
    }

    protected fun showCameraError() { feedback.setText(R.string.camera_error) }
    protected fun releaseUseCases() { provider?.unbind(*ownedUseCases.toTypedArray()); ownedUseCases.clear() }
    protected fun mainExecutorCompat() = java.util.concurrent.Executor { command -> runOnUiThread(command) }
    protected fun cameraText(text: Int, size: Int = 14) = TextView(this).apply {
        setText(text); textSize = size.toFloat(); setTextColor(Color.WHITE); includeFontPadding = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    protected fun cameraButton(text: Int, primary: Boolean = false, action: () -> Unit) = cameraText(text).apply {
        gravity = Gravity.CENTER; minHeight = dp(48); setPadding(dp(16), dp(10), dp(16), dp(10))
        setTextColor(if (primary) Studio.ink else Color.WHITE)
        background = Studio.touch(this@CameraScreen, if (primary) Studio.mint else Studio.raised, 18)
        setOnClickListener { action() }; isFocusable = true
    }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
