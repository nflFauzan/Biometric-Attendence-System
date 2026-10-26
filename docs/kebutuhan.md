# Kebutuhan Sistem

## 1. Gambaran Umum

Biometric Attendance & HR Management System adalah aplikasi web untuk mengelola data karyawan, absensi biometrik, aturan kerja, lembur, penggajian, kasbon, dan laporan perusahaan.

Sistem dirancang untuk kebutuhan awal sekitar 25 karyawan di 3 cabang, dengan rencana satu mesin absensi sidik jari per cabang. Jumlah karyawan dan perangkat dapat berubah.

Mesin absensi mengirimkan data scan melalui jaringan ke backend. Backend menjadi pusat validasi, penyimpanan, dan pemrosesan aturan bisnis. Aplikasi web mengambil data dari backend dan tidak berkomunikasi langsung dengan mesin.

Dokumen ini menjadi acuan kebutuhan fungsional, kebutuhan nonfungsional, prioritas implementasi, dan pengujian penerimaan.

### 1.1 Penanda Status

- **Wajib:** kebutuhan yang menjadi baseline implementasi.
- **Bersyarat:** kebutuhan yang bergantung pada kemampuan hardware, kebijakan, atau keputusan lain.
- **Perlu konfirmasi:** kebutuhan yang belum boleh difinalisasi sebelum mendapat keputusan dari klien.

### 1.2 Prioritas

- **P0 — Core:** diperlukan untuk sistem inti dan proses absensi/payroll yang dapat diuji.
- **P1 — Operasional:** diperlukan untuk operasional yang lebih lengkap dan nyaman.
- **P2 — Pengembangan lanjutan:** dapat dilakukan setelah kebutuhan inti stabil.

Prioritas menunjukkan urutan implementasi, bukan pengganti persetujuan kebutuhan bisnis.

---

## 2. Aktor dan Hak Akses

### 2.1 Role Awal

Sistem menggunakan tiga role awal:

1. **Owner:** mengelola aturan perusahaan, akun tertentu, audit, serta proses bisnis sesuai kewenangannya.
2. **HR/Finance:** mengelola data karyawan, memeriksa absensi, memverifikasi komponen penggajian, dan mengelola payroll sesuai izin.
3. **Karyawan:** melakukan absensi dan menggunakan fitur mandiri yang diaktifkan untuknya.

### 2.2 Matriks Hak Akses

| Fitur | Owner | HR/Finance | Karyawan |
|---|---|---|---|
| Dashboard dan laporan perusahaan | Ya | Ya | Tidak |
| Data karyawan (tambah/ubah) dan cabang absen | Ya | Lihat saja (K-128) | Tidak |
| Master cabang dan kategori | Ya | Ya | Tidak |
| Data perangkat dan status koneksi | Ya | Ya | Tidak |
| Pendaftaran biometrik | Ya | Ya | Tidak |
| Monitoring dan rekap absensi | Ya | Ya | Tidak |
| Koreksi absensi | Ya | Ya, sesuai izin | Tidak |
| Pengajuan dan persetujuan izin/cuti | Ya | Ya | Mengajukan |
| Verifikasi lembur | Ya | Ya | Tidak |
| Pengelolaan aturan dan tarif | Ya | Tidak | Tidak |
| Pengelolaan akun Karyawan | Ya | Ya | Tidak |
| Pengelolaan akun Owner/HR | Ya | Tidak | Tidak |
| Payroll dan finalisasi | Ya | Ya, sesuai izin | Tidak |
| Pencatatan kasbon | Ya | Ya, sesuai izin | Tidak |
| Pengaturan bonus | Ya | Sesuai kewenangan yang ditetapkan | Tidak |
| Audit log | Ya | Tidak pada baseline awal | Tidak |
| Profil dan ganti password | Ya | Ya | Ya |
| Melihat riwayat absensi sendiri | Belum relevan | Belum relevan | Ya, hanya miliknya (K-127) |
| Melihat slip gaji sendiri | Belum relevan | Belum relevan | Perlu konfirmasi |

Catatan:

- HR/Finance merupakan satu role dengan akses yang sama pada baseline awal.
- Hak akses wajib divalidasi pada backend.
- Akses untuk membuka kembali payroll, menyetujui koreksi, dan mengekspor data sensitif harus mengikuti kewenangan yang ditetapkan.
- Custom role dan pembatasan data berdasarkan cabang masih perlu dikonfirmasi.

---

## 3. Kebutuhan Fungsional

