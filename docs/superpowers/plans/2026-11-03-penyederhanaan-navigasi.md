# Penyederhanaan Navigasi Owner & Finance — Rencana Implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sidebar Owner/Finance dari 19 menu menjadi 6 (Beranda, Absensi, Izin & Cuti, Gaji & Laporan, Karyawan, Pengaturan), halaman yang tumpang tindih digabung jadi tab, dan Gaji & Laporan mendapat daftar cek periode sebelum unduh.

**Architecture:** Frontend saja. Tab memakai query string `?tab=` lewat satu hook `useSearchParamState`; halaman lama dipakai ulang sebagai isi tab (PageHeader + sub-nav lamanya dilepas). Alamat lama dialihkan oleh komponen `RedirectKeepSearch` yang membawa query string. Jumlah "belum beres" dihitung di frontend dari endpoint yang sudah ada lewat hook `useFixCounts`, dipakai bersama oleh badge tab Absensi dan daftar cek.

**Tech Stack:** React + react-router + TanStack Query + Vitest/Testing Library (`src/test/fetchMock.ts`), shadcn di `src/components/ui` (jangan diedit).

**Spec:** `docs/superpowers/specs/2026-11-03-penyederhanaan-navigasi-design.md`

## Global Constraints

- Backend, `SecurityConfig`, `AccessControlTests`, schema Flyway: **tidak diubah**.
- Hak akses halaman sama dengan sekarang: khusus Owner hanya Jenis Izin dan Audit Log (`RequireRole roles={['OWNER']}`); lainnya `MANAGERS = ['OWNER','HR_FINANCE']`.
- Menu & tampilan role `EMPLOYEE` tidak berubah.
- Semua teks UI Bahasa Indonesia; label menu persis: `Beranda`, `Absensi`, `Izin & Cuti`, `Gaji & Laporan`, `Karyawan`, `Pengaturan`.
- Nama tab persis: Absensi = `Harian` / `Perlu Dibereskan` / `Lembur` (nilai `harian` / `bereskan` / `lembur`); Gaji = `Laporan` / `Kasbon` (`laporan` / `kasbon`); Karyawan = `Profil` / `Absensi` / `Akun & Sidik jari` / `Kasbon` (`profil` / `absensi` / `akun` / `kasbon`). Tab pertama = bawaan bila `tab` kosong/tak dikenal.
- Daftar cek **memperingatkan, tidak mengunci**: tombol unduh tetap aktif.
- Panggilan API hanya lewat `src/lib/api.ts`.
- **Jangan commit/push** — pengguna commit sendiri. Langkah "Laporkan" = tulis ringkasan file yang berubah.
- Test dijalankan dengan `npm test` dari `frontend/` (tidak mengganggu `spring-boot:run` pengguna).

## Review Focus

1. **Bookmark lama dengan query string** (`/absensi/karyawan/12?tanggal=2026-11-02`, `/absensi/perlu-ditinjau?from=…`) harus mendarat di tab baru dengan tanggal yang sama — test di Task 2, 4, 5.
2. **Belum login membuka alamat lama** → ke `/login?next=<alamat lama>`, setelah login dialihkan ke alamat baru — test di Task 2.
3. **Finance membuka `/pengaturan/jenis-izin` atau `/pengaturan/audit-log` langsung** → dialihkan ke beranda, kartunya tidak tampil — test di Task 2.
4. **Scan tak dikenal mencapai batas server (500)** → hitungan tampil `500+`, bukan angka yang menyesatkan — test di Task 3.
5. **Satu endpoint hitungan gagal** → daftar cek menampilkan baris "Gagal memuat …" untuk bagian itu (bukan "Semua beres") — test di Task 6.

---

### Task 1: Hook `useSearchParamState` dan komponen `PageTabs`

**Files:**
- Create: `frontend/src/lib/useSearchParamState.ts`, `frontend/src/components/PageTabs.tsx`
- Test: `frontend/src/lib/useSearchParamState.test.tsx`, `frontend/src/components/PageTabs.test.tsx`

