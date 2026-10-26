
const fs = require('fs');
let xml = fs.readFileSync('app/src/main/AndroidManifest.xml', 'utf8');
xml = xml.replace('android:name=".CameraActivity" android:exported="false"', 'android:name=".CameraActivity" android:exported="true"');
xml = xml.replace('android:name=".ScanActivity" android:exported="false"', 'android:name=".ScanActivity" android:exported="true"');
fs.writeFileSync('app/src/main/AndroidManifest.xml', xml, 'utf8');

