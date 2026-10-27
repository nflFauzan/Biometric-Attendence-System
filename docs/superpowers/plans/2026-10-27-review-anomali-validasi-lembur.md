# Daftar Perlu Ditinjau dan Validasi Lembur — Rencana Implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Owner dan Finance & Accounting mendapat dua fitur: daftar data absensi yang tidak lengkap atau tidak wajar per periode (FR-REV-001), dan layar validasi lembur sebelum lembur masuk laporan atau payroll (FR-REV-003).

**Architecture:**
- Method rentang multi-karyawan baru di `AttendanceService`. Method ini juga dipakai rencana laporan.
- Daftar "perlu ditinjau" dihitung langsung dari hasil kalkulasi, tanpa tabel baru.
- Validasi lembur disimpan di tabel baru `overtime_validations` (V18). Setiap baris menyimpan kandidat lembur saat divalidasi, sehingga validasi yang tidak cocok lagi dengan data terbaru otomatis terlihat "perlu validasi ulang".

**Tech Stack:** Spring Boot 4 / Java 21 / JPA / Flyway / PostgreSQL (Testcontainers), React + Vite + TypeScript + Vitest.

**Spec:** requirement HLX-TKS-ATT-REQ-001 v0.6 §6.5:
- **FR-REV-001 (Must):** "HR & Finance melihat daftar data absensi tidak lengkap atau tidak wajar (misalnya tanpa jam keluar) untuk ditinjau."
- **FR-REV-003 (Must):** "HR & Finance memvalidasi lembur sebelum lembur masuk laporan atau perhitungan payroll."
- §6.5: "Koreksi absensi dan validasi lembur oleh HR & Finance adalah pengelolaan data absensi; keduanya bukan perubahan parameter atau perhitungan payroll."

Rujukan lain: dasar kandidat lembur di `docs/alur.md` §9 dan K-110; dokumen review gap bagian 3A.

**Urutan antarrencana:** kerjakan setelah `2026-10-27-perbaikan-kecil-v06.md` dan sebelum `2026-10-27-laporan-ekspor-absensi.md`. Rencana laporan memakai `AttendanceService.range` dan lembur tervalidasi dari rencana ini.

## Konfirmasi di awal sesi (tanyakan sebelum Task 1)

1. **Kriteria "perlu ditinjau".** Bawaan: satu hari masuk daftar bila memenuhi salah satu kondisi berikut:
   - status `TIDAK_LENGKAP`;
   - punya penanda `URUTAN_TIDAK_WAJAR`, `HADIR_SAAT_IZIN`, `PERLU_TINJAUAN_HARI_LIBUR`, atau `ADA_SCAN_TERTAHAN`;
   - aturan kerja belum diatur.

   `TIDAK_HADIR` dan `IZIN_MENUNGGU` tidak dimasukkan. Tidak hadir bukan data yang salah, dan izin yang menunggu sudah muncul di lonceng.
2. **Tidak ada tombol "tandai sudah ditinjau".** Bawaan: sebuah baris baru hilang setelah datanya berubah, lewat koreksi atau keputusan scan tertahan. Akibatnya, baris `HADIR_SAAT_IZIN` dan `PERLU_TINJAUAN_HARI_LIBUR` akan terus tampil. Kalau pengguna ingin bisa menandai "sudah ditinjau", itu butuh tabel tambahan; tanyakan dulu sebelum menambah task.
3. **Aturan validasi lembur.** Bawaan:
   - lembur yang disetujui diisi dalam menit, antara `0` sampai kandidat;
   - catatan wajib bila lembur dikurangi atau ditolak;
   - **tidak ada pembulatan**, karena pembulatan 0,5 jam masih terbuka di Q-008;
   - yang memvalidasi adalah Owner dan Finance & Accounting;
   - validasi masih bisa diubah selama payroll belum ada (Fase 5).
4. **Kandidat berubah setelah divalidasi.** Misalnya jam pulang dikoreksi. Bawaan: validasi lama tetap tersimpan, tetapi statusnya `PERLU_VALIDASI_ULANG` dan tidak dihitung sebagai lembur tervalidasi sampai divalidasi lagi.

## Global Constraints

