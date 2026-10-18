# Hapus Sidik Jari, Kunci PIN, dan Peringatan Kapasitas (Rencana 5) — Rencana Implementasi

> **Untuk agent pelaksana:** WAJIB memakai superpowers:subagent-driven-development (disarankan) atau superpowers:executing-plans untuk mengerjakan rencana ini per task. Langkah memakai checkbox (`- [ ]`).

**Tujuan:** sidik jari bisa dihapus dari semua alat rakitan, baik per jari maupun otomatis saat karyawan keluar atau nonaktif. PIN terkunci sampai semua mesin aktif selesai menghapus. Halaman Mesin memperingatkan sensor yang hampir penuh.

**Arsitektur:**
- **Jenis perintah baru.** `DELETE_TEMPLATE` dan `DELETE_EMPLOYEE` dibuat di antrean `devicecommand` yang sudah ada. Batas waktu, pembatalan, dan laporan langkah hanya berlaku untuk `ENROLL`.
- **Modul `fingerprint`** memiliki penghapusan dan kunci PIN.
- **Arah ketergantungan.** `employee` tidak boleh bergantung pada `fingerprint`, yang sudah bergantung pada `employee`. Karena itu `EmployeeService` menerbitkan event sinkron `EmployeeChangedEvent` di dalam transaksinya. Listener di `fingerprint` memeriksa kunci PIN (melempar `ApiException` sehingga transaksi batal) dan menjalankan penghapusan saat karyawan keluar.
- **Tanggal keluar yang tiba belakangan** ditangkap oleh pemeriksaan terjadwal setiap jam.

**Tech stack:** Spring Boot 4 (Java 21), Flyway, PostgreSQL, JUnit 5 + MockMvc + Testcontainers; React + TypeScript + TanStack Query + Vitest; Node.js (simulator).

**Spec:** [`docs/superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md`](../specs/2026-10-16-pendaftaran-sidik-jari-web-design.md) **§17** (mengikat, disetujui pengguna 2026-10-18), ditambah §3.4, §3.5, §4. Kontrak mesin: [`docs/protokol-alat-rakitan.md`](../../protokol-alat-rakitan.md). Ini rencana 5 dari 7.

## Batasan global

**Cara kerja**
- Pengguna yang melakukan commit. Langkah "Commit" berarti: berhenti, laporkan file yang berubah dan hasil test.
- Test backend dijalankan dari **salinan** `backend` (tanpa `target`) di scratchpad: `./mvnw -q test`. Jangan matikan proses `spring-boot:run` pengguna.
- `LeaveTests.concurrentOverlappingSubmissionsSaveOnlyOne` kadang gagal karena deadlock (flaky lama). Ulangi dulu sebelum menyimpulkan ada regresi.
- Perubahan schema hanya lewat `V9__hapus_sidik_jari.sql`. Setiap perubahan data dicatat lewat `AuditService` di transaksi yang sama.
- Endpoint baru wajib masuk matrix `AccessControlTests`: Owner dan HR/Finance boleh, Karyawan 403, tanpa login 401.

**Nama yang dipakai**
- Jenis perintah: `ENROLL`, `DELETE_TEMPLATE`, `DELETE_EMPLOYEE`.
- PIN dianggap *menunggu penghapusan* jika ada `DELETE_*` dengan `device_pin` itu, di mesin `active = true`, berstatus `MENUNGGU`, `DIKIRIM`, atau `GAGAL`.
- Aksi audit baru: `FINGERPRINT_DELETED`, `DELETE_REQUEST`, `COMMAND_RETRY`. Pelaku dari pemeriksaan terjadwal: `sistem` (`DeviceCommandService.SYSTEM_ACTOR`).

**Pesan pengguna** (persis)

