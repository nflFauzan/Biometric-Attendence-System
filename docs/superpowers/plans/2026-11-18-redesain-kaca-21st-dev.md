# Redesain Kaca Menyeluruh (21st.dev Adjustable Glassmorphism) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengubah keseluruhan antarmuka web Biometric Attendance System (Takka Steel) mengadopsi estetika 21st.dev Adjustable Glassmorphism (panel kaca semi-transparan mengambang, efek blur & edge light halus, ambient room background yang dapat diganti user di produksi, floating dock & topbar navigation, bento-grid dashboard) tanpa mengubah business logic maupun skema database, dan memvalidasinya secara visual melalui Playwright MCP dengan look-and-fix loop hingga 100% identik dengan referensi.

**Architecture:** Menerapkan layer presentasi modular: sistem token CSS kaca 21st.dev di `src/index.css`, state manager wallpaper & material kaca client-side berbasis `localStorage` di `src/lib/glassTheme.ts`, kerangka shell responsif "Floating Islands" (`AppLayout.tsx`) dengan topbar kaca dan dock mengambang, restyling komponen shadcn UI (`input`, `dialog`, `card`, `table`, `FormDialog`), dashboard bento, rute `/design-preview` untuk isolasi visual, serta visual audit loop menggunakan Playwright MCP (`browser_take_screenshot`, `browser_resize`, visual comparison) untuk menjamin kesesuaian 100% dengan referensi 21st.dev.

**Tech Stack:** React 19, TypeScript, Tailwind CSS v4, GSAP & `@gsap/react`, Lucide React, Radix UI, TanStack React Query, Vitest, Playwright MCP Server.

