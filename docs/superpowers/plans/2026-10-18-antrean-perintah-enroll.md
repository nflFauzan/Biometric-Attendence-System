# Antrean Perintah dan Pendaftaran Sidik Jari dari Web — Rencana Implementasi

> **Untuk agent pelaksana:** WAJIB memakai superpowers:subagent-driven-development (disarankan) atau superpowers:executing-plans untuk mengerjakan rencana ini per task. Langkah memakai checkbox (`- [ ]`).

**Tujuan:** admin mendaftarkan sidik jari karyawan dari halaman Detail Karyawan. Server menitipkan perintah `ENROLL` ke alat rakitan lewat heartbeat, mengikuti progresnya, dan mencatat pendaftaran yang berhasil. Simulator menggantikan mesin.

**Arsitektur:**
- Modul baru `devicecommand` berisi antrean perintah umum: entity, siklus status, batas waktu, endpoint web untuk melihat dan membatalkan, serta sisi mesin (kirim lewat heartbeat, progress, result).
- Modul baru `fingerprint` berisi persetujuan karyawan, catatan sidik jari terdaftar, dan pembuatan perintah `ENROLL`. Ketergantungannya satu arah: `fingerprint` → `devicecommand`.
- Hasil `ENROLL` yang berhasil diteruskan ke `fingerprint` lewat event Spring sinkron, di transaksi yang sama.
- Web mengikuti status lewat SSE yang sudah ada. Event hanya membawa ID perintah; data diambil ulang lewat API biasa, dengan polling 3 detik sebagai cadangan.

**Tech stack:** Spring Boot 4 (Java 21), Flyway, PostgreSQL (partial unique index), Jackson 3, JUnit 5 + MockMvc + Testcontainers + `@RecordApplicationEvents`; React + TypeScript + TanStack Query + Vitest; Node.js (simulator).

**Spec:** [`docs/superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md`](../specs/2026-10-16-pendaftaran-sidik-jari-web-design.md):
- §3.2, §3.6, §3.7, §4, §9;
- **§16**, yang mengikat: disetujui pengguna 2026-10-17, "approve semua".

Kontrak: [`docs/protokol-alat-rakitan.md`](../../protokol-alat-rakitan.md). Ini rencana 3 dari 7.

## Batasan global

**Template dan data perintah**
- **Template sidik jari tidak pernah disimpan, di-log, atau diaudit di Rencana 3.** `ResultRequest` tidak punya field `template`, sehingga Jackson mengabaikannya (Boot mematikan `FAIL_ON_UNKNOWN_PROPERTIES`).
- Isi perintah hanya PIN, jari (1 atau 2), dan nama tampilan, tanpa data rahasia.

**Status, jenis, dan kode**
- Jenis perintah: `ENROLL`.
- Status: `MENUNGGU`, `DIKIRIM`, `BERHASIL`, `GAGAL`, `KEDALUWARSA`, `DIBATALKAN`. "Aktif" berarti `MENUNGGU` atau `DIKIRIM`.
- Kode error mesin untuk `ENROLL`: `JARI_TIDAK_COCOK`, `KUALITAS_BURUK`, `JARI_SUDAH_TERDAFTAR`, `SENSOR_PENUH`, `WAKTU_HABIS`, `SENSOR_ERROR`. Kode lain disimpan apa adanya (maksimal 40 karakter).
- Error mesin tetap berupa JSON `{"error":"<KODE>"}`, ditambah 404 `PERINTAH_TIDAK_DIKENAL` dan 409 `PERINTAH_TIDAK_AKTIF`.

**Pesan pengguna** (persis; sebagian dari spec §9)

| Kondisi | Pesan |
|---|---|
| Aktif, tanpa langkah | `Menunggu mesin…` |
| Aktif, langkah n | `Tempelkan jari (n/3)` |
| `BERHASIL` | `Berhasil` |
| `KEDALUWARSA` | `Mesin tidak mengambil perintah. Periksa koneksi mesin.` |
| `DIBATALKAN` | `Pendaftaran dibatalkan.` |
| `GAGAL` + `JARI_TIDAK_COCOK` | `Tiga tempelan tidak cocok satu sama lain. Ulangi dengan jari yang sama.` |
| `GAGAL` + `KUALITAS_BURUK` | `Sidik jari tidak terbaca jelas. Bersihkan jari atau sensor, lalu ulangi.` |
| `GAGAL` + `JARI_SUDAH_TERDAFTAR` | `Sidik jari ini sudah terdaftar atas nama {nama}.` |
| `GAGAL` + `SENSOR_PENUH` | `Kapasitas sensor mesin penuh.` |
| `GAGAL` + `WAKTU_HABIS` | `Mesin tidak merespons. Periksa koneksi mesin.` |
| `GAGAL` + `SENSOR_ERROR` | `Sensor bermasalah. Hubungi teknisi.` |
| `GAGAL` + kode lain | `Pendaftaran gagal ({kode}).` |

Untuk `JARI_SUDAH_TERDAFTAR`, `{nama}` diisi nama karyawan pemilik PIN, atau `PIN {pin}` jika PIN tidak dikenal.

