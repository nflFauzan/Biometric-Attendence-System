# Laporan Penggajian Aktual (AS-1 s/d AS-7) & Buku Kasbon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Membangun modul buku kasbon/piutang karyawan berbasis ledger dan menghasilkan laporan ekspor Excel/PDF yang 100% identik dengan format fisik klien (AS-1 sampai AS-7), mencakup rekap upah & lembur mingguan supir/kenek (Sabtu–Jumat), rekap gaji bulanan staf kantor (kop Takka Steel), dan pengajuan kas per toko.

**Architecture:** Modul backend baru `loan` mengelola transaksi pinjaman dan cicilan kasbon. Modul `report` diperluas dengan dua tipe laporan baru (`upah-mingguan` dan `gaji-bulanan`) yang mengagregasi kehadiran fisik, aturan kerja tarif rupiah, dan cicilan kasbon aktif ke dalam berkas multi-sheet Apache POI (.xlsx) dan OpenPDF (.pdf). Frontend menyediakan halaman kelola kasbon baru (`/kasbon`) dan memperbarui halaman laporan (`/laporan`) dengan filter cepat periode Sabtu–Jumat serta tombol konfirmasi tutup buku idempoten.

**Tech Stack:** Spring Boot 4, Java 21, Spring Data JPA, Flyway migration, PostgreSQL 17, Apache POI (XSSF), OpenPDF, React 19, TypeScript, Tailwind CSS, Lucide icons, Vitest.

**Spec:** `docs/superpowers/specs/2026-10-30-laporan-aktual-dan-kasbon-design.md`

## Global Constraints

- Uang wajib menggunakan `BigDecimal` di backend dan integer format IDR di frontend; dilarang menggunakan floating-point (`float`/`double`) untuk perhitungan nominal upah/kasbon.
- Hak akses: Hanya role `OWNER` dan `HR_FINANCE` yang diizinkan mengakses data kasbon dan laporan penggajian.
- Scan asli dari mesin biometrik tidak boleh diubah atau dihapus; koreksi absensi tersimpan terpisah.
- Tiga laporan presensi fisik yang sudah ada (`rekap-absensi`, `rekap-lembur`, `detail-absensi`) tetap dipertahankan tanpa perubahan regresi.
- Pratinjau dan ekspor laporan bersifat read-only (idempoten); pemotongan saldo kasbon permanen hanya dieksekusi melalui aksi eksplisit tutup buku.

## Review Focus

1. Upah kotor lebih kecil dari cicilan kasbon: sistem membatasi potongan maksimal sebesar upah kotor agar sisa upah tidak negatif (`Sisa Upah >= 0`), sisa kewajiban tetap menjadi saldo aktif.
2. Pengunduhan berulang laporan: berkas Excel/PDF dapat diunduh berulang kali tanpa memotong saldo kasbon lebih dari sekali.
3. Karyawan hadir di hari libur/Minggu: supir/kenek yang masuk hari Minggu tetap dihitung upah harian dan lembur flat (sesuai dokumen AS-1/AS-2).
4. Staf tidak hadir atau izin: status izin atau alpa mengurangi uang harian (`Onsite = Total Days * Wages`), tetapi Gaji Pokok tetap dibayar penuh (sesuai AS-5).
5. Pembagian cabang AS-1: upah dikelompokkan per cabang penempatan karyawan (`Takka 1`, `Takka 2`, `Takka Cilong`) dan baris "Cash" merangkum total uang tunai yang perlu disiapkan.

---

