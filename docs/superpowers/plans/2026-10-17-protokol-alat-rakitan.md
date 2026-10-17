# Protokol Alat Rakitan (`/device/v1`) — Rencana Implementasi

> **Untuk agent pelaksana:** WAJIB memakai superpowers:subagent-driven-development (disarankan) atau superpowers:executing-plans untuk mengerjakan rencana ini per task. Langkah memakai checkbox (`- [ ]`).

**Tujuan:** alat rakitan ESP32 bisa didaftarkan dari web, diatur pertama kali dengan satu kode pengaturan, lalu mengirim heartbeat dan scan dengan kunci rahasia. Simulator menggantikan mesin sampai firmware siap.

**Arsitektur:** adapter protokol kedua `ingest/rakitan` (K-054, K-087) di samping `ingest/zkpush`. Keduanya memakai inti `ScanIngestService` yang sama. Mesin dikenali dari SHA-256 kuncinya (`DeviceContactService.contactByKey`). Endpoint dipisah per jenis mesin: `/iclock/**` hanya untuk `ZK_PUSH`, `/device/v1/**` hanya untuk `RAKITAN_V1`. Kode pengaturan disusun di browser dari kunci yang dikirim server sekali.

**Tech stack:** Spring Boot 4 (Java 21), Flyway, PostgreSQL, Jackson 3 (`tools.jackson`), JUnit 5 + MockMvc + Testcontainers; React + TypeScript + Vitest; Node.js (simulator, tanpa dependency).

**Spec:** [`docs/superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md`](../specs/2026-10-16-pendaftaran-sidik-jari-web-design.md) §5, §6, §15, dan kontrak [`docs/protokol-alat-rakitan.md`](../../protokol-alat-rakitan.md). Ini rencana 2 dari 7 (§14 spec). Pengguna menyetujui semua keputusan §15 dan perubahan API "Tambah mesin" pada 2026-10-17.

## Batasan global

- Kontrak `/device/v1` mengikuti `docs/protokol-alat-rakitan.md` **persis**: nama field, kode error, dan batas 200 scan.
- Nilai jenis mesin: `ZK_PUSH` (bawaan) dan `RAKITAN_V1`. Jenis mesin tidak bisa diubah setelah dibuat.
- ID mesin rakitan: `RK-` + 6 karakter heksadesimal huruf besar, dibuat server, disimpan di `serial_number`.
- Kunci: 32 byte `SecureRandom`, base64url tanpa padding (43 karakter). Yang disimpan hanya SHA-256 hex huruf kecil (64 karakter). Kunci tidak pernah masuk audit log, log aplikasi, atau response selain response penerbitan kunci.
- Kode pengaturan: `BAS1.` + base64url tanpa padding dari JSON `{"server","device","key"}`, disusun di browser.
- Error mesin berupa JSON `{"error":"<KODE>"}`, bukan ProblemDetail: 401 `KUNCI_DITOLAK`, 403 `MESIN_NONAKTIF`, 400 `FORMAT_SALAH`, 413 `BATCH_TERLALU_BESAR`, 500 `GAGAL_SIMPAN`. Mesin tak dikenal sama dengan kunci salah (401).
- Mesin nonaktif dan kunci salah **tidak** memperbarui `last_seen_at` (K-067).
- Balasan scan 200 hanya setelah transaksi simpan selesai (K-020/K-061).
- Hanya Owner yang boleh menambah alat rakitan dan menerbitkan kunci. HR/Finance tetap bisa menambah mesin ZK-Push dan mengubah nama/cabang/status semua mesin.
- Waktu di kontrak: ISO-8601 dengan offset, presisi detik, **detik selalu ditulis** (`09:41:00+07:00`).
- Scan asli tidak pernah diubah. Baris mentah scan rakitan = JSON satu objek scan seperti diterima (diserialisasi ulang ringkas).
- Perubahan schema hanya lewat `V7__alat_rakitan.sql`.
- Setiap endpoint web baru masuk `SecurityConfig` dan `AccessControlTests`. Setiap perubahan data web dicatat `AuditService` di transaksi yang sama.
- Pesan pengguna dan dokumentasi dalam Bahasa Indonesia.
- **Commit dan push dikerjakan pengguna.** Langkah "Commit (oleh pengguna)" berarti: berhenti, laporkan file yang berubah, biarkan pengguna yang commit.

## Fokus review

1. **Waktu tanpa detik:** `OffsetDateTime.toString()` membuang `:00`. `serverTime` harus memakai `DateTimeFormatter.ISO_OFFSET_DATE_TIME` dari nilai yang dipotong ke detik. Test: unit test `formatServerTime` di detik ke-0 (Task 3).
2. **ID alat rakitan dipakai di `/iclock`:** siapa pun yang membaca layar mesin bisa mencoba mengirim ATTLOG ber-SN `RK-…`. Harus 401 dan tidak masuk daftar "mesin belum terdaftar" (Task 3).
3. **Satu scan rusak di tengah batch:** antrean microSD tidak boleh tersangkut. Scan valid tetap tersimpan, scan rusak masuk `rejected_scan_lines`, dan ID-nya tetap di `accepted` (Task 4).
4. **Kirim ulang dengan waktu berbeda tetapi ID sama** (jam mesin disetel ulang di tengah antrean): tetap satu baris, karena aturan unik `(device_id, device_scan_id)` (Task 4).
5. **Kunci lama setelah "Buat kode baru":** langsung 401, dan kontak pertama dengan kunci baru memunculkan konfirmasi "baru saja terhubung" lagi (Task 2, Task 3).

