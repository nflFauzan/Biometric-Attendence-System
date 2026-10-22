# Cabang Absen per Karyawan — Rencana Implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** menerapkan K-103. Cabang absen diatur per karyawan; scan dari cabang yang tidak diizinkan ditahan dari rekap sampai ditinjau; pendaftaran dan sidik jari mengikuti daftar cabang.

**Architecture:**
- Cabang tambahan disimpan di tabel baru. Cabang penempatan tetap di `employees.branch_id` dan selalu diizinkan.
- Penanda `CABANG_TIDAK_DIIZINKAN` diberikan saat scan diterima.
- Keputusan tinjauan disimpan di tabel `scan_reviews`, terpisah dari scan asli.
- `AttendanceService` membuang scan yang tertahan sebelum perhitungan harian.

**Tech Stack:** Spring Boot 4 / Java 21 / JPA / Flyway / PostgreSQL; React + Vite + TypeScript + TanStack Query + shadcn.

**Spec:** [docs/superpowers/specs/2026-10-22-cabang-absen-per-karyawan-design.md](../specs/2026-10-22-cabang-absen-per-karyawan-design.md)

## Global Constraints

- Migration baru: `V11__cabang_absen.sql`. V10 sudah dipakai.
- Scan asli (`attendance_scans`) tidak diubah oleh peninjauan. Satu-satunya pembaruan penanda yang diizinkan ada di `linkUnmatched` (K-068, query yang sudah ada).
- Setiap perubahan data dicatat lewat `AuditService` di transaksi yang sama. Aksi baru: `SCAN_REVIEW`.
- Error berformat ProblemDetail, dengan pesan Bahasa Indonesia persis:
  - *"Karyawan tidak diizinkan absen di cabang mesin ini."*
  - *"Alasan wajib diisi."*
  - *"Scan ini tidak tertahan karena cabang."*
- Endpoint baru masuk ke `SecurityConfig` dan matriks `AccessControlTests`. Aksesnya Owner dan HR/Finance; Karyawan mendapat 403.
- Test backend dijalankan dari salinan `backend` di scratchpad (`./mvnw -q test`), tanpa mengganggu `spring-boot:run` pengguna. `LeaveTests` dan `DeviceCommandLifecycleTests` diketahui kadang gagal sendiri: ulangi sebelum menyimpulkan ada regresi.
- Pengguna yang melakukan commit sendiri. Langkah "Commit" diganti dengan melaporkan file yang berubah.

## Review Focus

1. **Karyawan tanpa PIN atau scan PIN tak dikenal.** Scan tanpa karyawan tidak boleh diberi penanda cabang. Pengujinya CB-07 dan sebuah test di Task 2.
2. **Cabang tambahan berisi cabang penempatan, ID ganda, atau ID yang tidak ada.** Penempatan diabaikan, ID ganda disatukan, dan ID yang tidak ada dibalas 404. Pengujinya Task 1.
3. **Scan tertahan di hari libur atau hari dengan koreksi aktif.** Koreksi aktif tetap menang. Scan tertahan hanya dibuang dari daftar scan perhitungan, sehingga hari itu tidak menjadi "Hadir" karenanya. Pengujinya Task 3.
4. **Mencabut cabang saat ada ENROLL aktif di mesin cabang itu.** ENROLL dibatalkan, sedangkan ENROLL di mesin cabang lain tidak disentuh. Pengujinya Task 4.
5. **Tinjauan massal berisi satu scan yang tidak tertahan.** Seluruh permintaan ditolak 400 dan tidak ada yang tersimpan. Pengujinya Task 3.

---