**Interfaces:**
- Produces:
  - `useSearchParamState(key: string, fallback: string): [string, (value: string) => void]` — membaca `searchParams.get(key) ?? fallback`; setter menulis dengan `setSearchParams(..., { replace: true })`, mempertahankan param lain, menghapus key bila `value === fallback` atau `''`.
  - `PageTabs({ tabs, param = 'tab', label }: { tabs: { value: string; label: string; badge?: number }[]; param?: string; label: string }): JSX.Element` — `role="tablist"` ber-`aria-label={label}`, tiap tab `<Link role="tab" aria-selected>` ke `?<param>=<value>`; param lain dibuang **kecuali** `from`,`to`,`branchId`,`categoryId` yang dibawa. Badge tampil bila `badge > 0`. Gaya sama dengan `AttendanceSubNav` sekarang.
  - `useActiveTab(tabs: { value: string }[], param = 'tab'): string` — nilai tak dikenal → `tabs[0].value`.

- [ ] **Step 1: Tulis test gagal**
  - `useSearchParamState`: di `MemoryRouter initialEntries={['/x?from=2026-11-01&a=1']}`, `value === '2026-11-01'`; setelah `set('2026-11-05')` lokasi `?from=2026-11-05&a=1`; `set(fallback)` menghapus `from`.
  - `PageTabs`: di `/absensi?tab=lembur&from=2026-11-01&x=9`, tab "Lembur" `aria-selected="true"`; link "Harian" ber-href `/absensi?tab=harian&from=2026-11-01` (tanpa `x`); badge `3` tampil, badge `0` tidak.
  - `useActiveTab` dengan `?tab=ngawur` → nilai tab pertama.
- [ ] **Step 2:** `npm test -- useSearchParamState PageTabs` → FAIL (modul tidak ada).
- [ ] **Step 3:** Implementasikan kedua file sesuai Interfaces.
- [ ] **Step 4:** `npm test -- useSearchParamState PageTabs` → PASS.
- [ ] **Step 5:** Laporkan.

---

### Task 2: Sidebar 6 menu, halaman Pengaturan, rute `/pengaturan/*`, pengalihan alamat

**Files:**
- Modify: `frontend/src/navigation.ts`, `frontend/src/App.tsx`, `frontend/src/components/AppLayout.tsx` (judul header + tombol Profil desktop), `frontend/src/pages/CalculationPreviewPage.tsx:117-120`, `frontend/src/pages/DashboardPage.tsx:145`
- Create: `frontend/src/pages/SettingsPage.tsx`, `frontend/src/components/SettingsSubpage.tsx`, `frontend/src/components/RedirectKeepSearch.tsx`
- Test: `frontend/src/App.test.tsx`, `frontend/src/pages/SettingsPage.test.tsx`

**Interfaces:**
- Produces:
  - `MENU` manager baru (urut): `/` Beranda (mobile), `/absensi` Absensi (mobile), `/izin` Izin & Cuti (mobile), `/gaji` Gaji & Laporan (mobile), `/karyawan` Karyawan, `/pengaturan` Pengaturan, `/profil` Profil dengan flag baru `mobileOnly: true` (hanya di "Lainnya" HP, tidak di sidebar desktop). Judul kelompok sidebar manager dihapus (satu daftar). Item EMPLOYEE tidak berubah.
  - `SETTINGS_PAGES: { slug: string; label: string; description: string; group: 'Perusahaan' | 'Aturan & gaji' | 'Akses & keamanan'; roles: Role[]; icon: LucideIcon }[]` — `cabang` Cabang, `kategori` Kategori Karyawan, `mesin` Mesin Absensi (Perusahaan); `aturan-kerja` Aturan Kerja, `kalender-libur` Kalender Libur, `jenis-izin` Jenis Izin/Cuti (OWNER), `simulasi` Simulasi Perhitungan (Aturan & gaji); `akun` Akun, `audit-log` Audit Log (OWNER) (Akses & keamanan).
  - `menuItemFor(pathname)` mengembalikan item Pengaturan untuk `/pengaturan/*` dan Gaji untuk `/gaji*`; `settingsPageFor(pathname)` untuk jejak header `Pengaturan / <label>`.
  - `RedirectKeepSearch({ to }: { to: string | ((params: Params) => string) })` — `<Navigate replace>` dengan query lama digabung (param milik `to` menang).
  - `SettingsSubpage` — layout route: tautan "← Pengaturan" ke `/pengaturan` lalu `<Outlet />`.

Rute (di dalam `RequireAuth`): `/pengaturan` → `SettingsPage`; `/pengaturan/<slug>` → halaman lama di bawah `SettingsSubpage` dengan guard sama seperti sekarang; `/gaji` → `ReportsPage` (sementara; tab di Task 6). Pengalihan: `/cabang`,`/kategori`,`/mesin`,`/akun`,`/aturan-kerja`,`/kalender-libur`,`/jenis-izin`,`/simulasi`,`/audit-log` → `/pengaturan/<slug>`; `/laporan` → `/gaji`. Dashboard link `/aturan-kerja` → `/pengaturan/aturan-kerja`.

