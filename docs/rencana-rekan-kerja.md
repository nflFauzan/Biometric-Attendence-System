# Rencana: Daftar Rekan Kerja untuk Role Karyawan

> **Dibatalkan oleh K-127 (2026-10-25):** karyawan hanya melihat absensinya sendiri (halaman Absensi Saya). Dokumen ini disimpan sebagai riwayat.

**Status: diimplementasikan 2026-10-14, menunggu checkpoint.** Hasil rapat pengguna dengan tim: 3 role sudah benar, dan **semua karyawan** boleh melihat tabel seluruh karyawan beserta absensinya, tanpa data pribadi yang sensitif. Ini sub-pekerjaan A dari urutan A (rekan kerja) → B (tema glassmorphism) → C (Fase 5 penggajian) yang disetujui pengguna 2026-10-14.

## 1. Tujuan dan batasan

- Karyawan yang login melihat absensi semua karyawan aktif per tanggal, dan riwayat absensi per orang per bulan.
- Hanya kolom yang ada di daftar izin (§2). Pembatasannya ditegakkan di backend lewat DTO khusus, bukan dengan menyembunyikan kolom di frontend (K-023).
- Tidak menambah role dan tidak mengubah schema database.
- Tidak live lewat SSE. Saluran `/api/dashboard/stream` tetap khusus pengelola.

## 2. Daftar kolom (disetujui pengguna 2026-10-14)

| Data | Terlihat | Catatan |
| --- | --- | --- |
| No. karyawan, nama, cabang, jabatan | Ya | |
| Status hari itu | Ya | Status sederhana (§4), hanya karyawan aktif |
| Jam masuk, jam pulang | Ya | Nilai efektif setelah koreksi, tanpa menyebut bahwa ada koreksi |
| Terlambat, lembur (kandidat) | Ya | Dalam detik, dibulatkan ke bawah ke menit penuh |
| Izin | Ya, hanya "Izin" | Tanpa jenis, alasan, surat, atau ID pengajuan |
| Jenis izin, alasan, surat | Tidak | Data kesehatan (Sakit, surat dokter) |
| No. HP | Tidak | Data pribadi |
| PIN mesin | Tidak | Bisa disalahgunakan di mesin |
| Kategori karyawan | Tidak | Menyiratkan cara penggajian |
| Tanggal masuk dan keluar | Tidak | |
| Koreksi scan, alasan, nama mesin, flag scan | Tidak | Data internal HR |
| Gaji, kasbon, BPJS | Tidak pernah | K-024 |

## 3. API

Paket baru `com.biometricattendance.team`. Kedua endpoint bisa dibuka **semua role yang login** (Owner, HR/Finance, Karyawan). Endpoint ini masuk matriks `AccessControlTests`.

| Method | Path | Keterangan |
| --- | --- | --- |
| GET | `/api/team/attendance?date=&branchId=` | `date` default hari ini (zona waktu aturan), `branchId` opsional |
| GET | `/api/team/employees/{id}/attendance?from=&to=` | Rentang maksimal 62 hari (sama dengan `requireRange` yang sudah ada). Karyawan yang tidak aktif atau tidak ada dibalas 404 |

Bentuk respons, berupa record di `TeamDtos` yang **hanya** berisi kolom yang diizinkan:

```
TeamDay(date, status, checkIn, checkOut, lateSeconds, overtimeSeconds)
TeamRow(employeeId, employeeNumber, fullName, branchId, branchName, position, day: TeamDay)
TeamDailyResponse(date, branchId, branches: [BranchOption(id, name)], rows: [TeamRow])
TeamEmployeeResponse(employeeId, employeeNumber, fullName, branchName, position, from, to, days: [TeamDay])
```

- `checkIn`/`checkOut` berupa `LocalTime` polos yang diambil dari `TimeValue.time()`. `TimeValue` sendiri **tidak** dikirim, karena field `source`-nya membocorkan adanya koreksi.
- Jam dan durasi dibulatkan ke bawah ke menit penuh. Koreksi selalu berdetik 0 sedangkan scan asli tidak, sehingga tanpa pembulatan koreksi bisa ditebak dari detiknya (ditemukan saat review akhir 2026-10-14).
- Tanggal (atau akhir rentang) yang belum terjadi dibalas 400, agar tidak tampil "Tidak hadir" palsu dan izin mendatang tidak terlihat. Di frontend, tombol hari berikutnya nonaktif pada hari ini.
- `branches` berisi daftar cabang untuk filter, karena `/api/branches` hanya untuk pengelola.

