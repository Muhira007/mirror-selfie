package id.mirrorselfie

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private lateinit var statusCard: LinearLayout
    private lateinit var roleBadge: TextView
    private lateinit var back: View
    private var photoAction: LinearLayout? = null
    private var videoAction: LinearLayout? = null
    private var networkMetric: TextView? = null
    private var controlFeedback: TextView? = null
    private var waitingVideo: View? = null
    private var video: SurfaceViewRenderer? = null
    private var viewerHud: FrameLayout? = null
    private var recordingStatus: TextView? = null
    private var connectionWarning: TextView? = null
    private val remoteTools = mutableListOf<View>()
    private var viewerControlsVisible = true
    private var currentPage = ""
    private var displayedCode = ""
    private var address = ""
    private var joinDraft = ""
    private var mirrored = false
    private var captureCancelled = false
    private var backRegistered = false
    private val nativeBack by lazy { android.window.OnBackInvokedCallback { showHome() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        address = savedInstanceState?.getString("address").orEmpty()
        joinDraft = savedInstanceState?.getString("joinDraft").orEmpty()
        mirrored = savedInstanceState?.getBoolean("mirrored") ?: false
        viewerControlsVisible = savedInstanceState?.getBoolean("viewerControlsVisible") ?: true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Studio.background
        window.navigationBarColor = Studio.background
        root = column().apply { setBackgroundColor(Studio.background); fitsSystemWindows = true }
        header = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(12)) }
        back = iconButton("back", R.string.back) { showHome() }.apply { visibility = View.GONE }
        header.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
        header.addView(ImageView(this).apply { setImageResource(R.mipmap.ic_launcher); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) })
        header.addView(column().apply {
            addView(label(R.string.app_name, 19, Studio.text, true))
            addView(label(R.string.brand_subtitle, 10, Studio.muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        roleBadge = label(R.string.local_badge, 9, Studio.mint, true).apply {
            letterSpacing = .08f; setPadding(dp(10), dp(7), dp(10), dp(7)); background = Studio.shape(this@MainActivity, Studio.raised, 20)
        }
        header.addView(roleBadge)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        statusCard = row().apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(13), dp(10), dp(13), dp(10)); background = Studio.shape(this@MainActivity, Studio.surface, 14)
            addView(StudioIcon(this@MainActivity, "link"), LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) })
        }
        status = label("", 12, Studio.muted)
        statusCard.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(20), 0, dp(20), dp(8)) })
        body = column()
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render(Session.state)
        handleTestIntent(intent)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("address", address); outState.putString("joinDraft", joinDraft); outState.putBoolean("mirrored", mirrored)
        outState.putBoolean("viewerControlsVisible", viewerControlsVisible)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleTestIntent(intent) }
    private fun handleTestIntent(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        intent.getStringExtra("joinCode")?.let {
            intent.removeExtra("joinCode")
            Session.join(applicationContext, it)
        }
    }
    override fun onStart() { super.onStart(); Session.listener = ::render; render(Session.state) }
    override fun onStop() { Session.listener = null; releaseVideo(); if (currentPage == "viewer") currentPage = ""; super.onStop() }

    private fun render(state: SessionState) {
        if (isFinishing || isDestroyed) return
        if (state.mode != "idle") captureCancelled = false
        status.text = if(captureCancelled) getString(R.string.capture_cancelled) else state.status
        statusCard.visibility = if (state.mode == "idle" && state.status == "Siap" && !captureCancelled) View.GONE else View.VISIBLE
        roleBadge.setText(when(state.mode) { "host" -> R.string.host_badge; "viewer" -> R.string.viewer_badge; else -> R.string.local_badge })
        if (state.mode == "viewer") {
            if (currentPage != "viewer" && Session.eglContext != null) showViewer()
        } else if (state.mode == "host") {
            if (currentPage != "host" || displayedCode != state.code) showHost(state.code)
        } else if (currentPage !in setOf("home", "join", "setup")) showHome()
        if (currentPage == "viewer") statusCard.visibility = View.GONE
        networkMetric?.text = state.rttMs?.let { getString(R.string.rtt_value, it) } ?: getString(R.string.rtt_waiting)
        controlFeedback?.text = state.controlText.ifEmpty { getString(when { state.camera.recording->R.string.control_recording; state.camera.available->R.string.control_ready; else->R.string.control_hint }) }
        photoAction?.let { it.isEnabled=state.live && state.camera.canTakePhoto; it.alpha=if(it.isEnabled) 1f else .35f }
        videoAction?.let {
            it.isEnabled=state.live && state.camera.canToggleVideo; it.alpha=if(it.isEnabled) 1f else .35f
            (it.getChildAt(1) as TextView).setText(if(state.camera.recording) R.string.stop_recording else R.string.record_stop)
            it.background=Studio.touch(this,if(state.camera.recording) Studio.coral else Studio.mint,18)
        }
        waitingVideo?.visibility = if (state.frames == 0L) View.VISIBLE else View.GONE
        remoteTools.forEach { it.isEnabled = state.live && state.camera.available && !state.camera.busy; it.alpha = if (it.isEnabled) 1f else .35f }
        video?.alpha = if (state.live || state.frames == 0L) 1f else .3f
        connectionWarning?.apply {
            text = if (state.reconnecting) "Menyambung ulang…\nCek Wi-Fi atau hotspot kedua HP" else "Belum ada gambar terbaru\nMemeriksa koneksi…"
            visibility = if (!state.live && (state.frames > 0 || state.reconnecting)) View.VISIBLE else View.GONE
        }
        recordingStatus?.apply {
            val cameraBattery = if (state.remoteBattery >= 0) "Kamera ${state.remoteBattery}%" else "Kamera —"
            val monitorBattery = if (state.localBattery >= 0) "Monitor ${state.localBattery}%" else "Monitor —"
            val storage = if (state.freeBytes >= 0) "Sisa %.1f GB".format(java.util.Locale.ROOT, state.freeBytes / 1_000_000_000.0) else "Sisa —"
            val rec = if (!state.live) "Status belum terbarui" else if (state.camera.recording) "● REC ${recordingLabel(state.camera.recordedMs)}" else if (state.camera.busy) "Memproses…" else "Siap"
            text = "$rec · $cameraBattery · $monitorBattery\n$storage" + if (state.camera.focusLocked && state.live) " · Fokus terkunci" else ""
            setTextColor(if (!state.live) Studio.muted else if (state.camera.recording) Studio.coral else Studio.text)
        }
    }

    private fun clear(page: String) {
        releaseVideo(); body.removeAllViews(); currentPage = page
        viewerHud = null; recordingStatus = null; connectionWarning = null; remoteTools.clear()
        header.visibility = if (page == "viewer") View.GONE else View.VISIBLE
        statusCard.visibility = if (page == "viewer" || (Session.state.mode == "idle" && Session.state.status == "Siap" && !captureCancelled)) View.GONE else View.VISIBLE
        applyViewerWindow(page == "viewer")
        if (page != "join") getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(body.windowToken,0)
        networkMetric = null; controlFeedback = null; waitingVideo = null; photoAction=null; videoAction=null
        back.visibility = if (page == "setup" || page == "join") View.VISIBLE else View.GONE
        if (Build.VERSION.SDK_INT >= 33) {
            if (backRegistered) onBackInvokedDispatcher.unregisterOnBackInvokedCallback(nativeBack)
            backRegistered = page == "setup" || page == "join"
            if (backRegistered) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, nativeBack)
        }
    }

    private fun page(name: String): LinearLayout {
        clear(name)
        val content = column().apply { setPadding(dp(20), dp(10), dp(20), dp(24)) }
        body.addView(ScrollView(this).apply {
            isFillViewport = true; clipToPadding = false; isVerticalScrollBarEnabled = false
            addView(content, FrameLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, -1))
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            content.alpha = 0f; content.translationY = dp(8).toFloat()
            content.animate().alpha(1f).translationY(0f).setDuration(180).start()
        }
        return content
    }

    private fun showHome() {
        val content = page("home")
        val hero = column().apply {
            setPadding(dp(22), dp(22), dp(22), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF173A40.toInt(), 0xFF10262F.toInt(), Studio.surface)).apply { cornerRadius = dp(28).toFloat() }
        }
        hero.addView(label(R.string.hero_title, 29, Studio.text, true))
        hero.addView(label(R.string.hero_body, 13, Studio.muted), margins(top = 9))
        hero.addView(StudioArtwork(this), LinearLayout.LayoutParams(-1, dp(140)))
        content.addView(hero)
        val heading = row().apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label(R.string.choose_role, 16, Studio.text, true), LinearLayout.LayoutParams(0, -2, 1f))
        heading.addView(iconButton("info", R.string.help) { help() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        content.addView(heading, margins(top = 12))
        content.addView(roleCard(R.string.sender_tag, R.string.sender_title, R.string.sender_caption, "camera", true) { showSenderSetup() })
        content.addView(roleCard(R.string.viewer_tag, R.string.viewer_title, R.string.viewer_caption, "monitor", false) { showJoin() }, margins(top = 12))
        val utilities = row()
        utilities.addView(utility(R.string.connection, R.string.connection_hint, "wifi") { openConnectionSettings() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        utilities.addView(utility(R.string.help, R.string.help_caption, "info") { help() }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
        content.addView(utilities, margins(top = 18))
        content.addView(label(R.string.local_footer, 11, Studio.muted).apply { gravity = Gravity.CENTER }, margins(top = 18))
    }

    private fun showSenderSetup() {
        val content = page("setup")
        intro(content, R.string.setup_title, R.string.setup_body)
        val addresses = SessionIdentity.localAddresses()
        val network = card()
        network.addView(iconHeading("wifi", R.string.network_title))
        network.addView(label(R.string.network_body, 13, Studio.muted), margins(top = 10))
        if (addresses.isEmpty()) {
            network.addView(label(R.string.no_network, 17, Studio.text, true), margins(top = 20))
            network.addView(label(R.string.no_network_body, 14), margins(top = 8))
            network.addView(action(R.string.network_settings, "wifi") { openConnectionSettings() }, margins(top = 20))
            network.addView(action(R.string.refresh_network, "link", primary = false) { showSenderSetup() }, margins(top = 10))
        } else {
            if (address !in addresses) address = addresses.first()
            val group = RadioGroup(this)
            addresses.forEach { ip ->
                group.addView(RadioButton(this).apply {
                    id = View.generateViewId(); text = ip; textSize = 16f; setTextColor(Studio.text)
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    buttonTintList = ColorStateList.valueOf(Studio.mint); minHeight = dp(52)
                    isChecked = ip == address
                    setOnCheckedChangeListener { _, checked -> if (checked) address = ip }
                }, RadioGroup.LayoutParams(-1, -2))
            }
            network.addView(group, margins(top = 12))
        }
        content.addView(network, margins(top = 24))
        if (addresses.isNotEmpty()) {
            content.addView(action(R.string.start_sender, "arrow") { requestCapture() }, margins(top = 20))
            content.addView(note(R.string.capture_note), margins(top = 16))
        }
    }

    private fun requestCapture() {
        captureCancelled = false
        val manager = getSystemService(MediaProjectionManager::class.java)
        val request = if (Build.VERSION.SDK_INT >= 34) manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) else manager.createScreenCaptureIntent()
        @Suppress("DEPRECATION")
        startActivityForResult(request, 20)
    }

    @Deprecated("Platform callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode==30 && resultCode==RESULT_OK) {
            data?.getStringExtra("code")?.let { Session.join(applicationContext,it) }
            return
        }
        if (requestCode == 20 && resultCode == RESULT_OK && data != null) {
            val size = resources.displayMetrics
            startForegroundService(Intent(this, ProjectionService::class.java).putExtra("permission", data)
                .putExtra("width", size.widthPixels).putExtra("height", size.heightPixels).putExtra("host", address))
        } else if (requestCode == 20) {
            captureCancelled = true; render(Session.state)
        }
    }

    private fun showHost(code: String) {
        val content = page("host"); displayedCode = code
        intro(content, if(code.isEmpty()) R.string.host_live_title else R.string.host_title,
            if(code.isEmpty()) R.string.host_live_body else R.string.host_body)
        if (code.isNotEmpty()) {
            val pairing = card()
            pairing.addView(label(R.string.pairing_card_title, 18, Studio.text, true))
            pairing.addView(label(R.string.pairing_card_body, 13), margins(top = 8))
            val matrix = MultiFormatWriter().encode(code, BarcodeFormat.QR_CODE, 600, 600)
            val pixels = IntArray(600*600) { if(matrix[it%600,it/600]) Color.BLACK else Color.WHITE }
            val bitmap = Bitmap.createBitmap(pixels,600,600,Bitmap.Config.ARGB_8888)
            pairing.addView(ImageView(this).apply {
                setImageBitmap(bitmap); adjustViewBounds = true
                setBackgroundColor(Color.WHITE); setPadding(dp(6),dp(6),dp(6),dp(6))
                contentDescription = getString(R.string.pairing_card_title)
            }, LinearLayout.LayoutParams(dp(218),dp(218)).apply { gravity=Gravity.CENTER_HORIZONTAL; topMargin=dp(18) })
            pairing.addView(label(R.string.pairing_note, 11).apply { gravity=Gravity.CENTER }, margins(top=12))
            pairing.addView(action(R.string.copy_code, "copy") {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name),code))
                toast(R.string.code_copied)
            }, margins(top=18))
            val fullCode = label(code, 11).apply {
                contentDescription=getString(R.string.pairing_code_description); setTextIsSelectable(true)
                typeface=Typeface.MONOSPACE; visibility=View.GONE
                setPadding(dp(12),dp(12),dp(12),dp(12)); background=Studio.shape(this@MainActivity,Studio.background,12)
            }
            val disclose = label(R.string.show_code, 12, Studio.mint, true).apply {
                minHeight=dp(48); gravity=Gravity.CENTER; background=Studio.touch(this@MainActivity,Color.TRANSPARENT,12)
                setOnClickListener {
                    val visible=fullCode.visibility!=View.VISIBLE; fullCode.visibility=if(visible) View.VISIBLE else View.GONE
                    setText(if(visible) R.string.hide_code else R.string.show_code)
                }
            }
            pairing.addView(disclose, margins(top=6)); pairing.addView(fullCode)
            content.addView(pairing, margins(top=22))
        } else {
            val ready = card()
            ready.addView(StudioArtwork(this), LinearLayout.LayoutParams(-1,dp(158)))
            ready.addView(label(R.string.host_live_card_title,18,Studio.text,true),margins(top=8))
            ready.addView(label(R.string.host_live_card_body,14),margins(top=10))
            content.addView(ready,margins(top=22))
        }
        content.addView(action(R.string.open_camera,"camera",primary=code.isEmpty()) {
            startActivity(Intent(this,CameraActivity::class.java))
        },margins(top=18))
        content.addView(action(R.string.stop_sharing,"close",primary=false,danger=true) { Session.stop() },margins(top=10))
    }

    private fun showJoin() {
        val content=page("join")
        intro(content,R.string.join_title,R.string.join_body)
        content.addView(action(R.string.scan_qr,"camera") {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(this,ScanActivity::class.java),30)
        },margins(top=22))
        val form=card()
        form.addView(iconHeading("link",R.string.join_card_title))
        form.addView(label(R.string.join_card_body,13),margins(top=10))
        val input=EditText(this).apply {
            hint=getString(R.string.join_placeholder); setText(joinDraft); setTextColor(Studio.text); setHintTextColor(Studio.muted)
            textSize=14f; typeface=Typeface.MONOSPACE; gravity=Gravity.TOP
            minLines=2; maxLines=4; setPadding(dp(16),dp(16),dp(16),dp(16))
            background=Studio.shape(this@MainActivity,Studio.background,16,Studio.border)
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { joinDraft=s?.toString().orEmpty() }
                override fun afterTextChanged(s:Editable?) {}
            })
        }
        form.addView(input,margins(top=18))
        val error=label(R.string.join_invalid,12,Studio.coral).apply { visibility=View.GONE; accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        form.addView(error,margins(top=10))
        form.addView(action(R.string.paste_code,"copy",primary=false) {
            val clip=getSystemService(ClipboardManager::class.java).primaryClip
            val value=if(clip!=null && clip.itemCount>0) clip.getItemAt(0).coerceToText(this)?.toString().orEmpty() else ""
            if(value.startsWith("mirror1:")) { input.setText(value); error.visibility=View.GONE } else toast(R.string.clipboard_empty)
        },margins(top=12))
        content.addView(form,margins(top=24))
        content.addView(action(R.string.join_action,"arrow",primary=false) {
            val code=input.text.toString().trim()
            if(runCatching { Pairing.parse(code) }.isFailure) {
                error.visibility=View.VISIBLE; input.requestFocus()
            } else {
                error.visibility=View.GONE
                getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken,0)
                Session.join(applicationContext,code)
            }
        },margins(top=20))
        content.addView(note(R.string.pairing_note),margins(top=16))
        content.addView(action(R.string.network_settings,"wifi",primary=false) { openConnectionSettings() },margins(top=18))
    }

    @Suppress("DEPRECATION")
    private fun applyViewerWindow(fullscreen: Boolean) {
        root.fitsSystemWindows = !fullscreen
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (fullscreen) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(!fullscreen)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) hide(WindowInsets.Type.systemBars()) else show(WindowInsets.Type.systemBars())
            }
        } else {
            window.decorView.systemUiVisibility = if (fullscreen) (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                ) else View.SYSTEM_UI_FLAG_VISIBLE
        }
        root.requestApplyInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && currentPage == "viewer") applyViewerWindow(true)
    }

    private fun toggleViewerControls() {
        viewerControlsVisible = !viewerControlsVisible
        viewerHud?.visibility = if (viewerControlsVisible) View.VISIBLE else View.GONE
    }

    private fun showViewer() {
        clear("viewer")
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val screen = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setOnClickListener { toggleViewerControls() }
        }
        body.addView(screen, LinearLayout.LayoutParams(-1, -1))
        video = SurfaceViewRenderer(this).also {
            it.init(Session.eglContext, null)
            it.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            it.setEnableHardwareScaler(true); it.setMirror(mirrored)
            // Keep every edge visible, including when the two phones have different ratios.
            screen.addView(it, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            Session.renderer = it
            it.setOnClickListener { toggleViewerControls() }
            val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent) = true
                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                    focusFromMonitor(event, false); return true
                }
                override fun onLongPress(event: MotionEvent) { focusFromMonitor(event, true) }
                override fun onDoubleTap(event: MotionEvent): Boolean { toggleViewerControls(); return true }
            })
            it.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
            it.contentDescription = getString(R.string.viewer_focus_gestures)
        }
        waitingVideo = column().apply {
            gravity = Gravity.CENTER
            addView(StudioIcon(this@MainActivity, "monitor"), LinearLayout.LayoutParams(dp(42), dp(42)).apply { gravity = Gravity.CENTER })
            addView(label(R.string.waiting_video, 14).apply { gravity = Gravity.CENTER }, margins(top = 14))
        }
        screen.addView(waitingVideo, FrameLayout.LayoutParams(-1, -1))
        val hud = FrameLayout(this).apply {
            visibility = if (viewerControlsVisible) View.VISIBLE else View.GONE
            // Insets protect controls from camera cutouts and gesture areas; video stays edge-to-edge.
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val safe = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(safe.left + dp(12), safe.top + dp(12), safe.right + dp(12), safe.bottom + dp(12))
                } else {
                    val safe = android.graphics.Rect()
                    if (Build.VERSION.SDK_INT >= 28) insets.displayCutout?.let {
                        safe.set(it.safeInsetLeft, it.safeInsetTop, it.safeInsetRight, it.safeInsetBottom)
                    }
                    @Suppress("DEPRECATION")
                    view.setPadding(maxOf(insets.stableInsetLeft, safe.left) + dp(12),
                        maxOf(insets.stableInsetTop, safe.top) + dp(12),
                        maxOf(insets.stableInsetRight, safe.right) + dp(12),
                        maxOf(insets.stableInsetBottom, safe.bottom) + dp(12))
                }
                insets
            }
        }
        viewerHud = hud
        screen.addView(hud, FrameLayout.LayoutParams(-1, -1))
        hud.requestApplyInsets()

        val toolbar = row().apply { gravity = Gravity.CENTER_VERTICAL }
        fun floatingIcon(icon: String, title: Int, onClick: () -> Unit) = iconButton(icon, title, onClick).apply {
            background = Studio.touch(this@MainActivity, 0x99000000.toInt(), 24)
        }
        toolbar.addView(floatingIcon("close", R.string.disconnect) { Session.stop() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        networkMetric = label(R.string.rtt_waiting, 11, Studio.text).apply {
            gravity = Gravity.CENTER; setPadding(dp(12), dp(8), dp(12), dp(8))
            background = Studio.shape(this@MainActivity, 0x99000000.toInt(), 24)
            minHeight = dp(48)
            setOnClickListener { toast(R.string.rtt_help) }
        }
        toolbar.addView(networkMetric, LinearLayout.LayoutParams(-2, dp(48)).apply { marginStart = dp(8) })
        val mirror = floatingIcon("mirror", if (mirrored) R.string.mirror_on else R.string.mirror) {}
        fun updateMirror() {
            mirror.isSelected = mirrored
            mirror.contentDescription = getString(if (mirrored) R.string.mirror_on else R.string.mirror)
            mirror.background = Studio.touch(this, 0x99000000.toInt(), 24, if (mirrored) Studio.mint else null)
        }
        updateMirror()
        mirror.setOnClickListener { mirrored = !mirrored; video?.setMirror(mirrored); updateMirror() }
        toolbar.addView(mirror, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        toolbar.addView(floatingIcon("hide", R.string.viewer_hide_controls) { toggleViewerControls() }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        hud.addView(toolbar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        val dock = column().apply {
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = Studio.shape(this@MainActivity, 0x99000000.toInt(), 24)
        }
        val tools = row().apply { gravity = Gravity.CENTER }
        for ((icon, title, command) in listOf(
            Triple("zoomOut", R.string.zoom_out, "zoomOut"), Triple("zoomIn", R.string.zoom_in, "zoomIn"),
            Triple("torch", R.string.torch, "torch"), Triple("timer", R.string.timer, "timer"), Triple("grid", R.string.grid, "grid"),
            Triple("settings", R.string.remote_settings, "settings")
        )) {
            val button = iconButton(icon, title) {
                if (command == "settings") RemoteCameraSettings.show(this) else Session.controlCamera(command)
            }
            remoteTools += button
            tools.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        dock.addView(tools)
        val shutter = row()
        photoAction = action(R.string.photo, "camera", primary = false) { Session.controlCamera("photo") }
        videoAction = action(R.string.record_stop, "record") { Session.controlCamera("video") }
        shutter.addView(photoAction, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        shutter.addView(videoAction, LinearLayout.LayoutParams(0, -2, 1.4f).apply { marginStart = dp(4) })
        dock.addView(shutter, margins(top = 4))
        controlFeedback = label(R.string.control_hint, 11, Studio.text).apply {
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        dock.addView(controlFeedback, margins(top = 8))
        // In landscape keep the controls to one side instead of covering the entire lower edge.
        hud.addView(dock, FrameLayout.LayoutParams(if (landscape) dp(320) else -1, -2,
            Gravity.BOTTOM or if (landscape) Gravity.END else Gravity.CENTER_HORIZONTAL))
        recordingStatus = label("", 11, Studio.text).apply {
            gravity = Gravity.CENTER; setPadding(dp(10), dp(7), dp(10), dp(7))
            background = Studio.shape(this@MainActivity, 0x99000000.toInt(), 16)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        screen.addView(recordingStatus, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(76) })
        recordingStatus?.setOnApplyWindowInsetsListener { view, insets ->
            val top = if (Build.VERSION.SDK_INT >= 30) insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()).top
                else @Suppress("DEPRECATION") insets.stableInsetTop
            (view.layoutParams as FrameLayout.LayoutParams).apply { topMargin = top + dp(76) }
            view.requestLayout(); insets
        }
        recordingStatus?.requestApplyInsets()
        connectionWarning = label("", 16, Studio.text, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(20), dp(16), dp(20), dp(16))
            background = Studio.shape(this@MainActivity, 0xDD091319.toInt(), 20)
            visibility = View.GONE; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        screen.addView(connectionWarning, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
    }

    private fun focusFromMonitor(event: MotionEvent, lock: Boolean) {
        val state = Session.state
        if (!state.live || !state.camera.available) return
        if (!state.camera.focusSupported) { Toast.makeText(this, "Lensa ini memakai fokus tetap.", Toast.LENGTH_SHORT).show(); return }
        val surface = video ?: return
        val point = FocusCoordinates.fromMonitor(event.x, event.y, surface.width, surface.height, mirrored) ?: return
        Session.controlCamera(if (lock) "focusLock" else "focus", x = point.first, y = point.second, rotation = state.camera.rotation)
        val marker = View(this).apply { background = Studio.shape(this@MainActivity, Color.TRANSPARENT, 10, Studio.mint) }
        val stage = surface.parent as FrameLayout
        stage.addView(marker, FrameLayout.LayoutParams(dp(48), dp(48)).apply {
            leftMargin = (surface.left + event.x - dp(24)).toInt(); topMargin = (surface.top + event.y - dp(24)).toInt()
        })
        marker.postDelayed({ stage.removeView(marker) }, 800)
    }

    private fun intro(content:LinearLayout,title:Int,description:Int) {
        content.addView(label(title,27,Studio.text,true),margins(top=12))
        content.addView(label(description,14),margins(top=12))
    }
    private fun roleCard(tag:Int,title:Int,caption:Int,icon:String,primary:Boolean,onClick:()->Unit):LinearLayout {
        val color=if(primary) Studio.ink else Studio.text
        return row().apply {
            gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),dp(17),dp(16),dp(17)); minimumHeight=dp(112)
            background=Studio.touch(this@MainActivity,if(primary) Studio.mint else Studio.surface,24,if(primary) null else Studio.border)
            val box=FrameLayout(this@MainActivity).apply { background=Studio.shape(this@MainActivity,if(primary) 0x140A2622 else Studio.raised,16) }
            box.addView(StudioIcon(this@MainActivity,icon,color),FrameLayout.LayoutParams(dp(26),dp(26),Gravity.CENTER))
            addView(box,LinearLayout.LayoutParams(dp(48),dp(54)).apply{marginEnd=dp(14)})
            addView(column().apply {
                addView(label(tag,10,if(primary) 0xFF315C51.toInt() else Studio.mint,true).apply{letterSpacing=.05f})
                addView(label(title,20,color,true),margins(top=4))
                addView(label(caption,11,if(primary) 0xFF315C51.toInt() else Studio.muted),margins(top=4))
            },LinearLayout.LayoutParams(0,-2,1f))
            addView(StudioIcon(this@MainActivity,"arrow",color),LinearLayout.LayoutParams(dp(20),dp(20)).apply{marginStart=dp(6)})
            setOnClickListener { onClick() }; isFocusable=true
        }
    }
    private fun utility(title:Int,caption:Int,icon:String,onClick:()->Unit)=row().apply {
        gravity=Gravity.CENTER_VERTICAL; minimumHeight=dp(78); setPadding(dp(12),dp(12),dp(12),dp(12))
        background=Studio.touch(this@MainActivity,Studio.surface,20)
        addView(StudioIcon(this@MainActivity,icon),LinearLayout.LayoutParams(dp(20),dp(20)).apply{marginEnd=dp(10)})
        addView(column().apply { addView(label(title,12,Studio.text,true)); addView(label(caption,10),margins(top=5)) },LinearLayout.LayoutParams(0,-2,1f))
        setOnClickListener{onClick()}; isFocusable=true
    }
    private fun action(title:Int,icon:String,primary:Boolean=true,danger:Boolean=false,onClick:()->Unit)=row().apply {
        gravity=Gravity.CENTER; minimumHeight=dp(56); setPadding(dp(12),dp(12),dp(12),dp(12))
        val color=when{danger->Studio.coral; primary->Studio.ink; else->Studio.text}
        background=Studio.touch(this@MainActivity,if(primary) Studio.mint else Studio.raised,18)
        addView(StudioIcon(this@MainActivity,icon,color),LinearLayout.LayoutParams(dp(19),dp(19)).apply{marginEnd=dp(9)})
        addView(label(title,14,color,true).apply{gravity=Gravity.CENTER})
        setOnClickListener{onClick()}; isFocusable=true
    }
    private fun iconButton(icon:String,description:Int,onClick:()->Unit)=FrameLayout(this).apply {
        contentDescription=getString(description); background=Studio.touch(this@MainActivity,Color.TRANSPARENT,16)
        addView(StudioIcon(this@MainActivity,icon,Studio.muted),FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER))
        setOnClickListener{onClick()}; isFocusable=true
    }
    private fun iconHeading(icon:String,title:Int)=row().apply {
        gravity=Gravity.CENTER_VERTICAL
        addView(StudioIcon(this@MainActivity,icon),LinearLayout.LayoutParams(dp(22),dp(22)).apply{marginEnd=dp(10)})
        addView(label(title,17,Studio.text,true),LinearLayout.LayoutParams(0,-2,1f))
    }
    private fun note(text:Int)=row().apply {
        gravity=Gravity.TOP; setPadding(dp(4),dp(4),dp(4),dp(4))
        addView(StudioIcon(this@MainActivity,"shield",Studio.muted),LinearLayout.LayoutParams(dp(18),dp(18)).apply{marginEnd=dp(9)})
        addView(label(text,12),LinearLayout.LayoutParams(0,-2,1f))
    }
    private fun card()=column().apply { setPadding(dp(20),dp(20),dp(20),dp(20)); background=Studio.shape(this@MainActivity,Studio.surface,24,Studio.border) }
    private fun eyebrow(text:Int)=label(text,10,Studio.mint,true).apply{letterSpacing=.12f}
    private fun label(text:Int,size:Int,color:Int=Studio.muted,bold:Boolean=false)=label(getString(text),size,color,bold)
    private fun label(value:String,size:Int,color:Int=Studio.muted,bold:Boolean=false)=TextView(this).apply {
        text=value; textSize=size.toFloat(); setTextColor(color); includeFontPadding=false
        typeface=Typeface.create(if(bold) "sans-serif-medium" else "sans-serif",Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(),1f)
    }
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    private fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
    private fun margins(top:Int=0)=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(top)}
    private fun dp(value:Int)=(value*resources.displayMetrics.density+.5f).toInt()
    private fun toast(text:Int)=Toast.makeText(this,getString(text),Toast.LENGTH_LONG).show()
    private fun openConnectionSettings()=startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
    private fun help() { AlertDialog.Builder(this).setTitle(R.string.help_title).setMessage(R.string.help_body).setPositiveButton(R.string.understood,null).show() }
    private fun releaseVideo() { Session.renderer=null; video?.release(); video=null }
    // Gestures on API 33+ use OnBackInvokedDispatcher above. Keep the legacy
    // button path too: vivo Android 13 still delivers its Back key here.
    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    @Deprecated("Legacy Android back fallback")
    override fun onBackPressed() { if(currentPage in setOf("setup","join")) showHome() else super.onBackPressed() }
    override fun onConfigurationChanged(newConfig:Configuration) {
        super.onConfigurationChanged(newConfig)
        if(Session.state.mode=="host") Session.resize(resources.displayMetrics.widthPixels,resources.displayMetrics.heightPixels)
        when(currentPage) { "viewer"->showViewer(); "home"->showHome(); "join"->showJoin(); "setup"->showSenderSetup(); "host"->showHost(Session.state.code) }
        render(Session.state)
    }
}