### Task 1: Tabel cabang tambahan dan endpoint pengaturannya

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__cabang_absen.sql` (kedua tabel: `employee_extra_branches` dan `scan_reviews`, lihat Task 3 untuk kolomnya)
- Modify: `backend/src/main/java/com/biometricattendance/employee/Employee.java`, `EmployeeDtos.java`, `EmployeeService.java`, `EmployeeController.java`
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java` (bila pola URL `/api/employees/**` belum mencakupnya)
- Test: `backend/src/test/java/com/biometricattendance/masterdata/EmployeeBranchAccessTests.java`, dan tambahan baris di `security/AccessControlTests.java`

**Interfaces:**
- Produces:
  - `Employee.getExtraBranches(): Set<Branch>`: `@ManyToMany` ke `employee_extra_branches`, fetch LAZY.
  - `Employee.isAllowedAt(Long branchId): boolean`: penempatan atau tambahan.
  - `Employee.allowedBranchIds(): Set<Long>`.
  - `EmployeeDtos.BranchAccessRequest(List<Long> extraBranchIds)`; `@NotNull`, maksimal 50 item.
  - `EmployeeDtos.BranchAccessResponse(Long placementBranchId, List<Long> extraBranchIds, int removedFingerprints)`.
  - `EmployeeService.branchAccess(Long id): BranchAccessResponse`.
  - `EmployeeService.updateBranchAccess(Long id, BranchAccessRequest req): BranchAccessResponse`. Pada task ini `removedFingerprints` selalu 0; Task 4 mengisinya.
  - Endpoint `GET` dan `PUT /api/employees/{id}/branches`.

SQL `employee_extra_branches`: `employee_id bigint not null references employees(id) on delete cascade`, `branch_id bigint not null references branches(id)`, `primary key (employee_id, branch_id)`.

- [ ] **Step 1: Tulis test yang gagal** di `EmployeeBranchAccessTests extends AttendanceFixtures`:
  - `extraBranchesAreSavedAndPlacementIgnored`: PUT `{extraBranchIds:[b2, placement, b2]}` → GET berisi `extraBranchIds == [b2]`, `placementBranchId == placement`.
  - `unknownBranchIs404`.
  - `placementChangeKeepsOldPlacementAsExtra` (CB-10): update karyawan dari b1 ke b2 → `extraBranchIds == [b1]`. Jika b2 sebelumnya cabang tambahan, b2 hilang dari daftar tambahan.
  - `branchChangeIsAudited`: audit `UPDATE` entitas karyawan memuat `extraBranchIds` sebelum dan sesudah.
  - Di `AccessControlTests`: GET dan PUT `/api/employees/{id}/branches`. Karyawan 403, tanpa login 401, Owner dan HR diizinkan.
- [ ] **Step 2: Jalankan dan pastikan gagal.** `./mvnw -q test -Dtest=EmployeeBranchAccessTests` → FAIL (404 atau compile error).
- [ ] **Step 3: Implementasikan.**
  - Tulis migration, mapping entity, service, dan controller.
  - `EmployeeService.update` memindahkan penempatan lama ke cabang tambahan setiap kali `branchId` berubah.
  - Audit memakai snapshot `BranchAccessResponse`.
- [ ] **Step 4: Jalankan test Task 1 dan `AccessControlTests`.** Harus PASS.
- [ ] **Step 5: Laporkan file yang berubah.**

### Task 2: Penanda `CABANG_TIDAK_DIIZINKAN` saat scan diterima

**Files:**
- Modify: `attendance/ScanFlag.java`, `ingest/ScanIngestService.java:80-89`, `attendance/AttendanceScanRepository.java` (`linkUnmatched`), `employee/EmployeeRepository.java`
- Modify: `frontend/src/lib/types.ts` (`ScanFlag`), `frontend/src/lib/attendance.ts` (label)
- Test: `backend/src/test/java/com/biometricattendance/ingest/BranchAccessIngestTests.java`

**Interfaces:**
- Consumes: `Employee.isAllowedAt(Long)` (Task 1).
- Produces:
  - `ScanFlag.CABANG_TIDAK_DIIZINKAN`, dengan label frontend *"Cabang tidak diizinkan"*.
  - Label `CABANG_BERBEDA` diubah menjadi *"Scan di cabang tambahan"*.