Keterangan Simulasi menjadi persis: "Coba hitung rekap satu hari memakai aturan yang berlaku untuk memeriksa pengaturan. Hasilnya tidak disimpan dan bukan laporan gaji; gaji dibuat di menu Gaji & Laporan."

- [ ] **Step 1: Tulis test gagal**
  - Owner: sidebar berisi tepat link `Beranda, Absensi, Izin & Cuti, Gaji & Laporan, Karyawan, Pengaturan` (urut), tanpa `Profil`, `Audit Log`, `Cabang`. Test lama "Owner melihat menu Audit Log" menjadi "Owner melihat kartu Audit Log di Pengaturan".
  - `/pengaturan` Owner: 9 kartu dalam 3 kelompok; HR_FINANCE: 7 kartu, tanpa "Jenis Izin/Cuti" dan "Audit Log".
  - HR_FINANCE membuka `/pengaturan/audit-log` → mendarat di `/`.
  - `/cabang` → URL `/pengaturan/cabang`, ada link kembali "Pengaturan", header memuat "Pengaturan" dan "Cabang".
  - Belum login membuka `/aturan-kerja` → `/login?next=%2Faturan-kerja`; setelah login berakhir di `/pengaturan/aturan-kerja`.
  - Header desktop punya tombol "Profil" ke `/profil`; dialog "Lainnya" HP tetap berisi link Profil, Karyawan, Pengaturan.
  - Test menu EMPLOYEE yang ada tetap lulus tanpa diubah.
- [ ] **Step 2:** `npm test -- App SettingsPage` → FAIL.
- [ ] **Step 3:** Implementasikan. Kartu Pengaturan = `Link` ke `/pengaturan/<slug>` dengan ikon, label, deskripsi satu baris; disaring `roles.includes(me.role)`.
- [ ] **Step 4:** `npm test -- App SettingsPage` → PASS.
- [ ] **Step 5:** `npm test` seluruhnya. Test halaman yang me-render path lama harus tetap lulus lewat pengalihan; yang memeriksa judul header/menu lama diperbarui ke path baru. Expected: semua PASS.
- [ ] **Step 6:** Laporkan.

---

### Task 3: Hook `useFixCounts`

**Files:**
- Create: `frontend/src/lib/fixCounts.ts`
- Test: `frontend/src/lib/fixCounts.test.tsx`

**Interfaces:**
- Consumes: `api`; tipe `ReviewItem`, `OvertimeRow`, `HeldScan`, `Scan` dari `src/lib/types.ts`.
- Produces:
  - `type FixFilter = { from: string; to: string; branchId?: string; categoryId?: string }`
  - `type FixCount = { count: number | null; capped: boolean; error: string | null; isPending: boolean }`
  - `useFixCounts(filter: FixFilter): { reviewDays: FixCount; overtimePending: FixCount; heldPending: FixCount; unmatched: FixCount; total: number; isPending: boolean }`
  - `UNMATCHED_LIMIT = 500` (sama dengan `AttendanceController.UNMATCHED_LIMIT`); `formatCount(c: FixCount): string` → `"500+"` bila `capped`.

Query (key berawalan `'/api/attendance'` / `'/api/overtime'` agar ikut ter-invalidate oleh aksi halaman yang ada):
- `/api/attendance/review-queue?from&to&branchId&categoryId` → `length`
- `/api/overtime?from&to&branchId&categoryId` → jumlah `status === 'BELUM_DIVALIDASI'`
- `/api/attendance/held-scans?from&to&status=BELUM_DITINJAU` → `length` (tanpa cabang)
- `/api/attendance/unmatched-scans?from&to` → `length`, `capped = length >= UNMATCHED_LIMIT`
- `total` = jumlah `count` yang tidak null.

- [ ] **Step 1: Tulis test gagal** dengan `mockFetch`: 2 review, 3 overtime (2 `BELUM_DIVALIDASI`, 1 `TERVALIDASI`), 1 held, 0 unmatched → `2, 2, 1, 0`, `total=5`. 500 scan tak dikenal → `formatCount(unmatched) === '500+'`. held-scans membalas 500 → `heldPending.error` terisi, `count=null`, tidak dihitung di `total`. Dengan `branchId: '3'`: URL review-queue memuat `branchId=3`, URL held-scans tidak.
- [ ] **Step 2:** `npm test -- fixCounts` → FAIL.
- [ ] **Step 3:** Implementasikan.
- [ ] **Step 4:** `npm test -- fixCounts` → PASS.
- [ ] **Step 5:** Laporkan.

