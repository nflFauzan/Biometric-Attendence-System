# Spesifikasi Desain: Redesain Kaca Menyeluruh (21st.dev Adjustable Glassmorphism)

**Tanggal:** 2026-11-18  
**Status:** Disetujui Pengguna (2026-11-18)  
**Tujuan:** Mentransformasi seluruh antarmuka web Biometric Attendance System (Takka Steel) mengadopsi estetika 21st.dev Adjustable Glassmorphism (panel kaca semi-transparan mengambang, efek blur & edge light halus, ambient room background yang dapat diganti user di produksi, floating dock & topbar navigation, bento-grid dashboard) tanpa mengubah business logic maupun skema database.

## 1. Prinsip Utama & Batasan Global

1. **Zero Schema Change**: Tidak ada migrasi Flyway baru, tidak ada penambahan tabel/kolom database.
2. **Zero Business Logic Change**: Kontrak API backend, aturan kerja, perhitungan lembur/gaji, hak akses Spring Security, dan alur form tetap 100% utuh.
3. **Fisika Kaca & Latar Belakang**: Kaca adalah material netral *frosted translucent* (opasitas 15%–35%, blur 18–24px, inset border highlight 1px). Kedalaman warna dan suasana ruangan dipancarkan oleh *ambient background image*.
4. **Identitas Takka Steel Terukur**: Aksen Steel Navy (`#104FA6` / `#5B9AEE`) dan Gold (`#F5CE02`) dipakai secara terukur pada indikator aktif, tombol aksi utama, fokus keyboard, dan badge penting, tanpa mengurangi estetika elegan 21st.dev.
5. **Kustomisasi Background Pengguna di Produksi**:
   - Preferensi wallpaper disimpan di `localStorage` per peramban pengguna (`takka_theme_bg`).
   - Tersedia galeri preset bawaan (Cozy Studio 21st.dev, Steel Architecture, Minimal Dark Loft, Clean Office) serta opsi input URL / upload file lokal.
   - Popover *Adjust Glass* (`.gw-material`) menyimpan setelan blur, opasitas, saturasi, edge light, dan tint ke `localStorage` (`takka_glass_settings`).
6. **Performa & Aksesibilitas**:
   - Tidak ada blur bertumpuk (nested blur dinonaktifkan via CSS `:where(.gw-glass) :is(.gw-glass)`).
   - Dukungan `prefers-reduced-transparency` (fallback solid) dan `prefers-reduced-motion` (animasi GSAP nonaktif).
   - Rasio kontras teks minimal 4.5:1 untuk keterbacaan tinggi.

## 2. Struktur Komponen & Alur Perubahan

1. **Token & CSS Dasar**:
   - File: `frontend/src/index.css`, `docs/design-system.md`, `docs/rencana-tema-kaca.md`.
   - Mengintegrasikan variabel token 21st.dev (`--gw-blur`, `--gw-opacity`, `--gw-saturation`, `--gw-tint`, `--gw-edge`, `--gw-surface`, `--gw-accent`).
2. **Kerangka Layout (`AppLayout.tsx`)**:
   - Menyematkan layer background dinamis (`.gw-room` + `.gw-room-shade`).
   - Floating header bar (`.gw-topbar.gw-glass`) dengan tombol *Adjust Glass*, notifikasi, dan profil.
   - Navigasi floating dock / rail adaptif (desktop, tablet, mobile).
3. **Halaman Login (`LoginPage.tsx`)**:
   - Kartu login melayang `glass-strong` di atas ambient background.
4. **Dashboard Bento (`DashboardPage.tsx` & `HomePage.tsx`)**:
   - Hero attendance stat panel, Ring SVG progress, list cards tindakan berkabut kaca, GSAP reveal animation.
5. **Form Field, Dialog, & Tabel Data (`FormDialog.tsx`, `input.tsx`, `select.tsx`, `CrudPage.tsx`, `table.tsx`)**:
   - Input transparan dengan border pendaran cahaya, modal popover kaca tebal, dan tabel berheader semi-pekat.
