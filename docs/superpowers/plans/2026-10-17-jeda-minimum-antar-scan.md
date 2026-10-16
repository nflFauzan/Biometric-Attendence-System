# Jeda Minimum Antar Scan — Rencana Implementasi

> **Untuk agent pelaksana:** WAJIB memakai superpowers:subagent-driven-development (disarankan) atau superpowers:executing-plans untuk mengerjakan rencana ini per task. Langkah memakai checkbox (`- [ ]`).

**Tujuan:** menambah kolom aturan "Jeda minimum antar scan (menit)" agar scan berdekatan (spam scan) dihitung sebagai satu kejadian saat menentukan jam masuk dan pulang.

**Arsitektur:** parameter baru `minScanGapMinutes` mengikuti pola parameter aturan yang sudah ada (`RuleValues` → `RuleField` → `RuleResolver` per kolom, K-046). `DailyAttendanceCalculator` mengelompokkan scan berdasarkan jeda sebelum memilih jam masuk/pulang, sementara daftar scan asli di `DailyResult.scanTimes` tetap utuh.

**Tech stack:** Spring Boot 4 (Java 21), Flyway, PostgreSQL, JUnit 5 + AssertJ; React + TypeScript + Vitest.

**Spec:** [`docs/superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md`](../specs/2026-10-16-pendaftaran-sidik-jari-web-design.md) §8 (K-092). Ini rencana 1 dari 7 sesuai §14 spec.

## Batasan global

- Tidak ada nilai bawaan di kode: **kosong (`null`) = tanpa penggabungan**, perilaku sama persis dengan sekarang (K-047).
- Jeda dihitung dari **scan pertama setiap kelompok**, bukan dari scan sebelumnya. Kelompok baru dimulai saat `scan − awal kelompok ≥ jeda`.
- Jam masuk = scan pertama dari kelompok pertama. Jam pulang = **scan pertama dari kelompok terakhir**.
- Scan asli tidak diubah atau dibuang. `DailyResult.scanTimes` tetap berisi semua scan hari itu.
- Parameter ini **tidak wajib** untuk perhitungan harian (tidak masuk `REQUIRED_FOR_DAILY`).
- Validasi input: bilangan bulat 1–240. Pesan: `"Jeda minimum minimal 1 menit"` / `"Jeda minimum maksimal 240 menit"`. Batas 240 adalah batas validasi teknis, bukan aturan bisnis.
- Perubahan schema hanya lewat migration Flyway baru `V6__jeda_minimum_scan.sql`.
- Dokumentasi dan pesan pengguna dalam Bahasa Indonesia.
- **Commit dan push dikerjakan pengguna.** Langkah "Commit" di setiap task berarti: berhenti, laporkan file yang berubah, dan biarkan pengguna yang commit.

## Fokus review

1. **Koreksi + penggabungan:** ada koreksi jam masuk dan scan spam di sore hari. Jam pulang harus tetap scan pertama kelompok terakhir, dan koreksi tetap menang.
2. **Scan dari tanggal lain** (sudah difilter per tanggal) tidak boleh ikut memulai kelompok di tanggal ini. Penggabungan dilakukan setelah filter tanggal.
3. **Versi aturan per tanggal:** jeda diisi mulai 1 Nov. Rekap 31 Okt tidak terpengaruh.
4. **Aturan karyawan menimpa global** hanya untuk kolom ini (K-046): global jeda 5, karyawan hanya mengisi toleransi. Jeda karyawan tetap 5.
5. **Form web mengosongkan nilai:** menghapus isi kolom di form harus mengirim `null`, bukan `0` (0 akan ditolak validasi).

---

### Task 1: Penggabungan scan di mesin perhitungan

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/calculation/RuleField.java` (tambah konstanta)
- Modify: `backend/src/main/java/com/biometricattendance/calculation/RuleValues.java` (field, `defines`, builder)
- Modify: `backend/src/main/java/com/biometricattendance/calculation/EffectiveRule.java` (getter)
- Modify: `backend/src/main/java/com/biometricattendance/calculation/RuleResolver.java:56` (case per kolom)
- Modify: `backend/src/main/java/com/biometricattendance/calculation/DailyAttendanceCalculator.java:39-63`
- Modify: `docs/aturan-bisnis.md` (§4.3 dan tabel TC §13)
- Test: `backend/src/test/java/com/biometricattendance/calculation/DailyAttendanceCalculatorTests.java`, `RuleResolverTests.java`

**Interfaces:**
- Produces:
  - `RuleField.MIN_SCAN_GAP("jeda minimum antar scan")`
  - Komponen record baru `Integer minScanGapMinutes`, diletakkan **terakhir** di `RuleValues` (setelah `baseSalary`), plus `RuleValues.Builder#minScanGapMinutes(Integer)`
  - `EffectiveRule#minScanGapMinutes(): Integer`
  - `static List<LocalTime> DailyAttendanceCalculator.groupStarts(List<LocalTime> sortedTimes, Integer gapMinutes)` (package-private): mengembalikan awal setiap kelompok; `gapMinutes == null` mengembalikan list input apa adanya.