Langkah:

- [ ] **Step 1: Tulis test yang gagal.** Kirim scan memakai helper push X401 di `AttendanceFixtures`.
  - `scanAtPlacementHasNoBranchFlag` (CB-01).
  - `scanAtExtraBranchIsCabangBerbeda` (CB-02).
  - `scanAtOtherBranchIsHeld` (CB-03, sisi ingest): flags = `{CABANG_TIDAK_DIIZINKAN}` saja, dan scan tetap tersimpan.
  - `laterBranchChangeDoesNotReflag` (CB-06): scan disimpan, lalu cabangnya dijadikan tambahan → penanda scan lama tetap.
  - `linkingUnknownPinFlagsDisallowedBranch` (CB-07): scan PIN tak dikenal di mesin cabang b2, lalu PIN diberikan ke karyawan cabang b1 → flags = `{CABANG_TIDAK_DIIZINKAN}`. Scan yang sama di mesin cabang b1 → flags kosong.
  - `unknownPinGetsNoBranchFlag`.
- [ ] **Step 2: Jalankan dan pastikan gagal.**
- [ ] **Step 3: Implementasikan.**
  - Ingest: pakai `employees.findByDevicePinWithBranches` (fetch join `extraBranches`) supaya tidak terjadi lazy-load per scan.
  - `linkUnmatched`: hapus `PIN_TIDAK_DIKENAL`, lalu tambahkan `CABANG_TIDAK_DIIZINKAN` bila `device.branch_id` tidak sama dengan penempatan dan tidak ada di `employee_extra_branches`. Gunakan satu `UPDATE ... FROM devices` dengan ekspresi CASE.
- [ ] **Step 4: Jalankan test Task 2, `AttendanceTests`, dan test ingest yang sudah ada.** Harus PASS.
- [ ] **Step 5: Laporkan file yang berubah.**

### Task 3: Peninjauan dan rekap tanpa scan tertahan

**Files:**
- Create: `attendance/ScanReview.java`, `attendance/ScanReviewRepository.java`, `attendance/HeldScanService.java`
- Modify: `attendance/AttendanceDtos.java`, `attendance/AttendanceController.java`, `attendance/AttendanceService.java:139-175`, `calculation/DailyAttendanceCalculator.java` (enum `Flag`), `dashboard/*` (hitungan), `config/SecurityConfig.java`
- Test: `backend/src/test/java/com/biometricattendance/attendance/HeldScanTests.java`, ditambah baris di `AccessControlTests`

**Interfaces:**
- Consumes: `ScanFlag.CABANG_TIDAK_DIIZINKAN` (Task 2).
- Produces:
  - Tabel `scan_reviews` di V11 (Task 1 sudah membuat file migration-nya), dengan kolom:
    - `scan_id bigint primary key references attendance_scans(id)`
    - `decision varchar(20) not null check (decision in ('DITERIMA','DIABAIKAN'))`
    - `reason varchar(500) not null`
    - `decided_by varchar(100) not null`
    - `decided_at timestamptz not null`
    - `version bigint not null default 0`
  - `enum ReviewStatus { BELUM_DITINJAU, DITERIMA, DIABAIKAN }` di `AttendanceDtos`.
  - `ScanResponse` mendapat field baru `ReviewStatus review` (null bila scan tidak tertahan) dan `boolean counted`.
  - `HeldScanResponse(ScanResponse scan, String employeeName, String placementBranchName, String deviceBranchName, ReviewStatus status, String reason, String decidedBy, Instant decidedAt)`.
  - `ReviewRequest(@NotEmpty @Size(max=200) List<Long> scanIds, @NotNull Decision decision, String reason)`, dengan `enum Decision { DITERIMA, DIABAIKAN }`.
  - `HeldScanService.list(LocalDate from, LocalDate to, ReviewStatus status): List<HeldScanResponse>`.
  - `HeldScanService.review(ReviewRequest): List<HeldScanResponse>`.
  - `HeldScanService.countPending(): long`.
  - Endpoint:
    - `GET /api/attendance/held-scans?from&to&status`
    - `POST /api/attendance/held-scans/review`
  - Penanda hari `Flag.ADA_SCAN_TERTAHAN`.
  - Dashboard mendapat field `heldScans: long`.

