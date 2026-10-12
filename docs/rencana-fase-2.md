# Rencana Teknis — Fase 2: Pengaturan Aturan + Mesin Perhitungan

**Status: disetujui dan diimplementasikan 2026-10-12; menunggu checkpoint pengguna.** Keputusan K-046–K-053 tercatat di [keputusan.md](keputusan.md).

Catatan implementasi:
- TC-08 di Fase 2 hanya bagian mesin perhitungan (koreksi menggantikan jam scan, scan asli utuh). Penyimpanan koreksi dan audit-nya diuji di Fase 3.
- Pertanyaan baru dari implementasi: Q-026 (koreksi aturan berlaku surut), Q-027 (libur per cabang), Q-028 (jeda antara jam pulang dan batas lembur).
Acuan: [rencana-pengerjaan.md](rencana-pengerjaan.md) Fase 2, [aturan-bisnis.md](aturan-bisnis.md) §3–5, §10, §13.

## 1. Keputusan yang sudah dipilih pengguna (2026-10-12)

| ID usulan | Keputusan |
|---|---|
| K-046 | Prioritas aturan **per kolom**: Karyawan > Kategori > Global. Kolom kosong mewarisi level di atasnya. Sistem menunjukkan asal setiap nilai. |
| K-047 | **Tidak ada seed** nilai aturan. Owner mengisi aturan Global lewat UI. Selama aturan wajib belum lengkap, perhitungan menolak dengan pesan "Aturan belum diatur: …". |
| K-048 | Istirahat **opsional**: jam mulai/selesai istirahat boleh kosong. Kosong = tidak ada pengurangan dan rekap diberi penanda `ISTIRAHAT_BELUM_DIATUR`. |
| K-049 | Aturan kerja dan tarif hanya diubah **Owner**; HR/Finance hanya melihat. Kalender libur dikelola **Owner dan HR/Finance**. Karyawan tidak punya akses. |

## 2. Usulan tambahan yang perlu disetujui

| ID usulan | Usulan | Alasan |
|---|---|---|
| K-050 | Versi aturan **tidak bisa diubah/dihapus setelah tanggal berlakunya tiba** (zona waktu `Asia/Jakarta`). Perubahan = membuat versi baru dengan tanggal berlaku baru. Versi yang belum berlaku boleh diubah atau dihapus (tercatat di audit log). | Menjamin "perubahan aturan tidak mengubah hasil pada tanggal sebelum tanggal berlakunya". Koreksi berlaku surut (K-033) butuh prosedur terpisah dan dicatat di pertanyaan terbuka. |
| K-051 | Kalender libur berlaku **untuk semua cabang** (belum per cabang). | Tidak ada informasi libur khusus cabang; per cabang bisa ditambah lewat migration nanti. |
| K-052 | Mesin perhitungan menyimpan durasi **persis (detik)**, tanpa pembulatan. | Aturan pembulatan belum diketahui (Q-008). |
| K-053 | Hari bukan hari kerja atau hari libur: sistem hanya menghitung total durasi hadir, **tanpa** memisahkan jam normal/lembur, dan memberi penanda `PERLU_TINJAUAN_HARI_LIBUR`. | Perlakuan kerja di hari libur belum diketahui (Q-005); aturan-bisnis §3.4 melarang satu rumus lembur dipukul rata. |

## 3. Schema — `V3__aturan_kerja_dan_kalender.sql`

