# Kebutuhan Sistem

## Gambaran

Web untuk mengelola karyawan, absensi, dan gaji bagi **satu klien** dengan **3 cabang** dan sekitar **25 karyawan** (jumlahnya bisa berubah). Setiap cabang memakai **1 mesin fingerprint rakitan sendiri** yang terhubung ke server lewat **Wi-Fi**.

Mesin hanya mengirim data scan ke server. **Server adalah pusat validasi dan perhitungan.** Web mengambil data dari server, dan mesin tidak pernah berbicara langsung dengan web.

## Role (disepakati)

| Fitur | Owner | HR/Finance | Karyawan |
| --- | --- | --- | --- |
| Dashboard dan semua laporan, ekspor Excel/PDF | ✓ | ✓ | — |
| Data karyawan, cabang, mesin, dan pendaftaran sidik jari | ✓ | ✓ | — |
| Monitoring absensi dan koreksi scan | ✓ | ✓ | — |
| Menyetujui izin/cuti | ✓ | ✓ | — |
| Memutuskan upah harian dan nominal lembur | ✓ | ✓ | — |
| Payroll, kasbon, BPJS, finalisasi gaji | ✓ | ✓ | — |
| Modul Pengaturan Aturan | ✓ | — | — |
| Kelola akun HR/Finance | ✓ | — | — |
| Melihat audit log | ✓ | — | — |
| Mengajukan izin/cuti + surat pendukung, melihat statusnya | — | — | ✓ |
| Melihat riwayat absensi sendiri | — | — | ✓ (belum dikonfirmasi) |

HR dan Finance adalah **satu role** dengan akses yang sama (K-002). HR/Finance hanya boleh mengelola akun Karyawan; kelola akun Owner/HR dan audit log hanya untuk Owner (K-016).

## Fitur per menu

**Area pengelola**

- **Dashboard:** yang hadir hari ini, yang terlambat, lembur berjalan, ringkasan per cabang, dan status mesin (online/offline).
- **Master data:** karyawan, cabang, kategori karyawan, akun, mesin absensi, dan pendaftaran sidik jari.
- **Absensi:** monitoring real-time, riwayat, rekap harian, dan koreksi scan.
- **Izin & cuti:** daftar pengajuan, persetujuan, dan dokumen pendukung.
- **Penggajian:** periode gaji (mingguan/bulanan), keputusan upah harian dan nominal lembur, kasbon, BPJS, dan finalisasi.
- **Laporan:** rekap harian, jam kerja, lembur, karyawan, dan penggajian. Ekspor Excel/PDF.
- **Pengaturan Aturan (Owner):** lihat [aturan-bisnis.md](aturan-bisnis.md#modul-pengaturan-aturan).
- **Audit log (Owner).**

**Area karyawan**

- Pengajuan izin/cuti dengan unggah surat, dan status pengajuannya.
- Riwayat absensi sendiri (belum dikonfirmasi).
- Profil dan ganti password.

## Kebutuhan non-fungsional

| Kebutuhan | Keterangan |
| --- | --- |
| Real-time | Scan tampil di dashboard dalam hitungan detik selama mesin online |
| Tidak ada data hilang | Scan tidak boleh hilang saat Wi-Fi atau server bermasalah (lihat [arsitektur.md](arsitektur.md)) |
| Retensi | Klien ingin riwayat absensi dan gaji **disimpan seumur hidup**. Perlu backup terjadwal dan uji restore |
| Jejak audit | Setiap koreksi scan, perubahan nominal, perubahan aturan, dan ekspor tercatat |
| Zona waktu | Dikonfigurasi eksplisit (nilai awal `Asia/Jakarta`), tidak bergantung pada komputer pengguna |
| Uang | Semua nominal dihitung dengan tipe desimal (tanpa floating point) |
| Privasi | Data biometrik dan gaji termasuk data pribadi (UU PDP No. 27/2022). Akses dibatasi, dan data biometrik karyawan yang keluar dihapus dari mesin |
| Ekspor | Excel dan PDF |