**Batas waktu** (K-094; parameter teknis di `application.properties`, bukan aturan bisnis)

```
app.device.enroll.pickup-timeout=30s
app.device.enroll.machine-timeout=60s
app.device.enroll.result-timeout=75s
app.device.command-sweep=5s
app.device.cancel-notice=2m
```

**Lain-lain**
- Perubahan schema hanya lewat `V8__antrean_perintah.sql`.
- Setiap endpoint web baru masuk `SecurityConfig` dan `AccessControlTests`.
- Setiap perubahan data dicatat `AuditService` di transaksi yang sama. Pelaku untuk laporan mesin adalah `mesin <serial>`.
- Error web berformat ProblemDetail (`ApiException`) dengan pesan Bahasa Indonesia.
- **Commit dan push dikerjakan pengguna.** Langkah "Commit (oleh pengguna)" berarti: berhenti, laporkan file yang berubah, lalu lanjut.

## Fokus review

1. **Batal dan BERHASIL datang bersamaan.** Admin menekan Batal tepat saat mesin selesai. Status tetap `DIBATALKAN`, tidak ada baris `employee_fingerprints`, dan mesin tetap dibalas 200 supaya berhenti mengirim. Selisih isi mesin dibereskan lewat rekonsiliasi di Rencana 4. Test: Task 3 `resultAfterCancelIsIgnored`.
2. **Template di body hasil tidak tersimpan di mana pun.** Test: Task 3 `templateInResultIsNeverStored` memeriksa bahwa `audit_logs` dan `device_commands` tidak memuat teks template.
3. **Dua admin klik "Daftarkan" bersamaan.** Tepat satu perintah aktif, dan klik kedua mendapat 409 dengan pesan Indonesia, bukan 500. Test: Task 1 `secondActiveEnrollForDeviceViolatesIndex` (index) dan Task 2 `concurrentEnrollBackstopReturns409`.
4. **Mesin putus di tengah pendaftaran.** Tanpa hasil selama 75 detik, status menjadi `GAGAL` + `WAKTU_HABIS`, dan dialog menampilkan pesan koneksi. Test: Task 1 `sentWithoutResultTimesOut`, dan Task 4 `dialog shows timeout message`.
5. **SSE terputus atau tidak tersedia.** Dialog tetap berganti status lewat polling. Semua test frontend berjalan tanpa `EventSource` (jsdom), jadi test dialog Task 4 sekaligus membuktikan cadangan ini.

---

### Task 1: Schema, model perintah, dan batas waktu

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__antrean_perintah.sql`
- Create: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommand.java`
- Create: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandRepository.java`
- Create: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandChangedEvent.java`
- Create: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandMessages.java`
- Create: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandService.java` (bagian `expireStale`)
- Create: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandSweeper.java`
- Create: `backend/src/main/java/com/biometricattendance/fingerprint/EmployeeFingerprint.java`
- Create: `backend/src/main/java/com/biometricattendance/fingerprint/EmployeeFingerprintRepository.java`
- Modify: `backend/src/main/java/com/biometricattendance/employee/Employee.java` (kolom persetujuan)
- Modify: `backend/src/main/java/com/biometricattendance/dashboard/DashboardStream.java` (event `DEVICE_COMMAND`)
- Modify: `backend/src/main/resources/application.properties` (5 properti batas waktu)
- Test: `backend/src/test/java/com/biometricattendance/devicecommand/DeviceCommandLifecycleTests.java`
- Test: `backend/src/test/java/com/biometricattendance/devicecommand/DeviceCommandMessagesTests.java`

**Interfaces:**
- Produces:
  - **`DeviceCommand`**
    - Konstanta `TYPE_ENROLL`, `MENUNGGU`, `DIKIRIM`, `BERHASIL`, `GAGAL`, `KEDALUWARSA`, `DIBATALKAN`.
    - Factory `static DeviceCommand enroll(Device device, Employee employee, int finger, String createdBy, Instant now)`: ID `UUID.randomUUID()`; `devicePin` dan `displayName` disalin dari karyawan.
    - Getter: `getId(): UUID`, `getDevice()`, `getEmployee()`, `getType()`, `getStatus()`, `getFinger(): Integer`, `getDevicePin()`, `getDisplayName()`, `getStep(): Integer`, `getErrorCode()`, `getErrorDetail()`, `getCreatedAt()`, `getSentAt()`, `getFinishedAt()`.
    - `boolean isActive()`.
    - Transisi: `markSent(Instant)` (hanya dari `MENUNGGU`), `recordProgress(int step)`, `succeed(Instant)`, `fail(String code, String detail, Instant)`, `expire(Instant)`, `cancel(Instant)`.
  - **`DeviceCommandRepository`**
    - `boolean existsActiveEnrollForDevice(Long deviceId)`
    - `boolean existsActiveEnroll(Long employeeId, int finger)`
    - `List<DeviceCommand> findActiveForEmployee(Long employeeId)`
    - `List<DeviceCommand> findActiveForDevice(Long deviceId)`
    - `List<DeviceCommand> findRecentlyCancelledSent(Long deviceId, Instant since)`
    - `List<DeviceCommand> findUnpickedBefore(Instant cutoff)` (`MENUNGGU`, `createdAt` < cutoff)
    - `List<DeviceCommand> findSentBefore(Instant cutoff)` (`DIKIRIM`, `sentAt` < cutoff)
  - **Event dan pesan**
    - `record DeviceCommandChangedEvent(UUID commandId, Long employeeId)`.
    - `DeviceCommandMessages.message(DeviceCommand c): String` mengikuti tabel pesan.
  - **`DeviceCommandService`**
    - `int expireStale(Instant now)` mengubah status, mencatat audit `COMMAND_EXPIRED` dengan pelaku `sistem`, dan menerbitkan `DeviceCommandChangedEvent` untuk setiap perubahan.
    - Batas waktu dibaca lewat `@Value("${app.device.enroll.pickup-timeout:30s}") Duration` dan `result-timeout:75s`.
  - **`EmployeeFingerprint` dan repository**
    - `EmployeeFingerprint`: kolom `employeeId`, `finger`, `device`, `commandId`, `enrolledAt`.
    - `EmployeeFingerprint.replace(Device, UUID commandId, Instant)`.
    - `EmployeeFingerprintRepository.findByEmployeeIdOrderByFinger(Long)` dan `findByEmployeeIdAndFinger(Long, int)`.
  - **`Employee`**: `getFingerprintConsentAt()`, `getFingerprintConsentBy()`, `recordFingerprintConsent(String by, Instant at)`.

- [ ] **Step 1: Tulis migration `V8__antrean_perintah.sql`**

```sql
ALTER TABLE employees ADD COLUMN fingerprint_consent_at TIMESTAMPTZ, ADD COLUMN fingerprint_consent_by VARCHAR(100);

