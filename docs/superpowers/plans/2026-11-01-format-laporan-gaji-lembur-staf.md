# Format Laporan Gaji dan Rekap Lembur Staf (AS-5, AS-6) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lembar `Gaji Staf` (AS-5) dan `Lembur Staf` (AS-6) pada laporan `gaji-bulanan`, baik Excel maupun PDF, tampil seperti dua foto format klien: kop, warna, kisi tanggal 1–31, kolom, format angka, dan tanda tangan.

**Architecture:** `ReportService` menambah data kop dan tanggal libur ke `ReportModel`. Dua renderer baru (`TakkaExcelSheets`, `TakkaPdfPages`) memakai satu sumber warna dan format (`TakkaFormat`), menggantikan renderer AS-5/AS-6 lama. Nama manager dan departemen disimpan di tabel baru `report_settings`, lalu diisi lewat form kecil di halaman Laporan.

**Tech Stack:** Spring Boot 4, Java 21, Flyway, Apache POI 5.4.1, OpenPDF 2.4.0, React + Vite + TypeScript.

**Spec:** dua foto di `Downloads/foto-meeting-08-10-2026` (`format laporan rekap lembur karyawan office.jpeg`, `laporan gaji karyawan office lengkap ... .jpeg`), `docs/dokumen-as-is.md` (AS-5, AS-6), dan bagian Spesifikasi Format di bawah. Foto berisi nama dan gaji asli, jadi jangan disalin ke repo.

## Global Constraints

- Tidak ada aturan bisnis yang di-hardcode. Tarif, upah, gaji pokok, dan hari kerja dibaca dari aturan yang berlaku. Fallback `100000`, `14000`, dan `40000` yang ada sekarang dihapus.
- Uang memakai `BigDecimal`. Perubahan schema hanya lewat migration `V20`.
- Perubahan pengaturan dicatat lewat `AuditService` di transaksi yang sama. Endpoint baru masuk ke `security/AccessControlTests`. Error berformat ProblemDetail dalam Bahasa Indonesia.
- Rumus gaji tidak berubah: `Total Wages = Gaji Pokok + Onsite + Upah Lembur − Input Piutang`.
- Fixture test memakai nama samaran, misalnya `STAF SATU`.
- Hak akses laporan tidak berubah (`/api/reports/**` untuk Owner dan Finance & Accounting).
- Di luar lingkup: AS-1, AS-2, AS-7, dan tiga laporan presensi. Admin bank `2500` yang masih hardcode di AS-7 tercatat sebagai utang tersendiri.
- Pengguna melakukan commit sendiri, jadi eksekutor tidak commit. Test backend dijalankan dari salinan terisolasi agar `spring-boot:run` pengguna tidak terganggu.

## Spesifikasi Format

Warna diperkirakan dari foto yang redup dan dinormalkan terhadap warna kertas, jadi semuanya ditaruh sebagai konstanta di `TakkaFormat` agar mudah diganti.

| Elemen | AS-5 `Gaji Staf` | AS-6 `Lembur Staf` |
|---|---|---|
| Pita judul | Biru `#8EA9DB` selebar tabel, teks putih tebal di tengah: `TAKKA STEEL` | Sama, teks `REKAP LEMBUR TAKKA STEEL` |
| Kop kiri | `Manager Name:` dan `Department:` tebal, nilainya biasa | Sama |
| Kop kanan | `Month:` tebal, nilai `September 2026` tebal berlatar emas `#FFE699` | Sama |
| Kolom awal | Nomor tanpa judul, `Employee Name`, pemisah sempit `#E2B96F` tanpa isi | Nomor tanpa judul, `NAMA KARYAWAN`, pemisah sama |
| Kisi tanggal | Selalu 31 kolom, judul `1`–`31` tebal tanpa nol di depan dan tanpa nama hari | Sama |
| Kolom akhir | `Total Days`, `Wages`, `UPAH LEMBUR`, `INPUT PIUTANG`, `ONSITE`, `GAJI POKOK`, `Total Wages` | `TOTAL LEMBUR`, `UANG LEMBUR`, `JUMLAH UPAH` |
| Baris akhir | Baris kosong berisi total Total Wages, lalu baris tinggi `TOTAL` dengan jumlah hari berlatar `#FFD966` di kolom Total Days | Tidak ada baris total |
| Tanda tangan | `Director Signature:____`, lalu `Manager Signature:____` | `Director Signature:____` saja |

