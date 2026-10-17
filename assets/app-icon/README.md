# Ikon Mirror Selfie

Ikon menggambarkan dua layar HP dan lensa kamera, dengan warna mint/biru serta titik rekam merah. Latar launcher memakai warna navy `#0D161C` yang sesuai dengan aplikasi.

## Berkas

- `source.png`: hasil asli imagegen, foreground transparan; tidak ditimpa saat packaging.
- `mirror-selfie-icon.png`: artwork persegi 512 × 512 dengan latar navy.
- `launcher-preview.png`: pratinjau masker bulat, kotak membulat, dan ukuran 48 px.
- `../../app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`: foreground adaptive 1024 × 1024, alpha dipertahankan.
- `../../app/src/main/res/mipmap-anydpi-v26/`: XML adaptive icon; background terpisah dari foreground.
- `../../app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/`: PNG launcher biasa dan bulat pada lima kepadatan.

Pengemasan ulang di Windows dengan PowerShell 7: `./scripts/package-icon.ps1`. Script hanya menempatkan, menskalakan proporsional, dan mengemas gambar asli ke ukuran resource Android. Simbol dipusatkan dalam lingkaran aman berdiameter 60 dp pada layer 108 dp untuk menghindari pemotongan oleh masker launcher.

## Pembuatan visual

Mode: **built-in imagegen**, bukan CLI/API runner. Tanggal: 17 Oktober 2026. Kategori: logo-brand. `transparent_background: true`.

Prompt final:

> Create one premium Android launcher icon foreground for an app named Mirror Selfie: a camera monitor app that mirrors the rear-camera phone to a second phone. Square 1024x1024 canvas, genuinely transparent background. A single bold centered emblem combining TWO subtly overlapping upright smartphone screen outlines with ONE large camera lens in the front screen. Rear screen offset slightly upper-right, front screen lower-left; unified compact mark rather than a scene. Front phone frame in bright mint and turquoise, rear phone frame in cool pale cyan, camera lens deep midnight navy with a luminous turquoise circular ring and one small white reflection. A tiny warm coral recording dot in the upper right of the front phone. Clean confident rounded geometry, beautiful subtle depth and restrained glossy highlights, smooth anti-aliased edges, contemporary polished technology brand, high contrast on a dark navy background. Recognizable at 48 pixels. No text, letters, labels, arrows, hands, people, photographic background, drop shadow outside the emblem, extra badges, or enclosing rounded-square tile. IMPORTANT Android adaptive icon safe zone: entire opaque emblem including every edge must fit inside the central 60 percent of the canvas width and height, with generous completely transparent outer margins of at least 20 percent on all four sides. Large simple lens, no fussy small UI details. Deliver only the isolated finished icon foreground on transparent background.

Hasil asli 1280 × 1280; ukuran resource disesuaikan oleh script packaging.

## Verifikasi

`assembleDebug` dan `lintDebug` lulus (0 error, 28 warning). APK diperbarui tanpa uninstall pada realme RMX3393 dan vivo V2131. Ikon diperiksa melalui halaman Info aplikasi di kedua HP: realme memakai masker kotak membulat, vivo bulat, dan simbol tampil utuh. Screenshot lokal: `artifacts/icon-realme.png` serta `artifacts/icon-vivo.png` (diabaikan Git). Layanan kontrol kamera realme tetap terdaftar aktif setelah update.