CREATE TABLE device_commands (
    id            UUID PRIMARY KEY,
    device_id     BIGINT NOT NULL REFERENCES devices (id),
    type          VARCHAR(30) NOT NULL CHECK (type IN ('ENROLL')),
    status        VARCHAR(20) NOT NULL CHECK (status IN ('MENUNGGU','DIKIRIM','BERHASIL','GAGAL','KEDALUWARSA','DIBATALKAN')),
    employee_id   BIGINT REFERENCES employees (id),
    finger        SMALLINT CHECK (finger IN (1, 2)),
    device_pin    VARCHAR(20),
    display_name  VARCHAR(150),
    step          SMALLINT,
    error_code    VARCHAR(40),
    error_detail  VARCHAR(255),
    created_by    VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    sent_at       TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ
);
CREATE UNIQUE INDEX ux_device_commands_enroll_device ON device_commands (device_id)
    WHERE type = 'ENROLL' AND status IN ('MENUNGGU', 'DIKIRIM');
CREATE UNIQUE INDEX ux_device_commands_enroll_finger ON device_commands (employee_id, finger)
    WHERE type = 'ENROLL' AND status IN ('MENUNGGU', 'DIKIRIM');
CREATE INDEX ix_device_commands_device_status ON device_commands (device_id, status);

CREATE TABLE employee_fingerprints (
    id           BIGSERIAL PRIMARY KEY,
    employee_id  BIGINT NOT NULL REFERENCES employees (id),
    finger       SMALLINT NOT NULL CHECK (finger IN (1, 2)),
    device_id    BIGINT NOT NULL REFERENCES devices (id),
    command_id   UUID NOT NULL REFERENCES device_commands (id),
    enrolled_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (employee_id, finger)
);
```

Sesuaikan panjang `display_name` dengan panjang kolom `full_name` karyawan di V1.

- [ ] **Step 2: Tulis test yang gagal**

`DeviceCommandLifecycleTests extends RakitanFixtures` (agar bisa memakai `createRakitan`, `createEmployee`), dengan `@Autowired DeviceCommandRepository`, `DeviceCommandService`, `DeviceRepository`, `EmployeeRepository`, dan `TransactionTemplate`:
- `secondActiveEnrollForDeviceViolatesIndex`: simpan dua `DeviceCommand.enroll` (karyawan berbeda) untuk mesin yang sama → `saveAndFlush` kedua melempar `DataIntegrityViolationException`.
- `finishedEnrollDoesNotBlockNewOne`: perintah pertama `cancel(now)`, lalu perintah kedua untuk mesin yang sama bisa disimpan.
- `unpickedEnrollExpiresAfter30Seconds`: perintah dibuat pada `t`, lalu `expireStale(t + 29 s)` → tetap `MENUNGGU`; `expireStale(t + 31 s)` → `KEDALUWARSA` dan `finishedAt` terisi.
- `sentWithoutResultTimesOut`: `markSent(t)`, lalu `expireStale(t + 74 s)` → tetap `DIKIRIM`; `expireStale(t + 76 s)` → `GAGAL`, `errorCode` `WAKTU_HABIS`.
- `expiryPublishesChangedEvent`: `@RecordApplicationEvents`; setelah `expireStale` ada satu `DeviceCommandChangedEvent` dengan `commandId` yang benar, dan satu audit `COMMAND_EXPIRED` dengan pelaku `sistem`.

`DeviceCommandMessagesTests` (unit, tanpa Spring): setiap baris tabel pesan di Batasan global, termasuk `Tempelkan jari (2/3)`, `Sidik jari ini sudah terdaftar atas nama Budi.` (`errorDetail` = "Budi"), dan `Pendaftaran gagal (KODE_ANEH).`.

- [ ] **Step 3: Jalankan test, pastikan gagal**

Run: `cd backend && ./mvnw -q test -Dtest='DeviceCommandLifecycleTests,DeviceCommandMessagesTests'`
Expected: compilation error (`DeviceCommand` belum ada).

- [ ] **Step 4: Implementasi**

- **Entity dan repository:** entity, repository (query JPQL dengan status aktif sebagai parameter atau literal), event, pesan, dan `expireStale`.
- **`DeviceCommandSweeper`:** `@Scheduled(fixedDelayString = "${app.device.command-sweep:5s}")` yang memanggil `expireStale(Instant.now())`.
- **`DashboardStream.onDeviceCommandChanged`:** `@TransactionalEventListener` yang mem-broadcast `{"type":"DEVICE_COMMAND","commandId":"<uuid>"}`.
- **`Employee`:** kolom persetujuan.
- **`application.properties`:** 5 properti batas waktu.

Transisi yang tidak sah, misalnya `succeed` pada perintah yang tidak aktif, melempar `IllegalStateException`. Service selalu memeriksa `isActive()` lebih dulu.

- [ ] **Step 5: Jalankan test, pastikan lulus**

Run: perintah Step 3.
Expected: `Tests run: …, Failures: 0, Errors: 0`. Baca dari `backend/target/surefire-reports`.

- [ ] **Step 6: Commit (oleh pengguna)**

---

### Task 2: API web — persetujuan, mulai pendaftaran, lihat, dan batalkan

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/fingerprint/FingerprintDtos.java`
- Create: `backend/src/main/java/com/biometricattendance/fingerprint/FingerprintService.java`
- Create: `backend/src/main/java/com/biometricattendance/fingerprint/FingerprintController.java`
- Create: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandDtos.java`
- Create: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandController.java`
- Modify: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandService.java` (`get`, `cancel`)
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java`
- Modify: `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java`
- Test: `backend/src/test/java/com/biometricattendance/fingerprint/FingerprintApiTests.java`