---

### Task 4: Absensi bertab (Harian · Perlu Dibereskan · Lembur)

**Files:**
- Create: `frontend/src/pages/AttendanceHubPage.tsx`, `frontend/src/pages/FixListTab.tsx`, `frontend/src/components/PeriodFilter.tsx`
- Modify: `AttendancePage.tsx`, `OvertimePage.tsx`, `ReviewQueuePage.tsx`, `HeldScansPage.tsx`, `UnmatchedScansPage.tsx`, `App.tsx`, `DashboardPage.tsx:143-144,216`
- Delete: `frontend/src/components/AttendanceSubNav.tsx` (setelah tidak dipakai)
- Test: `AttendancePages.test.tsx`, `ReviewQueuePage.test.tsx`, `HeldScansPage.test.tsx`, `OvertimePage.test.tsx`, baru `FixListTab.test.tsx`

**Interfaces:**
- Consumes: `PageTabs`, `useActiveTab`, `useSearchParamState` (Task 1); `useFixCounts`, `formatCount`, `FixFilter` (Task 3).
- Produces:
  - `PeriodFilter({ showCategory?: boolean })` — DatePicker Dari/Sampai + select cabang (+ kategori) yang membaca/menulis `from`,`to`,`branchId`,`categoryId` di URL; bawaan `from` = tanggal 1 bulan berjalan, `to` = `localDateIso()`.
  - `usePeriodFilter(): FixFilter` — membaca nilai yang sama.
  - Ekspor isi tanpa header: `AttendanceDailyTab` (dari `AttendancePage`), `OvertimeTab` (filter diganti `PeriodFilter showCategory` + `usePeriodFilter`), `ReviewQueueSection(filter: FixFilter)`, `HeldScansSection({ from, to })` (filter status internal tetap, bawaan `BELUM_DITINJAU`), `UnmatchedScansSection({ from, to })`.
  - `AttendanceHubPage` di `/absensi`: `PageHeader` "Absensi" + `PageTabs label="Sub menu absensi"`; badge `bereskan` = `reviewDays + heldPending + unmatched`, badge `lembur` = `overtimePending`.
  - `FixListTab`: `PeriodFilter` sekali, lalu tiga bagian berjudul **"Hari bermasalah"**, **"Scan dari cabang lain"**, **"Scan sidik jari tak dikenal"** dengan jumlah; jumlah 0 tampil ringkas "Beres". Bagian scan diberi catatan "Semua cabang" bila `branchId` terisi.
  - Link baris ke detail karyawan sementara tetap `/absensi/karyawan/:id?...` (diganti di Task 5).
- Pengalihan: `/absensi/perlu-ditinjau`, `/absensi/scan-tertahan`, `/absensi/scan-tak-dikenal` → `/absensi?tab=bereskan`; `/absensi/lembur` → `/absensi?tab=lembur` (query dibawa).
- Dashboard: "Scan dengan PIN tak dikenal" & "Scan tertahan" → `/absensi?tab=bereskan`; StatTile "Kandidat lembur" → `/absensi?tab=lembur`.

- [ ] **Step 1: Tulis test gagal**
  - `/absensi` → heading "Absensi", tab "Harian" terpilih, tabel harian tampil (asersi lama dari `AttendancePages.test.tsx` dipindah).
  - `/absensi/perlu-ditinjau?from=2026-10-01&to=2026-10-05` → URL `/absensi?tab=bereskan&from=2026-10-01&to=2026-10-05`; permintaan review-queue dan held-scans memakai rentang itu.
  - Mock 2 review + 1 held + 0 unmatched: badge tab "Perlu Dibereskan" `3`; tiga heading bagian tampil; bagian tak dikenal "Beres".
  - `/absensi/lembur` → tab "Lembur" terpilih; aksi setujui/kurangi/tolak di `OvertimePage.test.tsx` tetap lulus (path render saja yang diubah).
  - Aksi "Masukkan ke rekap"/"Abaikan" di `HeldScansPage.test.tsx` tetap lulus di dalam tab.
