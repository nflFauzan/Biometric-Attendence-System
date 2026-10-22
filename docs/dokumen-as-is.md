# Dokumen AS-IS Client

**Status:** dianalisis 2026-10-22 dari foto dokumen yang diterima pada rapat 2026-10-08. Isinya **indikasi**, bukan keputusan. Setiap indikasi perlu dikonfirmasi client (daftar di §5) sebelum dipakai sebagai aturan. Foto asli berisi nama dan gaji sebenarnya, sehingga tidak disimpan di repo. Nama di dokumen ini disamarkan.

Client ingin web bisa menghasilkan **Excel dan PDF** dengan format seperti dokumen-dokumen ini (K-105). Dokumen ini melengkapi daftar laporan baseline di K-042 dan [aturan-bisnis.md](aturan-bisnis.md) §12.

## 1. Daftar dokumen

| Kode | Dokumen | Periode | Isi | Pengesah |
|---|---|---|---|---|
| AS-1 | **Absensi** (upah mingguan supir dan kenek), Lokasi "TOKO" | Mingguan, "Minggu ke-1", 26 Sep–03 Okt 2026 | Kolom hari **S M S S R K J** (Sabtu–Jumat) berisi V atau sel merah (tidak masuk), Jumlah Hari, Upah (per hari), Jumlah Upah, Lembur (Rp), Input Piutang, Sisa Upah, Insentif. Baris Jumlah Total dan kotak TOTAL UPAH. | Direktur Operasional |
| AS-2 | **Lemburan** (supir dan kenek) | Mingguan, sama dengan AS-1 | Jam lembur per hari (kelipatan 0,5), total jam (kolom berlabel "JMLH HARI"), Uang Lembur per jam, Jumlah Lemburan | — |
| AS-3 | **Form Lemburan Supir, Kenek dan Gudang** (tulis tangan, per orang) | Harian | Hari, Tgl, Jam Masuk (kosong), Jam Keluar, Kegiatan (tujuan atau pekerjaan), TTD | Tanda tangan per baris |
| AS-4 | **Lap. Detail Absensi** dari mesin absensi lama | "Waktu Ab 2026-09-24 ~ 2026-10-03", dengan tanggal tabulasi | Per karyawan: ID 3 digit, Nama, Dept (OPS, PRC, FIN, MAR, SHIP), dan kisi per tanggal berisi jam-jam scan hari itu | — |
| AS-5 | **Gaji bulanan staf** (kop "TAKKA STEEL") | Bulanan, September 2026 | Manager Name, Department. Kisi tanggal 1–31 (Minggu dan tanggal yang tidak ada diberi warna merah), Total Days, Wages, Upah Lembur, Input Piutang, Onsite, Gaji Pokok, Total Wages, dan TOTAL hari | Director, Manager |
| AS-6 | **Rekap Lembur Takka Steel** (staf) | Bulanan, September 2026 | Jam lembur per tanggal, Total Lembur, Uang Lembur per jam, Jumlah Upah | Director |
| AS-7 | **Pengajuan Toko Takka 2** | Tanggal 01 Oktober 2026 | Nama Akun "Beban Gaji Karyawan", Akun Induk "Beban Administrasi dan Umum", No Invoice (misalnya `11-01102026`), rincian gaji per orang dan admin bank, total pengajuan, keterangan | Yang Menyetujui, Yang Mengetahui, Yang Mengajukan |

## 2. Rumus yang cocok dengan angka

Rumus berikut direkonstruksi dari angka di foto, dan totalnya cocok sampai ke rupiah.

### 2.1 Supir dan kenek (AS-1, AS-2)

`Sisa Upah = (Jumlah Hari × Upah per hari) + (Jam Lembur × Uang Lembur per jam) − Input Piutang`

| Contoh | Hari × Upah | Lembur | Piutang | Sisa Upah |
|---|---|---|---|---|
| Supir 1 | 7 × 100.000 = 700.000 | 16 jam × 14.000 = 224.000 | — | 924.000 |
| Supir 3 | 6 × 100.000 = 600.000 | 8 jam × 14.000 = 112.000 | 100.000 | 612.000 |
| Supir 6 | 7 × 110.000 = 770.000 | 53 jam × 14.000 = 742.000 | 150.000 | 1.362.000 |
| Total 18 orang | 10.205.000 | 5.806.000 | 1.800.000 | **14.211.000** |

- Upah per hari berbeda per orang: 60.000, 80.000, 100.000, 110.000, dan 125.000.
- Uang lembur per jam berbeda per orang: 14.000 atau 10.000.

### 2.2 Staf bulanan (AS-5, AS-6, AS-7)

