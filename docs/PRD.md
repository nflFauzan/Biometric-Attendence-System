# Product Requirements Document (PRD)
# Biometric Attendance & HR Management System

| Informasi | Detail |
|---|---|
| Nama proyek | Biometric Attendance & HR Management System |
| Jenis sistem | Web-based HR Management System dengan integrasi perangkat absensi biometrik |
| Versi dokumen | 1.0 — Draft |
| Tanggal | 11 Oktober 2026 |
| Status | Draft untuk validasi dan penyelarasan dokumen |
| Target pengguna | Owner, HR/Finance, dan Karyawan |
| Target organisasi | Satu perusahaan dengan tiga cabang |
| Perkiraan karyawan awal | Sekitar 25 orang |
| Bahasa aplikasi | Bahasa Indonesia |
| Zona waktu default | Asia/Jakarta, perlu konfirmasi |
| Status implementasi | Tahap perencanaan dan validasi kebutuhan |

> **Catatan status:** Dokumen ini menjadi dasar kebutuhan produk. Ketentuan yang sudah disepakati dibedakan dari usulan implementasi dan pertanyaan yang belum dijawab. Detail yang belum dikonfirmasi tidak boleh dianggap sebagai keputusan final.

---

## 1. Ringkasan Produk

Biometric Attendance & HR Management System adalah aplikasi berbasis web untuk mengelola data karyawan, absensi sidik jari, aturan kerja, lembur, izin, piutang karyawan, serta perhitungan dan rekap penggajian.

Sistem dirancang untuk menggantikan proses pengambilan data absensi manual dari mesin fingerprint melalui USB dan mengurangi pekerjaan administratif dalam menyusun laporan kehadiran serta penggajian.

Perangkat absensi pada setiap cabang akan mengirimkan data pemindaian ke backend melalui jaringan. Backend memvalidasi dan menyimpan data tersebut, kemudian aplikasi web menggunakannya untuk menampilkan absensi, menghitung durasi kerja dan lembur, serta menyusun rekap penggajian.

Sistem harus mendukung dua periode penggajian:

- **Mingguan:** untuk supir dan kenek.
- **Bulanan:** untuk karyawan lainnya.

Hasil penggajian harus dapat ditelusuri hingga ke data absensi, aturan perhitungan, tarif yang berlaku, lembur, bonus, dan transaksi piutang yang membentuk nominal akhirnya.

## 2. Latar Belakang dan Permasalahan

### 2.1 Kondisi saat ini

Berdasarkan informasi awal dari klien, proses absensi dan penggajian menghadapi beberapa kendala:

1. Data dari mesin fingerprint perlu diambil menggunakan USB.
2. Data absensi tidak langsung tersedia dalam aplikasi untuk dipantau oleh pihak terkait.
3. Rekap kehadiran dan penggajian masih membutuhkan pekerjaan administratif.
4. Perhitungan lembur perlu menggabungkan data absensi dan pemeriksaan manual.
5. Terdapat perbedaan komponen penggajian antara supir/kenek dan karyawan bulanan.
6. Piutang atau kasbon karyawan perlu dicatat dan diperhitungkan dalam penggajian.
7. Perubahan tarif karyawan berpotensi menyebabkan ketidaksesuaian perhitungan ketika periode lama dibuka kembali.

### 2.2 Tujuan solusi

Sistem diharapkan dapat:

- Mengirim dan menyimpan data absensi secara otomatis melalui jaringan.
- Menyediakan pemantauan absensi secara mendekati real-time.
- Menghitung durasi kerja dan lembur berdasarkan aturan yang dikonfigurasi.
- Menggabungkan perhitungan otomatis dengan koreksi dan verifikasi manual.
- Menghasilkan rekap penggajian mingguan dan bulanan sesuai kebutuhan perusahaan.
- Menyediakan pencatatan piutang/kasbon dalam bentuk ledger atau buku transaksi.
- Menjaga konsistensi perhitungan periode lama meskipun tarif atau aturan berubah.
- Menyediakan kontrol akses berdasarkan peran pengguna.
- Memudahkan audit terhadap perubahan data dan hasil perhitungan.

## 3. Tujuan Produk dan Indikator Keberhasilan

### 3.1 Tujuan produk

| ID | Tujuan |
|---|---|
| OBJ-01 | Menghilangkan ketergantungan pada pengambilan data absensi rutin menggunakan USB. |
| OBJ-02 | Menyediakan sumber data absensi terpusat dari tiga cabang. |
| OBJ-03 | Menghasilkan perhitungan penggajian yang dapat ditelusuri dan diverifikasi. |
| OBJ-04 | Mendukung aturan dan komponen penggajian yang berbeda berdasarkan jenis karyawan. |
| OBJ-05 | Menjaga integritas data historis dan menyediakan jejak audit. |
| OBJ-06 | Mengurangi risiko kesalahan administratif dalam rekap kehadiran, lembur, dan piutang. |

### 3.2 Indikator keberhasilan

Indikator berikut merupakan target evaluasi awal, bukan jaminan performa yang sudah teruji.

- Data pemindaian yang berhasil diterima perangkat tersedia di sistem tanpa proses impor USB rutin.
- Data absensi dapat ditelusuri berdasarkan karyawan, cabang, tanggal, dan sumber data.
- Sistem tidak membuat absensi ganda ketika perangkat mengirim ulang data pemindaian yang sama.
- Hasil perhitungan penggajian dapat direkonsiliasi dengan empat format laporan yang digunakan perusahaan.
- Perubahan tarif baru tidak mengubah nominal tarif yang tersimpan pada periode penggajian historis.
- Pengguna hanya dapat mengakses tindakan sesuai dengan hak aksesnya.
- Koreksi terhadap data dan hasil penggajian dapat ditelusuri melalui audit log.