Isi sel:

- **Nama:** huruf besar. Nomor baris berurutan 1, 2, 3, dan seterusnya.
- **Merah `#E59191`:**
  - Seluruh kolom hari libur, termasuk judulnya, dan tanggal yang tidak ada di bulan itu (31 September).
  - Di AS-5, juga setiap hari tanpa V.
- **Kisi AS-5:**
  - `LENGKAP` berisi `V` berlatar putih, termasuk bila masuk di hari libur.
  - `TIDAK_LENGKAP` berisi `?` berlatar putih, agar scan yang belum beres tidak terlihat seperti alpa.
  - Izin, tidak hadir, tidak ada scan, dan di luar masa kerja berupa sel merah kosong.
- **Kisi AS-6:** kosong bila 0. Bila ada, tampil tanpa nol berlebih dengan koma: `4`, `1,5`, `3,5`, `1,33`. Warna kolom libur tetap merah walau ada isinya.
- **Baris AS-6:** hanya karyawan yang punya `overtimeRate`. Karyawan dengan lembur 0 jam tetap tampil.

Format angka:

| Kolom | Tampilan | Format Excel |
|---|---|---|
| `Total Days`, `TOTAL LEMBUR` | `26`, `30,5` | `General` |
| `Wages`, `UPAH LEMBUR`, `UANG LEMBUR` | `40.000`; lembur nol tampil `0` | `#,##0` |
| `INPUT PIUTANG` | `Rp 500.000`; kosong bila 0 | `_("Rp"* #,##0_);;;` |
| `ONSITE`, `GAJI POKOK` | `Rp` rata kiri, angka rata kanan; kosong bila tanpa gaji pokok | `_("Rp"* #,##0_)` |
| `Total Wages`, `JUMLAH UPAH` | Sama; `JUMLAH UPAH` nol tampil `Rp -` | `_("Rp"* #,##0_);;_("Rp"* "-"_)` |

Keputusan penyusun yang perlu dicek:

- **Font:** Verdana di Excel, diperkirakan dari bentuk huruf di foto. Verdana tidak boleh di-embed, jadi PDF memakai DejaVu Sans yang bentuknya paling dekat.
- **Pemisah ribuan di Excel:** mengikuti setelan regional komputer. Titik muncul pada setelan Indonesia. PDF selalu memakai titik.
- **Tulisan tangan dan cacat foto tidak ditiru:** `2.500` tulisan tangan di Input Piutang, nomor baris 10 yang terlewat (kemungkinan baris tersembunyi), dan angka `182` di halaman AS-6.
- **Urutan baris:** menurut nomor karyawan. Urutan di foto bukan alfabet dan berbeda antara AS-5 dan AS-6.
- **Bulan:** nama Indonesia (`Oktober 2026`).
- **Libur:** kolom merah adalah tanggal di luar `workDays` semua karyawan dalam laporan, ditambah hari libur di menu Hari Libur.
- **Posisi total Total Wages:** diperkirakan, karena foto melengkung di bagian itu.
- **Cetak:** A4 landscape, muat satu halaman lebar, gridline disembunyikan.

## Yang Terblokir

**Q-051 (memperluas Q-044): baris tanpa gaji pokok.** Tiga baris di AS-5 tidak cocok dengan rumus yang ada: `26 × 140.000` tertulis `3.500.000`, `26 × 180.000` tertulis `4.500.000`, dan `26 × 160.000` tertulis `4.000.000`. Ketiganya sama dengan `25 × Wages`, atau mungkin gaji tetap bulanan. Satu baris juga memuat piutang `500.000` yang tampaknya tidak mengurangi total. Rumusnya tidak ditebak: sampai klien menjawab, baris seperti ini tetap dihitung `Hari × Wages`, sehingga angkanya bisa berbeda dari foto. Task 1 mencatat pertanyaan ini.

**Q-050: file `.xlsx` asli.** Warna, font, dan lebar kolom diperkirakan dari foto. Bila file asli diterima, konstanta `TakkaFormat` disesuaikan dengan file itu.

## Review Focus

