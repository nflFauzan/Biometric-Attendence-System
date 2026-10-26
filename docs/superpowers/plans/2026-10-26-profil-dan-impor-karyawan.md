# Profil Lengkap dan Impor Karyawan — Rencana Implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Tujuan:**
- Owner mengimpor 28 karyawan dari template Excel client.
- Dalam satu kali simpan, sistem membuat data karyawan lengkap dengan profilnya, sekaligus akun web masing-masing.
- Label role "HR/Finance" diganti menjadi "Finance & Accounting".

**Arsitektur:**
- Impor berjalan dalam dua langkah, tanpa server menyimpan state di antaranya:
  1. `POST /api/employee-imports/preview` menerima file xlsx (multipart) dan tanggal mulai. Server membaca dan merapikan isi file, lalu mengusulkan username. Hasilnya berupa baris-baris beserta errornya. Belum ada yang disimpan.
  2. `POST /api/employee-imports` menerima JSON berisi baris yang sudah diperiksa Owner (username dan role mungkin sudah diubah). Server memvalidasi ulang seluruh baris, lalu menyimpan semuanya dalam **satu transaksi**: semua tersimpan atau tidak ada sama sekali. Audit log ikut di transaksi yang sama.
- Kolom profil baru masuk ke tabel `employees` lewat V17: email, jenis kelamin, tanggal lahir, alamat, catatan. Kolom ini juga bisa diedit di form karyawan biasa.

**Tech Stack:** Spring Boot 4 / Java 21, Flyway, Apache POI (`poi-ooxml`, dependency baru), React + Vite + TypeScript, Vitest.

**Spec:** tidak ada spec terpisah. Sumbernya:
- Jawaban pengguna pada 2026-10-26, yang dicatat di Task 6 sebagai K-129 s.d. K-132.
- Template `docs/Template_Pengisian_Data_Karyawan Takka Steel.xlsx`. File ini berisi data pribadi dan diabaikan git. **Jangan salin isinya ke test atau ke repo.** Test memakai xlsx sintetis yang dibuat di memori.

## Keputusan pengguna (2026-10-26) yang dipakai rencana ini

| # | Keputusan |
|---|---|
| 1 | Tanggal masuk tidak perlu diminta ke client. Semua karyawan hasil impor diberi tanggal masuk = **tanggal mulai pemakaian sistem**, yang dipilih Owner saat impor. Bawaannya tanggal 1 bulan berjalan (Asia/Jakarta). |
| 2 | ID 001 = Owner. ID 003 dan 005 = `HR_FINANCE`. Label tampilan role diganti menjadi **"Finance & Accounting"**. Kode enum `HR_FINANCE` tidak berubah. |
| 3 | Username `<nama>@takka.id` (contoh `dafa@takka.id`). Password awal `<nama>-<ID>` (contoh `harfianti-002`), wajib diganti saat login pertama. |
| 4 | Staf Gudang (Unit "Office") digaji bulanan, dengan kategori `KANTOR`. |
| 5 | Data dirapikan otomatis saat impor. |
| 6 | Tanggal lahir yang janggal tetap diimpor dan bisa diedit nanti. |
| 7 | Semua karyawan di ketiga cabang dibuatkan akun. |

## Global Constraints

- Schema hanya diubah lewat `V17__profil_karyawan.sql`. Semua kolom baru nullable.
- Pesan error untuk pengguna ditulis dalam Bahasa Indonesia dan berformat ProblemDetail (`ApiException`).
- Setiap perubahan data dicatat lewat `AuditService` dalam transaksi yang sama.
- Endpoint baru hanya untuk Owner (`hasRole(OWNER)` di `SecurityConfig`) dan wajib masuk `AccessControlTests`.
- Password tidak pernah ditulis ke audit log atau log aplikasi. Password awal hanya dikembalikan satu kali, di respons simpan impor.
- Aturan password minimal 8 karakter (`UserAccountDtos.MIN_PASSWORD_LENGTH`) tetap berlaku, termasuk untuk impor.
- Pemetaan Unit ke kategori: `Office` → kode `KANTOR`; `Supir` dan `Kenek` → kode `SUPIR_KENEK`. Kategori dicari berdasarkan kode. Jika kodenya tidak ada, baris itu error.
- Cabang dicocokkan berdasarkan **nama** di antara cabang aktif (trim, tidak membedakan huruf besar/kecil).
- PIN mesin = ID karyawan apa adanya (`001`).
- Commit dilakukan pengguna sendiri. Langkah "Commit" di bawah berarti: berhenti, lalu laporkan file yang berubah.
- Test backend dijalankan dari salinan repo di scratchpad. Jangan dari working tree, karena dipakai `spring-boot:run` pengguna.

