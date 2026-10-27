# Perbaikan Kecil Sesuai Requirement v0.6 — Rencana Implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menutup empat celah kecil terhadap requirement v0.6 yang tidak menunggu client atau hardware: test TC-18, akun ikut nonaktif saat karyawan dinonaktifkan, batas percobaan login, dan filter kategori di Absensi Harian.

**Architecture:** Tidak ada modul atau tabel baru. Perubahannya:
- satu test kalkulator;
- satu hook di `EmployeeService.update`;
- satu komponen pembatas login di modul `auth`, disimpan di memori seperti batas laju verifikasi surat (K-116);
- satu parameter query di `AttendanceController.daily`.

**Tech Stack:** Spring Boot 4 / Java 21 / JPA / PostgreSQL (Testcontainers), React + Vite + TypeScript + Vitest.

**Spec:** requirement HLX-TKS-ATT-REQ-001 v0.6:
- AC-WT-05 dan TC-18 (`docs/aturan-bisnis.md:516`);
- FR-EMP-005 dan AC-LV-02 (akun karyawan dinonaktifkan);
- NFR-SEC-004: "Kebijakan kata sandi, batas percobaan masuk, dan sesi yang berakhir otomatis untuk seluruh akun. Nilai rincinya ditetapkan pada desain (IS-06).";
- pemantauan absensi: "Pemantauan dapat difilter per cabang, kategori karyawan, dan tanggal."

Rujukan lain: dokumen review gap https://claude.ai/artifact/8HsHVLem5kZhTEeuw5iFdZ, bagian 3A.

**Urutan antarrencana:** rencana ini dikerjakan pertama. Setelah itu `2026-10-27-review-anomali-validasi-lembur.md`, lalu `2026-10-27-laporan-ekspor-absensi.md`.

## Konfirmasi di awal sesi (tanyakan sebelum Task 1)

1. **Karyawan diaktifkan kembali (Task 2).** Bawaan: akunnya **tidak** ikut aktif otomatis. Owner atau Finance & Accounting mengaktifkannya sendiri di menu Akun, karena akun yang sempat dinonaktifkan perlu ditinjau dulu (misalnya password sudah bocor).
2. **Nilai batas login (Task 3).** Bawaan:
   - **5** kali gagal berturut-turut per username → dikunci **15 menit**;
   - kunci disimpan di memori server, jadi hilang bila server di-restart;
   - login yang berhasil mengosongkan hitungan;
   - nilainya diatur lewat properti `app.login.max-failures` dan `app.login.lock-minutes`.

   Ini nilai keamanan, bukan aturan bisnis, tetapi NFR-SEC-004 menyerahkannya ke tahap desain. Karena itu tetap perlu persetujuan pengguna.
3. **Label status izin (baris 11 review gap)** sengaja tidak dimasukkan karena belum dijawab pengguna.

## Global Constraints

- Ikuti CLAUDE.md:
  - aturan bisnis tidak di-hardcode;
  - setiap endpoint atau parameter baru masuk `AccessControlTests`;
  - error berformat ProblemDetail dengan pesan Bahasa Indonesia;
  - setiap perubahan data diaudit lewat `AuditService` dalam transaksi yang sama.
- **Commit** mengikuti bagian "Aturan Git Commit" di CLAUDE.md, tetapi **hanya bila pengguna meminta**. Kalau tidak diminta, cukup laporkan file yang berubah.
- Test backend dijalankan dari **salinan** `backend` (tanpa `target`) di scratchpad: `./mvnw -q test -Dtest=...`. Jangan matikan `spring-boot:run` milik pengguna. Kalau suite penuh kehabisan memori, jalankan per kelompok kelas dan laporkan apa adanya.
- Test frontend: `cd frontend && npx vitest run`, `npx tsc -b`, `npm run lint`.
- Jangan pernah melaporkan test lulus tanpa benar-benar menjalankannya.
- Label role di layar adalah "Finance & Accounting" (K-132). Kode role tetap `HR_FINANCE`.
- Keputusan baru dicatat di `docs/keputusan.md` mulai **K-137**. Cek dulu nomor terakhir.

## Review Focus

1. **Karyawan dinonaktifkan saat sedang login.** Sesinya harus langsung tidak berlaku, lewat `SessionRevoker.revokeAll(username)` yang juga dipakai `UserAccountService.update`. Diuji di Task 2.
2. **Karyawan tanpa akun dinonaktifkan.** Tidak boleh error, dan tidak ada audit akun yang tercatat. Diuji di Task 2.
3. **Username yang tidak ada dicoba berulang kali.** Tetap dihitung dan dikunci dengan pesan yang sama, supaya penyerang tidak bisa mengetahui username mana yang ada. Diuji di Task 3.
4. **Password benar dimasukkan saat akun masih terkunci.** Tetap ditolak sampai masa kunci habis. Diuji di Task 3.
5. **Filter kategori dengan ID yang tidak ada.** Hasilnya daftar kosong dengan status 200, bukan 500. Diuji di Task 4.