- Ikuti CLAUDE.md:
  - aturan bisnis tidak di-hardcode;
  - schema hanya diubah lewat Flyway (berikutnya **V18**; cek dulu nomor terakhir);
  - setiap endpoint baru masuk `AccessControlTests`;
  - error berformat ProblemDetail dengan pesan Bahasa Indonesia;
  - setiap perubahan data diaudit dalam transaksi yang sama;
  - scan asli tidak pernah diubah.
- Kandidat lembur **selalu** diambil dari `DailyResultResponse.overtimeCandidateSeconds`, hasil kalkulator yang sudah ada. Jangan menghitung ulang lembur di modul ini.
- **Commit** mengikuti "Aturan Git Commit" di CLAUDE.md, tetapi **hanya bila pengguna meminta**.
- Test backend dijalankan dari salinan `backend` di scratchpad. Jangan matikan `spring-boot:run` milik pengguna.
- Jangan pernah melaporkan test lulus tanpa benar-benar menjalankannya.
- Teks di layar memakai "Finance & Accounting" (K-132).
- Keputusan baru dicatat di `docs/keputusan.md` dengan nomor lanjutan dari yang terakhir.

## Review Focus

1. **Koreksi jam pulang setelah lembur divalidasi.** Status menjadi `PERLU_VALIDASI_ULANG` dan hari itu tidak masuk total tervalidasi. Diuji di Task 3.
2. **Dua pengguna memvalidasi hari yang sama bersamaan.** Unique `(employee_id, work_date)` ditambah `@Version` memastikan satu berhasil dan yang lain mendapat 409. Diuji di Task 3.
3. **Validasi dikirim dari layar yang datanya sudah usang.** Request membawa `expectedCandidateSeconds`. Kalau nilainya berbeda dari kalkulasi terbaru, respons 409 `"Kandidat lembur sudah berubah; muat ulang halaman"`. Diuji di Task 3.
4. **Karyawan dinonaktifkan di tengah periode.** Hari-hari sebelum nonaktif tetap muncul, karena riwayat tetap dilaporkan (FR-EMP-002). Diuji di Task 1.
5. **Rentang lebih dari 62 hari, atau tanggal akhir sebelum tanggal awal.** Respons 400 dengan pesan yang sama dengan `AttendanceService.requireRange`. Diuji di Task 1.

---

### Task 1: Rekap rentang multi-karyawan di `AttendanceService`

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/attendance/AttendanceService.java` (method publik baru; memakai ulang `compute` privat di ±121)
- Modify: `backend/src/main/java/com/biometricattendance/attendance/AttendanceDtos.java` (record baru)
- Modify: `backend/src/main/java/com/biometricattendance/employee/EmployeeRepository.java` bila perlu query karyawan yang relevan untuk rentang tertentu
- Test: `backend/src/test/java/com/biometricattendance/attendance/AttendanceTests.java`

**Interfaces:**
- Produces: `record EmployeeDays(Long employeeId, String employeeNumber, String fullName, Long branchId, String branchName, Long categoryId, String categoryName, List<DayRecord> days)` di `AttendanceDtos`.
- Produces: `List<EmployeeDays> AttendanceService.range(LocalDate from, LocalDate to, Long branchId, Long categoryId, Long employeeId)`.
  - `@Transactional(readOnly = true)`, dan memanggil `requireRange` lebih dulu.
  - Karyawan yang diambil: yang aktif, **ditambah** yang nonaktif dengan `endDate` kosong atau ≥ `from`.
  - Filter yang bernilai null diabaikan.
  - Hasil diurutkan menurut nama cabang, lalu nama karyawan.

- [ ] **Step 1: Tulis test yang gagal**

- `rangeReturnsDaysForEveryEmployeeInFilter`: dua karyawan di dua cabang. Dengan filter cabang A, hasilnya hanya karyawan cabang A, dan jumlah `days` = jumlah tanggal dalam rentang.
- `rangeIncludesEmployeeDeactivatedInsideRange`: karyawan `active=false` dengan `endDate` di tengah rentang tetap muncul.
- `rangeRejectsTooLongOrReversedRange`: menghasilkan `ApiException` 400.

- [ ] **Step 2: Jalankan test dan pastikan gagal.**
- [ ] **Step 3: Implementasi.**
- [ ] **Step 4: Jalankan** `./mvnw -q test -Dtest=AttendanceTests` → PASS.

---

### Task 2: Daftar "Perlu Ditinjau" (FR-REV-001)

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/attendance/ReviewQueueService.java`
- Modify: `backend/src/main/java/com/biometricattendance/attendance/AttendanceController.java`, `AttendanceDtos.java`
- Create: `frontend/src/pages/ReviewQueuePage.tsx`, `frontend/src/pages/ReviewQueuePage.test.tsx`
- Modify: `frontend/src/App.tsx` (route `absensi/perlu-ditinjau`), `frontend/src/navigation.ts`, `frontend/src/navigation.test.ts`, `frontend/src/lib/types.ts`
- Test: `backend/src/test/java/com/biometricattendance/attendance/ReviewQueueTests.java`, `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java`