### 3.1 Autentikasi dan Manajemen Akun

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| AUTH-001 | Sistem menyediakan halaman login menggunakan username dan password. | P0 | Wajib |
| AUTH-002 | Username tidak membedakan huruf besar/kecil dan harus unik setelah normalisasi. | P0 | Wajib |
| AUTH-003 | Sistem memvalidasi kredensial pada backend. | P0 | Wajib |
| AUTH-004 | Sistem menggunakan session cookie dengan CSRF protection dan durasi sesi 8 jam. | P0 | Wajib |
| AUTH-005 | Akun Owner awal dibuat melalui konfigurasi environment, bukan kredensial tetap di source code. | P0 | Wajib |
| AUTH-006 | Akun baru atau akun yang di-reset wajib mengganti password sesuai mekanisme yang ditentukan. | P0 | Wajib |
| AUTH-007 | Owner dapat mengelola akun Owner dan HR/Finance; HR/Finance dapat mengelola akun Karyawan. | P0 | Wajib |
| AUTH-008 | Sistem mencegah penonaktifan atau penghapusan akun Owner terakhir yang masih aktif. | P0 | Wajib |
| AUTH-009 | Pengguna dapat keluar dari sistem dan mengakhiri sesi. | P0 | Wajib |
| AUTH-010 | Sistem menolak akses ke fitur yang tidak diizinkan bagi role pengguna. | P0 | Wajib |
| AUTH-011 | Sistem menyediakan reset password oleh pengguna berwenang. | P1 | Wajib secara operasional |
| AUTH-012 | Sistem mendukung custom role dan izin per fitur. | P2 | Perlu konfirmasi |

### 3.2 Dashboard

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| DASH-001 | Dashboard menampilkan ringkasan karyawan yang hadir hari ini. | P0 | Wajib |
| DASH-002 | Dashboard menampilkan jumlah keterlambatan hari ini. | P0 | Wajib |
| DASH-003 | Dashboard menampilkan kandidat lembur berdasarkan scan yang tersedia. | P0 | Wajib |
| DASH-004 | Dashboard menyediakan ringkasan absensi per cabang. | P1 | Wajib |
| DASH-005 | Dashboard menampilkan status perangkat dan waktu komunikasi terakhir jika data tersebut tersedia. | P1 | Bersyarat pada integrasi |
| DASH-006 | Dashboard memperbarui data ketika backend menerima scan baru. | P0 | Wajib |
| DASH-007 | Dashboard membedakan data valid, data belum lengkap, dan data yang menunggu pemeriksaan. | P0 | Wajib |

Target pembaruan dalam hitungan detik berlaku ketika perangkat, jaringan, backend, dan mekanisme pembaruan tersedia. Target waktu respons yang terukur perlu ditetapkan dalam pengujian performa.

### 3.3 Master Data Karyawan

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| EMP-001 | Pengguna berwenang dapat menambah data karyawan. | P0 | Wajib |
| EMP-002 | Pengguna berwenang dapat melihat detail dan daftar karyawan. | P0 | Wajib |
| EMP-003 | Pengguna berwenang dapat mengubah data karyawan. | P0 | Wajib |
| EMP-004 | Sistem mendukung status aktif dan nonaktif. | P0 | Wajib |
| EMP-005 | Data karyawan dapat dikaitkan dengan cabang. | P0 | Wajib |
| EMP-006 | Data karyawan dapat dikaitkan dengan kategori payroll, seperti supir, kenek, atau staf. | P0 | Wajib |
| EMP-007 | Sistem menyimpan komponen gaji pokok dan upah harian secara terpisah jika berlaku. | P0 | Wajib |
| EMP-008 | Sistem menyimpan relasi antara karyawan, identitas biometrik, dan perangkat yang digunakan. | P0 | Wajib |
| EMP-009 | Sistem mempertahankan referensi historis karyawan pada transaksi absensi dan payroll. | P0 | Wajib |
| EMP-010 | Sistem menyediakan proses keluar karyawan dan penonaktifan akses. | P1 | Wajib |
| EMP-011 | Sistem menyediakan impor massal data karyawan dari Excel. | P1 | Perlu konfirmasi kebutuhan dan format |
| EMP-012 | Sistem menyediakan pembatasan data karyawan berdasarkan cabang. | P2 | Perlu konfirmasi |

Kolom wajib pada formulir tambah karyawan harus mengikuti template data karyawan yang disetujui klien. Sistem tidak boleh meminta data pribadi yang tidak diperlukan untuk operasional tanpa dasar kebutuhan yang jelas.

