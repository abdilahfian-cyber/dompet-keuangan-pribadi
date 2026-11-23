# Dompet — Keuangan Pribadi Android

Prototype proyek Android native untuk Samsung Galaxy S22 Ultra.

## Fitur dasar
- Dashboard saldo
- Uang masuk / uang keluar
- Penyimpanan lokal
- Pencatatan kategori dan catatan
- Target arsitektur siap dikembangkan menjadi dashboard grafik, anggaran, tabungan, laporan, backup/restore, dan PIN/biometrik.

## Membuat APK
Buka proyek ini di Android Studio, tunggu Gradle Sync selesai, lalu pilih:
Build > Build APK(s)

APK debug biasanya berada di:
app/build/outputs/apk/debug/app-debug.apk

Catatan: lingkungan ChatGPT ini tidak menyertakan Android SDK/Gradle sehingga APK tidak dikompilasi di sini.

## Unduh APK di Samsung Galaxy S22 Ultra

**[⬇ Unduh Dompet.apk (versi terbaru)](../../releases/latest/download/Dompet.apk)**
(aktif setelah build pertama selesai; jika tidak terbuka, pakai halaman Releases di repository ini)

### Menyiapkan sekali saja
1. Buat repository GitHub, lalu upload seluruh isi proyek ini (termasuk folder `.github` dan file `app/dompet.keystore`).
2. Tab Actions > "Build APK" > Run workflow. Tunggu sekitar 3-5 menit sampai hijau.
3. Setiap push ke branch main otomatis membuat APK baru di Releases.

### Install di S22 Ultra
1. Buka halaman repository di HP (Chrome atau Samsung Internet), ketuk tautan Unduh di atas.
2. Buka file dari notifikasi atau My Files > Download. Jika muncul "tidak diizinkan menginstal aplikasi tidak dikenal", ketuk Pengaturan, aktifkan "Izinkan dari sumber ini", kembali, lalu ketuk Instal.
3. Jika muncul peringatan Play Protect, ketuk Detail > Tetap instal.
4. Jika instalasi terblokir: Pengaturan > Keamanan dan privasi > Auto Blocker, matikan sementara.
5. Update: unduh APK terbaru dan install di atas aplikasi lama. Jangan uninstall dulu, supaya data tidak hilang.