**Interfaces:**
- Consumes: `AttendanceService.range(...)` dari Task 1.
- Produces: `GET /api/attendance/review-queue?from=&to=&branchId=&categoryId=` → `List<ReviewItem>`.
  - Bentuk data: `record ReviewItem(Long employeeId, String employeeNumber, String fullName, String branchName, String categoryName, LocalDate date, AttendanceStatus status, Set<Flag> flags, boolean ruleMissing, List<String> reasons)`.
  - Isi `reasons`: `"Tidak lengkap"`, `"Urutan jam tidak wajar"`, `"Hadir saat izin"`, `"Hadir di hari libur"`, `"Ada scan tertahan"`, `"Aturan kerja belum diatur"`.
  - Urutan: tanggal terbaru dulu, lalu nama.
  - Default periode: `to` = hari ini, `from` = tanggal 1 bulan dari `to`.
- Hak akses: Owner dan Finance & Accounting. Prefix `/api/attendance/**` sudah tercakup di `SecurityConfig`, tetapi endpoint ini tetap ditambahkan ke matrix `AccessControlTests`.
- Menu: `{ path: '/absensi/perlu-ditinjau', label: 'Perlu Ditinjau', roles: MANAGERS, icon: ListChecks, group: 'Operasional' }`, tepat setelah "Absensi Harian".

- [ ] **Step 1: Tulis test backend yang gagal** (`ReviewQueueTests`, memakai `AttendanceFixtures` dan data sintetis)

- `incompleteDayIsListed`: hanya satu scan pada hari kerja → baris muncul dengan `reasons` berisi `"Tidak lengkap"`.
- `completeNormalDayIsNotListed`.
- `absentDayIsNotListed`.
- `correctedDayLeavesTheQueue`: setelah `POST /api/attendance/corrections` mengisi jam pulang, baris itu hilang.
- `heldScanDayIsListed`.
- `employeeCannotReadReviewQueue` → 403. Tambahkan juga ke matrix `AccessControlTests`.

- [ ] **Step 2: Jalankan test dan pastikan gagal.**

- [ ] **Step 3: Implementasi backend**

`ReviewQueueService.list(from, to, branchId, categoryId)` menyaring hasil `range(...)` dengan kriteria Konfirmasi no. 1. Lalu jalankan `./mvnw -q test -Dtest='ReviewQueueTests,AccessControlTests'` → PASS.

- [ ] **Step 4: Tulis test frontend yang gagal** (`ReviewQueuePage.test.tsx`)

- Tabel menampilkan nama, tanggal, dan alasan.
- Tautan "Tinjau" mengarah ke `/absensi/karyawan/:id?from=<date>&to=<date>`, yaitu halaman koreksi yang sudah ada.
- Filter tanggal, cabang, dan kategori ikut dikirim sebagai query.
- Saat kosong, tampil teks `"Tidak ada data yang perlu ditinjau pada periode ini."`.

- [ ] **Step 5: Implementasi frontend**

Ikuti pola filter di `AttendancePage.tsx` dan `HeldScansPage.tsx`. Kalau `AttendanceEmployeePage` belum membaca `from`/`to` dari query string, tambahkan, beserta test-nya di `AttendancePages.test.tsx`.

- [ ] **Step 6: Jalankan** `npx vitest run src/pages src/navigation.test.ts` dan `npx tsc -b` → PASS.

- [ ] **Step 7: Catat dan laporkan**

