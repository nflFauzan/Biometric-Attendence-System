# Rencana Pengerjaan

**Status: disetujui 2026-10-09.** Fase 0 dan Fase 1 selesai. Fase 2 diimplementasikan 2026-10-12 (rincian di [rencana-fase-2.md](rencana-fase-2.md)) dan menunggu checkpoint; Fase 3 menunggu persetujuan.

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

- API penerimaan scan dari mesin: kredensial per mesin, ID unik per scan, OK hanya setelah tersimpan, tahan scan ganda.
- **Simulator mesin** untuk mengirim scan, mensimulasikan offline lalu kirim ulang, dan scan ganda.
- Rekap harian otomatis, dashboard real-time (SSE), dan status mesin online/offline.
- Koreksi scan dengan alasan wajib dan audit log.

**Selesai jika:** test integrasi membuktikan tidak ada scan hilang atau ganda (termasuk skenario kirim ulang), dan dashboard berubah dalam hitungan detik setelah simulator mengirim scan.

## Fase 4 — Izin dan cuti

- Pengajuan oleh karyawan dengan unggah surat, persetujuan oleh HR/Finance/Owner, dan efeknya ke rekap.
- Detail jenis dan kuota menunggu jawaban pertanyaan terbuka no. 10.

## Fase 5 — Penggajian

- Periode mingguan dan bulanan sesuai kategori.
- Draf gaji, keputusan upah harian per hari, dan nominal lembur.
- Finalisasi dan penguncian periode.
- Kasbon dan BPJS dibuat setelah aturannya diketahui (pertanyaan 7–8). Sampai saat itu, disediakan input potongan manual.

**Selesai jika:** TC-P1 sampai TC-P4 lulus, dan periode final tidak bisa diubah.

## Fase 6 — Laporan dan ekspor

- Rekap harian, jam kerja, lembur, karyawan, dan penggajian.
- Ekspor Excel/PDF, dengan setiap ekspor tercatat di audit log.

## Fase 7 — Integrasi mesin asli dan rilis

- Menyesuaikan kontrak dengan prototipe mesin, lalu uji end-to-end di satu cabang sebelum ketiganya.
- Deployment, TLS, backup terjadwal, dan uji restore. Fase ini menunggu keputusan hosting.

## Yang bisa dikerjakan paralel

- Frontend bisa mulai di setiap fase memakai kontrak OpenAPI dan data contoh.
- Tim hardware membangun prototipe mesin berdasarkan "kebutuhan minimal untuk mesin" di [arsitektur.md](arsitektur.md).

## Definition of Done (setiap tugas)

- Sesuai dokumen. Kalau ada perubahan aturan, dokumen diperbarui di commit yang sama.
- Test relevan ditulis dan **benar-benar dijalankan** hingga lulus.
- Tidak ada credential di repo.
- Perubahan database lewat migration Flyway.