`Total Wages = Gaji Pokok + Onsite + Upah Lembur − Input Piutang`, dengan `Onsite = Total Days × Wages` (Wages umumnya 40.000 per hari).

| Contoh | Onsite | Gaji Pokok | Lembur | Piutang | Total | Di AS-7 |
|---|---|---|---|---|---|---|
| Staf 1 | 26 × 40.000 = 1.040.000 | 3.000.000 | 0 | — | 4.040.000 | 4.040.000 |
| Staf 2 | 1.040.000 | 3.000.000 | 20 jam × 14.000 = 280.000 | — | 4.320.000 | 4.320.000 |
| Staf 6 | 23 × 40.000 = 920.000 | 2.000.000 | 490.000 | — | 3.410.000 | 3.410.000 |
| Staf 8 | 21 × 40.000 = 840.000 | 1.000.000 | 40.000 | 500.000 | 1.380.000 | — |

- Gaji pokok tetap dibayar penuh walau hadir hanya 21 atau 23 hari dari 26.
- Total AS-5: 43.992.000. Total AS-7 (Toko Takka 2): 28.965.500, termasuk "Admin Bank (4 org)" 10.000 dan satu admin bank 2.500.

## 3. Indikasi aturan

| Topik | Indikasi | Pertanyaan |
|---|---|---|
| Hari kerja | Staf bekerja Senin–Sabtu; hadir penuh September = 26 hari, dan Minggu diberi warna merah. Supir dan kenek punya 7 kolom hari, dan sebagian masuk hari Minggu. | Q-001 |
| Kerja hari Minggu | Supir yang masuk Minggu mendapat upah harian biasa, dan lemburnya memakai tarif yang sama. Tidak terlihat tarif khusus. | Q-005 |
| Periode | Mingguan Sabtu–Jumat, diberi nomor "Minggu ke-N". Judul periode "26 Sep–03 Okt" mencakup 8 tanggal, padahal kolomnya hanya 7. Bulanan mengikuti bulan kalender, dan dana diajukan tanggal 1 bulan berikutnya. | Q-006 |
| Gaji pokok | Tidak prorata. Ketidakhadiran hanya mengurangi Onsite. Supir dan kenek tidak punya gaji pokok. | Q-007 |
| Pembulatan | Jam lembur dalam satuan 0,5 jam. Semua nominal rupiah bulat. | Q-008 |
| Tarif lembur | Tarif per jam flat, ditetapkan per orang (14.000 atau 10.000), tanpa pengali hari Minggu. | Q-009 |
| BPJS dan pajak | Tidak ada kolom BPJS maupun PPh di dokumen mana pun. | Q-010 |
| Piutang | Dipotong dengan cicilan bernominal bulat (50.000–500.000) yang ditentukan setiap periode. | Q-011 |
| Insentif | Ada kolom Insentif di AS-1, tetapi masih kosong. | Q-012 |
| Ketidakhadiran | Hanya ditandai sel merah. Tidak ada pembeda izin, sakit, atau alpa. | Q-013 |
| Keterlambatan | Staf rata-rata scan masuk sekitar 07:30. Tidak ada kolom terlambat di laporan mana pun. | Q-003 |
| Scan tidak lengkap | Supir 10 pada 30 Sep hanya punya satu scan (21:27). Di AS-1 hari itu awalnya merah, lalu dikoreksi tangan menjadi V. | Q-004 |
| Pengesahan | Laporan disiapkan Manager Accounting & Finance, lalu disahkan Director atau Direktur Operasional. | Q-016, Q-017 |
| Cabang | Nama cabang: Takka 1, Takka 2, Takka Cilong. Upah mingguan dibagi per toko (tulisan tangan di AS-1), dan pengajuan dana dibuat per toko (AS-7). | Q-022, Q-038 |
| Brand | Kop "TAKKA STEEL" dengan garis biru. | Q-024, Q-025 |
| PIN mesin | Mesin lama memakai ID 3 digit (001, 002, …) dan mencatat Dept. | Q-031 |

## 4. Temuan yang belum tercatat sebelumnya