## Review Focus

1. **Username kembar**, baik di dalam file maupun dengan akun yang sudah ada. Baris yang bentrok ditandai di pratinjau. Saat simpan, server menolak dengan 409 dan tidak ada yang tersimpan. Test: `importRejectsDuplicateUsernameAndSavesNothing` (Task 3).
2. **Nomor karyawan sudah ada di database**, misalnya karena impor diulang. Hasilnya error per baris, bukan 500 dari constraint unik. Test: `previewFlagsExistingEmployeeNumber` (Task 3).
3. **Password awal kurang dari 8 karakter** (`eni-006`, `nur-008`). Pratinjau memberi error pada baris itu dan menyarankan username yang lebih panjang. Test: `previewFlagsShortInitialPassword` (Task 3).
4. **Sel Excel dengan bentuk berbeda**, yang semuanya harus dirapikan dengan benar:
   - ID tersimpan sebagai angka (`1.0`).
   - Nomor HP tersimpan sebagai angka tanpa 0 di depan.
   - Tanggal lahir berupa serial Excel atau sel berformat tanggal.

   Test: `parsesNumericCellsAsText`, `normalizesPhoneAndBirthDate` (Task 2).
5. **File bukan xlsx, rusak, atau terlalu besar.** Hasilnya 400 dengan pesan Bahasa Indonesia, bukan 500. Test: `previewRejectsNonXlsx` (Task 3). Batas ukuran mengikuti konfigurasi multipart yang sudah ada.

---