**Interfaces:**
- Consumes: Task 1, `DeviceStatus.isOnline(Device, Instant)`, `AuditService.record(...)`, `CurrentUser.username()`, `ApiException`.
- Produces:
  - **HTTP**

    | Endpoint | Body | Balasan |
    |---|---|---|
    | `GET /api/employees/{id}/fingerprints` | | `FingerprintOverview` |
    | `POST /api/employees/{id}/fingerprint-consent` | | 200 `FingerprintOverview` |
    | `POST /api/employees/{id}/fingerprints/enroll` | `{"deviceId":5,"finger":1}` | 201 `CommandResponse` |
    | `GET /api/device-commands/{id}` | | `CommandResponse` |
    | `POST /api/device-commands/{id}/cancel` | | 200 `CommandResponse` |

  - **DTO**
    - `CommandResponse(UUID id, String type, String status, Long deviceId, String deviceName, Long employeeId, Integer finger, Integer step, String errorCode, String message, Instant createdAt, Instant finishedAt)` dengan `static from(DeviceCommand)`.
    - `FingerprintOverview(Instant consentAt, String consentBy, List<FingerResponse> fingers, List<CommandResponse> activeCommands)`.
    - `FingerResponse(int finger, Long deviceId, String deviceName, Instant enrolledAt)`.
    - `EnrollRequest(@NotNull(message = "Pilih mesin") Long deviceId, @NotNull @Min(1) @Max(2) Integer finger)`, dengan pesan "Jari harus 1 atau 2".

- [ ] **Step 1: Tulis test yang gagal**

`FingerprintApiTests extends RakitanFixtures`. Helper `consent(employeeId)` dan `enroll(employeeId, deviceId, finger)`. Mesin dibuat Online dengan satu `heartbeat(key, "{}")`.

**Persetujuan**
- `consentIsRecordedOnceWithAudit`: `POST …/fingerprint-consent` dua kali, lalu periksa:
  - `consentAt` tidak berubah pada panggilan kedua;
  - `consentBy` = `owner-absensi`;
  - hanya ada satu audit `FINGERPRINT_CONSENT`.

**Mulai pendaftaran**
- `enrollCreatesWaitingCommand`: 201, `status` `MENUNGGU`, `message` `Menunggu mesin…`, `finger` 1; ada audit `ENROLL_REQUEST`.
- `enrollPreconditions` (parameterized). Setiap kondisi gagal dengan status dan `detail` persis berikut:

  | Kondisi | Status | `detail` |
  |---|---|---|
  | Tanpa persetujuan | 400 | `Centang persetujuan karyawan dulu.` |
  | PIN kosong | 400 | `Isi PIN mesin karyawan dulu.` |
  | Karyawan nonaktif | 400 | `Karyawan nonaktif tidak bisa didaftarkan sidik jarinya.` |
  | Mesin ZK-Push | 400 | `Mesin ini tidak mendukung pendaftaran dari web; daftarkan langsung di mesin.` |
  | Mesin nonaktif | 400 | `Mesin nonaktif.` |
  | Mesin belum pernah heartbeat | 400 | `Mesin sedang offline.` |
  | Mesin tidak ada | 404 | |
  | `finger` 3 | 400 | `Jari harus 1 atau 2` |