Target angka untuk waktu respons, ketersediaan layanan, dan toleransi keterlambatan sinkronisasi perlu ditentukan setelah spesifikasi perangkat dan hosting dikonfirmasi.

## 4. Ruang Lingkup Produk

### 4.1 Termasuk dalam ruang lingkup

1. Autentikasi dan otorisasi pengguna.
2. Pengelolaan karyawan dan data kepegawaian dasar.
3. Pengelolaan cabang dan perangkat absensi.
4. Pengaturan aturan kerja global dan per karyawan.
5. Penerimaan serta pengelolaan data pemindaian fingerprint.
6. Rekap kehadiran dan koreksi manual yang terkontrol.
7. Pengelolaan izin, sakit, dan cuti sesuai kebijakan yang akan dikonfirmasi.
8. Penghitungan dan verifikasi lembur.
9. Pengelolaan penggajian mingguan dan bulanan.
10. Pengelolaan piutang/kasbon karyawan.
11. Pengelolaan bonus atau insentif.
12. Rekap dan laporan penggajian.
13. Ekspor laporan, minimal ke format yang disepakati.
14. Audit log dan penguncian data historis.
15. Integrasi backend dengan perangkat absensi melalui jaringan.

### 4.2 Di luar ruang lingkup awal atau belum dipastikan

- Akuntansi dan general ledger lengkap.
- Perhitungan pajak penghasilan dan seluruh kewajiban payroll otomatis.
- Integrasi langsung dengan bank untuk transfer gaji.
- Aplikasi mobile native.
- Modul rekrutmen, penilaian kinerja, dan pelatihan.
- Integrasi dengan sistem eksternal lain yang belum disebutkan.
- Sistem multi-perusahaan atau multi-tenant.
- Fitur notifikasi melalui WhatsApp, SMS, atau layanan pihak ketiga.

Fitur di luar ruang lingkup ini dapat dipertimbangkan sebagai pengembangan berikutnya setelah kebutuhan utama tervalidasi.

## 5. Pengguna dan Hak Akses

### 5.1 Peran utama

Sistem memiliki tiga template peran awal.

| Peran | Tanggung jawab utama |
|---|---|
| Owner | Mengelola pengaturan utama, aturan kerja, akun, dan keputusan bisnis penting. Memiliki kewenangan atas bonus sesuai kebijakan perusahaan. |
| HR/Finance | Mengelola data karyawan, memantau absensi, memverifikasi lembur, mengelola piutang, serta menyiapkan dan memproses penggajian sesuai hak akses. |
| Karyawan | Mengakses informasi pribadi yang diizinkan serta mengajukan izin atau cuti apabila fitur tersebut disetujui. |

### 5.2 Prinsip hak akses

- Pengguna harus login sebelum mengakses fitur yang dilindungi.
- Backend menjadi sumber utama penegakan hak akses; pembatasan menu di frontend saja tidak cukup.
- Akses terhadap penggajian, piutang, data biometrik, dan pengaturan harus dibatasi sesuai kebutuhan.
- Tindakan sensitif perlu mencatat pelaku, waktu, objek yang diubah, dan nilai perubahan yang relevan.
- Penghapusan atau penonaktifan karyawan tidak boleh merusak riwayat absensi dan penggajian.
- Template tiga peran di atas merupakan baseline. Kebutuhan pembuatan role khusus dan pengaturan izin fitur per role masih perlu dikonfirmasi.

**Belum diketahui:** apakah Owner dan HR/Finance dapat mengakses seluruh cabang atau perlu pembatasan akses berdasarkan cabang.

## 6. Modul dan Kebutuhan Fungsional

### 6.1 Dashboard

**Tujuan:** Menyajikan ringkasan kondisi absensi dan penggajian sesuai peran pengguna.

Kebutuhan:

- Menampilkan jumlah karyawan aktif.
- Menampilkan ringkasan kehadiran, keterlambatan, ketidakhadiran, dan data absensi bermasalah.
- Menampilkan ringkasan lembur yang menunggu verifikasi.
- Menampilkan status periode penggajian.
- Menampilkan ringkasan piutang dan penggajian sesuai hak akses.
- Menyediakan filter berdasarkan tanggal, periode, atau cabang jika dibutuhkan.

**Kriteria penerimaan:**

- Data dashboard berasal dari data tersimpan di backend.
- Ringkasan mengikuti filter yang dipilih.
- Karyawan tidak dapat melihat informasi gaji atau piutang milik karyawan lain.
- Data yang belum terverifikasi tidak ditampilkan sebagai hasil final tanpa penanda status yang jelas.

### 6.2 Master Data Karyawan

Kebutuhan:

- Menambah, melihat, mengubah, dan menonaktifkan karyawan.
- Menyimpan identitas dan informasi kepegawaian yang diperlukan.
- Menentukan cabang dan status aktif karyawan.
- Menentukan jenis karyawan dan skema penggajian.
- Menyimpan tarif harian, gaji pokok, dan komponen lain yang relevan.
- Mengelola informasi yang diperlukan untuk menghubungkan karyawan dengan identitas pada perangkat fingerprint.
- Menyediakan riwayat perubahan data penting.

**Kriteria penerimaan:**

- Karyawan baru dapat dibuat tanpa menghapus data karyawan lain.
- Karyawan nonaktif tidak dihitung sebagai karyawan aktif, tetapi riwayat transaksinya tetap tersedia.
- Perubahan tarif berlaku sesuai tanggal efektif dan tidak menimpa tarif historis yang telah digunakan.
- Proses pendaftaran atau penghapusan sidik jari mengikuti kemampuan perangkat yang telah dikonfirmasi.

### 6.3 Master Data Cabang dan Perangkat

Kebutuhan:

- Menyimpan data tiga cabang awal.
- Menghubungkan perangkat absensi dengan cabang masing-masing.
- Menyimpan identitas perangkat dan status koneksinya jika protokol perangkat mendukung.
- Mencatat waktu terakhir data diterima dari perangkat.
- Mendeteksi dan mencatat kegagalan penerimaan data.
- Menyediakan pengelolaan kredensial perangkat secara aman.