---

### Task 1: Schema, model kunci, dan ID scan

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__alat_rakitan.sql`
- Create: `backend/src/main/java/com/biometricattendance/device/DeviceKeys.java`
- Modify: `backend/src/main/java/com/biometricattendance/device/Device.java`
- Modify: `backend/src/main/java/com/biometricattendance/device/DeviceRepository.java`
- Modify: `backend/src/main/java/com/biometricattendance/ingest/ScanEvent.java`
- Modify: `backend/src/main/java/com/biometricattendance/attendance/AttendanceScanRepository.java`
- Modify: `backend/src/main/java/com/biometricattendance/ingest/ScanIngestService.java` (meneruskan `deviceScanId`)
- Test: `backend/src/test/java/com/biometricattendance/device/DeviceKeysTests.java`

**Interfaces:**
- Produces:
  - `Device.PROTOCOL_ZK_PUSH = "ZK_PUSH"`, `Device.PROTOCOL_RAKITAN = "RAKITAN_V1"`; `setProtocol(String)` (hanya dipakai saat membuat); `getKeyHash()`; `getKeyFirstUsedAt()`; `issueKey(String keyHash)` mengisi hash dan mengosongkan `keyFirstUsedAt`; `markKeyFirstUsed(Instant at)`; `isRakitan()`.
  - `DeviceKeys.newKey(): String` (43 karakter), `DeviceKeys.hash(String key): String` (64 hex huruf kecil), `DeviceKeys.newDeviceId(): String` (`RK-[0-9A-F]{6}`).
  - `DeviceRepository.findByKeyHash(String keyHash): Optional<Device>` (join fetch branch).
  - `ScanEvent` mendapat komponen terakhir `UUID deviceScanId` (boleh null). Konstruktor 5 argumen lama tetap ada dan mengisi null, jadi `ZkPushParser` tidak berubah.
  - `AttendanceScanRepository.insertIgnoringDuplicate(..., String rawLine, UUID deviceScanId)`: kolom `device_scan_id` ikut di-insert, klausa menjadi `on conflict do nothing` (tanpa target).

- [ ] **Step 1: Tulis test `DeviceKeysTests` yang gagal**

```java
@Test void newKeyIs43CharBase64UrlWithoutPadding() {
    String key = DeviceKeys.newKey();
    assertThat(key).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    assertThat(DeviceKeys.newKey()).isNotEqualTo(key);
}
@Test void hashIsLowercaseSha256Hex() {
    // SHA-256("abc")
    assertThat(DeviceKeys.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
}
@Test void deviceIdHasRkPrefixAndSixUpperHex() {
    assertThat(DeviceKeys.newDeviceId()).matches("RK-[0-9A-F]{6}");
}
```

- [ ] **Step 2: Jalankan dan pastikan gagal**

Run: `cd backend && ./mvnw -q test -Dtest=DeviceKeysTests`
Expected: gagal kompilasi, `DeviceKeys` belum ada.

- [ ] **Step 3: Tulis migration `V7__alat_rakitan.sql`**

```sql
-- Rencana 2 alat rakitan (K-087, K-088, K-099). Kunci disimpan sebagai SHA-256; X401 tidak punya kunci.
ALTER TABLE devices ADD COLUMN key_hash VARCHAR(64) UNIQUE;
-- Kontak pertama yang berhasil dengan kunci saat ini; kosong = kunci baru belum pernah dipakai.
ALTER TABLE devices ADD COLUMN key_first_used_at TIMESTAMPTZ;
ALTER TABLE devices ADD CONSTRAINT devices_protocol_check CHECK (protocol IN ('ZK_PUSH', 'RAKITAN_V1'));

-- ID scan buatan alat rakitan. X401 selalu kosong; UNIQUE mengabaikan NULL sehingga X401 tetap memakai
-- attendance_scans_uq (mesin + PIN + waktu).
ALTER TABLE attendance_scans ADD COLUMN device_scan_id UUID;
ALTER TABLE attendance_scans ADD CONSTRAINT attendance_scans_device_scan_uq UNIQUE (device_id, device_scan_id);
```

- [ ] **Step 4: Implementasikan `DeviceKeys`, perubahan `Device`, `DeviceRepository`, `ScanEvent`, `insertIgnoringDuplicate`, dan `ScanIngestService`**

`DeviceKeys` adalah kelas utilitas final dengan satu `SecureRandom` statis. `hash` memakai `MessageDigest.getInstance("SHA-256")` dan `HexFormat.of()`. `ScanIngestService` meneruskan `event.deviceScanId()` sebagai argumen terakhir; tidak ada perubahan lain di inti. Parameter `deviceScanId` di query native ditulis `cast(:deviceScanId as uuid)` supaya null aman.

- [ ] **Step 5: Jalankan test unit dan regresi ZK-Push**

Run: `cd backend && ./mvnw -q test -Dtest=DeviceKeysTests,ZkPushIngestTests,IngestFailureTests`
Expected: semua lulus (migration V7 jalan di Testcontainers; perilaku X401 tidak berubah, termasuk test request bersamaan).

- [ ] **Step 6: Commit (oleh pengguna)**

---

### Task 2: API web — tambah alat rakitan dan "Buat kode baru"

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/device/DeviceDtos.java`
- Modify: `backend/src/main/java/com/biometricattendance/device/DeviceService.java`
- Modify: `backend/src/main/java/com/biometricattendance/device/DeviceController.java`
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java`
- Modify: `backend/src/main/resources/application.properties` (`app.device.public-url=${DEVICE_PUBLIC_URL:}`, dengan komentar)
- Modify: `.env.example` (tambah `DEVICE_PUBLIC_URL=` kosong)
- Test: `backend/src/test/java/com/biometricattendance/device/RakitanDeviceAdminTests.java` (extends `AttendanceFixtures`)
- Test: `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java`

**Interfaces:**
- Consumes: `DeviceKeys`, `Device.issueKey`, `Device.setProtocol` (Task 1).
- Produces:
  - `DeviceRequest` mendapat komponen terakhir `@Pattern(regexp = "ZK_PUSH|RAKITAN_V1", message = "Jenis mesin tidak dikenal") String protocol` (null = `ZK_PUSH`). `@NotBlank` di `serialNumber` dihapus; `@Size(max = 100)` tetap.
  - `IssuedKey(String deviceKey, String setupServerUrl)` — `setupServerUrl` dari `app.device.public-url`, null jika kosong.
  - `DeviceResponse` mendapat komponen `boolean keyIssued`, `Instant keyFirstUsedAt`, `String lastInfo`, dan terakhir `IssuedKey issuedKey` (null kecuali di response penerbitan kunci). `DeviceResponse.withKey(IssuedKey)` membuat salinan berisi kunci.
  - `POST /api/devices` (201): untuk `RAKITAN_V1` mengembalikan `issuedKey`.
  - `POST /api/devices/{id}/key` (200, Owner): `DeviceResponse` berisi `issuedKey`.
  - `DeviceService.issueKey(Long id): DeviceResponse`.

Aturan service:
- Membuat `ZK_PUSH`: `serialNumber` kosong → 400 `"Serial number wajib diisi"`. Selain itu sama dengan sekarang.
- Membuat `RAKITAN_V1`: bukan Owner → 403 `"Hanya Owner yang bisa menambah alat rakitan"`. `serialNumber` dari request diabaikan; ID dibuat `DeviceKeys.newDeviceId()`, diulang sampai belum terpakai. Kunci diterbitkan.
- Mengubah: `protocol` diisi dan berbeda dari yang tersimpan → 400 `"Jenis mesin tidak bisa diubah. Buat mesin baru."`. Untuk `RAKITAN_V1`, `serialNumber` dari request diabaikan (ID tetap). Untuk `ZK_PUSH` kosong → 400 seperti saat membuat.
- `issueKey`: mesin tidak ada → 404; mesin `ZK_PUSH` → 400 `"Mesin ZK-Push tidak memakai kunci"`. Kunci lama langsung tidak berlaku. Audit `ISSUE_KEY` dengan before/after **tanpa** kunci.
- Audit `CREATE` menyimpan response **tanpa** `issuedKey`.

`SecurityConfig`: tambahkan `.requestMatchers(HttpMethod.POST, "/api/devices/*/key").hasRole(OWNER)` **sebelum** matcher `/api/devices/**`.

- [ ] **Step 1: Tulis test `RakitanDeviceAdminTests` yang gagal**

```java
@Test void ownerCreatesRakitanDeviceAndGetsKeyOnce() {
    // POST /api/devices {"name":"Uji Kampus","branchId":b,"active":true,"protocol":"RAKITAN_V1"} sebagai OWNER
    // → 201, $.protocol == "RAKITAN_V1", $.serialNumber cocok "RK-[0-9A-F]{6}",
    //   $.issuedKey.deviceKey panjang 43, $.keyIssued == true, $.keyFirstUsedAt == null
    // DB: devices.key_hash == DeviceKeys.hash(deviceKey)
    // GET /api/devices → baris mesin ini punya issuedKey null
    // audit_logs entity DEVICE id ini: after_data tidak mengandung deviceKey
}
@Test void hrFinanceCannotCreateRakitanButCanCreateZkPush() {
    // HR + RAKITAN_V1 → 403, $.detail == "Hanya Owner yang bisa menambah alat rakitan"
    // HR + tanpa protocol + serialNumber → 201, $.protocol == "ZK_PUSH", $.keyIssued == false
}
@Test void zkPushStillRequiresSerialNumber() {
    // OWNER, protocol ZK_PUSH, serialNumber "" → 400, $.detail == "Serial number wajib diisi"
}
@Test void unknownProtocolIsRejected() {
    // protocol "LAINNYA" → 400, $.errors.protocol == "Jenis mesin tidak dikenal"
}
@Test void protocolAndRakitanIdCannotChange() {
    // buat RAKITAN_V1 → PUT dengan protocol ZK_PUSH → 400 "Jenis mesin tidak bisa diubah. Buat mesin baru."
    // PUT tanpa protocol, serialNumber "LAIN-1", name "Baru" → 200, serialNumber tetap RK-…, name "Baru"
}
@Test void newKeyRevokesOldKeyAndResetsFirstUse() {
    // buat RAKITAN_V1 (kunci A); set key_first_used_at lewat jdbc
    // POST /api/devices/{id}/key (OWNER) → 200, issuedKey.deviceKey B != A, keyFirstUsedAt null
    // DB key_hash == hash(B); audit ISSUE_KEY ada dan tidak berisi B
}
@Test void issuingKeyForZkPushIsRejected() {
    // createDevice(...) ZK → POST /key → 400 "Mesin ZK-Push tidak memakai kunci"
}
```

Di `AccessControlTests`: tambah test `deviceKeyIsOwnerOnly` — `POST /api/devices/{MISSING}/key` dengan csrf: OWNER → 404, HR_FINANCE → 403, karyawan → 403, anonim → 401.

- [ ] **Step 2: Jalankan dan pastikan gagal**

Run: `cd backend && ./mvnw -q test -Dtest=RakitanDeviceAdminTests,AccessControlTests`
Expected: FAIL (field `protocol`/`issuedKey` belum ada, endpoint `/key` belum ada).

- [ ] **Step 3: Implementasikan DTO, service, controller, dan `SecurityConfig` sesuai Interfaces di atas**

Peran Owner dicek di service dengan `CurrentUser.hasRole("OWNER")` (pola `UserAccountService`).

- [ ] **Step 4: Jalankan test task ini dan semua test mesin**

Run: `cd backend && ./mvnw -q test -Dtest=RakitanDeviceAdminTests,AccessControlTests,ZkPushIngestTests,DashboardTests,DeviceStatusTests`
Expected: semua lulus.

- [ ] **Step 5: Commit (oleh pengguna)**

---

### Task 3: Autentikasi mesin, heartbeat, dan pemisahan endpoint

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/device/DeviceContactService.java`
- Modify: `backend/src/main/java/com/biometricattendance/device/DeviceContactEvent.java`
- Create: `backend/src/main/java/com/biometricattendance/device/KeyContact.java`
- Create: `backend/src/main/java/com/biometricattendance/ingest/rakitan/RakitanDtos.java`
- Create: `backend/src/main/java/com/biometricattendance/ingest/rakitan/RakitanController.java`
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/biometricattendance/dashboard/DashboardStream.java`
- Test: `backend/src/test/java/com/biometricattendance/ingest/RakitanFixtures.java` (abstract, extends `AttendanceFixtures`)
- Test: `backend/src/test/java/com/biometricattendance/ingest/RakitanHeartbeatTests.java`

**Interfaces:**
- Consumes: `DeviceRepository.findByKeyHash`, `DeviceKeys.hash`, `Device.markKeyFirstUsed` (Task 1); `POST /api/devices` dengan `RAKITAN_V1` (Task 2).
- Produces:
  - `KeyContact(Status status, Device device)` dengan `enum Status { ACCEPTED, REJECTED, INACTIVE }`; `device` null jika `REJECTED`.
  - `DeviceContactService.contactByKey(String bearerKey, String ip, String firmware, String info): KeyContact` (`@Transactional`). Kosong/tak dikenal/bukan `RAKITAN_V1` → `REJECTED`, tanpa perubahan apa pun. Nonaktif → `INACTIVE`, tanpa memperbarui kontak. Selain itu `recordContact` + jika `keyFirstUsedAt` null → `markKeyFirstUsed(now)`.
  - `DeviceContactEvent(Long deviceId, String deviceName, boolean cameOnline, boolean firstConnect)`. `contact(...)` lama mengirim `firstConnect = false`.
  - `contact(serial, …)` (jalur `/iclock`): mesin ditemukan tetapi `protocol` bukan `ZK_PUSH` → `Optional.empty()` **tanpa** dicatat di `UnregisteredDeviceTracker`.
  - SSE: jika `firstConnect`, broadcast `{"type":"DEVICE_CONNECTED","deviceId":…,"deviceName":…}`.
  - `RakitanFixtures`: `createRakitan(long branchId): Rakitan` dengan `record Rakitan(long id, String deviceId, String key)`; `heartbeat(String key, String json): ResultActions`; `postScans(String key, String json): ResultActions`.
  - `RakitanDtos.HeartbeatRequest(String firmware, String ip, Long uptimeSeconds, Integer pendingScans, Sensor sensor)`, `Sensor(Integer used, Integer capacity)`, `HeartbeatResponse(String serverTime, String timezone, String deviceName, String branchName, List<Object> commands)`, `ErrorBody(String error)`.
  - `RakitanController.formatServerTime(Instant now, ZoneId zone): String` (static, package-private).

Perilaku `POST /device/v1/heartbeat`:
- Header `Authorization: Bearer <kunci>`; tanpa awalan `Bearer ` = kunci kosong.
- `ip` = field body jika ada, selain itu `request.getRemoteAddr()`. `info` = `"uptime=<n>s; antrean=<n>; sensor=<used>/<capacity>"`, hanya bagian yang dikirim, dipisah `"; "`, null jika tidak ada satu pun.
- Response 200: `serverTime` = `formatServerTime(Instant.now(), zona cabang)`; `timezone` = zona cabang; `commands` = `[]`.
- 401/403 sesuai Batasan global. Body bukan JSON → 400 `FORMAT_SALAH` (lewat `@ExceptionHandler(HttpMessageNotReadableException.class)` di controller). Exception lain → 500 `GAGAL_SIMPAN`, dengan log error tanpa kunci.

`SecurityConfig`: `.requestMatchers("/device/v1/**").permitAll()` dengan komentar (kunci per mesin, K-088), dan `/device/v1/**` ditambahkan ke `ignoringRequestMatchers` CSRF.

- [ ] **Step 1: Tulis `RakitanFixtures` dan test `RakitanHeartbeatTests` yang gagal**

```java
@Test void serverTimeAlwaysHasSeconds() {
    assertThat(RakitanController.formatServerTime(Instant.parse("2026-10-17T02:41:00Z"), ZoneId.of("Asia/Jakarta")))
        .isEqualTo("2026-10-17T09:41:00+07:00");
}
@Test void heartbeatWithValidKeyReturnsBranchTimeAndRecordsContact() {
    // createRakitan → heartbeat(key, {"firmware":"0.1.0","ip":"192.168.43.51","uptimeSeconds":3600,
    //   "pendingScans":2,"sensor":{"used":12,"capacity":150},"fieldBaru":true})
    // → 200, $.timezone == "Asia/Jakarta", $.deviceName, $.branchName, $.commands kosong,
    //   $.serverTime cocok "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+07:00"
    // GET /api/devices: firmware "0.1.0", lastIp "192.168.43.51", online true,
    //   lastInfo "uptime=3600s; antrean=2; sensor=12/150", keyFirstUsedAt terisi
}
@Test void emptyBodyHeartbeatIsAccepted() { /* body {} → 200 */ }
@Test void missingOrWrongKeyIs401AndNotRecorded() {
    // tanpa header → 401 {"error":"KUNCI_DITOLAK"}; "Bearer salah" → 401; lastSeenAt tetap null
}
@Test void inactiveDeviceIs403AndStaysOffline() {
    // PUT active false → heartbeat → 403 {"error":"MESIN_NONAKTIF"}; lastSeenAt tetap null
}
@Test void revokedKeyIsRejectedImmediately() {
    // heartbeat(A) 200 → POST /api/devices/{id}/key → heartbeat(A) 401, heartbeat(B) 200
}
@Test void malformedJsonIs400() { /* body "{bukan json" → 400 {"error":"FORMAT_SALAH"} */ }
@Test void rakitanIdIsRejectedAtIclockAndNotListedAsUnregistered() {
    // postAttlog(rakitan.deviceId(), lines(line(pin, ...))) → 401; scanCount 0;
    // GET /api/devices/unregistered-attempts tidak berisi deviceId ini
}
@Test void firstContactPerKeyNotifiesDashboard() {
    // buka /api/dashboard/stream (HR, asyncStarted) → heartbeat(A) → konten berisi "DEVICE_CONNECTED"
    //   dan nama mesin; heartbeat(A) kedua tidak menambah kemunculan "DEVICE_CONNECTED"
    // POST /key → heartbeat(B) → jumlah kemunculan "DEVICE_CONNECTED" menjadi 2
}
```

- [ ] **Step 2: Jalankan dan pastikan gagal**

Run: `cd backend && ./mvnw -q test -Dtest=RakitanHeartbeatTests`
Expected: FAIL (`/device/v1/heartbeat` belum ada dan ditolak `denyAll`).

- [ ] **Step 3: Implementasikan `KeyContact`, `contactByKey`, penjaga protokol `/iclock`, event, `DashboardStream`, `RakitanDtos`, heartbeat di `RakitanController`, dan `SecurityConfig`**

- [ ] **Step 4: Jalankan test task ini dan regresi**

Run: `cd backend && ./mvnw -q test -Dtest=RakitanHeartbeatTests,ZkPushIngestTests,DashboardTests,AccessControlTests`
Expected: semua lulus.

- [ ] **Step 5: Commit (oleh pengguna)**

---

### Task 4: Pengiriman scan `POST /device/v1/scans`

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/ingest/rakitan/RakitanScanParser.java`
- Modify: `backend/src/main/java/com/biometricattendance/ingest/rakitan/RakitanDtos.java`
- Modify: `backend/src/main/java/com/biometricattendance/ingest/rakitan/RakitanController.java`
- Test: `backend/src/test/java/com/biometricattendance/ingest/rakitan/RakitanScanParserTests.java`
- Test: `backend/src/test/java/com/biometricattendance/ingest/RakitanScanIngestTests.java` (extends `RakitanFixtures`)

