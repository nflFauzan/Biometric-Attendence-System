# Rapikan Rencana 3 (M1–M5, Q-018/Q-019) — Rencana Implementasi

> **Untuk agent pelaksana:** WAJIB memakai superpowers:subagent-driven-development (disarankan) atau superpowers:executing-plans untuk mengerjakan rencana ini per task. Langkah memakai checkbox (`- [ ]`).

**Tujuan:** menutup lima kekurangan kecil yang ditemukan di review akhir Rencana 3, lalu memperbarui Q-018 dan Q-019 seperti yang diminta spec §13.

**Arsitektur:** tidak ada modul baru, migration baru, atau endpoint baru. Perbaikannya:
- percobaan ulang sekali di controller pembatalan (M1);
- test tambahan (M2);
- audit di `EnrollmentRecorder` (M3);
- `@DynamicUpdate` pada `Employee` (M4);
- satu kode error baru yang hanya dipakai server (M5).

**Tech stack:** Spring Boot 4 (Java 21), Hibernate 7, JUnit 5 + MockMvc + Testcontainers + `@MockitoSpyBean`; React + Vitest.

**Spec:** [`docs/superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md`](../specs/2026-10-16-pendaftaran-sidik-jari-web-design.md) §9, §13, §16. Kontrak mesin: [`docs/protokol-alat-rakitan.md`](../../protokol-alat-rakitan.md). Keputusan pengguna 2026-10-18: "kerjakan A dulu"; M5 memakai pilihan "pisahkan kode".

## Batasan global

- Pengguna yang melakukan commit. Langkah "Commit" berarti: berhenti, laporkan file yang berubah dan hasil test.
- Test backend dijalankan dari **salinan** `backend` (tanpa `target`) di scratchpad: `./mvnw -q test`. Jangan jalankan test di `backend/` milik pengguna, dan jangan matikan proses `spring-boot:run` pengguna.
- `LeaveTests.concurrentOverlappingSubmissionsSaveOnlyOne` kadang gagal karena deadlock (flaky lama). Ulangi dulu sebelum menyimpulkan ada regresi.
- Pesan untuk pengguna dalam Bahasa Indonesia dan ditulis persis seperti di rencana ini.
- Kontrak mesin `/device/v1` **tidak berubah**: mesin tetap mengirim `WAKTU_HABIS` saat tidak ada jari yang ditempelkan.
- Pesan baru:
  - `WAKTU_HABIS` (dari mesin) → `Tidak ada jari yang ditempelkan. Ulangi pendaftaran.`
  - `TIDAK_ADA_HASIL` (batas waktu 75 detik di server) → `Mesin tidak merespons. Periksa koneksi mesin.`

## Fokus review

1. **Batal kalah dari laporan langkah, bukan dari hasil.** Kalau percobaan ulang menemukan perintah masih aktif, pembatalan harus berhasil (200, `DIBATALKAN`), bukan dijawab "Perintah sudah selesai." Test: Task 1 `cancelRetriesWhenProgressWins`.
2. **Percobaan ulang hanya sekali.** Kalau konflik terulang lagi, pengguna mendapat 409 pesan umum yang sudah ada, bukan loop. Test: Task 1 `cancelGivesUpAfterOneRetry`.
3. **Edit karyawan oleh HR menghapus persetujuan yang baru tersimpan.** Ini arah sebaliknya dari M4: update satu baris penuh menulis `fingerprint_consent_at = null`. Test: Task 4 `employeeEditKeepsConcurrentConsent`.
4. **Data lama berkode `WAKTU_HABIS` dari server.** Baris lama tetap tampil dengan pesan mesin. Ini diterima karena baru berisi data development, dan dicatat di K-101.
5. **Audit tidak boleh memuat template.** Snapshot audit sidik jari hanya berisi `finger`, `deviceId`, `commandId`, dan `enrolledAt`. Test: Task 3 `reEnrollIsAuditedWithBeforeAndAfter` memeriksa kunci JSON-nya.

---

