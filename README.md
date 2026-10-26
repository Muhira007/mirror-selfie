# Mirror Selfie

Versi pengembangan terbaru: **0.4.0**. Pengirim dan monitor dapat dipilih pada kedua HP. Seluruh layar pengirim dibagikan melalui Wi-Fi atau hotspot lokal. Kontrol kamera terbaru menggunakan kamera di dalam Mirror Selfie dan tidak memerlukan Aksesibilitas.

## Penggunaan versi 0.4

1. Pasang APK yang sama pada kedua HP, lalu sambungkan ke Wi-Fi atau hotspot yang sama. Internet tidak diperlukan.
2. Di pengirim pilih **Kirim layar → Mulai berbagi**, lalu setujui dialog Android.
3. Di monitor pilih **Lihat layar → Pindai QR**. Alternatifnya, tempel kode koneksi lengkap. QR berlaku dua menit dan hanya dipakai untuk pemasangan awal.
4. Di pengirim pilih **Buka kamera**. Foto/video tersimpan di pengirim melalui MediaStore, di `Pictures/MirrorSelfie` dan `Movies/MirrorSelfie`.
5. Di monitor gunakan Foto/Rekam, atau roda gigi untuk pengaturan kamera. Putuskan sesi melalui tombol silang atau notifikasi pengirim.

- Ketuk gambar untuk fokus; tahan untuk kunci fokus. Kunci pencahayaan ikut diterapkan bila didukung dan ISO/shutter Auto. Kamera mengonfirmasi hasil fokus.
- Ketuk gambar **dua kali** untuk tampil/sembunyikan kontrol. Ruang hitam dan tombol mata dicoret juga bisa menyembunyikannya. Aspect-fit menjaga seluruh gambar terlihat.
- Roda gigi membuka mode Foto/Video, kombinasi resolusi/FPS, ISO, shutter, WB Kelvin, lepas kunci fokus, dan kunci pencahayaan. Pilihan mengikuti kemampuan lensa; pengaturan mode/format/manual diblokir selama rekaman atau pemrosesan.
- ISO dan shutter manual dipakai berpasangan. Saat baru satu dipilih, nilai pasangannya mengikuti pengukuran kamera terdekat yang didukung. Memilih Auto pada salah satunya mengembalikan keduanya ke otomatis.
- WB menerima angka 2000–8000 K atau Auto bila tersedia. Konversi Kelvin ke gain masih pendekatan, belum dikalibrasi warna per sensor.
- Status REC dan durasi berasal dari event recorder pengirim. Baterai kedua HP serta ruang kosong pengirim tetap terlihat saat panel kontrol disembunyikan.
- Saat transport terputus, monitor mencoba menyambung ulang hingga dua menit. Sesi, PeerConnection, dan satu izin MediaProjection dipertahankan. Perintah kamera lama tidak dikirim ulang.
- Jika gambar tidak diperbarui selama sekitar 2,5 detik, monitor meredupkan gambar dan menunggu pembaruan sebelum mengaktifkan kontrol. Layar sumber yang benar-benar statis juga dapat memunculkan penanda ini.
- Sambung ulang membutuhkan proses/sesi pengirim tetap hidup dan alamat IP yang sama. Setelah sesi dihentikan, proses mati, atau alamat berubah, mulai sesi dan pindai QR baru.

Progres dan batas pengujian terbaru: [PROGRESS.md](PROGRESS.md). APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Catatan historis prototipe 0.2

Bagian berikut merekam pengujian lama memakai kamera OEM. Nama tombol dan metode Aksesibilitas di bagian ini tidak berlaku pada kamera internal versi terbaru.

Kamera belakang realme RMX3393 menjadi sumber gambar; vivo V2131 menjadi monitor. Media dikirim langsung lewat jaringan lokal menggunakan WebRTC, tanpa login, server cloud, STUN, atau TURN.

Versi terbaru: **0.2.0-studio** (versionCode 2). Halaman utama, pengirim, pairing, dan monitor telah didesain ulang dengan kartu peran, ilustrasi native, tema gelap/mint, serta panel monitor portrait/landscape. [Pratinjau halaman utama](assets/ui-preview/home.png) · [Form koneksi](assets/ui-preview/connect.png) · [Laporan perubahan UI](UI_REPORT.md).

Sumber yang dibagikan adalah **seluruh layar HP pengirim**: halaman utama, kamera, dan aplikasi lain yang mengizinkan capture dapat terlihat di monitor. Kontrol jarak jauh tetap terbatas pada tombol kamera realme. Konten yang diproteksi aplikasi/Android mungkin hitam.

Pilihan pengirim dan monitor tersedia di kedua HP, sehingga peran dapat dipilih terbalik: vivo **Jadikan kamera**, realme **Jadikan monitor**. Label nama perangkat pada UI mengikuti susunan awal. Susunan terbalik belum diuji langsung; kompatibilitas capture kamera vivo belum dikonfirmasi. Jika vivo menjadi pengirim, buka Kamera vivo secara manual: pintasan Buka kamera dan layanan Foto/Rekam-Stop saat ini khusus kamera realme.

## Menjalankan prototipe