1. Februari dan bulan 30 hari: tanggal yang tidak ada tetap merah dan jumlah kolom tetap 31.
2. Lembur di hari libur: angkanya terbaca di sel merah dan ikut terjumlah.
3. Lembur bukan kelipatan 0,5 (K-140, tanpa pembulatan): tampil `1,33`, tidak dibulatkan diam-diam.
4. Karyawan masuk atau keluar di tengah bulan: hari di luar masa kerja merah dan tidak dihitung.
5. Banyak karyawan atau nama panjang: PDF tidak terpotong, judul tabel berulang, dan tanda tangan tidak tertinggal sendirian di halaman baru.

---

### Task 1: Kontrak model, pengaturan kop, dan dokumen

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__pengaturan_laporan.sql`
- Create: `backend/src/main/java/com/biometricattendance/report/ReportSetting.java`, `ReportSettingRepository.java`, `ReportSettingService.java`
- Modify: `report/ReportController.java`, `report/ReportService.java`, `report/ReportDtos.java`
- Modify: `docs/keputusan.md` (K-142), `docs/pertanyaan-terbuka.md` (Q-051), `docs/dokumen-as-is.md`
- Test: `backend/src/test/java/com/biometricattendance/report/ReportServiceFinancialTests.java`, `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java`

**Interfaces:**
- Produces:
  - Tabel `report_settings(report_slug VARCHAR(64) PRIMARY KEY, manager_name VARCHAR(150), department_name VARCHAR(150), updated_at TIMESTAMPTZ NOT NULL)`.
  - `GET` dan `PUT /api/reports/settings/{slug}` dengan body `ReportSettingRequest(String managerName, String departmentName)`.
  - `ReportModel` bertambah `String managerName, String departmentName, String monthLabel, List<LocalDate> offDates`. Konstruktor lama tetap ada dan mengisi nilai kosong.
  - `ReportFinancial.overtimeRate`, `dailyWage`, dan `baseSalary` boleh `null` bila aturan belum diisi. Nominal turunannya menjadi 0.

- [ ] **Step 1: Tulis test yang gagal di `ReportServiceFinancialTests`**
  - `monthlyReportRejectsPartialMonth`: rentang `2026-09-01`–`2026-09-15` melempar `ApiException` 400 berpesan `Laporan gaji bulanan harus satu bulan penuh`.
  - `monthlyReportCarriesHeader`: setelah setting disimpan, model memuat `managerName`, `departmentName`, dan `monthLabel == "September 2026"`.
  - `offDatesFollowWorkRule`: aturan `workDays` Senin–Sabtu pada September 2026 menghasilkan `offDates` 6, 13, 20, 27. Hari Libur tanggal 17 ikut masuk.
  - `missingRateIsNotInvented`: tanpa tarif lembur, `overtimeRate()` bernilai null dan `overtimeWage()` bernilai 0.
  - `monthlyRowsSortedByEmployeeNumber`.
  - `AccessControlTests`: `GET` dan `PUT /api/reports/settings/gaji-bulanan` diizinkan untuk Owner dan Finance & Accounting, ditolak 403 untuk Karyawan.
- [ ] **Step 2: Jalankan test**: `./mvnw test -Dtest='ReportServiceFinancialTests,AccessControlTests'`. Hasil yang diharapkan: FAIL karena field dan endpoint belum ada.
- [ ] **Step 3: Implementasikan migration, setting, dan model**
  - Validasi satu bulan penuh hanya untuk `GAJI_BULANAN`.
  - `offDates` dihitung dari `workRuleService.resolve(emp, date)` dan `HolidayRepository`. Tanggal masuk `offDates` bila tidak ada karyawan dalam laporan yang bekerja pada hari itu. Aturan tanpa `workDays` diabaikan.
  - Hapus fallback nominal di `ReportService.computeFinancial` dan `ExcelReportWriter`.
- [ ] **Step 4: Jalankan test**: perintah yang sama. Hasil yang diharapkan: PASS.
- [ ] **Step 5: Perbarui dokumen**: K-142 berisi keputusan format di rencana ini, Q-051 berisi blokir baris tanpa gaji pokok, dan `dokumen-as-is.md` mendapat temuan `25 × Wages`.

### Task 2: Sumber format bersama dan lembar Excel

**Files:**
- Create: `report/TakkaFormat.java`, `report/TakkaExcelSheets.java`
- Modify: `report/ExcelReportWriter.java` (hapus `monthlySalarySheet`, `monthlyOvertimeSheet`; `titleBlockTakka` tetap dipakai AS-7)
- Test: `backend/src/test/java/com/biometricattendance/report/TakkaExcelSheetsTests.java`, `ReportExportWritersTests.java`

**Interfaces:**
- Consumes: field baru `ReportModel` dari Task 1.
- Produces:
  - `TakkaFormat`: konstanta warna hex dan `static String hoursText(BigDecimal)` (`4`, `1,5`, `1,33`, dan kosong untuk 0 atau null).
  - `TakkaFormat.cellFill(ReportDay, LocalDate, List<LocalDate> offDates, int dayOfMonth)` mengembalikan `enum DayFill { WHITE, RED }` dan menjadi satu-satunya aturan warna kisi.
  - `TakkaFormat.overtimeRows(List<ReportEmployee>)` untuk AS-6.
  - `TakkaExcelSheets.salary(XSSFWorkbook, ReportModel)` dan `.overtime(XSSFWorkbook, ReportModel)`. Nama lembar tetap `Gaji Staf` dan `Lembur Staf`, dan lembar `Pengajuan Kas Toko` tidak berubah.

- [ ] **Step 1: Tulis test yang gagal**. Baca kembali `.xlsx` dengan POI, memakai fixture September 2026 bernama samaran:
  - Teks dan warna pita, `Manager Name:`, `Department:`, `Month:`, dan latar emas nilai bulan.
  - Urutan judul AS-5 dan AS-6 persis seperti tabel Spesifikasi Format, dan selalu 31 kolom tanggal.
  - Kolom 6, 13, 20, 27, dan 31 merah dari judul sampai baris terakhir. Hari Minggu dengan status `LENGKAP` berisi `V` berlatar putih.
  - Februari 2026: kolom 29, 30, dan 31 merah. Lembur `1.50` di hari Minggu tampil sebagai angka 1,5 di sel merah. Karyawan keluar tanggal 15: tanggal 16 merah dan kosong.
  - Format angka tiap kolom sama dengan tabel Format Angka. Sel piutang 0 kosong. `JUMLAH UPAH` 0 memakai format dengan `"-"`.
  - Baris total memuat jumlah hari berlatar `#FFD966`. AS-6 tidak punya baris total. Tanda tangan AS-5 dua, AS-6 satu.
  - Karyawan tanpa tarif tidak muncul di AS-6, dan karyawan dengan lembur 0 jam tetap muncul.
  - Rekonsiliasi 10 baris samaran dengan gaji pokok dari foto: jumlah Total Wages `31.992.000`. Angka ini adalah `43.992.000` dikurangi tiga baris Q-051.
  - `printSetup` landscape, A4, muat satu halaman lebar, gridline mati.