**Satu pendaftaran aktif**
- `oneActiveEnrollPerDevice`: karyawan A di mesin M, lalu karyawan B di mesin M → 409 `Mesin sedang dipakai untuk pendaftaran.`
- `oneActiveEnrollPerFinger`: karyawan A jari 1 di mesin M1, lalu A jari 1 di mesin M2 → 409 `Jari ini sedang didaftarkan.`
- `staleCommandDoesNotBlock`: ubah `created_at` perintah lewat JDBC menjadi 31 detik lalu → pendaftaran baru di mesin yang sama 201. Ini membuktikan `expireStale` dipanggil sebelum pemeriksaan.
- `concurrentEnrollBackstopReturns409`: sisipkan baris aktif lewat JDBC tepat sebelum `saveAndFlush`. Caranya: `@MockitoSpyBean DeviceCommandRepository` yang `existsActiveEnrollForDevice` dibuat mengembalikan `false` → tetap 409 `Mesin sedang dipakai untuk pendaftaran.`, bukan 500.

**Lihat dan batalkan**
- `overviewListsFingersAndActiveCommands`: `fingers` kosong dan `activeCommands` berisi satu perintah.
- `cancelActiveCommand`: `POST /api/device-commands/{id}/cancel` → `DIBATALKAN`, pesan `Pendaftaran dibatalkan.`, ada audit `CANCEL_COMMAND`. Membatalkan untuk kedua kali → 409 `Perintah sudah selesai.`
- `unknownCommandIs404`: UUID acak → 404; teks bukan UUID → 404.

**`AccessControlTests`**
- Tambah `fingerprintAndCommandEndpointsForOwnerAndHrOnly`. Karyawan → 403 dan anonim → 401 untuk kelima endpoint.
- Owner dan HR melewati keamanan (status apa pun selain 401/403).

- [ ] **Step 2: Jalankan test, pastikan gagal**

Run: `cd backend && ./mvnw -q test -Dtest='FingerprintApiTests,AccessControlTests'`
Expected: compilation error, atau 403 dari `denyAll` untuk `/api/device-commands/**`.

- [ ] **Step 3: Implementasi**

- **Urutan `FingerprintService.startEnroll`:**
  1. Cari karyawan (404).
  2. Periksa karyawan aktif dan `endDate` kosong.
  3. Periksa PIN terisi.
  4. Periksa persetujuan tercatat.
  5. Cari mesin (404).
  6. Periksa `isRakitan`, mesin aktif, dan mesin Online.
  7. Panggil `commands.expireStale(now)`.
  8. Jalankan `existsActiveEnrollForDevice`, lalu `existsActiveEnroll`.
  9. `saveAndFlush` dan audit.
- **Penanganan `DataIntegrityViolationException`:** jika pesan memuat nama constraint `ux_device_commands_enroll_device`, balas 409 untuk mesin; selain itu balas 409 untuk jari.
- **`DeviceCommandController`** dipetakan ke `/api/device-commands`.
- **`SecurityConfig`:** tambah `"/api/device-commands/**"` ke grup `hasAnyRole(OWNER, HR_FINANCE)` yang berisi `/api/employees/**`.

- [ ] **Step 4: Jalankan test, pastikan lulus**

Run: perintah Step 2.
Expected: `Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit (oleh pengguna)**

---

### Task 3: Sisi mesin — heartbeat membawa perintah, progress, dan hasil

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/ingest/rakitan/RakitanDtos.java`
- Modify: `backend/src/main/java/com/biometricattendance/ingest/rakitan/RakitanController.java`
- Modify: `backend/src/main/java/com/biometricattendance/devicecommand/DeviceCommandService.java` (`deliver`, `progress`, `result`)
- Create: `backend/src/main/java/com/biometricattendance/devicecommand/EnrollSucceededEvent.java`
- Create: `backend/src/main/java/com/biometricattendance/fingerprint/EnrollmentRecorder.java`
- Test: `backend/src/test/java/com/biometricattendance/ingest/RakitanCommandTests.java`
- Modify: `backend/src/test/java/com/biometricattendance/ingest/RakitanFixtures.java` (helper `progress`, `result`)