**Kriteria penerimaan:**

- Data absensi yang diterima dapat diidentifikasi sumber perangkat dan cabangnya.
- Perangkat yang tidak terdaftar atau tidak terautentikasi tidak dapat mengirimkan data sebagai perangkat resmi.
- Kegagalan koneksi tidak menyebabkan data yang sudah tersimpan hilang.
- Kemampuan menyimpan data ketika offline dan mengirimkannya kembali bergantung pada firmware dan spesifikasi perangkat.

### 6.4 Pengaturan Aturan Kerja

Sistem harus menyediakan aturan kerja global dan aturan khusus per karyawan.

Aturan yang dapat dipertimbangkan meliputi:

- Jam masuk dan jam pulang.
- Batas keterlambatan dan toleransi.
- Batas pulang lebih awal.
- Batas mulai perhitungan lembur.
- Tarif harian dan tarif lembur.
- Kebijakan hari kerja dan hari libur.
- Perlakuan terhadap izin, sakit, dan cuti.
- Aturan khusus berdasarkan jenis karyawan.

Aturan per karyawan dapat mengesampingkan aturan global sesuai prioritas yang ditetapkan sistem.

**Prinsip:** aturan dan tarif harus memiliki tanggal efektif. Sistem harus dapat menjelaskan aturan mana yang digunakan untuk menghitung suatu tanggal atau periode.

**Belum diketahui:** kalender hari kerja resmi, ketentuan istirahat, hari libur nasional, toleransi keterlambatan, aturan pembulatan, dan prioritas konflik antara aturan global dengan aturan per karyawan.

### 6.5 Absensi

Kebutuhan:

- Menerima data scan fingerprint dari backend/perangkat.
- Menyimpan data scan asli tanpa mengubah isi historisnya.
- Menghubungkan scan dengan karyawan, perangkat, cabang, dan waktu.
- Menampilkan waktu masuk dan pulang.
- Menghitung keterlambatan, pulang lebih awal, dan durasi kerja sesuai aturan.
- Menggabungkan data perangkat dengan koreksi manual yang memiliki alasan dan identitas pelaku.
- Menandai scan tidak dikenal, scan tidak berpasangan, scan ganda, atau kondisi anomali lain.
- Menyediakan rekap harian dan periode.

**Kriteria penerimaan:**

- Pengiriman ulang scan yang sama tidak menciptakan catatan absensi ganda.
- Koreksi manual tidak menimpa data scan asli.
- Setiap koreksi menyimpan nilai sebelum dan sesudah perubahan, alasan, pelaku, dan waktu.
- Sistem dapat membedakan data asli perangkat, data hasil perhitungan, dan koreksi manual.
- Scan yang bermasalah tidak otomatis dianggap valid tanpa aturan penanganan yang jelas.

### 6.6 Izin, Sakit, dan Cuti

Modul ini disiapkan untuk mengelola pengajuan ketidakhadiran dan dampaknya pada rekap kehadiran serta penggajian.

Kebutuhan awal:

- Mengajukan izin atau cuti apabila fitur disetujui.
- Menyimpan jenis pengajuan, tanggal, alasan, dan status.
- Menyediakan proses persetujuan oleh pihak berwenang.
- Menampilkan status pengajuan kepada pemohon.
- Menghubungkan status yang telah disetujui dengan rekap absensi dan penggajian sesuai kebijakan.

**Belum diketahui:** jenis izin/cuti, kuota, dokumen pendukung, approver, serta apakah sakit dan cuti dibayar atau memengaruhi komponen gaji tertentu.

### 6.7 Pengelolaan Lembur

Sistem menggunakan data scan sebagai dasar perhitungan awal lembur dan memungkinkan pemeriksaan manual.

**Ketentuan yang sudah disepakati:**

- Batas mulai lembur adalah setelah pukul **17.00**.
- Lembur tidak otomatis dimulai hanya karena karyawan telah bekerja selama delapan jam.
- Scan perangkat dan verifikasi/koreksi manual sama-sama menjadi bagian dari proses pengelolaan lembur.
- Perhitungan awal sistem perlu dapat diperiksa sebelum menjadi hasil penggajian final.

Contoh: karyawan masuk pukul 15.00 dan pulang pukul 18.00. Dengan asumsi tidak ada istirahat yang perlu dikurangkan, durasi kerja reguler adalah dua jam dan lembur satu jam.

Kebutuhan:

- Menghitung lembur berdasarkan waktu yang memenuhi aturan.
- Menampilkan dasar perhitungan dan sumber datanya.
- Memungkinkan koreksi atau verifikasi oleh pengguna berwenang.
- Menyimpan alasan koreksi dan riwayat perubahan.
- Menghitung nominal lembur menggunakan tarif yang berlaku.
- Menghubungkan lembur yang telah disetujui dengan periode penggajian yang benar.

**Belum diketahui:** apakah seluruh jenis karyawan berhak lembur, bagaimana menangani scan pulang yang hilang, waktu istirahat, lembur pada hari libur, pembulatan durasi, serta pihak yang memberikan persetujuan akhir.

### 6.8 Pengelolaan Piutang/Kasbon

Piutang karyawan harus dikelola sebagai ledger atau buku transaksi, bukan hanya sebagai satu angka potongan yang dapat diganti sembarangan.

Kebutuhan:

- Mencatat transaksi piutang/kasbon.
- Mencatat pengurangan atau pembayaran piutang.
- Menampilkan saldo piutang per karyawan.
- Menampilkan riwayat transaksi dan perubahan saldo.
- Menghubungkan potongan piutang dengan periode penggajian.
- Menampilkan saldo awal, transaksi, potongan, dan saldo akhir.
- Menjaga agar satu potongan payroll tidak tercatat dua kali.

