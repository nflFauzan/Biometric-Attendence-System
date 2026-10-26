# Akun Karyawan dengan Pesan WhatsApp — Rencana Implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tambah karyawan sekaligus akun login, kirim kredensial lewat pesan WhatsApp siap kirim (juga saat reset password), buka penulisan data karyawan untuk Finance & Accounting, dan hapus fitur impor karyawan dari Excel.

**Architecture:**
- **Backend.** `POST /api/employees` menerima objek `account` opsional. `EmployeeService.create` membuat karyawan, lalu memanggil `UserAccountService.create` di transaksi yang sama.
- **Frontend.** Usulan username/password dan teks pesan dihitung di frontend dengan fungsi murni di `lib/credentials.ts` dan `lib/whatsapp.ts`. `FormDialog` mendapat dua kemampuan kecil: field bersyarat dan nilai turunan. Jendela pesan dibuat sebagai komponen tersendiri.

**Tech Stack:** Spring Boot 4 / Java 21 / JPA / MockMvc; React + Vite + TS / TanStack Query / Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-26-akun-karyawan-pesan-wa-design.md`

## Global Constraints

- Pengguna commit sendiri. **Jangan commit atau push.** Langkah "commit" diganti dengan mencatat file yang berubah di ledger.
- Test backend dijalankan dari salinan repo di scratchpad: `bash "C:/Users/THINKP~1/AppData/Local/Temp/claude/C--all-project-Biometric-Attendence-System/57aa9c82-816b-4568-bb22-9bbe55e3ca56/scratchpad/bt.sh" "KelasA,KelasB"`. Pisahkan kelas dengan koma, bukan `+`.
- Test frontend: `cd frontend && npx vitest run <file>`, lalu `npx tsc -b` dan `npm run build` di akhir.
- Password tidak boleh masuk respons API, audit log, log aplikasi, atau localStorage.
- Setiap perubahan data dicatat di audit log lewat `AuditService` dalam transaksi yang sama. Pesan error ditulis dalam Bahasa Indonesia, berformat ProblemDetail.
- Migration lama tidak boleh diubah. Fitur ini **tidak** butuh migration baru.
- Kode role `HR_FINANCE` tidak berubah. Labelnya "Finance & Accounting".
- Teks pesan WA harus persis seperti spec §6. Nama aplikasi = konstanta `APP_NAME = 'Sistem Absensi Takka Steel'`.
- Data pribadi dari `docs/Template_Pengisian_Data_Karyawan*.xlsx` tidak boleh disalin ke test atau kode.

## Review Focus

1. **No. HP dengan spasi, tanda hubung, atau `+62`** (mis. `+62 812-3456-7890`) → `wa.me/6281234567890`. Ditest di Task 3.
2. **Nama dengan tanda baca atau awalan saja** (mis. `M.`, `Siti`, `O'Neil`) → username tetap valid menurut pola server dan tidak kosong. Kalau kosong, field dibiarkan kosong agar pengguna mengisi sendiri. Ditest di Task 3.
3. **Owner mengubah usulan username, lalu mengetik ulang nama** → username yang diubah manual tidak tertimpa. Ditest di Task 4.
4. **Username sudah dipakai saat tambah karyawan** → 409 dan karyawan tidak tersimpan. Ditest di Task 2.
5. **F&A mengirim `account.role = OWNER` lewat API** → 403 dan karyawan tidak tersimpan. Ditest di Task 2.

---

### Task 1: Hapus fitur impor karyawan dari Excel

