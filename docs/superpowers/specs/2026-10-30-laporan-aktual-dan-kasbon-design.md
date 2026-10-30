# Design Document: Laporan Penggajian Aktual (AS-1 s/d AS-7) & Buku Kasbon Karyawan

**Tanggal:** 30 Oktober 2026  
**Status:** Draf Perancangan (Menunggu Persetujuan)  
**Terkait:** [K-031](file:///c:/all%20project/Biometric-Attendence-System/docs/keputusan.md), [K-032](file:///c:/all%20project/Biometric-Attendence-System/docs/keputusan.md), [K-037](file:///c:/all%20project/Biometric-Attendence-System/docs/keputusan.md), [K-105](file:///c:/all%20project/Biometric-Attendence-System/docs/keputusan.md), [dokumen-as-is.md](file:///c:/all%20project/Biometric-Attendence-System/docs/dokumen-as-is.md)

---

## 1. Intent & The Why

Sistem absensi dan penggajian Takka Steel saat ini telah berhasil mendata kehadiran, memproses scan mesin biometrik multi-cabang, dan menghitung jam kerja serta lembur. Namun, laporan ekspor Excel dan PDF yang tersedia di menu `/laporan` saat ini baru berupa data presensi fisik jam kerja, belum memuat format komersial/finansial yang digunakan perusahaan setiap minggunya (sebagaimana terdokumentasi pada foto meeting fisik 8 Oktober 2026, AS-1 s/d AS-7).

Tujuan perancangan ini adalah mewujudkan sistem laporan yang menghasilkan dokumen Excel (.xlsx) dan PDF cetak yang **100% identik dalam tata letak, kop, formula rupiah, rincian per cabang, dan lembar pendukung lembur** dengan format fisik klien, didukung oleh modul pencatatan **Kasbon / Piutang Karyawan** berbasis ledger transaksi otomatis.

---

## 2. Goals, Non-Goals, Anti-Goals

### Goals
1. **Laporan Upah Mingguan Supir & Kenek (Format AS-1 & AS-2):**
   - Mendukung siklus pembayaran mingguan 7 hari dari Sabtu sampai Jumat (**S M S S R K J**).
   - Menghitung `Jumlah Hari Hadir × Upah Harian` (misal Rp 60rb, 80rb, 100rb, 110rb, 125rb).
   - Menghitung `Jam Lembur Mingguan × Tarif Lembur/Jam` (Rp 14.000 untuk Supir, Rp 10.000 untuk Kenek).
   - Mengambil potongan cicilan kasbon aktif (`INPUT PIUTANG`).
   - Menghitung `Sisa Upah = Jumlah Upah + Lembur - Input Piutang`.
   - Menampilkan lembar kedua (*Sheet 2: Rekap Lemburan*) berisi breakdown jam lembur harian per karyawan (AS-2).
   - Menampilkan rekapitulasi cash per cabang (Takka 1, Takka 2, Takka Cilong, Cash) dan tanda tangan Direktur Operasional di bagian bawah laporan.
2. **Laporan Gaji Bulanan Staf Kantor (Format AS-5, AS-6 & AS-7):**
   - Mendukung periode bulanan (tanggal 1 s/d 31) dengan kop resmi *TAKKA STEEL*.
   - Menghitung `Onsite = Total Hari Masuk × Wages Harian` (default Rp 40.000/hari).
   - Mengambil `Gaji Pokok` bulanan dari aturan kerja karyawan (misal Rp 1.000.000 s/d 3.000.000).
   - Menghitung `Upah Lembur = Jam Lembur Bulanan × Tarif Lembur`.
   - Mengambil potongan cicilan kasbon bulanan (`INPUT PIUTANG`).
   - Menghitung `Total Gaji = Gaji Pokok + Onsite + Upah Lembur - Input Piutang`.
   - Menampilkan lembar kedua (*Sheet 2: Rekap Lembur Staf*, AS-6) dan lembar ketiga (*Sheet 3: Pengajuan Kas Toko*, AS-7 dengan rincian biaya transfer bank Rp 2.500/orang).
   - Pengesahan bertingkat: TTD Manager Accounting & Finance dan Director.
3. **Modul Buku Kasbon (Piutang) Karyawan:**
   - Tabel database `employee_loans` dan `loan_repayments` dengan integritas referensial dan audit log.
   - Menu web di grup *Operasional* untuk mencatat pinjaman kasbon baru, jadwal cicilan, sisa saldo, dan riwayat pembayaran.
   - Integrasi otomatis: saat laporan mingguan atau bulanan di-generate, sistem otomatis menarik cicilan aktif dan memotong sisa pinjaman hingga lunas.
4. **Ekspor Excel & PDF Berpresisi Tinggi:**
   - Excel multi-sheet menggunakan Apache POI dengan styling border, background warna, freeze panes, dan formula kalkulasi.
   - PDF landscape rapi menggunakan OpenPDF / iText dengan font Open Sans yang siap dicetak di kertas A4.

### Non-Goals
- Perhitungan pajak penghasilan PPh 21 atau BPJS Ketenagakerjaan (dikonfirmasi pada AS-IS bahwa Takka Steel tidak memotong BPJS/pajak di lembar upah ini).
- Integrasi pembayaran otomatis ke perbankan (Internet Banking API); pembayaran gaji tetap dilakukan manual transfer bank atau uang tunai (cash).

### Anti-Goals
- Tidak mengedit saldo kasbon secara langsung tanpa riwayat ledger transaksi (harus mematuhi K-037).
- Tidak menghapus atau merusak 3 laporan presensi fisik yang sudah ada (`rekap-absensi`, `rekap-lembur`, `detail-absensi`).

---

## 3. Constraints

- Backend: Spring Boot 4, Java 21, Spring Data JPA, Flyway migration, PostgreSQL 17.
- Hak akses: Hanya role `OWNER` dan `HR_FINANCE` yang berwenang melihat atau mengelola kasbon serta mengunduh laporan upah/gaji (role `EMPLOYEE` dibatasi).
- Tipe data nominal rupiah: Menggunakan `BigDecimal` di backend dan integer format IDR di frontend, tidak menggunakan floating point.

---

## 4. Approaches & Evaluation

1. **Pendekatan 1: Ekstensi Laporan Terintegrasi + Buku Kasbon Ledger (DIPILIH):**
   - Menambahkan 2 slug laporan baru di backend & frontend (`upah-mingguan` dan `gaji-bulanan`).
   - Membuat modul Kasbon baru (`loan` domain) dengan tabel `employee_loans` dan `loan_repayments`.
   - *Alasan dipilih:* Memenuhi 100% kebutuhan dokumen fisik AS-1 s/d AS-7, menjaga keamanan transaksi utang karyawan, dan memisahkan laporan upah finansial dari laporan presensi fisik murni.
2. **Pendekatan 2: Modifikasi Langsung Laporan Kehadiran yang Ada:**
   - Memodifikasi laporan absensi yang sudah ada tanpa membuat modul kasbon.
   - *Alasan ditolak:* Melanggar aturan sistem K-037 dan merusak laporan rekap absensi murni bagi cabang.
3. **Pendekatan 3: Modul Penggajian Formal dengan Freeze Snapshot:**
   - Membangun modul penggajian enterprise lengkap dengan draf, revisi, dan penguncian periode.
   - *Alasan ditolak:* Terlalu rumit dan kaku bagi owner toko besi yang membutuhkan laporan cepat per minggu.

---

## 5. Architectural & Data Model Design

### 5.1 Skema Database (`V19__kasbon_dan_pinjaman.sql`)
```sql
CREATE TABLE employee_loans (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    loan_number VARCHAR(64) NOT NULL UNIQUE,
    loan_date DATE NOT NULL,
    total_amount NUMERIC(14, 2) NOT NULL CHECK (total_amount > 0),
    installment_amount NUMERIC(14, 2) NOT NULL CHECK (installment_amount > 0),
    remaining_amount NUMERIC(14, 2) NOT NULL CHECK (remaining_amount >= 0),
    status VARCHAR(32) NOT NULL DEFAULT 'AKTIF', -- 'AKTIF', 'LUNAS', 'DIBATALKAN'
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loan_repayments (
    id BIGSERIAL PRIMARY KEY,
    loan_id BIGINT NOT NULL REFERENCES employee_loans(id) ON DELETE CASCADE,
    repayment_date DATE NOT NULL,
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    period_label VARCHAR(128) NOT NULL,
    report_type VARCHAR(64) NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_employee_loans_emp_status ON employee_loans(employee_id, status);
CREATE INDEX idx_loan_repayments_loan_id ON loan_repayments(loan_id);
```

### 5.2 Backend Domain Architecture
- **Paket Baru `com.biometricattendance.loan`:**
  - `EmployeeLoan`, `LoanRepayment` (JPA Entities)
  - `EmployeeLoanRepository`, `LoanRepaymentRepository`
  - `LoanDtos`: `CreateLoanRequest`, `LoanResponse`, `LoanRepaymentResponse`
  - `LoanService`: pembuatan kasbon baru, pengambilan cicilan aktif untuk periode laporan, pelunasan manual, dan audit log.
  - `LoanController`: REST API di `/api/loans` (dilindungi `OWNER` dan `HR_FINANCE`).
- **Pembaruan Paket `com.biometricattendance.report`:**
  - Tambah tipe laporan di `ReportType`:
    - `UPAH_MINGGUAN("upah-mingguan", "Laporan Upah Mingguan (Supir/Kenek)")`
    - `GAJI_BULANAN("gaji-bulanan", "Laporan Gaji Bulanan (Staf)")`
  - Tambah DTO field finansial di `ReportDtos` (upah harian, lembur nominal, piutang, sisa upah, onsite, gaji pokok).
  - Update `ExcelReportWriter` dengan renderer khusus:
    - `weeklyWageSheet` (Format AS-1) & `weeklyOvertimeSheet` (Format AS-2)
    - `monthlySalarySheet` (Format AS-5), `monthlyOvertimeSheet` (Format AS-6), `storeSubmissionSheet` (Format AS-7)
  - Update `PdfReportWriter` untuk merender PDF landscape bertata letak persis dokumen fisik.

### 5.3 Frontend Updates
- **Halaman Kasbon Baru (`LoansPage.tsx`):**
  - Daftar kasbon karyawan, status aktif/lunas, progress pelunasan, dialog tambah kasbon baru.
  - Ditambahkan ke Sidebar di bawah grup *Operasional*.
- **Penyempurnaan Halaman Laporan (`ReportsPage.tsx`):**
  - Opsi dropdown laporan mencakup:
    1. Rekap Absensi
    2. Rekap Lembur Tervalidasi
    3. Detail Absensi
    4. **Upah Mingguan (Supir & Kenek) [AS-1 & AS-2]** (Dilengkapi shortcut filter cepat: "Sabtu–Jumat Minggu Ini", "Minggu Lalu")
    5. **Gaji Bulanan (Staf Kantor) [AS-5, AS-6 & AS-7]** (Shortcut: "Bulan Ini", "Bulan Lalu")
  - Tabel pratinjau menampilkan kolom rupiah lengkap sebelum diunduh ke Excel / PDF.

---

## 6. Resolved Decisions & Builder Calls

Berdasarkan tinjauan arsitektur dan konfirmasi pengguna, keputusan alur berikut ditetapkan secara definitif:

1. **Mekanisme Eksekusi Pemotongan Kasbon (Idempotensi & Keamanan):**
   - **Pratinjau & Unduh Bersifat Read-Only:** Membuka pratinjau web atau mengunduh Excel/PDF kapan saja hanya membaca data proyeksi cicilan (`INPUT PIUTANG`) tanpa memotong saldo di database. File dapat dicek dan diunduh berulang kali tanpa risiko pemotongan ganda.
   - **Aksi Konfirmasi Tutup Buku Periode:** Pada halaman laporan tersedia tombol *"Terapkan Potongan Kasbon Periode Ini"*. Saat diklik oleh Finance/Owner, sistem mengeksekusi `POST /api/loans/apply-period-deductions` yang mencatat baris `loan_repayments` dan mengurangi `remaining_amount`. Tombol ini idempoten: jika periode tersebut sudah pernah ditutup buku, sistem menampilkan status *"Sudah Dipotong"* dan mencegah pemotongan kedua.
2. **Keterbatasan Maksimal Potongan & Kebijakan Kasbon:**
   - **Maksimal Potongan Kasbon:** Jika total upah kotor karyawan pada periode berjalan lebih kecil dari nominal cicilan normal (misal karyawan hanya masuk 1 hari), sistem membatasi potongan maksimal sebesar upah yang diperoleh agar upah bersih tidak pernah negatif (`Sisa Upah >= 0`). Sisa kewajiban kasbon tetap menjadi saldo aktif untuk periode selanjutnya.
   - **Satu Pinjaman Aktif per Karyawan:** Untuk mencegah tumpang tindih, satu karyawan hanya boleh memiliki 1 pinjaman dengan status `AKTIF`. Jika ada pinjaman tambahan sebelum lunas, pinjaman baru ditambahkan ke saldo berjalan atau pinjaman lama dilunasi terlebih dahulu.
3. **Logika Sheet 3 (Format AS-7: Pengajuan Kas Toko):**
   - Sheet ini merangkum kebutuhan kas transfer gaji staf per cabang. Jika filter cabang dipilih (misal Toko Takka 2), lembar ini mencantumkan nama staf cabang tersebut, total transfer, dan biaya admin bank Rp 2.500 per transfer. Jika filter "Semua Cabang", dibuatkan tabulasi rekap per cabang. Nomor invoice berformat: `INV-<branch_code>-<date_ddMMyyyy>`.
4. **Logika Agregasi Cabang Supir & Kenek (Format AS-1):**
   - Kotak ringkasan di bawah tabel mengelompokkan total upah berdasarkan cabang asal karyawan (`employee.branch_id`), yaitu Takka 1, Takka 2, dan Takka Cilong. Baris "Cash" menjumlahkan seluruh total upah yang dibayarkan secara tunai.
5. **Logika Wages / Onsite Staf (Format AS-5):**
   - `Wages` harian staf diambil dari `work_rules.dailyWage` (standar Rp 40.000).
   - `Total Days` hanya menghitung kehadiran fisik (V). Hari libur atau izin tidak dihitung dalam `Total Days`, sehingga Onsite murni dibayarkan sesuai hari masuk. Gaji Pokok tetap dibayarkan utuh sesuai aturan kerja.

---

## 7. What's Left to the Builder

- Pengaturan styling sel Apache POI (border tipis hitam, warna merah muda untuk libur/tidak hadir, highlight hijau/kuning untuk total).
- Spesimen tanda tangan di bagian bawah sheet (Direktur Operasional, Manager Accounting & Finance, Director).
- Penanganan kasus edge jika sisa saldo kasbon lebih kecil dari cicilan normal, dipotong sebesar sisa saldo tersebut dan status pinjaman otomatis berubah menjadi `LUNAS`.