- [ ] **Step 1: Tulis test yang gagal** di `HeldScanTests`:
  - `heldScanIsNotCountedInRecap` (CB-03): karyawan dengan scan masuk dan pulang di cabang yang tidak diizinkan. Rekap harian: status tidak "Hadir", scan tetap terlihat dengan `review = BELUM_DITINJAU` dan `counted = false`, flags hari memuat `ADA_SCAN_TERTAHAN`.
  - `acceptedScanCountsAndRawScanUnchanged` (CB-04): setelah review DITERIMA, rekap menghitungnya, kolom `flags` dan `scanned_at` di `attendance_scans` sama seperti sebelum review, dan ada satu audit `SCAN_REVIEW` per scan.
  - `decisionCanBeChanged` (CB-05): DITERIMA lalu DIABAIKAN → keluar dari rekap, dan audit `before.decision == DITERIMA`.
  - `reviewRequiresReason`: reason kosong atau hanya spasi → 400 *"Alasan wajib diisi."*
  - `batchWithUnheldScanIsRejectedWhole` (CB-13 dan Review Focus 5): 400 *"Scan ini tidak tertahan karena cabang."*, dan tidak ada baris `scan_reviews` yang dibuat.
  - `concurrentReviewOneWins` (CB-14): dua thread meninjau scan yang sama secara bersamaan; hasilnya satu 200 dan satu 409.
  - `activeCorrectionStillWins`: hari dengan koreksi aktif dan scan tertahan → hasil sesuai koreksi.
  - `listFiltersByStatus` dan `dashboardCountsPending`.
  - Di `AccessControlTests`: kedua endpoint baru. Karyawan 403, tanpa login 401.
- [ ] **Step 2: Jalankan dan pastikan gagal.**
- [ ] **Step 3: Implementasikan.**
  - `AttendanceService` memuat review untuk scan dalam rentang dengan satu query `findByScanIdIn`.
  - Scan dibuang dari input kalkulator bila `CABANG_TIDAK_DIIZINKAN` dan keputusannya bukan `DITERIMA`.
  - `ADA_SCAN_TERTAHAN` ditambahkan ke `DailyResult.flags` bila ada scan yang `BELUM_DITINJAU`. Lakukan di `AttendanceService` (kalkulator tetap murni).
  - Dalam `review`, upsert memakai `@Version` (pembaruan) atau constraint primary key (dua insert bersamaan). Kedua jenis konflik dipetakan ke 409 *"Scan ini baru saja ditinjau pengguna lain. Muat ulang halaman."*
  - Kirim `AttendanceChangedEvent` untuk setiap karyawan yang terdampak. Pola after-commit mengikuti `CorrectionService`.
- [ ] **Step 4: Jalankan test Task 3, `AttendanceTests`, `DashboardTests`, `TeamTests`, dan `AccessControlTests`.** Harus PASS.
- [ ] **Step 5: Laporkan file yang berubah.**

### Task 4: Pendaftaran dibatasi dan pencabutan cabang menghapus sidik jari

**Files:**
- Modify: `fingerprint/FingerprintService.java` (`startEnroll`), `fingerprint/FingerprintRemoval.java`, `devicecommand/DeviceCommandService.java`, `devicecommand/DeviceCommandRepository.java`, `employee/EmployeeService.java` (`updateBranchAccess`)
- Test: `backend/src/test/java/com/biometricattendance/fingerprint/BranchRevokeTests.java`

