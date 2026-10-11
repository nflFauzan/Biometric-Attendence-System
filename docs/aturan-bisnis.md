# Aturan Bisnis

## 1. Tujuan Dokumen

Dokumen ini mendefinisikan aturan bisnis untuk sistem **Biometric Attendance & HR Management System**, khususnya pengelolaan jadwal kerja, absensi, keterlambatan, lembur, upah, payroll, kasbon, dan perubahan aturan perusahaan.

Dokumen ini menjadi acuan bagi Business Analyst, Software Architect, Developer, QA, dan pihak klien selama proses pengembangan serta pengujian sistem.

### 1.1 Status Aturan

Setiap aturan harus dipahami berdasarkan statusnya:

- **Disepakati** — keputusan yang sudah ditetapkan dan menjadi acuan implementasi.
- **Nilai awal** — nilai konfigurasi awal yang dapat berubah sesuai kebijakan perusahaan.
- **Usulan** — pendekatan yang direncanakan, tetapi masih perlu validasi.
- **Belum dikonfirmasi** — keputusan yang harus diperoleh dari klien sebelum implementasi final.

Prinsip utama: aturan kerja dan perhitungan gaji harus dikelola melalui sistem konfigurasi, bukan ditanam sebagai angka tetap di dalam kode program.

---

## 2. Prinsip Umum

1. Sistem digunakan oleh satu perusahaan dengan beberapa cabang.
2. Sistem menyediakan tiga kategori pengguna awal: Owner, HR/Finance, dan Karyawan.
3. Owner dapat mengatur aturan kerja secara global maupun khusus per karyawan.
4. Aturan dan tarif dapat memiliki tanggal mulai berlaku.
5. Perubahan aturan tidak boleh mengubah hasil payroll historis yang sudah dihitung dan disimpan.
6. Data scan asli dari mesin absensi harus dipertahankan dan tidak boleh ditimpa oleh koreksi manual.
7. Koreksi absensi, perubahan komponen gaji, dan perubahan status payroll harus tercatat dalam audit log.
8. Sistem menghasilkan usulan perhitungan berdasarkan aturan yang berlaku. Pihak yang berwenang tetap dapat melakukan verifikasi atau koreksi melalui prosedur yang ditentukan.
9. Seluruh perhitungan payroll harus dapat ditelusuri kembali ke data absensi, aturan, tarif, dan komponen yang digunakan.

---

## 3. Aturan Jadwal Kerja

### 3.1 Nilai Jadwal Awal

| Parameter | Nilai awal | Status |
|---|---|---|
| Jam masuk | 08.00 | Acuan awal |
| Jam pulang normal | 17.00 | Acuan awal |
| Jam istirahat | Belum ditetapkan secara final | Perlu konfirmasi |
| Hari kerja | Senin–Sabtu | Usulan, perlu konfirmasi |
| Toleransi keterlambatan | Dapat dikonfigurasi | Nilai awal 0 menit |
| Target jam kerja harian | 8 jam | Acuan awal |
| Target jam kerja bulanan | 200 jam | Belum dikonfirmasi |
| Sistem shift | Tidak menggunakan shift pada kebutuhan awal | Acuan awal |

Nilai di atas tidak boleh dianggap sebagai kebijakan final jika statusnya masih berupa usulan atau menunggu konfirmasi.

### 3.2 Pengaturan Jadwal

Sistem perlu mendukung pengaturan jadwal pada dua tingkat:

- **Aturan global atau kategori karyawan:** berlaku sebagai konfigurasi umum.
- **Aturan khusus karyawan:** dapat digunakan ketika seorang karyawan memiliki ketentuan berbeda.

Jika tersedia aturan khusus karyawan, sistem harus memiliki mekanisme prioritas yang jelas agar aturan yang berlaku dapat ditentukan secara konsisten.

Perubahan jadwal harus memiliki tanggal mulai berlaku. Jadwal yang berlaku pada periode lampau harus tetap dapat ditelusuri.

### 3.3 Jam Kerja Normal

Untuk perhitungan awal, jam kerja normal dimulai pada pukul 08.00 dan berakhir pada pukul 17.00.