### Task 1: Skema Database Kasbon & Entity JPA

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__kasbon_dan_pinjaman.sql`
- Create: `backend/src/main/java/com/biometricattendance/loan/EmployeeLoan.java`
- Create: `backend/src/main/java/com/biometricattendance/loan/LoanRepayment.java`
- Create: `backend/src/main/java/com/biometricattendance/loan/EmployeeLoanRepository.java`
- Create: `backend/src/main/java/com/biometricattendance/loan/LoanRepaymentRepository.java`
- Test: `backend/src/test/java/com/biometricattendance/loan/LoanRepositoryTests.java`

**Interfaces:**
- Consumes: `Employee` entity dari `com.biometricattendance.employee`
- Produces: `EmployeeLoanRepository` dan `LoanRepaymentRepository` untuk digunakan `LoanService`

- [ ] **Step 1: Tulis tes repository kasbon yang memverifikasi relasi dan status**

```java
@Test
void testCreateLoanAndRepayment() {
    Employee emp = createTestEmployee();
    EmployeeLoan loan = new EmployeeLoan(emp, "KB-202610-001", LocalDate.of(2026, 10, 1),
            new BigDecimal("1000000.00"), new BigDecimal("100000.00"), new BigDecimal("1000000.00"), "AKTIF", "Pinjaman operasional");
    loanRepo.save(loan);
    assertNotNull(loan.getId());
    
    LoanRepayment rep = new LoanRepayment(loan, LocalDate.of(2026, 10, 8), new BigDecimal("100000.00"), "Minggu 1 Okt 2026", "UPAH_MINGGUAN", "Cicilan 1");
    repaymentRepo.save(rep);
    assertNotNull(rep.getId());
}
```

- [ ] **Step 2: Jalankan tes untuk memastikan gagal sebelum kode dibuat**

Run: `cd backend; ./mvnw test -Dtest=LoanRepositoryTests`  
Expected: FAIL dengan class/file not found

- [ ] **Step 3: Tulis migrasi V19 dan implementasi entitas JPA serta repositori**

Buat migrasi `V19__kasbon_dan_pinjaman.sql` dengan constraint foreign key ke `employees` dan unique check `loan_number`. Implementasikan `EmployeeLoan` dan `LoanRepayment` dengan audit timestamp.

- [ ] **Step 4: Jalankan tes untuk memverifikasi berhasil**

Run: `cd backend; ./mvnw test -Dtest=LoanRepositoryTests`  
Expected: PASS

- [ ] **Step 5: Commit Task 1**

```bash
git add backend/src/main/resources/db/migration/V19__kasbon_dan_pinjaman.sql backend/src/main/java/com/biometricattendance/loan/ backend/src/test/java/com/biometricattendance/loan/
git commit -m "feat(loan): tambahkan skema database dan entitas JPA buku kasbon karyawan"
```

---

### Task 2: Service & REST API Kasbon Karyawan

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/loan/LoanDtos.java`
- Create: `backend/src/main/java/com/biometricattendance/loan/LoanService.java`
- Create: `backend/src/main/java/com/biometricattendance/loan/LoanController.java`
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java`
- Test: `backend/src/test/java/com/biometricattendance/loan/LoanServiceTests.java`

**Interfaces:**
- Consumes: `EmployeeLoanRepository`, `LoanRepaymentRepository`, `AuditService`
- Produces: `LoanService.getActiveInstallments(List<Long> employeeIds, LocalDate date)` dan API `/api/loans`

- [ ] **Step 1: Tulis unit test untuk logika pemotongan cicilan dan batasan 1 pinjaman aktif**

```java
@Test
void testDeductInstallmentUpdatesRemainingAndMarksLunas() {
    // Pinjaman Rp 150.000 dengan cicilan Rp 100.000
    // Periode 1 potong Rp 100.000 (sisa Rp 50.000, masih AKTIF)
    // Periode 2 potong Rp 50.000 (sisa Rp 0, status berubah LUNAS)
}
```

- [ ] **Step 2: Jalankan tes untuk memverifikasi gagal**

Run: `cd backend; ./mvnw test -Dtest=LoanServiceTests`  
Expected: FAIL

- [ ] **Step 3: Implementasikan DTO, LoanService, LoanController, dan proteksi URL SecurityConfig**

Daftarkan endpoint `/api/loans/**` dengan hak akses `hasAnyRole('OWNER', 'HR_FINANCE')`. Tambahkan method `applyPeriodDeductions` yang idempoten.

- [ ] **Step 4: Jalankan tes untuk memverifikasi lulus**

Run: `cd backend; ./mvnw test -Dtest=LoanServiceTests`  
Expected: PASS

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/src/main/java/com/biometricattendance/loan/ backend/src/main/java/com/biometricattendance/config/SecurityConfig.java backend/src/test/java/com/biometricattendance/loan/
git commit -m "feat(loan): implementasikan business logic kasbon dan REST API terproteksi"
```

---

### Task 3: Backend Report DTO & Service untuk Upah Mingguan & Gaji Bulanan

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/report/ReportType.java`
- Modify: `backend/src/main/java/com/biometricattendance/report/ReportDtos.java`
- Modify: `backend/src/main/java/com/biometricattendance/report/ReportService.java`
- Test: `backend/src/test/java/com/biometricattendance/report/ReportServiceFinancialTests.java`

**Interfaces:**
- Consumes: `LoanService`, `WorkRuleRepository`, `AttendanceService`
- Produces: `ReportModel` yang diperkaya kolom: `dailyWage`, `grossWage`, `overtimeWage`, `loanDeduction`, `netWage`, `baseSalary`, `onsiteWage`

- [ ] **Step 1: Tulis unit test perhitungan formula AS-1 (Upah Mingguan) dan AS-5 (Gaji Bulanan)**

```java
@Test
void testWeeklyWageCalculationMatchesAS1() {
    // Supir 1: 7 hari * 100.000 = 700.000 + Lembur 16 jam * 14.000 = 224.000 -> Total 924.000
}

@Test
void testMonthlySalaryCalculationMatchesAS5() {
    // Staf 2: Onsite (26 * 40.000 = 1.040.000) + Pokok 3.000.000 + Lembur 20j * 14.000 (280.000) = 4.320.000
}
```

- [ ] **Step 2: Jalankan tes untuk memverifikasi gagal**

Run: `cd backend; ./mvnw test -Dtest=ReportServiceFinancialTests`  
Expected: FAIL

- [ ] **Step 3: Implementasikan penambahan tipe laporan dan agregasi nilai finansial di ReportService**

Tambahkan `UPAH_MINGGUAN` dan `GAJI_BULANAN` ke `ReportType`. Lengkapi `ReportDtos` dengan field rupiah dan agregasi per cabang Takka 1, Takka 2, Takka Cilong, dan Cash.

- [ ] **Step 4: Jalankan tes untuk memverifikasi lulus**

Run: `cd backend; ./mvnw test -Dtest=ReportServiceFinancialTests`  
Expected: PASS

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/src/main/java/com/biometricattendance/report/ backend/src/test/java/com/biometricattendance/report/
git commit -m "feat(report): tambahkan tipe upah mingguan dan gaji bulanan dengan kalkulasi finansial"
```

---

### Task 4: Excel & PDF Writers Multi-Sheet Identik Dokumen Klien (AS-1 s/d AS-7)

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/report/ExcelReportWriter.java`
- Modify: `backend/src/main/java/com/biometricattendance/report/PdfReportWriter.java`
- Test: `backend/src/test/java/com/biometricattendance/report/ReportExportWritersTests.java`

**Interfaces:**
- Consumes: `ReportModel` dari Task 3
- Produces: `byte[] write(ReportModel model)` menghasilkan berkas `.xlsx` dan `.pdf` multi-sheet

- [ ] **Step 1: Tulis tes pembentukan workbook Excel dan PDF dengan lembar pendukung**

```java
@Test
void testWeeklyWageExcelContainsAS1AndAS2Sheets() {
    byte[] xlsx = excelWriter.write(weeklyWageModel);
    XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx));
    assertNotNull(wb.getSheet("Rekap Upah"));
    assertNotNull(wb.getSheet("Rekap Lemburan"));
}
```

- [ ] **Step 2: Jalankan tes untuk memverifikasi gagal**

Run: `cd backend; ./mvnw test -Dtest=ReportExportWritersTests`  
Expected: FAIL

- [ ] **Step 3: Implementasikan layout Apache POI dan OpenPDF untuk AS-1 s/d AS-7**

Tulis renderer `weeklyWageSheet`, `weeklyOvertimeSheet`, `monthlySalarySheet`, `monthlyOvertimeSheet`, dan `storeSubmissionSheet`. Lengkapi dengan kop TAKKA STEEL, tabel ringkasan tunai cabang, dan kolom tanda tangan.

- [ ] **Step 4: Jalankan tes untuk memverifikasi lulus**

Run: `cd backend; ./mvnw test -Dtest=ReportExportWritersTests`  
Expected: PASS

- [ ] **Step 5: Commit Task 4**

```bash
git add backend/src/main/java/com/biometricattendance/report/ backend/src/test/java/com/biometricattendance/report/
git commit -m "feat(report): implementasikan ekspor Excel multi-sheet dan PDF berformat AS-1 sampai AS-7"
```

---

### Task 5: Frontend Halaman Kasbon / Piutang Karyawan (`/kasbon`)

**Files:**
- Create: `frontend/src/pages/LoansPage.tsx`
- Modify: `frontend/src/navigation.ts`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/pages/LoansPage.test.tsx`

**Interfaces:**
- Consumes: `/api/loans` API dari backend Task 2
- Produces: Komponen `<LoansPage />` terdaftar di rute `/kasbon`

- [ ] **Step 1: Tulis frontend unit test untuk halaman Kasbon (render daftar, dialog tambah)**

```tsx
it('menampilkan daftar kasbon karyawan dan sisa pinjaman', async () => {
    renderWithProviders(<LoansPage />);
    expect(await screen.findByText('KB-202610-001')).toBeInTheDocument();
});
```

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal**

Run: `cd frontend; npx vitest run src/pages/LoansPage.test.tsx --pool=threads`  
Expected: FAIL

- [ ] **Step 3: Implementasikan `LoansPage.tsx` dan daftarkan ke navigation & routes**

Gunakan styling modern (Tailwind + shadcn UI), tabel daftar kasbon, status badge (`AKTIF`, `LUNAS`), dan dialog form pinjaman baru.

- [ ] **Step 4: Jalankan test untuk memverifikasi lulus**

Run: `cd frontend; npx vitest run src/pages/LoansPage.test.tsx src/navigation.test.ts --pool=threads`  
Expected: PASS

- [ ] **Step 5: Commit Task 5**

```bash
git add frontend/src/pages/LoansPage.tsx frontend/src/pages/LoansPage.test.tsx frontend/src/navigation.ts frontend/src/App.tsx
git commit -m "feat(ui): tambahkan halaman manajemen kasbon dan rute navigasi operasional"
```

---

### Task 6: Frontend Halaman Laporan (Filter Sabtu–Jumat, Kolom Rupiah, Tombol Tutup Buku)

**Files:**
- Modify: `frontend/src/pages/ReportsPage.tsx`
- Modify: `frontend/src/lib/types.ts`
- Test: `frontend/src/pages/ReportsPage.test.tsx`

**Interfaces:**
- Consumes: Tipe laporan `upah-mingguan` dan `gaji-bulanan`, endpoint `/api/loans/apply-period-deductions`
- Produces: Pratinjau tabel upah rupiah di web, tombol shortcut periode, tombol tutup buku

- [ ] **Step 1: Tulis unit test untuk pratinjau tabel upah mingguan dan bulanan**

```tsx
it('menampilkan kolom sisa upah rupiah pada laporan upah mingguan', async () => {
    renderWithProviders(<ReportsPage />);
    // Pilih slug upah-mingguan
    expect(await screen.findByText('Sisa Upah (Rp)')).toBeInTheDocument();
});
```

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal**

Run: `cd frontend; npx vitest run src/pages/ReportsPage.test.tsx --pool=threads`  
Expected: FAIL

- [ ] **Step 3: Implementasikan ekstensi ReportsPage dengan shortcut periode Sabtu–Jumat dan tombol aksi Tutup Buku**

Tambahkan tombol filter cepat *"Sabtu–Jumat Minggu Ini"*, kolom tampilan upah, lembur rupiah, potongan kasbon, dan tombol *"Terapkan Potongan Kasbon"*.

- [ ] **Step 4: Jalankan test untuk memverifikasi lulus**

Run: `cd frontend; npx vitest run src/pages/ReportsPage.test.tsx --pool=threads`  
Expected: PASS

- [ ] **Step 5: Commit Task 6**

```bash
git add frontend/src/pages/ReportsPage.tsx frontend/src/pages/ReportsPage.test.tsx frontend/src/lib/types.ts
git commit -m "feat(ui): perbarui halaman laporan dengan format penggajian rupiah dan tombol tutup buku"
```

---

### Task 7: Verifikasi End-to-End & Uji Ekspor Excel/PDF Fisik

**Files:**
- Test: Script verifikasi E2E / pengujian unduhan berkas nyata

- [ ] **Step 1: Jalankan seluruh suite pengujian backend**

Run: `cd backend; ./mvnw test`  
Expected: BUILD SUCCESS (semua unit test, integration test, dan security test pass)

- [ ] **Step 2: Jalankan seluruh suite pengujian frontend**

Run: `cd frontend; npm run test`  
Expected: Semua test pass tanpa error

- [ ] **Step 3: Uji ekspor Excel nyata dan verifikasi rumus angka**

Unduh berkas `upah-mingguan.xlsx` dan `gaji-bulanan.xlsx` via API test, verifikasi bahwa sel tabel dan total cocok dengan data rapat 8 Oktober 2026.

- [ ] **Step 4: Commit dokumentasi penyelesaian**

```bash
git add docs/
git commit -m "docs: perbarui dokumentasi keputusan dan arsitektur laporan aktual"
```

