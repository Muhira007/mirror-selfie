# Mirror Selfie — Progress

Terakhir diperbarui: 17 Oktober 2026  
Tahap: P1 selesai — uji kelayakan perangkat dan kontrol kamera terbatas; MVP lengkap masih memiliki pekerjaan lanjutan.  
Implementasi aplikasi: UI Studio 0.2.0 dibuat; APK debug terbaru terpasang pada kedua HP.  
Dokumen utama: [TECHSTACK.md](TECHSTACK.md).

## 1. Permintaan pengguna dan batas pekerjaan

- HP 1 memakai kamera belakang; HP 2 menampilkan layar untuk membantu framing.
- Pengguna juga ingin membuat video di luar rumah tanpa router Wi-Fi; jalur yang direncanakan adalah hotspot antarkedua HP tanpa internet.
- Pengguna memilih realme RMX3393 sebagai perekam/pengirim (HP 1); vivo V2131 menjadi monitor (HP 2).
- Pilih metode koneksi yang paling sesuai dan mudah digunakan.
- Tulis tech stack/blueprint dan progres dalam Markdown.
- Instruksi awal: blueprint dahulu. Pengguna kemudian menginstruksikan “kerjakan”, sehingga P1 mulai diimplementasikan.
- Pengguna meminta kontrol kamera dari monitor dan memilih tombol rekam/stop serta foto; bukan kendali sentuh penuh.

## 2. Pekerjaan yang telah selesai

- [x] Memeriksa folder proyek; folder kosong ketika pemeriksaan awal dilakukan.
- [x] Memeriksa instruksi AGENTS.md pada workspace dan direktori induk yang relevan; tidak ditemukan.
- [x] Mengidentifikasi perbedaan screen mirroring kamera bawaan dan kamera internal aplikasi.
- [x] Membandingkan Wi-Fi lokal, hotspot, Wi-Fi Direct, Bluetooth, internet, dan USB untuk kebutuhan ini.
- [x] Memilih rekomendasi awal Kotlin + MediaProjection + WebRTC melalui Wi-Fi lokal/hotspot.
- [x] Menentukan satu aplikasi dengan peran pengirim dan monitor.
- [x] Menulis alur pairing, arsitektur, lifecycle, izin, dan keamanan sesi.
- [x] Menuliskan batas shutter jarak jauh, kualitas monitor, dan lokasi file foto asli.
- [x] Menetapkan sasaran performa dan cara pengukuran sebagai target, bukan hasil uji.
- [x] Mencatat risiko preview kamera OEM, hotspot, codec, rotasi, dan binary native.
- [x] Menautkan dokumentasi resmi Android/WebRTC serta repository maintainer.
- [x] Membuat TECHSTACK.md dan PROGRESS.md.
- [x] Memeriksa konsistensi scope, status, dan struktur Markdown; workspace hanya berisi dua file Markdown.
- [x] Mendeteksi dua HP USB dan membaca properti keduanya melalui ADB: realme RMX3393 Android 14/API 34 dan vivo V2131 Android 13/API 33; keduanya mendukung arm64-v8a. Status unauthorized awal pada vivo telah terselesaikan.
- [x] Menetapkan peran perangkat sesuai jawaban pengguna: realme sebagai perekam/pengirim dan vivo sebagai monitor.
- [x] Membuat project Android Kotlin, Gradle wrapper, pipeline capture, WebRTC, dan signaling WSS lokal.
- [x] Build APK debug ARM64, empat unit test, dan lint lulus pada revisi terakhir yang diperiksa.
- [x] Memasang aplikasi pada kedua HP melalui ADB.
- [x] Menguji preview kamera bawaan realme tampil di vivo melalui Wi-Fi yang sama.
- [x] Menguji perekaman video realme bersamaan dengan streaming; memeriksa metadata file uji yang dihasilkan.
- [x] Memperbaiki capture Android modern menggunakan adapter sendiri, TLS keystore EC, dan aspect-fit monitor.
- [x] Menambahkan RTT jaringan yang diberi label terpisah dari latensi kamera.
- [x] Pengguna mengaktifkan AccessibilityService kamera realme; tombol Rekam/Stop dari vivo berhasil diuji.
- [x] Menguji foto jarak jauh: file 3072 x 4096 tersimpan di realme.
- [x] Menguji hotspot tanpa router/data seluler; video dan kontrol tetap bekerja.
- [x] Menguji remote Rekam/Stop dalam mode Film yang dipilih pengguna; klip 13,101 detik tersimpan.
- [x] Mengakhiri sesi uji dan memeriksa MediaProjection telah dilepas.
- [x] Membuat ikon kamera/dua layar; memeriksa tampilan ikon di realme dan vivo.
- [x] Merombak halaman utama, setup pengirim, kode koneksi, dan monitor dengan tema studio gelap/mint, ilustrasi native, serta komponen yang konsisten.
- [x] Menguji UI Studio: preview kamera melalui hotspot, Cermin on/off, rotasi monitor, validasi kode kosong, feedback perintah kamera, navigasi kembali kedua HP, dan Putuskan.
- [x] Build 0.2.0-studio, empat unit test, lint 0 error/21 warning, signature dan zipalign lulus. APK diperbarui pada kedua HP tanpa uninstall.

