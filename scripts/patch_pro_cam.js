
const fs = require('fs');

let ca = fs.readFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', 'utf8');

// Add imports
ca = ca.replace('import androidx.camera.core.*', 'import androidx.camera.core.*\nimport androidx.camera.camera2.interop.Camera2CameraControl\nimport androidx.camera.camera2.interop.CaptureRequestOptions\nimport android.hardware.camera2.CaptureRequest');

// Add PRO parameters in CameraActivity
const paramTarget = 'private var countdownRunning = false';
const proParams = `private var countdownRunning = false
    private var proPanelVisible = false
    private var proIso = 0 // 0 = Auto
    private var proShutterMs = 0.0 // 0.0 = Auto
    private var proKelvin = 0 // 0 = Auto
    private lateinit var proPanel: LinearLayout
    private lateinit var isoButton: TextView
    private lateinit var shutterButton: TextView
    private lateinit var wbButton: TextView`;
ca = ca.replace(paramTarget, proParams);

// Add PRO button in TopBar
const topBarSearch = 'topBar.addView(ratioButton, LinearLayout.LayoutParams(-2, dp(38)))';
const topBarReplace = `topBar.addView(ratioButton, LinearLayout.LayoutParams(-2, dp(38)).apply { marginEnd = dp(6) })
        val proToggle = pillBtn("PRO") {
            proPanelVisible = !proPanelVisible
            proPanel.visibility = if (proPanelVisible) View.VISIBLE else View.GONE
        }
        topBar.addView(proToggle, LinearLayout.LayoutParams(-2, dp(38)))`;
ca = ca.replace(topBarSearch, topBarReplace);

// Add PRO control panel floating above bottom bar
const bottomBarTarget = 'val bottomParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)';
const proPanelLayout = `// PRO controls panel (ISO, Shutter, Kelvin)
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

        val bottomParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)`;
ca = ca.replace(bottomBarTarget, proPanelLayout);

// Apply Camera2 options dynamically
const proMethods = `
    private fun applyProSettings() {
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

        control.setCaptureRequestOptions(builder.build())
    }

    private fun showIsoDialog() {
        val isos = listOf("Auto", "100", "200", "400", "800", "1600", "3200", "6400")
        AlertDialog.Builder(this).setTitle("ISO")
            .setItems(isos.toTypedArray()) { _, which ->
                proIso = if (which == 0) 0 else isos[which].toInt()
                isoButton.text = if (proIso == 0) "ISO: Auto" else "ISO %d".format(proIso)
                applyProSettings()
            }.show()
    }

    private fun showShutterDialog() {
        val names = listOf("Auto", "1/1000s", "1/500s", "1/250s", "1/125s", "1/60s", "1/30s", "1/15s", "1/4s", "1s")
        val values = listOf(0.0, 1.0, 2.0, 4.0, 8.0, 16.6, 33.3, 66.6, 250.0, 1000.0)
        AlertDialog.Builder(this).setTitle("Shutter Speed")
            .setItems(names.toTypedArray()) { _, which ->
                proShutterMs = values[which]
                shutterButton.text = if (proShutterMs == 0.0) "S: Auto" else names[which]
                applyProSettings()
            }.show()
    }

    private fun showWbDialog() {
        val kelvins = listOf("Auto", "2800K (Lampu Kuning)", "3200K (Tungsten)", "4000K (Fluorescent)", "5000K (Siang Netral)", "5600K (Matahari Terik)", "6500K (Berawan)", "7500K (Teduh/Shade)")
        val values = listOf(0, 2800, 3200, 4000, 5000, 5600, 6500, 7500)
        AlertDialog.Builder(this).setTitle("White Balance (Kelvin)")
            .setItems(kelvins.toTypedArray()) { _, which ->
                proKelvin = values[which]
                wbButton.text = if (proKelvin == 0) "WB: Auto" else "%dK".format(proKelvin)
                applyProSettings()
            }.show()
    }
`;

ca = ca.replace('private fun result(text: Int)', proMethods + '\n    private fun result(text: Int)');

// In bindCamera, after camera is bound, apply pro settings
ca = ca.replace(
  'camera?.cameraControl?.enableTorch(torchEnabled)',
  'camera?.cameraControl?.enableTorch(torchEnabled)\n            applyProSettings()'
);

fs.writeFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', ca, 'utf8');
console.log('CameraActivity PRO patched');