---

### Task 1: Test TC-18 (masuk 09.00 = 7 jam)

**Files:**
- Test: `backend/src/test/java/com/biometricattendance/calculation/DailyAttendanceCalculatorTests.java` (dekat `configuredBreakIsSubtractedFromRegularWork`, ±236)

- [ ] **Step 1: Tulis test**

```java
/** TC-18 (K-110): 08.00–17.00 dengan istirahat 12.00–13.00 = 8 jam; masuk 09.00 = 7 jam. */
@Test
void lateArrivalAtNineCountsSevenWorkingHours() {
    EffectiveRule rule = rule(LocalTime.of(12, 0), LocalTime.of(13, 0));

    assertThat(calculate(rule, "08:00", "17:00").regularWork()).isEqualTo(Duration.ofHours(8));
    assertThat(calculate(rule, "09:00", "17:00").regularWork()).isEqualTo(Duration.ofHours(7));
}
```
Cek dulu bahwa helper `rule(...)` (baris 35) memakai jadwal 08.00–17.00. Kalau belum, buat helper uji dengan jadwal itu. Nilainya harus berasal dari aturan uji, bukan dari konstanta produksi.

- [ ] **Step 2: Jalankan**

Run: `./mvnw -q test -Dtest=DailyAttendanceCalculatorTests`
Expected: PASS. Kalau gagal, **berhenti dan laporkan ke pengguna**, karena yang salah adalah perhitungannya, bukan test-nya.

- [ ] **Step 3: Laporkan.**

---

### Task 2: Akun ikut nonaktif saat karyawan dinonaktifkan