## 3. Keputusan arsitektur sementara

| ID | Keputusan | Status |
| --- | --- | --- |
| D-01 | Android native Kotlin; widget/drawable/Canvas dipakai pada P1 dan UI Studio. Compose tetap opsi MVP | UI Studio 0.2.0 diimplementasikan tanpa dependency UI baru |
| D-02 | Mirror kamera bawaan dengan MediaProjection | Asumsi dari kebutuhan; menunggu preferensi pengguna dan uji HP |
| D-03 | Wi-Fi sama sebagai jalur utama, hotspot manual sebagai jalur tanpa router | Direkomendasikan; belum diuji |
| D-04 | WebRTC media + signaling WSS lokal | Direkomendasikan; kandidat dependency belum dikunci |
| D-05 | Tanpa akun, backend cloud, atau public STUN/TURN untuk MVP LAN | Desain awal; host candidates harus dibuktikan di perangkat |
| D-06 | Satu pengirim dan satu monitor per sesi | Sesuai kebutuhan dua HP |
| D-07 | QR aman, aspect-fit, normal/cermin, indikator stale frame | Masuk cakupan MVP yang direncanakan |
| D-08 | Kontrol shutter opsional khusus kamera realme, melalui AccessibilityService yang diaktifkan di pengaturan Android | Foto, Rekam/Stop mode Video, dan Rekam/Stop mode Film telah diuji |
| D-09 | minSdk 26 sebagai baseline sementara | Tinjau kembali setelah model HP diketahui |
| D-10 | Implementasi P1 dimulai setelah instruksi “kerjakan” | APK dibuat dan dipasang pada kedua HP |
| D-11 | HP 1: realme RMX3393 sebagai perekam/pengirim; HP 2: vivo V2131 sebagai monitor | Peran realme dipilih pengguna; vivo menjadi monitor dari dua perangkat yang tersedia |

## 4. Informasi yang masih perlu dilengkapi

| Informasi | Kondisi sekarang | Pengaruh |
| --- | --- | --- |
| Merek/model HP 1 | realme RMX3393, dipilih pengguna sebagai perekam/pengirim | Kemampuan capture preview, codec, suhu, mode kamera |
| Merek/model HP 2 | vivo V2131 sebagai monitor | Decode, scanner QR, Wi-Fi, ABI |
| Versi Android kedua HP | realme RMX3393: Android 14/API 34; vivo V2131: Android 13/API 33; keduanya berstatus device | Permission, MediaProjection, minSdk |
| Pilihan kamera bawaan/internal | P1 menggunakan kamera bawaan realme; preview dan rekaman telah berjalan saat streaming | Kontrol opsional memakai layanan aksesibilitas khusus kamera |
| Kebutuhan foto saja atau juga video | Pengguna menyebut pembuatan video di luar rumah | Uji encoder saat kamera merekam menjadi kebutuhan nyata |
| Router tersedia saat penggunaan | Penggunaan di luar rumah tanpa router dikonfirmasi | Hotspot antarkedua HP menjadi jalur penggunaan outdoor |

Informasi ini tidak menghalangi penyusunan blueprint. Kompatibilitas perangkat belum dapat dinyatakan lulus. Asumsi ditulis agar dapat diperbarui tanpa menganggapnya keputusan final pengguna.