**Spec:** [docs/superpowers/specs/2026-11-18-redesain-kaca-21st-dev-design.md](file:///C:/all%20project/Biometric-Attendence-System/docs/superpowers/specs/2026-11-18-redesain-kaca-21st-dev-design.md) dan [implementation-plan-glassmorphism.md](file:///C:/Users/THINKPAD%20L450/.gemini/antigravity/brain/86ce9d2c-4fa9-46f8-aa23-125a33366096/implementation-plan-glassmorphism.md)

## Global Constraints

- Zero Schema Change: Tidak ada file migrasi database Flyway baru atau perubahan tabel/kolom database.
- Zero Business Logic Change: Seluruh DTO, endpoint Spring Boot, aturan absensi, formula gaji, dan query TanStack tetap utuh.
- Identitas Takka Steel: Aksen Steel Navy (`#104FA6` / `#5B9AEE`) dan Gold (`#F5CE02`) digunakan secara terukur pada indikator aktif, tombol CTA, fokus keyboard, dan badge.
- Kontras Aksesibilitas: Rasio kontras teks minimal 4.5:1 terhadap permukaan kaca di semua mode (WCAG AA). Formula panel kaca memakai rasio terhitung 21st.dev: `calc((0.18 + var(--gw-opacity) * 0.5) * 100%)`.
- Anti-Nested Blur: Kaca di dalam panel kaca tidak memicu filter blur bertumpuk agar performa rendering GPU tetap ringan.
- Visual Fidelity 100%: Tampilan wajib diaudit dan dicocokkan dengan referensi `media_1794967538202_07e49da4.jpg` via Playwright MCP screenshot, dan diperbaiki dalam loop berulang hingga akurat.
- Aturan Git: Sesuai CLAUDE.md, gunakan nama dan email akun yang sesuai perannya (`aiinunzahra` untuk UI, `nisa262006` untuk docs, `PatoyUhuy` untuk test) tanpa `Co-Authored-By` dan tanpa kurung pada tipe commit (`feat: ...`, `docs: ...`, `test: ...`).

## Review Focus (7 Celah Kritis Teridentifikasi)

1. **Floating Islands vs Edge-to-Edge**: Layout lama menempel kaku di tepi layar; layout baru wajib menggunakan panggung melayang `.gw-workspace` ber-radius lengkung (topbar rounded-20px, rail rounded-23px, stage rounded-27px).
2. **Data-Dense Tables Clipping**: Tabel penggajian 15 kolom dan rekap bulanan dibungkus container horizontal scroll kaca internal (`overflow-x-auto` + `--gw-scrollbar`) dengan sticky frosted header (`glass-head`) agar tidak merusak lengkungan kaca.
3. **Wallpaper Luminance Blindspot**: Gambar background yang terlalu terang/putih di produksi diredam dengan shade vignette adaptif minimal 30% dan panel kaca berlapis opasitas minimum 25%–35%.
4. **Dialog Overlay Dimmer**: Modal overlay standar (`bg-black/80`) diganti dengan *frosted dark tint* (`bg-black/35 backdrop-blur-sm`) agar wallpaper tetap tampak mewah saat modal terbuka.
5. **Navigasi 6-8 Menu**: Desktop menggunakan kombinasi Side Icon Rail + Bottom Dock + modal Pengaturan; mobile menggunakan 4 navigasi utama + sheet "Lainnya".
6. **Isolasi Pengujian Visual Playwright MCP**: Disediakan rute `/design-preview` dengan data contoh lengkap agar Playwright MCP dapat melakukan audit visual secara deterministik tanpa bergantung pada koneksi live database.
7. **GSAP Vitest Timeout**: Hook `useGSAP` diproteksi agar tidak menggantung test runner Vitest saat `window.matchMedia` atau lingkungan JSDOM aktif.

---

### Task 1: Token CSS Kaca 21st.dev & Pembaruan Dokumentasi Desain

**Files:**
- Modify: `frontend/src/index.css:38-120`
- Modify: `docs/design-system.md:40-100`
- Modify: `docs/rencana-tema-kaca.md:15-35`
- Test: `frontend/src/test/glassTokens.test.ts`

**Interfaces:**
- Produces: CSS variables `--gw-blur`, `--gw-opacity`, `--gw-saturation`, `--gw-edge`, `--gw-tint`, `--gw-surface`, `--gw-panel`, `--gw-room`, `--gw-accent`; CSS classes `.gw-glass`, `.gw-panel`, `.gw-dock`, `.gw-rail`, `.gw-topbar`.

- [ ] **Step 1: Tulis unit test untuk memverifikasi ketersediaan token CSS kaca 21st.dev**

Buat file `frontend/src/test/glassTokens.test.ts`:
```ts
import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'

describe('Glassmorphism CSS Tokens (21st.dev)', () => {
  const css = fs.readFileSync(path.resolve(__dirname, '../index.css'), 'utf-8')

  it('mendefinisikan variabel materi kaca 21st.dev', () => {
    expect(css).toContain('--gw-blur')
    expect(css).toContain('--gw-opacity')
    expect(css).toContain('--gw-saturation')
    expect(css).toContain('--gw-tint')
    expect(css).toContain('--gw-edge')
  })

  it('mencegah nested backdrop blur untuk performa GPU', () => {
    expect(css).toMatch(/:where\(\.gw-glass,\s*\.gw-panel\)\s*:is\(\.gw-glass,\s*\.gw-panel\)/)
  })

  it('memiliki fallback prefers-reduced-transparency', () => {
    expect(css).toContain('prefers-reduced-transparency')
  })
})
```

- [ ] **Step 2: Jalankan test untuk memastikan test gagal**

Run: `npm test --prefix frontend src/test/glassTokens.test.ts`
Expected: FAIL karena variabel `--gw-*` belum terpasang di `index.css`.

- [ ] **Step 3: Implementasikan token kaca 21st.dev di `frontend/src/index.css` dan perbarui dokumen desain**

Tambahkan token visual 21st.dev ke dalam `frontend/src/index.css`:
- Root variables: `--gw-blur: 20px;`, `--gw-opacity: 0.3;`, `--gw-saturation: 1.25;`, `--gw-edge: 0.6;`, `--gw-tint: #ffffff;`.
- Aturan anti-nested blur dan utility classes `.gw-glass`, `.gw-panel`, `.gw-dock`, `.gw-rail`.
- Perbarui dokumentasi di `docs/design-system.md` dan `docs/rencana-tema-kaca.md` mencatat spesifikasi K-085 revisi 21st.dev.

- [ ] **Step 4: Jalankan test untuk memastikan test lolos**

Run: `npm test --prefix frontend src/test/glassTokens.test.ts`
Expected: PASS (semua assertion lolos).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/test/glassTokens.test.ts frontend/src/index.css docs/design-system.md docs/rencana-tema-kaca.md
git config user.name "nisa262006" && git config user.email "sabrinafitri593@gmail.com"
git commit -m "docs: perbarui spesifikasi token tema kaca 21st.dev"
```

---

### Task 2: State Manager Wallpaper Dinamis, Modal Pengatur Kaca, & Rute Preview

**Files:**
- Create: `frontend/src/lib/glassTheme.ts`
- Create: `frontend/src/components/GlassAdjustModal.tsx`
- Create: `frontend/src/components/AppBackdrop.tsx`
- Create: `frontend/src/pages/DesignPreviewPage.tsx`
- Modify: `frontend/src/App.tsx` (registrasi route `/design-preview`)
- Test: `frontend/src/test/glassTheme.test.ts`

**Interfaces:**
- Produces in `frontend/src/lib/glassTheme.ts`:
  ```ts
  export interface GlassSettings {
    blur: number
    opacity: number
    saturation: number
    edge: number
    tint: string
  }
  export interface WallpaperOption {
    id: string
    name: string
    url: string
    thumbnail: string
  }
  export const WALLPAPER_PRESETS: WallpaperOption[]
  export function loadGlassSettings(): GlassSettings
  export function saveGlassSettings(settings: GlassSettings): void
  export function loadWallpaper(): string
  export function saveWallpaper(url: string): void
  ```

- [ ] **Step 1: Tulis unit test untuk `glassTheme.ts`**

Buat `frontend/src/test/glassTheme.test.ts`:
```ts
import { beforeEach, describe, expect, it } from 'vitest'
import { loadGlassSettings, saveGlassSettings, loadWallpaper, saveWallpaper, WALLPAPER_PRESETS } from '../lib/glassTheme'

describe('Glass Theme State Manager', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('mengembalikan nilai bawaan jika localStorage kosong', () => {
    const s = loadGlassSettings()
    expect(s.blur).toBeGreaterThan(0)
    expect(s.opacity).toBeGreaterThan(0)
    expect(loadWallpaper()).toBe(WALLPAPER_PRESETS[0].url)
  })

  it('menormalkan nilai yang korup di localStorage ke rentang aman', () => {
    localStorage.setItem('takka_glass_settings', JSON.stringify({ blur: 999, opacity: -5 }))
    const s = loadGlassSettings()
    expect(s.blur).toBeLessThanOrEqual(40)
    expect(s.opacity).toBeGreaterThanOrEqual(0.08)
  })

  it('menyimpan dan memuat preferensi wallpaper', () => {
    saveWallpaper('https://example.com/custom.jpg')
    expect(loadWallpaper()).toBe('https://example.com/custom.jpg')
  })
})
```

- [ ] **Step 2: Jalankan test untuk memverifikasi test gagal**

Run: `npm test --prefix frontend src/test/glassTheme.test.ts`
Expected: FAIL karena modul belum dibuat.

- [ ] **Step 3: Implementasikan `glassTheme.ts`, `GlassAdjustModal.tsx`, `AppBackdrop.tsx`, dan `DesignPreviewPage.tsx`**

1. Buat `frontend/src/lib/glassTheme.ts` dengan fungsi validasi angka batas (`bounded`), normalisasi, dan interaksi `localStorage`.
2. Buat `frontend/src/components/AppBackdrop.tsx` yang me-render layer background `.gw-room` (gambar dengan fallback graceful ke gradasi Takka) + shade overlay `.gw-room-shade`.
3. Buat `frontend/src/components/GlassAdjustModal.tsx` yang berisi kontrol slider blur, opasitas, saturasi, edge light, pemilih warna tint, tombol preset (Clear, Frosted, Solid), serta galeri wallpaper preset bawaan dan input custom URL.
4. Buat `frontend/src/pages/DesignPreviewPage.tsx` yang me-render panggung lengkap 21st.dev (`adjustable-glass-dashboard.tsx`) dan daftarkan di `App.tsx` pada rute `/design-preview` untuk keperluan inspeksi visual Playwright MCP.

- [ ] **Step 4: Jalankan test untuk memverifikasi test lolos**

Run: `npm test --prefix frontend src/test/glassTheme.test.ts`
Expected: PASS (3 tests passed).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/glassTheme.ts frontend/src/components/GlassAdjustModal.tsx frontend/src/components/AppBackdrop.tsx frontend/src/pages/DesignPreviewPage.tsx frontend/src/App.tsx frontend/src/test/glassTheme.test.ts
git config user.name "aiinunzahra" && git config user.email "ainunzahra273@gmail.com"
git commit -m "feat: tambahkan modul tema kaca dinamis modal pengatur wallpaper dan rute preview 21st.dev"
```