Aturan (K-137):
- Ketika `PUT /api/employees/{id}` mengubah `active` dari `true` menjadi `false`, akun yang terhubung (`UserAccountRepository.findByEmployeeId`) ikut dinonaktifkan dalam transaksi yang sama.
- Perubahan akun dicatat di audit sebagai `UPDATE` pada entitas akun.
- Semua sesi akun itu dicabut.
- Kalau karyawan diaktifkan kembali, akunnya tetap nonaktif (bawaan dari Konfirmasi no. 1).

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/employee/EmployeeService.java` (`update` ±93; pengisian `setActive` ±224)
- Modify: `frontend/src/components/EmployeeFormDialog.tsx`
- Test: `backend/src/test/java/com/biometricattendance/masterdata/MasterDataTests.java`, `backend/src/test/java/com/biometricattendance/auth/SessionRevocationTests.java`, `frontend/src/pages/EmployeesPage.test.tsx`

**Interfaces:**
- Consumes:
  - `UserAccountRepository.findByEmployeeId(Long): Optional<UserAccount>`
  - `UserAccount.setActive(boolean)`
  - `SessionRevoker.revokeAll(String username)`
  - `AuditService.record(String action, String entityType, Object entityId, Object before, Object after)`
  - `UserResponse.from(UserAccount)` untuk snapshot audit (pola yang sama dengan `UserAccountService.update`)

- [ ] **Step 1: Tulis test yang gagal**

- `deactivatingEmployeeDeactivatesLinkedAccount`: buat karyawan beserta akunnya, lalu kirim PUT dengan `active=false`. Harapan:
  - `GET /api/users` menunjukkan akun `active=false`;
  - login memakai password akun itu → 401;
  - ada audit `UPDATE` untuk entitas akun.
- `deactivatingEmployeeEndsLinkedAccountSessions` (di `SessionRevocationTests`, ikuti pola sesi yang sudah ada): setelah karyawan dinonaktifkan, request berikutnya dengan sesi lama → 401.
- `deactivatingEmployeeWithoutAccountStillWorks` → 200.
- `reactivatingEmployeeKeepsAccountInactive`: nonaktifkan lalu aktifkan lagi. Akun tetap `active=false`.

- [ ] **Step 2: Jalankan test dan pastikan gagal**

Run: `./mvnw -q test -Dtest='MasterDataTests,SessionRevocationTests'` → FAIL.

- [ ] **Step 3: Implementasi**

1. Di `EmployeeService.update`, simpan `boolean wasActive = employee.isActive()` sebelum kolom apa pun diubah.
2. Setelah karyawan disimpan, kalau `wasActive && !request.active()`, panggil method privat baru `deactivateLinkedAccount(Employee)`.
3. `deactivateLinkedAccount`:
   - kalau tidak ada akun atau akun sudah nonaktif, tidak melakukan apa-apa;
   - kalau ada: buat snapshot `before`, jalankan `setActive(false)`, simpan, catat audit, lalu `revokeAll`.

Baca dulu komentar auto-flush di `UserAccountService.update` (±82) supaya tidak melanggar CHECK `user_accounts_employee_required`.

- [ ] **Step 4: Jalankan test**

Run: `./mvnw -q test -Dtest='MasterDataTests,SessionRevocationTests,UserAccountTests,AuthFlowTests'` → PASS.

- [ ] **Step 5: Frontend**

Di `EmployeeFormDialog`, saat checkbox Aktif dimatikan untuk karyawan yang punya akun, tampilkan teks bantuan `"Akun login karyawan ini juga akan dinonaktifkan."`. Cara mengetahui apakah karyawan punya akun: lihat field yang sudah dipakai dialog itu (K-133). Kalau belum ada, tampilkan teksnya tanpa syarat.

Tambahkan test di `EmployeesPage.test.tsx`, lalu jalankan `npx vitest run src/pages/EmployeesPage.test.tsx` dan `npx tsc -b`.

- [ ] **Step 6: Catat dan laporkan**

- `docs/keputusan.md`: tambah **K-137 Akun ikut nonaktif bersama karyawan** (FR-EMP-005, AC-LV-02).
- `docs/pertanyaan-terbuka.md`: tambahkan catatan pada IS-06 bahwa penonaktifan sudah otomatis, sedangkan pengaktifan kembali dilakukan manual.

---

### Task 3: Batas percobaan login

Aturan (K-138, nilai dari Konfirmasi no. 2):
- Setelah `max-failures` kali gagal berturut-turut untuk satu username, login username itu ditolak selama `lock-minutes`, termasuk bila passwordnya benar. Username dinormalisasi dengan trim dan lowercase.
- Respons penolakan: **429** berformat ProblemDetail dengan detail `"Terlalu banyak percobaan masuk. Coba lagi dalam N menit."`. N dibulatkan ke atas.
- Login yang berhasil mengosongkan hitungan.
- Username yang tidak ada juga dihitung.
- Saat kunci mulai berlaku, catat di audit dengan aksi `LOGIN_LOCKED`, entitas `AUTH`, dan aktor = username yang dicoba (`AuditService.record(actor, ...)`).

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/auth/LoginAttemptLimiter.java`
- Modify: `backend/src/main/java/com/biometricattendance/auth/AuthController.java` (`login` 53-68)
- Modify: `backend/src/main/resources/application.properties` (`app.login.max-failures=5`, `app.login.lock-minutes=15`)
- Modify: `frontend/src/pages/LoginPage.tsx`
- Test: `backend/src/test/java/com/biometricattendance/auth/LoginAttemptLimiterTests.java` (unit, `Clock` tetap), `backend/src/test/java/com/biometricattendance/auth/AuthFlowTests.java`, `frontend/src/pages/LoginPage.test.tsx`

**Interfaces:**
- Produces: `LoginAttemptLimiter` (`@Component`), dengan:
  - konstruktor `(Clock clock, @Value("${app.login.max-failures}") int maxFailures, @Value("${app.login.lock-minutes}") int lockMinutes, AuditService audit)`;
  - `void checkAllowed(String username)`, yang melempar `ApiException` 429 bila username sedang terkunci;
  - `void recordFailure(String username)`;
  - `void recordSuccess(String username)`.
- Penyimpanan: `ConcurrentHashMap<String, State>` dengan `record State(int failures, Instant lockedUntil)`. Entri kedaluwarsa dibersihkan saat dibaca. Kalau belum ada bean `Clock`, cek cara modul lain mendapat waktu (`PublicLetterController` atau `AttendanceService.today()`) dan ikuti caranya.

- [ ] **Step 1: Tulis test unit yang gagal** (`LoginAttemptLimiterTests`)

- `locksAfterMaxFailures`: 5× `recordFailure` → `checkAllowed` melempar 429.
- `unlocksAfterLockMinutes`: `Clock` dimajukan 15 menit 1 detik → `checkAllowed` tidak melempar.
- `successResetsFailures`: 4 gagal, 1 sukses, lalu 4 gagal lagi → belum terkunci.
- `usernamesAreNormalised`: `"Budi "` dan `"budi"` dihitung bersama.

- [ ] **Step 2: Tulis test integrasi yang gagal** (`AuthFlowTests`, username unik per test)

