# Laporan Absensi dan Ekspor Excel/PDF — Rencana Implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Owner dan Finance & Accounting bisa melihat dan mengunduh tiga laporan berbasis data absensi, dalam format Excel dan PDF:
- Rekap Absensi;
- Rekap Lembur Tervalidasi;
- Detail Absensi (jam scan).

Ketiganya bisa difilter per periode, cabang, kategori, dan karyawan. Laporan upah, gaji, dan pengajuan dana **tidak** termasuk di sini; itu Fase 5 (K-105).

**Architecture:** Paket modul baru `report`:
- `ReportService` menyusun model laporan dari `AttendanceService.range(...)` dan `OvertimeService.validatedSeconds(...)`.
- `ExcelReportWriter` (Apache POI) dan `PdfReportWriter` (OpenPDF, sudah dipakai untuk surat izin) menulis model yang sama ke file.
- `ReportController` menyediakan pratinjau JSON dan unduhan file. Setiap unduhan dicatat di audit log.

**Tech Stack:** Spring Boot 4 / Java 21; Apache POI `poi-ooxml` 5.4.1 (dependency baru; pernah dipakai lalu dihapus di K-130); OpenPDF 2.4.0 (sudah ada); font Poppins/Open Sans di `backend/src/main/resources/fonts`; React + Vite + TypeScript + Vitest.

**Spec:** requirement HLX-TKS-ATT-REQ-001 v0.6 §6.6:
- **FR-RPT-001–003 (Must):** "Rekap harian absensi; rekap jam kerja dan hari kerja; rekap lembur per periode."
- **FR-RPT-004 (Should):** "Laporan absensi supir/kenek berbasis jam kerja per minggu."
- **FR-RPT-005 (Must):** "Kolom minimum: tanggal, hari, nama, kategori, jam masuk, jam keluar, counting hours, counting days."
- **FR-RPT-006 (Must):** "Ekspor Excel dan PDF oleh HR & Finance dan Owner."
- **FR-RPT-007 (Should):** "Filter per periode, cabang, kategori, dan karyawan."
- **FR-REV-003:** hanya lembur tervalidasi yang masuk laporan.

Format laporan mengikuti K-105 dan `docs/dokumen-as-is.md`:
- AS-4: detail jam scan;
- AS-1 dan AS-5: kisi kehadiran;
- AS-2 dan AS-6: rekap lembur.

**Urutan antarrencana:** rencana ini dikerjakan **terakhir**. Rencana `2026-10-27-review-anomali-validasi-lembur.md` harus selesai dulu, karena rencana ini memakai `AttendanceService.range`, `EmployeeDays`, dan `OvertimeService.validatedSeconds`.

## Konfirmasi di awal sesi (tanyakan sebelum Task 1)

1. **Definisi "counting days" dan "counting hours" (FR-RPT-005).** Ini aturan bisnis yang belum tercatat. Usulan bawaan:
   - **counting days**: jumlah hari berstatus `LENGKAP`.
     - Hari `TIDAK_LENGKAP` baru dihitung setelah dikoreksi.
     - Hari `IZIN` tidak dihitung, tetapi ditampilkan di kolom tersendiri ("Izin").
   - **counting hours**: jumlah `regularWorkSeconds` (jam kerja normal, istirahat sudah dipotong).
   - Lembur tervalidasi ditampilkan di kolom terpisah dan tidak ditambahkan ke counting hours.

   Kalau pengguna belum bisa memutuskan, catat pertanyaan baru di `docs/pertanyaan-terbuka.md`, pakai usulan ini sementara, dan beri label kolom yang jelas ("Hari lengkap", "Jam kerja normal").
2. **Library Excel: Apache POI `poi-ooxml` 5.4.1.** Menambah dependency berarti perubahan arsitektur, jadi perlu persetujuan. POI disebut di PRD dan K-105, dan versi ini pernah lulus test di repo ini.
3. **Tata letak.** Mendekati foto AS-IS, tetapi belum persis, karena file Excel asli dari client belum ada (K-105). Isi sel kisi kehadiran bawaan:

   | Status | Isi sel |
   |---|---|
   | `LENGKAP` | `V` |
   | `TIDAK_LENGKAP` | `?` |
   | `IZIN` | `I` |
   | `TIDAK_HADIR` | kosong, isian merah |
   | `TIDAK_ADA_SCAN` (bukan hari kerja atau libur) | kosong, isian abu-abu |

