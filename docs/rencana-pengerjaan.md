# Rencana Pengerjaan

**Status: disetujui 2026-10-09.** Fase 0 dan Fase 1 selesai. Fase 2 diimplementasikan 2026-10-12 (rincian di [rencana-fase-2.md](rencana-fase-2.md)) dan menunggu checkpoint. Fase 3 diimplementasikan 2026-10-12 (rincian di [rencana-fase-3.md](rencana-fase-3.md)) dan menunggu checkpoint. Skenario X-01–X-08 dengan mesin X401 lulus 2026-10-12 (hasil di [panduan-mesin-x401.md](panduan-mesin-x401.md) Tahap 6). Fase 4 diimplementasikan 2026-10-13 (rincian di [rencana-fase-4.md](rencana-fase-4.md)) dan disetujui 2026-10-14. Daftar rekan kerja untuk karyawan diimplementasikan 2026-10-14 ([rencana-rekan-kerja.md](rencana-rekan-kerja.md)).

Prinsip urutan:
- Bagian yang paling menentukan benar-tidaknya gaji (aturan dan perhitungan) dikerjakan dan diuji lebih dulu.
- Mesin asli disambungkan paling akhir. Sebelum itu dipakai simulator.
- Setiap fase diakhiri **checkpoint**: hasil ditunjukkan, test dijalankan, lalu disetujui sebelum lanjut.

## Fase 0 — Persiapan repo

- Buat git tag arsip untuk eksperimen lama (K-009). Push tag ke GitHub hanya dengan izin.
- Hapus `backend/` NestJS lama, karena isinya sudah tersimpan di tag.
- Buat struktur baru:
  - `backend/`: Spring Boot, build tool sesuai pilihan tim (Maven/Gradle)
  - `frontend/`: React + Vite + TypeScript
  - `docker-compose.yml`: PostgreSQL untuk development, credential dari `.env` dan tidak di-commit
- Tambahkan health check dan satu test contoh yang lulus di backend dan frontend.

**Selesai jika:** `docker compose up` menjalankan database, backend dan frontend bisa dijalankan, dan test contoh lulus.

## Fase 1 — Akun, role, dan master data

- Login, 3 role (Owner, HR/Finance, Karyawan), dan ganti password.
- CRUD cabang, kategori karyawan, karyawan, dan mesin (tanpa integrasi).
- Audit log dasar.
- Kerangka web: layout, menu sesuai role, dan halaman master data.

**Selesai jika:** setiap role hanya melihat menu dan data miliknya (dibuktikan test otorisasi), dan perubahan data tercatat di audit log.

## Fase 2 — Pengaturan Aturan + mesin perhitungan

- Modul Pengaturan Aturan dengan tanggal berlaku, per kategori, dan per karyawan.
- Kalender hari libur.
- **Mesin perhitungan rekap harian** sebagai kode murni, diuji dengan TC-01 sampai TC-08 dari [aturan-bisnis.md](aturan-bisnis.md), ditambah kasus perubahan aturan di tengah periode.

**Selesai jika:** semua test case lulus, dan perubahan aturan tidak mengubah hasil pada tanggal sebelum tanggal berlakunya.

## Fase 3 — Absensi

- **Inti penerimaan scan** yang tidak bergantung jenis mesin (K-054): mesin terdaftar, OK hanya setelah tersimpan, tahan scan ganda.
- **Adapter ZK-Push** untuk mesin uji Solution X401 (K-055), disusun dari log tangkapan asli ([panduan-mesin-x401.md](panduan-mesin-x401.md) Tahap 1–4, Q-029).
- Pemetaan karyawan ke PIN mesin (Q-031).
- **Simulator mesin** yang berbicara protokol ZK-Push untuk mengirim scan, mensimulasikan offline lalu kirim ulang, dan scan ganda.
- Rekap harian otomatis, dashboard real-time (SSE), dan status mesin online/offline.
- Koreksi scan dengan alasan wajib dan audit log.

**Selesai jika:** test integrasi membuktikan tidak ada scan hilang atau ganda (termasuk skenario kirim ulang), dashboard berubah dalam hitungan detik setelah simulator mengirim scan, dan skenario X-01 sampai X-08 di panduan X401 lulus dengan mesin sungguhan.

## Fase 4 — Izin dan cuti

- Pengajuan oleh karyawan dengan unggah surat, persetujuan oleh HR/Finance/Owner, dan efeknya ke rekap.
- Notifikasi langsung ke Owner/HR dan surat pengajuan yang bisa dicetak/disimpan PDF (K-081, K-082).
- Jenis izin diatur Owner; kuota, dampak ke gaji, dan izin per jam menunggu Q-013 (K-070–K-072).