**Interfaces:**
- Consumes: `Employee.isAllowedAt` (Task 1) dan `updateBranchAccess` (Task 1).
- Produces:
  - `FingerprintRemoval.revokeBranches(Employee e, Set<Long> revokedBranchIds, String actor, Instant now): int`, yang mengembalikan jumlah jari yang dilupakan.
  - `DeviceCommandService.cancelActiveEnrollsOnBranches(Long employeeId, Set<Long> branchIds, String actor, Instant now): int`.

Langkah:

- [ ] **Step 1: Tulis test yang gagal.** Pola fixture mengikuti test sidik jari yang sudah ada (RakitanFixtures dan EmployeeFingerprint).
  - `enrollOnDisallowedBranchIs400` (CB-08): pesan *"Karyawan tidak diizinkan absen di cabang mesin ini."*
  - `enrollOnExtraBranchIsAllowed`.
  - `revokingBranchForgetsFingersOnItsDevicesOnly` (CB-09):
    - Siapkan jari 1 di mesin cabang b2 (tambahan) dan jari 2 di mesin cabang b1 (penempatan).
    - Cabut b2 → catatan jari 1 hilang, jari 2 tetap, dan ada tepat satu `DELETE_TEMPLATE` untuk jari 1 ke mesin b2.
    - Tidak ada perintah ke mesin lain.
    - Respons `removedFingerprints == 1`, dan ada audit `FINGERPRINT_DELETED`.
  - `revokingBranchCancelsEnrollOnlyThere` (Review Focus 4): ENROLL aktif di mesin b2 → `DIBATALKAN`, sedangkan ENROLL aktif di mesin b1 tetap aktif.
  - `placementChangeDeletesNothing` (CB-10, sisi sidik jari).
- [ ] **Step 2: Jalankan dan pastikan gagal.**
- [ ] **Step 3: Implementasikan.**
  - Pemeriksaan di `startEnroll` diletakkan setelah pengecekan mesin ditemukan, dan sebelum pengecekan online.
  - `revokeBranches` memakai `forget` yang sudah ada, ditambah `DeviceCommand.deleteTemplate(record.getDevice(), …)` hanya untuk perangkat rakitan.
  - `updateBranchAccess` menghitung `revoked = before − after`, lalu memanggil keduanya di transaksi yang sama.
- [ ] **Step 4: Jalankan test Task 4, test `fingerprint/*`, dan `devicecommand/*`.** Harus PASS.
- [ ] **Step 5: Laporkan file yang berubah.**

### Task 5: Frontend Detail Karyawan (kartu Cabang absen dan filter mesin)

**Files:**
- Create: `frontend/src/components/BranchAccessCard.tsx`
- Modify: `frontend/src/pages/EmployeeDetailPage.tsx`, `frontend/src/components/EnrollDialog.tsx`, `frontend/src/lib/types.ts`
- Test: `frontend/src/components/BranchAccessCard.test.tsx`, dan tambahan di `EmployeeFingerprint.test.tsx`

**Interfaces:**
- Consumes: `GET` dan `PUT /api/employees/{id}/branches` (Task 1), serta respons `removedFingerprints` (Task 4).
- Produces:
  - Type `BranchAccess { placementBranchId: number; extraBranchIds: number[]; removedFingerprints: number }`.
  - Prop `allowedBranchIds: number[]` di `EnrollDialog`.

Langkah:

- [ ] **Step 1: Tulis test yang gagal** (Vitest + Testing Library):
  - Penempatan tampil tercentang, disabled, dan diberi keterangan "Penempatan".
  - Mencentang cabang lalu menekan Simpan mengirim PUT dengan `extraBranchIds` yang benar.
  - Menghapus centang cabang yang punya jari menampilkan konfirmasi *"2 sidik jari di mesin {nama mesin} akan dihapus."* Batal tidak mengirim apa-apa.
  - Hanya Owner dan HR yang bisa melihat dan mengubah kartu ini.
  - `EnrollDialog` hanya menampilkan mesin yang `branchId`-nya ada di `allowedBranchIds`.