4. **Laporan mingguan supir/kenek (FR-RPT-004).** Cukup dengan filter kategori dan rentang tanggal bebas. Tidak ada tombol "minggu Sabtu–Jumat", karena periode mingguan masih terbuka (Q-006) dan tidak boleh ditanam di kode.

## Global Constraints

- Ikuti CLAUDE.md:
  - aturan bisnis tidak di-hardcode;
  - setiap endpoint baru masuk `AccessControlTests`;
  - error memakai ProblemDetail dengan pesan Bahasa Indonesia.
- Setiap unduhan dicatat lewat `AuditService.record("EXPORT", "REPORT", <slug>, null, <filter>)` (K-105, AUD-009). Pratinjau JSON tidak dicatat.
- Data laporan **hanya** diambil dari `AttendanceService.range(...)` dan `OvertimeService.validatedSeconds(...)`. Jam kerja dan lembur tidak dihitung ulang di modul `report`.
- Durasi ditampilkan sebagai jam desimal dengan 2 angka di belakang koma (`BigDecimal`, `RoundingMode.HALF_UP`), misalnya 7,50. Ini hanya format tampilan, bukan aturan pembulatan.
- Nama hari Bahasa Indonesia diambil dari array tetap (Senin…Minggu), bukan dari locale JVM. Format tanggal `dd-MM-yyyy`.
- Rentang maksimal 62 hari, mengikuti `AttendanceService.requireRange`.
- Karyawan yang nonaktif di dalam rentang tetap ikut dilaporkan (FR-EMP-002), sesuai perilaku `range(...)`.
- **Commit** mengikuti "Aturan Git Commit" di CLAUDE.md, tetapi **hanya bila pengguna meminta**.
- Test backend dijalankan dari salinan `backend` di scratchpad. Jangan matikan `spring-boot:run` milik pengguna.
- Jangan pernah melaporkan test lulus tanpa benar-benar menjalankannya.
- Teks di layar dan di dalam file memakai "Finance & Accounting" (K-132).

## Review Focus

1. **Periode tanpa data sama sekali.** Excel dan PDF tetap terbentuk dengan judul, filter, dan teks "Tidak ada data". Tidak boleh error 500 atau menghasilkan file rusak. Diuji di Task 3 dan 4.
2. **Nama karyawan dengan karakter khusus** (`'`, `&`, huruf beraksen). Tidak ada karakter yang hilang di PDF (font di-embed), dan Excel tetap valid. Diuji di Task 3 dan 4.
3. **Lembur berstatus `PERLU_VALIDASI_ULANG` atau `BELUM_DIVALIDASI`.** Tidak ikut dijumlahkan. Diuji di Task 2.
4. **Rentang 62 hari untuk sekitar 25 karyawan.** Kisi tanggal di PDF dipecah ke beberapa halaman tanpa ada tanggal yang hilang. Diuji di Task 4.
5. **Nama file unduhan.** Hanya berisi slug dan tanggal, misalnya `rekap-absensi_2026-10-01_2026-10-31.xlsx`. Nama cabang yang mengandung spasi atau garis miring tidak ikut dimasukkan. Diuji di Task 5.

---

### Task 1: Dependency dan model laporan

**Files:**
- Modify: `backend/pom.xml` (properti `poi.version` = 5.4.1 dan dependency `org.apache.poi:poi-ooxml`)
- Create: `backend/src/main/java/com/biometricattendance/report/ReportType.java`, `ReportDtos.java`

**Interfaces:**
- Produces: `enum ReportType { REKAP_ABSENSI, REKAP_LEMBUR, DETAIL_ABSENSI }`.
  - Slug masing-masing: `rekap-absensi`, `rekap-lembur`, `detail-absensi`.
  - Judul Bahasa Indonesia ada di enum.
  - Sediakan `static Optional<ReportType> fromSlug(String)`.