- [ ] **Step 1: Tulis test yang gagal** di `DailyAttendanceCalculatorTests`. Tambah helper `ruleWithGap(Integer gapMinutes)` yang sama dengan `ruleWithoutBreak()` plus `.minScanGapMinutes(gapMinutes)`.

```java
@Test
void tc17SpamScanWithoutCheckOutIsIncomplete() {
	DailyResult result = calculate(ruleWithGap(5), "08:00:00", "08:00:04", "08:00:08");

	assertThat(result.status()).isEqualTo(AttendanceStatus.TIDAK_LENGKAP);
	assertThat(result.scanTimes()).hasSize(3); // scan asli tetap utuh
}

@Test
void emptyGapKeepsCurrentBehaviour() {
	DailyResult result = calculate(ruleWithGap(null), "08:00:00", "08:00:08");

	assertThat(result.checkIn().time()).isEqualTo(LocalTime.of(8, 0, 0));
	assertThat(result.checkOut().time()).isEqualTo(LocalTime.of(8, 0, 8));
}

@Test
void scanExactlyAtGapStartsNewGroup() {
	DailyResult result = calculate(ruleWithGap(5), "08:00:00", "08:05:00");

	assertThat(result.checkOut().time()).isEqualTo(LocalTime.of(8, 5, 0));
}

@Test
void spamAtCheckOutUsesFirstScanOfLastGroup() {
	DailyResult result = calculate(ruleWithGap(5), "08:00", "17:00:00", "17:00:04");

	assertThat(result.checkOut().time()).isEqualTo(LocalTime.of(17, 0, 0));
	assertThat(result.status()).isEqualTo(AttendanceStatus.LENGKAP);
}

@Test
void continuousSpamLongerThanGapMakesTwoEvents() {
	// jeda dihitung dari awal kelompok: 08:00 → kelompok 1; 08:05:30 → kelompok 2
	DailyResult result = calculate(ruleWithGap(5), "08:00:00", "08:02:00", "08:04:00", "08:05:30", "08:07:00");

	assertThat(result.checkIn().time()).isEqualTo(LocalTime.of(8, 0, 0));
	assertThat(result.checkOut().time()).isEqualTo(LocalTime.of(8, 5, 30));
}

@Test
void correctionStillWinsOverGroupedScans() {
	// koreksi jam masuk 07:55; scan sore spam → pulang = 17:00:00
	DailyResult result = DailyAttendanceCalculator.calculate(new DailyInput(MONDAY, JAKARTA,
			scans(MONDAY, "08:10", "17:00:00", "17:00:03"),
			new DailyInput.Correction(LocalTime.of(7, 55), null), ruleWithGap(5), false));

	assertThat(result.checkIn().source()).isEqualTo(TimeSource.KOREKSI);
	assertThat(result.checkOut().time()).isEqualTo(LocalTime.of(17, 0, 0));
}
```

Di `RuleResolverTests`: `minScanGapIsResolvedPerField`. Global mengisi `minScanGapMinutes(5)`, karyawan hanya mengisi `lateToleranceMinutes(10)`. Assert `rule.minScanGapMinutes()` = 5, `rule.source(RuleField.MIN_SCAN_GAP).level()` = `GLOBAL`, dan `missingForDailyCalculation()` tidak memuat `MIN_SCAN_GAP`. Tambah juga `minScanGapFollowsEffectiveDate`: versi global 1 Okt tanpa jeda, versi global 1 Nov `minScanGapMinutes(5)`. Resolve 31 Okt → `null`; resolve 1 Nov → 5.

> Sesuaikan konstruktor `DailyInput`/`Correction` dengan signature yang ada di `DailyInput.java`. Assertion-nya yang mengikat.

- [ ] **Step 2: Jalankan test, pastikan gagal**