**Kriteria penerimaan:**

- Saldo dapat direkonsiliasi dari transaksi ledger.
- Setiap transaksi memiliki tanggal, nominal, jenis, pelaku, dan referensi yang relevan.
- Perubahan atau pembatalan transaksi memiliki jejak audit.
- Potongan yang telah diproses dalam periode penggajian tidak boleh digandakan ketika periode dibuka kembali.

**Belum diketahui:** apakah pelunasan harus sekaligus atau dapat dicicil, batas maksimum potongan, dan siapa yang berwenang menyetujui transaksi piutang.

### 6.9 Bonus atau Insentif

Kolom insentif pada format penggajian yang ada diperlakukan sebagai **bonus yang nominalnya ditentukan oleh Owner**.

Kebutuhan:

- Menyimpan bonus untuk karyawan dan periode terkait.
- Mencatat pihak yang menetapkan bonus, waktu penetapan, dan alasan jika diwajibkan.
- Memasukkan bonus ke komponen penggajian yang sesuai.
- Menampilkan bonus sebagai komponen terpisah pada detail penggajian.
- Menyimpan nominal bonus historis ketika periode telah dihitung.

Tidak ada formula bonus otomatis yang diasumsikan pada versi awal.

**Belum diketahui:** apakah bonus berlaku untuk semua jenis karyawan, apakah bonus bisa direvisi sebelum finalisasi, dan apakah diperlukan proses persetujuan tambahan.

### 6.10 Penggajian

Sistem harus mendukung dua skema penggajian dengan komponen yang berbeda.

#### A. Supir dan Kenek — Mingguan

Komponen yang perlu ditampilkan:

- Identitas karyawan.
- Rekap kehadiran harian.
- Jumlah hari kerja yang diperhitungkan.
- Tarif harian.
- Total upah harian.
- Durasi dan nominal lembur.
- Piutang/kasbon dan potongan yang disetujui.
- Bonus jika diberikan.
- Total upah sebelum potongan.
- Total potongan.
- Gaji bersih yang harus dibayarkan.

Rumus konseptual:

`Upah harian + lembur + bonus (jika ada) - potongan yang disetujui`

Upah harian dihitung berdasarkan hari yang memenuhi aturan pembayaran, bukan sekadar jumlah scan tanpa validasi.

#### B. Karyawan Lain — Bulanan

Komponen yang perlu ditampilkan:

- Identitas karyawan.
- Rekap kehadiran harian.
- Jumlah hari kerja yang diperhitungkan.
- Gaji pokok.
- Upah harian terpisah dari gaji pokok.
- Durasi dan nominal lembur.
- Piutang/kasbon dan potongan yang disetujui.
- Bonus jika diberikan.
- Total penghasilan.
- Total potongan.
- Gaji bersih yang harus dibayarkan.

Rumus konseptual:

`Gaji pokok + upah harian + lembur + bonus (jika ada) - potongan yang disetujui`

**Penting:** upah harian merupakan komponen terpisah dari gaji pokok untuk skema bulanan. Cara menghitung dan membayar setiap komponen tetap harus mengikuti aturan yang dikonfirmasi klien. BPJS, PPh 21, dan potongan lain tidak boleh dihitung otomatis sebelum komponen serta kebijakannya disepakati.

#### C. Siklus penggajian

Status yang diusulkan:

1. **Draft:** periode sedang disiapkan.
2. **Review:** hasil perhitungan menunggu pemeriksaan.
3. **Final:** hasil telah disetujui dan dikunci.
4. **Reopened:** periode final dibuka kembali melalui tindakan yang berwenang.
5. **Final kembali:** hasil koreksi telah diperiksa dan difinalisasi ulang.

Nama status dapat diselaraskan pada tahap desain. Yang wajib adalah perbedaan antara data yang belum diverifikasi dan hasil final.

### 6.11 Laporan dan Ekspor

Sistem harus dapat menghasilkan empat format laporan utama berdasarkan dokumen/formulir perusahaan yang telah menjadi acuan:

1. Rekap absensi dan penggajian mingguan supir/kenek.
2. Rekap lembur supir/kenek.
3. Rekap absensi dan penggajian bulanan karyawan.
4. Rekap lembur bulanan karyawan.

Setiap laporan perlu menyediakan detail yang cukup untuk menelusuri total nominal hingga ke sumber perhitungannya.

Fitur yang diharapkan:

- Filter periode dan karyawan.
- Filter cabang jika dibutuhkan.
- Tampilan ringkasan dan detail.
- Ekspor Excel.
- Format PDF atau cetak jika disepakati.
- Penanda status draft, review, final, atau reopened.
- Total per karyawan dan total keseluruhan sesuai format laporan.

**Kriteria penerimaan:** hasil ekspor harus konsisten dengan detail yang ditampilkan pada aplikasi dan menggunakan tarif historis yang sesuai dengan periode tersebut.

### 6.12 Audit Log

Audit log diperlukan untuk tindakan yang memengaruhi data dan hasil penggajian.

Minimal mencakup:

- Perubahan data karyawan penting.
- Perubahan aturan dan tarif.
- Koreksi absensi.
- Koreksi/verifikasi lembur.
- Transaksi piutang.
- Perubahan bonus.
- Perhitungan, finalisasi, pembukaan kembali, dan finalisasi ulang payroll.
- Perubahan hak akses dan akun.

Audit log harus menyimpan pelaku, waktu, tindakan, objek terkait, dan informasi perubahan yang relevan. Data scan asli tidak boleh diubah melalui proses koreksi rutin.

## 7. Aturan Bisnis Utama