- Produces: record berikut.
  - `record ReportFilter(LocalDate from, LocalDate to, Long branchId, Long categoryId, Long employeeId)`
  - `record ReportModel(ReportType type, String title, String periodLabel, List<String> filterLabels, List<LocalDate> dates, List<ReportEmployee> employees)`
  - `record ReportEmployee(String employeeNumber, String fullName, String branchName, String categoryName, List<ReportDay> days, ReportTotals totals)`
  - `record ReportDay(LocalDate date, String dayName, AttendanceStatus status, LocalTime checkIn, LocalTime checkOut, BigDecimal regularHours, BigDecimal validatedOvertimeHours, List<LocalTime> scanTimes, String mark)`; `mark` diisi sesuai Konfirmasi no. 3.
  - `record ReportTotals(int countingDays, int leaveDays, int absentDays, BigDecimal countingHours, BigDecimal validatedOvertimeHours)`

- [ ] **Step 1:** Tambahkan dependency, lalu jalankan `./mvnw -q -DskipTests package` di salinan → BUILD SUCCESS.
- [ ] **Step 2:** Tulis enum dan record. Keduanya diuji lewat Task 2.

---

### Task 2: `ReportService` menyusun model

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/report/ReportService.java`
- Test: `backend/src/test/java/com/biometricattendance/report/ReportServiceTests.java` (integrasi, memakai `AttendanceFixtures` dan data sintetis)

**Interfaces:**
- Consumes:
  - `AttendanceService.range(from, to, branchId, categoryId, employeeId): List<EmployeeDays>`
  - `OvertimeService.validatedSeconds(List<EmployeeDays>): Map<Long, Map<LocalDate, Long>>`
- Produces: `ReportModel ReportService.build(ReportType type, ReportFilter filter)` (`@Transactional(readOnly = true)`).
  - `filterLabels` berisi nama cabang, kategori, dan karyawan yang dipilih. Kalau tidak difilter, isinya "Semua cabang", "Semua kategori", dan "Semua karyawan".
  - `periodLabel` berformat `"01-10-2026 s.d. 31-10-2026"`.

- [ ] **Step 1: Tulis test yang gagal**

- `countingDaysCountsOnlyCompleteDays`: 3 hari `LENGKAP`, 1 `TIDAK_LENGKAP`, dan 1 `IZIN` → `countingDays = 3`, `leaveDays = 1`.
- `countingHoursSumsRegularWorkOnly`: aturan uji 08–17 dengan istirahat 12–13; satu hari scan 08:00–17:00 dan satu hari 09:00–17:00 → `countingHours = 15.00`.
- `onlyValidatedOvertimeIsIncluded`: satu hari lembur tervalidasi 2 jam dan satu hari lembur yang belum divalidasi → `validatedOvertimeHours = 2.00`.
- `revalidationNeededOvertimeIsExcluded`.
- `filtersAreApplied`: `filterLabels` berisi nama kategori.
- `emptyResultStillHasHeader`.

- [ ] **Step 2: Jalankan test dan pastikan gagal.**
- [ ] **Step 3: Implementasi.** `mark` dan nama hari ditentukan di sini, supaya kedua writer tidak punya logika sendiri.
- [ ] **Step 4:** Jalankan `./mvnw -q test -Dtest=ReportServiceTests` → PASS.

---

### Task 3: `ExcelReportWriter`

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/report/ExcelReportWriter.java`
- Test: `backend/src/test/java/com/biometricattendance/report/ExcelReportWriterTests.java` (unit; model dibuat langsung)

**Interfaces:**
- Produces: `byte[] ExcelReportWriter.write(ReportModel model)`.

Isi workbook:
- **REKAP_ABSENSI**
  - Sheet `Rekap`: diawali baris judul, periode, dan filter. Setiap karyawan satu baris dengan kolom:
    - No. karyawan, Nama, Kategori, Cabang;
    - satu kolom per tanggal berisi `mark`, dengan warna isian sesuai Konfirmasi no. 3;
    - Hari lengkap, Izin, Tidak hadir, Jam kerja normal, Lembur tervalidasi.
  - Sheet `Detail`: satu baris per karyawan per tanggal, dengan kolom FR-RPT-005: Tanggal, Hari, Nama, Kategori, Jam masuk, Jam keluar, Jam kerja normal, Lembur tervalidasi, Status.