**Interfaces:**
- Consumes: `contactByKey` (Task 3), `ScanEvent` 6 komponen dan `ScanIngestService.ingest` (Task 1).
- Produces:
  - `RakitanDtos.ScanBatch(List<Map<String, Object>> scans)` — `scans` null → 400 `FORMAT_SALAH`. `ScansResponse(List<String> accepted, int saved, int duplicates, int ignored, int rejected)`.
  - `RakitanScanParser(JsonMapper json)` (`tools.jackson.databind.json.JsonMapper`, seperti `AuditService`); `Parsed parse(List<Map<String, Object>> scans, ZoneId zone)` dengan `record Parsed(List<ScanEvent> events, List<RejectedLine> rejected, List<String> accepted)`.
  - `RakitanController.MAX_SCANS = 200`.

Aturan parser per scan (urutan pemeriksaan, alasan ditolak persis):
1. `rawLine` = `json.writeValueAsString(scan)`.
2. `id` berupa teks tidak kosong → masuk `accepted` apa pun hasilnya. Bukan UUID → ditolak `"ID scan bukan UUID"`. Tidak ada → ditolak `"ID scan kosong"`.
3. `pin` bukan teks, kosong, atau lebih dari 20 karakter → `"PIN kosong atau lebih dari 20 karakter"`.
4. `time` tidak bisa dibaca `OffsetDateTime.parse` (termasuk tanpa offset) → `"Waktu harus ISO-8601 dengan offset"`.
5. Valid → `ScanEvent(pin, time.atZoneSameInstant(zone).toLocalDateTime(), null, verify (teks atau null), rawLine, uuid)`.