```sql
CREATE TABLE work_rules (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope                   VARCHAR(10)  NOT NULL,          -- GLOBAL | CATEGORY | EMPLOYEE
    category_id             BIGINT       REFERENCES employee_categories (id),
    employee_id             BIGINT       REFERENCES employees (id),
    effective_from          DATE         NOT NULL,
    -- Semua parameter boleh NULL = mewarisi level di atasnya (K-046).
    work_start              TIME,
    work_end                TIME,
    break_start             TIME,
    break_end               TIME,
    late_tolerance_minutes  INTEGER      CHECK (late_tolerance_minutes >= 0),
    overtime_start          TIME,
    work_days               VARCHAR(30),                    -- mis. 'MON,TUE,WED,THU,FRI,SAT'
    daily_wage              NUMERIC(14,2) CHECK (daily_wage >= 0),
    overtime_rate_per_hour  NUMERIC(14,2) CHECK (overtime_rate_per_hour >= 0),
    base_salary             NUMERIC(14,2) CHECK (base_salary >= 0),
    note                    VARCHAR(255),
    created_by              VARCHAR(50)  NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT work_rules_scope_target CHECK (
        (scope = 'GLOBAL'   AND category_id IS NULL     AND employee_id IS NULL) OR
        (scope = 'CATEGORY' AND category_id IS NOT NULL AND employee_id IS NULL) OR
        (scope = 'EMPLOYEE' AND category_id IS NULL     AND employee_id IS NOT NULL)),
    CONSTRAINT work_rules_work_order  CHECK (work_start IS NULL OR work_end IS NULL OR work_end > work_start),
    CONSTRAINT work_rules_break_pair  CHECK ((break_start IS NULL) = (break_end IS NULL)),
    CONSTRAINT work_rules_break_order CHECK (break_start IS NULL OR break_end > break_start)
);
-- Satu versi per target per tanggal berlaku.
CREATE UNIQUE INDEX work_rules_version_uq
    ON work_rules (scope, COALESCE(category_id, 0), COALESCE(employee_id, 0), effective_from);

CREATE TABLE holidays (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    holiday_date  DATE         NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

Tarif (`daily_wage`, `overtime_rate_per_hour`, `base_salary`) ikut disimpan sebagai parameter bertanggal berlaku agar AC-06 bisa dibuktikan di level resolver. Nominal gaji **tidak dihitung** di Fase 2 (itu Fase 5).

## 4. Backend

Paket baru mengikuti konvensi modul domain:

| Paket | Isi |
|---|---|
| `workrule` | Entity, repository, DTO, `WorkRuleService` (validasi + audit), `WorkRuleController`. |
| `holiday` | CRUD kalender libur + audit. |
| `calculation` | **Kode murni tanpa Spring/DB:** `RuleResolver`, `EffectiveRule` (nilai + asal per kolom), `DailyAttendanceCalculator`, `DailyResult`. Ditambah `CalculationService` yang memuat data dari DB lalu memanggil kode murni. |

### Aturan mesin perhitungan harian

Input: tanggal, zona waktu cabang, daftar scan (`Instant`), koreksi opsional (jam masuk/pulang), `EffectiveRule`, status hari libur.

- Jam masuk = scan pertama, jam pulang = scan terakhir (usulan §4.3). Koreksi, bila ada, menggantikan nilai tersebut, dan sumbernya ditandai `KOREKSI`. Daftar scan input tidak pernah diubah.
- Hanya satu scan → status `TIDAK_LENGKAP`, jam masuk/pulang tidak diisi, tidak ada durasi (TC-07).
- Keterlambatan = `max(0, masuk − (jam_masuk + toleransi))` (TC-02, TC-03).
- Jam kerja normal = irisan `[max(masuk, jam_masuk), min(pulang, jam_pulang)]` dikurangi irisan dengan istirahat bila diatur (TC-01).
- Pulang lebih awal = `max(0, jam_pulang − pulang)`, hanya sebagai informasi.
- Kandidat lembur = `max(0, pulang − max(batas_lembur, masuk))` (TC-04, TC-05, TC-06).
- Hari bukan kerja/libur → K-053.
- Tidak ada potongan, pembulatan, atau nominal.

### API

| Method | Path | Owner | HR/Finance | Karyawan |
|---|---|:-:|:-:|:-:|
| GET | `/api/work-rules?scope=&categoryId=&employeeId=` | ✓ | ✓ | ✗ |
| POST | `/api/work-rules` | ✓ | ✗ | ✗ |
| PUT/DELETE | `/api/work-rules/{id}` (hanya versi yang belum berlaku) | ✓ | ✗ | ✗ |
| GET | `/api/work-rules/effective?employeeId=&date=` | ✓ | ✓ | ✗ |
| GET | `/api/holidays?year=` | ✓ | ✓ | ✗ |
| POST/PUT/DELETE | `/api/holidays[/{id}]` | ✓ | ✓ | ✗ |
| POST | `/api/calculation/daily-preview` (simulasi: karyawan, tanggal, jam scan) | ✓ | ✓ | ✗ |

Semua endpoint masuk `SecurityConfig` dan test matrix `AccessControlTests`. Setiap perubahan dicatat `AuditService` dalam transaksi yang sama.

## 5. Frontend

- Menu baru grup **Pengaturan**: "Aturan Kerja", "Kalender Libur", "Simulasi Perhitungan".
- **Aturan Kerja:** pilih level (Global/Kategori/Karyawan), daftar versi per tanggal berlaku, form tambah versi (kolom kosong = mewarisi). Versi yang sudah berlaku tampil terkunci. Panel "Aturan yang berlaku" untuk karyawan dan tanggal tertentu, lengkap dengan asal tiap nilai.
- **Kalender Libur:** daftar per tahun memakai `CrudPage`.
- **Simulasi Perhitungan:** pilih karyawan dan tanggal, isi jam scan, lalu tampilkan hasil (keterlambatan, jam normal, kandidat lembur, penanda). Dipakai untuk checkpoint karena scan asli baru ada di Fase 3.

## 6. Pengujian

| Test | Jenis |
|---|---|
| TC-01 sampai TC-07 | Unit test `DailyAttendanceCalculator` (murni) |
| TC-08 (bagian mesin): koreksi menggantikan jam scan, scan input tetap utuh, sumber ditandai `KOREKSI` | Unit test. Bagian penyimpanan koreksi + audit diuji di Fase 3. |
| Perubahan aturan di tengah periode: versi berlaku 15 Okt mengubah jam masuk → 14 Okt tetap pakai nilai lama | Unit test `RuleResolver` + integration test |
| AC-06 (level resolver): tarif Rp80.000 (Sep) → Rp100.000 (Okt) | Unit test `RuleResolver` |
| Prioritas per kolom, aturan wajib belum lengkap, istirahat kosong | Unit test |
| Versi yang sudah berlaku ditolak saat diubah/dihapus | Integration test |
| Hak akses semua endpoint baru | `AccessControlTests` |
| Audit log tercatat untuk aturan dan libur | Integration test |
| Halaman Aturan Kerja, Kalender Libur, Simulasi | Vitest + uji manual Playwright |

## 7. Dokumen yang diperbarui setelah implementasi

- `keputusan.md`: K-046 sampai K-053.
- `aturan-bisnis.md` §10: prioritas per kolom dan aturan versi.
- `pertanyaan-terbuka.md`: tambah pertanyaan prosedur koreksi aturan berlaku surut dan libur per cabang.
- `rencana-pengerjaan.md`: status Fase 2.