- **REKAP_LEMBUR**: setiap karyawan satu baris dengan satu kolom per tanggal berisi jam lembur tervalidasi (dikosongkan bila 0), ditambah kolom Total lembur. Tanpa nilai rupiah.
- **DETAIL_ABSENSI** (gaya AS-4): setiap karyawan satu baris dengan satu kolom per tanggal berisi jam-jam scan hari itu, dipisahkan baris baru (`wrapText`).

Ketentuan umum: angka jam ditulis sebagai sel numerik berformat `0.00`, baris judul di-freeze, dan lebar kolom tetap.

- [ ] **Step 1: Tulis test yang gagal** (baca ulang hasil dengan `XSSFWorkbook`)

- `rekapHasRekapAndDetailSheets`
- `markCellsMatchStatus` (termasuk warna isian untuk `TIDAK_HADIR`)
- `hoursAreNumericCells` (nilai 7.5 dengan tipe `NUMERIC`)
- `emptyModelProducesValidWorkbookWithNoDataText`
- `specialCharactersInNamesSurvive` (`"Siti 'Ani' & Rekan Ä"`)

- [ ] **Step 2: Jalankan test dan pastikan gagal.**
- [ ] **Step 3: Implementasi.**
- [ ] **Step 4:** Jalankan `./mvnw -q test -Dtest=ExcelReportWriterTests` → PASS.

---