- [ ] **Step 2:** `npm test -- AttendancePages ReviewQueuePage HeldScansPage OvertimePage FixListTab` → FAIL.
- [ ] **Step 3:** Implementasikan; lepas `PageHeader` dan `AttendanceSubNav` dari kelima halaman, ganti state rentang tanggal lokal dengan `usePeriodFilter` (tanggal tunggal di Harian tetap seperti sekarang). Hapus `AttendanceSubNav.tsx`.
- [ ] **Step 4:** Test yang sama → PASS; `npm test` → semua PASS; `npm run build` → sukses.
- [ ] **Step 5:** Laporkan.

---

### Task 5: Karyawan — detail bertab dan kolom status akun

**Files:**
- Modify: `EmployeeDetailPage.tsx`, `AttendanceEmployeePage.tsx`, `EmployeesPage.tsx`, `App.tsx`, `AttendancePage.tsx:54`, `ReviewQueuePage.tsx:131`
- Create: `frontend/src/components/EmployeeLoansTab.tsx`
- Test: `EmployeeDetailPage.test.tsx`, `EmployeesPage.test.tsx`, `AttendancePages.test.tsx` (bagian detail absensi karyawan)

**Interfaces:**
- Consumes: `PageTabs`, `useActiveTab` (Task 1).
- Produces:
  - `EmployeeAttendanceTab({ employeeId }: { employeeId: string })` — isi `AttendanceEmployeePage` tanpa tautan "Absensi harian" dan tanpa `PageHeader`; tetap membaca `from`,`to`,`tanggal` dari URL.
  - `EmployeeLoansTab({ employeeId }: { employeeId: number })` — tabel read-only dari `GET /api/loans?employeeId=<id>` (`EmployeeLoan[]`); tombol "Kelola kasbon" ke `/gaji?tab=kasbon`; kosong → "Belum ada kasbon."
  - `EmployeeDetailPage` (`/karyawan/:id`): header (nama + status + "Kembali ke daftar") lalu tab `profil` (Data kepegawaian + `BranchAccessCard`), `absensi`, `akun` (kartu Akun web + `FingerprintSection`), `kasbon`.
  - Teks "Nonaktifkan di menu Akun jika perlu." menjadi "Nonaktifkan lewat Pengaturan → Akun jika perlu."
  - `EmployeesPage`: kolom baru **"Akun"** — badge "Punya akun" / "Belum" dari pencocokan `GET /api/users` per `employeeId`.
- Pengalihan: `/absensi/karyawan/:id` → `/karyawan/:id?tab=absensi` (query dibawa). Link di `AttendancePage.tsx:54` dan `ReviewQueuePage.tsx:131` langsung ke alamat baru.

- [ ] **Step 1: Tulis test gagal**
  - `/absensi/karyawan/12?tanggal=2026-11-02` → URL `/karyawan/12?tab=absensi&tanggal=2026-11-02`, tab "Absensi" terpilih, dialog koreksi bisa dibuka (asersi lama dipindah).
  - `/karyawan/12` → tab "Profil" terpilih; tab "Akun & Sidik jari" memuat "Reset password"/"Buat akun" seperti test lama.
  - `/karyawan/12?tab=kasbon` dengan 1 kasbon → baris tampil, link "Kelola kasbon" ke `/gaji?tab=kasbon`; kosong → "Belum ada kasbon."
  - `/karyawan` dengan karyawan 12 punya akun, 13 tidak → kolom Akun "Punya akun" / "Belum".
- [ ] **Step 2:** `npm test -- EmployeeDetailPage EmployeesPage AttendancePages` → FAIL.
- [ ] **Step 3:** Implementasikan.
- [ ] **Step 4:** Test yang sama → PASS; `npm test` → semua PASS.
- [ ] **Step 5:** Laporkan.

---

### Task 6: Gaji & Laporan bertab + daftar cek periode

**Files:**
- Create: `frontend/src/pages/PayrollPage.tsx`, `frontend/src/components/PeriodChecklist.tsx`
- Modify: `ReportsPage.tsx` (lepas `PageHeader`, sisipkan checklist + peringatan di dekat tombol unduh), `LoansPage.tsx` (lepas `PageHeader`), `App.tsx`
- Test: `ReportsPage.test.tsx`, `LoansPage.test.tsx`, baru `PeriodChecklist.test.tsx`