### 3.4 Master Data Cabang dan Perangkat

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| DEV-001 | Sistem mendukung pencatatan cabang perusahaan. | P0 | Wajib |
| DEV-002 | Sistem mendukung pencatatan perangkat absensi. | P0 | Wajib |
| DEV-003 | Perangkat memiliki identitas unik yang dapat divalidasi backend. | P0 | Wajib |
| DEV-004 | Perangkat dikaitkan dengan cabang. | P0 | Wajib |
| DEV-005 | Backend menerima data scan melalui API atau protokol integrasi yang disepakati. | P0 | Wajib |
| DEV-006 | Sistem mencatat waktu komunikasi terakhir jika didukung protokol. | P1 | Bersyarat |
| DEV-007 | Sistem menampilkan status koneksi perangkat berdasarkan sinyal yang tersedia. | P1 | Bersyarat |
| DEV-008 | Sistem mendukung konfigurasi koneksi untuk perangkat yang ditambahkan kemudian. | P1 | Wajib |
| DEV-009 | Sistem mendukung antrean offline dan sinkronisasi otomatis. | P1 | Perlu validasi hardware |
| DEV-010 | Sistem mendukung penghapusan biometrik pada perangkat melalui mekanisme terkontrol. | P1 | Perlu validasi hardware |

### 3.5 Pendaftaran Biometrik

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| BIO-001 | Sistem dapat mencatat status pendaftaran sidik jari karyawan. | P0 | Wajib |
| BIO-002 | Sistem dapat mengaitkan identitas biometrik dengan karyawan. | P0 | Wajib |
| BIO-003 | Sistem dapat membedakan status belum didaftarkan, menunggu, berhasil, atau gagal jika mekanisme integrasi mendukungnya. | P1 | Bersyarat |
| BIO-004 | Sistem dapat menampilkan kegagalan enrollment dan menyediakan tindak lanjut. | P1 | Bersyarat |
| BIO-005 | Sistem dapat mencatat status penghapusan biometrik saat karyawan keluar. | P1 | Wajib |
| BIO-006 | Sistem membatasi akses ke informasi biometrik. | P0 | Wajib |
| BIO-007 | Sistem tidak menyimpan template sidik jari pada backend sebelum lokasi penyimpanan dan protokol biometrik diputuskan. | P0 | Wajib sebagai batasan desain |

**Keputusan teknis yang masih diperlukan:** apakah template sidik jari disimpan dan dicocokkan pada sensor atau dikirim ke server; bagaimana enrollment dilakukan; dan bagaimana penghapusan dilakukan pada perangkat.

### 3.6 Integrasi Scan Absensi

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| ATT-001 | Backend menerima kejadian scan dari perangkat yang terdaftar. | P0 | Wajib |
| ATT-002 | Backend memvalidasi identitas perangkat dan struktur data scan. | P0 | Wajib |
| ATT-003 | Setiap kejadian scan memiliki identitas yang dapat digunakan untuk mendeteksi duplikasi. | P0 | Wajib |
| ATT-004 | Backend menyimpan scan secara persisten sebelum memberikan ACK sukses. | P0 | Wajib |
| ATT-005 | Pengiriman ulang kejadian scan yang sama tidak menghasilkan catatan ganda. | P0 | Wajib |
| ATT-006 | Sistem menyimpan raw scan terpisah dari hasil rekap absensi. | P0 | Wajib |
| ATT-007 | Sistem memproses scan menjadi kandidat jam masuk dan jam pulang sesuai aturan yang berlaku. | P0 | Wajib |
| ATT-008 | Sistem menandai data scan yang tidak lengkap atau tidak wajar. | P0 | Wajib |
| ATT-009 | Sistem menggunakan zona waktu yang dikonfigurasi secara eksplisit. | P0 | Wajib |
| ATT-010 | Sistem dapat memproses ulang rekap ketika scan baru atau koreksi sah diterima. | P0 | Wajib |
| ATT-011 | Sistem mendukung penyimpanan offline pada perangkat dan pengiriman ulang setelah koneksi pulih. | P1 | Bersyarat pada hardware |
| ATT-012 | Sistem menyediakan simulator untuk pengujian sebelum perangkat fisik tersedia. | P0 | Wajib |

### 3.7 Monitoring dan Rekap Absensi

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| ATT-013 | Pengguna berwenang dapat melihat absensi hari ini. | P0 | Wajib |
| ATT-014 | Pengguna berwenang dapat melihat riwayat absensi berdasarkan tanggal dan karyawan. | P0 | Wajib |
| ATT-015 | Sistem menampilkan jam masuk dan pulang yang diproses dari scan. | P0 | Wajib |
| ATT-016 | Sistem menghitung keterlambatan berdasarkan jadwal dan toleransi yang berlaku. | P0 | Wajib |
| ATT-017 | Sistem menampilkan status pulang lebih awal jika dapat ditentukan dari data yang valid. | P1 | Wajib |
| ATT-018 | Sistem menampilkan status data yang tidak lengkap dan memerlukan pemeriksaan. | P0 | Wajib |
| ATT-019 | Sistem membedakan status kehadiran dari kelayakan upah harian. | P0 | Wajib |
| ATT-020 | Sistem menampilkan rekap berdasarkan cabang dan periode. | P1 | Wajib |
| ATT-021 | Sistem menyediakan riwayat absensi mandiri untuk Karyawan. | P1 | Perlu konfirmasi akses |
| ATT-022 | Sistem menyediakan fitur ekspor rekap absensi. | P1 | Wajib |