| Kondisi | Pesan |
|---|---|
| Hapus jari saat `ENROLL` jari itu aktif | `Jari ini sedang didaftarkan.` |
| Ganti/kosongkan PIN, karyawan masih punya jari | `Hapus sidik jari dulu sebelum mengganti PIN.` |
| Ganti/kosongkan PIN, PIN lama menunggu penghapusan | `Tunggu sampai sidik jari selesai dihapus di semua mesin.` |
| PIN baru sedang menunggu penghapusan | `PIN ini masih menunggu penghapusan sidik jari di mesin.` |
| `ENROLL` ke mesin yang masih punya `DELETE_*` untuk PIN itu, belum `BERHASIL` | `Tunggu sampai sidik jari lama selesai dihapus di mesin ini.` |
| Batalkan perintah hapus | `Perintah penghapusan tidak bisa dibatalkan.` (409) |
| "Coba lagi" pada perintah yang bukan `DELETE_*` `GAGAL` | `Hanya penghapusan yang gagal yang bisa dicoba lagi.` (409) |
| Hapus aktif, mesin aktif dan online | `Menunggu mesin…` |
| Hapus aktif, mesin offline | `Menunggu (mesin offline)` |
| Hapus aktif, mesin nonaktif | `Menunggu (mesin nonaktif)` |
| Hapus `BERHASIL` | `Terhapus` |
| Hapus `GAGAL`, kode `SENSOR_ERROR` | `Sensor bermasalah. Hubungi teknisi.` |
| Hapus `GAGAL`, kode lain | `Penghapusan gagal ({kode}).` |
| Catatan X401 | `Hapus juga langsung di mesin X401 jika karyawan pernah didaftarkan di sana.` |
| Sensor > ambang | `Kapasitas sensor hampir penuh.` |

**Konfigurasi teknis** (`application.properties`): `app.device.sensor-warning-percent=90` dan `app.fingerprint.departure-check=1h`.

## Fokus review

1. **`expireStale` menyentuh perintah hapus.** Perintah hapus yang menunggu mesin offline berhari-hari tidak boleh kedaluwarsa atau menjadi `TIDAK_ADA_HASIL`. Test: Task 1 `deleteCommandsNeverExpire`.
2. **Ganti PIN dan nonaktifkan dalam satu simpan.** Karena masih ada jari, permintaan ditolak 400 dan tidak ada yang terhapus. Pemeriksaan kunci berjalan sebelum penghapusan. Test: Task 4 `deactivateAndChangePinTogetherIsRejected`.
3. **Zona waktu cabang untuk tanggal keluar.** Cabang `Asia/Jayapura` (UTC+9), tanggal keluar 2026-10-18. Pukul 2026-10-18T15:30Z, yaitu 00.30 tanggal 19 waktu Jayapura, karyawan sudah dihapus. Pukul 14:30Z (23.30 tanggal 18) belum dihapus. Test: Task 3 `departureUsesBranchTimezone`.
4. **Pemeriksaan terjadwal berjalan dua kali.** Penghapusan tidak boleh terulang, dan `DELETE_EMPLOYEE` tidak boleh dobel. Test: Task 3 `departureCheckRunsOnce`.
5. **Mesin X401 dan mesin belum pernah tersambung tidak mendapat perintah**, tetapi mesin rakitan nonaktif yang pernah tersambung tetap mendapat perintah. Test: Task 2 `deleteTargetsEveryConnectedRakitan`.

---

