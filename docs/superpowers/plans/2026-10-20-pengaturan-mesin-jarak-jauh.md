# Pengaturan Mesin Jarak Jauh (Rencana 6) — Rencana Implementasi

> **Untuk agent pelaksana:** WAJIB memakai superpowers:subagent-driven-development atau superpowers:executing-plans untuk mengerjakan rencana ini per task. Langkah memakai checkbox (`- [ ]`).

**Tujuan:** pengaturan alat rakitan bisa dilakukan dari halaman Mesin:
- pengaturan ringan (paket berversi);
- Wi-Fi dan alamat server (`APPLY_NETWORK`);
- ganti kunci dua tahap (`ROTATE_KEY`).

Tindakan berisiko wajib dikonfirmasi ulang dengan password.

**Arsitektur:**
- **Modul baru `devicesettings`.** Berisi paket pengaturan ringan, paket jaringan, dan rotasi kunci. Modul ini bergantung pada `device` dan `devicecommand`. Agar tidak terjadi siklus, `devicecommand` hanya menyimpan `networkConfigId` (Long) dan tidak memakai association ke entity modul ini.
- **Penyisipan data rahasia.** Password Wi-Fi dan kunci tertunda disimpan terenkripsi lewat `common/SecretCipher` (AES-256-GCM). Isinya didekripsi hanya saat balasan heartbeat dibuat.
- **Perpindahan kunci.** Diselesaikan di `DeviceContactService.contactByKey` lewat update atomik. Setelah itu event sinkron `KeyRotatedEvent` menyelesaikan perintahnya di `devicecommand`.

**Tech stack:** Spring Boot 4 (Java 21), Flyway, PostgreSQL, JUnit 5 + MockMvc + Testcontainers; React + TypeScript + TanStack Query + Vitest; Node.js (simulator).

**Spec:** [`docs/superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md`](../specs/2026-10-16-pendaftaran-sidik-jari-web-design.md) **§18** (mengikat), ditambah §4.2, §6, §7. Kontrak mesin: [`docs/protokol-alat-rakitan.md`](../../protokol-alat-rakitan.md). Ini rencana 6 dari 7.

## Batasan global

- Pengguna yang melakukan commit. Langkah "Commit" berarti: berhenti, laporkan file yang berubah dan hasil test.
- Test backend dijalankan dari salinan `backend` di scratchpad (`bt.sh`), jangan dari folder pengguna. `LeaveTests.concurrentOverlappingSubmissionsSaveOnlyOne` flaky; ulangi dulu sebelum menyimpulkan ada regresi.
- Schema hanya lewat `V10__pengaturan_mesin.sql`. Semua perubahan schema Rencana 6 ditulis di Task 3 sekaligus.
- Setiap perubahan data dicatat lewat `AuditService` di transaksi yang sama. Audit **tidak pernah** berisi password Wi-Fi, kunci, atau hash kunci.
- Endpoint baru wajib masuk `SecurityConfig` dan matrix `AccessControlTests`.
- Error memakai ProblemDetail dengan pesan Bahasa Indonesia. Pesan yang dipakai persis seperti di spec §18:
  - "Password salah."
  - "Mesin masih menerapkan perubahan jaringan atau kunci."
  - "Perintah sudah dikirim ke mesin dan tidak bisa dibatalkan."
  - "Kunci enkripsi server belum diatur. Hubungi pengelola server."
- Nilai teknis ada di `application.properties` dan tidak di-hardcode:
  - `app.device.settings.default.*`
  - `app.device.network.result-timeout=10m`
  - `app.device.rotate-key.timeout=24h`
  - `app.crypto.data-key=${DATA_ENCRYPTION_KEY:}`
- Akun nyata untuk test konfirmasi password adalah `owner` / `owner-awal-123` (bootstrap di `IntegrationTest`). Principal mock seperti `owner-absensi` tidak punya akun, jadi konfirmasinya selalu ditolak.

## Review Focus

