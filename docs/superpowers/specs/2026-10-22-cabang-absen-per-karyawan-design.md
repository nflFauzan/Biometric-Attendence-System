# Rancangan: Cabang Absen per Karyawan (K-103)

**Status:** disetujui pengguna 2026-10-22, diterapkan (K-106). Pengguna meminta rancangan disusun langsung tanpa sesi tanya-jawab, jadi semua rincian di bawah adalah **keputusan Claude** yang boleh diubah.

## 1. Tujuan

Client ingin sebagian karyawan boleh absen di cabang lain, dan sebagian tidak (K-103). Hasil yang diharapkan:

- Owner dan HR/Finance mengatur cabang absen tiap karyawan di Detail Karyawan.
- Sidik jari hanya bisa didaftarkan di mesin cabang yang diizinkan.
- Scan dari cabang yang tidak diizinkan tetap disimpan, tetapi tidak masuk rekap sampai ditinjau. Rekap ini dipakai juga oleh Absensi, Rekan Kerja, dashboard, dan nanti penggajian.

**Bukan tujuan:** pembagian upah per toko (Q-038), zona waktu per cabang, dan penyalinan template ke beberapa mesin (Rencana 4).

**Kegagalan yang harus dihindari:**
- Scan asli diubah atau hilang.
- Karyawan yang tidak diizinkan tetap tercatat hadir tanpa ada yang tahu.
- Mengubah cabang penempatan diam-diam menghapus sidik jari.

## 2. Model data

### 2.1 Cabang tambahan (migration `V11__cabang_absen.sql`)

`employee_extra_branches (employee_id, branch_id)`: primary key gabungan, kedua kolom FK, dan `ON DELETE CASCADE` dari karyawan.

- **Cabang yang diizinkan = cabang penempatan ∪ cabang tambahan.** Cabang penempatan tidak disimpan di tabel ini, sehingga selalu diizinkan dan tidak bisa dicabut (K-103: "selalu tercentang").
- **Data lama.** Karyawan yang sudah ada tidak punya cabang tambahan, jadi setelah migration hanya cabang penempatan yang diizinkan. Sebelum dipakai, HR perlu mencentang cabang tambahan untuk karyawan yang memang boleh berpindah.
- **Saat cabang penempatan diganti lewat form karyawan:**
  - Cabang lama otomatis menjadi cabang tambahan, sehingga akses dan sidik jari di sana tidak hilang.
  - Cabang baru dihapus dari daftar tambahan bila ada.
  - Keduanya dicatat di audit perubahan karyawan.
  - Untuk mencabut cabang lama, HR menghapus centangnya di Detail Karyawan (§4).

### 2.2 Penanda scan

- Penanda baru `ScanFlag.CABANG_TIDAK_DIIZINKAN` diberikan saat scan diterima (`ScanIngestService`), jika cabang mesin tidak termasuk cabang yang diizinkan pada saat itu.
- `CABANG_BERBEDA` tetap ada, tetapi artinya menyempit menjadi "scan di cabang tambahan yang diizinkan". Scan seperti ini tetap masuk rekap, dan penandanya hanya informasi. Satu scan tidak pernah membawa kedua penanda sekaligus.
- **Pemberian PIN belakangan (K-068).** Saat scan `PIN_TIDAK_DIKENAL` dihubungkan ke karyawan, scan yang mesinnya tidak berada di cabang yang diizinkan karyawan itu ikut diberi `CABANG_TIDAK_DIIZINKAN` dalam query yang sama.
- **Tidak berlaku surut.** Mengubah daftar cabang tidak menilai ulang scan yang sudah tersimpan. Penanda mencerminkan izin pada saat scan diterima, sehingga rekap masa lalu tidak berubah diam-diam. Scan lama yang tertahan diselesaikan lewat peninjauan.

### 2.3 Hasil peninjauan

Hasil peninjauan disimpan di tabel terpisah, `scan_reviews`:

| Kolom | Isi |
|---|---|
| `scan_id` | Primary key, FK ke `attendance_scans` |
| `decision` | `DITERIMA` atau `DIABAIKAN` (CHECK) |
| `reason` | Wajib, maksimal 500 karakter |
| `decided_by` | Pengguna yang memutuskan |
| `decided_at` | Waktu keputusan |
| `version` | Untuk `@Version` |