1. **Koreksi manual yang tidak ikut menjumlah.** Hari Supir 10 dikoreksi tangan dari 5 menjadi 6, dan sisa upahnya dari 723.000 menjadi 823.000. Namun Jumlah Upah, TOTAL UPAH (14.211.000), dan pembagian per toko tetap memakai angka lama. Ada selisih 100.000 yang tidak terlihat. Masalah seperti ini dicegah oleh sistem: koreksi tersimpan terpisah, dan semua total dihitung ulang.
2. **Pembagian per toko.** Tulisan tangan di AS-1: Takka 1 = 1.868.000, Takka 2 = 11.253.000, Takka Cilong = 1.090.000 (jumlah 14.211.000), lalu "Cash → 13.121.000" (= Takka 1 + Takka 2). Kelompok Takka Cilong tampaknya adalah orang-orang dengan upah 60.000–80.000 dan lembur 10.000 per jam. Dasar pembagian belum diketahui (Q-038).
3. **Lembur supir tidak selalu dari scan.**
   - Supir 10, Kamis 1 Okt: scan pulang 22:33 (5,5 jam setelah 17:00), tetapi di AS-2 tercatat 8 jam.
   - Beberapa supir tercatat 8 jam per hari selama berhari-hari.
   - Lembur supir dicatat di Form Lemburan, berisi jam keluar, kegiatan, dan tanda tangan (Q-039).
4. **Pembulatan lembur tidak konsisten jika dihitung dari scan pulang dikurangi 17:00.**

   | Scan pulang | Selisih dari 17:00 | Jam lembur di rekap |
   |---|---|---|
   | 20:28 | 3 j 28 m | 3,5 |
   | 20:11 | 3 j 11 m | 3,5 |
   | 18:01 | 1 j 1 m | 1 |
   | 18:03 | 1 j 3 m | 1 |
   | 21:27 | 4 j 27 m | 4,5 |
   | 18:33 | 1 j 33 m | 1,5 |

   Pola ini tidak cocok dengan pembulatan ke atas ataupun ke bawah per 0,5 jam. Mungkin ada toleransi menit, atau penilaian manual (Q-008, Q-040).
5. **Jumlah karyawan sekitar 32** (18 supir/kenek di AS-1 dan 14 staf di AS-5), bukan 25 seperti yang disebut di K-103 (Q-041).
6. **Atribut karyawan baru.**
   - Departemen (OPS, PRC, FIN, MAR, SHIP).
   - Nama panggilan: AS-7 memakai nama pendek atau panggilan ("Om …"), sedangkan AS-5 memakai nama lengkap (Q-042).
7. **Metode bayar.** Supir dan kenek dibayar tunai. Staf ditransfer, dengan admin bank 2.500 per orang yang **ditanggung perusahaan** (masuk AS-7, tidak dipotong dari gaji). Angka 2.500 juga ditulis tangan di kolom Input Piutang AS-5, tetapi tidak mengurangi total (Q-043).
8. **Skema harian di lembar staf.** Beberapa baris AS-5 memiliki Wages 140.000–180.000 tanpa gaji pokok, mirip upah harian murni. Foto miring, sehingga barisnya tidak bisa dipastikan (Q-044).
9. **Kisi bulanan selalu 31 kolom.** Tanggal yang tidak ada (31 September) ikut diberi warna merah.

## 5. Daftar konfirmasi untuk client

Dijawab dengan Benar atau Salah, ditambah keterangan bila perlu.

1. Supir dan kenek: periode Sabtu–Jumat; kerja hari Minggu dibayar upah harian biasa; tarif lembur sama setiap hari. (Q-001, Q-005, Q-006)
2. Staf: hari kerja Senin–Sabtu; gaji pokok selalu penuh; ketidakhadiran hanya mengurangi Onsite 40.000 per hari. (Q-001, Q-007)
3. Tarif lembur per orang (14.000 atau 10.000), dan kelipatan 0,5 jam. **Bagaimana aturan pembulatannya?** (Q-008, Q-009, Q-040)
4. Lembur supir: dari scan, dari Form Lemburan, atau keduanya? Siapa yang mengesahkan? (Q-039)
5. Pembagian upah per toko: berdasarkan cabang penempatan, cabang tempat scan, atau lainnya? (Q-038)
6. Tidak ada BPJS dan PPh di versi pertama. (Q-010)
7. Cicilan piutang ditentukan per periode tanpa batas tertentu. (Q-011)
8. Admin bank ditanggung perusahaan; supir dibayar tunai, staf ditransfer. (Q-043)
9. Jumlah karyawan sebenarnya, Departemen, dan nama panggilan. (Q-041, Q-042)
10. Skema Wages 140.000–180.000 tanpa gaji pokok di lembar staf. (Q-044)
11. Minta **file Excel asli** semua dokumen, agar tata letak, rumus, dan kolom ekspor bisa ditiru persis.

## 6. Pemakaian untuk pengujian

Setelah dikonfirmasi, angka AS-1 dan AS-5 dijadikan test rekonsiliasi Fase 5 (K-042, K-043). Data karyawan disamarkan, dan sistem harus menghasilkan total 14.211.000 dan 43.992.000 dari input yang sama. Koreksi Supir 10 dipakai sebagai kasus uji koreksi.