Scan sebelum jam masuk tidak otomatis menambah jam kerja normal. Waktu kerja normal dihitung berdasarkan rentang jadwal yang berlaku dan aturan istirahat perusahaan.

Pengurangan waktu istirahat harus mengikuti konfigurasi yang sudah disetujui. Sistem tidak boleh mengasumsikan durasi atau pengurangan istirahat yang belum dikonfirmasi.

### 3.4 Hari Libur

Sistem perlu mendukung kalender hari kerja dan hari libur.

Kebijakan untuk hari Minggu, hari libur nasional, hari libur perusahaan, serta pekerjaan khusus pada hari libur masih perlu dikonfirmasi.

Sebelum kebijakan tersebut ditetapkan, developer tidak boleh menganggap seluruh pekerjaan pada hari libur otomatis menggunakan satu rumus lembur yang sama.

---

## 4. Aturan Absensi

### 4.1 Sumber Data Absensi

Sumber data absensi utama adalah scan sidik jari pada perangkat yang terhubung ke backend melalui jaringan.

Alur umum:

1. Karyawan melakukan scan sidik jari.
2. Perangkat mengirimkan data scan ke backend.
3. Backend memvalidasi perangkat, identitas, dan data yang diterima.
4. Backend menyimpan data scan secara persisten.
5. Sistem memproses data scan menjadi catatan kehadiran.
6. Dashboard dan laporan menampilkan hasil pemrosesan sesuai hak akses pengguna.

Data scan harus memiliki identitas kejadian yang memungkinkan sistem mengenali pengiriman ulang agar tidak menghasilkan absensi ganda.

### 4.2 Data Scan Asli dan Hasil Pemrosesan

Sistem harus membedakan:

- **Raw scan:** catatan scan asli yang diterima dari perangkat.
- **Attendance record:** hasil interpretasi scan menjadi jam masuk, jam pulang, dan status kehadiran.
- **Attendance correction:** koreksi yang dilakukan oleh pengguna berwenang.

Raw scan tidak boleh diubah atau dihapus hanya karena terjadi koreksi pada catatan kehadiran.

Apabila koreksi dilakukan, sistem harus menyimpan nilai sebelum koreksi, nilai setelah koreksi, alasan, pengguna yang melakukan koreksi, dan waktu perubahan.

### 4.3 Penentuan Scan Masuk dan Pulang

Usulan awal adalah menggunakan scan pertama sebagai kandidat jam masuk dan scan terakhir sebagai kandidat jam pulang dalam satu hari kerja.

Namun, aturan ini harus divalidasi terhadap perilaku mesin, scan berulang, scan lintas tengah malam, dan kemungkinan scan yang tidak lengkap.

Jika hanya terdapat satu scan atau urutan scan tidak wajar, sistem harus menandai catatan sebagai tidak lengkap atau perlu ditinjau. Sistem tidak boleh mengarang jam masuk atau pulang yang tidak tersedia.

### 4.4 Keterlambatan

Rumus dasar keterlambatan:

`Keterlambatan = max(0, Jam Masuk Aktual - (Jam Masuk Jadwal + Toleransi))`

Contoh dengan jam masuk pukul 08.00 dan toleransi 0 menit:

- Scan masuk pukul 07.50: keterlambatan 0 menit.
- Scan masuk pukul 08.00: keterlambatan 0 menit.
- Scan masuk pukul 08.10: keterlambatan 10 menit.

Keterlambatan dicatat sebagai informasi absensi dan bahan evaluasi HR.

**Potongan gaji otomatis karena terlambat tidak boleh diterapkan sebelum kebijakannya dikonfirmasi.** Apabila kebijakan potongan disetujui kemudian, aturan dan perhitungannya harus ditetapkan secara terpisah.

### 4.5 Pulang Lebih Awal

Pulang sebelum jam pulang normal harus dapat teridentifikasi pada catatan absensi.

Sistem dapat menampilkan durasi kekurangan waktu kerja sebagai informasi evaluasi, tetapi konsekuensi terhadap upah atau gaji belum boleh ditentukan secara otomatis sebelum aturan perusahaan dikonfirmasi.

### 4.6 Koreksi Absensi

Koreksi absensi hanya dapat dilakukan oleh pengguna yang memiliki izin.