- [ ] **Step 2: Jalankan**: `./mvnw test -Dtest=TakkaExcelSheetsTests`. Hasil yang diharapkan: FAIL karena kelas belum ada.
- [ ] **Step 3: Implementasikan `TakkaFormat` dan `TakkaExcelSheets`** dengan font Verdana. Kosongkan sel `ONSITE` dan `GAJI POKOK` bila `baseSalary` null. Sesuaikan `ReportExportWritersTests` agar memakai fixture satu bulan penuh.
- [ ] **Step 4: Jalankan**: `./mvnw test -Dtest='TakkaExcelSheetsTests,ReportExportWritersTests'`. Hasil yang diharapkan: PASS.

### Task 3: Halaman PDF

**Files:**
- Create: `report/TakkaPdfPages.java`, `backend/src/main/resources/fonts/DejaVuSans.ttf`, `DejaVuSans-Bold.ttf`, `LICENSE-DejaVu.txt`
- Modify: `report/PdfReportWriter.java` (`GAJI_BULANAN` didelegasikan ke `TakkaPdfPages`; kepala dan kaki halaman lama tidak dipakai untuk jenis ini)
- Test: `backend/src/test/java/com/biometricattendance/report/TakkaPdfPagesTests.java`

**Interfaces:**
- Consumes: `TakkaFormat` dari Task 2 (warna, `hoursText`, `cellFill`, `overtimeRows`).
- Produces: `TakkaPdfPages.write(ReportModel) : byte[]` berisi AS-5 lalu AS-6, masing-masing A4 landscape, tanpa teks `Periode`, `Filter`, atau `Dicetak`.