**Interfaces:**
- Consumes: Task 1, Task 2 (`enroll` di test), `DeviceContactService.contactByKey`.
- Produces:
  - **Heartbeat response** menjadi `HeartbeatResponse(String serverTime, String timezone, String deviceName, String branchName, List<CommandItem> commands, List<String> cancel)`.
    - `CommandItem(String id, String type, String pin, int finger, String displayName, long timeoutSeconds)`; `timeoutSeconds` diisi dari `app.device.enroll.machine-timeout`.
    - `cancel` berisi ID perintah `DIBATALKAN` yang pernah dikirim dan selesai dalam `app.device.cancel-notice` terakhir.
  - **Request DTO:** `ProgressRequest(Integer step)` dan `ResultRequest(String status, String error, String pin)`. Tidak ada field `template`.
  - **`DeviceCommandService`**
    - `Delivery deliver(Device device, Instant now)`, dengan `record Delivery(List<DeviceCommand> commands, List<UUID> cancel)`. `MENUNGGU` yang masih dalam batas pengambilan menjadi `DIKIRIM`, dan `DIKIRIM` ikut dikirim ulang.
    - `CommandUpdate progress(Device device, String commandId, Integer step)` dan `CommandUpdate result(Device device, String commandId, ResultRequest body)`, dengan `enum CommandUpdate { OK, NOT_FOUND, NOT_ACTIVE, BAD_REQUEST }`.
  - **Event:** `record EnrollSucceededEvent(UUID commandId, Long employeeId, int finger, Long deviceId, Instant at)`, ditangani `EnrollmentRecorder` dengan `@EventListener` sinkron yang meng-upsert `employee_fingerprints`.
  - **HTTP mesin:** `POST /device/v1/commands/{id}/progress` dan `POST /device/v1/commands/{id}/result`. Autentikasi sama dengan heartbeat (401/403).

    | `CommandUpdate` | Balasan |
    |---|---|
    | `NOT_FOUND` | 404 `PERINTAH_TIDAK_DIKENAL` |
    | `NOT_ACTIVE` pada progress | 409 `PERINTAH_TIDAK_AKTIF` |
    | `NOT_ACTIVE` pada result | 200 `{}` |
    | `BAD_REQUEST` | 400 `FORMAT_SALAH` |
    | `OK` | 200 `{}` |

- [ ] **Step 1: Tulis test yang gagal**

`RakitanCommandTests extends RakitanFixtures`:

**Pengiriman lewat heartbeat**
- `heartbeatDeliversEnrollAndMarksSent`: setelah `enroll`, heartbeat →
  - `$.commands[0].type` = `ENROLL`, `pin` = PIN karyawan, `finger` = 1, `displayName` = nama karyawan, `timeoutSeconds` = 60;
  - status perintah `DIKIRIM`.
- `sentCommandIsRedeliveredUntilFinished`: heartbeat kedua tetap membawa ID yang sama.
- `otherDevicesDoNotSeeCommand`: heartbeat mesin lain → `commands` kosong.
- `expiredCommandIsNotDelivered`: `created_at` mundur 31 detik lewat JDBC → `commands` kosong.
- `cancelledSentCommandAppearsInCancelList`: kirim, batalkan lewat API, lalu heartbeat → `$.cancel[0]` = ID itu dan `commands` kosong.

**Progress**
- `progressUpdatesStep`: `progress(key, id, 2)` → 200; status perintah `message` = `Tempelkan jari (2/3)`.
- `progressValidation`:
  - step 0 → 400 `FORMAT_SALAH`;
  - step 4 → 400;
  - ID mesin lain → 404 `PERINTAH_TIDAK_DIKENAL`;
  - teks bukan UUID → 404;
  - setelah dibatalkan → 409 `PERINTAH_TIDAK_AKTIF`.

**Hasil**
- `successRecordsFingerprint`: result `{"status":"BERHASIL"}` → 200, lalu periksa:
  - overview `fingers[0]` berisi jari 1 dan nama mesin;
  - status `BERHASIL`;
  - ada audit `COMMAND_RESULT` dengan pelaku `mesin RK-…`.
- `reEnrollReplacesRecord`: pendaftaran kedua untuk jari yang sama di mesin lain berhasil → tetap satu baris, dengan `deviceId` baru.
- `duplicateResultIsIgnored`: kirim BERHASIL dua kali → keduanya 200, satu baris fingerprint, satu audit hasil.
- `resultAfterCancelIsIgnored`: batalkan lalu kirim BERHASIL → 200; status tetap `DIBATALKAN`; `fingers` kosong.
- `failureCarriesMessage`: `{"status":"GAGAL","error":"KUALITAS_BURUK"}` → pesan sesuai tabel.
- `duplicateFingerNamesOwner`: `{"status":"GAGAL","error":"JARI_SUDAH_TERDAFTAR","pin":"<pin karyawan B>"}` → `Sidik jari ini sudah terdaftar atas nama <nama B>.`. Dengan PIN tidak dikenal `7777` → `… atas nama PIN 7777.`
- `badResultStatusIs400`: `{"status":"SELESAI"}` → 400 `FORMAT_SALAH`.
- `templateInResultIsNeverStored`: BERHASIL dengan `"template":"VEVNUExBVEUtUkFIQVNJQQ"` → 200. `select count(*)` dari `audit_logs` dan `device_commands`, dengan semua kolom teks di-cast ke text, yang memuat `VEVNUExBVEUtUkFIQVNJQQ` → 0.
- `transitionsPublishChangedEvent`: `@RecordApplicationEvents` → heartbeat yang mengirim, progress, dan result masing-masing menerbitkan satu `DeviceCommandChangedEvent`.

- [ ] **Step 2: Jalankan test, pastikan gagal**

Run: `cd backend && ./mvnw -q test -Dtest='RakitanCommandTests,RakitanHeartbeatTests'`
Expected: compilation error (`CommandItem` belum ada).