Setiap koreksi wajib memiliki alasan dan jejak audit. Koreksi tidak mengubah raw scan, melainkan membuat perubahan terkontrol pada catatan kehadiran yang digunakan dalam proses perhitungan.

Kewenangan koreksi dan kebutuhan persetujuan tambahan harus mengikuti matriks akses yang ditetapkan perusahaan.

---

## 5. Aturan Lembur

### 5.1 Waktu Mulai Lembur

**Keputusan yang disepakati: lembur mulai dihitung setelah pukul 17.00, tanpa mensyaratkan karyawan sudah menyelesaikan delapan jam kerja normal.**

Contoh:

| Rentang scan | Jam kerja normal sebelum pukul 17.00 | Kandidat lembur |
|---|---:|---:|
| 08.00–17.00 | Mengikuti aturan istirahat dan jadwal | 0 menit |
| 08.00–18.00 | Mengikuti aturan istirahat dan jadwal | 60 menit |
| 15.00–18.00 | 120 menit sebelum pukul 17.00, sebelum penyesuaian istirahat | 60 menit |
| 08.00–17.30 | Mengikuti aturan istirahat dan jadwal | 30 menit |

Contoh tersebut menggambarkan batas waktu lembur, bukan otomatis menetapkan nominal pembayaran lembur.

### 5.2 Perhitungan Durasi

Rumus dasar kandidat lembur pada hari kerja normal:

`Kandidat Lembur = max(0, Jam Pulang Aktual - 17.00)`

Durasi kandidat dihitung berdasarkan waktu aktual. Penyesuaian untuk istirahat, pembulatan, scan tidak lengkap, hari libur, dan ketentuan khusus lainnya harus mengikuti aturan yang sudah dikonfirmasi.

Jam pulang yang digunakan dalam perhitungan harus berasal dari catatan absensi yang telah diproses atau dikoreksi secara sah.

### 5.3 Verifikasi Lembur

Sistem menghitung kandidat durasi lembur dari data scan dan menampilkan usulan perhitungan kepada pengguna berwenang.

Pengguna berwenang dapat memverifikasi atau mengoreksi hasil sesuai prosedur. Koreksi harus memiliki alasan dan tercatat di audit log.

Lembur tidak mensyaratkan pengajuan lembur terlebih dahulu pada rancangan awal. Namun, kebutuhan persetujuan atau verifikasi aktivitas lembur oleh pihak perusahaan tetap perlu dikonfirmasi.

### 5.4 Tarif Lembur

Tarif lembur harus dapat dikonfigurasi sesuai kebijakan perusahaan, termasuk jika berbeda berdasarkan kategori atau karyawan.

Rumus dasar usulan nominal:

`Usulan Nominal Lembur = Durasi Lembur yang Disetujui × Tarif per Jam`

Apabila durasi disimpan dalam menit:

`Usulan Nominal Lembur = Menit Lembur yang Disetujui × Tarif per Jam / 60`

Tarif final, aturan pembulatan, kategori yang berhak menerima lembur, dan perlakuan lembur pada hari libur masih perlu dikonfirmasi. Angka tarif yang pernah digunakan sebagai contoh tidak boleh dianggap sebagai tarif final sebelum disetujui.

Nominal yang ditetapkan harus tersimpan sebagai bagian dari data perhitungan payroll dan tidak berubah ketika tarif masa depan diperbarui.

---

## 6. Aturan Karyawan dan Komponen Penghasilan

### 6.1 Kategori Penggajian Awal

Terdapat dua kelompok penggajian utama:

| Kategori | Periode payroll | Komponen utama |
|---|---|---|
| Karyawan kantor atau staf lainnya | Bulanan | Gaji pokok, upah harian jika berlaku, lembur yang disetujui, dan komponen lain yang telah dikonfirmasi |
| Supir dan kenek | Mingguan | Upah harian, lembur yang disetujui, dan komponen lain yang telah dikonfirmasi |

Penetapan kategori karyawan harus dapat dikonfigurasi. Kategori menentukan pengaturan awal, tetapi nominal dan ketentuan khusus dapat berbeda untuk setiap karyawan.

### 6.2 Gaji Pokok

Gaji pokok merupakan komponen terpisah dari upah harian.