1. **Kunci lama setelah perpindahan.** Heartbeat dengan kunci lama setelah kunci baru dipakai harus 401 `KUNCI_DITOLAK`. Test: `KeyRotationTests.oldKeyRejectedAfterNewKeyUsed`.
2. **Dua heartbeat bersamaan dengan kunci baru.** Hasilnya satu perpindahan, satu audit `KEY_ROTATED`, dan keduanya 200. Test: `KeyRotationTests.concurrentFirstUseOfNewKeyPromotesOnce`.
3. **Data rahasia tidak bocor.** Password Wi-Fi dan kunci tidak boleh muncul di response `/api/**` mana pun, maupun di `audit_logs`. Test: `NetworkCommandTests.wifiPasswordNeverInApiOrAudit` dan `KeyRotationTests.pendingKeyNeverInApiOrAudit`.
4. **"Semua mesin".** Hanya alat rakitan aktif yang ikut, dan versi hanya naik untuk mesin yang nilainya berubah. Test: `DeviceSettingsTests.applyToAllSkipsInactiveAndZkAndUnchanged`.
5. **Versi mesin lebih baru dari server** (mis. setelah database dipulihkan). Server tetap mengirim paketnya. Aturannya: kirim jika versi server > 0 **dan** berbeda dari versi mesin. Test: `DeviceSettingsTests.machineAheadOfServerGetsPackageAgain`. Ini ruling yang tercatat di ledger.

---

### Task 1: `SecretCipher` (AES-256-GCM) dan konfigurasi kunci

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/common/SecretCipher.java`
- Modify: `backend/src/main/resources/application.properties`, `backend/src/test/java/com/biometricattendance/IntegrationTest.java` (properti `app.crypto.data-key=<base64 32 byte uji>`), `.env.example`, `docker-compose.yml`
- Test: `backend/src/test/java/com/biometricattendance/common/SecretCipherTests.java` (unit, tanpa Spring)

**Interfaces:**
- Produces: `@Component SecretCipher(@Value("${app.crypto.data-key:}") String base64Key)` dengan:
  - `boolean isConfigured()`
  - `String encrypt(String plain)`: hasilnya `v1:` + base64(iv‖ct+tag). Melempar `ApiException.badRequest("Kunci enkripsi server belum diatur. Hubungi pengelola server.")` jika belum diatur.
  - `String decrypt(String sealed)`: `IllegalStateException` jika rusak.
- Kunci yang ada tetapi bukan 32 byte membuat aplikasi gagal start: `IllegalArgumentException` di konstruktor.

- [ ] **Step 1: test gagal.** `SecretCipherTests`:
  - `roundTrip`: decrypt(encrypt("rahasia")) = "rahasia", dan hasil diawali "v1:";
  - `freshIvEachTime`: dua encrypt dari teks yang sama hasilnya berbeda;
  - `tamperedCiphertextRejected`: satu karakter base64 diubah → `IllegalStateException`;
  - `missingKeyNotConfigured`: `new SecretCipher("")` → isConfigured false, dan encrypt melempar ApiException dengan pesan spec;
  - `wrongKeyLengthFailsFast`: 16 byte → IllegalArgumentException.
- [ ] **Step 2:** jalankan `-Dtest=SecretCipherTests`. Expected: kompilasi gagal karena kelas belum ada.
- [ ] **Step 3: implementasi.** `Cipher.getInstance("AES/GCM/NoPadding")`, IV 12 byte dari `SecureRandom`, tag 128 bit. Tambahkan properti beserta komentarnya, `DATA_ENCRYPTION_KEY=` beserta cara membuatnya (`openssl rand -base64 32`) di `.env.example`, dan `DATA_ENCRYPTION_KEY: ${DATA_ENCRYPTION_KEY:-}` di service backend `docker-compose.yml`.
- [ ] **Step 4:** jalankan lagi. Expected: 5/5 lulus.
- [ ] **Step 5: Commit** (laporkan).

### Task 2: Konfirmasi ulang password, dan "Buat kode baru" memakainya

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/account/PasswordConfirmation.java`
- Modify: `device/DeviceController.java` (`issueKey` menerima `@Valid @RequestBody ConfirmRequest`), `device/DeviceService.java`, `device/DeviceDtos.java` (`record ConfirmRequest(@NotBlank(message = "Password wajib diisi") String confirmPassword)`)
- Modify tests: `ingest/RakitanFixtures.java` (`issueNewKey` memakai `user("owner").roles("OWNER")` dan body password), `security/AccessControlTests.java` (`deviceKeyIsOwnerOnly` mengirim body `{"confirmPassword":"x"}`)
- Test: `backend/src/test/java/com/biometricattendance/device/DeviceKeyConfirmTests.java`
- Frontend: `frontend/src/pages/DevicesPage.tsx` (`NewKeyDialog` ditambah input password `type="password"` berlabel "Password Anda"; tombol nonaktif selama kosong), test di `frontend/src/pages/DevicesPage.test.tsx`

