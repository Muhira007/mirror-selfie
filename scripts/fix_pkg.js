
const fs = require('fs');
let ca = fs.readFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', 'utf8');

// Remove wrongly prepended import
ca = ca.replace('import android.app.AlertDialog\npackage id.mirrorselfie', 'package id.mirrorselfie\n\nimport android.app.AlertDialog');

fs.writeFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', ca, 'utf8');
console.log('Fixed package declaration order');

