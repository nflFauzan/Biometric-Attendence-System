# Penyederhanaan Navigasi Owner & Finance — Desain

Tanggal: 2026-11-03 · Status: disetujui 2026-11-03, dikerjakan 2026-11-06 (K-145)

## 1. Maksud dan alasan

Sistem ini dibuat untuk menggantikan alur manual klien: menarik data mesin pakai USB, menerjemahkan laporan mesin, lalu menghitung upah/gaji dengan tangan. Ukuran berhasilnya: **laporan dan gaji keluar cepat dan angkanya benar**.

Sidebar Owner/Finance sekarang berisi **19 menu** dalam 5 kelompok yang disusun menurut cara sistem dibangun (Operasional, Master Data, Pengaturan, Sistem, Saya), bukan menurut pekerjaan pengguna. Pengguna yang gaptek tidak tahu harus mulai dari mana dan urutan apa yang perlu dibereskan sebelum gaji bisa dihitung.

Pengguna harian untuk putaran ini: **Owner** dan **Finance** (role `HR_FINANCE`). Tampilan karyawan tidak disentuh.

## 2. Temuan audit (kondisi sekarang)

| # | Temuan | Bukti |
|---|--------|-------|
| A1 | 4 menu tampil dua kali: Perlu Ditinjau, Validasi Lembur, Scan Tak Dikenal, Scan Tertahan ada di sidebar **dan** sebagai tab di Absensi Harian | `frontend/src/navigation.ts`, `components/AttendanceSubNav.tsx` |
| A2 | Menu "Hitung Upah / Gaji" sebenarnya **Simulasi Perhitungan** satu hari (tidak disimpan). Keterangannya usang ("scan asli baru tersedia di Fase 3"). Penggajian sebenarnya ada di Laporan | `pages/CalculationPreviewPage.tsx:118` |
| A3 | Ada dua halaman per karyawan: `/karyawan/:id` (profil, akun web, akses cabang, sidik jari) dan `/absensi/karyawan/:id` (absensi + koreksi) | `pages/EmployeeDetailPage.tsx`, `pages/AttendanceEmployeePage.tsx` |
| A4 | Halaman Akun tumpang tindih dengan kartu "Akun web" di detail karyawan. Akun yang benar-benar hanya bisa diurus di halaman Akun = akun Owner/Finance tanpa karyawan | `pages/UsersPage.tsx`, `EmployeeDetailPage.tsx` |
| A5 | Kasbon menu sendiri, padahal pemotongannya (tutup buku) ada di Laporan | `pages/LoansPage.tsx`, `ReportsPage.tsx:388` |
| A6 | Menu jarang dipakai (Cabang, Kategori, Mesin, Kalender Libur, Jenis Izin, Aturan Kerja, Audit Log) tampil sama menonjol dengan menu harian | `navigation.ts` |
| A7 | Kotak "Kandidat lembur" dan "Terlambat" di Dashboard mengarah ke `/absensi`, bukan ke tempat menindaklanjutinya | `pages/DashboardPage.tsx:215-216` |

## 3. Peran (diputuskan pengguna)

- **Finance** mengerjakan semua pekerjaan rutin: membereskan absensi, menyetujui izin, validasi lembur, membuat laporan/gaji, mencatat kasbon.
- **Owner** = semua yang Finance bisa + "kunci": angka upah/tarif (ada di Aturan Kerja), akun Owner/Finance, Jenis Izin, Audit Log.
- "Payroll hanya Owner" berarti **Finance dan karyawan tidak bisa mengubah angka gaji** (termasuk gaji sendiri), bukan Finance tidak boleh melihat/menyusun laporan. Hak akses backend saat ini sudah sesuai (`/api/work-rules/**` tulis = Owner saja) dan **tidak diubah**.

## 4. Tujuan, bukan tujuan, anti-tujuan

**Tujuan**
- Sidebar Owner/Finance dari 19 menu menjadi **6 menu**, sama untuk kedua peran.
- Satu jalur jelas menuju gaji: **daftar cek periode** di halaman Gaji & Laporan yang menunjukkan apa yang masih harus dibereskan, dengan tautan langsung ke tempat membereskannya.
- Satu halaman per karyawan untuk semua hal tentang orang itu.

**Bukan tujuan**
- Tidak mengubah tampilan/menu karyawan (`EMPLOYEE`).
- Tidak mengubah aturan bisnis, perhitungan, format laporan AS-1..AS-7, atau hak akses backend.
- Tidak mengubah schema database.

**Anti-tujuan (gagal walau "jalan")**
- Ada fitur yang hilang atau tidak bisa ditemukan lagi setelah digabung.
- Tautan/bookmark lama mati (harus dialihkan).
- Finance jadi bisa mengubah upah/tarif.
- Daftar cek memblokir unduhan sehingga laporan tidak bisa dibuat saat ada data yang memang dibiarkan (daftar cek **memperingatkan, tidak mengunci**).

