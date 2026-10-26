
const fs = require('fs');
let code = fs.readFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', 'utf8');

code = code.replace(
  'VideoCapture.Builder(recorder).apply { if(selected.fps>0) setTargetFrameRate(android.util.Range(selected.fps,selected.fps)) }.build()',
  'VideoCapture.Builder(recorder).apply { setVideoStabilizationEnabled(true); if(selected.fps>0) setTargetFrameRate(android.util.Range(selected.fps,selected.fps)) }.build()'
);

fs.writeFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', code, 'utf8');

