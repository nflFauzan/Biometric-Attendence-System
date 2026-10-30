# Penyederhanaan UI/UX untuk Pengguna Awam (Gaptek) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menyederhanakan antarmuka sistem absensi Takka Steel dari 17 menu terfragmentasi menjadi 4 pilar utama yang intuitif bagi pengguna awam (Owner toko besi dan Finance lapangan), menghapus jargon developer, dan merapikan modal menu HP.

**Architecture:** 
1. Merestrukturisasi `navigation.ts` agar grup menu sidebar desktop menjadi ramping dan bottom navigation mobile hanya menampilkan 4 tugas harian esensial (Dashboard, Absensi, Izin, Laporan).
2. Mengintegrasikan penanganan scan bermasalah (Perlu Ditinjau, Scan Tertahan, Scan Tak Dikenal) ke dalam tab terpadu di halaman Absensi (`AttendancePage.tsx`).
3. Mengganti seluruh jargon teknis developer (*"Kandidat Lembur"*, *"Simulasi Perhitungan"*) menjadi istilah operasional toko yang mudah dipahami (*"Jam Lembur"*, *"Hitung Gaji / Upah"*).
4. Mengubah modal "Lainnya" di layar mobile (`AppLayout.tsx`) dari 17 link bertumpuk menjadi kelompok kartu aksi terstruktur.

**Tech Stack:** React 18, React Router v7, Lucide Icons, Tailwind CSS, Vitest / React Testing Library.

**Spec:** [analisis_ui_ux_gaptek_takka_steel.md](../../../Users/THINKPAD%20L450/.gemini/antigravity/brain/9128ecb5-6828-48b5-a6e3-c7a922185bfc/analisis_ui_ux_gaptek_takka_steel.md)

## Global Constraints

- Semua rute URL yang ada (`/absensi`, `/absensi/perlu-ditinjau`, `/absensi/lembur`, `/absensi/scan-tak-dikenal`, `/absensi/scan-tertahan`, `/karyawan`, `/mesin`, `/laporan`, `/simulasi`, dll.) **harus tetap berfungsi** agar tidak merusak deep link, riwayat browser, dan bookmark.
- Akun karyawan (`ROLE_EMPLOYEE`) tetap mempertahankan 4 menu pribadinya (`/beranda`, `/izin-saya`, `/absensi-saya`, `/profil`).
- Seluruh 296 pengujian vitest frontend harus tetap lulus (atau diperbarui jika menguji label yang diganti).

## Review Focus

1. **Pengguna Mobile HP (390px):** Tombol menu bawah tidak boleh overflow, dan drawer "Lainnya" harus dapat ditutup dengan mudah tanpa scroll berlebihan.
2. **Keterbacaan Kolom Absensi:** Penggantian label "Kandidat lembur" menjadi "Jam lembur" tidak boleh mematahkan format waktu atau pembulatan.
3. **Tab Hub Scan Bermasalah:** Berpindah antar-tab di `/absensi` harus mempertahankan filter tanggal dan cabang yang sedang dipilih.
4. **Hak Akses Peran:** Tombol/tab pengaturan yang hanya untuk Owner (`Audit Log`, `Jenis Izin`) tidak boleh bocor ke role `HR_FINANCE` atau `EMPLOYEE`.
5. **Akses Cepat dari Dashboard:** Tautan anomali di kartu dashboard harus mengarah langsung ke tab yang bersesuaian di halaman Absensi.

---

### Task 1: Pembersihan Jargon Developer pada Halaman Utama

**Files:**
- Modify: `frontend/src/pages/AttendancePage.tsx`
- Modify: `frontend/src/pages/AttendanceEmployeePage.tsx`
- Modify: `frontend/src/pages/CalculationPreviewPage.tsx`
- Test: `frontend/src/pages/AttendancePages.test.tsx`
- Test: `frontend/src/pages/CalculationPreviewPage.test.tsx`

**Interfaces:**
- Mengubah label header tabel: `"Kandidat lembur"` ➔ `"Jam lembur"`.
- Mengubah judul halaman simulasi: `"Simulasi Perhitungan"` ➔ `"Pratinjau Hitung Gaji"`.

