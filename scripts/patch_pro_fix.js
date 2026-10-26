
const fs = require('fs');
let ca = fs.readFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', 'utf8');

// Ensure android.app.AlertDialog is imported
if (!ca.includes('import android.app.AlertDialog')) {
    ca = 'import android.app.AlertDialog\n' + ca;
}

// Fix lambda parameter types for setItems
ca = ca.replace(
    '.setItems(isos.toTypedArray()) { _, which ->',
    '.setItems(isos.toTypedArray()) { _, which: Int ->'
);
ca = ca.replace(
    '.setItems(names.toTypedArray()) { _, which ->',
    '.setItems(names.toTypedArray()) { _, which: Int ->'
);
ca = ca.replace(
    '.setItems(kelvins.toTypedArray()) { _, which ->',
    '.setItems(kelvins.toTypedArray()) { _, which: Int ->'
);

fs.writeFileSync('app/src/main/java/id/mirrorselfie/CameraActivity.kt', ca, 'utf8');
console.log('Fixed dialog types');

