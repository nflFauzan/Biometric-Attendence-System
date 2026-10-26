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

## Konvensi kode

- Backend: paket per modul domain (`branch`, `employee`, `account`, `audit`, ...), masing-masing berisi entity, repository, DTO (record), service, controller. Setiap perubahan data dicatat lewat `AuditService` di transaksi yang sama.
- Hak akses per URL diatur terpusat di `config/SecurityConfig`; setiap endpoint baru wajib masuk ke test matrix `AccessControlTests`.
- Error API berformat ProblemDetail; pesan untuk pengguna dalam Bahasa Indonesia.
- Frontend: komponen shadcn ada di `src/components/ui` (jangan diedit kecuali perlu); halaman daftar + form memakai `CrudPage`; panggilan API lewat `src/lib/api.ts` (menangani cookie sesi dan CSRF).

## Aturan Git Commit & Pembagian Kontributor per Fitur/Modul

Jika diminta untuk melakukan commit / "commit dan push" / "commit tim", Claude Code wajib mengikuti protokol berikut:

### 1. Prinsip Granularitas (Commit per Fitur / Modul)
- **Jangan memaksakan hanya 4 commit** jika perubahan mencakup beberapa fitur atau modul yang berbeda.
- Pecah commit secara logis dan atomik **per fitur / per modul**.
- Jika dalam satu siklus kerja terdapat beberapa modul berbeda (misal: Modul Cuti dan Modul Mesin), commit dibuat terpisah untuk masing-masing modul tersebut.
- Jumlah commit fleksibel (bisa 4, 6, 8, atau N commit) asalkan setiap commit merepresentasikan satu unit fitur/modul yang jelas dan terisolasi.

### 2. Pemetaan Author Berdasarkan 4 Role Akun
Setiap commit pada modul/fitur tertentu wajib di-commit dengan akun yang sesuai tanggung jawab perannya:

1. **`nflFauzan <naufalmnf1692006@gmail.com>`** (*Software Architecture / Full Stack Dev / DevOps*):
   - Migrasi database Flyway (`V*.sql`), Spring Security, arsitektur backend domain, model JPA, service core, konfigurasi server/properties, dan Docker orchestration.
2. **`aiinunzahra <ainunzahra273@gmail.com>`** (*Hardware Integration Engineer / UI-UX*):
   - Integrasi hardware & protokol alat (rakitan/ZKTeco), simulator perangkat, komponen UI/UX, formulir interaktif, dialog modal, desain visual/tema, dan halaman aplikasi web frontend.
3. **`PatoyUhuy <broka10cncprn@gmail.com>`** (*QA / Software Tester*):
   - Seluruh file pengujian otomatis: unit test backend, integration test, security test matrix, concurrency/race condition test, test fixtures, dan pengujian halaman/komponen frontend (`*.test.tsx`, `*.test.ts`, fetch mocks).
4. **`nisa262006 <sabrinafitri593@gmail.com>`** (*Project Manager / Business Analyst*):
   - Seluruh dokumen di direktori `docs/`: Product Requirements Document (PRD), spesifikasi teknis, rencana implementasi fitur, log keputusan arsitektur (`keputusan.md`), aturan bisnis (`aturan-bisnis.md`), alur sistem (`alur.md`), dan catatan pertanyaan terbuka.

### 3. Format Pesan Commit (Wajib Dipatuhi)
- **DILARANG menggunakan tanda kurung setelah tipe prefix.** Gunakan format langsung:
  - `feat: <penjelasan detail judul>`
  - `test: <penjelasan detail judul>`
  - `docs: <penjelasan detail judul>`
  - *(JANGAN gunakan `feat(leave):` atau `feat(frontend):`)*.
- **Pesan commit harus lengkap dan mendalam**:
  - Baris pertama: Judul deskriptif yang menjelaskan fitur/modul secara spesifik.
  - Baris kedua: Baris kosong.
  - Baris berikutnya: Poin-poin bullet (`- ...`) yang merinci perubahan teknis, file/komponen utama yang terpengaruh, dan alasan perubahannya.

### 4. Prosedur Eksekusi Commit
1. Jalankan `git status -u` untuk mengidentifikasi seluruh file yang berubah dan baru.
2. Kelompokkan file ke dalam unit fitur/modul yang terpisah.
3. Untuk setiap unit fitur/modul:
   - Pasang identitas akun yang sesuai: `git config user.name "..."` dan `git config user.email "..."`.
   - Stage hanya file-file milik modul tersebut: `git add <file-1> <file-2> ...`.
   - Commit dengan pesan yang detail dan lengkap.
4. Setelah seluruh modul/fitur selesai di-commit di lokal, lakukan verifikasi dengan `git log -n <N>` dan `git status`.
5. Jalankan `git push origin main` untuk mengirimkan seluruh rangkaian commit ke GitHub.
