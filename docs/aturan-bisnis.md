# Aturan Bisnis

**Prinsip utama (disepakati):** semua aturan kerja dan gaji disimpan sebagai **pengaturan** yang diatur Owner lewat web. Tidak ada aturan bisnis yang di-hardcode. Angka di dokumen ini adalah **nilai awal**.

## Jam kerja (nilai awal)

| Aturan | Nilai awal | Status |
| --- | --- | --- |
| Jam masuk / pulang | 08.00 / 17.00 | Disepakati |
| Istirahat | 12.00–13.00, tidak dihitung sebagai jam kerja | Disepakati sebagai nilai awal (masih perkiraan klien) |
| Datang sebelum jam masuk | Jam kerja dihitung mulai jam masuk | Disepakati |
| Terlambat | Scan masuk setelah jam masuk + toleransi (nilai awal toleransi 0 menit). Hanya dicatat, tanpa potongan otomatis | Disepakati |
| Target | 8 jam/hari, 200 jam/bulan (25 hari kerja) | Disepakati |
| Di bawah target | Ditandai untuk evaluasi HR. Tidak ada potongan otomatis | Disepakati |
| Hari kerja | Senin–Sabtu | **Usulan**, menunggu konfirmasi |
| Scan masuk dan pulang | Scan pertama hari itu = masuk, scan terakhir = pulang. Kalau hanya ada satu scan, statusnya "tidak lengkap" | **Usulan** |
| Lupa scan | HR/Finance/Owner boleh mengoreksi, dan setiap koreksi tercatat | Disepakati |

### Rumus harian (hari kerja)

```
awal_kerja   = max(scan_masuk, jam_masuk)
akhir_normal = min(scan_pulang, jam_pulang)
jam_kerja    = (akhir_normal − awal_kerja) − bagian istirahat yang berada di dalam rentang itu
lembur       = max(0, scan_pulang − jam_pulang)          # per menit
terlambat    = max(0, scan_masuk − (jam_masuk + toleransi))
```

Pada hari yang bukan hari kerja (nilai awal: Minggu), **seluruh** waktu antara scan masuk dan scan pulang dihitung lembur, dan **tidak ada upah harian**.

## Lembur (disepakati)

- Lembur dihitung **otomatis, tanpa pengajuan**.
- Durasi dihitung **per menit**.
- Tarif dasar **Rp14.000/jam**. Sistem mengusulkan nominal = menit × tarif / 60, dan **HR/Finance/Owner menetapkan nominal akhirnya**.
- Apakah lembur berlaku untuk suatu kategori karyawan (misalnya supir/kenek) diatur di Pengaturan Aturan.

## Gaji

| | Karyawan kantor | Supir/kenek |
| --- | --- | --- |
| Periode | Bulanan | Mingguan |
| Gaji pokok | Ya, tetap dibayar penuh walaupun tidak masuk (contoh Rp2.000.000) | Tidak ada |
| Upah harian | Per hari masuk (contoh Rp40.000) | Per hari masuk (Rp100.000) |
| Lembur | Ya | Diatur Owner |
| Potongan | Kasbon, BPJS (rincian menyusul) | Kasbon, BPJS (rincian menyusul) |

- **Upah harian** diusulkan sistem untuk setiap hari masuk (hari kerja), dengan keterangan apakah hari itu genap 8 jam. **HR/Finance/Owner memutuskan** upah hari itu dibayar atau tidak.
- Nominal gaji pokok dan upah harian **bisa berbeda per karyawan**. Nilai kategori hanya menjadi nilai awal.
- Rumus:
  - Kantor: `gaji pokok + Σ upah harian yang disetujui + Σ nominal lembur − kasbon − BPJS`
  - Supir/kenek: `Σ upah harian yang disetujui + Σ nominal lembur − kasbon − BPJS`
- Setelah periode **difinalisasi**, data periode itu terkunci.

## Modul Pengaturan Aturan

Hanya Owner yang bisa mengubahnya (disepakati). Setiap perubahan punya tanggal **"berlaku mulai"**, dan periode yang sudah final **tidak ikut berubah**. Setiap perubahan juga tercatat di audit log.

| Kelompok | Yang bisa diatur |
| --- | --- |
| Jadwal kerja (per kategori) | Jam masuk, jam pulang, jam istirahat (dipotong atau tidak), toleransi terlambat, hari kerja |
| Hari libur | Kalender libur, dan apakah kerja di hari libur dihitung lembur |
| Lembur (per kategori) | Berlaku atau tidak, tarif per jam, pembulatan (per menit / 15 menit / per jam) |
| Target dan evaluasi | Jam per hari, jam per bulan, hari kerja per bulan |
| Kategori karyawan | Nama, periode gaji (mingguan/bulanan), ada gaji pokok atau tidak, upah harian awal |
| Per karyawan | Gaji pokok dan upah harian khusus |
| Potongan (menyusul) | Komponen dan tarif BPJS, aturan kasbon |

## Contoh hitungan (menjadi test case)

Memakai nilai awal: jam 08.00–17.00, istirahat 12.00–13.00 dipotong, toleransi 0, Minggu bukan hari kerja.

| ID | Kasus | Terlambat | Jam kerja | Lembur | Catatan |
| --- | --- | --- | --- | --- | --- |
| TC-01 | Senin 07.30–17.40 | 0 | 8j | 40m | Jam kerja mulai 08.00 |
| TC-02 | Senin 08.00–17.00 | 0 | 8j | 0 | |
| TC-03 | Senin 12.00–18.00 | 240m | 4j | 60m | Jika istirahat tidak dipotong: 5j |
| TC-04 | Senin 08.00–15.00 | 0 | 6j | 0 | Di bawah 8 jam, ditandai; upah harian tetap diusulkan |
| TC-05 | Senin 08.10–17.00 | 10m | 7j50m | 0 | Di bawah 8 jam, ditandai |
| TC-06 | Senin 09.00–12.30 | 60m | 3j | 0 | 30 menit di jam istirahat tidak dihitung |
| TC-07 | Minggu 08.00–11.00 | — | 0 | 180m | Tanpa upah harian |
| TC-08 | Senin, hanya 1 scan 08.00 | — | — | — | Status tidak lengkap, menunggu koreksi |

| ID | Kasus gaji | Hasil sebelum potongan |
| --- | --- | --- |
| TC-P1 | Kantor: pokok Rp2.000.000, 25 hari × Rp40.000 disetujui, lembur ditetapkan Rp140.000 | Rp3.140.000 |
| TC-P2 | Kantor: tidak masuk sebulan | Rp2.000.000 |
| TC-P3 | Kantor: 25 hari masuk, 2 hari upahnya tidak disetujui | 2.000.000 + 23 × 40.000 = Rp2.920.000 |
| TC-P4 | Supir: 6 hari × Rp100.000, tanpa lembur | Rp600.000 |

Hal yang belum pasti (pembulatan rupiah, istirahat di hari Minggu, apakah lembur ikut dihitung dalam target 200 jam) dicatat di [pertanyaan-terbuka.md](pertanyaan-terbuka.md).