- [ ] **Step 2: Jalankan dan pastikan gagal.** `npm test -- --run BranchAccessCard`
- [ ] **Step 3: Implementasikan.**
  - Pakai komponen shadcn yang sudah ada (Card, Checkbox, AlertDialog) dan `api()` dari `src/lib/api.ts`.
  - Setelah simpan, invalidasi query karyawan dan sidik jari.
  - Tampilkan toast *"Cabang absen disimpan."*, ditambah *"{n} sidik jari dihapus."* bila n > 0.
- [ ] **Step 4: Jalankan `npm test -- --run` dan `npm run build`.** Harus PASS.
- [ ] **Step 5: Laporkan file yang berubah.**

### Task 6: Frontend halaman Scan Tertahan, label rekap, dan kartu dashboard

**Files:**
- Create: `frontend/src/pages/HeldScansPage.tsx`, `frontend/src/pages/HeldScansPage.test.tsx`
- Modify: `frontend/src/App.tsx` (route `absensi/scan-tertahan`, `managers(...)`), menu Absensi di `AppLayout.tsx`, `pages/DashboardPage.tsx`, tampilan scan di `AttendanceEmployeePage.tsx` / `AttendancePage.tsx`, `lib/types.ts`, `lib/attendance.ts`

**Interfaces:**
- Consumes: endpoint Task 3, `ScanResponse.review` dan `counted`, `Flag.ADA_SCAN_TERTAHAN`, serta `heldScans` di dashboard.

Langkah:

- [ ] **Step 1: Tulis test yang gagal:**
  - Halaman memuat daftar dengan status bawaan "Belum ditinjau".
  - Memilih dua baris lalu "Masukkan ke rekap" membuka dialog. Simpan tetap nonaktif selama alasan kosong.
  - Kirim mengirim `{scanIds:[..], decision:'DITERIMA', reason}` lalu memuat ulang daftar.
  - Kartu dashboard "Scan tertahan" menaut ke `/absensi/scan-tertahan`.
  - Scan dengan `counted=false` tampil dengan label "Tertahan" atau "Diabaikan".
- [ ] **Step 2: Jalankan dan pastikan gagal.**
- [ ] **Step 3: Implementasikan.** Ikuti pola `UnmatchedScansPage` untuk filter tanggal, tabel, dan queryKey `['/api/attendance', 'held', from, to, status]`. Label penanda hari: `ADA_SCAN_TERTAHAN` → *"Ada scan tertahan"*.
- [ ] **Step 4: Jalankan `npm test -- --run` dan `npm run build`.** Harus PASS.
- [ ] **Step 5: Laporkan file yang berubah.**

### Task 7: Dokumen dan test penuh

**Files:**
- Modify: `docs/keputusan.md` (tabel ringkas K-106 dan bagian detail; status K-103 menjadi "Disepakati, diterapkan"), `docs/aturan-bisnis.md` §4.7, `docs/pertanyaan-terbuka.md` Q-022, `docs/README.md` (tautan spec dan plan bila pola README memuatnya)

Langkah:

- [ ] **Step 1: Tulis K-106.**
  - Isinya: rincian spec §2–§7, ditambah kelima keputusan Claude yang sudah disetujui pengguna 2026-10-22.
  - Catat bahwa K-106 mengganti arti `CABANG_BERBEDA` di K-065.
- [ ] **Step 2: Perbarui aturan-bisnis §4.7 dan Q-022.** Cara peninjauan sudah terjawab; zona waktu per cabang masih terbuka.
- [ ] **Step 3: Jalankan test backend penuh dari salinan dan test frontend penuh.** Laporkan jumlah lulus/gagal apa adanya.
- [ ] **Step 4: Laporkan seluruh file yang berubah kepada pengguna untuk di-commit.**