**Files:**
- Delete: `backend/src/main/java/com/biometricattendance/employee/EmployeeImportParser.java`, `EmployeeImportService.java`, `EmployeeImportController.java`, `EmployeeImportDtos.java`
- Delete: `backend/src/test/java/com/biometricattendance/employee/EmployeeImportParserTests.java`, `EmployeeImportTests.java`
- Delete: `frontend/src/pages/EmployeeImportPage.tsx`, `frontend/src/pages/EmployeeImportPage.test.tsx`
- Modify: `backend/pom.xml` (hapus dependency `poi-ooxml` dan properti `poi.version`)
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java:57` (hapus matcher `/api/employee-imports/**`)
- Modify: `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java:115-117` (hapus dua baris impor)
- Modify: `frontend/src/App.tsx` (rute `karyawan/impor` dan import-nya), `frontend/src/pages/EmployeesPage.tsx` (toolbar impor), `frontend/src/pages/EmployeesPage.test.tsx` (test tautan impor), `frontend/src/lib/types.ts` (tipe `Import*`)

**Interfaces:**
- Produces: `ApiException.withProperty`, `ApiError.problem`, `UserAccountDtos.USERNAME_PATTERN` tetap ada dan dipakai Task 2.

- [ ] **Step 1:** Hapus file dan baris di atas. `apiForm` di `lib/api.ts` dihapus bila tidak ada pemakai lain (cek dengan grep).
- [ ] **Step 2:** Pastikan tidak ada sisa referensi.
  - Run: `grep -rn "employee-imports\|EmployeeImport\|poi-ooxml\|ImportPreview\|ImportCommit" backend/src backend/pom.xml frontend/src`
  - Expected: tidak ada output.
- [ ] **Step 3:** Pastikan frontend tetap lolos.
  - Run: `cd frontend && npx tsc -b && npx vitest run src/pages/EmployeesPage.test.tsx`
  - Expected: tsc bersih, test PASS.
- [ ] **Step 4:** Pastikan backend tetap lolos.
  - Run: `bt.sh "AccessControlTests,MasterDataTests"`
  - Expected: PASS (kompilasi berhasil tanpa POI).
- [ ] **Step 5:** Catat file yang berubah di ledger. Jangan commit.

### Task 2: Backend — karyawan + akun dalam satu transaksi, tanggal masuk otomatis, hak tulis F&A

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/employee/EmployeeDtos.java`
- Modify: `backend/src/main/java/com/biometricattendance/employee/EmployeeService.java`
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java:58-59` (hapus dua matcher khusus Owner untuk POST/PUT karyawan; matcher manajer di baris 60 sudah mencakup `/api/employees/**`)
- Create: `backend/src/test/java/com/biometricattendance/employee/EmployeeAccountTests.java`
- Modify: `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java` (`ownerOnlyEmployeeWrites` → `managerEmployeeWrites`)
- Modify: `backend/src/test/java/com/biometricattendance/masterdata/MasterDataTests.java` (bila ada test yang mengandalkan `joinDate` wajib)

**Interfaces:**
- Produces (JSON kontrak untuk Task 4):
  - `POST /api/employees` body = `EmployeeRequest`, dengan field opsional baru `account: { username: string, password: string, role: 'OWNER'|'HR_FINANCE'|'EMPLOYEE' } | null`. `joinDate` boleh `null`.
  - Respons tetap `EmployeeResponse`, tanpa password.
- Java:
  - `EmployeeDtos.AccountRequest(String username, String password, Role role)` dengan validasi yang sama dengan `CreateUserRequest`: `@NotBlank` + `@Pattern(USERNAME_PATTERN, USERNAME_MESSAGE)`; `@NotBlank` + `@Size(min = MIN_PASSWORD_LENGTH, max = 100, message = "Password minimal 8 karakter")`; `@NotNull(message = "Role wajib dipilih")`.
  - `EmployeeRequest` mendapat komponen terakhir `@Valid AccountRequest account`. Anotasi `@NotNull` pada `joinDate` dihapus.

- [ ] **Step 1: Tulis test yang gagal** di `EmployeeAccountTests extends IntegrationTest`, dengan data sintetis dan username unik per test:
  - `createWithAccountMakesBothAndHidesPassword`
    - Owner POST dengan `account {username:"<u>@takka.id", password:"<u>-X1-takka", role:"EMPLOYEE"}` → 201.
    - Respons tidak memuat string password.
    - `accounts.findByUsername` ada, terhubung ke karyawan baru, `mustChangePassword` true, `passwordEncoder.matches` true.
    - Tidak ada `auditLogs.findAll()` yang `afterData`-nya memuat password.
  - `createWithoutJoinDateUsesTodayInBranchZone`
    - `joinDate` null → `joinDate == LocalDate.now(ZoneId.of(<timezone cabang>))`.
  - `updateWithoutJoinDateKeepsExisting`
    - Buat dengan `2026-01-05`, lalu PUT tanpa `joinDate` → tetap `2026-01-05`.
  - `duplicateUsernameRollsBackEmployee`
    - Akun dengan username X sudah ada. POST karyawan baru dengan username X → 409 "Username sudah dipakai".
    - `employees.existsByEmployeeNumberIgnoreCase(<no baru>)` false.
  - `financeCanCreateEmployeeWithEmployeeAccount`
    - `user("fa").roles("HR_FINANCE")` → 201, dan akun role EMPLOYEE dibuat.
  - `financeCannotCreateOwnerAccountAndNothingIsSaved`
    - HR_FINANCE dengan `account.role = OWNER` → 403 "Finance & Accounting hanya boleh mengelola akun karyawan".
    - Karyawan tidak tersimpan.
  - `accountPasswordTooShortIsFieldError`
    - Password `"pendek"` → 400, dengan error field `account.password`.
- [ ] **Step 2:** Ubah `AccessControlTests`.
  - Ganti `ownerOnlyEmployeeWrites` / `employeeDataIsWritableByOwnerOnly` menjadi `managerEmployeeWrites` / `employeeDataIsWritableByManagers` untuk tiga baris yang tersisa.
  - Isi test: EMPLOYEE → 403 dengan `ProblemResponses.FORBIDDEN`; tanpa login → 401; OWNER **dan** HR_FINANCE → status bukan 401/403.
  - Ganti juga komentar K-128 menjadi rujukan K-135.
- [ ] **Step 3:** Jalankan dan pastikan gagal.
  - Run: `bt.sh "EmployeeAccountTests,AccessControlTests"`
  - Expected: FAIL. `account` diabaikan, `joinDate` null → 400, HR → 403.
- [ ] **Step 4:** Implementasi.
  - **`EmployeeService`** mendapat dependency `UserAccountService`. Ini aman: `UserAccountService` tidak bergantung pada `EmployeeService`.
  - **`create`**
    - Bila `account != null`, panggil `accounts.create(new CreateUserRequest(a.username(), a.password(), a.role(), created.id()))` setelah karyawan disimpan dan diaudit.
    - Exception dari sana membatalkan seluruh transaksi.
  - **`apply` menerima `LocalDate joinDate` efektif:**
    - saat create: `request.joinDate()` atau `LocalDate.now(ZoneId.of(branch.getTimezone()))` (cabang hasil lookup `branchId`);
    - saat update: `request.joinDate()` atau `employee.getJoinDate()`.
  - Validasi "Tanggal keluar tidak boleh sebelum tanggal masuk" memakai tanggal efektif ini.
  - Hapus `.requestMatchers(HttpMethod.POST, "/api/employees")` dan `.requestMatchers(HttpMethod.PUT, "/api/employees/*", "/api/employees/*/branches")` dari `SecurityConfig`.