Run: `cd backend && ./mvnw -q test -Dtest=DailyAttendanceCalculatorTests,RuleResolverTests`
Expected: kompilasi gagal (`minScanGapMinutes` belum ada).

- [ ] **Step 3: Implementasi**
- Tambah `MIN_SCAN_GAP` ke `RuleField`, komponen `minScanGapMinutes` ke `RuleValues` (case `defines`, builder, `build()`), getter di `EffectiveRule`, dan case `MIN_SCAN_GAP -> to.minScanGapMinutes(from.minScanGapMinutes())` di `RuleResolver`.
- Perbarui pemanggil konstruktor posisional `new RuleValues(...)` (`WorkRule.java:146`, `WorkRuleDtos.java:44`) dengan sementara mengirim `null` di posisi terakhir. Task 2 mengisinya dengan nilai sebenarnya.
- Di `DailyAttendanceCalculator.calculate`, setelah `scanTimes` dibuat, hitung `List<LocalTime> events = groupStarts(scanTimes, rule.minScanGapMinutes())`. Pakai `events` (bukan `scanTimes`) untuk cabang `size() >= 2` / `size() == 1` / `isEmpty()` dalam memilih masuk/pulang. `scanTimes` tetap diteruskan ke `DailyResult`.
- `groupStarts`: telusuri list terurut, simpan `start` kelompok berjalan, dan mulai kelompok baru jika `!Duration.between(start, t).minus(Duration.ofMinutes(gap)).isNegative()` (perbandingan `Duration`, bukan pembulatan menit).
- Perbarui Javadoc kelas: baris "Jam masuk = scan pertama, jam pulang = scan terakhir" menjadi "…setelah scan berdekatan digabung menurut jeda minimum (K-092)".

- [ ] **Step 4: Jalankan test, pastikan lulus**

Run: `cd backend && ./mvnw -q test -Dtest=DailyAttendanceCalculatorTests,RuleResolverTests`
Expected: semua lulus, termasuk test TC lama yang sudah ada.

- [ ] **Step 5: Perbarui `docs/aturan-bisnis.md`**
- §4.3: tambah paragraf "Scan berdekatan" yang menjelaskan bahwa jika *Jeda minimum antar scan* diisi, scan yang berjarak kurang dari jeda dari scan pertama kelompoknya dihitung satu kejadian; kosong = tanpa penggabungan; scan asli tetap tersimpan (K-092).
- Tabel test case §13: tambah baris `| TC-17 | Jeda minimum 5 menit; scan 08.00.00, 08.00.04, 08.00.08; tidak ada scan lain | Satu kejadian scan, status Tidak Lengkap, ketiga scan asli tetap tersimpan |`.
- Perbarui Javadoc kelas test agar menyebut TC-17.

- [ ] **Step 6: Commit (oleh pengguna).** Laporkan daftar file yang berubah.

---