Urutan di controller: autentikasi (401/403) → `scans` null (400) → lebih dari 200 (413) → parse → `ingest.ingest(deviceId, events, rejected, null)` → 200 dengan `accepted` dari parser dan angka dari `IngestResult`.

- [ ] **Step 1: Tulis `RakitanScanParserTests` yang gagal**

```java
@Test void validScanIsConvertedToBranchLocalTime() {
    // {id:UUID, pin:"1696", time:"2026-10-17T01:01:02Z", verify:"FP"}, zona Asia/Jakarta
    // → events[0].localTime == 2026-10-17T08:01:02, verifyMode "FP", deviceScanId == UUID, accepted == [id]
}
@Test void timeWithoutOffsetIsRejectedButIdAccepted() {
    // time "2026-10-17T08:01:02" → events kosong, rejected[0].reason "Waktu harus ISO-8601 dengan offset",
    // rejected[0].rawLine berisi "2026-10-17T08:01:02", accepted == [id]
}
@Test void missingIdIsRejectedAndNotAccepted() { /* reason "ID scan kosong", accepted kosong */ }
@Test void nonUuidIdIsRejectedButAccepted() { /* id "abc" → reason "ID scan bukan UUID", accepted == ["abc"] */ }
@Test void blankOrLongPinIsRejected() { /* pin "" dan pin 21 karakter → "PIN kosong atau lebih dari 20 karakter" */ }
```

