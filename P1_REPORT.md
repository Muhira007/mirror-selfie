# Laporan uji kelayakan P1

Tanggal: 17 Oktober 2026. Status: **P1 lulus untuk pasangan perangkat pengguna**. Ini bukan hasil uji lintas perangkat atau rilis MVP lengkap.

## Perangkat dan artefak

| Peran | Perangkat | OS | ABI APK |
| --- | --- | --- | --- |
| Perekam/pengirim | realme RMX3393 | Android 14 / API 34 | arm64-v8a |
| Monitor | vivo V2131 | Android 13 / API 33 | arm64-v8a |

APK debug terbaru: `app/build/outputs/apk/debug/app-debug.apk`, versi `0.2.0-studio`, paket `id.mirrorselfie`. APK yang sama dipasang melalui ADB pada kedua HP. APK memakai debug signing untuk pengujian pribadi, belum rilis publik. Pengujian kelayakan P1 pada tabel di bawah awalnya memakai versi `0.1.0-p1`; verifikasi UI terbaru dicatat di [UI_REPORT.md](UI_REPORT.md).

SHA-256 APK terbaru (UI Studio): `da9a4a7e01972c22c650e7e4ca0724a809eb4d379397f9ccece6b159e4817326`.

Pembaruan visual 17 Oktober 2026: ikon launcher kamera/dua layar dipasang melalui update APK pada kedua HP. Adaptive foreground transparan, background navy, serta PNG biasa/bulat tersedia. `assembleDebug` dan `lintDebug` dijalankan ulang: lulus, 0 error dan 28 warning. Pengujian streaming di bawah berasal dari pengujian P1 sebelum perubahan ikon; alur kamera tidak diubah. Sumber, pratinjau, dan prompt: [assets/app-icon/README.md](assets/app-icon/README.md).

## Hasil pengujian

| Skenario | Hasil / bukti |
| --- | --- |
| Capture layar realme ketika kamera bawaan terbuka | Preview berhasil terlihat di vivo; tidak hitam pada perangkat ini |
| Wi-Fi yang sama | Video langsung melalui WebRTC, tanpa STUN/TURN/cloud |
| Sampel kualitas Wi-Fi | VP8, frame 576 x 1280, sekitar 24 fps pada statistik yang diperiksa |
| Rekaman sambil streaming | File uji 32,834 detik dan 95,151 detik tersimpan di realme |
| Tombol remote Video | Rekam/Stop dari vivo menghasilkan file 12,644 detik |
| Hotspot offline | realme menjadi SoftAP, vivo tersambung langsung; Wi-Fi router realme dan data seluler kedua HP mati; preview serta kontrol tetap berjalan |
| Foto melalui hotspot | Foto di realme 3072 x 4096, 3.071.612 byte, setelah tombol Foto di vivo |
| Video melalui hotspot | Klip 15,002 detik, 38.184.911 byte, tersimpan di realme |
| Mode Film pengguna | Mode Film tetap dipakai; rekam dan stop dari vivo menghasilkan klip 13,101 detik, 33.166.204 byte |
| Framing monitor | Pemeriksaan visual menunjukkan seluruh layar sumber dengan letterbox setelah perbaikan viewport |
| Penghentian sesi | Monitor kembali ke halaman awal; `dumpsys media_projection` menunjukkan `null` |

Metadata file dibaca dari MediaStore hanya untuk rentang waktu pengujian. Klip/foto uji tetap berada di galeri realme; tidak diunggah atau dihapus otomatis. Screenshot lokal pengujian berada di `artifacts/` yang diabaikan Git.

## Latensi

RTT jaringan yang teramati pada beberapa sampel Wi-Fi stabil adalah 4–7 ms; sampel awal 25–62 ms. Pada hotspot, sampel termasuk 2–12 ms setelah stabil dan 33 ms pada awal sesi. Sampel ini bukan median/p95 dari benchmark terkontrol.

**Latensi kamera-ke-monitor belum diukur.** RTT hanya perjalanan bolak-balik paket jaringan dan tidak mencakup seluruh proses sensor, preview kamera, capture layar, encode, buffering, decode, dan display. FPS juga bukan ukuran latency. Sasaran median <=250 ms pada blueprint belum dinyatakan tercapai.

## Perbaikan dari pengujian nyata

1. Adapter capture AAR yang diaudit masih membuat ulang virtual display. P1 memakai `ProjectionCapturer` sendiri agar resize memakai display yang sama.
2. TLS RSA Android Keystore gagal pada jalur raw RSA padding Conscrypt. Key sesi diganti EC P-256; sertifikat tetap dipin melalui QR dan token pairing tetap sekali pakai.
3. Renderer match-parent menyebabkan tampilan terpotong. Renderer sekarang wrap-content di viewport agar pengukuran aspect-fit bekerja.
4. Android network monitor libwebrtc mengabaikan interface hotspot offline. Enumerasi native dipakai dan interface seluler/VPN/loopback diabaikan. Hotspot kemudian berhasil.
5. Kamera mode Film memiliki tombol `com.oplus.camera:id/shutter_button` yang sama, dengan deskripsi `Video Recording Button`. Kontrol mencari node, sehingga tidak bergantung posisi tombol shutter.
6. Pembukaan kamera membawa aktivitas kamera yang ada ke depan, tanpa memaksa intent mode Video.

## Pemeriksaan build

- `assembleDebug` lulus.
- Empat unit test lulus: penolakan token salah dan pemakaian sekali; batas kedaluwarsa; pairing serentak hanya satu pemenang; dimensi frame mempertahankan rasio dan bounding box.
- `lintDebug` lulus tanpa error; warning nonfatal masih ada, termasuk API/dependency yang lebih baru dan cakupan APK ARM64.
- ELF library ARM64 memiliki LOAD alignment `0x4000`; APK lolos `zipalign -P 16` dan verifikasi signature pada pemeriksaan.
- Runtime perangkat page-size 16 KB belum diuji; hasil alignment bukan pengganti uji runtime.

## Pekerjaan berikutnya

- Scanner QR dalam aplikasi agar pairing mandiri tanpa USB mudah digunakan; P1 menyediakan input kode lengkap dan helper USB.
- Reconnect otomatis dan kredensial resume; P1 mengharuskan sesi/kode baru setelah putus.
- Pengujian penolakan/pencabutan izin, rotasi dan mode kamera lain secara sistematis, thermal/daya 20 menit, serta audit cleanup memori.
- Benchmark latency end-to-end menggunakan metode pada blueprint.
- Merapikan UI MVP, preferensi kualitas, dan validasi target SDK terbaru sebelum distribusi umum.

Petunjuk penggunaan: [README.md](README.md). Tracker: [PROGRESS.md](PROGRESS.md).