## 5. Rencana tahapan berikutnya

P0 dan P1 selesai. Sebagian fondasi P2/P3 turut dibuat untuk pengujian; keseluruhan tahap P2/P3 belum dianggap selesai.

| Tahap | Pekerjaan | Bukti selesai yang diperlukan | Status |
| --- | --- | --- | --- |
| P0 — Blueprint | Scope, stack, diagram, risiko, target, dan progres | Dua dokumen konsisten dan bisa ditinjau | Selesai |
| P1 — Kelayakan perangkat | Spike capture kamera realme dan stream LAN ke vivo | Preview/rekaman Wi-Fi serta hotspot offline berhasil; foto dan mode Film remote terverifikasi | Selesai |
| P2 — Koneksi dan lifecycle | Pairing, TLS/pinning, service, permissions, routing, reconnect | QR invalid ditolak; sesi berhenti bersih; hotspot tanpa internet bekerja | Fondasi ada; pengujian lengkap/reconnect otomatis belum selesai |
| P3 — Monitor MVP | Kedua peran, aspect-fit, rotasi, kualitas, indikator koneksi | Keempat tepi frame terlihat dan alur pengguna selesai | UI Studio/aspect-fit/rotasi monitor sudah diuji; scanner QR, pilihan kualitas, dan UX MVP lengkap belum selesai |
| P4 — Uji penggunaan | Benchmark, sesi panjang, perubahan jaringan, foto/video | Hasil terukur beserta keterbatasan perangkat | Belum dimulai |
| P5 — Distribusi pribadi | Signing, panduan instalasi, verifikasi jalur pemasangan | APK nanti terpasang dan berjalan pada kedua HP | Belum dimulai |
| P6 — Fitur lanjutan | Otomasi hotspot atau kamera internal sesuai kebutuhan | Blueprint diperbarui berdasarkan hasil uji dan pilihan pengguna | Ditunda |

## 6. Checklist uji masa depan

- [x] Streaming langsung pada Wi-Fi sama tanpa STUN/TURN/server cloud; isolasi internet total diuji terpisah melalui hotspot.
- [x] realme sebagai hotspot, vivo sebagai client, kedua data seluler nonaktif. Variasi data aktif dan perpindahan jaringan ketika sesi berjalan belum diuji sistematis.
- [x] Kamera belakang bawaan tampil; viewport aspect-fit diperiksa visual setelah perbaikan.
- [x] Foto remote tersimpan dengan benar ketika streaming aktif.
- [x] Rekaman video tetap berfungsi saat streaming; pergantian lensa belum diuji.
- [ ] Orientasi portrait/landscape benar tanpa crop otomatis.
- [ ] Mode cermin hanya mengubah monitor.
- [ ] Izin capture/network/QR ditolak atau dicabut ditangani.
- [ ] QR kedaluwarsa, token digunakan ulang, pin sertifikat salah, monitor kedua ditolak.
- [ ] Putus Wi-Fi, alamat berubah, reconnect, dan sesi lama tidak tercampur.
- [ ] Pengirim membuka kamera, monitor masuk background, layar terkunci, dan process mati ditangani.
- [ ] Sesi 20 menit: FPS, latency, memori, suhu, daya, dan frame drop dicatat.
- [x] Native library ARM64 terpasang pada kedua HP; ELF LOAD alignment 0x4000 dan APK zipalign -P 16 lulus. Runtime pada perangkat page-size 16 KB belum diuji.
- [x] Uji penghentian: monitor kembali ke halaman awal dan dumpsys media_projection menunjukkan null. Audit resource/memory jangka panjang belum dilakukan.

## 7. Bukti dan batas verifikasi saat ini

