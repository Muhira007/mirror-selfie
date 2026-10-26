
const fs = require('fs');

// 1. Update CameraControl.kt
let cc = fs.readFileSync('app/src/main/java/id/mirrorselfie/CameraControl.kt', 'utf8');
cc = cc.replace(
  'data class CameraSnapshot(val available: Boolean = false, val recording: Boolean = false, val busy: Boolean = false) {',
  'data class CameraSnapshot(val available: Boolean = false, val recording: Boolean = false, val busy: Boolean = false, val zoom: Float = 1.0f, val maxZoom: Float = 5.0f, val torch: Boolean = false) {'
);
cc = cc.replace(
  'if (action != "photo" && action != "video") return "Tombol ini belum didukung."',
  '// Supported actions: photo, video, zoomIn, zoomOut, torch, timer\n        if (action !in setOf("photo", "video", "zoomIn", "zoomOut", "torch", "grid")) return "Tombol ini belum didukung."'
);
fs.writeFileSync('app/src/main/java/id/mirrorselfie/CameraControl.kt', cc, 'utf8');
console.log('CameraControl updated');

