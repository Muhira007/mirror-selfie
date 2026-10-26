
const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', 'utf8');

code = code.replace('private var mode="photo"', 'private var mode="photo"\n    private var targetRatio=androidx.camera.core.AspectRatio.RATIO_16_9');
code = code.replace('private lateinit var qualityButton:TextView', 'private lateinit var qualityButton:TextView\n    private lateinit var ratioButton:TextView');

const oldControls = 'controls.addView(qualityButton,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})';
const newControls = `val settingsRow=LinearLayout(this)
        ratioButton=cameraButton(R.string.aspect_ratio) {
            targetRatio = if(targetRatio == androidx.camera.core.AspectRatio.RATIO_16_9) androidx.camera.core.AspectRatio.RATIO_4_3 else androidx.camera.core.AspectRatio.RATIO_16_9
            bindCamera()
        }
        settingsRow.addView(ratioButton,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(5)})
        settingsRow.addView(qualityButton,LinearLayout.LayoutParams(0,-2,1.5f).apply{marginStart=dp(5)})
        controls.addView(settingsRow,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(8)})`;
code = code.replace(oldControls, newControls);

const oldPreview = 'val previewUseCase=Preview.Builder().setTargetAspectRatio(if(mode=="photo") androidx.camera.core.AspectRatio.RATIO_4_3 else androidx.camera.core.AspectRatio.RATIO_16_9).setTargetRotation(rotation()).build().also{it.setSurfaceProvider(preview.surfaceProvider)}';
const newPreview = `val ratio = if(mode=="video") androidx.camera.core.AspectRatio.RATIO_16_9 else targetRatio
            val previewUseCase=Preview.Builder().setTargetAspectRatio(ratio).setTargetRotation(rotation()).build().also{it.setSurfaceProvider(preview.surfaceProvider)}`;
code = code.replace(oldPreview, newPreview);

code = code.replace('ImageCapture.Builder().setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)', 'ImageCapture.Builder().setTargetAspectRatio(ratio)');

const oldQuality = 'qualityButton.isEnabled=recording==null && !busy && videoModes.isNotEmpty()';
const newQuality = `ratioButton.isEnabled=recording==null && !busy && mode=="photo"
            ratioButton.text=if(targetRatio==androidx.camera.core.AspectRatio.RATIO_16_9) "9:16" else "3:4"
            qualityButton.isEnabled=recording==null && !busy && videoModes.isNotEmpty()`;
code = code.replace(oldQuality, newQuality);

fs.writeFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', code, 'utf8');