- Baris scan asli tidak pernah diubah oleh peninjauan.
- **Scan tertahan** = scan berpenanda `CABANG_TIDAK_DIIZINKAN` yang belum punya keputusan `DITERIMA`. Ini mencakup yang belum ditinjau maupun yang diabaikan.
- Keputusan boleh diubah. Perubahan menimpa baris `scan_reviews`, dan nilai lama tersimpan di audit log.

## 3. Rekap

- `AttendanceService` membuang scan tertahan sebelum memanggil `DailyAttendanceCalculator`.
- Detail hari tetap menampilkan scan tersebut dengan label **"Tertahan"** atau **"Diabaikan"**, agar Owner/HR tahu ada scan yang tidak dihitung.
- Penanda hari baru `ADA_SCAN_TERTAHAN` dipakai jika hari itu memiliki scan yang belum ditinjau.
- Karena Rekan Kerja (K-083), dashboard, dan nanti penggajian semuanya memakai rekap yang sama, perilakunya otomatis konsisten.
- Respons Rekan Kerja tidak memuat scan, jadi tidak ada data baru yang bocor.

## 4. Mengatur cabang (Detail Karyawan)

- Kartu baru **"Cabang absen"** berisi daftar centang semua cabang aktif. Cabang penempatan tercentang, tidak bisa diubah, dan diberi keterangan "Penempatan".
- Endpoint: `PUT /api/employees/{id}/branches` dengan isi `{ "extraBranchIds": [...] }`, untuk Owner dan HR/Finance. Respons berisi daftar cabang terbaru beserta ringkasan dampaknya.
- **Mencabut cabang** (cabang yang sebelumnya tercentang dihilangkan centangnya), dalam satu transaksi:
  1. ENROLL aktif karyawan itu di mesin cabang yang dicabut dibatalkan.
  2. Setiap catatan jari (`EmployeeFingerprint`) yang mesinnya ada di cabang itu dilupakan, lalu `DELETE_TEMPLATE` dikirim ke mesin itu saja. Ini memakai mekanisme hapus jari K-102.
  3. Audit `UPDATE` karyawan mencatat daftar cabang sebelum dan sesudah, serta jumlah jari yang dihapus.
- Sebelum menyimpan pencabutan yang menghapus jari, web menampilkan konfirmasi, misalnya *"2 sidik jari di mesin Takka Cilong akan dihapus."* Jumlahnya dihitung di frontend dari data sidik jari yang sudah ditampilkan di halaman yang sama.
- Mesin X401 tidak dikirimi perintah (tidak didukung). Scan dari X401 di cabang yang dicabut tertahan lewat penanda §2.2.

## 5. Pendaftaran sidik jari

- `FingerprintService.startEnroll` menolak mesin yang cabangnya tidak diizinkan dengan 400: *"Karyawan tidak diizinkan absen di cabang mesin ini."*
- Pilihan mesin di form pendaftaran hanya menampilkan mesin di cabang yang diizinkan.

## 6. Peninjauan scan tertahan

- **Halaman baru "Scan Tertahan"** di menu Absensi, untuk Owner dan HR/Finance, dengan pola yang sama seperti halaman Scan PIN tak dikenal.
  - Filter: rentang tanggal (bawaan 30 hari terakhir) dan status (Belum ditinjau / Diterima / Diabaikan; bawaan Belum ditinjau).
  - Kolom: waktu scan, karyawan, cabang penempatan, mesin dan cabangnya, status, alasan, dan peninjau.
  - Aksi: **Masukkan ke rekap** dan **Abaikan**, untuk satu baris atau beberapa baris terpilih. Keduanya wajib diberi alasan.
- **Endpoint:**
  - `GET /api/attendance/held-scans?from&to&status`
  - `POST /api/attendance/held-scans/review` dengan isi `{ scanIds, decision, reason }`, maksimal 200 scan per permintaan.
  - Scan yang tidak berpenanda `CABANG_TIDAK_DIIZINKAN` ditolak 400.
