# Mirror Selfie — pembaruan UI Studio

Tanggal: 17 Oktober 2026. Versi: **0.2.0-studio**, versionCode **2**. APK debug ARM64 diperbarui melalui ADB pada realme RMX3393 (Android 14) dan vivo V2131 (Android 13).

## Tampilan dan alur

- Tema navy/mint selaras dengan ikon aplikasi, tipografi yang lebih jelas, latar gradasi, kartu membulat, ripple, serta transisi masuk singkat. Transisi mengikuti pengaturan animator Android.
- Halaman utama memiliki ilustrasi dua HP yang digambar native, kartu **Jadikan kamera** / **Jadikan monitor**, pintasan koneksi dan kontrol kamera, serta panduan penggunaan.
- Setup pengirim menampilkan pilihan jaringan dan instruksi berbagi layar. Halaman pairing menempatkan QR dalam kartu putih, menyediakan salin kode, dan menyembunyikan kode lengkap sampai diminta.
- Form monitor menyediakan tempel kode, validasi kode sebelum koneksi, pesan kesalahan di dekat input, dan area yang dapat digulir saat keyboard terbuka.
- Monitor mempertahankan aspect-fit seluruh layar sumber. Foto, Rekam/Stop, Cermin, dan Putuskan berada dalam panel terpisah; panel berpindah ke kanan dalam landscape. Cermin aktif ditandai teks dan border mint.
- RTT tetap diberi label **RTT jaringan**, dengan penjelasan saat disentuh. Tombol rekam tetap merupakan perintah shutter, bukan indikator bahwa kamera pasti sedang merekam.
- Nama launcher menjadi **Mirror Selfie**. Ikon, izin berbagi layar, pairing, dan layanan kontrol kamera tetap digunakan.

[Pratinjau halaman utama](assets/ui-preview/home.png) · [Pratinjau koneksi](assets/ui-preview/connect.png).

## Implementasi

UI memakai Kotlin dan widget Android native pada stack yang ada: `MainActivity.kt`, `StudioViews.kt`, resource string, dan tema aplikasi. Ilustrasi serta line icon memakai Canvas dengan objek gambar yang digunakan ulang. Tidak ada dependency UI tambahan atau permintaan gambar melalui jaringan.

Back gesture API 33+ memakai `OnBackInvokedDispatcher`; jalur tombol Back lama tetap tersedia karena vivo Android 13 masih mengirimkannya ke `onBackPressed`. Callback aktif pada halaman formulir. Acuan: [dokumentasi Back Android](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture).

## Verifikasi

| Pemeriksaan | Hasil |
| --- | --- |
| Build | `assembleDebug` lulus |
| Unit test yang sudah ada | 4 test, 0 gagal, 0 error |
| Lint | 0 error, 21 warning nonfatal |
| APK | Signature terverifikasi; `zipalign -c -P 16 4` lulus |
| Instalasi | Update tanpa uninstall berhasil di kedua HP; layanan aksesibilitas realme tetap aktif |
| Halaman utama / setup / koneksi | Diperiksa langsung lewat screenshot kedua ukuran layar; konten panjang dapat digulir |
| Kode kosong | Ditolak dengan pesan inline; tidak memulai sesi |
| Back | Setup realme dan form vivo kembali ke halaman utama; fallback tombol vivo diperbaiki berdasarkan pengujian |
| Pairing / preview | Pairing memakai helper USB pengujian; gambar tetap dikirim melalui hotspot lokal. Kamera realme dan halaman aplikasi terlihat di vivo |
| Cermin / rotasi | Cermin on/off bekerja; keadaan cermin dipertahankan saat monitor diputar. Label landscape pada revisi akhir tampil utuh |
| Tombol Foto / feedback | Saat aplikasi pengirim berada di depan, perintah dari UI monitor menerima respons layanan: buka kamera realme terlebih dahulu. Tidak menghasilkan file foto baru |
| Penghentian | Putuskan kembali ke halaman awal; `dumpsys media_projection` realme menunjukkan `null`. Pengaturan rotasi vivo dipulihkan |

Screenshot pengujian tambahan berada di `artifacts/` dan diabaikan Git. Tidak ada rekaman/foto baru yang sengaja dibuat dalam pengujian UI ini. Bukti shutter nyata Video/Film dan foto pada versi P1 tetap ada di [P1_REPORT.md](P1_REPORT.md); shutter nyata tidak diuji ulang pada revisi visual ini.

APK: `app/build/outputs/apk/debug/app-debug.apk`, 13.747.670 byte.

SHA-256: `da9a4a7e01972c22c650e7e4ca0724a809eb4d379397f9ccece6b159e4817326`.

## Batas pekerjaan

Perubahan ini menyelesaikan perombakan tampilan, bukan seluruh MVP. Pemindai QR dalam aplikasi, reconnect otomatis, pilihan kualitas, benchmark latency end-to-end, serta pengujian durasi panjang tetap pekerjaan lanjutan. Jalur MediaProjection membagikan seluruh layar pengirim; beberapa aplikasi dapat melindungi kontennya dari capture. Kontrol monitor tetap dibatasi pada Foto dan Rekam/Stop kamera realme, tanpa kendali sentuh umum.

Kedua pilihan peran tersedia di kedua HP, namun susunan vivo sebagai pengirim dan realme sebagai monitor belum diuji. Capture kamera vivo belum dikonfirmasi; pintasan pembuka kamera dan layanan kontrol shutter masih khusus realme. Nama perangkat pada kartu UI mengikuti susunan awal pengguna.