---

### Task 3: Kerangka Navigasi & Shell "Floating Islands" (`AppLayout.tsx`)

**Files:**
- Modify: `frontend/src/components/AppLayout.tsx`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `AppBackdrop`, `GlassAdjustModal`, `loadGlassSettings`, `loadWallpaper`
- Produces: Layout panggung melayang (`.gw-workspace`) yang mengintegrasikan topbar kaca floating (`.gw-topbar.gw-glass`), compact side rail untuk desktop/tablet, dan floating bottom dock pill untuk mobile.

- [ ] **Step 1: Periksa ekspektasi layout pada `src/App.test.tsx`**

Baca `src/App.test.tsx` untuk memastikan navigasi path, tombol logout, toggle profil, dan menu lainnya tetap teruji tanpa merusak test suite.

- [ ] **Step 2: Modifikasi `AppLayout.tsx` mengadopsi arsitektur "Floating Islands" 21st.dev**

- Pindahkan kontainer dari tepi layar kaku ke `.gw-workspace` melayang di tengah.
- Pasang `AppBackdrop` di lapisan latar belakang (`z-index: -3`).
- Header menjadi floating rounded-20px glass bar (`.gw-topbar.gw-glass`) berisi brand mark, tombol "Adjust glass", toggle tema, lonceng notifikasi, dan avatar inisial.
- Navigasi samping menjadi floating rounded-23px glass rail (`.gw-rail.gw-glass`) dan dock pill desktop (`.gw-dock`).
- Navigasi mobile menjadi floating rounded-2xl glass pill di bagian bawah layar.
- Bungkus `main` dengan pulau kaca melayang rounded-27px (`.gw-main.gw-glass`).