### Task 1: M1 — batal yang kalah balapan

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandController.java`
- Test: `backend/src/test/java/com/biometricattendance/devicecommand/DeviceCommandCancelRaceTests.java` (baru, `extends RakitanFixtures`)

**Interfaces:**
- Consumes: `DeviceCommandService.cancel(String id): CommandResponse` (tidak diubah).
- Produces: perilaku `POST /api/device-commands/{id}/cancel`. Konflik `@Version` pertama dicoba ulang sekali; pesannya ditentukan oleh hasil percobaan ulang itu.

- [ ] **Step 1: Tulis test yang gagal.** Pakai `@MockitoSpyBean DeviceCommandService service`. Siapkan perintah dengan pola `sent()` di `RakitanCommandTests`; boleh menyalin helper kecilnya atau memindahkannya ke `RakitanFixtures`.
  - `cancelReportsFinishedWhenResultWins`: kirim `result` BERHASIL dulu, lalu `doThrow(new ObjectOptimisticLockingFailureException(DeviceCommand.class, id)).doCallRealMethod().when(service).cancel(id)`. `cancel(id)` → 409, `$.detail` = `Perintah sudah selesai.`, status tetap `BERHASIL`.
  - `cancelRetriesWhenProgressWins`: perintah masih `DIKIRIM`, stub yang sama. → 200, `$.status` = `DIBATALKAN`.
  - `cancelGivesUpAfterOneRetry`: `doThrow(...)` dua kali. → 409, `$.detail` = `Data baru saja diubah oleh pengguna lain. Muat ulang lalu coba lagi`. `verify(service, times(2)).cancel(id)`.
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run: `./mvnw -q test -Dtest=DeviceCommandCancelRaceTests`. Expected: dua test pertama FAIL, karena pesan umum dan bukan hasil percobaan ulang.
- [ ] **Step 3: Implementasi.** Di `DeviceCommandController.cancel`, tangkap `ObjectOptimisticLockingFailureException` dari `service.cancel(id)` dan panggil `service.cancel(id)` sekali lagi, tanpa menangkap exception dari panggilan kedua. Percobaan ulang ada di controller, di luar transaksi, sehingga panggilan kedua membaca status terbaru. Tambahkan komentar satu baris yang menjelaskan alasannya.
- [ ] **Step 4: Jalankan dan pastikan lulus.** Run: `./mvnw -q test -Dtest='DeviceCommandCancelRaceTests,DeviceCommandLifecycleTests,RakitanCommandTests'`. Expected: PASS.
- [ ] **Step 5: Perbarui dokumen.** Di `docs/protokol-alat-rakitan.md` §hasil, ganti kalimat "Jika admin membatalkan tepat saat mesin selesai, status tetap `DIBATALKAN`; …" dengan:

  > Jika admin membatalkan tepat saat mesin selesai, yang tersimpan lebih dulu yang berlaku. Batal lebih dulu: status `DIBATALKAN`, hasil diabaikan, dan selisih isi sensor dibereskan lewat rekonsiliasi (Rencana 4). Hasil lebih dulu: status mengikuti hasil, dan admin mendapat pesan *"Perintah sudah selesai."*

  Di `docs/keputusan.md` K-100, butir "Laporan setelah batal diabaikan", tambahkan kalimat yang sama untuk kasus hasil lebih dulu.
- [ ] **Step 6: Commit.** Berhenti dan laporkan.

### Task 2: M2 — test 401/403 untuk progress dan result

**Files:**
- Test: `backend/src/test/java/com/biometricattendance/ingest/RakitanCommandTests.java`
- Modify (jika perlu): `backend/src/test/java/com/biometricattendance/ingest/RakitanFixtures.java`

**Interfaces:**
- Produces: helper `protected void deactivate(Rakitan rakitan, long branchId)` di `RakitanFixtures`, berisi `PUT /api/devices/{id}` dengan `active:false` seperti di `RakitanScanIngestTests.wrongKeyAndInactiveDeviceSaveNothing`. `RakitanScanIngestTests` ikut memakai helper ini.

- [ ] **Step 1: Tulis test.**
  - `progressAndResultRejectWrongKey`: `progress("salah", id, 1)` dan `result("salah", id, BERHASIL)` → `expectError(..., 401, "KUNCI_DITOLAK")`. Lalu `command(id)`: `$.status` = `DIKIRIM`, `$.message` = `Menunggu mesin…`, dan `fingerprintRows(employee)` = 0.
  - `progressAndResultRejectInactiveDevice`: `deactivate(...)`, lalu progress dan result → `expectError(..., 403, "MESIN_NONAKTIF")`. Status perintah dan `fingerprintRows` sama seperti test di atas.
- [ ] **Step 2: Jalankan.** Run: `./mvnw -q test -Dtest='RakitanCommandTests,RakitanScanIngestTests'`. Expected: PASS, karena filter autentikasi mesin sudah menjaga `/device/v1/**`. Kalau FAIL, itu bug sungguhan: berhenti dan laporkan, jangan mengubah test.
- [ ] **Step 3: Commit.** Berhenti dan laporkan.

### Task 3: M3 — audit pendaftaran jari

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/devicecommand/EnrollSucceededEvent.java`
- Modify: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandService.java` (method `result`)
- Modify: `backend/src/main/java/com/biometricattendance/fingerprint/EnrollmentRecorder.java`
- Test: `backend/src/test/java/com/biometricattendance/ingest/RakitanCommandTests.java`

**Interfaces:**
- Produces: `record EnrollSucceededEvent(UUID commandId, Long employeeId, int finger, Long deviceId, String actor, Instant at)`. `actor` diisi `machineActor(device)`, yaitu `"mesin <serial>"`.
- Audit yang dicatat lewat `AuditService.record(actor, action, "EmployeeFingerprint", id, before, after, null)`:
  - `FINGERPRINT_ENROLLED`: `before` = null, `after` = snapshot. Untuk baris baru, simpan dengan `saveAndFlush` dulu agar `id` tersedia.
  - `FINGERPRINT_REENROLLED`: `before` = snapshot lama, `after` = snapshot baru.
  - Snapshot: `LinkedHashMap` berisi `finger`, `deviceId`, `commandId` (string), dan `enrolledAt` (string).

- [ ] **Step 1: Tulis test yang gagal.**
  - `successRecordsFingerprint` ditambah: tepat 1 baris `audit_logs` dengan `action = 'FINGERPRINT_ENROLLED'`, `actor_username` = `"mesin " + deviceId`, dan `before_data` null.
  - `reEnrollIsAuditedWithBeforeAndAfter`: alur sama dengan `reEnrollReplacesRecord`, lalu periksa satu baris `FINGERPRINT_REENROLLED`. `before_data.deviceId` = mesin pertama, `after_data.deviceId` = mesin kedua, dan kunci `after_data` persis `{finger, deviceId, commandId, enrolledAt}`. Nama kolom JSON audit mengikuti tabel `audit_logs` di migration V1.
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run: `./mvnw -q test -Dtest=RakitanCommandTests`. Expected: FAIL, karena baris audit belum ada.
- [ ] **Step 3: Implementasi** sesuai Interfaces di atas. Perbarui javadoc `EnrollmentRecorder`: penggantian catatan sekarang tercatat di audit.
- [ ] **Step 4: Jalankan dan pastikan lulus.** Run: `./mvnw -q test -Dtest='RakitanCommandTests,DeviceCommandLifecycleTests,FingerprintApiTests'`. Expected: PASS.
- [ ] **Step 5: Commit.** Berhenti dan laporkan.

### Task 4: M4 — persetujuan dan edit karyawan tidak saling menimpa

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/employee/Employee.java` (tambah `@org.hibernate.annotations.DynamicUpdate`)
- Test: `backend/src/test/java/com/biometricattendance/fingerprint/FingerprintConsentRaceTests.java` (baru, `extends RakitanFixtures`)

**Interfaces:**
- Produces: setiap UPDATE `employees` hanya menulis kolom yang berubah. Ini berlaku untuk semua penulis `Employee`, termasuk edit karyawan dan persetujuan.

Kedua test memakai `TransactionTemplate`. Di dalam satu transaksi, `JdbcTemplate` memakai koneksi yang sama dengan JPA, sehingga perubahan "dari pengguna lain" ditulis setelah entity dimuat dan sebelum flush.

- [ ] **Step 1: Tulis test yang gagal.**
  - `consentKeepsConcurrentEmployeeEdit`: di dalam transaksi, jalankan `fingerprintService.consent(id)` dengan `SecurityContext` berisi user `owner-absensi`. Lalu `jdbc.update("update employees set full_name = 'Nama Baru' where id = ?", id)`. Setelah commit: `full_name` = `Nama Baru` dan `fingerprint_consent_at` tidak null.
  - `employeeEditKeepsConcurrentConsent`: di dalam transaksi, `employeeRepository.findById(id)`, lalu `jdbc.update("update employees set fingerprint_consent_at = now(), fingerprint_consent_by = 'hr' where id = ?", id)`, lalu `employee.setFullName("Nama Baru")`. Setelah commit: `fingerprint_consent_by` = `hr` dan `full_name` = `Nama Baru`.
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run: `./mvnw -q test -Dtest=FingerprintConsentRaceTests`. Expected: kedua test FAIL karena kolom lain tertimpa.
- [ ] **Step 3: Implementasi.** Tambahkan `@DynamicUpdate` pada `Employee`, dengan komentar satu baris: persetujuan dan edit HR menulis kolom berbeda, jadi tidak boleh saling menimpa.
- [ ] **Step 4: Jalankan dan pastikan lulus.** Run: `./mvnw -q test -Dtest='FingerprintConsentRaceTests,FingerprintApiTests,Employee*Tests'`. Expected: PASS.
- [ ] **Step 5: Commit.** Berhenti dan laporkan.

### Task 5: M5 — batas waktu server punya kode sendiri

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommand.java` (konstanta)
- Modify: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandService.java` (`expireStale`)
- Modify: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandMessages.java`
- Modify: `frontend/src/pages/EmployeeFingerprint.test.tsx:228` (fixture memakai kode server yang baru)
- Test: `DeviceCommandMessagesTests.java`, `DeviceCommandLifecycleTests.java:102`

**Interfaces:**
- Produces: `public static final String TIDAK_ADA_HASIL = "TIDAK_ADA_HASIL";` di `DeviceCommand`. `WAKTU_HABIS` tetap ada, karena dipakai mesin.

- [ ] **Step 1: Ubah test.**
  - `DeviceCommandMessagesTests`: `failed("WAKTU_HABIS", null)` → `Tidak ada jari yang ditempelkan. Ulangi pendaftaran.`, dan `failed("TIDAK_ADA_HASIL", null)` → `Mesin tidak merespons. Periksa koneksi mesin.`
  - `DeviceCommandLifecycleTests:102`: expected `TIDAK_ADA_HASIL`.
  - Frontend fixture: `errorCode: 'TIDAK_ADA_HASIL'`.
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run: `./mvnw -q test -Dtest='DeviceCommandMessagesTests,DeviceCommandLifecycleTests'`. Expected: FAIL.
- [ ] **Step 3: Implementasi.** `expireStale` memakai `TIDAK_ADA_HASIL`. `DeviceCommandMessages.failure` memetakan kedua kode ke pesan di Batasan global.
- [ ] **Step 4: Jalankan dan pastikan lulus.** Run backend: `./mvnw -q test -Dtest='DeviceCommand*Tests,RakitanCommandTests'`. Run frontend (di `frontend/`): `npx vitest run src/pages/EmployeeFingerprint.test.tsx`. Expected: PASS.
- [ ] **Step 5: Perbarui dokumen.**
  - `docs/protokol-alat-rakitan.md`: di bagian batas waktu, server menandai `GAGAL` + `TIDAK_ADA_HASIL`. Di tabel kode error, pesan `WAKTU_HABIS` diganti pesan baru, dengan catatan bahwa `TIDAK_ADA_HASIL` hanya diisi server dan tidak pernah dikirim mesin.
  - Spec §9 tabel (baris 269) dan §16 butir 5 (baris 382): ganti dengan cara yang sama.
  - `docs/keputusan.md`: K-094 rincian (baris 459) diperbarui. Tambahkan **K-101** di tabel dan rincian: "Batas waktu tanpa hasil dari server memakai kode `TIDAK_ADA_HASIL`, terpisah dari `WAKTU_HABIS` mesin (tidak ada jari), agar pesan web menunjuk penyebab yang benar. Batal yang bentrok dengan laporan mesin dicoba ulang sekali; yang tersimpan lebih dulu yang berlaku. Data development lama berkode `WAKTU_HABIS` dari server tidak dimigrasi." Tanggal 2026-10-18, status Disepakati.
- [ ] **Step 6: Commit.** Berhenti dan laporkan.

### Task 6: Q-018, Q-019, dan pemeriksaan akhir

**Files:**
- Modify: `docs/pertanyaan-terbuka.md` (Q-018, Q-019, tabel prioritas)

- [ ] **Step 1: Tandai Q-018 "Dijawab sebagian 2026-10-18"** dengan isi berikut:
  - komponen: ESP32 DevKit, sensor R503, TFT 2,4" non-touch, RTC DS3231, microSD, buzzer;
  - protokol `/device/v1` (K-087, `protokol-alat-rakitan.md`);
  - identifikasi lewat PIN per karyawan (K-056).
  - Yang masih terbuka: siapa yang menulis firmware (Q-034).
- [ ] **Step 2: Tandai Q-019 "Dijawab sebagian 2026-10-18"** dengan isi berikut:
  - pendaftaran dipicu dari web dengan persetujuan karyawan (K-091, K-094, K-100);
  - template dicocokkan di sensor dan tidak disimpan server.
  - Yang masih terbuka: daftar di setiap mesin atau disalin (bergantung Q-035/Rencana 4), penghapusan saat nonaktif (Rencana 5), dan mesin rusak/diganti.
- [ ] **Step 3: Pemeriksaan penuh.** Jalankan backend `./mvnw -q test` dari salinan, lalu frontend `npm test -- --run` dan `npm run build` di `frontend/`. Expected: semua PASS (sebelumnya 333 backend dan 87 frontend, sekarang ditambah test baru). Laporkan jumlah aslinya.
- [ ] **Step 4: Perbarui memori** `rencana-3-status.md`: M1–M5 dan Q-018/Q-019 selesai.
- [ ] **Step 5: Commit.** Berhenti dan laporkan.