| Pemeriksaan | Hasil |
| --- | --- |
| Sumber teknis | Dokumentasi resmi dan repository maintainer ditinjau; tautan ada di TECHSTACK.md |
| Pemeriksaan workspace awal | Tidak ada source project atau instruksi AGENTS.md yang ditemukan |
| Pembuatan dokumen | TECHSTACK.md dan PROGRESS.md ditulis |
| Pemeriksaan dokumen awal | Pada tahap blueprint hanya Markdown dibuat; implementasi kemudian diizinkan pengguna |
| Deteksi USB/ADB | Kedua transport berstatus device. realme RMX3393 Android 14/API 34 dan vivo V2131 Android 13/API 33; ABI keduanya arm64-v8a, armeabi-v7a, armeabi |
| Instalasi SDK/dependency | SDK/JDK yang sudah tersedia digunakan; dependency aplikasi di-resolve melalui Gradle |
| Build / unit test / lint | assembleDebug, 4 test (0 gagal), lintDebug lulus; instrumented test belum dijalankan |
| Uji streaming dua HP | Wi-Fi lokal: VP8, 576 x 1280, sekitar 24 fps pada sampel; hotspot offline juga berhasil |
| Uji rekaman | Klip di realme: 32,834 detik; 95,151 detik; remote Video 12,644 detik; hotspot 15,002 detik; Film 13,101 detik |
| Uji foto | Foto remote 3072 x 4096, ukuran file 3.071.612 byte, tersimpan di realme |
| RTT / latency / daya | Wi-Fi: sampel stabil 4–7 ms, awal 25–62 ms. Hotspot: beberapa sampel 2–12 ms, awal 33 ms. Bukan benchmark latency end-to-end; latency dan daya belum diukur |
| APK / publikasi | APK debug dibangun dan dipasang pada dua HP; tidak dipublikasikan |
| Kontrol kamera | Pengguna mengaktifkan layanan; foto serta Rekam/Stop Video/Film berhasil. Setelah reset aplikasi, layanan dipulihkan lewat dialog Android |
| Penghentian sesi | Monitor kembali ke halaman awal dan MediaProjection realme null pada pemeriksaan |

## 8. Log perubahan

| Tanggal | Perubahan | Dampak |
| --- | --- | --- |
| 2026-10-17 | Membuat blueprint v0.1 dan tracker progres | Menetapkan arah teknis dan urutan validasi tanpa mulai build |
| 2026-10-17 | Memeriksa struktur dokumen dan isi workspace | Hanya TECHSTACK.md dan PROGRESS.md; tidak ada source, konfigurasi build, atau APK |
| 2026-10-17 | Mendeteksi dua HP melalui USB dan memperbarui blueprint v0.2 | realme RMX3393 terkonfirmasi Android 14/API 34 dan mendukung arm64-v8a; V2131 terbaca sebagai nama USB Windows, menunggu otorisasi ADB |
| 2026-10-17 | Membaca vivo setelah otorisasi USB selesai | vivo V2131 terkonfirmasi Android 13/API 33 dan mendukung arm64-v8a; kedua HP dapat diakses ADB |
| 2026-10-17 | Memperjelas kebutuhan video outdoor | Menetapkan hotspot lokal sebagai jalur tanpa router; streaming bersamaan dengan rekaman belum diuji |
| 2026-10-17 | Menetapkan peran HP sesuai pilihan pengguna | realme RMX3393 sebagai perekam/pengirim, vivo V2131 sebagai monitor; kriteria P1 memprioritaskan perekaman video |
| 2026-10-17 | Pengguna menginstruksikan implementasi; project P1 dibuat dan diuji | Build/test/lint lulus; APK dipasang, capture kamera dan perekaman berhasil melalui Wi-Fi lokal |
| 2026-10-17 | Memperbaiki adapter capture, TLS keystore, dan viewport | Preview terlihat; seluruh layar tampil dengan aspect-fit; signaling tetap terautentikasi |
| 2026-10-17 | Menambahkan kontrol terbatas sesuai pilihan pengguna | Layanan diaktifkan manual; remote Rekam/Stop menghasilkan klip 12,644 detik; foto dan hotspot masih diuji |
| 2026-10-17 | Memperbaiki enumerasi interface WebRTC untuk SoftAP | Hotspot offline berhasil dengan data seluler kedua HP mati; foto 3072 x 4096 dan klip 15,002 detik tersimpan |
| 2026-10-17 | Menguji mode Film sesuai pertanyaan pengguna | Tombol shutter sama; remote rekam/stop menghasilkan klip 13,101 detik dengan mode Film dipertahankan |
| 2026-10-17 | Menutup P1 dan menulis laporan pengujian | Batas pengujian, pekerjaan P2/P3, dan cara menjalankan dicatat di README.md serta P1_REPORT.md |
| 2026-10-17 | Membuat dan memasang ikon aplikasi kamera/dua layar | Asset imagegen disimpan di assets/app-icon; adaptive icon dan lima kepadatan PNG biasa/bulat diintegrasikan. Build/lint lulus, APK diperbarui di realme dan vivo; tampilan ikon diperiksa lewat Info aplikasi pada kedua HP, tidak terpotong. Layanan aksesibilitas realme tetap aktif |
| 2026-10-17 | Merombak seluruh halaman aplikasi menjadi UI Studio 0.2.0 | Halaman utama, pengirim, pairing dan monitor diperbarui; perbaikan label landscape dan Back vivo diuji. Build/4 test/lint/signature/zipalign lulus; APK terpasang di kedua HP. Bukti serta batas uji ada di UI_REPORT.md |
| 2026-10-17 | Menjelaskan cakupan mirroring sesuai pertanyaan pengguna | Seluruh layar pengirim dibagikan, bukan khusus kamera; kontrol dari monitor tetap Foto dan Rekam/Stop kamera realme |
| 2026-10-17 | Memperjelas pertukaran peran perangkat | Pengirim/monitor dapat dipilih pada kedua HP. Susunan vivo sebagai pengirim belum diuji; pembuka kamera dan kontrol shutter saat ini khusus realme |