Untuk staf dengan gaji pokok bulanan, nominal gaji pokok harus disimpan sebagai komponen tersendiri. Kebijakan pembayaran penuh, prorata, atau penyesuaian karena ketidakhadiran harus dikonfirmasi sebelum implementasi final.

Sistem tidak boleh otomatis mengurangi gaji pokok berdasarkan jumlah hari tidak masuk jika kebijakan tersebut belum ditetapkan.

### 6.3 Upah Harian

Upah harian dihitung sebagai komponen tersendiri dari gaji pokok.

Sistem mengusulkan upah harian untuk setiap hari yang memenuhi kriteria kehadiran berdasarkan aturan yang berlaku. Usulan harus menyertakan informasi yang relevan, seperti status scan, durasi kerja, dan hasil verifikasi.

Pihak yang berwenang menentukan apakah usulan upah harian disetujui berdasarkan kebijakan perusahaan.

Nilai upah harian dapat berbeda untuk setiap karyawan. Nominal contoh dalam dokumen atau percakapan tidak menjadi tarif final tanpa konfirmasi.

### 6.4 Bonus atau Insentif

Bonus atau insentif ditentukan oleh Owner.

Pada versi pertama, sistem menyediakan pencatatan nominal bonus yang ditetapkan dan komponen tersebut dapat dimasukkan ke payroll. Sistem tidak menghitung bonus secara otomatis berdasarkan KPI atau rumus lain sebelum kebijakannya disetujui.

### 6.5 Potongan dan Kewajiban

Komponen potongan harus dikelola secara eksplisit. Sistem tidak boleh memasukkan potongan yang belum dikonfirmasi sebagai kebijakan perusahaan.

BPJS, PPh 21, dan potongan lain hanya diimplementasikan setelah komponen, dasar perhitungan, pihak yang menanggung, serta aturan penerapannya disepakati.

---

## 7. Aturan Kasbon atau Piutang Karyawan

Kasbon harus dicatat sebagai **ledger transaksi**, bukan hanya saldo yang dapat diubah langsung tanpa riwayat.

Data transaksi kasbon sekurang-kurangnya mencakup:

- Karyawan terkait.
- Tanggal transaksi.
- Jenis transaksi, seperti pemberian kasbon atau pembayaran/pelunasan.
- Nominal transaksi.
- Keterangan atau alasan.
- Pengguna yang mencatat.
- Referensi periode payroll jika transaksi terkait payroll.

Saldo kasbon dihitung dari transaksi yang tercatat, bukan diisi sembarangan sebagai nilai akhir.

Rumus konseptual:

`Saldo Kasbon = Total Kasbon yang Diberikan - Total Pembayaran atau Pelunasan`

Pemotongan kasbon dari payroll hanya dilakukan sesuai kebijakan perusahaan yang telah disetujui.

Besaran cicilan, batas pemotongan per periode, persetujuan transaksi, dan perlakuan terhadap saldo yang belum lunas masih perlu dikonfirmasi. Perubahan transaksi harus memiliki jejak audit.

---

## 8. Perhitungan Payroll

### 8.1 Periode Payroll

Sistem harus mendukung dua periode penggajian:

- **Mingguan:** untuk supir dan kenek.
- **Bulanan:** untuk karyawan lainnya.

Tanggal mulai dan akhir periode, tanggal pembayaran, dan penanganan transaksi yang berada di batas periode harus mengikuti kalender payroll yang disetujui perusahaan.

### 8.2 Rumus Konseptual

Untuk staf bulanan:

`Payroll Bersih = Gaji Pokok + Total Upah Harian Disetujui + Total Lembur Disetujui + Bonus/Komponen Tambahan - Total Potongan Disetujui`

Untuk supir dan kenek:

`Payroll Bersih = Total Upah Harian Disetujui + Total Lembur Disetujui + Bonus/Komponen Tambahan - Total Potongan Disetujui`

Rumus di atas adalah struktur konseptual. Komponen yang tidak berlaku untuk seorang karyawan tidak ditambahkan secara otomatis.

Komponen seperti BPJS, PPh 21, dan potongan lainnya hanya digunakan setelah kebijakan perusahaan dikonfirmasi. Perlakuan gaji pokok terhadap ketidakhadiran, prorata, serta komponen tambahan juga harus mengikuti aturan yang berlaku.