- [ ] **Step 3: Implementasi**

- **Heartbeat:** setelah `contactByKey` berhasil, `RakitanController.heartbeat` memanggil `commands.deliver(device, now)` dan memetakannya ke `CommandItem`. Pemanggilan ini berjalan di transaksi tersendiri.
- **`result` BERHASIL:** `succeed`, terbitkan `EnrollSucceededEvent`, lalu audit.
- **`result` GAGAL:** `fail(code, detail)`. Untuk `JARI_SUDAH_TERDAFTAR`, `detail` diisi `employees.findByDevicePin(pin)` → nama lengkap, atau `PIN <pin>`.
- **Validasi body:** `status` di luar `BERHASIL`/`GAGAL`, atau `GAGAL` tanpa `error`, menghasilkan `BAD_REQUEST`.
- **Penanganan error:** `ExceptionHandler` yang sudah ada tetap menangani JSON rusak (400) dan kegagalan simpan (500 `GAGAL_SIMPAN`). Perbarui test heartbeat lama yang memeriksa `$.commands` kosong supaya juga memeriksa `$.cancel` kosong.

- [ ] **Step 4: Jalankan test, pastikan lulus**

Run: `cd backend && ./mvnw -q test`
Expected: seluruh suite `Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit (oleh pengguna)**

---

### Task 4: Frontend — bagian "Sidik Jari" dan dialog pendaftaran

**Files:**
- Modify: `frontend/src/lib/types.ts`
- Modify: `frontend/src/lib/useDashboardStream.ts` (tipe `DEVICE_COMMAND`, `commandId?: string`)
- Create: `frontend/src/components/FingerprintSection.tsx`
- Create: `frontend/src/components/EnrollDialog.tsx`
- Modify: `frontend/src/pages/EmployeeDetailPage.tsx` (pasang `<FingerprintSection employee={data} />` di bawah bagian yang ada)
- Test: `frontend/src/pages/EmployeeFingerprint.test.tsx`

**Interfaces:**
- Consumes: HTTP Task 2, event SSE `DEVICE_COMMAND` dari Task 1, dan `GET /api/devices`.
- Produces:
  - Tipe: `DeviceCommand`, `FingerprintOverview`, `FingerRecord` (sama dengan DTO Task 2) dan `CommandStatus`.
  - `FingerprintSection({ employee }: { employee: Employee })`.
  - `EnrollDialog({ employee, finger, onClose })`.

**Perilaku**

`FingerprintSection`:
- **Judul:** "Sidik Jari".
- **Persetujuan:**
  - checkbox berlabel "Karyawan sudah menyetujui penyimpanan sidik jari";
  - setelah dicentang, checkbox terkunci dan tampil "Disetujui oleh {consentBy}, {tanggal}";
  - mencentang memanggil `POST …/fingerprint-consent`.
- **Baris jari ke-1 dan ke-2:** status "Terdaftar di {mesin}, {tanggal}" atau "Belum terdaftar". Tombolnya "Daftarkan" atau "Daftar ulang"; "Daftar ulang" membuka konfirmasi "Daftar ulang jari ke-{n}?".
- **Tombol nonaktif disertai alasan:**
  - "Centang persetujuan karyawan dulu.";
  - "Isi PIN mesin karyawan dulu.";
  - "Karyawan nonaktif.".

`EnrollDialog`:
- **Pilihan mesin:** `GET /api/devices` disaring `protocol === 'RAKITAN_V1' && active && online`. Bawaannya mesin di cabang karyawan.
- **Tanpa alat rakitan Online:** tampil "Tidak ada alat rakitan yang online. Mesin X401 tidak mendukung pendaftaran dari web; daftarkan langsung di mesin."
- **Mulai:** "Mulai" memanggil `POST …/enroll`.
- **Mengikuti status:**
  - `useQuery(['/api/device-commands', id])` dengan `refetchInterval: 3000` selama perintah aktif;
  - di-invalidate saat event `DEVICE_COMMAND` dengan `commandId` yang sama;
  - yang ditampilkan `message` dari server, dengan `role="status"`.
- **Batal:** tombol "Batal" saat aktif memanggil `POST …/cancel`.
- **Selesai:** saat status final, tombol berganti "Tutup", dan query overview karyawan di-invalidate.
- **Error API** (409/400) ditampilkan dengan `ErrorAlert`.

- [ ] **Step 1: Tulis test yang gagal**

`EmployeeFingerprint.test.tsx`, memakai `mockFetch` dan pola `renderAt('/karyawan/12')` dari `DevicesPage.test.tsx`:
- **Tombol terkunci tanpa persetujuan:** `consent locks enroll until checked` — tombol "Daftarkan" jari ke-1 disabled dan alasannya tampil. Setelah checkbox dicentang, ada `POST /api/employees/12/fingerprint-consent` dan tombol aktif.
- **Daftar alat rakitan kosong:** `no online rakitan shows X401 message` — `/api/devices` hanya berisi ZK-Push Online dan alat rakitan offline → teks X401 tampil, tombol "Mulai" tidak ada.
- **Dialog mengikuti status:** `dialog follows command status to success`:
  1. `GET /api/device-commands/<id>` mengembalikan `Menunggu mesin…`, lalu `Tempelkan jari (1/3)`, lalu `Berhasil`; status berganti lewat polling.
  2. Gunakan `vi.useFakeTimers({ shouldAdvanceTime: true })`, atau handler yang mengubah balasan setiap kali dipanggil disertai `waitFor` dengan timeout 10 detik.
  3. Teks muncul berurutan, lalu tombol "Tutup" muncul.
- **Batal:** `cancel posts and shows cancelled` — "Batal" memanggil `POST /api/device-commands/<id>/cancel` → `Pendaftaran dibatalkan.`.
- **Pesan waktu habis:** `dialog shows timeout message` — perintah `GAGAL` dengan `message` `Mesin tidak merespons. Periksa koneksi mesin.` → teks tampil.
- **Konfirmasi daftar ulang:** `re-enroll asks for confirmation` — jari ke-1 terdaftar, lalu "Daftar ulang" → dialog "Daftar ulang jari ke-1?".

- [ ] **Step 2: Jalankan test, pastikan gagal**

Run: `cd frontend && npx vitest run src/pages/EmployeeFingerprint.test.tsx`
Expected: FAIL (teks "Sidik Jari" tidak ditemukan).

- [ ] **Step 3: Implementasi** komponen dan tipe sesuai bagian Perilaku. Ikuti gaya bagian yang ada di `EmployeeDetailPage`: `section` dengan `rounded-lg border glass`, judul `h2`. Komponen dialog memakai `Dialog` shadcn seperti `SetupCodeDialog`.

- [ ] **Step 4: Jalankan semua test frontend, lint, dan build**

Run: `cd frontend && npm test && npm run lint && npm run build`
Expected: semua test lulus, lint exit 0, build sukses.

- [ ] **Step 5: Commit (oleh pengguna)**

---

### Task 5: Simulator `ENROLL` dan dokumentasi

**Files:**
- Modify: `tools/device-simulator/rakitan.mjs`
- Modify: `docs/protokol-alat-rakitan.md`
- Modify: `docs/keputusan.md`
- Modify: `docs/pertanyaan-terbuka.md`

**Interfaces:**
- Consumes: kontrak HTTP mesin dari Task 3.

- [ ] **Step 1: Simulator.** `live` membaca `commands` dan `cancel` dari setiap heartbeat.
  - Untuk `ENROLL` yang belum dikerjakan (ID dicatat dalam `Set`), cetak `Layar: Daftarkan jari {finger} — {displayName}`. Lalu kirim progress 1, 2, 3 dengan jeda `--enroll-delay` detik (bawaan 2), kemudian result.
  - Opsi `--enroll-result <BERHASIL|KODE>` (bawaan `BERHASIL`) dan `--owner-pin <pin>` untuk `JARI_SUDAH_TERDAFTAR`.
  - Berhenti tanpa mengirim result jika progress dibalas 409, atau jika ID muncul di `cancel`.
  - Perbarui komentar daftar perintah di awal file.

- [ ] **Step 2: Verifikasi simulator tanpa server.**

Run: `node tools/device-simulator/rakitan.mjs live --code BAS1.x`
Expected: `Kode pengaturan tidak valid` dan exit code 1. Ini memastikan file tetap bisa di-parse.

- [ ] **Step 3: Kontrak `protokol-alat-rakitan.md`.**
  - Status menjadi "berlaku sejak Rencana 2; perintah sejak Rencana 3".
  - Cakupan tidak lagi menyebut `commands` sebagai rencana berikutnya.
  - §4: contoh response dengan `commands` (`CommandItem`) dan `cancel`, plus aturan pengiriman ulang dan deduplikasi ID oleh mesin.
  - §3: tambah baris 404 `PERINTAH_TIDAK_DIKENAL` dan 409 `PERINTAH_TIDAK_AKTIF`.
  - §6 menjadi §7. Bagian baru §6 "Perintah dan pendaftaran sidik jari" memuat:
    - siklus status dan batas waktu 30/60/75 detik;
    - mesin tidak menerima absensi selama pendaftaran;
    - endpoint progress/result beserta contoh body;
    - kode error dengan arti di layar;
    - template tidak dikirim di versi ini (jika terkirim, server mengabaikannya).
  - Tabel simulator §8 mendapat baris `live --enroll-result KUALITAS_BURUK`.

- [ ] **Step 4: `keputusan.md` dan `pertanyaan-terbuka.md`.**
  - Baris §2 dan bagian rinci untuk K-091 (persetujuan) dan K-094 (2 jari, satu `ENROLL` aktif, 30/60 detik + cadangan 15 detik).
  - K-100: rincian Rencana 3, yaitu template belum disimpan, pengiriman ulang lewat heartbeat, daftar `cancel`, laporan setelah batal diabaikan, dan unique index.
  - Q-034, Q-035, Q-036 ditulis persis seperti spec §13.

- [ ] **Step 5: Jalankan semua test sekali lagi.**

Run: `cd backend && ./mvnw -q test` lalu `cd frontend && npm test`
Expected: keduanya tanpa kegagalan.

- [ ] **Step 6: Commit (oleh pengguna)**