- [ ] **Step 2: Tulis `RakitanScanIngestTests` yang gagal**

```java
@Test void scansAreSavedAndAcknowledged() {
    // karyawan PIN p di cabang yang sama; 2 scan "+07:00" → 200, accepted == [id1,id2], saved 2;
    // DB: scanned_at 08:01:02 WIB = 01:01:02Z, device_scan_id == id1, raw_line berisi id1, flags null
}
@Test void resendWithSameIdsIsStoredOnce() { /* kirim batch sama 2× → kedua 200, kedua accepted penuh, ke-2 duplicates 2, scanCount 2 */ }
@Test void sameIdWithDifferentTimeIsStillOneRow() { /* id1 jam 08:00 lalu id1 jam 08:05 → scanCount 1, duplicates 1 */ }
@Test void oneBadScanDoesNotBlockOthers() {
    // [valid, tanpa offset, valid] → 200, saved 2, rejected 1, accepted 3 id;
    // rejected_scan_lines untuk mesin ini: 1 baris dengan alasan "Waktu harus ISO-8601 dengan offset"
}
@Test void scansBeforeAcceptFromAreIgnoredButAccepted() { /* PUT acceptScansFrom 2026-10-01; scan 2026-09-30 → ignored 1, accepted [id] */ }
@Test void unknownPinIsSavedWithFlag() { /* flags == "PIN_TIDAK_DIKENAL" */ }
@Test void moreThan200ScansIs413() { /* 201 scan → 413 {"error":"BATCH_TERLALU_BESAR"}, scanCount 0 */ }
@Test void missingScansArrayIs400() { /* {} → 400 FORMAT_SALAH */ }
@Test void wrongKeyAndInactiveDeviceSaveNothing() { /* 401 dan 403, scanCount 0 */ }
@Test void emptyBatchIsOk() { /* {"scans":[]} → 200, accepted [], semua angka 0 */ }
```