## 5. Struktur baru

### 5.1 Sidebar (Owner & Finance)

| Menu | Isi | Menggantikan |
|------|-----|--------------|
| **Beranda** | Dashboard sekarang; kotak & "Perlu perhatian" mengarah ke tab yang tepat | Dashboard |
| **Absensi** | Tab: **Harian** · **Perlu Dibereskan** · **Lembur** | Absensi Harian, Perlu Ditinjau, Scan Tak Dikenal, Scan Tertahan, Validasi Lembur |
| **Izin & Cuti** | tidak berubah | Izin & Cuti |
| **Gaji & Laporan** | Tab: **Laporan** (daftar cek + laporan + unduh + tutup buku kasbon) · **Kasbon** | Laporan, Kasbon & Piutang |
| **Karyawan** | Daftar + detail bertab (lihat 5.3) | Karyawan, Akun (bagian karyawan) |
| **Pengaturan** | Halaman berisi kartu (lihat 5.4) | Cabang, Kategori, Mesin, Akun (Owner/Finance), Aturan Kerja, Kalender Libur, Jenis Izin, Simulasi, Audit Log |

Profil, Ganti password, dan Keluar tetap di menu pengguna di header (Profil dipindah dari sidebar ke header).

Di HP (bottom navigation): Beranda, Absensi, Izin & Cuti, Gaji & Laporan + "Lainnya" (Karyawan, Pengaturan, Profil).

### 5.2 Absensi — tab "Perlu Dibereskan"

Satu tab berisi tiga bagian bertumpuk, masing-masing dengan jumlah, dan bagian kosong ditampilkan ringkas sebagai "beres":
1. **Hari bermasalah** (isi Perlu Ditinjau sekarang)
2. **Scan dari cabang lain** (isi Scan Tertahan sekarang)
3. **Scan sidik jari tak dikenal** (isi Scan Tak Dikenal sekarang)

Isi tiap bagian = komponen halaman yang ada sekarang (dipakai ulang, bukan ditulis ulang). Badge jumlah total tampil di tab.

### 5.3 Karyawan — detail bertab

`/karyawan/:id` dengan tab: **Profil** (data kepegawaian + akses cabang) · **Absensi** (isi `AttendanceEmployeePage`: rekap & koreksi) · **Akun & Sidik jari** (kartu akun web + sidik jari) · **Kasbon** (pinjaman karyawan ini, dari `GET /api/loans?employeeId=`).

Daftar karyawan menampilkan kolom status akun (punya akun / belum) agar halaman Akun tidak dibutuhkan untuk karyawan.

### 5.4 Pengaturan — halaman kartu

Satu halaman `/pengaturan` berisi kartu bergrup; tiap kartu membuka halaman yang sudah ada (halaman lama tetap, hanya pintunya berubah):
- **Perusahaan:** Cabang · Kategori Karyawan · Mesin Absensi
- **Aturan & gaji:** Aturan Kerja (upah/tarif; Finance hanya lihat) · Kalender Libur · Jenis Izin/Cuti (Owner) · Simulasi Perhitungan
- **Akses & keamanan:** Akun (Owner & Finance; Finance tetap hanya melihat/mengelola akun karyawan seperti sekarang, K-135) · Audit Log (Owner)

Kartu yang tidak boleh diakses role pengguna tidak ditampilkan. Hak akses tiap halaman **sama persis dengan sekarang**: hanya Jenis Izin dan Audit Log yang khusus Owner (sudah dijaga `RequireRole`). Finance tidak kehilangan akses ke Cabang, Kategori, Mesin, maupun Akun. "Hitung Upah / Gaji" diganti nama menjadi **Simulasi Perhitungan** dan keterangan usangnya diperbaiki.

### 5.5 Gaji & Laporan — daftar cek periode

Di atas pilihan laporan, setelah pengguna memilih periode (dan cabang/kategori bila ada):

> **Sebelum unduh, bereskan dulu:**
> ⚠ 3 hari bermasalah → *Bereskan* · ⚠ 5 lembur belum divalidasi → *Validasi* · ⚠ 2 scan cabang lain belum ditinjau → *Tinjau* · ⚠ 1 scan tak dikenal → *Lihat*
> ✓ Semua beres — laporan siap diunduh

- Hitungan diambil dari endpoint yang **sudah ada** dan semuanya menerima `from`/`to`: `GET /api/attendance/review-queue`, `GET /api/overtime` (status belum divalidasi), `GET /api/attendance/held-scans?status=BELUM_DITINJAU`, `GET /api/attendance/unmatched-scans`. **Tidak perlu endpoint baru.**
- Tiap baris menautkan ke tab yang tepat **dengan periode yang sama** (parameter tanggal di URL).
- Tombol unduh tetap aktif; bila daftar belum beres, tampil peringatan kuning di dekat tombol.