- [ ] **Step 1: Perbarui ekspektasi test pada test file**
  Ubah string pencarian `"Kandidat lembur"` menjadi `"Jam lembur"` pada `AttendancePages.test.tsx` dan `CalculationPreviewPage.test.tsx`.

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan**
  Run: `cd frontend; npx vitest run src/pages/AttendancePages.test.tsx src/pages/CalculationPreviewPage.test.tsx`  
  Expected: FAIL (karena label di komponen belum diubah).

- [ ] **Step 3: Implementasikan penggantian teks di komponen**
  - Di `AttendancePage.tsx`: ganti teks kolom header dan list mobile dari `"Kandidat lembur"` menjadi `"Jam lembur"`.
  - Di `AttendanceEmployeePage.tsx`: ganti label statis `"Kandidat lembur"` menjadi `"Jam lembur"`.
  - Di `CalculationPreviewPage.tsx`: ganti judul halaman dan kolom header dari `"Kandidat lembur"` menjadi `"Jam lembur"`.

- [ ] **Step 4: Jalankan test untuk memverifikasi keberhasilan**
  Run: `cd frontend; npx vitest run src/pages/AttendancePages.test.tsx src/pages/CalculationPreviewPage.test.tsx`  
  Expected: PASS.

- [ ] **Step 5: Commit perubahan**
  ```bash
  git add frontend/src/pages/AttendancePage.tsx frontend/src/pages/AttendanceEmployeePage.tsx frontend/src/pages/CalculationPreviewPage.tsx frontend/src/pages/AttendancePages.test.tsx frontend/src/pages/CalculationPreviewPage.test.tsx
  git commit -m "refactor(ui): ganti jargon kandidat lembur dan simulasi perhitungan ke bahasa operasional toko"
  ```

---

### Task 2: Hub Tab Terpadu Masalah Scan pada Halaman Absensi

**Files:**
- Modify: `frontend/src/pages/AttendancePage.tsx`
- Test: `frontend/src/pages/AttendancePages.test.tsx`

**Interfaces:**
- Menambahkan sub-header tab di bagian atas `AttendancePage.tsx`:
  - `[Absensi Hari Ini]` (`/absensi`)
  - `[Perlu Ditinjau]` (`/absensi/perlu-ditinjau`)
  - `[Scan Tertahan]` (`/absensi/scan-tertahan`)
  - `[Scan Tak Dikenal]` (`/absensi/scan-tak-dikenal`)
  - `[Validasi Lembur]` (`/absensi/lembur`)

- [ ] **Step 1: Tulis test untuk sub-navigasi tab absensi**
  Pada `AttendancePages.test.tsx`, tambahkan test case yang memastikan tab-tab masalah scan dirender sebagai tautan sub-navigasi yang dapat diklik.

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan**
  Run: `cd frontend; npx vitest run src/pages/AttendancePages.test.tsx`  
  Expected: FAIL (sub-navigasi belum ada).

- [ ] **Step 3: Implementasikan bar sub-navigasi tab di `AttendancePage.tsx`**
  Gunakan elemen `<nav aria-label="Sub menu absensi">` dengan styling pill/tab yang elegan (glass pill tab) yang menyorot tab aktif berdasarkan URL saat ini (`/absensi`).

- [ ] **Step 4: Jalankan test untuk memverifikasi keberhasilan**
  Run: `cd frontend; npx vitest run src/pages/AttendancePages.test.tsx`  
  Expected: PASS.

- [ ] **Step 5: Commit perubahan**
  ```bash
  git add frontend/src/pages/AttendancePage.tsx frontend/src/pages/AttendancePages.test.tsx
  git commit -m "feat(ui): tambahkan sub-navigasi tab terpadu untuk masalah scan di halaman absensi"
  ```

---

### Task 3: Restrukturisasi Navigasi Sidebar Desktop & Bottom Bar Mobile

**Files:**
- Modify: `frontend/src/navigation.ts`
- Modify: `frontend/src/navigation.test.ts`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Mengelompokkan `MENU_GROUPS` menjadi 4 kategori bersih:
  `['Operasional', 'Kelola Toko', 'Pengaturan', 'Saya']`
- Mengatur 4 menu utama untuk bottom bar mobile (`mobile: true`):
  1. `Dashboard` (`/`)
  2. `Absensi` (`/absensi`)
  3. `Izin & Cuti` (`/izin`)
  4. `Laporan & Gaji` (`/laporan`)
  *(Karyawan dan Mesin dipindahkan dari bottom bar ke menu Kelola Toko di dalam modal "Lainnya" agar layar HP tidak penuh).*