- [ ] **Step 3: Jalankan verifikasi test layout**

Run: `npm test --prefix frontend src/App.test.tsx`
Expected: PASS (seluruh 15 test di `App.test.tsx` tetap hijau).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/AppLayout.tsx
git config user.name "aiinunzahra" && git config user.email "ainunzahra273@gmail.com"
git commit -m "feat: transformasikan layout aplikasi mengadopsi struktur pulau kaca floating 21st.dev"
```

---

### Task 4: Halaman Login Kaca Mengambang (`LoginPage.tsx`)

**Files:**
- Modify: `frontend/src/pages/LoginPage.tsx`
- Test: `frontend/src/pages/LoginPage.test.tsx`

**Interfaces:**
- Consumes: `AppBackdrop`, `fieldClass`, `api`
- Produces: Halaman login bertema ambient glassmorphism dengan kartu login melayang di tengah, input berkabut, dan tombol pill Steel Blue.

- [ ] **Step 1: Jalankan unit test `LoginPage.test.tsx` saat ini sebagai baseline**

Run: `npm test --prefix frontend src/pages/LoginPage.test.tsx`
Expected: PASS (9 tests passed).

- [ ] **Step 2: Restyle `LoginPage.tsx`**

- Tampilkan latar belakang `AppBackdrop` penuh di balik kartu login.
- Ubah kartu container login menjadi floating frosted glass card (`.gw-panel.gw-glass`) dengan efek *edge light* kilau atas.
- Field input username dan password menggunakan gaya translucent dengan kontras teks tajam dan fokus border aksen Takka.
- Tombol submit login menggunakan style pill button dengan hover kilau cerah.

- [ ] **Step 3: Jalankan test untuk memverifikasi tidak ada regresi logika login**

Run: `npm test --prefix frontend src/pages/LoginPage.test.tsx`
Expected: PASS (9 tests passed tanpa error autentikasi atau form).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/LoginPage.tsx
git config user.name "aiinunzahra" && git config user.email "ainunzahra273@gmail.com"
git commit -m "feat: perbarui halaman login dengan kartu kaca mengambang 21st.dev"
```