| ID | Aturan | Status |
|---|---|---|
| BR-01 | Perusahaan memiliki tiga cabang awal dan satu sistem terpusat. | Disepakati |
| BR-02 | Sistem memiliki tiga template peran awal: Owner, HR/Finance, dan Karyawan. | Disepakati |
| BR-03 | Supir/kenek menggunakan penggajian mingguan. | Disepakati |
| BR-04 | Karyawan lainnya menggunakan penggajian bulanan. | Disepakati |
| BR-05 | Penggajian bulanan memisahkan gaji pokok dan upah harian. | Disepakati |
| BR-06 | Batas mulai lembur adalah setelah pukul 17.00, bukan setelah delapan jam kerja. | Disepakati |
| BR-07 | Perhitungan lembur menggunakan data scan dan dapat diverifikasi atau dikoreksi secara manual. | Disepakati |
| BR-08 | Piutang/kasbon dicatat melalui ledger transaksi. | Disepakati |
| BR-09 | Bonus ditentukan nominalnya oleh Owner. | Disepakati |
| BR-10 | Tarif dan aturan memiliki tanggal efektif. | Disepakati sebagai prinsip |
| BR-11 | Tarif historis tidak boleh berubah ketika periode lama dibuka kembali. | Disepakati |
| BR-12 | Scan asli harus tetap tersedia meskipun terdapat koreksi manual. | Disepakati |
| BR-13 | Upah harian perlu diperhitungkan untuk hari yang memenuhi aturan pembayaran; keputusan pembayaran mengikuti kewenangan yang ditetapkan. | Keputusan awal, perlu validasi detail |
| BR-14 | Aturan kerja dapat ditetapkan secara global maupun per karyawan. | Disepakati |
| BR-15 | Finalisasi payroll harus memiliki kontrol perubahan dan jejak audit. | Disepakati sebagai prinsip |
| BR-16 | BPJS dan PPh 21 hanya dihitung otomatis setelah kebijakan dan komponennya dikonfirmasi. | Batasan implementasi |

### 7.1 Tarif dan aturan historis

Sistem wajib menjaga histori tarif berdasarkan tanggal efektif dan periode penggajian.

Contoh:

- Tarif harian karyawan pada September adalah Rp80.000.
- Tarif berubah menjadi Rp100.000 mulai Oktober.
- Payroll September telah dihitung atau difinalisasi.
- September dibuka kembali pada November untuk memperbaiki data absensi.

Perhitungan ulang September harus tetap menggunakan tarif Rp80.000 apabila tarif itulah yang berlaku pada periode tersebut. Tarif Rp100.000 digunakan untuk tanggal atau periode yang memenuhi tanggal efektifnya.

Implementasi yang disarankan:

1. Setiap tarif dan aturan memiliki tanggal mulai berlaku serta, jika diperlukan, tanggal akhir berlaku.
2. Perhitungan payroll menyimpan snapshot komponen, tarif, aturan, dan hasil perhitungan.
3. Pembukaan kembali tidak menghapus snapshot historis.
4. Perubahan data dilakukan melalui proses koreksi yang dapat diaudit.
5. Setelah diperiksa, periode dapat difinalisasi ulang dengan versi hasil perhitungan yang baru.
6. Sistem harus dapat membandingkan hasil sebelum dan sesudah koreksi.

Snapshot historis adalah kebutuhan bisnis. Bentuk implementasi teknisnya dapat berupa snapshot perhitungan atau mekanisme versioning yang setara.

## 8. Arsitektur dan Integrasi Sistem

### 8.1 Alur arsitektur

```text
Mesin Fingerprint Cabang 1 ─┐
Mesin Fingerprint Cabang 2 ─┼── Wi-Fi / Jaringan
Mesin Fingerprint Cabang 3 ─┘
              │
              ▼
        Backend API
              │
      Validasi dan autentikasi
              │
       Database PostgreSQL
              │
     ┌────────┴─────────┐
     ▼                  ▼
  Web Application    Payroll Engine
     │                  │
     └────────┬─────────┘
              ▼
      Laporan dan Rekap
```

Mesin mengirimkan data ke backend. Aplikasi web membaca data melalui backend, bukan mengakses mesin secara langsung.

### 8.2 Prinsip integrasi perangkat

- Setiap perangkat memiliki identitas dan autentikasi sendiri.
- Data scan mempunyai identitas unik agar pengiriman ulang tidak menghasilkan duplikasi.
- Backend memberikan acknowledgment berhasil hanya setelah data berhasil disimpan secara persisten.
- Kegagalan jaringan harus menghasilkan perilaku retry yang aman.
- Jika perangkat mendukung penyimpanan offline, data yang belum terkirim harus dapat dikirim kembali setelah koneksi pulih.
- Waktu perangkat perlu dapat diverifikasi atau disinkronkan.
- Koneksi ke server menggunakan kanal yang aman.
- Kegagalan sinkronisasi harus dapat didiagnosis melalui log/status perangkat.

**Ketergantungan hardware:** kemampuan antrean offline, RTC, protokol komunikasi, kapasitas penyimpanan, mekanisme enrollment, dan pengelolaan template sidik jari belum dianggap tersedia sebelum dikonfirmasi dengan perancang perangkat.

### 8.3 Stack teknologi

Stack yang direncanakan:

- Backend: Java LTS dan Spring Boot.
- Database: PostgreSQL.
- Migrasi database: Flyway.
- Frontend: React, Vite, dan TypeScript.
- API: REST dengan dokumentasi OpenAPI.
- Autentikasi dan otorisasi: Spring Security.
- Pembaruan informasi: SSE jika diperlukan.
- Ekspor Excel: Apache POI.
- Pengujian: JUnit 5, MockMvc, dan Testcontainers sesuai kebutuhan.
- Deployment: Docker Compose dan reverse proxy dengan TLS.

Stack ini merupakan keputusan arsitektur awal dan perlu diselaraskan dengan dokumen arsitektur yang berlaku.

## 9. Kebutuhan Nonfungsional

### 9.1 Keamanan