### Task 4: `PdfReportWriter`

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/report/PdfReportWriter.java`
- Test: `backend/src/test/java/com/biometricattendance/report/PdfReportWriterTests.java`

**Interfaces:**
- Produces: `byte[] PdfReportWriter.write(ReportModel model)`.
  - Kertas A4 landscape, dengan font yang di-embed. Cara memuat font sama dengan `leave/letter/LeaveLetterRenderer`.
  - Kepala halaman: judul, periode, dan filter. Kaki halaman: nomor halaman dan waktu cetak dalam zona waktu perusahaan.
  - Isinya sama dengan sheet utama Excel. Sheet `Detail` dari Rekap Absensi tidak dimasukkan ke PDF.
  - Kalau kolom tanggal melebihi 31, kisi dipecah ke halaman berikutnya.

- [ ] **Step 1: Tulis test yang gagal** (ekstrak teks dengan `PdfTextExtractor` dari OpenPDF; ikuti pola `LeaveLetterRendererTests`)

- `pdfContainsTitlePeriodAndEmployeeNames`
- `emptyModelProducesPdfWithNoDataText`
- `specialCharactersRenderWithEmbeddedFont`
- `sixtyTwoDayRangeSplitsAcrossPages` (lebih dari 1 halaman, dan tanggal terakhir tetap muncul)

- [ ] **Step 2: Jalankan test dan pastikan gagal.**
- [ ] **Step 3: Implementasi.**
- [ ] **Step 4:** Jalankan `./mvnw -q test -Dtest=PdfReportWriterTests` → PASS.

---

### Task 5: `ReportController`, hak akses, dan audit

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/report/ReportController.java`
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java` (`/api/reports/**` untuk Owner dan Finance & Accounting)
- Test: `backend/src/test/java/com/biometricattendance/report/ReportApiTests.java`, `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java`

**Interfaces:**
- Produces: `GET /api/reports/{slug}?from=&to=&branchId=&categoryId=&employeeId=` → `ReportModel` (JSON pratinjau).
- Produces: `GET /api/reports/{slug}.xlsx?...` dan `GET /api/reports/{slug}.pdf?...` → `ResponseEntity<byte[]>`.
  - Header `Content-Disposition: attachment; filename="<slug>_<from>_<to>.<ext>"`, mengikuti pola `LeaveController.pdfResponse`.
- Slug yang tidak dikenal → 404 `"Jenis laporan tidak dikenal"`.
- `from` atau `to` kosong → 400 `"Periode wajib diisi"`.

- [ ] **Step 1: Tulis test yang gagal**

- `previewReturnsModelJson`
- `xlsxDownloadHasHeadersAndIsAudited` (satu audit `EXPORT` untuk entitas `REPORT`)
- `pdfDownloadIsAudited`
- `previewIsNotAudited`
- `unknownSlugIs404`, `missingPeriodIs400`, `tooLongRangeIs400`
- `AccessControlTests`:
  - karyawan → 403 untuk ketiga bentuk endpoint;
  - anonim → 401;
  - Owner dan Finance & Accounting → bukan 403.

- [ ] **Step 2: Jalankan test dan pastikan gagal.**
- [ ] **Step 3: Implementasi.**
- [ ] **Step 4:** Jalankan `./mvnw -q test -Dtest='Report*Tests,AccessControlTests'` → PASS.

---

### Task 6: Halaman Laporan (frontend)

**Files:**
- Create: `frontend/src/pages/ReportsPage.tsx`, `frontend/src/pages/ReportsPage.test.tsx`
- Modify: `frontend/src/App.tsx` (route `laporan`, khusus manajer), `frontend/src/navigation.ts`, `frontend/src/navigation.test.ts`, `frontend/src/lib/types.ts`
- Create bila belum ada helper umum: `frontend/src/lib/download.ts` berisi `saveBlob(blob: Blob, filename: string): void`. Ekstrak dari pola yang sudah ada di `lib/leaveLetter.ts`.

**Interfaces:**
- Consumes: endpoint dari Task 5 dan `apiBlob(path)` dari `lib/api.ts`.
- Menu: `{ path: '/laporan', label: 'Laporan', roles: MANAGERS, icon: FileSpreadsheet, group: 'Operasional' }`.

Isi halaman:
- pilihan jenis laporan;
- filter periode memakai pemilih tanggal standar (K-136), cabang, kategori, dan karyawan;
- tabel pratinjau dari JSON;
- tombol **Unduh Excel** dan **Unduh PDF**.

- [ ] **Step 1: Tulis test yang gagal**

- Pratinjau memanggil `GET /api/reports/rekap-absensi?from=...&to=...&categoryId=3`.
- Tombol unduh memanggil `.xlsx`/`.pdf` dengan query yang sama, lalu menjalankan `saveBlob` (di-mock).
- Data kosong menampilkan `"Tidak ada data pada periode ini."`.
- Pesan error 400 dari server ditampilkan.
- `navigation.test.ts`: Owner dan Finance & Accounting melihat menu Laporan, karyawan tidak.

- [ ] **Step 2: Jalankan test dan pastikan gagal.**
- [ ] **Step 3: Implementasi.**
- [ ] **Step 4:** Jalankan `npx vitest run`, `npx tsc -b`, dan `npm run lint` → PASS.

---

### Task 7: Dokumen dan verifikasi akhir

- [ ] `docs/keputusan.md`: tambahkan keputusan **Laporan absensi dan ekspor**, berisi:
  - tiga jenis laporan;
  - Apache POI 5.4.1 dan OpenPDF;
  - definisi counting days dan counting hours (atau rujukan ke pertanyaan terbuka bila belum dikonfirmasi);
  - audit `EXPORT`;
  - catatan bahwa tata letak masih menunggu file asli client.

  Perbarui juga butir library di bagian "Yang belum diputuskan" pada K-105.
- [ ] `docs/pertanyaan-terbuka.md`: tambahkan pertanyaan counting days/hours bila belum terjawab, dan catat bahwa file Excel asli AS-IS masih dibutuhkan.
- [ ] Jalankan suite backend penuh dari salinan (bila kehabisan memori, per paket), lalu `npx vitest run`, `npx tsc -b`, dan `npm run lint`.
- [ ] Perbarui dokumen review gap (Claude Docs):
  - bagian 1: ubah item laporan dari Belum menjadi Sudah, kecuali laporan upah dan gaji (tetap Fase 5);
  - bagian 3A: tandai FR-RPT-001–003 dan 005–007 selesai, dan FR-RPT-004 tercakup lewat filter.
- [ ] Laporkan ke pengguna: file yang berubah, angka hasil test yang sebenarnya, dan hal yang belum dikerjakan (laporan upah/gaji dan tata letak persis).