- [ ] **Step 3: Jalankan dan pastikan gagal**

Run: `cd backend && ./mvnw -q test -Dtest=RakitanScanParserTests,RakitanScanIngestTests`
Expected: FAIL (kelas parser dan endpoint belum ada).

- [ ] **Step 4: Implementasikan `RakitanScanParser`, `ScanBatch`/`ScansResponse`, dan endpoint scans**

- [ ] **Step 5: Jalankan seluruh test backend**

Run: `cd backend && ./mvnw -q test`
Expected: semua lulus (butuh Docker untuk Testcontainers).

- [ ] **Step 6: Commit (oleh pengguna)**

---

### Task 5: Halaman Mesin — jenis mesin, kode pengaturan, dan konfirmasi terhubung

**Files:**
- Create: `frontend/src/lib/setupCode.ts`
- Create: `frontend/src/lib/setupCode.test.ts`
- Create: `frontend/src/components/SetupCodeDialog.tsx`
- Create: `frontend/src/components/DeviceNotifications.tsx`
- Modify: `frontend/src/components/CrudPage.tsx` (prop `onSaved`)
- Modify: `frontend/src/components/AppLayout.tsx` (pasang `DeviceNotifications` di samping `LeaveNotifications`)
- Modify: `frontend/src/lib/types.ts`
- Modify: `frontend/src/lib/useDashboardStream.ts` (tipe event)
- Modify: `frontend/src/pages/DevicesPage.tsx`
- Test: `frontend/src/pages/DevicesPage.test.tsx` (baru)