Setiap pembaruan progres berikutnya harus mencatat pekerjaan nyata, hasil pemeriksaan, dan masalah yang masih terbuka. Jangan menandai sebuah fitur selesai hanya karena desainnya sudah ditulis.

## 9. Monitor frameless — 2026-10-18

- Header aplikasi, kartu status, margin, dan sudut membulat pada viewport monitor dihapus. Video menggunakan seluruh area layar dengan status bar/navigation bar tersembunyi.
- Aspect-fit dipertahankan supaya keempat tepi sumber terlihat. Perbedaan rasio atau orientasi kedua HP dapat menghasilkan ruang hitam.
- Kontrol mengambang: putuskan, RTT, cermin, sembunyikan kontrol, zoom, lampu, timer, garis, foto, dan rekam/stop. Ketuk video atau area kosong untuk menyembunyikan/menampilkan kontrol. Kondisi ini dipertahankan saat rotasi dan pemulihan Activity.
- Insets notch dan area navigasi hanya diterapkan pada kontrol. Dalam landscape, panel kamera berada di kanan bawah.
- Verifikasi: assembleDebug, testDebugUnitTest (8 tes, 0 gagal), dan lintDebug lulus (0 error; warning masih ada). Pemeriksaan perangkat dilakukan dengan realme sebagai pengirim dan vivo sebagai monitor melalui jaringan lokal.
- Terverifikasi pada vivo: viewport 720 × 1600 tanpa header/bingkai monitor, tampil/sembunyi kontrol lewat video dan ruang hitam, cermin aktif/nonaktif, rotasi ke landscape dengan sumber portrait tetap utuh, serta header kembali setelah sesi diputuskan. Pengaturan rotasi HP dikembalikan ke kondisi awal. Screenshot lokal ada di `artifacts/monitor-frameless/` (diabaikan Git).
- Android 8–11 dan perangkat dengan notch berbeda belum diuji langsung. Uji sesi panjang dan ukuran font besar belum dilakukan untuk tampilan baru ini.

## 10. Fokus, pengaturan, status rekaman, sambung ulang — 2026-10-18

Versi pengembangan 0.4.0 (versionCode 4). Empat fitur pilihan pengguna diimplementasikan; batas uji perangkat di bawah masih berlaku.

### Implementasi