- [ ] **Step 5:** Jalankan dan pastikan lolos.
  - Run: `bt.sh "EmployeeAccountTests,AccessControlTests,MasterDataTests,UserAccountTests"`, ditambah kelas test cabang absen (cari dengan `grep -l "/branches" backend/src/test -r`).
  - Expected: semua PASS.
- [ ] **Step 6:** Catat di ledger.

### Task 3: Frontend — usulan kredensial, teks pesan, nomor WA

**Files:**
- Create: `frontend/src/lib/credentials.ts`, `frontend/src/lib/credentials.test.ts`
- Modify: `frontend/src/lib/whatsapp.ts`, `frontend/src/lib/whatsapp.test.ts`

**Interfaces (Produces):**
```ts
// lib/credentials.ts
export const APP_NAME = 'Sistem Absensi Takka Steel'
export const USERNAME_DOMAIN = '@takka.id'
export function suggestLocalPart(fullName: string): string          // '' bila tak ada kata yang tersisa
export function suggestUsername(fullName: string): string           // '' bila local part kosong, selain itu `${local}@takka.id`
export function suggestPassword(username: string, employeeNumber?: string | null): string
// `${local}-${employeeNumber}-takka`, atau `${local}-takka` bila employeeNumber kosong; local = bagian sebelum '@'; '' bila username kosong
export type CredentialKind = 'created' | 'reset'
export function credentialMessage(p: { kind: CredentialKind; name: string; username: string; password: string; origin: string }): string
// lib/whatsapp.ts (tambahan)
export function normalizeWhatsAppNumber(phone: string | null | undefined): string | null
export function whatsAppSendUrl(text: string, phone?: string | null): string
```