### 8.3 Tahapan Payroll

Alur umum payroll:

1. Sistem menentukan periode payroll.
2. Sistem mengambil data absensi yang relevan.
3. Sistem menghitung kandidat upah harian dan lembur.
4. Pengguna berwenang memverifikasi data dan koreksi yang diperlukan.
5. Sistem menghitung komponen penghasilan dan potongan yang berlaku.
6. Pengguna memeriksa rincian perhitungan.
7. Payroll difinalisasi oleh pengguna berwenang.
8. Sistem menyimpan hasil final beserta snapshot perhitungannya.
9. Laporan payroll dapat ditampilkan atau diekspor sesuai hak akses.

### 8.4 Snapshot Perhitungan

Setiap payroll yang difinalisasi harus memiliki snapshot atau mekanisme versi yang setara, yang merekam data penting untuk mereproduksi hasil perhitungan, termasuk:

- Periode payroll dan identitas karyawan.
- Komponen penghasilan dan potongan yang digunakan.
- Tarif dan aturan yang berlaku saat perhitungan.
- Data absensi dan hasil koreksi yang menjadi input.
- Durasi lembur dan nominal yang disetujui.
- Usulan dan keputusan upah harian.
- Nominal bonus dan komponen tambahan.
- Hasil perhitungan bruto, potongan, dan bersih.
- Waktu finalisasi serta pengguna yang melakukan finalisasi.

Tujuannya adalah agar hasil payroll lama tetap dapat dijelaskan dan diverifikasi meskipun konfigurasi perusahaan berubah.

---

## 9. Finalisasi dan Pembukaan Kembali Payroll

### 9.1 Finalisasi

Setelah payroll difinalisasi, data hasil perhitungan periode tersebut tidak boleh berubah secara diam-diam akibat perubahan absensi, tarif, atau aturan untuk periode berikutnya.

Perubahan data sumber setelah finalisasi harus ditangani melalui prosedur koreksi yang terkontrol.

### 9.2 Pembukaan Kembali

Payroll yang sudah final dapat dibuka kembali melalui prosedur yang diizinkan. Kewenangan, alasan wajib, dan kebutuhan persetujuan tambahan masih perlu dikonfirmasi oleh perusahaan.

Ketika payroll dibuka kembali:

1. Sistem mempertahankan snapshot final sebelumnya.
2. Sistem mencatat siapa yang membuka kembali dan alasannya.
3. Perubahan dilakukan melalui proses koreksi yang tercatat.
4. Sistem menghitung ulang berdasarkan data dan aturan historis yang semestinya berlaku pada periode tersebut, bukan otomatis menggunakan tarif terbaru.
5. Setelah diperiksa, payroll dapat difinalisasi kembali sebagai versi baru.
6. Sistem mempertahankan riwayat versi sebelumnya untuk audit.

### 9.3 Contoh Tarif Historis

Misalnya tarif upah harian seorang karyawan adalah Rp80.000 pada bulan September, kemudian berubah menjadi Rp100.000 mulai Oktober.

Jika payroll September dibuka kembali pada bulan Oktober, perhitungan September tetap menggunakan tarif historis Rp80.000, kecuali terdapat koreksi resmi yang memang menetapkan perubahan berlaku surut dan telah disetujui sesuai prosedur perusahaan.

Perubahan tarif yang berlaku mulai Oktober tidak boleh secara otomatis mengubah hasil payroll September.

---

## 10. Pengaturan Aturan Kerja dan Tarif

Pengaturan aturan harus mendukung konfigurasi global dan konfigurasi per karyawan, sesuai hak akses.

Kelompok pengaturan yang direncanakan:

| Kelompok | Contoh parameter |
|---|---|
| Jadwal kerja | Jam masuk, jam pulang, hari kerja, dan jam istirahat |
| Keterlambatan | Toleransi dan metode perhitungan |
| Evaluasi kehadiran | Target jam kerja dan status kekurangan jam |
| Lembur | Jam mulai, tarif, kategori yang berhak, pembulatan, dan verifikasi |
| Penggajian | Periode mingguan/bulanan dan batas periode |
| Penghasilan | Gaji pokok, upah harian, bonus, dan komponen tambahan |
| Potongan | Kasbon, BPJS, PPh 21, dan komponen yang telah disetujui |
| Kalender | Hari kerja, hari libur, dan perlakuan kerja pada hari libur |