### Task 1: Kolom profil karyawan (V17 + API + form)

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__profil_karyawan.sql`
- Create: `backend/src/main/java/com/biometricattendance/employee/Gender.java`
- Modify: `backend/src/main/java/com/biometricattendance/employee/Employee.java`, `EmployeeDtos.java`, `EmployeeService.java` (salin field baru di create dan update)
- Modify: `frontend/src/lib/types.ts`, `frontend/src/pages/EmployeesPage.tsx`, `frontend/src/pages/EmployeeDetailPage.tsx`, `frontend/src/pages/ProfilePage.tsx`
- Test: `backend/src/test/java/com/biometricattendance/masterdata/MasterDataTests.java`, `frontend/src/pages/EmployeesPage.test.tsx`, `frontend/src/pages/EmployeeDetailPage.test.tsx`

**Interfaces:**
- Produces:
  - `enum Gender { LAKI_LAKI, PEREMPUAN, TIDAK_DISEBUTKAN }`
  - Field baru di `Employee`: `String email`, `Gender gender`, `LocalDate birthDate`, `String address`, `String notes`. Kelimanya ditambahkan di akhir `EmployeeRequest` dan `EmployeeResponse` dengan urutan yang sama.
- TypeScript:
  - `Employee` mendapat `email: string | null`, `gender: 'LAKI_LAKI' | 'PEREMPUAN' | 'TIDAK_DISEBUTKAN' | null`, `birthDate: string | null`, `address: string | null`, `notes: string | null`.
  - Label di `types.ts`: `GENDER_LABELS = { LAKI_LAKI: 'Laki-laki', PEREMPUAN: 'Perempuan', TIDAK_DISEBUTKAN: 'Tidak ingin menyebutkan' }`.

- [ ] **Step 1: Tulis test yang gagal di `MasterDataTests`.**
  - `employeeProfileFieldsRoundTripAndAreAudited`: Owner membuat karyawan dengan `email "a@contoh.id"`, `gender "PEREMPUAN"`, `birthDate "1990-05-01"`, `address "Jl. Contoh 1"`, `notes "catatan"`. Respons GET memuat kelima nilai itu, dan `afterData` audit CREATE memuat `"Jl. Contoh 1"`.
  - `employeeEmailMustBeValid`: `email "bukan-email"` → 400.
- [ ] **Step 2: Jalankan test dan pastikan gagal.** Perintah: `./mvnw test -Dtest=MasterDataTests`. Harapan: FAIL (field tidak dikenal atau null).
- [ ] **Step 3: Implementasi.**
  - Migration: `email VARCHAR(150)`, `gender VARCHAR(20)` dengan `CHECK (gender IN ('LAKI_LAKI','PEREMPUAN','TIDAK_DISEBUTKAN'))`, `birth_date DATE`, `address VARCHAR(500)`, `notes VARCHAR(500)`.
  - Validasi DTO:
    - email: `@Email(message = "Format email tidak valid") @Size(max = 150)`
    - alamat dan catatan: `@Size(max = 500)`
    - tanggal lahir: `@Past(message = "Tanggal lahir harus sebelum hari ini")`
  - Entity memakai `@Enumerated(EnumType.STRING)`. String kosong disimpan sebagai null, sama seperti `phone` sekarang.
- [ ] **Step 4: Jalankan test dan pastikan lulus.** Jalankan juga `EmployeeBranchAccessTests`, karena bentuk snapshot audit berubah.
- [ ] **Step 5: Frontend.**
  - Form `EmployeesPage`: tambah Email, Jenis kelamin (`type: 'select'` dari `GENDER_LABELS`), Tanggal lahir (`date`), Alamat, Catatan. Perbarui `emptyValues`, `toValues`, dan `toBody` (pakai `emptyToNull`).
  - `EmployeeDetailPage` dan `ProfilePage`: tampilkan kelima nilai; isi "—" bila kosong.
  - Test: `EmployeesPage.test.tsx` mengirim `gender: 'PEREMPUAN'` di body POST. `EmployeeDetailPage.test.tsx` menampilkan "Perempuan" dan alamatnya.
- [ ] **Step 6: Verifikasi frontend.** Jalankan `npm test -- EmployeesPage EmployeeDetailPage ProfilePage` dan `npx tsc -b`. Harapan: PASS.
- [ ] **Step 7: Commit (pengguna).**

### Task 2: Parser template Excel

**Files:**
- Modify: `backend/pom.xml` (tambah `org.apache.poi:poi-ooxml:5.4.1`)
- Create: `backend/src/main/java/com/biometricattendance/employee/EmployeeImportParser.java`
- Test: `backend/src/test/java/com/biometricattendance/employee/EmployeeImportParserTests.java`. Ini unit test murni tanpa Spring; xlsx dibuat di memori dengan `XSSFWorkbook`.

**Interfaces:**
- Produces:
  - `record ParsedRow(int rowNumber, String employeeNumber, String fullName, String unit, String position, String branchName, String phone, String email, Gender gender, LocalDate birthDate, String address, String notes, String accountStatus, List<String> errors)`
  - `static List<ParsedRow> EmployeeImportParser.parse(InputStream xlsx)`. Melempar `ApiException.badRequest("File bukan Excel .xlsx yang valid")` bila file tidak bisa dibuka.
  - `static String suggestUsername(String fullName)`: mengembalikan bagian sebelum `@takka.id` saja.
  - `static final String USERNAME_DOMAIN = "@takka.id"`

**Aturan pembacaan:**

*Sheet dan header*
- Pakai sheet `"Data Karyawan"`. Bila tidak ada, pakai sheet pertama.
- Header ada di baris 1 dan dicocokkan dari **teksnya**, bukan posisi kolom. Teks header di-trim dan akhiran ` [OPSIONAL]` dibuang lebih dulu.
- Daftar header: `ID Karyawan`, `Nama Lengkap`, `Unit/Departemen`, `Jabatan`, `Lokasi/Cabang Kerja`, `No HP`, `Email`, `Jenis Kelamin`, `Tanggal Lahir`, `Alamat Domisili`, `Status Akun`, `Catatan`.
- Header wajib: ID, Nama, Unit, Cabang, Status Akun. Bila salah satu tidak ditemukan → `badRequest("Kolom '<nama>' tidak ditemukan di baris judul")`.

*Baris*
- Baris yang semua selnya kosong dilewati.
- Maksimal 500 baris data. Lebih dari itu → `badRequest`.

*Nilai sel*
- **Teks:** trim, lalu spasi berderet diringkas jadi satu (`" M Rochmat"` → `"M Rochmat"`).
- **Sel angka:** dibaca sebagai teks tanpa `.0`.
- **ID:** bila berupa angka, diberi nol di depan sampai 3 digit (`1` → `"001"`).
- **No HP:** buang semua karakter selain digit. Awalan `62` diganti `0`. Awalan `8` diberi `0` di depan (`81398137148` → `081398137148`).
- **Tanggal lahir:**
  - sel bertipe tanggal → `LocalDate`
  - sel angka → serial Excel (epoch `1899-12-30`)
  - teks `dd/MM/yyyy` atau `yyyy-MM-dd` diterima
  - bentuk lain → error baris `"Tanggal lahir tidak terbaca"`
- **Jenis kelamin:** `Laki-laki` / `Perempuan` / `Tidak ingin menyebutkan` → enum. Kosong → null. Nilai lain → error baris.
- **Alamat:** baris pertama menjadi alamat. Baris sesudahnya (setelah newline) dipindahkan ke depan `notes`, dipisah `"; "` bila notes sudah berisi.
- **Unit** harus salah satu dari `Office` / `Supir` / `Kenek`. **Status Akun** harus salah satu dari `Aktif` / `Nonaktif` / `Belum dibuat`. Nilai lain → error baris.

*Usulan username (`suggestUsername`)*
1. Ubah ke huruf kecil.
2. Lewati awalan `muhammad`, `muhamad`, `mohammad`, `mohamad`, `moh`, `muh`, `m`, lalu ambil kata pertama sesudahnya.
3. Buang karakter selain `a-z0-9`.

Contoh: `"Muhammad Dafa Fakhrika"` → `dafa`; `"M Rochmat"` → `rochmat`; `"Harfianti Nur Wafa"` → `harfianti`.

- [ ] **Step 1: Tulis test yang gagal**, dengan data sintetis:
  - `readsHeadersByNameNotPosition`: urutan kolom diacak, nilai tetap terbaca.
  - `parsesNumericCellsAsText`: ID numerik `1` → `"001"`.
  - `normalizesPhoneAndBirthDate`: `81234567890` → `"081234567890"`; `"+62 812-3456"` → `"08123456"`; serial `32888` → `1990-01-15`.
  - `trimsAndCollapsesSpaces`
  - `movesExtraAddressLinesToNotes`: `"Jl. A\nbeda dengan KTP"` → address `"Jl. A"`, notes `"beda dengan KTP"`.
  - `flagsUnknownUnitAndGender`
  - `suggestsUsernameSkippingMuhammadPrefix`: ketiga contoh di atas.
  - `rejectsMissingRequiredHeader`
  - `rejectsNonXlsxBytes`
- [ ] **Step 2: Jalankan test dan pastikan gagal.** Perintah: `./mvnw test -Dtest=EmployeeImportParserTests`. Harapan: FAIL (kelas belum ada).
- [ ] **Step 3: Implementasi** sesuai aturan di atas. Pakai `DataFormatter` untuk membaca teks dan `DateUtil.isCellDateFormatted` untuk membedakan sel tanggal dari sel angka.
- [ ] **Step 4: Jalankan test dan pastikan PASS.**
- [ ] **Step 5: Commit (pengguna).**

### Task 3: Service dan endpoint impor (pratinjau + simpan)

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/employee/EmployeeImportDtos.java`, `EmployeeImportService.java`, `EmployeeImportController.java`
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java`. Tambah aturan `/api/employee-imports/**` → `hasRole(OWNER)`, diletakkan sebelum `/api/employees/**`.
- Modify: `backend/src/main/java/com/biometricattendance/account/UserAccountDtos.java`. Regex username menjadi `[A-Za-z0-9._@-]{3,50}`, dengan pesan `"Username 3-50 karakter: huruf, angka, titik, @, _ atau -"`.
- Test: `backend/src/test/java/com/biometricattendance/employee/EmployeeImportTests.java` (extends `IntegrationTest`) dan `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java`

**Interfaces:**
- Consumes: `EmployeeImportParser.parse`, `suggestUsername`, `USERNAME_DOMAIN` (Task 2); field profil `Employee` (Task 1).
- Produces (JSON):
  - `PreviewRow(int rowNumber, String employeeNumber, String fullName, String unit, Long categoryId, String categoryName, String position, Long branchId, String branchName, String phone, String email, Gender gender, LocalDate birthDate, String address, String notes, String accountStatus, String username, Role role, List<String> errors)`
  - `PreviewResponse(LocalDate startDate, List<PreviewRow> rows, int errorCount)`
  - `CommitRequest(@NotNull LocalDate startDate, @NotEmpty @Size(max = 500) @Valid List<PreviewRow> rows)`
  - `CreatedAccount(String employeeNumber, String fullName, String username, Role role, String initialPassword)`
  - `CommitResponse(int employeesCreated, int accountsCreated, List<CreatedAccount> accounts)`
- Endpoint:
  - `POST /api/employee-imports/preview`: multipart `file` dan `startDate` (opsional; bawaan tanggal 1 bulan berjalan di `Asia/Jakarta`).
  - `POST /api/employee-imports`: JSON `CommitRequest`.

**Aturan pratinjau**
- Setiap baris diberi `username = suggestUsername(fullName) + USERNAME_DOMAIN` dan `role = EMPLOYEE`.
- Role bawaan tidak membaca ID tertentu. Owner sendiri yang mengubah role 001/003/005 di layar.

**Validasi per baris**
- Pratinjau dan simpan memakai fungsi yang sama: `List<String> validate(PreviewRow row, ...)`.
- Kondisi yang menjadi error baris:
  - cabang tidak ditemukan
  - kategori untuk Unit tersebut tidak ada
  - nomor karyawan sudah dipakai di database, atau kembar di dalam file
  - PIN (`employeeNumber`) sudah dipakai
  - username tidak cocok dengan regex, sudah dipakai, kembar di dalam file, atau tidak berakhiran `@takka.id`
  - password awal kurang dari 8 karakter → `"Password awal <x> kurang dari 8 karakter; perpanjang username"`
  - error yang dibawa dari parser
- `initialPassword(row) = localPart(username) + "-" + employeeNumber`

**Aturan simpan**
- Bila ada satu error saja: lempar `ApiException.conflict("Ada <n> baris bermasalah; periksa ulang di pratinjau")`, dengan `rows` beserta errornya dimasukkan ke properti ProblemDetail. **Tidak ada yang tersimpan.**
- Bila semua bersih, simpan dalam satu `@Transactional`. Untuk setiap baris:
  - Buat `Employee` dengan `joinDate = startDate`, `active = (status == "Aktif")`, `devicePin = employeeNumber`.
  - Buat `UserAccount` dengan `mustChangePassword = true` dan `active` mengikuti status. Baris berstatus `Belum dibuat` tidak dibuatkan akun.
  - Catat audit `CREATE` untuk karyawan dan akun. Snapshot audit tidak memuat password.
- Di akhir, catat satu audit `IMPORT` dengan entity `EMPLOYEE_IMPORT`, berisi jumlah dan nomor-nomor karyawan yang diimpor.
- Impor boleh menetapkan role `OWNER`, karena endpoint ini sendiri hanya untuk Owner.

- [ ] **Step 1: Tulis test yang gagal di `EmployeeImportTests`.** Buat helper `xlsx(String[]... rows)` di kelas test untuk menyusun xlsx sintetis. Cabang dibuat dengan nama `unique("Cab")`.
  - `previewNormalizesAndSuggestsUsername`: pratinjau mengembalikan `username "dafa@takka.id"`, `categoryName "Kantor"`, dan `errors` kosong.
  - `previewFlagsExistingEmployeeNumber`
  - `previewFlagsShortInitialPassword`: nama `"Eni Contoh"`, ID `006` → error memuat `"kurang dari 8 karakter"`.
  - `previewRejectsNonXlsx` → 400.
  - `commitCreatesEmployeesAndAccountsAtomically`: 3 baris; satu ber-role `HR_FINANCE`, satu berstatus `Belum dibuat`. Harapan:
    - `employeesCreated 3`, `accountsCreated 2`
    - `joinDate == startDate`
    - login dengan `harfianti-002` berhasil, dan `mustChangePassword` bernilai true
    - audit log tidak memuat string password
  - `importRejectsDuplicateUsernameAndSavesNothing`: dua baris ber-username sama → 409, dan jumlah karyawan di database tidak berubah.
  - `commitRevalidatesEditedRows`: body diubah memakai cabang yang tidak dikenal → 409.
- [ ] **Step 2: Tambah kedua endpoint ke `AccessControlTests` sebagai Owner-only.** HR dan EMPLOYEE → 403; anonim → 401. Ikuti pola tabel `ownerOnly…` yang sudah ada.
- [ ] **Step 3: Jalankan test dan pastikan gagal.** Perintah: `./mvnw test -Dtest=EmployeeImportTests+AccessControlTests`. Harapan: FAIL.
- [ ] **Step 4: Implementasi** controller, service, DTO, aturan security, dan regex username. Tambah juga `usernameMayContainAt` di `UserAccountTests`: `"budi@takka.id"` diterima.
- [ ] **Step 5: Jalankan test yang sama, ditambah `UserAccountTests`, `AuthFlowTests`, dan `MasterDataTests`.** Harapan: PASS.
- [ ] **Step 6: Commit (pengguna).**

### Task 4: Halaman Impor Karyawan (frontend)

**Files:**
- Create: `frontend/src/pages/EmployeeImportPage.tsx`, `frontend/src/pages/EmployeeImportPage.test.tsx`
- Modify:
  - `frontend/src/App.tsx`: rute `/karyawan/impor`, hanya untuk Owner, mengikuti pola rute Owner yang sudah ada.
  - `frontend/src/pages/EmployeesPage.tsx`: tombol "Impor dari Excel" untuk Owner.
  - `frontend/src/lib/types.ts`: tipe `ImportPreviewRow`, `ImportPreview`, dan `ImportCommitResult`, sesuai DTO Task 3.
  - `frontend/src/lib/api.ts`: bila belum ada helper upload multipart, tambah `apiUpload(path, formData)` yang tetap membawa CSRF.

**Interfaces:**
- Consumes: kedua endpoint dari Task 3.

**Alur layar:**
1. **Pilih file dan tanggal.** Owner memilih file `.xlsx` dan mengisi **Tanggal mulai dihitung** (bawaan tanggal 1 bulan ini). Keterangan di bawahnya: "Tanggal masuk semua karyawan impor diisi tanggal ini."
2. **Pratinjau.**
   - Tabel berkolom: Baris, ID, Nama, Cabang, Kategori, Jabatan, Username (input yang bisa diedit), Role (select: Karyawan / Finance & Accounting / Owner), Status, Masalah. Baris yang bermasalah diberi warna.
   - Tombol **"Simpan N karyawan"** nonaktif selama ada error yang bisa dicek di sisi klien (username kosong, atau tanpa `@takka.id`).
   - Error dari server (409) ditampilkan per baris.
3. **Hasil.**
   - Tabel berkolom: ID, Nama, Username, Password awal, Role.
   - Tombol "Unduh CSV". File dibuat di browser dan tidak dikirim ke server.
   - Peringatan: "Password ini hanya ditampilkan sekali. Bagikan ke masing-masing karyawan; mereka wajib menggantinya saat login pertama."

- [ ] **Step 1: Tulis test yang gagal:**
  - `uploadsFileAndShowsPreview`: dengan fetch mock untuk pratinjau, nama dan username tampil.
  - `editedUsernameAndRoleAreSentOnCommit`: body POST memuat `role: 'HR_FINANCE'` dan username yang sudah diedit.
  - `showsRowErrorsFromServer409`
  - `showsInitialPasswordsOnceAfterCommit`
  - `importButtonOnlyForOwner` (di `EmployeesPage.test.tsx`)
- [ ] **Step 2: Jalankan test dan pastikan gagal.** Perintah: `npm test -- EmployeeImportPage`. Harapan: FAIL.
- [ ] **Step 3: Implementasi.**
  - Pakai komponen shadcn yang sudah ada (`Table`, `Input`, `Select`, `Button`, `Badge`) dan ikuti `docs/design-system.md`.
  - Layout harus berfungsi di lebar 375 px; tabel boleh digulir ke samping.
- [ ] **Step 4: Verifikasi.** Jalankan `npm test`, `npx tsc -b`, dan `npm run build`. Harapan: PASS.
- [ ] **Step 5: Commit (pengguna).**

### Task 5: Label role "Finance & Accounting"

**Files:**
- Modify (hanya teks yang dilihat pengguna; komentar kode boleh tetap):
  - Frontend:
    - `frontend/src/lib/types.ts:5` (`HR_FINANCE: 'Finance & Accounting'`)
    - `frontend/src/pages/LoginPage.tsx:65`, `LeaveTypesPage.tsx:36,55`, `UsersPage.tsx:29`, `VerifyLetterPage.tsx:93`
    - `frontend/src/components/LeaveRequestForm.tsx:132`
  - Backend:
    - `backend/src/main/java/com/biometricattendance/account/UserAccountService.java:135`
    - `backend/src/main/java/com/biometricattendance/leave/LeaveLetterService.java:139,232,248,269` (teks pada PDF surat)
    - `backend/src/main/java/com/biometricattendance/leave/LeaveService.java:375,506,707,732,788`
  - Teks lain yang dilihat pengguna dan menyebut "HR" sebagai tahap. Contoh: badge "HR lalu Owner" → "Finance & Accounting lalu Owner". Cari dengan `grep -rn "HR\b" frontend/src --include=*.tsx`, lalu periksa hasilnya satu per satu.
- Test: semua test yang meng-assert teks lama. Cari dengan `grep -rln "HR/Finance" frontend/src backend/src/test`.

- [ ] **Step 1: Ubah assertion test ke teks baru** (`Finance & Accounting`). Jalankan dan pastikan gagal.
- [ ] **Step 2: Ganti teksnya.** Label baru lebih panjang, jadi periksa kotak tanda tangan di PDF tidak terpotong: jalankan `LeaveLetterTests`, dan lihat PDF hasilnya bila test menyimpan file.
- [ ] **Step 3: Verifikasi.** Jalankan `npm test` dan `./mvnw test -Dtest='Leave*Tests+UserAccountTests'`. Harapan: PASS.
- [ ] **Step 4: Commit (pengguna).**

### Task 6: Dokumen dan verifikasi akhir

**Files:**
- Modify:
  - `docs/keputusan.md`:
    - K-129: profil karyawan dan V17
    - K-130: impor karyawan dari template client (dua langkah, atomik, tanggal masuk = tanggal mulai pemakaian sistem)
    - K-131: format username `<nama>@takka.id` dan password awal `<nama>-<ID>`
    - K-132: label role "Finance & Accounting"
  - `docs/pertanyaan-terbuka.md`:
    - Q-041 terjawab (28 karyawan)
    - Q-042 terjawab sebagian (Unit = kategori; nama panggilan menjadi bagian username)
    - Q-045 terjawab sebagian (semua karyawan dibuatkan akun)
  - `docs/aturan-bisnis.md`: catatan bahwa tanggal masuk karyawan hasil impor = tanggal mulai pemakaian sistem.
  - `docs/rencana-pengerjaan.md`: entri revisi 2026-10-26.

- [ ] **Step 1: Perbarui dokumen di atas.** Tulis dalam Bahasa Indonesia, dengan gaya mengikuti entri K-128.
- [ ] **Step 2: Jalankan suite penuh.**
  - Backend: `./mvnw test` dari salinan di scratchpad.
  - Frontend: `npm test`, `npx tsc -b`, `npm run build`.
  - Laporkan angka sebenarnya.
- [ ] **Step 3: Uji manual dengan file asli client** lewat UI lokal, **tanpa menyalin filenya ke repo**:
  - pratinjau 28 baris
  - ubah role 001/003/005
  - simpan ke database dev
  - laporkan ke pengguna baris yang masih bermasalah
- [ ] **Step 4: Commit (pengguna).**
