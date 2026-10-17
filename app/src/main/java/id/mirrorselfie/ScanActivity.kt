package id.mirrorselfie

import android.app.Activity
import android.content.Intent
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScanActivity:CameraScreen() {
    override val titleRes=R.string.scan_qr
    private val analyzer=Executors.newSingleThreadExecutor()
    private val decoder=QrDecoder()
    private val done=AtomicBoolean(false)
    private var lastDecode=0L

    override fun bindCamera() {
        val p=provider ?: return
        runCatching {
            releaseUseCases()
            val lens=if(p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            val picture=Preview.Builder().build().also{it.setSurfaceProvider(preview.surfaceProvider)}
            val analysis=ImageAnalysis.Builder()
                .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(Size(1280,720),ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(analyzer) { image ->
                try {
                    val now=android.os.SystemClock.elapsedRealtime()
                    if(!screenActive || done.get() || now-lastDecode<250) return@setAnalyzer
                    lastDecode=now
                    val plane=image.planes[0]
                    val buffer=plane.buffer
                    val bytes=ByteArray(image.width*image.height)
                    for(y in 0 until image.height) for(x in 0 until image.width)
                        bytes[y*image.width+x]=buffer.get(y*plane.rowStride+x*plane.pixelStride)
                    val code=decoder.decode(bytes,image.width,image.height) ?: return@setAnalyzer
                    if(runCatching { Pairing.parse(code) }.isFailure) {
                        runOnUiThread { if(screenActive) feedback.setText(R.string.scan_invalid) }
                    } else if(done.compareAndSet(false,true)) runOnUiThread {
                        if(!isFinishing && screenActive) { setResult(Activity.RESULT_OK,Intent().putExtra("code",code)); finish() }
                    }
                } finally { image.close() }
            }
            ownedUseCases.add(picture); ownedUseCases.add(analysis)
            p.bindToLifecycle(this,lens,picture,analysis)
            feedback.setText(R.string.scan_hint)
        }.onFailure { showCameraError() }
    }
    override fun onDestroy() { super.onDestroy(); analyzer.shutdown() }
}