Setiap perubahan aturan atau tarif perlu memiliki tanggal mulai berlaku, pengguna yang melakukan perubahan, waktu perubahan, serta riwayat nilai sebelumnya.

Aturan yang sudah digunakan oleh payroll final harus tetap dapat ditelusuri. Perubahan konfigurasi baru berlaku sesuai tanggal efektifnya dan tidak boleh mengubah snapshot payroll historis.

---

## 11. Hak Akses dan Audit

Setiap tindakan yang memengaruhi absensi atau payroll harus dibatasi berdasarkan hak akses pengguna.

Tindakan yang perlu tercatat dalam audit log antara lain:

- Perubahan aturan kerja dan tarif.
- Koreksi absensi.
- Verifikasi atau koreksi lembur.
- Persetujuan atau penolakan komponen upah harian.
- Pencatatan dan perubahan transaksi kasbon.
- Perubahan komponen payroll.
- Finalisasi, pembukaan kembali, dan finalisasi ulang payroll.
- Perubahan data karyawan yang berdampak pada perhitungan.

Audit log harus memuat informasi yang memadai untuk mengetahui pelaku, waktu, objek yang berubah, dan alasan jika diwajibkan oleh proses.

Hak akses final untuk Owner, HR/Finance, dan Karyawan mengikuti dokumen kebutuhan serta keputusan akses yang telah disepakati. Sistem tidak boleh hanya mengandalkan penyembunyian menu di frontend; backend juga wajib memvalidasi izin setiap tindakan.

---

## 12. Laporan Payroll sebagai Acuan Pengujian

Empat jenis laporan/formulir aktual perusahaan menjadi acuan utama untuk memastikan hasil sistem sesuai kebutuhan operasional.

1. **Absensi dan payroll mingguan supir/kenek.**
2. **Laporan lembur supir/kenek.**
3. **Absensi dan payroll bulanan karyawan staf.**
4. **Laporan lembur bulanan karyawan staf.**

Contoh data, format, kolom, rumus, dan hasil yang diharapkan dari formulir aktual perlu dikumpulkan serta divalidasi bersama klien.

Keempat laporan tersebut menjadi baseline rekonsiliasi dan pengujian regresi. Format final, kolom wajib, dan aturan pembulatan mengikuti dokumen aktual yang telah dikonfirmasi.

---

## 13. Skenario Pengujian Bisnis

Skenario berikut menjadi contoh awal pengujian. Skenario yang bergantung pada aturan belum dikonfirmasi tidak boleh dijadikan ekspektasi final sebelum kebijakan disepakati.

| ID | Skenario | Ekspektasi |
|---|---|---|
| TC-01 | Karyawan scan sebelum jam masuk | Jam kerja normal tidak dimulai sebelum jadwal jika aturan tersebut berlaku |
| TC-02 | Karyawan scan tepat pukul 08.00 | Keterlambatan 0 menit dengan toleransi 0 |
| TC-03 | Karyawan scan pukul 08.10 | Keterlambatan 10 menit dengan toleransi 0 |
| TC-04 | Karyawan pulang pukul 17.00 | Tidak ada kandidat lembur setelah jam pulang normal |
| TC-05 | Karyawan pulang pukul 18.00 | Kandidat lembur 60 menit, sebelum aturan penyesuaian lain |
| TC-06 | Karyawan bekerja pukul 15.00–18.00 | Kandidat lembur 60 menit; waktu sebelum pukul 17.00 dipisahkan dari lembur |
| TC-07 | Karyawan hanya memiliki satu scan | Catatan ditandai tidak lengkap dan tidak mengarang scan yang hilang |
| TC-08 | HR melakukan koreksi absensi | Raw scan tetap utuh dan koreksi tercatat dalam audit |
| TC-09 | Tarif berubah mulai bulan berikutnya | Perhitungan periode sebelumnya tetap menggunakan tarif historis |
| TC-10 | Payroll difinalisasi | Snapshot perhitungan dan identitas pelaku finalisasi tersimpan |
| TC-11 | Payroll final dibuka kembali | Versi sebelumnya tetap tersimpan dan pembukaan kembali tercatat |
| TC-12 | Payroll dihitung ulang setelah tarif berubah | Tarif historis digunakan untuk periode lampau sesuai tanggal efektif |
| TC-13 | Kasbon ditambahkan atau dilunasi | Saldo berasal dari transaksi ledger dan dapat ditelusuri |
| TC-14 | Bonus ditetapkan oleh Owner | Nominal bonus masuk ke payroll tanpa memerlukan rumus otomatis yang belum disetujui |
| TC-15 | Perangkat mengirim scan yang sama berulang kali | Sistem mencegah kejadian yang sama dihitung ganda berdasarkan identitas kejadian |
| TC-16 | Pengguna tanpa izin mencoba mengubah payroll | Backend menolak tindakan dan mencatat kejadian sesuai kebijakan audit |