## 6. Pengalihan alamat lama

| Lama | Baru |
|------|------|
| `/absensi/perlu-ditinjau`, `/absensi/scan-tertahan`, `/absensi/scan-tak-dikenal` | `/absensi?tab=bereskan` |
| `/absensi/lembur` | `/absensi?tab=lembur` |
| `/absensi/karyawan/:id` | `/karyawan/:id?tab=absensi` |
| `/laporan` | `/gaji` (tab Laporan) |
| `/kasbon` | `/gaji?tab=kasbon` |
| `/akun` | `/pengaturan/akun` |
| `/cabang`, `/kategori`, `/mesin`, `/aturan-kerja`, `/kalender-libur`, `/jenis-izin`, `/simulasi`, `/audit-log` | `/pengaturan/<nama>` |

Tautan dari notifikasi (lonceng izin, notifikasi mesin) dan dari PDF/QR harus tetap berfungsi.

## 7. Batasan

- Frontend saja (React + Vite + TS); komponen shadcn tidak diedit; panggilan API lewat `src/lib/api.ts`.
- Backend & `SecurityConfig` tidak berubah, jadi `AccessControlTests` tidak berubah.
- Test frontend yang ada (`*.test.tsx`) disesuaikan dengan path/label baru; ditambah test untuk daftar cek, pengalihan alamat, dan penyaringan kartu Pengaturan per role.
- Catat keputusan baru di `docs/keputusan.md` (K-145) dan perbarui `docs/design-system.md` §6.9 (urutan menu).

## 8. Keputusan rinci (diputuskan perancang setelah pemeriksaan pembuat)

Ditandai sebagai keputusan perancang, bukan keputusan pengguna; boleh dikoreksi saat membaca.

1. **Periode lewat URL.** Halaman Perlu Ditinjau, Scan Tertahan, Scan Tak Dikenal, dan Lembur sekarang menyimpan `from`/`to` di state lokal. Keempatnya diubah sedikit agar membaca nilai awal dari query string (`?from=&to=&branchId=&categoryId=`) dan menulisnya kembali saat filter berubah. Perubahan ini masuk lingkup.
2. **Filter tab "Perlu Dibereskan".** Satu filter bersama di atas tab (tanggal dari–sampai + cabang). Bawaan: tanggal 1 bulan berjalan sampai hari ini, sama dengan bawaan review-queue/lembur sekarang. Cabang hanya disaring di bagian yang endpoint-nya mendukung (`review-queue`); bagian scan tertahan/tak dikenal mengabaikan filter cabang dan menuliskan "semua cabang".
3. **Badge jumlah.** Badge tab Perlu Dibereskan = jumlah ketiga bagian dengan rentang bawaan di atas, dimuat hanya saat halaman Absensi dibuka (3 query ringan, data ±25 karyawan). Badge tab Lembur = jumlah `status = BELUM_DIVALIDASI` dari `GET /api/overtime` (status dihitung server, K-140; disaring di frontend).
4. **Daftar cek.** Memakai `BELUM_DIVALIDASI` untuk lembur dan `status=BELUM_DITINJAU` untuk scan tertahan. Cabang/kategori diteruskan ke endpoint yang mendukung (`review-queue`, `overtime`); untuk scan, keterangan "semua cabang".
5. **Sub-halaman Pengaturan.** Sidebar menyorot "Pengaturan" di semua `/pengaturan/*`; judul header berupa jejak "Pengaturan / Cabang"; tiap sub-halaman diberi tautan "← Pengaturan". `menuItemFor` diperluas dengan daftar sub-halaman (bukan item sidebar) untuk judul.
6. **Kolom status akun di daftar karyawan.** Dihitung di frontend dengan mencocokkan `GET /api/users` (satu panggilan, sudah dipakai di detail karyawan) dengan `employeeId`. Tanpa perubahan backend.
7. **Membuat akun Owner.** Tidak berubah: pilihan role di form akun sudah dibatasi per role (Finance hanya bisa membuat akun Karyawan).
8. **HP.** Profil tetap ada di "Lainnya" di HP selain di menu header.
9. **Pengalihan.** Query string lama ikut dibawa (mis. rentang tanggal di `/absensi/karyawan/:id`). Pengalihan berada di dalam `RequireAuth`, jadi pengguna yang belum login tetap ke `/login?next=` dulu lalu kembali ke alamat baru.

## 9. Diserahkan ke pembuat

- Bentuk visual tab, kartu, dan daftar cek (ikuti `docs/design-system.md`, tema kaca yang ada).
- Apakah tab memakai query string (`?tab=`) atau sub-path, asalkan pengalihan di §6 terpenuhi dan tombol Kembali browser wajar.
- Urutan pengerjaan (disarankan: sidebar + pengalihan → Absensi → Karyawan → Pengaturan → Gaji & daftar cek).