### 3.8 Koreksi Absensi

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| COR-001 | Pengguna berwenang dapat mengoreksi catatan absensi. | P0 | Wajib |
| COR-002 | Setiap koreksi wajib memiliki alasan. | P0 | Wajib |
| COR-003 | Raw scan asli tidak boleh ditimpa oleh koreksi. | P0 | Wajib |
| COR-004 | Sistem mencatat nilai sebelum dan sesudah koreksi. | P0 | Wajib |
| COR-005 | Sistem mencatat pelaku dan waktu koreksi. | P0 | Wajib |
| COR-006 | Sistem memproses ulang rekap setelah koreksi yang sah. | P0 | Wajib |
| COR-007 | Koreksi yang berdampak pada payroll final harus melalui prosedur yang diizinkan. | P0 | Wajib |
| COR-008 | Sistem mendukung persetujuan kedua untuk koreksi tertentu. | P2 | Perlu konfirmasi |

### 3.9 Pengaturan Aturan Kerja

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| RULE-001 | Owner dapat melihat dan mengubah aturan kerja. | P0 | Wajib |
| RULE-002 | Sistem mendukung aturan global. | P0 | Wajib |
| RULE-003 | Sistem mendukung aturan khusus karyawan. | P0 | Wajib |
| RULE-004 | Sistem menyimpan tanggal mulai berlaku untuk aturan yang dapat berubah. | P0 | Wajib |
| RULE-005 | Sistem menerapkan prioritas aturan secara konsisten. | P0 | Wajib |
| RULE-006 | Sistem mempertahankan riwayat aturan yang digunakan untuk payroll historis. | P0 | Wajib |
| RULE-007 | Sistem menyediakan konfigurasi jam masuk, jam pulang, toleransi, dan kalender kerja. | P0 | Wajib, dengan nilai final menunggu konfirmasi |
| RULE-008 | Sistem menyediakan konfigurasi tarif lembur. | P0 | Wajib, tarif final menunggu konfirmasi |
| RULE-009 | Sistem menyediakan konfigurasi komponen penghasilan dan potongan yang disetujui. | P0 | Wajib |
| RULE-010 | Sistem mendukung custom role untuk mengelola aturan. | P2 | Perlu konfirmasi |

### 3.10 Izin dan Cuti

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| LEAVE-001 | Sistem mencatat pengajuan izin atau cuti. | P1 | Wajib untuk modul izin/cuti |
| LEAVE-002 | Pengajuan memiliki karyawan, jenis, tanggal, alasan, dan status. | P1 | Wajib |
| LEAVE-003 | Sistem mendukung dokumen pendukung jika diwajibkan. | P1 | Wajib jika kebijakan memerlukannya |
| LEAVE-004 | HR/Finance atau approver yang ditetapkan dapat menyetujui atau menolak pengajuan. | P1 | Wajib |
| LEAVE-005 | Sistem menyimpan alasan penolakan jika ada. | P1 | Wajib |
| LEAVE-006 | Pengajuan yang belum disetujui tidak otomatis dianggap sebagai izin yang sah. | P1 | Wajib |
| LEAVE-007 | Status izin/cuti yang disetujui dapat digunakan dalam rekap absensi. | P1 | Wajib |
| LEAVE-008 | Sistem menghitung kuota cuti dan saldo cuti. | P2 | Perlu konfirmasi kebijakan |

Jenis izin/cuti, kuota, approver final, serta dampak terhadap payroll harus dikonfirmasi sebelum aturan final diterapkan.

### 3.11 Lembur

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| OT-001 | Sistem menghitung kandidat lembur berdasarkan catatan scan yang valid. | P0 | Wajib |
| OT-002 | Pada hari kerja normal, kandidat lembur dimulai setelah pukul 17.00. | P0 | Disepakati |
| OT-003 | Sistem tidak mensyaratkan delapan jam kerja normal selesai sebelum kandidat lembur dimulai. | P0 | Disepakati |
| OT-004 | Sistem menampilkan durasi dan dasar perhitungan lembur. | P0 | Wajib |
| OT-005 | Pengguna berwenang dapat memverifikasi atau mengoreksi durasi lembur. | P0 | Wajib |
| OT-006 | Koreksi lembur memiliki alasan dan audit log. | P0 | Wajib |
| OT-007 | Nominal lembur dihitung berdasarkan tarif yang berlaku dan durasi yang disetujui. | P0 | Wajib |
| OT-008 | Tarif historis tetap digunakan untuk periode yang bersangkutan. | P0 | Wajib |
| OT-009 | Sistem menyediakan laporan lembur per kategori dan periode payroll. | P0 | Wajib |
| OT-010 | Sistem mendukung pengajuan dan persetujuan lembur sebelum pekerjaan dilakukan. | P2 | Perlu konfirmasi |
| OT-011 | Sistem menghitung perlakuan khusus lembur pada hari libur. | P1 | Perlu konfirmasi kebijakan |