1. Pasang APK yang sama pada kedua HP.
2. Hubungkan ke Wi-Fi yang sama. Di luar rumah, aktifkan hotspot pribadi realme lalu sambungkan vivo ke hotspot itu. Hotspot tanpa internet telah diuji pada kedua HP ini dengan data seluler mati.
3. Di realme, pilih **Jadikan kamera** → pilih alamat jaringan lokal → **Mulai pengirim** → setujui dialog Android.
4. Pindahkan **kode koneksi lengkap** ke vivo melalui **Salin kode koneksi** di pengirim. Di vivo, pilih **Jadikan monitor**, masukkan kode atau gunakan **Tempel kode**, lalu **Hubungkan monitor**. Kode sekali pakai dan berlaku dua menit. QR sudah ditampilkan, tetapi scanner dalam aplikasi belum termasuk P1.
5. Setelah terhubung, buka kamera bawaan realme. Tombol aplikasi membawa kembali kamera tanpa memaksa mode Video, sehingga mode Film yang sudah dipilih dapat dipertahankan. Foto/video asli disimpan oleh kamera realme; gambar pada vivo hanya untuk framing.
6. Tekan **Putuskan** pada vivo atau **Berhenti** di notifikasi realme untuk mengakhiri sesi. Untuk pairing ulang, buat sesi dan kode baru.

Kedua layar harus terbuka. Pada jaringan tanpa internet, pilih tetap terhubung bila Android menanyakan. Saat memakai hotspot, pilih alamat interface hotspot yang dapat dijangkau vivo, bukan alamat Wi-Fi router yang berbeda.

## Kontrol kamera dari vivo

Kontrol ini opsional dan khusus paket kamera `com.oplus.camera` pada realme yang diuji.

1. Di realme buka **Aktifkan kontrol kamera**, atau Pengaturan → Aksesibilitas → Aplikasi yang didownload.
2. Aktifkan **Mirror Selfie — kontrol kamera** melalui dialog Android. Aplikasi tidak mengaktifkan izin ini sendiri.
3. Setelah pairing dan kamera terbuka, gunakan **Foto** atau **Rekam/Stop** pada vivo.
4. Jika mode perlu berganti, penekanan pertama membuka mode yang diminta. Baca konfirmasi, lalu tekan sekali lagi untuk mengambil foto atau mulai merekam. Tombol Foto menolak peralihan saat video sedang direkam.

Kontrol hanya berjalan dalam sesi pengirim aktif, dari monitor terautentikasi, saat kamera realme di foreground. Fitur ini tidak menawarkan tap/swipe umum atau kendali aplikasi lain. Aksesibilitas dapat dimatikan kembali melalui Pengaturan Android.

Rekam/Stop juga telah diuji dalam **mode Film** pilihan pengguna. Tombol Foto digunakan dalam mode FOTO, bukan untuk memotret di mode Film. Perpindahan FOTO/VIDEO menggunakan tap terbatas pada carousel realme RMX3393 dalam portrait; model, layout kamera, dan orientasi lain belum dijamin. Menutup paksa aplikasi melalui pengaturan/ADB dapat menonaktifkan layanan aksesibilitas sehingga perlu diaktifkan kembali.

## Indikator kecepatan

Monitor menggunakan tampilan penuh tanpa header atau bingkai. Ketuk gambar atau area kosong untuk menyembunyikan/menampilkan kontrol; tombol mata dicoret juga menyembunyikannya. Foto/Rekam dan alat kamera berada di panel mengambang. Dalam landscape, panel berada di kanan bawah. Seluruh gambar pengirim tetap terlihat; ruang hitam dapat muncul jika rasio kedua layar berbeda.

Frame rate menunjukkan kelancaran video. **RTT jaringan** yang tampil adalah waktu bolak-balik koneksi WebRTC, bukan latensi kamera-ke-monitor. Latensi end-to-end belum boleh disimpulkan dari RTT atau FPS; metode pengukuran terpisah ada di [TECHSTACK.md](TECHSTACK.md).

## Build

Toolchain yang digunakan: JDK 17, Gradle 9.6.0, AGP 9.4.0 dengan Kotlin bawaan 2.2.10, Android SDK 36/build tools 36.0.0. `minSdk=26`, `targetSdk=36`; APK spike dibatasi ARM64 untuk kedua perangkat pengguna. Target SDK 37 beserta izin LAN dievaluasi sebelum distribusi umum.

Atur `sdk.dir` pada `local.properties` sesuai lokasi SDK komputer Anda, lalu jalankan di PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

P1 dan UI Studio menggunakan widget Android native, drawable, Canvas, serta JSON bawaan. Perombakan tampilan tidak memerlukan dependency UI baru. Compose masih merupakan opsi pengembangan berikutnya; scanner QR, penyimpanan preferensi, reconnect, dan UX MVP lengkap belum selesai.

## Pengujian perangkat melalui USB

`scripts/device-ui.ps1` menyediakan pembacaan kontrol UI, tap berdasarkan label/resource ID yang terlihat, serta screenshot lokal. Aksi `pair` memindahkan kode sesi realme ke vivo melalui kanal USB pengujian dalam memori. Frame video tetap melalui Wi-Fi, bukan melalui komputer.

Kemudahan intent `joinCode` hanya aktif dalam build debug. Jangan mengunggah dump UI pairing atau screenshot pribadi; folder `artifacts/` sudah diabaikan Git. Skrip mengasumsikan dua serial perangkat pengujian saat ini dan SDK pada lokasi Android default Windows.

Status pekerjaan dan batas pengujian: [PROGRESS.md](PROGRESS.md). Bukti P1: [P1_REPORT.md](P1_REPORT.md).