- **Setelah keputusan:**
  - Satu audit `SCAN_REVIEW` dicatat per scan, berisi keputusan lama dan baru.
  - `AttendanceChangedEvent` dikirim setelah commit, sehingga Absensi dan dashboard diperbarui lewat SSE.
- **Dashboard.** Jumlah scan yang belum ditinjau tampil sebagai kartu "Scan tertahan" yang menaut ke halaman ini, seperti kartu "Scan dengan PIN tak dikenal".

## 7. Hak akses dan audit

- Owner dan HR/Finance: mengatur cabang, melihat, dan meninjau scan tertahan. Karyawan tidak punya akses (403).
- Semua endpoint baru masuk ke `SecurityConfig` dan `AccessControlTests`.
- Aksi audit baru: `SCAN_REVIEW`. Perubahan cabang memakai `UPDATE` pada entitas karyawan.

## 8. Pengujian

| ID | Skenario | Harapan |
|---|---|---|
| CB-01 | Scan di cabang penempatan | Masuk rekap, tanpa penanda cabang |
| CB-02 | Scan di cabang tambahan | Masuk rekap, penanda `CABANG_BERBEDA` |
| CB-03 | Scan di cabang yang tidak diizinkan | Tersimpan utuh, penanda `CABANG_TIDAK_DIIZINKAN`, tidak dihitung di rekap, hari diberi `ADA_SCAN_TERTAHAN` |
| CB-04 | Scan CB-03 diterima dengan alasan | Masuk rekap; baris scan asli tidak berubah; audit `SCAN_REVIEW` tercatat |
| CB-05 | Keputusan diubah dari diterima menjadi diabaikan | Keluar lagi dari rekap; audit memuat keputusan lama |
| CB-06 | Daftar cabang diubah setelah scan diterima | Penanda scan lama tidak berubah (tidak berlaku surut) |
| CB-07 | PIN tak dikenal dihubungkan ke karyawan, dan mesinnya ada di cabang yang tidak diizinkan | Scan diberi `CABANG_TIDAK_DIIZINKAN` |
| CB-08 | ENROLL ke mesin di cabang yang tidak diizinkan | 400 dengan pesan Bahasa Indonesia |
| CB-09 | Cabang tambahan dicabut, padahal ada jari di mesin cabang itu | Catatan jari dilupakan, `DELETE_TEMPLATE` hanya dikirim ke mesin itu, ENROLL aktif di sana dibatalkan, audit tercatat |
| CB-10 | Cabang penempatan diganti | Cabang lama menjadi cabang tambahan; tidak ada jari yang dihapus |
| CB-11 | Cabang penempatan dikirim di `extraBranchIds` | Diabaikan (tidak disimpan ganda) |
| CB-12 | Karyawan atau tanpa login memanggil endpoint baru | 403/401 (`AccessControlTests`) |
| CB-13 | Tinjauan dengan scan yang tidak berpenanda | 400, tidak ada perubahan |
| CB-14 | Dua peninjau memutuskan scan yang sama bersamaan | Satu berhasil, satu 409 (`@Version`) |

Frontend: kartu Cabang absen (penempatan terkunci, konfirmasi hapus jari), filter mesin di form pendaftaran, dan halaman Scan Tertahan (filter, pilih banyak, alasan wajib).

## 9. Dokumen yang diperbarui

- `keputusan.md`: K-106 berisi rincian ini (merinci K-103 dan mengganti arti `CABANG_BERBEDA` di K-065).
- `aturan-bisnis.md` §4.7.
- `pertanyaan-terbuka.md` Q-022: cara peninjauan terjawab.
- `docs/README.md` bila ada dokumen rencana baru.

## 10. Keputusan Claude yang perlu dicek pengguna

1. Cabang penempatan yang lama **otomatis tetap diizinkan** saat penempatan diganti, dan baru dicabut jika HR menghapus centangnya.
2. **Tidak berlaku surut:** mengubah daftar cabang tidak menilai ulang scan lama.
3. **Alasan wajib**, baik untuk menerima maupun mengabaikan scan tertahan.
4. Mencabut cabang **langsung menghapus sidik jari** di mesin cabang itu, setelah dikonfirmasi di layar.
5. Setelah migration, semua karyawan hanya diizinkan di cabang penempatannya sampai HR mencentang cabang tambahan.