- Password disimpan dalam bentuk hash yang aman.
- Endpoint yang dilindungi wajib memeriksa autentikasi dan otorisasi.
- Kredensial perangkat tidak boleh disimpan dalam frontend atau repository.
- Rahasia deployment disimpan melalui konfigurasi lingkungan yang sesuai.
- Data payroll dan piutang dibatasi berdasarkan hak akses.
- Akses dan perubahan data sensitif dicatat.
- Penggunaan data biometrik harus mengikuti ketentuan perlindungan data yang berlaku, termasuk dasar pemrosesan, akses, retensi, dan penghapusan yang sesuai.

### 9.2 Integritas data

- Data scan asli tidak dapat diedit melalui alur koreksi biasa.
- Transaksi penting memiliki identitas dan riwayat perubahan.
- Operasi yang berpotensi dikirim ulang harus aman terhadap duplikasi.
- Perhitungan uang menggunakan tipe desimal yang tepat, bukan floating-point biasa.
- Perhitungan payroll dapat direproduksi dari input, aturan, dan snapshot yang tersimpan.
- Pembukaan kembali periode tidak boleh merusak data historis.

### 9.3 Performa dan ketersediaan

- Dashboard dan tabel harus tetap dapat digunakan pada jumlah karyawan serta transaksi yang bertambah.
- Sinkronisasi perangkat tidak boleh bergantung pada halaman web sedang terbuka.
- Kegagalan satu perangkat tidak boleh menghentikan penerimaan data dari perangkat lain.
- Backup database dan prosedur pemulihan perlu disiapkan sebelum produksi.
- Target ketersediaan, response time, kapasitas transaksi, dan RPO/RTO ditetapkan setelah kebutuhan hosting dikonfirmasi.

### 9.4 Usability

- Antarmuka berbahasa Indonesia.
- Status data dan proses harus jelas.
- Data hasil scan, koreksi manual, dan hasil perhitungan harus dapat dibedakan.
- Pengguna dapat melihat dasar perhitungan tanpa perlu memeriksa database secara langsung.
- Tindakan berisiko seperti finalisasi dan pembukaan kembali payroll memerlukan konfirmasi yang jelas.

## 10. Kebutuhan Data Utama

Entitas berikut merupakan rancangan konseptual. Struktur tabel final ditentukan pada dokumen desain data.

| Entitas | Fungsi |
|---|---|
| User | Akun untuk mengakses sistem |
| Role & Permission | Peran dan izin fitur |
| Employee | Data karyawan |
| Branch | Data cabang |
| Attendance Device | Identitas dan konfigurasi perangkat |
| Device Credential | Kredensial perangkat yang dikelola secara aman |
| Fingerprint Mapping | Pemetaan identitas karyawan ke perangkat, sesuai mekanisme hardware |
| Raw Attendance Scan | Data scan asli dari perangkat |
| Attendance Record | Rekap atau interpretasi absensi |
| Attendance Correction | Koreksi manual beserta audit |
| Work Rule | Aturan kerja global dan per karyawan |
| Effective-Dated Rate | Tarif dengan tanggal efektif |
| Leave Request | Pengajuan dan status izin/cuti |
| Overtime Record | Durasi, tarif, dan nominal lembur |
| Overtime Verification | Status dan riwayat verifikasi lembur |
| Debt Transaction | Transaksi piutang/kasbon |
| Bonus | Bonus yang ditetapkan untuk karyawan/periode |
| Payroll Period | Periode penggajian |
| Payroll Result | Hasil penggajian per karyawan |
| Payroll Snapshot | Tarif, aturan, komponen, dan hasil perhitungan yang digunakan |
| Payroll Adjustment | Koreksi terhadap hasil penggajian |
| Audit Log | Riwayat tindakan dan perubahan |

Data biometrik mentah atau template sidik jari tidak boleh disimpan di server hanya karena tersedia entitas pemetaan. Lokasi penyimpanan dan mekanisme pencocokan biometrik ditentukan setelah arsitektur hardware dipastikan.

## 11. Alur Utama Sistem

### 11.1 Absensi

1. Karyawan melakukan pemindaian sidik jari.
2. Perangkat mengenali pemindaian sesuai kemampuan hardware.
3. Perangkat mengirimkan event scan ke backend.
4. Backend memvalidasi perangkat, format, identitas, dan duplikasi.
5. Backend menyimpan scan asli.
6. Backend mengirimkan acknowledgment setelah penyimpanan berhasil.
7. Sistem memproses scan menjadi data absensi.
8. Pengguna berwenang meninjau data anomali dan melakukan koreksi jika diperlukan.
9. Data absensi yang tervalidasi menjadi input untuk lembur dan payroll.

### 11.2 Lembur

1. Sistem membaca scan dan aturan lembur yang berlaku.
2. Sistem menghitung kandidat durasi lembur berdasarkan batas pukul 17.00.
3. Sistem menampilkan dasar perhitungan.
4. HR/Finance atau pihak berwenang memeriksa hasil.
5. Koreksi dicatat bersama alasan dan pelakunya.
6. Lembur yang disetujui dimasukkan ke perhitungan payroll sesuai periode.

### 11.3 Penggajian

1. Pengguna memilih jenis dan periode penggajian.
2. Sistem mengambil data absensi, tarif historis, lembur, bonus, piutang, dan komponen relevan.
3. Sistem menghitung nominal awal.
4. Pengguna meninjau anomali, komponen, dan total.
5. Koreksi yang diperlukan dilakukan melalui proses terkontrol.
6. Pengguna berwenang memfinalisasi periode.
7. Sistem menyimpan hasil dan snapshot historis.
8. Laporan dapat ditampilkan atau diekspor.
9. Jika koreksi setelah finalisasi diperlukan, periode dibuka kembali sesuai kewenangan dan prosedur audit.
10. Setelah koreksi diverifikasi, periode difinalisasi ulang.

### 11.4 Perubahan karyawan