**Selesai jika:** alur ajukan → setujui/tolak/batalkan teruji (termasuk tumpang tindih dan keputusan bersamaan), izin disetujui tercermin di rekap sebagai Izin, pengajuan yang menunggu tidak mengubah rekap, dan karyawan tidak bisa melihat pengajuan atau surat milik karyawan lain.

## Fase 5 — Penggajian

- Periode mingguan dan bulanan sesuai kategori.
- Draf gaji, keputusan upah harian per hari, dan nominal lembur.
- Finalisasi dan penguncian periode.
- Kasbon dan BPJS dibuat setelah aturannya diketahui (pertanyaan 7–8). Sampai saat itu, disediakan input potongan manual.

**Selesai jika:** TC-P1 sampai TC-P4 lulus, dan periode final tidak bisa diubah.

## Fase 6 — Laporan dan ekspor

- Rekap harian, jam kerja, lembur, karyawan, dan penggajian.
- Ekspor Excel/PDF, dengan setiap ekspor tercatat di audit log.
- Format laporan mengikuti dokumen AS-IS client ([dokumen-as-is.md](dokumen-as-is.md), K-105). Laporan yang hanya butuh data absensi (detail absensi dan kisi kehadiran) boleh dikerjakan sebelum Fase 5.

## Revisi 2026-10-26 — Profil, akun, dan pesan WA karyawan

- Profil lengkap karyawan (V17, K-129) dan label role "Finance & Accounting" (K-132), [rencana](superpowers/plans/2026-10-26-profil-dan-impor-karyawan.md).
- Impor template Excel karyawan (K-130) dibatalkan dan dihapus. Gantinya: tambah karyawan sekaligus akun dengan pesan WA siap kirim, tanggal masuk otomatis, dan hak tulis Finance & Accounting atas data karyawan (K-133 sampai K-135, [rencana](superpowers/plans/2026-10-26-akun-karyawan-pesan-wa.md)).
- Data asli client tidak masuk repo; 28 karyawan dimasukkan lewat form setelah cabang Takka 1, Takka 2, dan Takka Cilong dibuat.
- Impor rekap Excel dari mesin cabang lain menyusul sebagai pekerjaan terpisah setelah format file dari client diterima.

## Revisi meeting client 2026-10-23 — Persetujuan ganda izin (diimplementasikan 2026-10-24)

- Penanda "butuh persetujuan Owner" di master Jenis Izin, dengan alur HR/Finance lalu Owner (K-109, TC-19). Pekerjaan ini mengubah schema, API, dan UI izin, sehingga rinciannya ditulis sebagai rencana tersendiri sebelum dikerjakan.
- Test otomatis TC-18 (masuk 09.00 = 7 jam, K-110). Mesin perhitungan tidak berubah.

**Diimplementasikan 2026-10-24** (K-113 sampai K-118, [spec](superpowers/specs/2026-10-24-persetujuan-ganda-surat-izin-design.md)):

- Status bertahap dan persetujuan ganda HR lalu Owner (migration V12, V13), TC-19 diuji di `LeaveApprovalTests.doubleApprovalNeedsHrThenOwner`.
- Surat PDF dari server dengan QR dan spesimen tanda tangan (V14), halaman verifikasi publik `/verifikasi/:token`, serta pencatatan keputusan Owner oleh HR dengan bukti.
- Area karyawan mobile-first: Beranda, navigasi bawah, alur pengajuan 4 langkah, dan Rekan Kerja berupa kartu di HP (Rekan Kerja diganti Absensi Saya oleh K-127).
- Menunggu uji manual di HP 375 px (terang dan gelap) dan templat surat dari client (D-05) bila ada.

## Fase 7 — Integrasi mesin asli dan rilis

**Pembaruan 2026-10-23 (K-112):** alat rakitan (sensor R307, K-107) dipasang di satu cabang dulu. Dua cabang lain belum memakai sistem (Q-045).

- Menyesuaikan kontrak dengan prototipe mesin rakitan (adapter baru atau meniru ZK-Push, Q-030), lalu uji end-to-end di satu cabang sebelum ketiganya. Skenario uji X-01 sampai X-08 dari mesin X401 dipakai ulang.
- Deployment, TLS, backup terjadwal, dan uji restore. Fase ini menunggu keputusan hosting.

## Yang bisa dikerjakan paralel

- Frontend bisa mulai di setiap fase memakai kontrak OpenAPI dan data contoh.
- Tim hardware membangun prototipe mesin berdasarkan "kebutuhan minimal untuk mesin" di [arsitektur.md](arsitektur.md).

## Definition of Done (setiap tugas)

- Sesuai dokumen. Kalau ada perubahan aturan, dokumen diperbarui di commit yang sama.
- Test relevan ditulis dan **benar-benar dijalankan** hingga lulus.
- Tidak ada credential di repo.
- Perubahan database lewat migration Flyway.