- [ ] **Step 1: Tulis test yang gagal.**
  - **`suggestLocalPart`:**
    - `'Muhammad Dafa'`→`'dafa'`, `'Siti Balqis'`→`'balqis'`, `'M Rochmat'`→`'rochmat'`, `'M. Rochmat'`→`'rochmat'`, `'Eni Daniyati'`→`'eni'`, `"O'Neil Putra"`→`'oneil'`, `'Siti'`→`'siti'`, `'  '`→`''`.
    - Awalan dilewati hanya bila masih ada kata sesudahnya. Awalan: muhammad, muhamad, mohammad, mohamad, moh, muh, m, siti. Huruf kecil, hanya `a-z0-9`.
  - **`suggestUsername('Eni Daniyati')`** → `'eni@takka.id'`; `suggestUsername('')` → `''`.
  - **`suggestPassword`:** `('eni@takka.id','006')` → `'eni-006-takka'`; `('owner', null)` → `'owner-takka'`; `('', '006')` → `''`.
  - **`credentialMessage` kind `created`** sama persis dengan blok "Akun baru" di spec §6, dengan `{nama aplikasi}`=APP_NAME, `{origin}`=`'https://absen.example'`. Bandingkan dengan string literal utuh di test.
  - **`credentialMessage` kind `reset`** memuat `'Password akun *Sistem Absensi Takka Steel* Kakak telah direset oleh Admin.'`, `'kredensial baru untuk masuk'`, `'*Password Baru*'`, dan tidak memuat `'sudah dibuat'`.
  - **`normalizeWhatsAppNumber`:** `'0812-3456-7890'`→`'6281234567890'`, `'+62 812 3456 7890'`→`'6281234567890'`, `'6281234567890'`→`'6281234567890'`, `'81234567890'`→`'6281234567890'`, `''`/`null`/`'-'`→`null`.
  - **`whatsAppSendUrl('a b', '0812')`** → `'https://wa.me/62812?text=a%20b'`; tanpa nomor → `'https://wa.me/?text=a%20b'`.
- [ ] **Step 2:** Run `npx vitest run src/lib/credentials.test.ts src/lib/whatsapp.test.ts`. Expected: FAIL (modul atau fungsi belum ada).
- [ ] **Step 3:** Implementasikan. Teks pesan disusun persis seperti spec §6. Baris pemisah `━` berjumlah 21 karakter. Label diberi spasi agar titik dua sejajar seperti di spec.
- [ ] **Step 4:** Run lagi. Expected: PASS.
- [ ] **Step 5:** Catat di ledger.

### Task 4: Frontend — FormDialog bersyarat/turunan, jendela pesan, form karyawan

**Files:**
- Modify: `frontend/src/components/FormDialog.tsx`, `frontend/src/components/CrudPage.tsx`
- Create: `frontend/src/components/CredentialMessageDialog.tsx`, `frontend/src/components/CredentialMessageDialog.test.tsx`
- Modify: `frontend/src/pages/EmployeesPage.tsx`, `frontend/src/pages/EmployeesPage.test.tsx`

**Interfaces:**
- Consumes: Task 3 (semua fungsi); Task 2 (kontrak JSON `account`).
- Produces:
  ```ts
  // FormDialog.tsx
  interface FieldDef { /* ...yang lama */ visible?: (values: FormValues) => boolean }
  interface FormDialogProps { /* ...yang lama */ derive?: (next: FormValues, prev: FormValues) => FormValues }
  // CrudPage.tsx
  derive?: (next: FormValues, prev: FormValues, editing: T | null) => FormValues
  onSaved?: (saved: T, editing: T | null, values: FormValues) => void   // values = isian form yang dikirim
  // CredentialMessageDialog.tsx
  export interface CredentialMessage { kind: CredentialKind; name: string; username: string; password: string; phone?: string | null }
  export function CredentialMessageDialog(props: { message: CredentialMessage; onClose: () => void }): JSX.Element
  ```
- Aturan `derive`: field turunan ikut diperbarui hanya bila nilai lamanya sama dengan usulan dari `prev`. Dengan begitu, nilai yang diubah manual tidak tertimpa. Field yang `visible`-nya false tidak dirender dan tidak divalidasi `required`.