**Interfaces:**
- Consumes: `POST /api/devices` dan `POST /api/devices/{id}/key` → `Device` dengan `issuedKey` (Task 2); event SSE `DEVICE_CONNECTED` (Task 3).
- Produces:
  - `types.ts`: `DeviceProtocol = 'ZK_PUSH' | 'RAKITAN_V1'`; `IssuedKey { deviceKey: string; setupServerUrl: string | null }`; `Device` mendapat `protocol: DeviceProtocol`, `keyIssued: boolean`, `keyFirstUsedAt: string | null`, `lastInfo: string | null`, `issuedKey: IssuedKey | null`.
  - `StreamEvent.type` mendapat `'DEVICE_CONNECTED'`; field opsional `deviceId?: number`, `deviceName?: string`.
  - `setupCode.ts`: `buildSetupCode(server: string, deviceId: string, key: string): string`; `normalizeServerUrl(input: string): string | null` (trim, buang `/` di akhir, null jika tidak diawali `http://` atau `https://` atau tidak ada host); `defaultServerUrl(issued: IssuedKey): string` = `setupServerUrl` atau `http://${window.location.hostname}:8888`.
  - `CrudPage` prop `onSaved?: (saved: T, editing: T | null) => void`, dipanggil dengan hasil PUT/POST setelah invalidate.
  - `SetupCodeDialog({ device, issuedKey, onClose })`.

Perilaku UI:
- Form **Tambah**: field `protocol` (select) "Jenis mesin": `ZK_PUSH` "Mesin ZK-Push (X401)" dan `RAKITAN_V1` "Alat rakitan". Untuk HR/Finance hanya opsi ZK-Push. `serialNumber` tidak `required` di form, hint: "Wajib untuk mesin ZK-Push, persis seperti di menu info mesin. Kosongkan untuk alat rakitan; ID dibuat otomatis."
- Form **Ubah**: tidak ada field `protocol`; `serialNumber` hanya tampil untuk `ZK_PUSH`. `toBody` tidak mengirim `protocol` saat mengubah.
- Kolom "Jenis": "ZK-Push" / "Alat rakitan". Kolom "Koneksi": alat rakitan dengan `keyIssued && !keyFirstUsedAt` → badge "Menunggu pengaturan". `lastInfo` tampil sebagai teks kecil di kolom "Firmware".
- Setelah membuat alat rakitan, `onSaved` membuka `SetupCodeDialog`.
- Row action **"Buat kode baru"**: hanya Owner dan hanya `RAKITAN_V1`. Konfirmasi dulu dengan `Dialog`: judul "Buat kode pengaturan baru?", isi "Kunci lama langsung tidak berlaku. Mesin harus diatur ulang dengan kode baru." Lalu POST `/api/devices/{id}/key` dan buka `SetupCodeDialog`.
- `SetupCodeDialog`: menampilkan ID mesin; input "Alamat server" berisi `defaultServerUrl`, dengan hint "Alamat yang dijangkau mesin, mis. IP laptop di hotspot HP + port 8888"; kode (`<textarea readOnly>`, font mono) diperbarui saat alamat diubah; tombol "Salin kode" (`navigator.clipboard.writeText`, label menjadi "Tersalin"); peringatan "Kode ini hanya tampil sekali. Jika hilang, buat kode baru." Alamat tidak valid → pesan "Alamat harus diawali http:// atau https://" dan kode tidak ditampilkan. Saat event `DEVICE_CONNECTED` dengan `deviceId` yang sama datang → tampil "✓ Mesin {nama} terhubung".
- `DeviceNotifications`: pop-up 10 detik "Mesin {deviceName} baru saja terhubung" untuk event `DEVICE_CONNECTED`, mengikuti pola toast `LeaveNotifications`.

- [ ] **Step 1: Tulis `setupCode.test.ts` yang gagal**

```ts
it('builds BAS1 code that decodes back to the payload', () => {
  const code = buildSetupCode('http://192.168.43.20:8888', 'RK-7F2A9C', 'k'.repeat(43))
  expect(code.startsWith('BAS1.')).toBe(true)
  expect(code).not.toMatch(/[+/=]/)
  const json = JSON.parse(atob(code.slice(5).replace(/-/g, '+').replace(/_/g, '/')))
  expect(json).toEqual({ server: 'http://192.168.43.20:8888', device: 'RK-7F2A9C', key: 'k'.repeat(43) })
})
it('normalizes server url', () => {
  expect(normalizeServerUrl(' http://10.0.0.5:8888/ ')).toBe('http://10.0.0.5:8888')
  expect(normalizeServerUrl('10.0.0.5:8888')).toBeNull()
  expect(normalizeServerUrl('http://')).toBeNull()
})
```

- [ ] **Step 2: Tulis `DevicesPage.test.tsx` yang gagal**

Mock `api` dan `useAuth` mengikuti pola `WorkRulesPage.test.tsx`. Test:
- `owner adds rakitan device and sees setup code once`: pilih "Alat rakitan", simpan → body POST berisi `protocol: 'RAKITAN_V1'`; dialog menampilkan `RK-7F2A9C` dan textarea yang nilainya diawali `BAS1.`; setelah ditutup, kode tidak tampil lagi.
- `hr finance only sees ZK-Push option`.
- `edit form has no protocol field and sends no protocol`.
- `new code asks for confirmation then posts to key endpoint` (POST `/api/devices/5/key`; tombol tidak ada untuk ZK-Push dan untuk HR/Finance).
- `rakitan without first contact shows Menunggu pengaturan`.
- `invalid server address hides the code`.