- [ ] **Step 1: Perbarui unit test navigasi di `navigation.test.ts`**
  Sesuaikan penegasan bahwa role `OWNER` dan `HR_FINANCE` memiliki grup menu yang rapi, dan bottom navigation mobile memprioritaskan alur kerja harian (Dashboard, Absensi, Izin, Laporan).

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan**
  Run: `cd frontend; npx vitest run src/navigation.test.ts`  
  Expected: FAIL.

- [ ] **Step 3: Modifikasi konfigurasi menu di `navigation.ts`**
  - Kelompokkan:
    - *Operasional*: Dashboard, Absensi Harian, Izin & Cuti, Laporan.
    - *Kelola Toko*: Karyawan, Cabang, Kategori Karyawan, Mesin Absensi, Akun.
    - *Pengaturan & Sistem*: Aturan Kerja, Kalender Libur, Jenis Izin/Cuti, Hitung Gaji, Audit Log.
    - *Saya*: Profil.
  - Atur flag `mobile: true` hanya pada 4 menu operasional harian.

- [ ] **Step 4: Jalankan test untuk memverifikasi kelulusan**
  Run: `cd frontend; npx vitest run src/navigation.test.ts src/App.test.tsx`  
  Expected: PASS.

- [ ] **Step 5: Commit perubahan**
  ```bash
  git add frontend/src/navigation.ts frontend/src/navigation.test.ts frontend/src/App.test.tsx
  git commit -m "feat(nav): restrukturisasi navigasi sidebar dan 4 pilar utama bottom bar mobile"
  ```

---

### Task 4: Desain Ulang Modal Menu Mobile ("Lainnya")

**Files:**
- Modify: `frontend/src/components/AppLayout.tsx`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Mengubah daftar vertikal memanjang di modal `moreOpen` menjadi pengelompokan berbasis seksi/kartu yang ramah sentuhan (touch-friendly):
  - *Seksi 1: Kelola Toko* (Karyawan, Mesin, Cabang, Akun)
  - *Seksi 2: Pengaturan & Sistem* (Aturan Kerja, Kalender Libur, Hitung Gaji, Audit Log)
  - *Seksi 3: Akun Pengguna* (Profil, Ganti Password, Keluar)

- [ ] **Step 1: Tulis test interaksi menu lainnya di mobile**
  Pada `App.test.tsx`, tambahkan pengujian bahwa membuka modal "Lainnya" menampilkan kategori terpisah, bukan satu daftar flat yang tidak terstruktur.

- [ ] **Step 2: Jalankan test untuk memverifikasi kegagalan**
  Run: `cd frontend; npx vitest run src/App.test.tsx`  
  Expected: FAIL.

- [ ] **Step 3: Implementasikan pengelompokan seksi di Dialog `AppLayout.tsx`**
  Ganti mapping flat `menu.filter((item) => !item.mobile)` dengan perulangan per grup menu (`MENU_GROUPS`) menggunakan header kecil dan tombol berukuran nyaman untuk jari jempol pengguna HP.

- [ ] **Step 4: Jalankan seluruh test suite frontend**
  Run: `cd frontend; npm run test`  
  Expected: PASS (seluruh 296+ tes lulus).

- [ ] **Step 5: Commit perubahan**
  ```bash
  git add frontend/src/components/AppLayout.tsx frontend/src/App.test.tsx
  git commit -m "feat(ui): kelompokkan menu lainnya di mobile menjadi kartu seksi yang rapi dan mudah diakses"
  ```

---

### Task 5: Verifikasi Visual dengan MCP Playwright

**Files:**
- Artifact: `docs/superpowers/plans/2026-10-30-penyederhanaan-ui-ux-gaptek.md`

- [ ] **Step 1: Jalankan browser Playwright pada resolusi Desktop (1440x900)**
  Ambil screenshot halaman `/absensi` dan `/` untuk memverifikasi kerapian sidebar baru dan tab hub scan bermasalah.

- [ ] **Step 2: Jalankan browser Playwright pada resolusi Mobile (390x844)**
  Ambil screenshot bottom bar mobile dan modal "Lainnya" untuk memverifikasi tidak ada lagi daftar 17 link memanjang.

- [ ] **Step 3: Konfirmasi tidak ada error konsol / regresi fungsi**
  Periksa konsol browser dan responsivitas halaman.