Tambahkan keputusan **Daftar Perlu Ditinjau** di `docs/keputusan.md`: kriteria yang dipakai, alasan tidak memakai tabel, dan rujukan FR-REV-001.

---

### Task 3: Validasi lembur (FR-REV-003)

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__validasi_lembur.sql`
- Create: paket `backend/src/main/java/com/biometricattendance/overtime/`, berisi `OvertimeValidation.java`, `OvertimeValidationRepository.java`, `OvertimeDtos.java`, `OvertimeService.java`, dan `OvertimeController.java`
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java` (`/api/overtime/**` untuk Owner dan Finance & Accounting)
- Create: `frontend/src/pages/OvertimePage.tsx`, `frontend/src/pages/OvertimePage.test.tsx`
- Modify: `frontend/src/App.tsx` (route `absensi/lembur`), `frontend/src/navigation.ts`, `frontend/src/navigation.test.ts`, `frontend/src/lib/types.ts`
- Test: `backend/src/test/java/com/biometricattendance/overtime/OvertimeValidationTests.java`, `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java`

Schema V18:
```sql
create table overtime_validations (
    id bigserial primary key,
    employee_id bigint not null references employees(id),
    work_date date not null,
    candidate_seconds bigint not null check (candidate_seconds >= 0),
    approved_seconds bigint not null check (approved_seconds >= 0 and approved_seconds <= candidate_seconds),
    note varchar(1000),
    validated_by varchar(100) not null,
    validated_at timestamptz not null,
    version bigint not null default 0,
    constraint ux_overtime_validations_day unique (employee_id, work_date)
);
```
`approved_seconds = 0` berarti lembur ditolak. Tabel ini tidak punya kolom status; status dihitung saat dibaca:
- `BELUM_DIVALIDASI`: belum ada baris untuk hari itu;
- `TERVALIDASI`: `candidate_seconds` sama dengan kandidat saat ini;
- `PERLU_VALIDASI_ULANG`: `candidate_seconds` berbeda dari kandidat saat ini.

**Interfaces:**
- Consumes: `AttendanceService.range(...)` dari Task 1.
- Produces: `GET /api/overtime?from=&to=&branchId=&categoryId=` → `List<OvertimeRow>`.
  - Hanya memuat hari dengan kandidat > 0, **atau** hari yang sudah punya validasi.
  - Bentuk data: `record OvertimeRow(Long employeeId, String fullName, String branchName, String categoryName, LocalDate date, LocalTime checkOut, long candidateSeconds, Long approvedSeconds, String note, String validatedByName, Instant validatedAt, OvertimeStatus status)`.
- Produces: `POST /api/overtime/validations`.
  - Body: `{ "items": [ { "employeeId", "workDate", "approvedMinutes", "expectedCandidateSeconds", "note" } ] }`, maksimal 200 item.
  - Satu request diproses atomik: kalau satu item gagal, semua dibatalkan.
  - Respons: `List<OvertimeRow>` yang sudah diperbarui.
  - Audit: `OVERTIME_VALIDATE` per item, dengan nilai sebelum dan sesudah.
- Produces (dipakai rencana laporan): `Map<Long, Map<LocalDate, Long>> OvertimeService.validatedSeconds(List<EmployeeDays> data)`. Hanya memuat hari berstatus `TERVALIDASI`, dengan nilai `approvedSeconds`.
- Validasi body (400, pesan Bahasa Indonesia):
  - `approvedMinutes` antara 0 dan kandidat; kalau lebih: `"Lembur disetujui tidak boleh melebihi kandidat"`;
  - kalau `approvedMinutes * 60 < kandidat` dan catatan kosong: `"Catatan wajib bila lembur dikurangi atau ditolak"`;
  - tanggal tidak boleh di masa depan.
- Menu: `{ path: '/absensi/lembur', label: 'Validasi Lembur', roles: MANAGERS, icon: Timer, group: 'Operasional' }`.

- [ ] **Step 1: Tulis test backend yang gagal** (`OvertimeValidationTests`, data sintetis)