- [ ] **Step 1: Tulis test yang gagal**. Baca teks dengan `PdfTextExtractor` milik OpenPDF:
  - Halaman 1 memuat `TAKKA STEEL`, `Employee Name`, `Total Wages`, `Rp`, dan `Director Signature:`, `Manager Signature:`.
  - Halaman AS-6 memuat `REKAP LEMBUR TAKKA STEEL`, `1,5`, dan hanya `Director Signature:`.
  - Teks tidak memuat `Filter:` maupun `Dicetak:`.
  - 40 karyawan bernama panjang: tidak ada error, jumlah halaman lebih dari 2, dan judul `Employee Name` muncul di tiap halaman tabel AS-5.
  - Daftar kosong: kop tetap tampil beserta pesan `Tidak ada data pada rentang dan filter ini.`
- [ ] **Step 2: Jalankan**: `./mvnw test -Dtest=TakkaPdfPagesTests`. Hasil yang diharapkan: FAIL.
- [ ] **Step 3: Implementasikan `TakkaPdfPages`.** Pakai satu `PdfPTable` per lembar dengan `setHeaderRows`, `Rp` rata kiri dan angka rata kanan lewat tabel sel bersarang, serta tanda tangan yang dijaga agar tetap bersama baris terakhir.
- [ ] **Step 4: Jalankan**: perintah yang sama. Hasil yang diharapkan: PASS.

### Task 4: Halaman Laporan

**Files:**
- Modify: `frontend/src/pages/ReportsPage.tsx`, `frontend/src/lib/types.ts`
- Test: `frontend/src/pages/ReportsPage.test.tsx`

**Interfaces:**
- Consumes: endpoint pengaturan dan field `ReportModel` dari Task 1.

- [ ] **Step 1: Tulis test yang gagal**
  - Memilih `gaji-bulanan` mengganti Dari/Sampai dengan satu pemilih bulan, dan permintaan memakai tanggal 1 sampai tanggal terakhir bulan itu.
  - Februari 2026 menghasilkan `from=2026-02-01&to=2026-02-28`.
  - Form Nama Manager dan Departemen memuat nilai lewat `GET`, lalu menyimpan lewat `PUT`.
  - Judul pratinjau: `Total Days`, `Wages`, `Upah Lembur`, `Input Piutang`, `Onsite`, `Gaji Pokok`, `Total Wages`.
- [ ] **Step 2: Jalankan**: `npx vitest run src/pages/ReportsPage.test.tsx`. Hasil yang diharapkan: FAIL.
- [ ] **Step 3: Implementasikan.** Tombol filter cepat mingguan disembunyikan untuk `gaji-bulanan`. Pratinjau tetap tabel ringkas, karena format persis berlaku untuk file unduhan.
- [ ] **Step 4: Jalankan**: `npx vitest run src/pages/ReportsPage.test.tsx && npx tsc --noEmit`. Hasil yang diharapkan: PASS.

### Task 5: Verifikasi menyeluruh dan perbandingan dengan foto

**Files:**
- Modify: `docs/keputusan.md`, `docs/pertanyaan-terbuka.md` bila ada hasil baru

- [ ] **Step 1: Jalankan suite penuh**: `./mvnw test` dari salinan terisolasi dan `npx vitest run`. Hasil yang diharapkan: tidak ada kegagalan baru. Kegagalan lama `FingerprintDepartureTests` dan `RakitanCommandTests` (flaky) dilaporkan apa adanya.
- [ ] **Step 2: Hasilkan contoh** dari data samaran September 2026: satu `.xlsx` dan satu `.pdf`, disimpan di scratchpad, bukan di repo.
- [ ] **Step 3: Ubah PDF ke PNG dan bandingkan dengan kedua foto**, baris per baris mengikuti tabel Spesifikasi Format. Perbaiki konstanta `TakkaFormat` bila warna atau proporsi tampak berbeda.
- [ ] **Step 4: Buka `.xlsx` di aplikasi spreadsheet** dan periksa pratinjau cetak: satu halaman lebar, pita selebar tabel, dan kolom tidak menampilkan `####`.
- [ ] **Step 5: Laporkan** hasil test yang benar-benar dijalankan, gambar perbandingan, dan perbedaan yang tersisa.