---

### Task 5: Restyling Primitives Form, Input, & Dialog Kaca

**Files:**
- Modify: `frontend/src/components/ui/input.tsx`
- Modify: `frontend/src/components/ui/select.tsx`
- Modify: `frontend/src/components/ui/dialog.tsx`
- Modify: `frontend/src/components/FormDialog.tsx`
- Test: `frontend/src/components/DatePickers.test.tsx`
- Test: `frontend/src/pages/EmployeesPage.test.tsx`

**Interfaces:**
- Produces: `Input`, `Select`, `Dialog`, `fieldClass`, `selectClass` dengan gaya kaca semi-transparan, border tipis bergradasi, modal overlay transparan berkabut (`bg-black/35 backdrop-blur-sm`), dan fokus aksen Steel Blue.

- [ ] **Step 1: Verifikasi unit test form dialog sebelum perubahan**

Run: `npm test --prefix frontend src/components/DatePickers.test.tsx`
Expected: PASS.

- [ ] **Step 2: Restyle komponen form di `input.tsx`, `select.tsx`, `dialog.tsx`, dan `FormDialog.tsx`**

- `input.tsx`: Ganti latar solid dengan translucent backdrop `bg-white/10 dark:bg-black/20 border-white/20 dark:border-white/10 backdrop-blur-md`, padding nyaman, focus ring aksen steel blue.
- `dialog.tsx`: Modifikasi `DialogOverlay` menjadi `bg-black/35 backdrop-blur-sm` agar wallpaper tetap tembus. Modifikasi `DialogContent` menggunakan material `.gw-panel.gw-glass` dengan bayangan mendalam (`box-shadow: 0 24px 80px var(--gw-popover-shadow)`).
- `FormDialog.tsx`: Perbarui `fieldClass` dan `selectClass` agar serasi dengan standar input kaca 21st.dev.

- [ ] **Step 3: Jalankan test untuk memastikan dialog dan date picker tetap berfungsi normal**

Run: `npm test --prefix frontend src/components/DatePickers.test.tsx`
Expected: PASS (12 tests passed).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/ui/input.tsx frontend/src/components/ui/select.tsx frontend/src/components/ui/dialog.tsx frontend/src/components/FormDialog.tsx
git config user.name "aiinunzahra" && git config user.email "ainunzahra273@gmail.com"
git commit -m "feat: sesuaikan komponen form dialog dan input dengan estetika kaca 21st.dev"
```

---

### Task 6: Transformasi Dashboard Utama ke Format Bento Kaca (`DashboardPage.tsx` & `HomePage.tsx`)

**Files:**
- Modify: `frontend/src/pages/DashboardPage.tsx`
- Modify: `frontend/src/pages/HomePage.tsx`
- Test: `frontend/src/pages/AttendancePages.test.tsx`
- Test: `frontend/src/pages/HomePage.test.tsx`

**Interfaces:**
- Consumes: `useAuth`, `useDashboardStream`, `api<DashboardSummary>`
- Produces: Layout Bento Dashboard 21st.dev dengan kartu hero kehadiran angka besar, Ring SVG interaktif, attention list dengan ikon kategori kaca, dan animasi entrance GSAP `.gw-reveal` yang terlindung dari timeout test runner.

- [ ] **Step 1: Jalankan unit test dashboard saat ini**

Run: `npm test --prefix frontend src/pages/AttendancePages.test.tsx`
Expected: PASS.

- [ ] **Step 2: Terapkan struktur bento grid dan GSAP reveal terlindung di `DashboardPage.tsx` dan `HomePage.tsx`**

- Bungkus kartu KPI dan daftar tindakan dengan panel kaca `.gw-panel.gw-reveal`.
- Hubungkan hook `useGSAP` dengan pelindung lingkungan non-test (`if (typeof window !== 'undefined' && process.env.NODE_ENV !== 'test')`).
- Desain ulang kartu "Hadir Hari Ini" dengan format tipografi besar `tabular-nums` khas 21st.dev dan pendaran ring emas/steel.
- Ubah daftar "Perlu Perhatian" menjadi list bergaya transaksi 21st.dev (ikon bulat di kiri, label tebal di tengah, chevron dan counter badge di kanan).
- Perbarui `HomePage.tsx` (dashboard karyawan) dengan kartu glass yang identik dan tombol pengajuan izin beraksen Steel Blue.

- [ ] **Step 3: Jalankan test untuk memastikan dashboard SSE stream dan data query tetap berjalan**

Run: `npm test --prefix frontend src/pages/AttendancePages.test.tsx src/pages/HomePage.test.tsx`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/DashboardPage.tsx frontend/src/pages/HomePage.tsx
git config user.name "aiinunzahra" && git config user.email "ainunzahra273@gmail.com"
git commit -m "feat: rekonstruksi dashboard dan beranda ke tata letak bento kaca 21st.dev"
```