- [ ] **Step 1: Tulis test yang gagal.**
  - **`CredentialMessageDialog.test.tsx`:**
    - Merender textarea hanya-baca berisi `credentialMessage(...)` dengan `origin = window.location.origin`.
    - Tombol "Salin pesan" memanggil `navigator.clipboard.writeText` (di-stub) dengan teks itu, lalu muncul "Pesan disalin".
    - Tautan "Kirim WA" punya `href` = `whatsAppSendUrl(text, '0812…')` dan `target="_blank"`.
    - Teks "Password ini hanya ditampilkan sekali. Untuk mengirim ulang, reset password." tampil.
    - `localStorage.setItem` tidak dipanggil (spy).
  - **`EmployeesPage.test.tsx`:**
    - `'form tambah tanpa tanggal masuk/keluar dan usulan akun mengikuti nama'`
      - Owner membuka Tambah. Tidak ada label "Tanggal masuk" atau "Tanggal keluar".
      - "Buatkan akun login" tercentang.
      - Ketik No. `006` dan Nama `Eni Daniyati` → Username `eni@takka.id`, Password sementara `eni-006-takka`, PIN mesin `006`.
    - `'username yang diubah manual tidak ditimpa'`
      - Ubah Username jadi `eni.d@takka.id`, lalu ubah Nama → Username tetap `eni.d@takka.id`.
    - `'menyimpan karyawan dengan akun lalu menampilkan pesan'`
      - POST `/api/employees` body memuat `account: {username:'eni@takka.id', password:'eni-006-takka', role:'EMPLOYEE'}` dan `joinDate: null`.
      - Setelah 201, jendela pesan tampil dengan "Halo Kak Eni Daniyati!".
    - `'tanpa centang akun, body tanpa account dan tanpa jendela pesan'`
    - `'F&A bisa menambah karyawan tetapi tidak memilih role'`
      - HR_FINANCE: tombol Tambah ada, field Role tidak ada, body `account.role = 'EMPLOYEE'`.
      - Test K-128 lama ("HR hanya melihat") diganti test ini.
    - `'form ubah: ada Tanggal keluar dan Aktif, tanpa Tanggal masuk dan bagian akun'`
- [ ] **Step 2:** Run `npx vitest run src/components/CredentialMessageDialog.test.tsx src/pages/EmployeesPage.test.tsx`. Expected: FAIL.
- [ ] **Step 3:** Implementasikan.
  - **`FormDialog`:** `set` menghitung `next`, lalu menerapkan `derive?.(next, current) ?? next`. Field yang `visible` false dilewati saat render.
  - **`CrudPage`:** meneruskan `derive` ke `FormDialog`, dan memanggil `onSaved(saved, editing, values)`.
  - **`EmployeesPage`:**
    - `readOnly` hanya untuk role selain OWNER/HR_FINANCE;
    - field tambah tanpa `joinDate`/`endDate`;
    - checkbox `createAccount` (label "Buatkan akun login", default `true`);
    - `username` (label "Username"), `password` (label "Password sementara", type `text` supaya terlihat), dan `accountRole` (label "Role", hanya Owner, default `EMPLOYEE`), masing-masing `visible: v => v.createAccount === true`;
    - `derive` mengisi `username`, `password`, dan `devicePin` dari `fullName`/`employeeNumber` dengan aturan di atas;
    - field ubah: tanpa `joinDate`, dengan `endDate` dan `active`;
    - `toBody` mengirim `joinDate: null` saat create, mengirim `joinDate` lama saat update (dari `editing.joinDate`), dan `account` hanya bila `createAccount`;
    - `onSaved` membuka `CredentialMessageDialog` bila create dengan akun, berisi `kind 'created'`, nama, username, password, dan HP dari values.
- [ ] **Step 4:** Run lagi, ditambah `npx vitest run src/components src/pages`, karena `FormDialog`/`CrudPage` dipakai banyak halaman. Expected: PASS.
- [ ] **Step 5:** Catat di ledger.

### Task 5: Frontend — reset/buat akun dengan pesan di menu Akun dan detail karyawan; cabang absen untuk F&A

**Files:**
- Modify: `frontend/src/pages/UsersPage.tsx`
- Modify: `frontend/src/pages/EmployeeDetailPage.tsx`, `frontend/src/pages/EmployeeDetailPage.test.tsx`
- Modify: `frontend/src/components/BranchAccessCard.tsx` (komentar K-128 → K-135), `frontend/src/components/BranchAccessCard.test.tsx`
- Create: `frontend/src/components/ResetPasswordDialog.tsx`, `frontend/src/pages/UsersPage.test.tsx`

