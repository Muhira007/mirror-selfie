
const fs = require('fs');
let ca = fs.readFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', 'utf8');

ca = ca.replace(
  'private fun applyProSettings() {',
  '@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)\n    private fun applyProSettings() {'
);

fs.writeFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', ca, 'utf8');
console.log('Added OptIn for Camera2Interop');