---

### Task 7: Restyling Tabel Data & Halaman Master Operasional (`CrudPage.tsx`, `table.tsx`, `card.tsx`)

**Files:**
- Modify: `frontend/src/components/ui/table.tsx`
- Modify: `frontend/src/components/ui/card.tsx`
- Modify: `frontend/src/components/CrudPage.tsx`
- Test: `frontend/src/pages/EmployeesPage.test.tsx`
- Test: `frontend/src/pages/DevicesPage.test.tsx`

**Interfaces:**
- Produces: Tabel master data dan kartu operasional dengan container horizontal scroll kaca internal (`overflow-x-auto` + `--gw-scrollbar`), header kaca pekat (`glass-head`), baris transparan dengan hover efek kilau, tombol aksi *pill segmented*.

- [ ] **Step 1: Jalankan test master data sebagai baseline**

Run: `npm test --prefix frontend src/pages/EmployeesPage.test.tsx`
Expected: PASS.

- [ ] **Step 2: Restyle `table.tsx`, `card.tsx`, dan `CrudPage.tsx`**

- `table.tsx`: Ganti border kaku dengan divider garis halus `--gw-line`, header kolom berlabel kapital (`label-caps`), baris hover berlatar semi-transparan. Bungkus tabel dalam container ber-scroll horizontal kaca internal.
- `card.tsx`: Tambahkan kelas `.gw-panel` bawaan dan pendaran kilau atas.
- `CrudPage.tsx`: Restyle bilah pencarian menjadi pill search bar (`.gw-search`) dengan input transparan dan tombol tambah data pill button.

- [ ] **Step 3: Jalankan verifikasi test halaman master karyawan dan mesin**

Run: `npm test --prefix frontend src/pages/EmployeesPage.test.tsx src/pages/DevicesPage.test.tsx`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/ui/table.tsx frontend/src/components/ui/card.tsx frontend/src/components/CrudPage.tsx
git config user.name "aiinunzahra" && git config user.email "ainunzahra273@gmail.com"
git commit -m "feat: restyle tabel dan halaman master data dengan permukaan kaca 21st.dev"
```

---

### Task 8: Pengujian Visual Playwright MCP & Look-and-Fix Loop (Iterasi Hingga 100% Identik 21st.dev)

**Target & Tools:**
- Server: Jalankan `npm run dev --prefix frontend` via background task.
- MCP Server: `playwright` (`browser_navigate`, `browser_resize`, `browser_take_screenshot`, `browser_click`).
- Acuan Visual: `C:/Users/THINKPAD L450/.gemini/antigravity/brain/86ce9d2c-4fa9-46f8-aa23-125a33366096/.user_uploaded/media_1794967538202_07e49da4.jpg`.

**Checkpoint Visual:**
1. Desktop Viewport (1440x900): Rute Visual Preview (`/design-preview`)
2. Desktop Viewport (1440x900): Halaman Login Kaca (`/login`)
3. Desktop Viewport (1440x900): Modal Interaktif "Adjust glass" terbuka
4. Mobile Viewport (375x812): Navigasi Floating Dock & Bento Cards
5. Desktop Viewport (1440x900): Modal Form Karyawan / Form Dialog Kaca

- [ ] **Step 1: Jalankan server frontend dan inisialisasi sesi Playwright MCP**

1. Jalankan `run_command` dengan `npm run dev --prefix frontend` sebagai background process.
2. Tunggu server aktif di `http://localhost:5173`.
3. Panggil MCP `browser_navigate` ke `http://localhost:5173/design-preview`.