**Interfaces:**
- Produces: `@Service PasswordConfirmation.require(String password)`. Mencocokkan password akun `CurrentUser.username()`. Jika akun tidak ada atau password tidak cocok, melempar `ApiException.badRequest("Password salah.")`.
- Urutan di `DeviceService.issueKey(Long id, String confirmPassword)`: 404 mesin, lalu 400 bukan rakitan, lalu password, lalu terbitkan kunci.

- [ ] **Step 1: test gagal.** `DeviceKeyConfirmTests`:
  - `wrongPasswordRejected`: 400 dengan `$.detail` "Password salah.", dan key_hash tidak berubah;
  - `missingPasswordRejected`: body `{}` → 400;
  - `correctPasswordIssuesKey`: 200, ada `$.issuedKey.deviceKey`, dan kunci lama 401 di heartbeat.
- [ ] **Step 2:** jalankan. Expected: gagal (400/200 tidak sesuai).
- [ ] **Step 3: implementasi.** Perbarui fixture dan AccessControlTests. Frontend mengirim `{ confirmPassword }`, dengan test "Buat kode baru meminta password" (tombol nonaktif sebelum diisi, dan body berisi password).
- [ ] **Step 4:** jalankan `DeviceKeyConfirmTests,AccessControlTests,Rakitan*Tests,Device*Tests` dan vitest DevicesPage. Expected: lulus semua.
- [ ] **Step 5: Commit** (laporkan).