### 3.12 Payroll

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| PAY-001 | Sistem mendukung payroll mingguan untuk supir dan kenek. | P0 | Disepakati |
| PAY-002 | Sistem mendukung payroll bulanan untuk staf lainnya. | P0 | Disepakati |
| PAY-003 | Sistem memisahkan gaji pokok bulanan dan upah harian. | P0 | Disepakati |
| PAY-004 | Sistem menyusun usulan upah harian berdasarkan aturan kehadiran. | P0 | Disepakati |
| PAY-005 | Pengguna berwenang dapat memverifikasi kelayakan upah harian. | P0 | Disepakati |
| PAY-006 | Sistem memasukkan lembur yang telah disetujui ke draf payroll. | P0 | Wajib |
| PAY-007 | Sistem menghitung payroll berdasarkan komponen penghasilan dan potongan yang berlaku. | P0 | Wajib |
| PAY-008 | Sistem menyediakan tampilan rincian perhitungan sebelum finalisasi. | P0 | Wajib |
| PAY-009 | Pengguna berwenang dapat memfinalisasi payroll. | P0 | Wajib |
| PAY-010 | Payroll final menyimpan snapshot tarif, aturan, input, komponen, dan hasil. | P0 | Wajib |
| PAY-011 | Payroll final dapat dibuka kembali melalui prosedur terkontrol. | P1 | Disepakati |
| PAY-012 | Finalisasi ulang membuat versi baru dan mempertahankan versi sebelumnya. | P0 | Wajib |
| PAY-013 | Sistem menyediakan riwayat status, finalisasi, pembukaan kembali, dan koreksi payroll. | P0 | Wajib |
| PAY-014 | Sistem mendukung BPJS dan PPh 21 jika kebijakan perhitungannya telah dikonfirmasi. | P1 | Bersyarat |
| PAY-015 | Sistem mendukung slip gaji mandiri untuk Karyawan. | P2 | Perlu konfirmasi |
| PAY-016 | Sistem menyediakan ekspor laporan payroll. | P0 | Wajib |

### 3.13 Kasbon atau Piutang Karyawan

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| DEBT-001 | Sistem mencatat transaksi kasbon karyawan. | P0 | Wajib |
| DEBT-002 | Sistem mencatat pembayaran atau pelunasan kasbon. | P0 | Wajib |
| DEBT-003 | Saldo kasbon dihitung dari ledger transaksi. | P0 | Wajib |
| DEBT-004 | Setiap transaksi memiliki tanggal, nominal, jenis, karyawan, dan keterangan. | P0 | Wajib |
| DEBT-005 | Sistem mencatat pengguna yang membuat atau mengubah transaksi. | P0 | Wajib |
| DEBT-006 | Pemotongan kasbon dari payroll mengikuti aturan yang disetujui. | P0 | Bersyarat pada kebijakan |
| DEBT-007 | Sistem menampilkan riwayat transaksi dan saldo kasbon. | P0 | Wajib |
| DEBT-008 | Sistem mendukung cicilan otomatis sesuai batas dan jadwal yang ditetapkan. | P2 | Perlu konfirmasi |

### 3.14 Bonus dan Komponen Tambahan

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| BONUS-001 | Owner dapat menetapkan nominal bonus karyawan. | P1 | Disepakati |
| BONUS-002 | Nominal bonus tersimpan sebagai komponen payroll. | P1 | Wajib |
| BONUS-003 | Sistem mencatat periode, nominal, dan pengguna yang menetapkan bonus. | P1 | Wajib |
| BONUS-004 | Sistem menghitung bonus otomatis berdasarkan KPI. | P2 | Tidak termasuk versi pertama; perlu keputusan baru jika dibutuhkan |

### 3.15 Laporan dan Ekspor

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| REP-001 | Sistem menyediakan laporan absensi harian. | P0 | Wajib |
| REP-002 | Sistem menyediakan laporan keterlambatan dan jam kerja. | P0 | Wajib |
| REP-003 | Sistem menyediakan laporan lembur. | P0 | Wajib |
| REP-004 | Sistem menyediakan laporan payroll mingguan supir/kenek. | P0 | Wajib |
| REP-005 | Sistem menyediakan laporan lembur supir/kenek. | P0 | Wajib |
| REP-006 | Sistem menyediakan laporan payroll bulanan staf. | P0 | Wajib |
| REP-007 | Sistem menyediakan laporan lembur bulanan staf. | P0 | Wajib |
| REP-008 | Sistem menyediakan filter berdasarkan periode, karyawan, dan cabang jika relevan. | P1 | Wajib |
| REP-009 | Sistem menyediakan ekspor Excel dan PDF. | P1 | Wajib |
| REP-010 | Laporan payroll historis menggunakan snapshot payroll terkait. | P0 | Wajib |
| REP-011 | Sistem menyediakan laporan riwayat transaksi kasbon. | P1 | Wajib |