- `fiveWrongPasswordsLockTheAccount`: 5× password salah → masing-masing 401. Percobaan ke-6 dengan password **benar** → 429, dengan detail yang diawali `"Terlalu banyak percobaan masuk"`.
- `unknownUsernameIsLimitedToo`: 6× username acak yang tidak ada → percobaan ke-6 mendapat 429.

- [ ] **Step 3: Jalankan test dan pastikan gagal**

Run: `./mvnw -q test -Dtest='LoginAttemptLimiterTests,AuthFlowTests'` → FAIL.

- [ ] **Step 4: Implementasi**

Di `AuthController.login`:
1. Panggil `limiter.checkAllowed(username)` sebelum `authenticate`.
2. Bungkus `authenticate` dengan try/catch `AuthenticationException`: panggil `recordFailure`, lalu lempar ulang exception-nya supaya respons 401 yang sudah ada tidak berubah.
3. Panggil `recordSuccess` setelah login berhasil.

- [ ] **Step 5: Jalankan test**

Run: `./mvnw -q test -Dtest='LoginAttemptLimiterTests,AuthFlowTests,SessionCookieHttpTests,AccessControlTests'` → PASS.

- [ ] **Step 6: Frontend**

Di `LoginPage.test.tsx`, mock `POST /api/auth/login` agar mengembalikan 429 dengan detail `"Terlalu banyak percobaan masuk. Coba lagi dalam 15 menit."`, lalu pastikan teks itu tampil. Ubah `LoginPage.tsx` hanya kalau pesan 429 belum ditampilkan apa adanya. Jalankan `npx vitest run src/pages/LoginPage.test.tsx`.

- [ ] **Step 7: Catat dan laporkan**

- `docs/keputusan.md`: tambah **K-138 Batas percobaan login**: nilai, perilaku, dan catatan bahwa kunci hilang saat server di-restart. Sebutkan NFR-SEC-004.
- `docs/pertanyaan-terbuka.md`: pada IS-06, catat bahwa nilai sementara sudah dipakai.

---

### Task 4: Filter kategori di Absensi Harian

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/attendance/AttendanceController.java:54` (parameter `categoryId`)
- Modify: `backend/src/main/java/com/biometricattendance/attendance/AttendanceService.java:80`
- Modify: `backend/src/main/java/com/biometricattendance/dashboard/DashboardService.java` (pemanggil `daily`; isi `null`)
- Modify: `frontend/src/pages/AttendancePage.tsx` (pilihan Kategori di samping Cabang, ±41-80)
- Test: `backend/src/test/java/com/biometricattendance/attendance/AttendanceTests.java`, `frontend/src/pages/AttendancePages.test.tsx`

**Interfaces:**
- Produces: `GET /api/attendance/daily?date=&branchId=&categoryId=`. Kalau `categoryId` kosong, data tidak difilter.
- Produces: `AttendanceService.daily(LocalDate date, Long branchId, Long categoryId)`.

- [ ] **Step 1: Tulis test yang gagal**

- Backend (`AttendanceTests`): dua karyawan di cabang yang sama dengan kategori berbeda. `?categoryId=<A>` hanya mengembalikan karyawan A. `?categoryId=999999` → 200 dengan `rows` kosong.
- Frontend (`AttendancePages.test.tsx`): memilih kategori memanggil `GET /api/attendance/daily?date=...&categoryId=3`. Opsinya diambil dari `GET /api/employee-categories`.

- [ ] **Step 2: Jalankan test dan pastikan gagal.**

- [ ] **Step 3: Implementasi**

Saring `list` dari `employees.search(branchId, true, null)` dengan `categoryId == null || e.getCategory().getId().equals(categoryId)`. Dengan sekitar 25 karyawan, penyaringan di memori sudah cukup.

- [ ] **Step 4: Jalankan test**

Run: `./mvnw -q test -Dtest='AttendanceTests,DashboardTests,AccessControlTests'`, `npx vitest run src/pages/AttendancePages.test.tsx`, dan `npx tsc -b` → PASS.

- [ ] **Step 5: Laporkan.** Tidak perlu keputusan baru, karena kebutuhan ini sudah tertulis di v0.6.

---

### Task 5: Verifikasi akhir

- [ ] Jalankan suite backend penuh dari salinan (`./mvnw -q test`). Kalau kehabisan memori, jalankan per paket dan laporkan apa adanya.
- [ ] Jalankan `npx vitest run`, `npx tsc -b`, dan `npm run lint`.
- [ ] Di dokumen review gap (Claude Docs, bagian 3A), tandai TC-18, akun nonaktif, batas login, dan filter kategori sebagai selesai.
- [ ] Laporkan ke pengguna: file yang berubah, angka hasil test yang sebenarnya, dan hal yang belum dikerjakan.