- [ ] **Step 2: Ambil tangkapan layar awal (Baseline Screenshots)**

1. Atur resolusi desktop: `browser_resize` ke `{ width: 1440, height: 900 }`.
2. Ambil screenshot preview workspace: `browser_take_screenshot` ke `preview-desktop.png`.
3. Buka popover "Adjust glass": `browser_click` pada tombol Adjust glass lalu ambil screenshot `adjust-glass-open.png`.
4. Navigasi ke `/login`: `browser_take_screenshot` ke `login-desktop.png`.
5. Atur resolusi mobile: `browser_resize` ke `{ width: 375, height: 812 }`.
6. Navigasi kembali ke `/design-preview`: ambil screenshot `preview-mobile.png`.

- [ ] **Step 3: Analisis Deviasi Visual vs Referensi 21st.dev (Audit Loop Iteration)**

Lakukan evaluasi visual mendalam terhadap screenshot yang dihasilkan dengan 5 kriteria audit 21st.dev:
1. **Material Kaca**: Apakah pendaran kilau atas (`inset 0 1px 0`), tingkat blur (`20px`), dan opasitas kaca (`0.25–0.35`) sudah tampak autentik dan tidak flat?
2. **Ambient Room Background**: Apakah gambar latar belakang tampak menembus kartu secara elegan dengan shade vignette yang tepat?
3. **Floating Topbar & Dock**: Apakah header bar dan dock navigasi melayang (*floating*) dengan rounded corners yang presisi, bukan menempel kaku ke tepi browser?
4. **Bento Card Balance & Typography**: Apakah kartu hero kehadiran, Ring progress SVG, dan daftar aktivitas sudah seimbang secara spasial dengan tipografi besar tabular?
5. **Form & Dialog**: Apakah input field transparan dengan border halus dan keterbacaan teks tajam?

- [ ] **Step 4: Eksekusi Look-and-Fix Loop (Perbaiki & Uji Ulang Hingga 100% Sesuai)**

Jika ditemukan ketidaksesuaian visual (misal: warna terlalu gelap, bayangan kurang menyebar, posisi dock terlalu mepet, atau blur kurang halus):
1. Perbaiki kode CSS / komponen terkait secara presisi.
2. Ambil screenshot ulang dengan `browser_take_screenshot`.
3. Bandingkan kembali dengan gambar referensi `media_1794967538202_07e49da4.jpg`.
4. **Ulangi loop ini** sampai seluruh elemen visual (topbar, dock, kartu bento, adjust glass modal, form) benar-benar 100% identik dengan estetika 21st.dev.

- [ ] **Step 5: Commit Bukti Screenshot & Penyempurnaan Visual**

```bash
git add frontend/src/index.css frontend/src/components/
git config user.name "aiinunzahra" && git config user.email "ainunzahra273@gmail.com"
git commit -m "feat: sempurnakan detail visual berdasarkan hasil audit iteratif Playwright MCP hingga 100% identik 21st.dev"
```

---

### Task 9: Verifikasi Regresi Menyeluruh & Build Produksi

**Files:**
- Test: Seluruh suite test frontend (`npm test --prefix frontend`)
- Build: `npm run build --prefix frontend`

**Interfaces:**
- Produces: 0 error kompilasi TypeScript/Vite, seluruh 336 unit/integration tests tetap hijau tanpa regresi fungsi.

- [ ] **Step 1: Jalankan verifikasi build produksi**

Run: `npm run build --prefix frontend`
Expected: Exit code 0, bundling selesai tanpa error tipe data TypeScript.

- [ ] **Step 2: Jalankan test suite penuh frontend**

Run: `npm test --prefix frontend`
Expected: Seluruh test unit & integrasi yang relevan lulus (PASS).

- [ ] **Step 3: Commit Verifikasi Akhir**

```bash
git config user.name "PatoyUhuy" && git config user.email "broka10cncprn@gmail.com"
git commit -m "test: verifikasi menyeluruh suite frontend dan stabilitas tema kaca 21st.dev"
```