- `candidateAfterWorkEndIsListedAsNotValidated`: scan 08:00 dan 19:00 dengan aturan uji yang jam pulangnya 17.00. Hasilnya satu baris `BELUM_DIVALIDASI`, dan `candidateSeconds` sama dengan `overtimeCandidateSeconds` dari `GET /api/attendance/employees/{id}` untuk hari yang sama.
- `approveFullCandidate`: tanpa catatan → `TERVALIDASI`, dan ada audit `OVERTIME_VALIDATE`.
- `reduceRequiresNote`: tanpa catatan → 400; dengan catatan → 200.
- `cannotApproveMoreThanCandidate` → 400.
- `rejectWithZeroMinutes`: 0 menit dengan catatan → `TERVALIDASI`, dengan `approvedSeconds` 0.
- `correctionAfterValidationNeedsRevalidation`: validasi dulu, lalu koreksi jam pulang ke 20:00 → status `PERLU_VALIDASI_ULANG`, dan `validatedSeconds(...)` tidak memuat hari itu.
- `staleExpectedCandidateIsRejected` → 409.
- `batchIsAtomic`: dua item, satu tidak valid → 400, dan tidak ada baris yang tersimpan.
- `concurrentValidationOfSameDay`: memakai helper konkuren dengan pola `LeaveApprovalTests.concurrently` → tepat satu yang berhasil.
- `employeeCannotValidateOvertime` → 403. Tambahkan juga ke matrix `AccessControlTests`.
- Test migration: unique `(employee_id, work_date)` dan CHECK `approved_seconds <= candidate_seconds` benar-benar berlaku. Ikuti pola test migration di `LeaveMigrationTests`.

- [ ] **Step 2: Jalankan test dan pastikan gagal.**

Run: `./mvnw -q test -Dtest='OvertimeValidationTests,AccessControlTests'` → FAIL.

- [ ] **Step 3: Implementasi backend**

Ikuti pola modul `leave`: entity dengan `@Version`, repository `findByEmployeeIdInAndWorkDateBetween`, record DTO, service `@Transactional`, controller, dan audit di transaksi yang sama. Tulis data dengan upsert per `(employee_id, work_date)`. Konflik unique atau `@Version` → 409 `"Lembur ini baru saja divalidasi pengguna lain; muat ulang halaman"`.

- [ ] **Step 4: Jalankan test backend.**

Run: `./mvnw -q test -Dtest='OvertimeValidationTests,AccessControlTests,AttendanceTests'` → PASS.

- [ ] **Step 5: Tulis test frontend yang gagal** (`OvertimePage.test.tsx`)

- Kandidat ditampilkan dengan format jam-menit yang sudah dipakai halaman absensi (cari helper di `lib/format`). Isian menit terisi otomatis dengan nilai kandidat.
- Mencentang dua baris lalu menekan **Validasi terpilih** mengirim satu `POST /api/overtime/validations` berisi 2 item.
- Error 400 dari server ditampilkan.
- Status `PERLU_VALIDASI_ULANG` tampil dengan badge berteks `"Perlu validasi ulang"`.

- [ ] **Step 6: Implementasi frontend**

Pakai filter periode, cabang, dan kategori seperti di Task 2. Badge mengikuti pola `lib/leave.ts`.

- [ ] **Step 7: Jalankan** `npx vitest run`, `npx tsc -b`, dan `npm run lint` → PASS.

- [ ] **Step 8: Catat dan laporkan**

- `docs/keputusan.md`: tambahkan keputusan **Validasi lembur**, mencakup V18, aturan dari Konfirmasi no. 3–4, tidak ada pembulatan (Q-008 tetap terbuka), dan rujukan FR-REV-003.
- `docs/alur.md` §9.1: tandai bahwa langkah pemeriksaan dan konfirmasi durasi sudah diimplementasikan.

---

### Task 4: Verifikasi akhir

- [ ] Jalankan suite backend penuh dari salinan. Kalau kehabisan memori, jalankan per paket dan laporkan apa adanya.
- [ ] Jalankan `npx vitest run`, `npx tsc -b`, dan `npm run lint`.
- [ ] Perbarui dokumen review gap (Claude Docs): di bagian 1, matriks sitemap "Validasi Lembur" berubah dari Belum menjadi Sudah; di bagian 3A, tandai FR-REV-001 dan FR-REV-003 selesai.
- [ ] Laporkan ke pengguna: file yang berubah, angka hasil test yang sebenarnya, dan hal yang belum dikerjakan.