### Task 1: Migration V9 dan perintah hapus di antrean

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__hapus_sidik_jari.sql`
- Modify: `devicecommand/DeviceCommand.java`, `DeviceCommandRepository.java`, `DeviceCommandService.java`, `DeviceCommandMessages.java`
- Modify: `ingest/rakitan/RakitanDtos.java` (`CommandItem`), `ingest/rakitan/RakitanController.java` (pemetaan item)
- Test: `devicecommand/DeviceCommandLifecycleTests.java`, `devicecommand/DeviceCommandMessagesTests.java`, `ingest/RakitanCommandTests.java`

**Interfaces (Produces):**
- **Migration V9:**
  - `device_commands.type` CHECK diganti menjadi `IN ('ENROLL','DELETE_TEMPLATE','DELETE_EMPLOYEE')`. Nama constraint bawaan Postgres kemungkinan `device_commands_type_check`; periksa dengan `\d device_commands`.
  - `employees.fingerprints_removed_at TIMESTAMPTZ`.
  - `devices.sensor_used INTEGER`, `devices.sensor_capacity INTEGER`.
  - Index `ix_device_commands_delete_pin ON device_commands (device_pin, status) WHERE type IN ('DELETE_TEMPLATE','DELETE_EMPLOYEE')`.
- **`DeviceCommand`:**
  - `TYPE_DELETE_TEMPLATE`, `TYPE_DELETE_EMPLOYEE`;
  - `static DeviceCommand deleteTemplate(Device, Employee, String pin, int finger, String createdBy, Instant)` dan `static DeviceCommand deleteEmployee(Device, Employee, String pin, String createdBy, Instant)`. Parameter `pin` diisi PIN saat ini;
  - `boolean isDelete()`;
  - `void requeue()`: status kembali `MENUNGGU`, lalu `step`, `errorCode`, `errorDetail`, `sentAt`, dan `finishedAt` dikosongkan.
- **Repository:** `findDeliverable` mengirim `DELETE_*` berstatus `MENUNGGU`/`DIKIRIM` tanpa memandang umurnya. Batas waktu hanya berlaku untuk `ENROLL`. `findUnpickedBefore` dan `findSentBefore` ditambah `c.type = 'ENROLL'`.
- **`DeviceCommandService`:**
  - `create(DeviceCommand command, String actor)`, dengan aksi audit `ENROLL_REQUEST` untuk ENROLL dan `DELETE_REQUEST` untuk hapus. `create(DeviceCommand)` yang lama tetap ada dan memanggil versi baru dengan `CurrentUser.username()`;
  - `progress` mengembalikan `NOT_ACTIVE` untuk perintah hapus;
  - `cancel` untuk perintah hapus melempar 409 dengan pesan di tabel;
  - `result` BERHASIL hanya menerbitkan `EnrollSucceededEvent` untuk ENROLL;
  - `int cancelActiveEnrolls(Long employeeId, String actor, Instant now)` mencatat audit `CANCEL_COMMAND`.
- **`DeviceCommandMessages.message`:**
  - `BERHASIL` dari perintah hapus → `Terhapus`;
  - perintah hapus yang aktif → `Menunggu mesin…`;
  - kode gagal tak dikenal dari perintah hapus → `Penghapusan gagal ({kode}).`
- **`CommandItem`:** `finger`, `displayName`, dan `timeoutSeconds` menjadi nullable (`Integer`/`Long`). Untuk perintah hapus: `displayName` = null dan `timeoutSeconds` = null. `DELETE_EMPLOYEE` juga `finger` = null.

- [ ] **Step 1: Tulis test yang gagal.**
  - Lifecycle `deleteCommandsNeverExpire`: simpan `deleteEmployee` lewat `tx()`, `markSent(t)`, lalu `expireStale(t.plusSeconds(3600))` → status tetap `DIKIRIM`.
  - Lifecycle `cancelActiveEnrollsCancelsOnlyEnroll`.
  - Messages `deleteMessages`: `Terhapus`, `Menunggu mesin…`, `Penghapusan gagal (X).`, dan `Sensor bermasalah. Hubungi teknisi.`
  - RakitanCommandTests:
    - `heartbeatDeliversDeleteCommands`: buat perintah dengan `DeviceCommandService.create` di `tx()`, lalu heartbeat. Item berisi `type`, `pin`, dan `finger` (null untuk `DELETE_EMPLOYEE`), serta `displayName` null.
    - `oldDeleteIsStillDelivered`: `created_at` dimundurkan 1 jam lewat `jdbc`.
    - `progressOnDeleteIs409` → `PERINTAH_TIDAK_AKTIF`.
    - `deleteResultDoesNotRecordFingerprint`.
    - `cancelDeleteIs409`.
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run: `./mvnw -q test -Dtest='DeviceCommand*Tests,RakitanCommandTests'`. Expected: FAIL (compile atau assertion).
- [ ] **Step 3: Implementasi** sesuai Interfaces.
- [ ] **Step 4: Jalankan.** Perintah sama. Expected: PASS, termasuk test ENROLL lama.
- [ ] **Step 5: Commit.** Berhenti dan laporkan.

### Task 2: Hapus satu jari

**Files:**
- Create: `fingerprint/FingerprintRemoval.java` (`@Service`, public). Kelas ini memiliki penentuan mesin tujuan dan pembuatan perintah hapus, dan dipakai Task 2 dan 3.
- Modify: `fingerprint/FingerprintService.java`, `FingerprintController.java`, `FingerprintDtos.java`, `EmployeeFingerprintRepository.java`, `device/DeviceRepository.java`
- Test: `fingerprint/FingerprintDeleteTests.java` (baru, `extends RakitanFixtures`), `security/AccessControlTests.java`

**Interfaces:**
- Consumes: Task 1 (`deleteTemplate`, `create(command, actor)`).
- Produces:
  - `DeviceRepository.findConnectedRakitan()`: `protocol = 'RAKITAN_V1' and lastSeenAt is not null`, termasuk mesin nonaktif.
  - `FingerprintRemoval.deleteFinger(Employee, int finger, String actor, Instant now)`. Menghapus baris jari (audit `FINGERPRINT_DELETED`, `before` = snapshot jari yang sama dengan `EnrollmentRecorder`, `after` null), lalu membuat satu `DELETE_TEMPLATE` per mesin tujuan. Snapshot dipindah ke `EmployeeFingerprint.snapshot()` agar dipakai bersama.
  - `FingerprintService.deleteFinger(Long employeeId, int finger): FingerprintOverview`:
    - 404 `Karyawan`, dan 404 `Sidik jari` jika jari itu tidak terdaftar;
    - 409 `Jari ini sedang didaftarkan.` jika `existsActiveEnroll`.
  - Endpoint `DELETE /api/employees/{employeeId}/fingerprints/{finger}` → 200 overview.
  - Guard ENROLL: `DeviceCommandRepository.existsUnfinishedDeleteOnDevice(deviceId, pin)`, yaitu `DELETE_*` berstatus `MENUNGGU`/`DIKIRIM`/`GAGAL`. Diperiksa di `startEnroll` setelah pemeriksaan mesin sibuk dan sebelum pemeriksaan jari sibuk. Pesannya di tabel, status 409.

- [ ] **Step 1: Tulis test yang gagal.**
  - `deleteFingerRemovesRecordAndQueuesPerMachine`: daftar jari 1 di mesin A, lalu `DELETE`. `fingers` kosong, satu `DELETE_TEMPLATE` per mesin rakitan yang pernah tersambung, ada audit `FINGERPRINT_DELETED` dan `DELETE_REQUEST`.
  - `deleteTargetsEveryConnectedRakitan`: mesin rakitan online, mesin rakitan online lalu dinonaktifkan, mesin rakitan yang belum pernah heartbeat, dan satu X401. Perintah hanya dibuat untuk dua mesin pertama.
  - `deleteWhileEnrollActiveIs409`.
  - `deleteUnknownFingerIs404`.
  - `enrollBlockedUntilOldDeleteDone`: setelah hapus, ENROLL ke mesin A → 409. Mesin A melapor `BERHASIL` lewat `result`, lalu ENROLL → 201.
  - AccessControlTests: tambahkan `{ HttpMethod.DELETE, "/api/employees/" + MISSING + "/fingerprints/1", null }` ke matrix yang ada.
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run: `./mvnw -q test -Dtest='FingerprintDeleteTests,AccessControlTests'`. Expected: FAIL.
- [ ] **Step 3: Implementasi.**
- [ ] **Step 4: Jalankan.** Run: `./mvnw -q test -Dtest='Fingerprint*Tests,AccessControlTests,RakitanCommandTests'`. Expected: PASS.
- [ ] **Step 5: Commit.** Berhenti dan laporkan.

### Task 3: Penghapusan saat karyawan keluar atau nonaktif

**Files:**
- Create: `employee/EmployeeChangedEvent.java`, `fingerprint/EmployeeFingerprintListener.java`, `fingerprint/DepartureSweeper.java`
- Modify: `employee/Employee.java` (`fingerprintsRemovedAt`), `employee/EmployeeService.java`, `employee/EmployeeRepository.java`, `fingerprint/FingerprintRemoval.java`, `fingerprint/FingerprintService.java` (syarat ENROLL), `application.properties`
- Test: `fingerprint/FingerprintDepartureTests.java` (baru)

**Interfaces:**
- Produces:
  - `public record EmployeeChangedEvent(Employee employee, String pinBefore, boolean created)`. Diterbitkan `EmployeeService.create` dan `update` setelah `apply` dan **sebelum** `repository.save`.
  - `Employee.getFingerprintsRemovedAt()`, `markFingerprintsRemoved(Instant)`, `clearFingerprintsRemoved()`.
  - `FingerprintRemoval.isDeparted(Employee, Instant now)`: `!active` atau `endDate < tanggal hari ini di zona cabang`.
  - `FingerprintRemoval.removeAll(Employee, String actor, Instant now)`. Isinya:
    - `cancelActiveEnrolls`;
    - hapus semua baris jari (audit per baris);
    - jika PIN ada dan `fingerprintConsentAt != null`, satu `DELETE_EMPLOYEE` per mesin tujuan;
    - `markFingerprintsRemoved(now)`.
  - Listener (`@EventListener`, sinkron), urutannya:
    1. pemeriksaan kunci PIN (Task 4; untuk sementara kosong);
    2. jika `isDeparted` dan `removedAt == null` → `removeAll(employee, CurrentUser.username(), now)`;
    3. jika tidak `isDeparted` dan `removedAt != null` → `clearFingerprintsRemoved()`.
  - `DepartureSweeper` (`@Scheduled(fixedDelayString = "${app.fingerprint.departure-check:1h}")`) memanggil `FingerprintRemoval.sweepDepartures(Instant now): int` dengan pelaku `sistem`:
    - kandidat diambil lewat `EmployeeRepository.findDepartureCandidates(LocalDate maxEndDate)` = `fingerprintsRemovedAt is null and endDate is not null and endDate < :maxEndDate`, dengan `maxEndDate` = tanggal UTC hari ini + 1;
    - kandidat disaring di Java dengan `isDeparted`.
  - Syarat ENROLL di `FingerprintService.startEnroll` diganti menjadi `!isActive() || isDeparted(...)`, dengan pesan lama.

- [ ] **Step 1: Tulis test yang gagal.** Semua lewat `PUT /api/employees/{id}`, kecuali sweeper yang dipanggil langsung dengan `now` tertentu.
  - `deactivateRemovesFingersAndQueuesDeleteEmployee`: ada audit `FINGERPRINT_DELETED`, ENROLL aktif menjadi `DIBATALKAN`, dan `fingerprints_removed_at` terisi.
  - `pastEndDateRemovesImmediately`.
  - `futureEndDateKeepsFingersUntilSweep`: tanggal keluar besok → jari tetap ada, dan ENROLL masih boleh.
  - `departureUsesBranchTimezone`: lihat Fokus review #3. Cabang dibuat dengan zona `Asia/Jayapura` lewat fixture cabang yang ada atau `jdbc`.
  - `departureCheckRunsOnce`: dua kali `sweepDepartures` → satu `DELETE_EMPLOYEE` per mesin.
  - `noConsentNoDeleteEmployee`.
  - `reactivateClearsMarker`.
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run: `./mvnw -q test -Dtest=FingerprintDepartureTests`. Expected: FAIL.
- [ ] **Step 3: Implementasi.**
- [ ] **Step 4: Jalankan.** Run: `./mvnw -q test -Dtest='Fingerprint*Tests,MasterDataTests'`. Expected: PASS.
- [ ] **Step 5: Commit.** Berhenti dan laporkan.

### Task 4: Kunci PIN

**Files:**
- Modify: `fingerprint/EmployeeFingerprintListener.java`, `devicecommand/DeviceCommandRepository.java`
- Test: `fingerprint/PinLockTests.java` (baru)

**Interfaces:**
- Produces:
  - `DeviceCommandRepository.existsPendingDeleteForPin(String pin)`, mengikuti definisi *menunggu penghapusan* di Batasan global.
  - Pemeriksaan di listener (langkah 1). Jika PIN berubah (`!Objects.equals(pinBefore, pin)`), urutannya:
    1. `!created` dan karyawan punya baris jari → 400 `Hapus sidik jari dulu sebelum mengganti PIN.`
    2. `!created` dan `pinBefore != null` dan `existsPendingDeleteForPin(pinBefore)` → 400 `Tunggu sampai sidik jari selesai dihapus di semua mesin.`
    3. `pin != null` dan `existsPendingDeleteForPin(pin)` → 400 `PIN ini masih menunggu penghapusan sidik jari di mesin.`

- [ ] **Step 1: Tulis test yang gagal.**
  - `pinLockedWhileFingerRegistered`.
  - `pinLockedUntilAllMachinesDelete`: hapus jari di 2 mesin, lalu ganti PIN → 400. Mesin 1 `BERHASIL` → masih 400. Mesin 2 `BERHASIL` → 200.
  - `failedDeleteKeepsLock`.
  - `deactivatingMachineUnlocksPin`.
  - `pendingPinCannotBeTakenByNewEmployee`: buat karyawan baru dengan PIN yang menunggu penghapusan. Siapkan dengan cara lepas kunci lewat mesin nonaktif, kosongkan PIN, lalu aktifkan mesin lagi → 400.
  - `deactivateAndChangePinTogetherIsRejected`: lihat Fokus review #2. Jari tetap ada.
  - `unchangedPinIsNotChecked`: edit nama karyawan yang PIN-nya menunggu penghapusan → 200.
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run: `./mvnw -q test -Dtest=PinLockTests`. Expected: FAIL.
- [ ] **Step 3: Implementasi.**
- [ ] **Step 4: Jalankan.** Run: `./mvnw -q test -Dtest='PinLockTests,Fingerprint*Tests,MasterDataTests'`. Expected: PASS.
- [ ] **Step 5: Commit.** Berhenti dan laporkan.

### Task 5: Daftar penghapusan, "Coba lagi", dan catatan X401

**Files:**
- Modify: `devicecommand/DeviceCommandService.java`, `DeviceCommandController.java`, `DeviceCommandRepository.java`, `fingerprint/FingerprintService.java`, `FingerprintDtos.java`
- Test: `fingerprint/FingerprintDeleteTests.java`, `security/AccessControlTests.java`

**Interfaces:**
- Produces:
  - `DeviceCommandService.retry(String id): CommandResponse`. Hanya untuk `DELETE_*` `GAGAL`; selain itu 409 dengan pesan di tabel. Memanggil `requeue()`, mencatat audit `COMMAND_RETRY`, dan menerbitkan event SSE seperti `changed`.
  - Endpoint `POST /api/device-commands/{id}/retry`.
  - `DeviceCommandRepository.findUnfinishedDeletesForEmployee(Long employeeId)`: `DELETE_*` berstatus `MENUNGGU`/`DIKIRIM`/`GAGAL`, `join fetch device`, urut `createdAt`.
  - `record DeletionResponse(UUID commandId, String type, Integer finger, Long deviceId, String deviceName, String status, String message, boolean retryable)`. `message`:
    - untuk perintah aktif: `Menunggu (mesin nonaktif)` jika `!device.isActive()`, `Menunggu (mesin offline)` jika `!deviceStatus.isOnline`, dan selain itu `DeviceCommandMessages.message`;
    - untuk `GAGAL`: `DeviceCommandMessages.message` (web menambahkan awalan "Gagal: ").
  - `FingerprintOverview` ditambah `List<DeletionResponse> deletions` dan `boolean zkPushDeviceActive` (ada mesin `ZK_PUSH` aktif).

- [ ] **Step 1: Tulis test yang gagal.**
  - `overviewListsDeletionsPerMachineWithStatus`: online, offline (`last_seen_at` dimundurkan), dan nonaktif.
  - `retryRequeuesFailedDelete`: setelah `GAGAL`, `retry` → `MENUNGGU`, heartbeat mengirimnya lagi, dan ada audit `COMMAND_RETRY`.
  - `retryOnlyForFailedDelete`: ENROLL `GAGAL` → 409, dan delete `MENUNGGU` → 409.
  - `successfulDeleteLeavesList`.
  - `zkPushFlag`.
  - AccessControlTests: `{ HttpMethod.POST, command + "/retry", null }`.
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run: `./mvnw -q test -Dtest='FingerprintDeleteTests,AccessControlTests'`. Expected: FAIL.
- [ ] **Step 3: Implementasi.**
- [ ] **Step 4: Jalankan.** Perintah sama ditambah `RakitanCommandTests`. Expected: PASS.
- [ ] **Step 5: Commit.** Berhenti dan laporkan.

### Task 6: Kapasitas sensor

**Files:**
- Modify: `device/Device.java`, `device/DeviceContactService.java`, `device/DeviceDtos.java`, `device/DeviceService.java` (atau tempat `DeviceResponse.from` dipanggil), `ingest/rakitan/RakitanController.java`, `application.properties`
- Test: `ingest/RakitanHeartbeatTests.java`

**Interfaces:**
- Produces:
  - `Device.recordSensor(Integer used, Integer capacity)` hanya menulis jika keduanya tidak null dan `capacity > 0`. Dipanggil dari `contactByKey` dengan `sensor` dari heartbeat. Mekanisme penulisannya sama dengan `recordContact`.
  - `contactByKey(String bearerKey, String ip, String firmware, String info, Integer sensorUsed, Integer sensorCapacity)`.
  - `DeviceResponse` ditambah `Integer sensorUsed, Integer sensorCapacity, boolean sensorAlmostFull`. `sensorAlmostFull` bernilai `used * 100 > capacity * percent`, dengan `percent` dari `app.device.sensor-warning-percent`, dihitung oleh pemanggil `from`.

- [ ] **Step 1: Tulis test yang gagal.**
  - `heartbeatStoresSensorCapacity`: `{"sensor":{"used":12,"capacity":150}}` → `GET /api/devices/{id}`: `sensorUsed` 12, `sensorCapacity` 150, `sensorAlmostFull` false.
  - `sensorAboveThresholdIsFlagged`: 136/150 → true. 135/150 tepat 90% → false.
  - `heartbeatWithoutSensorKeepsLastValue`.
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run: `./mvnw -q test -Dtest=RakitanHeartbeatTests`. Expected: FAIL.
- [ ] **Step 3: Implementasi.**
- [ ] **Step 4: Jalankan.** Run: `./mvnw -q test -Dtest='RakitanHeartbeatTests,Device*Tests,MasterDataTests,ZkPushIngestTests'`. Expected: PASS.
- [ ] **Step 5: Commit.** Berhenti dan laporkan.

### Task 7: Frontend

**Files:**
- Modify: `frontend/src/lib/types.ts`, `frontend/src/components/FingerprintSection.tsx`, `frontend/src/pages/DevicesPage.tsx`
- Test: `frontend/src/pages/EmployeeFingerprint.test.tsx`, `frontend/src/pages/DevicesPage.test.tsx`

**Interfaces:**
- Consumes: DTO dari Task 5 dan 6.
- Produces:
  - `DeviceCommand.type` menjadi `'ENROLL' | 'DELETE_TEMPLATE' | 'DELETE_EMPLOYEE'`.
  - Type baru `Deletion` (mengikuti `DeletionResponse`).
  - `FingerprintOverview.deletions` dan `zkPushDeviceActive`.
  - `Device.sensorUsed`, `sensorCapacity`, `sensorAlmostFull`.

- [ ] **Step 1: Tulis test yang gagal** (Vitest + MSW/mocking yang sudah dipakai di file test).
  - `EmployeeFingerprint.test.tsx`:
    - tombol Hapus per jari memakai `ConfirmDeleteButton` (label `jari ke-1`), lalu memanggil `DELETE …/fingerprints/1`;
    - daftar "Penghapusan di mesin" menampilkan nama mesin dan pesan;
    - `GAGAL` menampilkan `Gagal: {pesan}` dan tombol "Coba lagi" yang memanggil `POST /api/device-commands/{id}/retry`;
    - catatan X401 tampil jika `zkPushDeviceActive` dan (`deletions` tidak kosong atau karyawan nonaktif).
  - `DevicesPage.test.tsx`: alat rakitan menampilkan `Sensor 12/150`, dan `sensorAlmostFull` menampilkan `Kapasitas sensor hampir penuh.`
- [ ] **Step 2: Jalankan dan pastikan gagal.** Run (di `frontend/`): `npx vitest run src/pages/EmployeeFingerprint.test.tsx src/pages/DevicesPage.test.tsx`. Expected: FAIL.
- [ ] **Step 3: Implementasi.** Panggilan API lewat `src/lib/api.ts`. Data dimuat ulang memakai invalidasi query yang sudah dipakai `FingerprintSection`. Tampilan mengikuti komponen dan gaya yang sudah ada; jangan mengedit `src/components/ui`.
- [ ] **Step 4: Jalankan.** Perintah sama, lalu `npm test -- --run` dan `npm run build`. Expected: PASS.
- [ ] **Step 5: Commit.** Berhenti dan laporkan.

### Task 8: Simulator, dokumen, dan pemeriksaan akhir

**Files:**
- Modify: `tools/device-simulator/rakitan.mjs`, `docs/protokol-alat-rakitan.md`, `docs/keputusan.md`, `docs/pertanyaan-terbuka.md` (Q-019)

- [ ] **Step 1: Simulator.** Mode `live` mengerjakan `DELETE_TEMPLATE`/`DELETE_EMPLOYEE` dari heartbeat. Hasilnya diatur `--delete-result BERHASIL` (bawaan) atau kode error, dan dicetak `Layar: Sidik jari dihapus`. Heartbeat mengirim `sensor` dari `--sensor-used` (bawaan 12) dan `--sensor-capacity` (bawaan 150). Perbarui komentar pemakaian di kepala file.
- [ ] **Step 2: Uji simulator.** Jalankan backend milik pengguna jika sedang berjalan; kalau tidak, lewati dan laporkan. Run `node tools/device-simulator/rakitan.mjs live --code … --delete-result BERHASIL`, hapus satu jari dari web, lalu pastikan daftar penghapusan kosong. Jika langkah ini dilewati, catat sebagai uji manual untuk pengguna.
- [ ] **Step 3: Protokol.** Tambahkan bagian perintah hapus: format item, idempoten (jari yang tidak ada = `BERHASIL`), tanpa laporan langkah (409), dikerjakan sesuai urutan daftar, tidak kedaluwarsa, tidak dibatalkan, dan field `sensor` dipakai untuk peringatan kapasitas. Tambahkan juga baris simulator di tabel uji.
- [ ] **Step 4: Keputusan.** Tambahkan **K-102** "Rincian Rencana 5" di tabel dan rincian, merangkum §17 dan keputusan pengguna A, B, dan tanggal keluar. Perbarui baris K-090 dan K-093 di tabel dengan rujukan ke K-102. Di Q-019, penghapusan saat nonaktif ditandai terjawab (K-102).
- [ ] **Step 5: Pemeriksaan penuh.** Backend `./mvnw -q test` dari salinan, frontend `npm test -- --run`, dan `npm run build`. Laporkan jumlah aslinya.
- [ ] **Step 6: Perbarui memori** `rencana-3-status.md`: Rencana 5 selesai, dan yang berikutnya Rencana 6.
- [ ] **Step 7: Commit.** Berhenti dan laporkan.