- [ ] **Step 3: Jalankan dan pastikan gagal**

Run: `cd frontend && npm test -- src/lib/setupCode.test.ts src/pages/DevicesPage.test.tsx`
Expected: FAIL (modul belum ada).

- [ ] **Step 4: Implementasikan sesuai Interfaces dan Perilaku UI**

`buildSetupCode` mengodekan JSON sebagai UTF-8 (`TextEncoder`) lalu base64url tanpa padding. Komponen shadcn yang dipakai: `dialog`, `button`, `input`, `label`, `alert`, `badge`.

- [ ] **Step 5: Jalankan seluruh test, lint, dan build frontend**

Run: `cd frontend && npm test && npm run lint && npm run build`
Expected: semua lulus tanpa error.

- [ ] **Step 6: Commit (oleh pengguna)**

---

### Task 6: Simulator alat rakitan dan dokumentasi

**Files:**
- Create: `tools/device-simulator/rakitan.mjs`
- Modify: `tools/device-simulator/simulate.mjs` (komentar kepala: rujuk `rakitan.mjs` untuk alat rakitan)
- Modify: `docs/protokol-alat-rakitan.md` (status "berlaku sejak Rencana 2"; body 5xx `{"error":"GAGAL_SIMPAN"}` di tabel §3; tambah bagian "Uji dengan simulator")
- Modify: `docs/keputusan.md`
- Modify: `docs/README.md` (indeks: kontrak protokol dan rencana 2)

**Interfaces:**
- Consumes: kontrak `/device/v1` (Task 3, Task 4); format kode pengaturan (Task 5).

Simulator (Node tanpa dependency, `fetch` bawaan), pemakaian `node tools/device-simulator/rakitan.mjs <perintah> --code BAS1.… [opsi]` (atau env `BAS_CODE`):
- `decode` — cetak server, ID mesin, dan 6 karakter awal kunci; kode tidak valid → keluar kode 1 dengan "Kode pengaturan tidak valid".
- `setup` — meniru langkah layar mesin: "✓ Server ditemukan" / "Server <alamat> tidak terjangkau. Laptop menyala? Satu jaringan?", lalu "✓ Kunci diterima" / pesan 401/403 dari kontrak §6, lalu "Siap absen — <branchName> <jam>".
- `heartbeat`, `scan --pin 9001 [--time ISO]`, `offline --pin 9001 --count 3` (satu batch), `duplicate --pin 9001` (batch sama dua kali, cetak `duplicates`), `bad-scan --pin 9001` (satu valid + satu tanpa offset), `oversize` (201 scan, harap 413), `wrong-key`, `live --pin 9001 [--every 10]` (heartbeat setiap 5 detik + scan setiap N detik).
- Setiap perintah mencetak status HTTP dan body balasan. Scan membawa UUID dari `crypto.randomUUID()` dan waktu ISO dengan offset zona lokal komputer, presisi detik.

`docs/keputusan.md` (daftar §2 dan bagian rinci, pola K-092):
- **K-087** sesuai tabel spec §12.
- **K-088** — kunci per mesin, hanya tampil sekali, disimpan SHA-256; "Buat kode baru" langsung mencabut. Catat bahwa penggantian dua tahap dan konfirmasi ulang password menyusul di Rencana 6.
- **K-099** — rincian kontrak Rencana 2 (keputusan Claude yang disetujui pengguna 2026-10-17): 403 `MESIN_NONAKTIF` untuk alat rakitan, endpoint terpisah per jenis mesin, jenis mesin tidak bisa diubah, hanya Owner yang menerbitkan kunci, konfirmasi terhubung per kunci baru, `accepted` mencakup scan yang ditolak, isi heartbeat di `last_info`.

- [ ] **Step 1: Tulis `rakitan.mjs`**

- [ ] **Step 2: Verifikasi tanpa backend**

Run: `node tools/device-simulator/rakitan.mjs decode --code BAS1.eyJzZXJ2ZXIiOiJodHRwOi8vMTI3LjAuMC4xOjg4ODgiLCJkZXZpY2UiOiJSSy0wMDAwMDEiLCJrZXkiOiJhYmNkZWYifQ`
Expected: mencetak `http://127.0.0.1:8888`, `RK-000001`, dan `abcdef`. Dengan `--code salah` → "Kode pengaturan tidak valid", exit code 1.

- [ ] **Step 3: Uji ujung ke ujung dengan backend lokal**

Jalankan `docker compose up --build`. Login sebagai Owner, tambah "Alat rakitan" di halaman Mesin, isi alamat `http://localhost:8888`, salin kode. Lalu:
`node tools/device-simulator/rakitan.mjs setup --code <kode>` → ketiga langkah berhasil dan pop-up "baru saja terhubung" muncul di web;
`scan`, `duplicate`, `bad-scan`, `oversize`, `wrong-key` → balasan sesuai kontrak; scan muncul di rekap/dashboard.
Laporkan hasil apa adanya. Jika Docker tidak tersedia, tulis bahwa langkah ini dilewati.

- [ ] **Step 4: Perbarui dokumen** (`protokol-alat-rakitan.md`, `keputusan.md`, `README.md`)

- [ ] **Step 5: Jalankan seluruh test sekali lagi**

Run: `cd backend && ./mvnw -q test` dan `cd frontend && npm test`
Expected: semua lulus.

- [ ] **Step 6: Commit (oleh pengguna)**