Empat laporan payroll utama harus direkonsiliasi dengan formulir aktual perusahaan sebelum penerimaan final.

### 3.16 Audit Log

| ID | Kebutuhan | Prioritas | Status |
|---|---|---|---|
| AUD-001 | Sistem mencatat koreksi absensi. | P0 | Wajib |
| AUD-002 | Sistem mencatat perubahan aturan dan tarif. | P0 | Wajib |
| AUD-003 | Sistem mencatat perubahan komponen payroll. | P0 | Wajib |
| AUD-004 | Sistem mencatat finalisasi, pembukaan kembali, dan finalisasi ulang payroll. | P0 | Wajib |
| AUD-005 | Sistem mencatat perubahan transaksi kasbon. | P0 | Wajib |
| AUD-006 | Sistem mencatat pengelolaan akun dan perubahan hak akses. | P0 | Wajib |
| AUD-007 | Audit log mencatat pelaku, waktu, tindakan, objek, dan alasan jika relevan. | P0 | Wajib |
| AUD-008 | Pengguna tanpa izin tidak dapat mengubah atau menghapus audit log. | P0 | Wajib |
| AUD-009 | Ekspor data sensitif dicatat jika fitur ekspor tersedia. | P1 | Wajib |

---

## 4. Kebutuhan Nonfungsional

### 4.1 Keamanan

| ID | Kebutuhan | Prioritas |
|---|---|---|
| NFR-SEC-001 | Password disimpan menggunakan algoritma hashing yang aman, bukan plaintext. | P0 |
| NFR-SEC-002 | Semua endpoint yang dilindungi memvalidasi sesi dan izin pengguna. | P0 |
| NFR-SEC-003 | Mutasi yang menggunakan session cookie dilindungi CSRF. | P0 |
| NFR-SEC-004 | Secret dan password awal tidak disimpan di repository. | P0 |
| NFR-SEC-005 | Akses ke payroll, kasbon, akun, dan data biometrik dibatasi. | P0 |
| NFR-SEC-006 | Deployment produksi menggunakan HTTPS/TLS. | P0 |
| NFR-SEC-007 | Input dari frontend dan perangkat divalidasi pada backend. | P0 |
| NFR-SEC-008 | Data sensitif tidak ditulis ke log aplikasi tanpa kebutuhan dan perlindungan yang sesuai. | P0 |

### 4.2 Integritas dan Keandalan Data

| ID | Kebutuhan | Prioritas |
|---|---|---|
| NFR-DATA-001 | Data scan tidak boleh hilang setelah backend menyatakan penyimpanan berhasil. | P0 |
| NFR-DATA-002 | Pengiriman ulang scan yang sama tidak menghasilkan catatan ganda. | P0 |
| NFR-DATA-003 | Koreksi tidak menghapus raw scan. | P0 |
| NFR-DATA-004 | Snapshot payroll historis tetap dapat ditelusuri setelah konfigurasi berubah. | P0 |
| NFR-DATA-005 | Backup database dilakukan secara terjadwal. | P0 |
| NFR-DATA-006 | Prosedur restore diuji sebelum sistem digunakan untuk operasional penting. | P0 |
| NFR-DATA-007 | Sistem mempertahankan referensi transaksi terhadap karyawan meskipun karyawan dinonaktifkan. | P0 |

Klien pernah menginginkan penyimpanan riwayat absensi dan gaji jangka panjang. Durasi retensi final, termasuk kebutuhan hukum dan kebijakan penghapusan, harus ditetapkan secara eksplisit. Penyimpanan seumur hidup tidak boleh dianggap sebagai keputusan final tanpa validasi.

### 4.3 Performa

| ID | Kebutuhan | Prioritas |
|---|---|---|
| NFR-PERF-001 | Scan yang berhasil disimpan tersedia di dashboard tanpa penundaan yang tidak perlu. | P0 |
| NFR-PERF-002 | Sistem tetap responsif untuk skala awal sekitar 25 karyawan dan 3 cabang. | P0 |
| NFR-PERF-003 | Rekap dan laporan menggunakan query yang efisien. | P1 |
| NFR-PERF-004 | Target waktu respons API dan pembaruan dashboard ditentukan melalui pengujian. | P1 |

### 4.4 Waktu dan Zona Waktu

| ID | Kebutuhan | Prioritas |
|---|---|---|
| NFR-TIME-001 | Zona waktu aplikasi dikonfigurasi secara eksplisit. | P0 |
| NFR-TIME-002 | Nilai awal zona waktu adalah `Asia/Jakarta`, menunggu konfirmasi operasional. | P0 |
| NFR-TIME-003 | Waktu scan dibedakan dari waktu penerimaan data oleh server. | P0 |
| NFR-TIME-004 | Perhitungan tanggal kerja menggunakan zona waktu dan kalender yang konsisten. | P0 |