### Task 2: Simpan dan kelola kolom lewat API

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__jeda_minimum_scan.sql`
- Modify: `backend/src/main/java/com/biometricattendance/workrule/WorkRule.java` (field + `toValues`/`apply` di sekitar baris 67, 146, 155)
- Modify: `backend/src/main/java/com/biometricattendance/workrule/WorkRuleDtos.java` (request baris 29–44, response baris 51–64)
- Modify: `docs/keputusan.md` (daftar §2 + detail K-092)
- Test: `backend/src/test/java/com/biometricattendance/workrule/WorkRuleTests.java`

**Interfaces:**
- Consumes: `RuleValues.minScanGapMinutes` dan `RuleField.MIN_SCAN_GAP` dari Task 1.
- Produces: field JSON `minScanGapMinutes` (angka atau `null`) di request/response `/api/work-rules`, dan kunci `sources.MIN_SCAN_GAP` di aturan efektif.

- [ ] **Step 1: Tulis test yang gagal** di `WorkRuleTests`, mengikuti pola test di baris 150–161:
  - `minScanGapIsSavedAndReturned`: buat aturan global dengan `"minScanGapMinutes":5`. Respons berisi `$.values.minScanGapMinutes` = 5. Aturan efektif karyawan berisi `$.sources.MIN_SCAN_GAP.level` = `"GLOBAL"`.
  - `minScanGapRejectsZeroAndAbove240`: nilai `0` → 400 dengan pesan `"Jeda minimum minimal 1 menit"`; `241` → 400 dengan pesan `"Jeda minimum maksimal 240 menit"`.
  - `minScanGapCanBeOmitted`: request tanpa field → tersimpan `null`.

- [ ] **Step 2: Jalankan test, pastikan gagal**

Run: `cd backend && ./mvnw -q test -Dtest=WorkRuleTests`
Expected: FAIL (field diabaikan atau `null`). Test integrasi butuh Docker untuk database test.

- [ ] **Step 3: Implementasi**
- `V6__jeda_minimum_scan.sql`:
  ```sql
  ALTER TABLE work_rules ADD COLUMN min_scan_gap_minutes INTEGER;
  ALTER TABLE work_rules ADD CONSTRAINT work_rules_min_scan_gap_positive
      CHECK (min_scan_gap_minutes IS NULL OR min_scan_gap_minutes > 0);
  ```
- `WorkRule`: field `private Integer minScanGapMinutes;`, ikut di `toValues()` dan `apply(values)`.
- `WorkRuleRequest`: `@Min(value = 1, message = "Jeda minimum minimal 1 menit") @Max(value = 240, message = "Jeda minimum maksimal 240 menit") Integer minScanGapMinutes`, diteruskan ke `RuleValues`. `WorkRuleResponse` ikut memuat `minScanGapMinutes`.
- Ganti `null` sementara dari Task 1 dengan nilai sebenarnya.

- [ ] **Step 4: Jalankan seluruh test backend**

Run: `cd backend && ./mvnw -q test`
Expected: seluruh suite lulus (tidak ada endpoint baru, jadi `AccessControlTests` tidak berubah).

- [ ] **Step 5: Perbarui `docs/keputusan.md`**: tambah K-092 ke daftar keputusan §2 dan detailnya di §5 (Absensi dan Lembur). Isinya keputusan K-092 dari spec §12, ditambah alasan (celah spam scan di `DailyAttendanceCalculator`, berlaku juga untuk X401) dan batas validasi 1–240.

- [ ] **Step 6: Commit (oleh pengguna).**

---

### Task 3: Kolom di halaman Pengaturan Aturan

**Files:**
- Modify: `frontend/src/lib/types.ts:133` (tipe nilai aturan) dan `:159` (union `RuleField`)
- Modify: `frontend/src/pages/WorkRulesPage.tsx` (kolom tabel ±baris 61, field form ±baris 88, nilai awal ±baris 115, konversi kirim ±baris 127–137)
- Test: `frontend/src/pages/WorkRulesPage.test.tsx`; perbarui fixture di `frontend/src/pages/CalculationPreviewPage.test.tsx:59`

**Interfaces:**
- Consumes: field JSON `minScanGapMinutes` dan `MIN_SCAN_GAP` dari Task 2.

- [ ] **Step 1: Tulis test yang gagal** di `WorkRulesPage.test.tsx`, mengikuti pola test toleransi yang ada:
  - `shows min scan gap column`: aturan dengan `minScanGapMinutes: 5` menampilkan `5 menit` di kolom berjudul `Jeda minimum antar scan`.
  - `sends null when min scan gap is cleared`: isi lalu kosongkan field `Jeda minimum antar scan (menit)`, lalu submit. Body request berisi `minScanGapMinutes: null`.
  - Tambah `minScanGapMinutes: null` ke fixture yang ada (baris 40) dan ke fixture `CalculationPreviewPage.test.tsx:59`.

- [ ] **Step 2: Jalankan test, pastikan gagal**

Run: `cd frontend && npx vitest run src/pages/WorkRulesPage.test.tsx`
Expected: FAIL (kolom/field belum ada).

- [ ] **Step 3: Implementasi**, meniru pola `lateToleranceMinutes` persis:
  - tipe `minScanGapMinutes: number | null` dan `'MIN_SCAN_GAP'` di union;
  - kolom tabel `field: 'MIN_SCAN_GAP'` dengan render `` `${v.minScanGapMinutes} menit` `` atau `null`;
  - field form `{ name: 'minScanGapMinutes', label: 'Jeda minimum antar scan (menit)', type: 'number' }`, dengan teks bantuan `Scan yang berjarak kurang dari jeda ini dihitung satu kali. Kosongkan jika tidak dipakai.` jika komponen form mendukung teks bantuan;
  - konversi kirim memakai `emptyToNull` lalu `Number`, sama seperti toleransi.

- [ ] **Step 4: Jalankan test, build, dan lint**

Run: `cd frontend && npm test && npm run build && npm run lint`
Expected: semua lulus tanpa error.

- [ ] **Step 5: Commit (oleh pengguna).**