- Monitor: ketuk gambar untuk fokus, tahan untuk kunci fokus, ketuk dua kali untuk tampil/sembunyikan kontrol. Tombol mata dicoret dan ketukan pada ruang hitam tetap tersedia. Koordinat memperhitungkan cermin dan ukuran renderer aspect-fit; pengirim menolak orientasi yang sudah berubah dan memakai meteringPointFactory PreviewView.
- Kunci fokus dikonfirmasi melalui Future CameraX. Kunci pencahayaan mengikuti dukungan AE lock dan hanya dipakai saat exposure Auto. Menu menyediakan lepas kunci fokus serta kunci/buka kunci pencahayaan.
- Roda gigi monitor mengatur Foto/Video, resolusi/FPS, ISO, shutter, dan WB numerik 2000–8000 K. Pilihan mengikuti kemampuan lensa. ISO dan shutter manual diisi berpasangan; Auto mengembalikan keduanya. Mode/format/manual diblokir selama rekaman atau pemrosesan. Kegagalan mode/format memulihkan pilihan sebelumnya.
- Durasi monitor mengikuti VideoRecordEvent.Status dan indikator REC menunggu Start. Finalize menentukan berhasil/gagal menyimpan. Monitor menampilkan baterai kedua HP dan ruang kosong pengirim; status tetap terlihat saat panel disembunyikan. Status lama ditandai dan kontrol dinonaktifkan ketika tidak ada gambar/status terbaru.
- Sambung ulang: jendela dua menit, token resume terpisah dari QR sekali pakai, pin sertifikat tetap diverifikasi, satu monitor aktif, tanpa penyimpanan kredensial ke disk. PeerConnection dan grant MediaProjection dipertahankan, ICE diulang setelah signaling tersambung lagi. Perintah shutter lama tidak diputar ulang.
- Bug konfigurasi FPS ganda di VideoCapture dan SessionConfig ditemukan saat uji mode Video; FPS kini hanya ditetapkan di SessionConfig. Countdown mempertahankan snapshot lengkap, mencegah shutter ganda, dan dibatalkan ketika kamera ditutup.

### Bukti uji

| Pemeriksaan | Hasil |
| --- | --- |
| Build dan pemeriksaan akhir | assembleDebug, testDebugUnitTest, lintDebug lulus; 16 tes, 0 gagal, 0 error lint (41 warning) |
| Koordinat fokus | Tes mencakup pembalikan X, renderer portrait di layar landscape, offset PreviewView, koordinat invalid dan view belum terukur |
| Keamanan resume | Tes token QR berbeda dari resume, batas waktu, notifikasi putus berulang tidak memperpanjang jendela, dan sesi yang tersambung bisa mengalami gangguan lagi |
| Pengaturan dua HP | realme pengirim → vivo monitor: ISO 200 dan pasangan shutter 1/30 s dikonfirmasi kembali; WB 5600 K dikonfirmasi; baterai dan ruang kosong tampil di monitor |
| Recorder vivo | Rekam/Stop lokal berhasil setelah perbaikan FPS; klip 17.580 ms, 1920 × 1080, 44.313.534 byte terdaftar di MediaStore |
| Kanal kontrol LAN ke vivo | `scripts/test-remote-camera.py`: autentikasi TLS dengan pin, telemetri, mode Video, Start, durasi, putus TCP mendadak, QR terpakai ditolak, resume terautentikasi, rekaman tetap berjalan, Stop dan Finalize berhasil |
| Penghentian uji | MediaProjection vivo null setelah klien uji mengakhiri sesi |
| APK | APK 0.4.0 dibuat dan dipasang di vivo. Pemasangan terakhir pada realme tersendat, lalu USB realme tidak kembali terdeteksi setelah ADB reconnect; pembaruan final realme masih menunggu USB pulih |

### Batas verifikasi yang masih terbuka

- Uji ulang realme pada build final, konfirmasi fokus/kunci fokus pada objek yang terlihat, dan pengujian gambar setelah perubahan pengaturan masih diperlukan. Konfirmasi request bukan pengukuran ketepatan warna atau fokus optik.
- Uji pemulihan gambar WebRTC dan retry klien Android antardua HP setelah Wi-Fi diputus belum selesai. Tes komputer menguji kanal kontrol pengirim dan kelangsungan recorder, bukan penerima video WebRTC. Jangan menyatakan uji sambung ulang dua HP sudah lulus.
- Proses/sesi pengirim harus tetap hidup dan alamat IP sama. Perubahan alamat, penghentian sesi, atau proses mati memerlukan QR baru.
- Kelvin memakai pendekatan gain yang belum dikalibrasi per sensor. Dukungan format dan kontrol dibatasi API yang diekspos produsen.
- Sumber layar yang benar-benar statis dapat memicu penanda tidak ada gambar terbaru. Uji sesi panjang, perubahan jaringan, perangkat lain, dan ukuran font besar belum dilakukan.