### 4.5 Kemudahan Pemeliharaan dan Pengujian

| ID | Kebutuhan | Prioritas |
|---|---|---|
| NFR-MAINT-001 | Backend menggunakan pemisahan modul dan tanggung jawab yang jelas. | P0 |
| NFR-MAINT-002 | Perubahan skema database dikelola melalui Flyway migration. | P0 |
| NFR-MAINT-003 | API didokumentasikan melalui OpenAPI. | P1 |
| NFR-MAINT-004 | Aturan perhitungan bisnis dapat diuji tanpa bergantung pada perangkat fisik. | P0 |
| NFR-MAINT-005 | Sistem menyediakan simulator untuk menguji integrasi scan. | P0 |
| NFR-MAINT-006 | Pengujian mencakup unit test dan integration test untuk proses kritis. | P0 |

### 4.6 Privasi dan Retensi

| ID | Kebutuhan | Prioritas |
|---|---|---|
| NFR-PRIV-001 | Akses ke data pribadi dibatasi berdasarkan kebutuhan kerja. | P0 |
| NFR-PRIV-002 | Penyimpanan dan pemrosesan data biometrik mengikuti desain yang disetujui. | P0 |
| NFR-PRIV-003 | Penghapusan biometrik saat karyawan keluar mengikuti kemampuan perangkat dan prosedur yang disetujui. | P1 |
| NFR-PRIV-004 | Kebijakan retensi dan penghapusan data ditetapkan sebelum produksi. | P0 |
| NFR-PRIV-005 | Backup dan ekspor data sensitif dilindungi dengan kontrol akses yang sesuai. | P0 |

---

## 5. Batasan dan Hal di Luar Cakupan Versi Pertama

Hal berikut tidak otomatis masuk ke versi pertama tanpa keputusan tambahan:

1. Sistem multi-tenant untuk banyak perusahaan.
2. Integrasi langsung frontend ke mesin atau database.
3. Perhitungan bonus otomatis berbasis KPI.
4. Custom role dan pembatasan izin per cabang.
5. Perhitungan BPJS, PPh 21, dan potongan yang kebijakannya belum dikonfirmasi.
6. Persetujuan lembur sebelum pekerjaan dilakukan.
7. Modul rekrutmen, penilaian kinerja, pelatihan, dan manajemen talenta.
8. Aplikasi mobile native.
9. Penyimpanan template sidik jari pada server sebelum desain biometrik ditetapkan.
10. Sinkronisasi offline otomatis sebelum dukungan perangkat dikonfirmasi.

---

## 6. Kriteria Penerimaan Sistem

Sistem dianggap memenuhi baseline inti ketika skenario berikut berhasil diuji.

| ID | Skenario | Kriteria penerimaan |
|---|---|---|
| AC-001 | Login valid | Pengguna dapat masuk sesuai role yang diberikan. |
| AC-002 | Login invalid | Kredensial salah tidak menghasilkan sesi yang sah. |
| AC-003 | Pembatasan akses | Pengguna yang tidak berwenang ditolak oleh backend. |
| AC-004 | Pembuatan karyawan | Data karyawan tersimpan dan dapat ditemukan kembali. |
| AC-005 | Penerimaan scan | Scan valid disimpan dan dapat ditelusuri ke perangkat serta karyawan. |
| AC-006 | Scan duplikat | Pengiriman ulang kejadian yang sama tidak menghasilkan absensi ganda. |
| AC-007 | Kegagalan penyimpanan | Backend tidak mengirim ACK sukses jika data belum tersimpan secara persisten. |
| AC-008 | Rekap absensi | Scan valid diproses sesuai aturan jadwal yang berlaku. |
| AC-009 | Scan tidak lengkap | Sistem menandai data untuk pemeriksaan dan tidak mengarang waktu yang hilang. |
| AC-010 | Koreksi absensi | Koreksi tercatat, raw scan tetap utuh, dan hasil rekap diperbarui. |
| AC-011 | Perhitungan lembur | Scan pulang pukul 18.00 menghasilkan kandidat lembur 60 menit pada hari kerja normal, sebelum penyesuaian lain. |
| AC-012 | Periode payroll | Sistem dapat memproses periode mingguan untuk supir/kenek dan bulanan untuk staf. |
| AC-013 | Pemisahan komponen | Gaji pokok bulanan dan upah harian tersimpan sebagai komponen berbeda. |
| AC-014 | Kasbon | Saldo kasbon dapat direkonsiliasi dengan ledger transaksi. |
| AC-015 | Finalisasi payroll | Snapshot input, aturan, tarif, komponen, hasil, dan pelaku finalisasi tersimpan. |
| AC-016 | Perubahan tarif | Perubahan tarif dengan tanggal efektif tidak mengubah payroll historis secara otomatis. |
| AC-017 | Reopen payroll | Versi sebelumnya tetap tersimpan dan pembukaan kembali tercatat. |
| AC-018 | Finalisasi ulang | Sistem menyimpan versi baru tanpa menghapus versi sebelumnya. |
| AC-019 | Audit log | Perubahan penting dapat ditelusuri berdasarkan pelaku, waktu, tindakan, dan objek. |
| AC-020 | Laporan payroll | Empat laporan utama dapat dibandingkan dengan formulir aktual yang telah disetujui. |
| AC-021 | Backup dan restore | Backup dapat dipulihkan melalui prosedur yang telah diuji. |
| AC-022 | Simulator | Alur scan dapat diuji sebelum mesin fisik tersedia. |