**Interfaces:**
- Consumes: `PageTabs`, `useActiveTab` (Task 1); `useFixCounts`, `formatCount`, `FixFilter` (Task 3).
- Produces:
  - `PeriodChecklist({ filter }: { filter: FixFilter })` — judul "Sebelum unduh, bereskan dulu" + periode `formatDate(from)–formatDate(to)`. Empat baris, urutan & teks persis:
    1. `{n} hari bermasalah` → **"Bereskan"** ke `/absensi?tab=bereskan&from&to[&branchId][&categoryId]`
    2. `{n} lembur belum divalidasi` → **"Validasi"** ke `/absensi?tab=lembur&from&to[&branchId][&categoryId]`
    3. `{n} scan dari cabang lain belum ditinjau` → **"Tinjau"** ke `/absensi?tab=bereskan&from&to`
    4. `{n} scan sidik jari tak dikenal` → **"Lihat"** ke `/absensi?tab=bereskan&from&to`
    Jumlah 0 → baris hijau: "Tidak ada hari bermasalah" / "Semua lembur sudah divalidasi" / "Tidak ada scan dari cabang lain" / "Tidak ada scan sidik jari tak dikenal". Error → "Gagal memuat <bagian>" (kuning). Semua 0 tanpa error → satu baris "Semua beres — laporan siap diunduh".
  - `ReportsPage` meneruskan `from`,`to`,`branchId`,`categoryId` yang sudah dihitungnya (termasuk rentang bulan `gaji-bulanan`) ke `PeriodChecklist`; bila `total > 0` atau ada error, teks kuning di sebelah tombol unduh: `Masih ada {total} hal belum beres`. Tombol unduh tidak dinonaktifkan karena checklist.
  - `PayrollPage` (`/gaji`): `PageHeader` "Gaji & Laporan" + `PageTabs label="Sub menu gaji"` (`laporan` → `ReportsPage`, `kasbon` → `LoansPage`).
- Pengalihan: `/kasbon` → `/gaji?tab=kasbon`.

- [ ] **Step 1: Tulis test gagal**
  - `/gaji` dengan 3 review, 5 overtime belum divalidasi, 2 held, 0 unmatched → "3 hari bermasalah", "5 lembur belum divalidasi", "2 scan dari cabang lain belum ditinjau", "Tidak ada scan sidik jari tak dikenal"; link "Validasi" ke `/absensi?tab=lembur&from=<from>&to=<to>`; "Masih ada 10 hal belum beres"; tombol "Unduh Excel" tidak disabled.
  - Semua 0 → "Semua beres — laporan siap diunduh", tanpa "Masih ada".
  - held-scans membalas 500 → "Gagal memuat scan dari cabang lain", tanpa "Semua beres".
  - Laporan `gaji-bulanan` bulan 2026-10 → review-queue diminta dengan `from=2026-10-01&to=2026-10-31`.
  - `/kasbon` → URL `/gaji?tab=kasbon`; test aksi kasbon yang ada dan tutup buku di `ReportsPage.test.tsx` tetap lulus di `/gaji`.
- [ ] **Step 2:** `npm test -- ReportsPage LoansPage PeriodChecklist` → FAIL.
- [ ] **Step 3:** Implementasikan.
- [ ] **Step 4:** Test yang sama → PASS; `npm test` → semua PASS; `npm run build` → sukses.
- [ ] **Step 5:** Laporkan.

---

### Task 7: Dokumen dan pemeriksaan akhir

**Files:**
- Modify: `docs/keputusan.md` (K-145), `docs/design-system.md` §6.9, dokumen lain di `docs/` yang menjelaskan navigasi lama, spec (status → disetujui & dikerjakan)

- [ ] **Step 1:** Tambah **K-145 — Navigasi Owner/Finance berbasis pekerjaan**: 6 menu, tab `?tab=`, Pengaturan sebagai halaman kartu, daftar cek memperingatkan tidak mengunci, hitungan dari endpoint yang ada, hak akses tidak berubah; rujuk spec.
- [ ] **Step 2:** Perbarui urutan menu di `design-system.md` §6.9; `grep -rn "Scan Tertahan\|Hitung Upah\|Kasbon & Piutang\|menu Akun" docs/` dan sesuaikan yang menjelaskan navigasi saat ini (catatan sejarah dibiarkan).
- [ ] **Step 3:** `npm test` → semua PASS; `npm run lint` → tanpa error; `npm run build` → sukses.
- [ ] **Step 4:** Periksa di browser sebagai Owner dan Finance: 6 menu, kartu Pengaturan per role, satu bookmark lama per kelompok dialihkan, daftar cek di `/gaji`, tampilan HP (bottom nav 4 menu + Lainnya berisi Karyawan, Pengaturan, Profil).
- [ ] **Step 5:** Laporkan ringkasan file yang berubah, dikelompokkan per modul (untuk pembagian commit oleh pengguna sesuai CLAUDE.md).