1. Pengguna berwenang membuat atau mengubah data karyawan.
2. Sistem menyimpan histori perubahan penting.
3. Jika perubahan memengaruhi tarif, tanggal efektif harus ditentukan.
4. Jika karyawan dinonaktifkan, histori absensi dan payroll tetap dipertahankan.
5. Jika diperlukan perubahan pada perangkat fingerprint, proses enrollment atau penghapusan dilakukan berdasarkan kemampuan perangkat yang telah dipastikan.

## 12. Kriteria Penerimaan Tingkat Produk

Produk dinyatakan memenuhi kebutuhan inti jika skenario berikut berhasil diuji:

| ID | Skenario | Hasil yang diharapkan |
|---|---|---|
| AC-01 | Perangkat mengirim scan valid | Scan tersimpan dan dapat dilihat melalui aplikasi |
| AC-02 | Perangkat mengirim scan yang sama dua kali | Hanya satu event logis yang digunakan dalam perhitungan |
| AC-03 | Perangkat kehilangan koneksi | Data yang sudah tersimpan tetap aman; perilaku data offline mengikuti kemampuan perangkat |
| AC-04 | HR mengoreksi absensi | Koreksi tercatat tanpa menghapus scan asli |
| AC-05 | Karyawan masuk 15.00 dan pulang 18.00 | Dengan asumsi tidak ada pengurangan istirahat, sistem menghitung dua jam kerja reguler dan satu jam lembur |
| AC-06 | Tarif berubah dari Rp80.000 menjadi Rp100.000 mulai Oktober | Periode September tetap menggunakan Rp80.000 |
| AC-07 | Periode September dibuka kembali pada November | Tarif historis September tetap digunakan |
| AC-08 | Piutang karyawan dicatat dan dipotong melalui payroll | Saldo ledger dan potongan payroll dapat direkonsiliasi |
| AC-09 | Owner menetapkan bonus | Bonus tampil sebagai komponen terpisah dan masuk ke perhitungan sesuai kebijakan |
| AC-10 | Payroll bulanan dihitung | Gaji pokok dan upah harian ditampilkan sebagai komponen terpisah |
| AC-11 | Payroll final dibuka kembali | Tindakan tercatat, snapshot lama tetap tersedia, dan hasil baru dapat dibandingkan |
| AC-12 | Pengguna tanpa izin mencoba mengakses payroll | Backend menolak akses |
| AC-13 | Laporan diekspor | Nominal dan komponen sesuai dengan data yang tampil di sistem |
| AC-14 | Karyawan dinonaktifkan | Data historis tetap tersedia dan tidak terhapus |

## 13. Risiko dan Ketergantungan

| Risiko | Dampak | Mitigasi awal |
|---|---|---|
| Spesifikasi perangkat belum final | Integrasi dan enrollment dapat berubah | Konfirmasi sensor, mikrokontroler, firmware, protokol, dan pemilik pengembangan |
| Koneksi cabang tidak stabil | Data absensi terlambat diterima | Tentukan antrean offline, retry, dan RTC berdasarkan kemampuan hardware |
| Aturan payroll belum lengkap | Hasil penggajian dapat berbeda dari perhitungan perusahaan | Validasi aturan melalui contoh kasus dan laporan aktual |
| Perubahan tarif historis | Gaji periode lama menjadi tidak konsisten | Effective dating, snapshot payroll, dan audit |
| Scan tidak lengkap atau ganda | Durasi kerja dan lembur salah | Validasi, deteksi anomali, serta koreksi manual terkontrol |
| Definisi potongan belum lengkap | Gaji bersih salah | Validasi piutang, BPJS, pajak, dan potongan lain |
| Perbedaan hak akses | Kebocoran informasi atau perubahan tidak sah | Matriks izin dan pengujian otorisasi |
| Data biometrik sensitif | Risiko privasi dan kepatuhan | Minimalkan penyimpanan, batasi akses, dan dokumentasikan tata kelola data |
| Data formulir belum terstruktur | Laporan aplikasi tidak sesuai kebiasaan kerja | Gunakan empat formulir aktual sebagai baseline rekonsiliasi |

## 14. Pertanyaan yang Masih Memerlukan Validasi

### 14.1 Aturan kerja dan absensi

- Apakah hari kerja resmi Senin–Sabtu?
- Bagaimana aturan jam istirahat dan pengurangannya dari durasi kerja?
- Berapa toleransi keterlambatan dan bagaimana pembulatannya?
- Bagaimana perlakuan hari libur nasional dan hari Minggu?
- Bagaimana menangani scan masuk atau pulang yang hilang?
- Apakah karyawan boleh melakukan absensi dari cabang lain?
- Apakah semua kategori karyawan mengikuti batas lembur pukul 17.00?

### 14.2 Payroll

- Bagaimana tanggal mulai dan akhir periode mingguan dan bulanan?
- Bagaimana aturan pembulatan nominal rupiah dan durasi lembur?
- Apakah lembur pada hari libur memiliki tarif khusus?
- Bagaimana upah harian dihitung untuk karyawan bulanan dan bagaimana hubungannya dengan gaji pokok?
- Bagaimana perlakuan izin, sakit, dan cuti terhadap gaji?
- Komponen BPJS, PPh 21, atau potongan lain apa yang perlu dihitung?
- Apakah piutang dipotong sekaligus atau dapat dicicil?
- Siapa yang boleh membuka kembali periode final?
- Apakah pembukaan kembali memerlukan alasan atau persetujuan tambahan?
- Apakah bonus hanya ditetapkan Owner, dan apakah ada proses persetujuan atau batas nominal?

### 14.3 Peran dan akses

- Apakah perusahaan membutuhkan pembuatan role khusus dan izin per fitur?
- Apakah akses Owner dan HR/Finance dibatasi per cabang?
- Apakah karyawan dapat melihat riwayat absensi dan slip gaji sendiri?
- Apakah pengajuan izin/cuti dilakukan melalui aplikasi?

