# Keputusan Review Gap v0.6 — Rencana Implementasi

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menerapkan enam keputusan pengguna (2026-10-25) atas tabel "Ketidaksesuaian dokumen dan kode" di dokumen review gap: karyawan hanya melihat absensinya sendiri, Owner boleh memutus langsung tanpa tahap HR, pengajuan yang ditolak bisa diajukan ulang, dan data karyawan serta cabang absen hanya diubah Owner.

**Architecture:** Perubahan kecil di modul yang sudah ada. `leave` (keputusan, surat, ajukan ulang + migration V16), `team` (diganti endpoint absensi milik sendiri), `config/SecurityConfig` (hak tulis data karyawan), dan halaman React terkait. Tidak ada modul baru.

**Tech Stack:** Spring Boot 4 / Java 21 / JPA / Flyway / PostgreSQL (Testcontainers), React + Vite + TypeScript + Vitest.

**Spec:** dokumen review gap "Review Gap Requirement v0.6 dan Implementasi Web" (https://claude.ai/artifact/8HsHVLem5kZhTEeuw5iFdZ), bagian 4, kolom "keputusan yang diambil" baris 1–6. Requirement acuan: HLX-TKS-ATT-REQ-001 v0.6. Jawaban pengguna disalin di bawah supaya rencana ini bisa dibaca tanpa membuka dokumen itu.

## Keputusan pengguna (verbatim, 2026-10-25)

| # | Topik | Jawaban pengguna |
|---|---|---|
| 1 | Karyawan melihat absensi rekan kerja | "ikutin requirement, karyawan gabisa liat history absen selain absen dari diri dia sendiri" |
| 2 | Satu pengguna memberi dua persetujuan | "kan yang sekarang harus dari tahap HR dulu ya untuk yang cuti dan sakit, saya ingin owner juga bisa langsung approved atau tolak, keterangan di hr nya kamu akalin aja, misal di approved/tolak langsung oleh owner" |
| 3 | Urutan persetujuan | "urutan nya sudah benar yang sekarang [...] saya ingin nambahin agar karyawan itu bisa ajukan ulang surat yang ditolak" |
| 4 | Keputusan Owner dicatat HR | "saya sudah setuju apa yang ada di kode yang sekarang" → **tidak ada perubahan** |
| 5 | HR/Owner mencatat atas nama karyawan | "saya sudah setuju apa yang ada di kode yang sekarang" → **tidak ada perubahan** |
| 6 | Data karyawan dan izin cabang | "iya hanya owner yang bisa, agar hr ga bisa manipulasi aturan kerja nya" |

Baris 7–11 (akun karyawan IS-06, pengaturan perangkat KP-08, dua cabang IS-02, test TC-18, label status) **belum dijawab pengguna** dan tidak masuk rencana ini. Satu-satunya pengecualian adalah TC-18, yang dimasukkan sebagai Task 7 opsional.

## Konfirmasi di awal sesi (tanyakan sebelum Task 1)

Untuk empat hal di bawah, rencana ini memakai pilihan bawaan. Minta pengguna menyetujui atau mengubahnya sebelum mulai:

1. **Hapus fitur Rekan Kerja (Task 4).** Yang dihapus: `TeamPage.tsx`, `TeamMemberPage.tsx` (isinya dipindah), `TeamPages.test.tsx`, `TeamController.java`, `TeamTests.java`, dan endpoint `/api/team/**`. Penggantinya halaman "Absensi Saya" `/absensi-saya`. CLAUDE.md mewajibkan bertanya dulu sebelum menghapus file.
2. **Ajukan ulang menambah kolom (Task 3).** Migration V16 menambah `leave_requests.resubmitted_from_id`. Lampiran **tidak** disalin, jadi karyawan mengunggah ulang. Satu pengajuan yang ditolak hanya bisa diajukan ulang sekali (unique index). Kalau pengajuan ulangnya ditolak lagi, karyawan mengajukan ulang dari pengajuan yang baru itu.
3. **Master Kategori dan Cabang (Task 5).** Jawaban baris 6 hanya menyebut data karyawan dan izin cabang. Pilihan bawaannya: **hanya** penulisan `/api/employees` yang menjadi khusus Owner; Kategori dan Cabang tetap bisa diubah HR. Tanyakan apakah keduanya juga perlu khusus Owner, karena aturan kerja bisa melekat ke kategori (`WorkRule.category`).
4. **Lonceng Owner (Task 1).** Pilihan bawaannya: daftar "menunggu" milik Owner tidak berubah, yaitu tahap Owner ditambah tahap HR untuk jenis satu persetujuan atau bila pemohon adalah HR tunggal. Owner bisa memutus langsung dari menu Izin & Cuti, tetapi pengajuan tahap HR untuk jenis dua persetujuan tidak dimasukkan ke loncengnya supaya tidak terlalu ramai.

## Global Constraints

- Ikuti CLAUDE.md:
  - aturan bisnis tidak di-hardcode
  - schema hanya diubah lewat Flyway
  - setiap endpoint baru masuk `AccessControlTests`
  - error memakai ProblemDetail dengan pesan Bahasa Indonesia
  - setiap perubahan data diaudit lewat `AuditService` dalam transaksi yang sama
- **Jangan commit atau push**, karena pengguna commit sendiri. Langkah "Commit" diganti "laporkan file yang berubah".
- Jalankan test backend dari **salinan** `backend` (tanpa `target`) di scratchpad, dengan `./mvnw -q test` atau `-Dtest=...`. Jangan matikan `spring-boot:run` milik pengguna. `LeaveTests.concurrentOverlappingSubmissionsSaveOnlyOne` dan `DeviceCommandLifecycleTests` kadang gagal karena deadlock; ulangi dulu sebelum menyimpulkan ada regresi.
- Test frontend: `cd frontend && npx vitest run`, `npx tsc -b`, `npm run lint` (oxlint; peringatan lama memang sudah ada).
- Jangan pernah melaporkan test lulus tanpa benar-benar menjalankannya.
- Migration berikutnya **V16**; V15 adalah yang terakhir dan belum di-commit. Migration yang sudah di-push (≤ V14) tidak boleh diubah.
- Teks UI dan dokumen dalam Bahasa Indonesia. Nama di surat tidak pernah memakai username (K-123).
- Keputusan baru dicatat di `docs/keputusan.md` mulai **K-125**. K-124 (Profil untuk semua role) sudah ada.

## Review Focus

1. **Owner memutus langsung saat HR juga sedang memutus.** Kalau HR menyetujui tahap HR bersamaan dengan Owner menyetujui langsung, keduanya menulis baris tahap yang berbeda (`HR` dan `OWNER`). Unique constraint per tahap tidak akan menangkapnya, jadi yang harus menangkap adalah `@Version` di `LeaveRequest`. Hasil yang diharapkan: tepat satu sukses dan yang lain mendapat 409 "baru saja diputus". Diuji di Task 1.
2. **Owner memutus langsung untuk jenis yang wajib surat pendukung.** Saat ini lampiran hanya dicek bila `stage == HR`. Persetujuan langsung Owner pada `PERLU_TINJAU_HR` tetap harus ditolak bila belum ada lampiran. Diuji di Task 1.
3. **Ajukan ulang padahal jenis izinnya sudah dinonaktifkan.** Hasil yang diharapkan: 409 dengan pesan yang jelas, bukan draft yang rusak. Diuji di Task 3.
4. **Absensi Saya dibuka akun tanpa data karyawan atau karyawan nonaktif.** Hasil yang diharapkan: 404 "Data karyawan untuk akun ini" (pola `MyProfileController`), bukan 500. Rentang tanggal di masa depan tetap 400. Diuji di Task 4.
5. **HR membuka detail karyawan setelah haknya dibatasi.** Halaman tetap bisa dibaca dan pendaftaran sidik jari tetap jalan (v0.6 catatan 7). Tombol ubah dan simpan tidak tampil, dan PUT/POST dibalas 403. Diuji di Task 5.

---

### Task 1: Owner memutus langsung pada tahap HR (backend)

Aturan baru (K-125): untuk pengajuan berstatus `PERLU_TINJAU_HR` dengan `requiresOwnerApproval = true`, Owner boleh langsung **Setujui** atau **Tolak**.
- Keputusannya disimpan sebagai **satu** baris riwayat tahap `OWNER` (metode `WEB`), lalu status menjadi `DISETUJUI` atau `DITOLAK`.
- Tahap HR tidak mendapat baris riwayat. Surat dan garis waktu menuliskannya sebagai "diputus langsung oleh Owner" (Task 2).
- Akibatnya satu pengguna tidak pernah memberi dua persetujuan (FR-LV-008), dan jalur khusus "Owner mengerjakan tahap HR bila pemohon HR tunggal" tidak diperlukan lagi.
- Untuk jenis satu persetujuan, perilakunya tidak berubah: Owner tetap memutus di tahap `HR` (K-073).

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/leave/LeaveService.java` (`decisionCheck` ±656-677, `approve` 173-191, javadoc `pending` ±338)
- Test: `backend/src/test/java/com/biometricattendance/leave/LeaveApprovalTests.java`

**Interfaces:**
- Produces: pengajuan dua persetujuan bisa berstatus `DISETUJUI` atau `DITOLAK` dengan `approvals` = `[OWNER]` saja. Task 2 membaca kondisi ini sebagai "tidak ada baris HR, ada baris OWNER".

- [ ] **Step 1: Ganti test lama dengan test untuk aturan baru**

Di `LeaveApprovalTests`:
- **Hapus** `ownerCannotSkipHrStageForDoubleApprovalType` (±83) dan `ownerMayDoHrStageWhenRequesterIsTheOnlyHr` (±160).
- **Ubah** `ownerSeesCanDecideAtHrStageOnlyWhenRequesterIsTheOnlyHr` (±200) menjadi `ownerSeesCanDecideAtHrStageOfDoubleApprovalType`. Isinya: `canDecide` milik Owner bernilai `true` pada `PERLU_TINJAU_HR`, tanpa mock `hrCounter`.
- Tambahkan:

```java
@Test
void ownerApprovesDoubleApprovalTypeDirectlyAtHrStage() throws Exception {
    long id = idOf(mockMvc.perform(submitFor(createEmployee(createBranch(), null), createType(true), "2026-10-05", "2026-10-05").with(HR)));
    mockMvc.perform(decide(id, "approve", "Langsung").with(OWNER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISETUJUI"))
        .andExpect(jsonPath("$.approvals.length()").value(1))
        .andExpect(jsonPath("$.approvals[0].stage").value("OWNER"))
        .andExpect(jsonPath("$.approvals[0].method").value("WEB"));
    assertThat(auditActions(id)).contains("APPROVE");
}

@Test
void ownerRejectsDoubleApprovalTypeDirectlyAtHrStage() throws Exception {
    // sama, decide(id, "reject", "Tidak ada pengganti") → DITOLAK, approvals = [OWNER approved=false]
}

@Test
void ownerDirectApprovalStillRequiresSupportingDocument() throws Exception {
    // jenis requiresDocument=true dan requiresOwnerApproval=true, tanpa lampiran → OWNER approve = 400,
    // pesan mengandung "wajib surat pendukung"
}

@Test
void hrAndOwnerDecidingAtTheSameTimeLetOnlyOneWin() throws Exception {
    // helper concurrently(2, ...) yang sudah ada: HR approve dan OWNER approve pada id yang sama.
    // Tepat satu 200 dan satu 409; approvals.length() == 1 atau status konsisten dengan baris yang tersimpan.
}
```

Test berikut tetap berlaku dan harus lulus tanpa diubah: `hrCannotApproveOwnerStage`, `requesterCannotDecideAnyStageOfOwnRequest`, alur normal HR lalu Owner, dan `OwnerDecisionTests` (K-117).

- [ ] **Step 2: Jalankan test dan pastikan yang baru gagal**

Run (di salinan backend): `./mvnw -q test -Dtest=LeaveApprovalTests`
Expected: `ownerApprovesDoubleApprovalTypeDirectlyAtHrStage` FAIL, dengan status `PERLU_TINJAU_OWNER` atau 403.

- [ ] **Step 3: Ubah `decisionCheck` dan `approve`**

- `decisionCheck(LeaveRequest r)`: untuk Owner pada `PERLU_TINJAU_HR` dengan `requiresOwnerApproval == TRUE`, kembalikan `Check.ok(ApprovalStage.OWNER)` tanpa memanggil `hrReviewers`. Cabang lain tetap sama.
- `approve`: lampiran wajib dicek bila `r.getStatus() == PERLU_TINJAU_HR`, bukan lagi bila `stage == HR`. `moreStages` tetap `stage == HR && requiresOwnerApproval`, sehingga stage `OWNER` langsung menjalankan `r.approve(...)`.
- `reject` tidak perlu diubah.
- `HrReviewerCounter` masih dipakai `pending()`, jadi jangan dihapus.
- Perbarui javadoc `approve` (±168-171) dan komentar di `decisionCheck`.

- [ ] **Step 4: Jalankan test leave dan akses**

Run: `./mvnw -q test -Dtest='Leave*Tests,OwnerDecisionTests,AccessControlTests,HrReviewerCounterTests'`
Expected: semua PASS.

- [ ] **Step 5: Catat keputusan**

- `docs/keputusan.md`:
  - Tambah **K-125 Owner boleh memutus langsung tanpa tahap HR**, dengan isi seperti paragraf pembuka task ini. Sebutkan bahwa K-125 menggantikan bagian K-113 "Owner boleh mengerjakan tahap HR bila pemohon satu-satunya HR" dan menjawab baris 2 review gap.
  - Beri catatan "diganti K-125" di butir K-079 pada K-113.
- `docs/alur.md` §8.1: tambah transisi `PERLU_TINJAU_HR --> DISETUJUI: Owner setuju langsung (jenis persetujuan ganda)` dan `PERLU_TINJAU_HR --> DITOLAK: Owner tolak langsung`.
- `docs/alur.md` §8.2: ganti kalimat "Bila pemohon satu-satunya HR aktif, Owner boleh mengerjakan tahap HR" dengan aturan K-125.

Laporkan file yang berubah. Jangan commit.

---

### Task 2: Surat PDF dan garis waktu menulis "langsung oleh Owner"

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/leave/LeaveLetterService.java` (`boxes` 224-236)
- Modify: `frontend/src/components/leave/ApprovalTimeline.tsx` (`stageStep`)
- Test: `backend/src/test/java/com/biometricattendance/leave/LeaveLetterTests.java`, `frontend/src/components/leave/ApprovalTimeline.test.tsx`

**Interfaces:**
- Consumes: hasil Task 1, yaitu pengajuan dengan `requiresOwnerApproval` yang `approvals`-nya hanya berisi `OWNER`.

Teks yang dipakai harus persis seperti ini:
- **Kotak "HR/Finance" di surat:** tanpa gambar dan tanpa nama. Catatannya `"Disetujui langsung oleh Owner"` atau `"Ditolak langsung oleh Owner"`, diikuti baris waktu keputusan Owner.
- **Langkah "Ditinjau HR" di garis waktu:** state `skipped`, dengan detail `"Disetujui langsung oleh Owner"` atau `"Ditolak langsung oleh Owner"`.

- [ ] **Step 1: Tulis test yang gagal**

`LeaveLetterTests`: setujui langsung oleh Owner (aturan Task 1), unduh `letter.pdf`, lalu ekstrak teksnya dengan pola yang sudah ada di kelas itu. Assert teks memuat `"Disetujui langsung oleh Owner"`.

`ApprovalTimeline.test.tsx`:
```tsx
it('menandai tahap HR sebagai diputus langsung oleh Owner', () => {
  render(<ApprovalTimeline request={sampleRequest({
    requiresOwnerApproval: true, status: 'DISETUJUI', nextStage: null,
    approvals: [{ ...hrApprovalFixture, stage: 'OWNER', approved: true }],
  })} />)
  expect(screen.getByText('Disetujui langsung oleh Owner')).toBeInTheDocument()
})
```
(Pakai fixture approval yang ada di `components/leave/fixtures.ts`, atau tambahkan satu bila belum ada.)

- [ ] **Step 2: Jalankan test dan pastikan gagal**

Run: `./mvnw -q test -Dtest=LeaveLetterTests` dan `npx vitest run src/components/leave/ApprovalTimeline.test.tsx`
Expected: FAIL karena teksnya belum ada.

- [ ] **Step 3: Implementasi**

- `LeaveLetterService.boxes`: bila `requiresOwnerApproval`, `stageOf(history, HR) == null`, dan `stageOf(history, OWNER) != null`, kotak HR menjadi `new SignatureBox("HR/Finance", null, null, verdict + " langsung oleh Owner\n" + when)`. Letakkan di method kecil `directByOwnerBox(LeaveApproval owner)`.
- `ApprovalTimeline.stageStep`: untuk `stage === 'HR'` yang tidak punya baris HR tetapi punya baris OWNER, kembalikan state `skipped` dengan detail di atas. Cek ini harus dijalankan sebelum cabang "Dilewati".
- Halaman verifikasi publik (`VerifyLetterPage`) menampilkan daftar approvals apa adanya, jadi tidak perlu diubah. Tetap jalankan test-nya.

- [ ] **Step 4: Jalankan test**

Run: `./mvnw -q test -Dtest='LeaveLetter*Tests'` dan `npx vitest run src/components/leave src/pages/VerifyLetterPage.test.tsx`
Expected: PASS.

- [ ] **Step 5: Catat dan laporkan**

Tambahkan satu butir di K-125 tentang teks surat dan garis waktu. Laporkan file yang berubah.

---

### Task 3: Karyawan mengajukan ulang pengajuan yang ditolak

Aturan (K-126):
1. Di detail pengajuan sendiri yang berstatus `DITOLAK`, karyawan menekan **Ajukan ulang**.
2. Server membuat **draft baru** yang menyalin jenis, tanggal, alasan, dan isian templat (`contactPhone`, `leaveAddress`, `delegateTo`, `healthFacility`), dengan `resubmittedFromId` menunjuk pengajuan lama.
3. Lampiran tidak disalin, dan pengajuan lama tidak berubah.
4. Karyawan diarahkan ke wizard `/izin-saya/:idBaru/ubah` untuk memeriksa isian, mengunggah lampiran, lalu mengirim.
5. Pengecekan saat kirim (tumpang tindih, jenis izin, templat beku) memakai jalur `submitMyDraft` yang sudah ada.

**Files:**
- Create: `backend/src/main/resources/db/migration/V16__ajukan_ulang_izin.sql`
- Modify: `backend/src/main/java/com/biometricattendance/leave/LeaveRequest.java` (field `resubmittedFromId`), `LeaveRequestRepository.java` (`existsByResubmittedFromId`), `LeaveService.java` (`resubmitMine`), `MyLeaveController.java` (endpoint), `LeaveDtos.java` (`LeaveRequestResponse.resubmittedFromId`)
- Modify: `frontend/src/lib/types.ts` (`LeaveRequest.resubmittedFromId: number | null`), `frontend/src/components/leave/MyLeaveActions.tsx`, `frontend/src/pages/LeaveDetailPage.tsx`, `frontend/src/components/leave/fixtures.ts`
- Test: `backend/src/test/java/com/biometricattendance/leave/LeaveDraftTests.java`, `LeaveMigrationTests.java`, `security/AccessControlTests.java`, `frontend/src/pages/MyLeavePage.test.tsx`

**Interfaces:**
- Produces: `POST /api/me/leave-requests/{id}/resubmit` → `201` + `LeaveRequestResponse` (draft baru).
- Produces: `LeaveService.resubmitMine(Long id): LeaveRequestResponse`.
- Produces: `LeaveRequestResponse.resubmittedFromId: Long` (null bila bukan pengajuan ulang).

Schema V16:
```sql
alter table leave_requests add column resubmitted_from_id bigint references leave_requests(id);
create unique index ux_leave_requests_resubmitted_from on leave_requests(resubmitted_from_id)
    where resubmitted_from_id is not null;
```

- [ ] **Step 1: Tulis test backend yang gagal** (di `LeaveDraftTests`, data sintetis)

- `resubmitCopiesRejectedRequestIntoNewDraft`: tolak sebuah pengajuan, lalu karyawan memanggil `POST .../resubmit`. Hasilnya 201 dengan `status = DRAFT`, `resubmittedFromId` = id lama, jenis/tanggal/alasan/isian sama, dan `attachments` kosong. Pengajuan lama tetap `DITOLAK`. Audit draft baru memuat `RESUBMIT`.
- `onlyRejectedRequestsCanBeResubmitted`: pengajuan `PERLU_TINJAU_HR` atau `DISETUJUI` → 409 `"Hanya pengajuan yang ditolak yang bisa diajukan ulang"`.
- `rejectedRequestCanBeResubmittedOnce`: resubmit kedua → 409 `"Pengajuan ini sudah diajukan ulang"`.
- `cannotResubmitSomeoneElsesRequest`: dicoba dari akun karyawan lain → 404 (pola `findMine`).
- `resubmitWithInactiveLeaveTypeIsRejected`: jenis izin dinonaktifkan setelah pengajuan ditolak → 409 `"Jenis izin ini sudah tidak aktif"`.
- `deletingResubmittedDraftAllowsResubmittingAgain`: hapus draft hasil ajukan ulang, lalu resubmit lagi → 201.

Tambahan:
- `LeaveMigrationTests`: kolom `resubmitted_from_id` ada, dan unique index mencegah dua baris dengan nilai yang sama.
- `AccessControlTests`: tambahkan `POST /api/me/leave-requests/{MISSING}/resubmit` ke `draftEndpoints()`, sehingga endpoint ini wajib login dan wajib terhubung ke karyawan.

- [ ] **Step 2: Jalankan test dan pastikan gagal**

Run: `./mvnw -q test -Dtest='LeaveDraftTests,LeaveMigrationTests,AccessControlTests'`
Expected: FAIL karena endpoint belum ada (404) atau kolom belum ada.

- [ ] **Step 3: Implementasi backend**

- Buat V16 seperti di atas. Tambahkan field `@Column(name = "resubmitted_from_id") Long resubmittedFromId` beserta getter di `LeaveRequest`.
- `LeaveService.resubmitMine(Long id)`, `@Transactional`:
  1. `findMine(id)`.
  2. Pastikan status `DITOLAK`.
  3. Pastikan `!requests.existsByResubmittedFromId(id)`.
  4. Pastikan jenis izin masih aktif.
  5. Buat draft lewat jalur `create(myEmployee(), new LeaveDraftRequest(...salinan..., true), true, true)` atau jalur internal yang setara, lalu set `resubmittedFromId`.
  6. Catat audit `RESUBMIT` dengan catatan `"Ajukan ulang dari #<id>"`.
  7. Tangkap `DataIntegrityViolationException` dari unique index dan ubah menjadi 409 dengan pesan yang sama. Ini menangani klik ganda.
- `MyLeaveController`: `@PostMapping("/{id}/resubmit") @ResponseStatus(HttpStatus.CREATED)`.
- `LeaveDtos.LeaveRequestResponse`: tambah `resubmittedFromId` di akhir record, lalu isi di `from(...)`.

- [ ] **Step 4: Jalankan test backend**

Run: `./mvnw -q test -Dtest='Leave*Tests,AccessControlTests'`
Expected: PASS.

- [ ] **Step 5: Tulis test frontend yang gagal** (`MyLeavePage.test.tsx`)

- Detail `/izin-saya/7` berstatus `DITOLAK` menampilkan tombol **Ajukan ulang**. Mengkliknya memanggil `POST /api/me/leave-requests/7/resubmit`, lalu berpindah ke `/izin-saya/9/ubah` (id diambil dari respons).
- Status selain `DITOLAK` tidak menampilkan tombol itu.
- Detail draft dengan `resubmittedFromId: 7` menampilkan tautan "Pengajuan ulang dari pengajuan sebelumnya" ke `/izin-saya/7`.

Run: `npx vitest run src/pages/MyLeavePage.test.tsx` → FAIL.

- [ ] **Step 6: Implementasi frontend**

- `MyLeaveActions`: untuk `status === 'DITOLAK'`, tambah tombol `Ajukan ulang` (`min-h-11`, ikon `RotateCcw`). Tombol memanggil `api<LeaveRequest>(\`/api/me/leave-requests/${id}/resubmit\`, { method: 'POST' })`, meng-invalidate `['/api/me/leave-requests']`, lalu `navigate(\`/izin-saya/${created.id}/ubah\`)`. Error ditampilkan dengan pola yang sudah ada di komponen itu.
- `LeaveDetailPage`: bila `resubmittedFromId != null`, tampilkan baris "Pengajuan ulang dari" berisi `Link` ke detail lama, yaitu `/izin-saya/:id` atau `/izin/:id` sesuai `mode`. Manajer juga melihat baris ini.
- `fixtures.ts`: tambahkan `resubmittedFromId: null` di `sampleRequest`.

- [ ] **Step 7: Jalankan test frontend dan typecheck**

Run: `npx vitest run src/pages src/components/leave` dan `npx tsc -b`
Expected: PASS, exit 0.

- [ ] **Step 8: Catat dan laporkan**

- `docs/keputusan.md`: tambah **K-126 Ajukan ulang pengajuan yang ditolak** (aturan di atas, V16, lampiran tidak disalin, sekali per pengajuan yang ditolak).
- `docs/alur.md` §8.1: tambah `DITOLAK --> [*]: karyawan ajukan ulang (draft baru)`.

Laporkan file yang berubah.

---

### Task 4: Karyawan hanya melihat absensinya sendiri

Aturan (K-127, menggantikan K-083, K-084, dan bagian "Rekan" di K-118):
- Endpoint dan halaman rekan kerja dihapus.
- Karyawan mendapat halaman **Absensi Saya** `/absensi-saya` berisi riwayat harian miliknya. Bentuk datanya sama dengan detail rekan sebelumnya (`TeamEmployeeResponse`, tanpa data sensitif).
- Beranda mengambil ringkasannya dari endpoint baru.
- Navigasi bawah karyawan menjadi Beranda, Izin, Absensi, Profil.

**Files:**
- Create: `backend/src/main/java/com/biometricattendance/team/MyAttendanceController.java`
- Modify: `backend/src/main/java/com/biometricattendance/team/TeamService.java` (hapus `daily` dan `branchOptions`; `forEmployee` tetap), `TeamDtos.java` (hapus `TeamRow`, `TeamDailyResponse`, `BranchOption` bila tidak terpakai)
- Delete: `backend/src/main/java/com/biometricattendance/team/TeamController.java`
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java:47-48` (hapus aturan `/api/team/**`; `/api/me/**` sudah `authenticated`)
- Delete + Create: `backend/src/test/java/com/biometricattendance/team/TeamTests.java` → `MyAttendanceTests.java` (pindahkan kasus "tidak membocorkan data sensitif" yang masih relevan)
- Modify: `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java:322-337`
- Delete: `frontend/src/pages/TeamPage.tsx`, `frontend/src/pages/TeamPages.test.tsx`
- Rename/Modify: `frontend/src/pages/TeamMemberPage.tsx` → `frontend/src/pages/MyAttendancePage.tsx`
- Modify: `frontend/src/App.tsx`, `frontend/src/navigation.ts`, `frontend/src/navigation.test.ts`, `frontend/src/pages/HomePage.tsx:28-31`, `frontend/src/pages/HomePage.test.tsx`, `frontend/src/lib/types.ts`
- Create: `frontend/src/pages/MyAttendancePage.test.tsx`

**Interfaces:**
- Produces: `GET /api/me/attendance?from=&to=` → `TeamEmployeeResponse` milik akun sendiri. Default `to` = hari ini dan default `from` = awal bulan `to`, sama dengan endpoint lama.
- Produces: route frontend `/absensi-saya` dan item menu `{ path: '/absensi-saya', label: 'Absensi', roles: ['EMPLOYEE'], icon: ClipboardCheck, group: 'Saya', mobile: true }` di posisi ke-3.

- [ ] **Step 1: Tulis test backend yang gagal** (`MyAttendanceTests`, data sintetis, memakai `AttendanceFixtures`)

- `employeeSeesOwnAttendanceOnly`: akun karyawan A memanggil `/api/me/attendance?from=2026-10-05&to=2026-10-08`. Hasilnya `employeeId` = A dan memuat hari ada scan. Field `phone`, `pin`, `reason`, dan catatan koreksi tidak ada (salin assertion kebocoran dari `TeamTests`).
- `accountWithoutEmployeeGets404`: akun Owner tanpa karyawan → 404 dengan pesan yang sama dengan `MyProfileController` (`ApiException.notFound("Data karyawan untuk akun ini")`).
- `inactiveEmployeeGets404`.
- `futureRangeIsRejected`: `to` = besok → 400 `"Tanggal belum terjadi"`.
- `teamEndpointsAreGone`: `GET /api/team/attendance` dan `/api/team/employees/1/attendance` sebagai karyawan → 403 (`anyRequest().denyAll()`).

`AccessControlTests`: ganti `teamAttendanceIsOpenToAllSignedInRoles` dan `teamMemberHistoryRequiresLoginOnly` dengan `myAttendanceRequiresLoginOnly` (anonim 401, sudah login tetapi tanpa karyawan 404).

- [ ] **Step 2: Jalankan test dan pastikan gagal**

Run: `./mvnw -q test -Dtest='MyAttendanceTests,AccessControlTests'` → FAIL.

- [ ] **Step 3: Implementasi backend**

`MyAttendanceController` (`@RequestMapping("/api/me/attendance")`):
- ambil akun lewat `UserAccountRepository.findByUsername(authentication.getName())`
- lempar 404 bila `getEmployee() == null`
- panggil `team.forEmployee(employeeId, from, end)`

Hapus `TeamController`, aturan `/api/team/**` di `SecurityConfig`, serta kode `TeamService`/`TeamDtos` yang tidak terpakai lagi. Perbarui javadoc yang masih menyebut K-083.

- [ ] **Step 4: Jalankan test backend**

Run: `./mvnw -q test -Dtest='MyAttendanceTests,AccessControlTests'` → PASS. Setelah itu jalankan seluruh suite sekali: `./mvnw -q test`.

- [ ] **Step 5: Tulis test frontend yang gagal**

- `MyAttendancePage.test.tsx`: karyawan membuka `/absensi-saya`. Halaman memanggil `GET /api/me/attendance?from=2026-10-01&to=2026-10-14` (pakai fake timer seperti `HomePage.test.tsx`) dan menampilkan baris per hari.
- `navigation.test.ts`: path menu karyawan `['/beranda', '/izin-saya', '/absensi-saya', '/profil']` dan label `['Beranda', 'Izin', 'Absensi', 'Profil']`. Karyawan tidak lagi punya `Rekan`.
- `HomePage.test.tsx`: ganti mock `GET /api/team/employees/12/attendance?...` menjadi `GET /api/me/attendance?...`.
- Route lama `/rekan-kerja` dan `/rekan-kerja/:id` diarahkan ke `/absensi-saya` (cukup satu kasus test).

Run: `npx vitest run src/pages/MyAttendancePage.test.tsx src/navigation.test.ts src/pages/HomePage.test.tsx` → FAIL.

- [ ] **Step 6: Implementasi frontend**

- Pindahkan isi `TeamMemberPage` ke `MyAttendancePage`: tanpa `useParams`, judul "Absensi Saya", query key `['/api/me/attendance', from, to]`.
- Hapus `TeamPage.tsx` dan `TeamPages.test.tsx`.
- `App.tsx`: ganti route `rekan-kerja*` dengan `absensi-saya`, lalu arahkan `rekan-kerja` dan `rekan-kerja/:id` ke `<Navigate to="/absensi-saya" replace />`.
- `navigation.ts`: ganti item Rekan dengan item Absensi.
- `HomePage.tsx`: ganti kedua query ke `/api/me/attendance` dan perbarui komentar yang menyebut K-083.
- `lib/types.ts`: hapus tipe `TeamDaily`/`TeamRow` bila tidak terpakai lagi.

- [ ] **Step 7: Jalankan seluruh test frontend, typecheck, dan lint**

Run: `npx vitest run`, `npx tsc -b`, `npm run lint`
Expected: PASS, exit 0, tanpa peringatan baru.

- [ ] **Step 8: Catat dan laporkan**

- `docs/keputusan.md`: tambah **K-127 Karyawan hanya melihat absensinya sendiri** (FR-USR-002, BR-018, AC-USR-01). Beri catatan "diganti K-127" di K-083, K-084, dan butir Rekan di K-118.
- Jalankan `grep -rn -i "rekan" docs/` dan sesuaikan `docs/aturan-bisnis.md` §11 serta dokumen lain yang menyebut daftar rekan kerja.
- `docs/rencana-rekan-kerja.md` dan `docs/superpowers/plans/2026-10-14-rekan-kerja.md`: tambahkan catatan "dibatalkan oleh K-127" di bagian atas. **Jangan dihapus.**
- `docs/pertanyaan-terbuka.md` Q-016 (Hak akses karyawan): baca dulu, lalu catat jawabannya (K-127).

Laporkan file yang berubah dan yang dihapus.

---

### Task 5: Data karyawan dan cabang absen hanya diubah Owner

Aturan (K-128):
- `POST /api/employees`, `PUT /api/employees/{id}`, dan `PUT /api/employees/{id}/branches` hanya untuk Owner.
- HR/Finance tetap bisa **membaca** daftar dan detail karyawan, serta tetap mengurus sidik jari (`/api/employees/{id}/fingerprints/**` dan `/fingerprint-consent`), sesuai v0.6 catatan 7.
- Alasan dari pengguna: HR tidak boleh memanipulasi aturan kerja lewat data karyawan, karena kategori dan aturan per karyawan melekat ke data karyawan. Aturan kerja sendiri sudah khusus Owner (K-049).

**Files:**
- Modify: `backend/src/main/java/com/biometricattendance/config/SecurityConfig.java:57-59` (aturan khusus diletakkan sebelum matcher umum)
- Modify: test yang membuat atau mengubah karyawan sebagai HR, diganti menjadi `OWNER`. Cari dengan `post("/api/employees").with(HR` dan `put("/api/employees...with(HR`. Saat ini ada di: `AttendanceFixtures.java:43-45`, `attendance/HeldScanTests.java`, `fingerprint/BranchRevokeTests.java`, `fingerprint/PinLockTests.java`, `ingest/BranchAccessIngestTests.java`, `ingest/RakitanFixtures.java`, `ingest/ZkPushIngestTests.java`, `masterdata/EmployeeBranchAccessTests.java`, `workrule/WorkRuleTests.java`
- Test: `backend/src/test/java/com/biometricattendance/security/AccessControlTests.java`
- Modify: `frontend/src/components/CrudPage.tsx` (prop `readOnly?: boolean`), `frontend/src/pages/EmployeesPage.tsx`, `frontend/src/pages/EmployeeDetailPage.tsx`, `frontend/src/components/BranchAccessCard.tsx`
- Test: `frontend/src/pages/EmployeeDetailPage.test.tsx`, `frontend/src/components/BranchAccessCard.test.tsx`

**Interfaces:**
- Produces: prop `readOnly?: boolean` pada `CrudPage`. Bila `true`, tombol Tambah, aksi Ubah per baris, dan dialog form tidak tampil; daftar tetap tampil.

- [ ] **Step 1: Tulis test akses yang gagal**

`AccessControlTests`:
```java
@Test
void employeeDataIsWritableByOwnerOnly() throws Exception {
    // HR: POST /api/employees, PUT /api/employees/{MISSING}, PUT /api/employees/{MISSING}/branches → 403
    // OWNER: endpoint yang sama → bukan 403 (400/404 karena body/ID uji)
    // HR: GET /api/employees dan GET /api/employees/{MISSING} → bukan 403
}

@Test
void hrKeepsFingerprintEnrollment() throws Exception {
    // HR: POST /api/employees/{MISSING}/fingerprint-consent dan /fingerprints/enroll → bukan 403
}
```
Sesuaikan juga matrix lama yang masih menganggap HR boleh menulis `/api/employees`, misalnya `managerEndpoints` dan `masterDataReadableByOwnerAndHrFinanceOnly`.

- [ ] **Step 2: Jalankan test dan pastikan gagal**

Run: `./mvnw -q test -Dtest=AccessControlTests` → FAIL, karena HR masih mendapat respons selain 403.

- [ ] **Step 3: Implementasi backend**

Sisipkan sebelum matcher umum di `SecurityConfig` (baris 57):
```java
// Data karyawan dan cabang absen hanya diubah Owner (K-128); sidik jari tetap untuk HR (v0.6 catatan 7).
.requestMatchers(HttpMethod.POST, "/api/employees").hasRole(OWNER)
.requestMatchers(HttpMethod.PUT, "/api/employees/*", "/api/employees/*/branches").hasRole(OWNER)
```
Lalu ganti fixture test dari `HR` ke `OWNER` di file-file yang disebut di atas.

- [ ] **Step 4: Jalankan seluruh suite backend**

Run: `./mvnw -q test` → PASS. Bila yang gagal hanya test flaky yang sudah tercatat, ulangi sekali.

- [ ] **Step 5: Tulis test frontend yang gagal**

- `EmployeeDetailPage.test.tsx`: saat login sebagai HR, tombol ubah data karyawan tidak ada, tetapi kartu sidik jari tetap ada.
- `BranchAccessCard.test.tsx`: saat login sebagai HR, tombol **Simpan** tidak ada, checkbox `disabled`, dan muncul teks `"Hanya Owner yang dapat mengubah cabang absen."`. Saat login sebagai Owner, tetap bisa menyimpan.
- Test daftar karyawan, di file baru `EmployeesPage.test.tsx`: HR tidak melihat tombol **Tambah**.

Run → FAIL.

- [ ] **Step 6: Implementasi frontend**

Ikuti pola yang sudah ada, `const isOwner = me?.role === 'OWNER'` (`DevicesPage.tsx:111`):
- `EmployeesPage`: `readOnly={!isOwner}`.
- `EmployeeDetailPage`: sembunyikan aksi ubah untuk non-Owner.
- `BranchAccessCard`: hanya-baca untuk non-Owner.
- `CrudPage`: implementasikan prop `readOnly`.

- [ ] **Step 7: Jalankan seluruh test frontend dan typecheck**

Run: `npx vitest run` dan `npx tsc -b` → PASS, exit 0.

- [ ] **Step 8: Catat dan laporkan**

- `docs/keputusan.md`: tambah **K-128 Data karyawan dan cabang absen hanya diubah Owner** (aturan di atas). Beri catatan di K-103 bahwa ketentuan "Owner atau HR/Finance" untuk cabang absen sekarang diganti K-128.
- Matriks akses di `docs/` (cari `grep -rn "HR/Finance" docs/*.md` pada bagian karyawan): sesuaikan.

Laporkan file yang berubah.

---

### Task 6: Perbarui dokumen review gap dan status

**Files:**
- Modify: dokumen Claude Docs "Review Gap Requirement v0.6 dan Implementasi Web", lewat konektor Claude Docs (`read` lalu `update`), bukan publish.
- Modify: memory `persetujuan-ganda-status.md`.

- [ ] **Step 1:** Di tabel bagian 4, tambahkan status "Selesai (K-125..K-128)" pada kolom keputusan baris 1, 2, 3, dan 6, tanpa menghapus jawaban pengguna. Untuk baris 4 dan 5, tulis "Tetap, sesuai kode".
- [ ] **Step 2:** Di bagian 1 (matriks sitemap), perbarui butir yang terdampak: Rekan diganti Absensi Saya, dan hak akses data karyawan. Di diagram sitemap bagian 5, ganti "Rekan" dengan "Absensi".
- [ ] **Step 3:** Laporkan ringkasan ke pengguna: file yang berubah dan yang dihapus, hasil test dengan angka sebenarnya, serta hal yang masih menunggu jawaban (baris 7–11).

---

### Task 7 (opsional, tanyakan dulu): Test TC-18 / AC-WT-05

Pengguna belum memutuskan baris 10, tetapi CLAUDE.md mewajibkan setiap TC menjadi test otomatis. Kerjakan task ini hanya bila pengguna setuju.

**Files:** Test: `backend/src/test/java/com/biometricattendance/calculation/DailyAttendanceCalculatorTests.java`

- [ ] **Step 1:** Tambahkan `lateArrivalAtNineCountsSevenWorkingHours` dengan skenario berikut:
  - aturan kerja 08.00–17.00 dengan istirahat 12.00–13.00, diambil dari `WorkRule` uji, bukan konstanta produksi
  - scan masuk 09:00 dan pulang 17:00
  - jam kerja reguler yang diharapkan `PT7H`

  Bandingkan dengan `configuredBreakIsSubtractedFromRegularWork` yang sudah ada (08–17 = 8 jam).
- [ ] **Step 2:** Run `./mvnw -q test -Dtest=DailyAttendanceCalculatorTests` → PASS. Bila gagal, **berhenti dan laporkan**, karena itu berarti perhitungannya yang salah, bukan test-nya.
- [ ] **Step 3:** Laporkan.
