# Rencana: Tema Kaca (Glassmorphism) Terang/Gelap

**Status: rancangan disetujui pengguna 2026-10-14 (empat bagian), langsung dieksekusi atas permintaan pengguna.** Sub-pekerjaan B dari urutan A (rekan kerja) → B (tema kaca) → C (Fase 5 penggajian). Keputusan dicatat sebagai K-085; K-017 tetap berlaku untuk warna brand, font, dan logo.

## 1. Pilihan pengguna

- **Kaca penuh:** semua kartu, panel, tabel, form, dialog, dan kerangka memakai blur asli di semua perangkat.
- **Terang dan gelap**, dengan tombol tema di header: Terang / Gelap / Ikut perangkat (default).
- **Responsif** di HP, tablet, dan desktop.

## 2. Latar dan permukaan

Latar statis (gradasi CSS, tanpa gambar, tanpa animasi) dipasang sekali di kerangka aplikasi dan halaman login:

| Mode | Dasar | Cahaya |
| --- | --- | --- |
| Terang | `#EEF3FA` | biru steel `#104FA6` kiri atas, biru muda `#96B0D8` kanan, emas `#F5CE02` tipis kanan bawah |
| Gelap | `#050D1A` | biru `#104FA6` dan `#3D65A8`, emas tipis |

Tiga tingkat kaca (utilitas di `frontend/src/index.css`):

| Utilitas | Terang | Gelap | Dipakai untuk |
| --- | --- | --- | --- |
| `glass` | putih 70%, blur 16px | navy `#0F2240` 55%, blur 16px | kartu, KPI, panel, kartu daftar di HP |
| `glass-strong` | putih 85%, blur 20px | navy 75%, blur 20px | tabel, form, dialog, menu, notifikasi |
| `glass-bar` | putih 60%, blur 16px | navy 60%, blur 16px | header, bottom nav |

Sidebar memakai kaca berwarna steel yang lebih pekat agar identitas brand tetap terasa. Semua kaca punya bingkai 1px yang terlihat, kilau tipis di tepi atas, dan bayangan lembut. Badge status, isi kolom input, dan halaman surat cetak A4 tetap solid.

Opasitas kaca terang dinaikkan dari usulan awal (55%/75%) menjadi 70%/85% mengikuti panduan ui-ux-pro-max untuk kaca terang yang berisi teks; disetujui pengguna 2026-10-14.

## 3. Kerangka responsif

| Ukuran | Lebar | Kerangka |
| --- | --- | --- |
| HP | < 768px | Header kaca menempel di atas; bottom nav kaca melayang (jarak 8px, sudut membulat, aman untuk notch); isi satu kolom |
| Tablet | 768–1023px | Sidebar kaca ringkas 76px (ikon + label kecil); tabel bisa digeser di dalam kartunya |
| Desktop | ≥ 1024px | Sidebar kaca penuh 248px; header kaca sticky; isi maksimal 1440px |

Target sentuh minimal 44px, tidak ada geser horizontal di level halaman, isi tidak tertutup header/bottom nav, teks isi minimal 16px di HP. Urutan lapisan: latar → isi → header/sidebar → bottom nav → dialog → notifikasi.

## 4. Tema

- Pilihan disimpan di `localStorage` (kunci `tema`). Skrip kecil di `index.html` memasang kelas `dark` sebelum halaman tampil, jadi tidak ada kedipan putih.
- "Ikut perangkat" langsung mengikuti perubahan pengaturan HP/PC.
- Tombol tema berupa satu tombol ikon (matahari/bulan/monitor) yang berganti urutan Terang → Gelap → Ikut perangkat, dengan label untuk pembaca layar. Ada di header pada semua ukuran layar.

## 5. Aksesibilitas dan performa

- Kontras teks minimal 4.5:1 (teks biasa dan muted) diuji otomatis dari token CSS: setiap tingkat kaca dicampur dengan warna latar terterang dan tergelap di tiap mode.
- Cincin fokus 2px yang terlihat di atas kaca. Status tidak hanya lewat warna.
- "Kurangi transparansi" (`prefers-reduced-transparency`) → kaca menjadi solid. Browser tanpa dukungan `backdrop-filter` → kaca 92%. "Kurangi gerakan" → transisi mati (aturan lama).
- Tidak ada blur bertumpuk: kaca di dalam kaca tidak diberi blur lagi (satu aturan CSS).
- Batas lolos: gulir mulus di Chrome dengan CPU diperlambat 4× pada lebar 375px, dan skor Lighthouse HP tidak turun lebih dari 5 poin. Jika tidak lolos, blur di HP diturunkan ke 8px setelah dilaporkan ke pengguna.

## 6. Pengujian

- Vitest: uji kontras dari `index.css`, uji logika tema (default, simpan, kelas `dark`, ikut perangkat), uji tombol tema di header. Semua test lama tetap hijau.
- Playwright di Docker: 375/768/1024/1440px × terang/gelap untuk login, dashboard, absensi, rekan kerja, karyawan + dialog, izin, dan profil.