## 4. Pemetaan status (`TeamStatus`)

Status dihitung dari `DayRecord` hasil `AttendanceService`, sehingga sama persis dengan rekap pengelola.

| Kondisi | `TeamStatus` |
| --- | --- |
| Ada izin **disetujui**, dan tidak ada kehadiran (status `IZIN`, atau aturan belum diatur tanpa scan/koreksi) | `IZIN` |
| Aturan kerja belum diatur | `BELUM_DIATUR` |
| `LENGKAP` | `HADIR` (terlambat ditandai lewat `lateSeconds > 0`) |
| `TIDAK_LENGKAP` | `TIDAK_LENGKAP` |
| `TIDAK_HADIR` | `TIDAK_HADIR` |
| `TIDAK_ADA_SCAN` (hari libur atau bukan hari kerja) | `LIBUR` |

- Izin yang masih **menunggu** tidak dibocorkan. Harinya tampil sesuai status kehadiran biasa, sama seperti pengaruhnya ke rekap (K-076).
- Hadir saat izin disetujui (`HADIR_SAAT_IZIN`) tampil sebagai `HADIR`, tanpa menyebut izin.
- Flag perhitungan dan flag scan tidak dikirim.

## 5. Frontend

- Menu **"Rekan Kerja"** di menu Karyawan, termasuk bottom nav di HP. Owner dan HR sudah punya menu Absensi yang lebih lengkap, jadi menu ini tidak ditambahkan untuk mereka. Endpoint-nya tetap boleh mereka buka.
- `/rekan-kerja`:
  - pilihan tanggal dengan panah hari sebelumnya/sesudahnya;
  - filter cabang dan kotak cari nama/No. karyawan;
  - tabel di desktop, kartu per orang di HP (< 768px, design-system §3);
  - badge status dengan ikon dan teks; "Terlambat" ditampilkan sebagai badge tambahan;
  - memuat ulang otomatis tiap 60 detik, plus tombol "Muat ulang".
- `/rekan-kerja/:id`:
  - riwayat satu bulan (default bulan berjalan) dengan navigasi bulan;
  - ringkasan jumlah Hadir, Terlambat, Tidak hadir, Izin, dan Tidak lengkap;
  - tabel/kartu per hari dengan kolom yang sama.

## 6. Pengujian

Backend (`TeamTests` + `AccessControlTests`):

1. Ketiga role mendapat 200 di kedua endpoint, dan anonim mendapat 401.
2. **Uji kebocoran**: skenario karyawan dengan No. HP, PIN, izin Sakit disetujui, koreksi scan beralasan, dan izin menunggu. JSON mentah respons tidak boleh mengandung kunci `phone`, `devicePin`, `categoryName`, `leaveTypeName`, `requestId`, `reason`, `scans`, `flags`, `correction`, `source`, dan tidak boleh mengandung teks "Sakit" maupun teks alasan koreksi.
3. Izin disetujui menjadi `IZIN`. Izin menunggu tidak mengubah status. Hadir saat izin tetap `HADIR`.
4. Jam efektif setelah koreksi muncul di `checkIn`/`checkOut` sebagai jam polos, tanpa keterangan sumber.
5. Filter cabang berlaku, dan karyawan nonaktif tidak tampil.
6. Riwayat karyawan nonaktif dibalas 404, dan rentang lebih dari 62 hari dibalas 400.

Frontend (`TeamPages.test.tsx`): tabel tampil dengan status Izin tanpa jenis, filter dan cari berjalan, klik baris membuka riwayat, dan menu Rekan Kerja hanya ada untuk Karyawan (`navigation.test`).

## 7. Dokumen yang diperbarui

- `keputusan.md`: **K-083** (karyawan boleh melihat absensi rekan kerja dengan daftar kolom §2) dan **K-084** (pemetaan status sederhana, izin tanpa jenis).
- `pertanyaan-terbuka.md` Q-016: riwayat absensi sendiri terjawab lewat halaman ini.
- `aturan-bisnis.md` bagian hak akses, `arsitektur.md` (paket `team`), dan `README.md` (indeks dokumen).