### 14.4 Hardware dan deployment

- Mikrokontroler, sensor, dan protokol komunikasi apa yang digunakan?
- Di mana template sidik jari disimpan dan di mana proses pencocokan berlangsung?
- Bagaimana enrollment dan penghapusan fingerprint dilakukan?
- Apakah perangkat mempunyai penyimpanan offline, RTC, dan mekanisme retry?
- Bagaimana server akan di-host dan bagaimana konektivitas tiap cabang disediakan?
- Apa target ketersediaan dan pemulihan data?
- Apa kebijakan retensi data biometrik dan bagaimana persetujuan serta penghapusan data dikelola?

Pertanyaan yang sudah dijawab klien perlu dipindahkan dari daftar terbuka ke dokumen keputusan dan aturan bisnis. Jangan mempertahankan pertanyaan yang sama sebagai hal belum diketahui jika jawabannya sudah disepakati.

## 15. Strategi Pengujian

Pengujian produk harus mengutamakan akurasi penggajian dan integritas data, bukan hanya keberhasilan halaman atau API.

### 15.1 Pengujian fungsional

- Login, hak akses, dan pembatasan fitur.
- CRUD data karyawan dan cabang.
- Penerimaan scan perangkat.
- Deteksi duplikasi dan scan anomali.
- Koreksi absensi dan audit log.
- Perhitungan lembur.
- Pengelolaan piutang dan saldo.
- Penetapan bonus.
- Payroll mingguan dan bulanan.
- Finalisasi dan pembukaan kembali payroll.
- Ekspor laporan.

### 15.2 Pengujian perhitungan

Gunakan contoh data yang telah diverifikasi oleh klien untuk menguji:

- Hari kerja dan jumlah kehadiran.
- Tarif harian.
- Gaji pokok.
- Durasi dan nominal lembur.
- Bonus.
- Piutang dan potongan.
- Total penghasilan dan gaji bersih.
- Perubahan tarif dengan tanggal efektif.
- Perhitungan ulang setelah periode dibuka kembali.

### 15.3 Regression test berbasis formulir

Empat formulir aktual perusahaan harus menjadi baseline pengujian:

1. Laporan mingguan supir/kenek.
2. Laporan lembur supir/kenek.
3. Laporan bulanan karyawan.
4. Laporan lembur bulanan karyawan.

Setiap komponen dan total pada formulir harus memiliki pemetaan ke sumber data dan aturan perhitungan di sistem. Perbedaan hasil harus dapat dijelaskan sebelum sistem dinyatakan siap digunakan untuk payroll nyata.

## 16. Definisi Selesai (Definition of Done)

Fitur dianggap selesai apabila:

- Kebutuhan dan aturan bisnisnya terdokumentasi.
- Hak akses backend sudah diterapkan dan diuji.
- Validasi input dan penanganan error tersedia.
- Data penting memiliki jejak audit sesuai kebutuhan.
- Pengujian unit dan integrasi yang relevan berhasil.
- Perhitungan uang menggunakan presisi yang sesuai.
- Migrasi database tersedia jika struktur data berubah.
- Dokumentasi API dan penggunaan diperbarui.
- Tidak ada regresi pada alur yang sudah disepakati.
- Untuk fitur payroll, hasilnya cocok dengan contoh perhitungan yang telah divalidasi klien.

## 17. Dokumen Turunan

PRD ini menjadi referensi utama untuk dokumen berikut:

| Dokumen | Tanggung jawab |
|---|---|
| `docs/README.md` | Indeks dokumen, status keputusan, dan tautan referensi |
| `docs/keputusan.md` | Keputusan teknologi, produk, dan batasan yang telah disepakati |
| `docs/pertanyaan-terbuka.md` | Pertanyaan yang masih menunggu jawaban |
| `docs/aturan-bisnis.md` | Formula, tarif, periode, dan aturan operasional |
| `docs/kebutuhan.md` | Kebutuhan fungsional dan matriks hak akses terperinci |
| `docs/alur.md` | Alur absensi, izin, lembur, piutang, payroll, dan koreksi |
| `docs/arsitektur.md` | Arsitektur aplikasi, integrasi perangkat, data, keamanan, dan deployment |
| `docs/design-system.md` | Prinsip UI/UX, komponen, tabel, status, dan pola interaksi |
| `docs/rencana-pengerjaan.md` | Fase implementasi, dependensi, pengujian, dan kriteria penyelesaian |

Jika ada konflik antara dokumen, keputusan yang telah dikonfirmasi klien menjadi acuan. Perubahan kebutuhan harus diperbarui di PRD dan dokumen terkait agar tidak ada aturan berbeda antarbagian.

---

## 18. Status Validasi

| Area | Status |
|---|---|
| Tujuan sistem dan kebutuhan utama | Berdasarkan diskusi awal |
| Tiga cabang dan perkiraan karyawan | Berdasarkan informasi awal klien |
| Peran dasar | Disepakati sebagai baseline |
| Payroll mingguan dan bulanan | Disepakati |
| Lembur setelah pukul 17.00 | Disepakati |
| Piutang berbasis ledger | Disepakati |
| Bonus ditentukan Owner | Disepakati |
| Tarif historis tetap saat periode dibuka kembali | Disepakati |
| Formula payroll secara lengkap | Perlu validasi |
| Detail aturan kerja, cuti, dan potongan | Perlu validasi |
| Spesifikasi hardware dan offline mode | Perlu konfirmasi |
| Hosting, SLA, dan retensi data | Perlu konfirmasi |
| Matriks izin final dan role khusus | Perlu konfirmasi |

**Langkah berikutnya:** validasi PRD ini, kemudian selaraskan `docs/keputusan.md`, `docs/pertanyaan-terbuka.md`, dan `docs/aturan-bisnis.md` sebelum melanjutkan perubahan pada alur, arsitektur, dan rencana implementasi.
