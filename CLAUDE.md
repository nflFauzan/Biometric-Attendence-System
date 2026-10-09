# CLAUDE.md

Panduan kerja untuk Claude Code di repository ini.

## Proyek

Sistem absensi fingerprint dan penggajian untuk satu klien: 3 cabang, 1 mesin rakitan per cabang (Wi-Fi), sekitar 25 karyawan. Server adalah pusat validasi dan perhitungan; mesin tidak pernah berbicara langsung dengan web.

**Baca dulu:** [docs/README.md](docs/README.md). Sumber kebenaran aturan adalah [docs/aturan-bisnis.md](docs/aturan-bisnis.md) dan [docs/keputusan.md](docs/keputusan.md).

## Stack (K-001)

Spring Boot 4 (Java 21, Maven) + Spring Data JPA + Flyway + PostgreSQL di `backend/`, dan React + Vite + TypeScript di `frontend/`. Seluruh sistem bisa dijalankan dengan `docker compose up --build`. Eksperimen lama (NestJS) hanya ada di git tag `archive/zkteco-adms-experiment`. Pengerjaan langsung di `main` (K-011).

## Aturan wajib

- Ikuti [docs/rencana-pengerjaan.md](docs/rencana-pengerjaan.md). Setiap fase butuh persetujuan pengguna sebelum dimulai.
- **Jangan hardcode aturan bisnis** (jam kerja, istirahat, lembur, tarif, upah). Semuanya dibaca dari Pengaturan Aturan yang berlaku pada tanggal kejadian.
- Uang memakai `BigDecimal`. Waktu memakai zona waktu yang eksplisit.
- Scan asli dari mesin tidak pernah diubah atau dihapus. Koreksi disimpan terpisah dan dicatat di audit log.
- Endpoint mesin hanya membalas sukses setelah data tersimpan, dan harus tahan terhadap scan ganda.
- Jangan mengarang kemampuan mesin. Komponen mesin belum diketahui; pakai simulator.
- Perubahan schema hanya lewat migration Flyway.
- Jangan commit credential atau `.env`.

## Wajib bertanya dulu jika

- Mengubah scope, arsitektur, schema database, kontrak API, atau aturan bisnis.
- Menemukan aturan yang tidak tercatat di dokumen. Jangan menebak; catat di `docs/pertanyaan-terbuka.md`.
- Akan menghapus file atau data, push, atau melakukan tindakan lain yang sulit dibatalkan.

## Alur kerja

Rencana → persetujuan → implementasi → test → perbarui dokumen.

- Test case di `docs/aturan-bisnis.md` (TC-xx) wajib menjadi test otomatis.
- Jangan pernah melaporkan test lulus tanpa benar-benar menjalankannya.
- Perbarui `docs/keputusan.md` untuk setiap keputusan baru.
- Dokumentasi ditulis dalam Bahasa Indonesia.