Kriteria yang bergantung pada kebijakan atau hardware baru dianggap final setelah keputusan terkait ditutup.

---

## 7. Strategi Pengujian

### 7.1 Unit Test

Menguji logika terisolasi, termasuk:

- Penentuan keterlambatan.
- Perhitungan kandidat lembur.
- Pemilihan aturan berdasarkan tanggal efektif.
- Perhitungan upah harian.
- Perhitungan komponen payroll.
- Perhitungan saldo kasbon.
- Penentuan hak akses.

### 7.2 Integration Test

Menguji interaksi antar komponen, termasuk:

- API penerimaan scan dan penyimpanan database.
- Idempotensi scan.
- Autentikasi dan otorisasi.
- Koreksi absensi beserta audit log.
- Snapshot payroll.
- Pembukaan kembali dan finalisasi ulang.
- Migrasi database melalui Flyway.

### 7.3 End-to-End Test

Menguji skenario operasional dari awal hingga akhir:

1. Tambah karyawan.
2. Kaitkan karyawan dengan cabang dan identitas biometrik.
3. Simulasikan scan masuk dan pulang.
4. Periksa hasil rekap absensi.
5. Verifikasi lembur.
6. Buat dan periksa draf payroll.
7. Finalisasi payroll.
8. Ubah tarif untuk periode berikutnya.
9. Buka kembali payroll lama dan pastikan tarif historis tetap digunakan.
10. Rekonsiliasi laporan terhadap formulir aktual perusahaan.

### 7.4 Pengujian Regresi

Setiap perubahan aturan bisnis wajib menjalankan pengujian regresi yang relevan. Pengujian payroll harus mencakup setidaknya:

- Tarif yang berubah berdasarkan tanggal efektif.
- Perubahan aturan global dan khusus karyawan.
- Koreksi absensi sebelum dan setelah finalisasi.
- Payroll yang dibuka kembali.
- Finalisasi ulang dengan snapshot baru.
- Rekonsiliasi empat laporan payroll.

---

## 8. Ketergantungan dan Pertanyaan Terbuka

Implementasi final membutuhkan konfirmasi pada hal berikut:

1. Spesifikasi MCU, sensor sidik jari, protokol, dan firmware.
2. Lokasi penyimpanan serta mekanisme pencocokan template sidik jari.
3. Mekanisme enrollment dan penghapusan biometrik.
4. Dukungan offline queue, RTC, retry, dan sinkronisasi.
5. Kalender kerja, jam istirahat, toleransi, dan perlakuan hari libur.
6. Penentuan scan masuk/pulang dan pembulatan durasi.
7. Tarif lembur dan metode verifikasi.
8. Cutoff payroll dan kebijakan penghasilan saat ketidakhadiran.
9. BPJS, PPh 21, dan potongan lain.
10. Aturan cicilan kasbon.
11. Kewenangan pembukaan kembali payroll.
12. Akses riwayat absensi dan slip gaji untuk karyawan.
13. Custom role dan akses berdasarkan cabang.
14. Hosting, konektivitas jaringan, dan target ketersediaan.
15. Kebijakan retensi, penghapusan, dan perlindungan data biometrik.
16. Format aktual empat laporan payroll.

Daftar pertanyaan dan status penyelesaiannya dikelola pada [`pertanyaan-terbuka.md`](./pertanyaan-terbuka.md).

---

## 9. Dokumen Terkait

- [`README.md`](./README.md)
- [`PRD.md`](./PRD.md)
- [`keputusan.md`](./keputusan.md)
- [`aturan-bisnis.md`](./aturan-bisnis.md)
- [`alur.md`](./alur.md)
- [`arsitektur.md`](./arsitektur.md)
- [`pertanyaan-terbuka.md`](./pertanyaan-terbuka.md)
- [`rencana-pengerjaan.md`](./rencana-pengerjaan.md)
- [`design-system.md`](./design-system.md)