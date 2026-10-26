
const fs = require('fs');
let s = fs.readFileSync('app/src/main/java/id/mirrorselfie/Session.kt', 'utf8');

// In onMessage for viewer receiving cameraState:
s = s.replace(
  'cameraSnapshot=CameraSnapshot(json.optBoolean("available"),json.optBoolean("recording"),json.optBoolean("busy"))',
  'cameraSnapshot=CameraSnapshot(json.optBoolean("available"),json.optBoolean("recording"),json.optBoolean("busy"),json.optDouble("zoom",1.0).toFloat(),json.optDouble("maxZoom",5.0).toFloat(),json.optBoolean("torch",false))'
);

// In sendCameraState:
s = s.replace(
  'send(JSONObject().put("type","cameraState").put("available",cameraSnapshot.available).put("recording",cameraSnapshot.recording).put("busy",cameraSnapshot.busy).put("message",controlText))',
  'send(JSONObject().put("type","cameraState").put("available",cameraSnapshot.available).put("recording",cameraSnapshot.recording).put("busy",cameraSnapshot.busy).put("zoom",cameraSnapshot.zoom.toDouble()).put("maxZoom",cameraSnapshot.maxZoom.toDouble()).put("torch",cameraSnapshot.torch).put("message",controlText))'
);

// In controlCamera:
s = s.replace(
  'if(action!="photo" && action!="video") return@dispatch',
  'if(action !in setOf("photo", "video", "zoomIn", "zoomOut", "torch", "grid")) return@dispatch'
);

fs.writeFileSync('app/src/main/java/id/mirrorselfie/Session.kt', s, 'utf8');
console.log('Session.kt patched');