Pengujian nominal payroll harus menggunakan data uji yang telah disepakati, termasuk formulir payroll aktual dan hasil perhitungan yang telah diverifikasi klien.

---

## 14. Aturan yang Masih Memerlukan Konfirmasi

Hal-hal berikut tidak boleh ditetapkan hanya berdasarkan asumsi developer:

1. Hari kerja resmi dan perlakuan hari Minggu atau hari libur.
2. Jam istirahat dan metode pengurangannya dari jam kerja.
3. Apakah target 200 jam per bulan benar-benar digunakan.
4. Dampak keterlambatan, pulang cepat, dan ketidakhadiran terhadap gaji.
5. Penanganan scan tidak lengkap, scan lintas tengah malam, dan pembulatan durasi.
6. Tarif lembur, kategori yang berhak, pembulatan, serta perlakuan lembur pada hari libur.
7. Tanggal mulai dan akhir periode payroll mingguan dan bulanan.
8. Kebijakan gaji pokok ketika karyawan tidak masuk atau bekerja sebagian periode.
9. Kriteria persetujuan upah harian dan kewenangan pihak yang menyetujuinya.
10. Komponen BPJS, PPh 21, dan potongan lain yang akan digunakan.
11. Batas cicilan dan persetujuan pemotongan kasbon.
12. Kriteria bonus atau insentif selain penetapan nominal oleh Owner.
13. Kewenangan, alasan, dan persetujuan untuk membuka kembali payroll.
14. Prosedur koreksi apabila payroll sudah dibayarkan.
15. Format final dan hasil yang diharapkan dari empat formulir payroll perusahaan.

Daftar pertanyaan terperinci dan status konfirmasi dikelola dalam [`pertanyaan-terbuka.md`](./pertanyaan-terbuka.md). Keputusan yang sudah disepakati harus dicatat dalam [`keputusan.md`](./keputusan.md).

---

## 15. Prinsip Implementasi

Developer dan QA harus mengikuti prinsip berikut:

1. Jangan menanam tarif, jadwal, dan kebijakan payroll yang dapat berubah sebagai konstanta bisnis di kode.
2. Jangan menganggap contoh angka sebagai kebijakan final tanpa konfirmasi.
3. Jangan mengubah raw scan ketika mengoreksi absensi.
4. Jangan menghitung satu kejadian scan lebih dari sekali.
5. Jangan mengubah hasil payroll historis hanya karena konfigurasi baru dibuat.
6. Jangan menerapkan potongan atau komponen penggajian yang belum disetujui.
7. Simpan input, aturan, tarif, dan hasil perhitungan agar payroll dapat ditelusuri.
8. Semua perubahan penting harus memiliki audit trail.
9. Uji rumus payroll terhadap formulir aktual perusahaan sebelum sistem digunakan untuk penggajian produksi.
10. Jika aturan bisnis masih ambigu, tandai sebagai belum dikonfirmasi dan minta keputusan yang eksplisit sebelum menetapkan perilaku final.

**Dokumen terkait:**
- [`PRD.md`](./PRD.md)
- [`keputusan.md`](./keputusan.md)
- [`pertanyaan-terbuka.md`](./pertanyaan-terbuka.md)
- [`alur.md`](./alur.md)
- [`arsitektur.md`](./arsitektur.md)
- [`kebutuhan.md`](./kebutuhan.md)