**Interfaces:**
- Consumes: `CredentialMessageDialog`, `suggestPassword`, `suggestUsername`, `UserAccount` (`employeeId`, `employeeName`, `username`), `Employee` (`phone`, `employeeNumber`, `fullName`).
- Produces: `ResetPasswordDialog({ account, employee?, onClose })`. Dialog ini berisi field "Password sementara" (type `text`, terisi `suggestPassword(account.username, employee?.employeeNumber)`). Setelah POST `/api/users/{id}/reset-password` berhasil, isinya berganti menjadi `CredentialMessageDialog` dengan `kind 'reset'`, `name = employee?.fullName ?? account.employeeName ?? account.username`, dan `phone = employee?.phone`.

- [ ] **Step 1: Tulis test yang gagal.**
  - **UsersPage:**
    - Reset password akun `eni@takka.id` milik karyawan No. `006` → field terisi `eni-006-takka`.
    - Setelah simpan, pesan memuat `*Password Baru*`.
    - Membuat akun baru dari menu Akun (pilih karyawan) → pesan versi `created`. Usulan password `{local}-{No}-takka` mengikuti username dan karyawan yang dipilih.
  - **EmployeeDetailPage:**
    - Karyawan tanpa akun menampilkan tombol "Buat akun", bukan tautan "Buat di menu Akun".
    - Klik → dialog terisi `suggestUsername(fullName)` dan `suggestPassword(...)`.
    - Simpan → POST `/api/users` dengan `employeeId`, role `EMPLOYEE`, lalu pesan `created` muncul dengan nomor WA dari `phone`.
    - Karyawan dengan akun menampilkan tombol "Reset password".
    - HR_FINANCE melihat `BranchAccessCard` yang bisa diubah (tidak `readOnly`).
- [ ] **Step 2:** Run `npx vitest run src/pages/UsersPage.test.tsx src/pages/EmployeeDetailPage.test.tsx src/components/BranchAccessCard.test.tsx`. Expected: FAIL.
- [ ] **Step 3:** Implementasikan.
  - **`UsersPage`:**
    - memakai `ResetPasswordDialog`, dengan data karyawan diambil dari query `/api/employees`;
    - form create memakai `derive` untuk usulan password dari username dan No. karyawan yang dipilih;
    - password bertipe `text`;
    - `onSaved` saat create membuka `CredentialMessageDialog`.
  - **`EmployeeDetailPage`:**
    - tombol "Buat akun" membuka `FormDialog` (Username, Password sementara) yang POST ke `/api/users` lalu menampilkan pesan;
    - tombol "Reset password" membuka `ResetPasswordDialog`;
    - `BranchAccessCard readOnly` hanya untuk role selain OWNER/HR_FINANCE;
    - invalidasi query `['/api/users']` setelah buat akun.
- [ ] **Step 4:** Run lagi, lalu jalankan seluruh suite: `npx vitest run`, `npx tsc -b`, `npm run build`. Expected: semua PASS, build OK.
- [ ] **Step 5:** Catat di ledger.

### Task 6: Dokumen

**Files:**
- Modify: `docs/keputusan.md`, `docs/aturan-bisnis.md` (§6.1 dan bagian hak akses cabang absen yang menyebut K-128), `docs/rencana-pengerjaan.md` (bagian "Revisi 2026-10-26"), `docs/pertanyaan-terbuka.md` (entri yang menyebut impor)
- Modify: `docs/superpowers/plans/2026-10-26-profil-dan-impor-karyawan.md` (catatan di atas: Task 2–4 dibatalkan oleh rencana ini)

- [ ] **Step 1:** Ubah `keputusan.md`.
  - **Keputusan baru:**
    - K-133: tambah karyawan sekaligus akun dan pesan WA, termasuk reset password.
    - K-134: tanggal masuk otomatis hari ini menurut zona cabang; form tambah tanpa tanggal masuk/keluar.
    - K-135: F&A boleh tambah/ubah karyawan dan cabang absen, tetapi tetap tidak boleh mengubah Aturan Kerja; ini membatalkan sebagian K-128.
  - **Keputusan lama:**
    - K-130 ditandai "Dibatalkan 2026-10-26, diganti K-133".
    - K-131: password sementara menjadi `<nama>-<ID>-takka`.
    - Bagian narasi "Data karyawan dari client (K-129–K-132)" disesuaikan.
- [ ] **Step 2:** Ubah dokumen lain sesuai daftar file. Tulis dalam Bahasa Indonesia.
- [ ] **Step 3:** Run `grep -rn "employee-imports\|Impor dari Excel" docs --include=*.md`. Expected: hanya ada di entri yang sudah ditandai dibatalkan atau di plan lama yang diberi catatan.
- [ ] **Step 4:** Catat di ledger.