### Task 3: Migration V10 dan paket pengaturan ringan

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__pengaturan_mesin.sql`, berisi:
  - tabel `device_settings` (device_id PK FK, version INT, header_text VARCHAR(32), greeting VARCHAR(64), name_display VARCHAR(10) CHECK IN ('FULL','FIRST','PIN'), brightness INT CHECK 10–100, buzzer_volume INT CHECK 0–100, duplicate_notice_seconds INT CHECK 0–600, heartbeat_seconds INT CHECK 2–20, updated_by, updated_at);
  - kolom `devices`: settings_applied_version INT NOT NULL DEFAULT 0, network_config_id BIGINT, pending_key_hash VARCHAR(64) UNIQUE, pending_key_enc TEXT;
  - tabel `device_network_configs` (id identity, device_id FK, server_url VARCHAR(255), created_by, created_at) dan FK devices.network_config_id;
  - tabel `device_network_wifi` (config_id FK, position INT, ssid VARCHAR(32), password_enc TEXT, PK(config_id, position));
  - kolom `device_commands`: network_config_id BIGINT FK; constraint type ditambah 'APPLY_NETWORK','ROTATE_KEY';
  - unique index `ux_device_commands_risky_device` ON (device_id) WHERE type IN ('APPLY_NETWORK','ROTATE_KEY') AND status IN ('MENUNGGU','DIKIRIM').
- Create (paket `devicesettings`): `DeviceSettings.java` (entity), `DeviceSettingsRepository.java`, `DeviceSettingsDefaults.java` (`@ConfigurationProperties("app.device.settings.default")` record), `DeviceSettingsDtos.java`, `DeviceSettingsService.java`, `DeviceSettingsController.java`
- Modify:
  - `device/Device.java`: `settingsAppliedVersion`, `networkConfigId`, `pendingKeyHash`, `pendingKeyEnc` beserta method domain.
  - `device/DeviceDtos.java`: `DeviceResponse` + `settingsVersion`, `settingsAppliedVersion`.
  - `device/DeviceService.java`: list mengisi `settingsVersion` lewat satu query `findAll` settings menjadi map.
  - `ingest/rakitan/RakitanDtos.java`: `HeartbeatRequest.settingsVersion` (Integer); `HeartbeatResponse.settings` (`SettingsPackage`, null jika tidak dikirim).
  - `ingest/rakitan/RakitanController.java`.
  - `config/SecurityConfig.java`: `/api/device-settings/**` untuk Owner dan HR. `/api/devices/*/settings` sudah tercakup `/api/devices/**`.
  - `application.properties`: bawaan sesuai spec §18.1.
- Test: `backend/src/test/java/com/biometricattendance/devicesettings/DeviceSettingsTests.java`, `AccessControlTests`

**Interfaces:**
- Produces:
  - `record SettingsValues(String headerText, String greeting, String nameDisplay, int brightness, int buzzerVolume, int duplicateNoticeSeconds, int heartbeatSeconds)` dengan Bean Validation sesuai batas spec.
  - `record SettingsResponse(Long deviceId, int version, int appliedVersion, SettingsValues values, Instant updatedAt, String updatedBy)`.
  - `DeviceSettingsService`:
    - `get(Long deviceId)`;
    - `update(Long deviceId, SettingsValues)`: bukan rakitan → 400 "Pengaturan hanya untuk alat rakitan";
    - `updateAll(SettingsValues) → List<SettingsResponse>`;
    - `SettingsPackage packageFor(Device device, Integer reportedVersion)`: mencatat `min(reported, version)` ke `settingsAppliedVersion` dan mengembalikan paket jika version > 0 && version != reported.
  - `record SettingsPackage(int version, String headerText, String greeting, String nameDisplay, int brightness, int buzzerVolume, int duplicateNoticeSeconds, int heartbeatSeconds)`.
  - Endpoint: `GET`/`PUT /api/devices/{id}/settings`, `PUT /api/device-settings`.

- [ ] **Step 1: test gagal.** `DeviceSettingsTests`:
  - `defaultsBeforeFirstSave`: GET → version 0 dengan nilai bawaan spec, dan heartbeat → `$.settings` null;
  - `saveBumpsVersionAndHeartbeatDeliversOnce`: PUT → version 1; heartbeat dengan `settingsVersion:0` → `$.settings.version` 1 dan `$.settings.greeting`; heartbeat dengan `settingsVersion:1` → `$.settings` null; GET → appliedVersion 1;
  - `unchangedSaveKeepsVersion`;
  - `validationRejectsOutOfRange`: heartbeatSeconds 25 dan brightness 5 → 400;
  - `zkPushRejected` → 400;
  - `applyToAllSkipsInactiveAndZkAndUnchanged` (Review Focus 4);
  - `machineAheadOfServerGetsPackageAgain` (Review Focus 5);
  - `auditPerDevice`: aksi `UPDATE_SETTINGS`.
  - Untuk `AccessControlTests`, tambahkan GET/PUT `/api/devices/{MISSING}/settings` dan PUT `/api/device-settings` dengan body valid (Owner/HR lolos, Karyawan 403, anonim 401). Owner/HR mendapat 404 untuk MISSING dan 200 untuk `/api/device-settings`.
- [ ] **Step 2:** jalankan. Expected: gagal kompilasi atau 404.
- [ ] **Step 3: implementasi.** Entity `DeviceSettings` memakai `@Version` agar dua simpan bersamaan tidak menaikkan versi yang sama secara diam-diam. `updateAll` memakai `deviceRepository` alat rakitan aktif.
- [ ] **Step 4:** jalankan `DeviceSettingsTests,AccessControlTests,RakitanHeartbeatTests,Device*Tests`. Expected: lulus.
- [ ] **Step 5: Commit** (laporkan).

### Task 4: Kerangka perintah berisiko di antrean

**Files:**
- Modify:
  - `devicecommand/DeviceCommand.java`: `TYPE_APPLY_NETWORK`, `TYPE_ROTATE_KEY`, `isRisky()`, `Long networkConfigId`, factory `applyNetwork(device, configId, createdBy, now)` dan `rotateKey(device, createdBy, now)`.
  - `devicecommand/DeviceCommandRepository.java`.
  - `devicecommand/DeviceCommandService.java`.
  - `devicecommand/DeviceCommandMessages.java`.
  - `fingerprint/FingerprintService.java`: `startEnroll` menolak jika ada perintah berisiko aktif.
  - `application.properties`.
- Test: `backend/src/test/java/com/biometricattendance/devicecommand/RiskyCommandTests.java`. Perintah dibuat langsung lewat repository, seperti `DeviceCommandLifecycleTests`.

**Interfaces:**
- Produces:
  - `DeviceCommandService.createRisky(DeviceCommand, String auditAction)`: memetakan pelanggaran `ux_device_commands_risky_device` ke 409 "Mesin masih menerapkan perubahan jaringan atau kunci.". Melempar `DataIntegrityViolationException` lain apa adanya.
  - `DeviceCommandRepository.existsActiveRisky(Long deviceId)`, `existsActiveEnroll(Long deviceId)`, `findLatestRisky(Long deviceId) → Optional<DeviceCommand>`.
  - `deliver`: perintah berisiko tidak ikut selama mesin punya ENROLL aktif.
  - `cancel`: perintah berisiko hanya untuk Owner (403 "Hanya Owner yang bisa membatalkan perintah ini") dan hanya selama MENUNGGU (409 dengan pesan spec). Batal ROTATE_KEY membuang kunci tertunda: `device.clearPendingKey()` dari Task 3.
  - `expireStale` ditambah:
    - APPLY_NETWORK DIKIRIM dengan `sentAt` < now − `network.result-timeout` → GAGAL `TIDAK_ADA_HASIL`;
    - ROTATE_KEY aktif dengan `createdAt` < now − `rotate-key.timeout` → GAGAL `KUNCI_BARU_TIDAK_DIPAKAI` + `clearPendingKey()`.
    - Keduanya memakai SKIP LOCKED seperti query yang sudah ada.
  - `result()` untuk ROTATE_KEY GAGAL → `clearPendingKey()`.
  - Pesan:
    - APPLY_NETWORK: "Menunggu mesin…", "Jaringan baru diterapkan", `WIFI_GAGAL` → "Gagal tersambung ke Wi-Fi '{detail}', kembali ke pengaturan lama.", `SERVER_TIDAK_TERJANGKAU` → "Server baru tidak terjangkau, kembali ke pengaturan lama.", `TIDAK_ADA_HASIL` → "Mesin tidak merespons. Periksa koneksi mesin.", lainnya → "Perubahan jaringan gagal ({kode}).";
    - ROTATE_KEY: "Kunci baru dipakai", `KUNCI_BARU_TIDAK_DIPAKAI` → "Mesin tidak memakai kunci baru dalam 24 jam. Kunci lama tetap berlaku.", lainnya → "Ganti kunci gagal ({kode}).";
    - Keduanya: DIBATALKAN → "Dibatalkan.".
  - `ResultRequest.detail` (String, opsional, dipotong 255) diteruskan ke `fail(code, detail, now)` untuk jenis selain ENROLL. ENROLL tetap memakai aturan `JARI_SUDAH_TERDAFTAR`.

- [ ] **Step 1: test gagal.** `RiskyCommandTests`:
  - `secondRiskyCommandRejected` (409 pesan spec);
  - `riskyHeldWhileEnrollActive` (heartbeat tidak memuat perintah berisiko selama ENROLL MENUNGGU, lalu muncul setelah ENROLL batal);
  - `enrollRejectedWhileRiskyActive` (409 pesan spec);
  - `riskyMenungguNeverExpires` (expireStale +48 jam pada APPLY_NETWORK MENUNGGU → tetap MENUNGGU);
  - `networkSentWithoutResultFailsAfter10Minutes`;
  - `rotateKeyFailsAfter24Hours` (pending key dikosongkan);
  - `cancelRiskyOnlyOwnerAndOnlyWaiting` (HR 403, Owner MENUNGGU → DIBATALKAN, Owner DIKIRIM → 409);
  - `failureDetailShownInMessage`.
- [ ] **Step 2:** jalankan. Expected: gagal.
- [ ] **Step 3:** implementasi.
- [ ] **Step 4:** jalankan `RiskyCommandTests,DeviceCommand*Tests,Rakitan*Tests,Fingerprint*Tests`. Expected: lulus.
- [ ] **Step 5: Commit** (laporkan).

### Task 5: `APPLY_NETWORK`

**Files:**
- Create (paket `devicesettings`): `DeviceNetworkConfig.java` (entity dengan `@ElementCollection @OrderColumn(name = "position")` dari `@Embeddable WifiEntry(ssid, passwordEnc)` ke `device_network_wifi`), `DeviceNetworkConfigRepository.java`, `NetworkDtos.java`, `NetworkService.java`
- Modify:
  - `devicesettings/DeviceSettingsController.java` (atau `NetworkController.java` baru): `GET`/`POST /api/devices/{id}/network`.
  - `config/SecurityConfig.java`: `/api/devices/*/network` hanya Owner, diletakkan sebelum `/api/devices/**`.
  - `devicecommand/DeviceCommandService.java`: hasil BERHASIL APPLY_NETWORK → `device.applyNetwork(command.getNetworkConfigId())`.
  - `ingest/rakitan/RakitanDtos.java`: `CommandItem` + `NetworkPayload network`, `String key`; `record NetworkPayload(List<WifiPayload> wifi, String serverUrl)`, `record WifiPayload(String ssid, String password)`.
  - `ingest/rakitan/RakitanController.java`: item APPLY_NETWORK diisi `networkService.payload(command.getNetworkConfigId())`.
- Test: `backend/src/test/java/com/biometricattendance/devicesettings/NetworkCommandTests.java`, `AccessControlTests`

**Interfaces:**
- Produces:
  - `record WifiInput(@NotBlank @Size(max = 32) String ssid, String password)`.
  - `record NetworkRequest(@NotBlank(message = "Password wajib diisi") String confirmPassword, @Size(max = 5) List<@Valid WifiInput> wifi, String serverUrl)`.
  - `record NetworkView(Long deviceId, List<String> currentWifi, String currentServerUrl, CommandResponse latest)`. `currentWifi` kosong dan `currentServerUrl` null berarti "Belum pernah diatur dari web"; `latest` adalah perintah berisiko terakhir (bisa ROTATE_KEY).
  - `NetworkService.request(Long deviceId, NetworkRequest) → CommandResponse` (201). Urutannya:
    1. 404 mesin;
    2. 400 bukan rakitan / nonaktif;
    3. password;
    4. validasi isi: minimal satu dari wifi/serverUrl; SSID ganda → "Nama Wi-Fi tidak boleh ganda"; password 1–7 atau > 63 karakter → "Password Wi-Fi 8–63 karakter atau kosong untuk Wi-Fi terbuka"; URL → "Alamat server harus diawali http:// atau https://";
    5. cipher terkonfigurasi;
    6. simpan paket, mengisi password kosong dari paket yang berlaku untuk SSID yang sama;
    7. `createRisky(..., "NETWORK_REQUEST")`.
  - `NetworkService.payload(Long configId) → NetworkPayload` dengan password yang sudah didekripsi. Password Wi-Fi terbuka berupa string kosong.
  - Snapshot audit hanya berisi `ssids` dan `serverUrl`.

- [ ] **Step 1: test gagal.** `NetworkCommandTests`:
  - `requestDeliveredWithDecryptedPasswords`;
  - `wrongPasswordRejected`;
  - `validationMessages` (ganda, panjang password, URL, kosong semua);
  - `successBecomesCurrentNetwork` (GET network → currentWifi + serverUrl);
  - `failureKeepsPreviousNetwork` (GAGAL WIFI_GAGAL detail "Kantor-5G" → pesan spec, current tidak berubah);
  - `blankPasswordKeepsStoredForSameSsid` (payload kedua memuat password lama);
  - `blankPasswordForNewSsidIsOpenNetwork`;
  - `missingCipherKeyRejected` (Spring context terpisah atau `SecretCipher` ditiru lewat `@MockitoBean` yang `isConfigured()` = false; pilih `@MockitoBean` di kelas test kecil `NetworkCipherMissingTests`);
  - `wifiPasswordNeverInApiOrAudit` (Review Focus 3: response POST/GET network, GET /api/devices, GET /api/device-commands/{id}, dan `select string_agg(before_data::text||after_data::text,'') from audit_logs` tidak memuat password).
  - Untuk `AccessControlTests`: GET/POST `/api/devices/{MISSING}/network` → Owner 404 (POST body berisi confirmPassword), HR 403, Karyawan 403, anonim 401.
- [ ] **Step 2:** jalankan. Expected: gagal.
- [ ] **Step 3:** implementasi.
- [ ] **Step 4:** jalankan `NetworkCommandTests,NetworkCipherMissingTests,RiskyCommandTests,AccessControlTests,Rakitan*Tests`. Expected: lulus.
- [ ] **Step 5: Commit** (laporkan).

### Task 6: `ROTATE_KEY` dan kunci dua tahap

**Files:**
- Create: `devicesettings/KeyRotationService.java`, `device/KeyRotatedEvent.java` (`record KeyRotatedEvent(Long deviceId, String serialNumber)`), `device/KeyRevokedEvent.java` (`record KeyRevokedEvent(Long deviceId)`)
- Modify:
  - `device/DeviceRepository.java`: `findByPendingKeyHash`, dan `@Modifying promotePendingKey(id, pendingHash)` yang mengembalikan int. SQL-nya: `update devices set key_hash = pending_key_hash, pending_key_hash = null, pending_key_enc = null, key_first_used_at = coalesce(key_first_used_at, :now) where id = :id and pending_key_hash = :hash`.
  - `device/DeviceContactService.java`: kunci yang cocok dengan `pending_key_hash` → promosi. Jika hasilnya 1, terbitkan `KeyRotatedEvent`. Jika 0 karena request lain sudah memindahkan, cari ulang dengan `findByKeyHash`. Kontak ini tidak memicu `firstConnect`.
  - `device/DeviceService.java`: `issueKey` mengosongkan kunci tertunda dan menerbitkan `KeyRevokedEvent`.
  - `devicecommand/DeviceCommandService.java`: `@EventListener onKeyRotated` → ROTATE_KEY aktif mesin itu menjadi BERHASIL, dengan audit `KEY_ROTATED` oleh `mesin <ID>`. `@EventListener onKeyRevoked` → ROTATE_KEY aktif menjadi DIBATALKAN (audit `CANCEL_COMMAND`, pelaku pengguna).
  - Controller: `POST /api/devices/{id}/rotate-key` (Owner, `ConfirmRequest`).
  - `config/SecurityConfig.java`: `/api/devices/*/rotate-key` hanya Owner.
  - `ingest/rakitan/RakitanController.java`: item ROTATE_KEY diisi `key` = `keyRotation.pendingKey(device)` (dekripsi).
- Test: `backend/src/test/java/com/biometricattendance/devicesettings/KeyRotationTests.java`, `AccessControlTests`

**Interfaces:**
- Produces:
  - `KeyRotationService.request(Long deviceId, String confirmPassword) → CommandResponse` (201). Urutannya: 404, 400 bukan rakitan/nonaktif, password, cipher, perintah berisiko aktif (409), kunci baru (`DeviceKeys.newKey`), lalu `device.setPendingKey(hash, enc)` dan `createRisky(..., "ROTATE_KEY_REQUEST")`.
  - `String pendingKey(Device)`.

- [ ] **Step 1: test gagal.** `KeyRotationTests`:
  - `bothKeysAcceptedDuringTransition` (heartbeat lama → `$.commands[0].key`; heartbeat dengan kunci baru → 200);
  - `oldKeyRejectedAfterNewKeyUsed` (Review Focus 1);
  - `commandSucceedsOnFirstUseAndNoConnectNotice` (status BERHASIL, audit KEY_ROTATED, tidak ada event DEVICE_CONNECTED; pakai `@RecordApplicationEvents` dengan `DeviceContactEvent.firstConnect` false);
  - `concurrentFirstUseOfNewKeyPromotesOnce` (Review Focus 2; dua thread);
  - `machineFailureDiscardsPendingKey`;
  - `revokeCancelsRotation` (issueKey → ROTATE_KEY DIBATALKAN, kunci tertunda 401);
  - `cancelWaitingRotationDiscardsPendingKey`;
  - `pendingKeyNeverInApiOrAudit` (Review Focus 3).
  - Untuk `AccessControlTests`: POST `/api/devices/{MISSING}/rotate-key` → Owner 404, HR/Karyawan 403, anonim 401.
- [ ] **Step 2:** jalankan. Expected: gagal.
- [ ] **Step 3:** implementasi.
- [ ] **Step 4:** jalankan `KeyRotationTests,RiskyCommandTests,DeviceKeyConfirmTests,AccessControlTests,Rakitan*Tests,Device*Tests`. Expected: lulus.
- [ ] **Step 5: Commit** (laporkan).

### Task 7: Frontend halaman Mesin

**Files:**
- Modify: `frontend/src/lib/types.ts`, `frontend/src/pages/DevicesPage.tsx`
- Create:
  - `frontend/src/components/ConfirmPasswordDialog.tsx`;
  - `frontend/src/components/DeviceSettingsDialog.tsx`: bagian "Tampilan & suara", "Jaringan", "Kunci";
  - `frontend/src/components/DeviceSettingsForm.tsx`: form pengaturan ringan, dipakai dialog per mesin dan dialog "Pengaturan semua mesin".
- Test: `frontend/src/pages/DeviceSettings.test.tsx`

**Interfaces:**
- Consumes: endpoint Task 3, 5, dan 6; `POST /api/device-commands/{id}/cancel`; SSE `DEVICE_COMMAND`.
- Tipe baru: `DeviceSettingsValues`, `DeviceSettingsResponse`, `NetworkView`. `Device` ditambah `settingsVersion`, `settingsAppliedVersion`.
- Tampilan:
  - kolom Firmware ditambah "Pengaturan terbaru sudah diterapkan ✓" atau "Menunggu mesin menerapkan versi N" untuk alat rakitan dengan settingsVersion > 0;
  - tombol baris "Pengaturan" untuk semua alat rakitan, dan toolbar "Pengaturan semua mesin";
  - bagian Jaringan dan Kunci hanya untuk Owner;
  - status perintah terakhir dengan tombol "Batalkan" selama MENUNGGU;
  - form jaringan memuat 1–5 baris SSID + password, alamat server, dan petunjuk password tulis saja dari spec §18.5.

- [ ] **Step 1: test gagal.** `DeviceSettings.test.tsx`:
  - `menyimpan pengaturan ringan satu mesin` (PUT body);
  - `pengaturan semua mesin` (PUT /api/device-settings);
  - `HR tidak melihat bagian Jaringan dan Kunci`;
  - `jaringan meminta password lalu POST` (body berisi wifi + confirmPassword, dan password akun tidak muncul di layar setelah kirim);
  - `menampilkan status perintah dan Batalkan saat MENUNGGU`;
  - `ganti kunci meminta password`;
  - `status versi pengaturan di daftar mesin`.
- [ ] **Step 2:** `npx vitest run src/pages/DeviceSettings.test.tsx`. Expected: gagal.
- [ ] **Step 3:** implementasi.
- [ ] **Step 4:** `npm test -- --run`, `npm run lint`, `npm run build`. Expected: lulus, tanpa warning lint baru.
- [ ] **Step 5: Commit** (laporkan).

### Task 8: Simulator dan dokumen

**Files:**
- Modify: `tools/device-simulator/rakitan.mjs`, `docs/protokol-alat-rakitan.md`, `docs/keputusan.md`, `docs/pertanyaan-terbuka.md`, `docs/superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md` (status §14 butir 6)

- [ ] **Step 1: simulator.** `live`:
  - menyimpan `settingsVersion` dari `settings`, mencetak isinya, dan mengirimnya di heartbeat;
  - `--network-result BERHASIL|<kode>` dengan `--network-detail`: APPLY_NETWORK dicetak (password disamarkan `***`), lalu hasilnya dikirim;
  - ROTATE_KEY: tanpa `--rotate-result`, kunci baru dipakai mulai heartbeat berikutnya dan dicetak "Kunci baru dipakai" (kode pengaturan lama tidak lagi berlaku); dengan `--rotate-result <kode>`, kirim GAGAL memakai kunci lama.
  - Uji dengan server tiruan kecil di scratchpad: satu skenario sukses dan satu gagal untuk masing-masing.
- [ ] **Step 2: protokol.**
  - §4: `settingsVersion`, `settings`, format perintah APPLY_NETWORK/ROTATE_KEY, `detail`;
  - §6b baru: perintah jaringan dan kunci, termasuk aturan "coba dulu, baru simpan", kembali otomatis 2 menit, hasil ROTATE_KEY tidak wajib, dan kunci lama ditolak setelah kunci baru dipakai;
  - §8: opsi simulator.
- [ ] **Step 3: keputusan.** K-103 (baris tabel + bagian rinci) merangkum spec §18 dan ruling ledger. Tambahkan Q-037 (ukuran layar menentukan batas teks) ke pertanyaan-terbuka dan ke daftar P0.
- [ ] **Step 4:** jalankan test backend penuh, frontend penuh, lint, dan build. Expected: semua lulus (flaky Leave diulang).
- [ ] **Step 5: Commit** (laporkan).
