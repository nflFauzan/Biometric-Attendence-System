# Persetujuan Ganda, Surat Izin PDF, dan Area Karyawan — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Pengajuan izin memakai status Draft → Perlu ditinjau HR → Perlu ditinjau Owner → Disetujui/Ditolak/Dibatalkan, dengan persetujuan ganda per jenis, surat PDF bertanda tangan + QR verifikasi, pencatatan keputusan Owner oleh HR, dan area karyawan mobile-first yang baru.

**Architecture:** Modul `leave` yang ada diperluas: status baru dan kolom templat di `leave_requests`, tabel riwayat tahap `leave_approvals`, dan spesimen tanda tangan per akun di modul baru `signature`. PDF dibuat di server (OpenPDF + ZXing) dan bisa diverifikasi lewat endpoint publik bertoken. Frontend memakai halaman izin yang ada, ditambah wizard 4 langkah, garis waktu status, dan Beranda karyawan.

**Tech Stack:** Spring Boot 4 / Java 21 / JPA / Flyway / PostgreSQL (Testcontainers), OpenPDF, ZXing; React + Vite + TypeScript + Tailwind + shadcn/ui + TanStack Query, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-10-24-persetujuan-ganda-surat-izin-design.md` (dibaca bersama plan ini).

## Global Constraints

- **Jangan commit atau push** (preferensi pengguna). Setiap langkah "Selesai" berarti berhenti dan laporkan file yang berubah serta hasil test.
- Test backend dijalankan dari **salinan** `backend` (tanpa `target`) di scratchpad, karena pengguna menjalankan `spring-boot:run` di `backend/target`: `rsync -a --exclude target backend/ "$SCRATCH/backend/" && cd "$SCRATCH/backend" && ./mvnw -q test -Dtest=<Kelas>`. Bila `rsync` tidak ada, gunakan `tar --exclude=target -cf - -C backend . | tar -xf - -C "$SCRATCH/backend"`. Jangan mematikan proses Java milik pengguna.
- `LeaveTests.concurrentOverlappingSubmissionsSaveOnlyOne` dan `DeviceCommandLifecycleTests` kadang flaky (deadlock/PIN bentrok). Ulangi sebelum menyimpulkan ada regresi.
- Frontend: `cd frontend && npx vitest run <path>` dan `npx tsc -b --noEmit` (atau `npm run build`).
- Perubahan schema hanya lewat migration Flyway baru: V12, V13, V14. Jangan mengubah V1–V11.
- Setiap perubahan data dicatat lewat `AuditService.record(...)` di transaksi yang sama. Endpoint baru wajib masuk ke `config/SecurityConfig` dan `AccessControlTests`.
- Error API berupa ProblemDetail lewat `ApiException`; pesan untuk pengguna dalam Bahasa Indonesia.
- Aturan bisnis tidak di-hardcode. Persetujuan ganda dan templat surat adalah kolom `leave_types` yang diatur Owner, dan data awalnya hanya seed.
- Nama status persis: `DRAFT`, `PERLU_TINJAU_HR`, `PERLU_TINJAU_OWNER`, `DISETUJUI`, `DITOLAK`, `DIBATALKAN`. Label UI: "Draft", "Perlu ditinjau HR", "Perlu ditinjau Owner", "Disetujui", "Ditolak", "Dibatalkan".
- Templat: `UMUM`, `IZIN`, `CUTI`, `SAKIT`. Cara keputusan: `WEB`, `WHATSAPP`, `LISAN`, `TERTULIS`. Tahap: `HR`, `OWNER`.
- UI: brand K-017 (steel `#104FA6`, gold `#F5CE02`, Poppins + Open Sans, tombol pill) dan tema kaca K-085. Ikon `lucide-react`. Tidak ada em-dash (—) di teks UI baru. Target sentuh ≥ 44px, label di atas isian, keadaan memuat/kosong/gagal di setiap halaman, dan `prefers-reduced-motion` dihormati.
- Teks UI dan dokumentasi dalam Bahasa Indonesia.

## Review Focus

1. **Pengajuan lama berstatus `MENUNGGU` setelah migrasi** harus menjadi `PERLU_TINJAU_HR`, tetap memblokir tumpang tindih, dan bisa disetujui. Riwayat yang sudah diputus mendapat satu baris `leave_approvals` (Task 1 dan Task 3: `legacyDecisionsBecomeApprovalRows`).
2. **Owner mengubah jenis izin saat ada pengajuan berjalan.** Rantai pengajuan tidak berubah karena jenis dibekukan saat kirim (Task 2: `typeSettingsAreFrozenAtSubmit`).
3. **Dua penyetuju menekan bersamaan di tahap yang sama** menghasilkan satu tahap tercatat dan satu 409 (Task 3: `concurrentStageApprovalsRecordOneStage`).
4. **Karyawan membuka PDF atau spesimen milik orang lain lewat ID tebakan** harus mendapat 404 (Task 5: `nobodyCanReadAnotherAccountsSignature`, Task 6: `employeeCannotDownloadAnotherEmployeesLetter`).
5. **Draft dengan tanggal bertabrakan** boleh disimpan tetapi ditolak saat dikirim, dan tidak mengubah rekap (Task 2: `draftsDoNotBlockOverlapOrRecapUntilSubmitted`).

---

### Task 1: Status baru, kolom jenis izin, dan migrasi V12

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__persetujuan_ganda_status.sql`
- Create: `backend/src/main/java/com/biometricattendance/leave/LetterTemplate.java`
- Modify: `backend/src/main/java/com/biometricattendance/leave/LeaveStatus.java`, `LeaveType.java`, `LeaveRequest.java`, `LeaveDtos.java`, `LeaveService.java`, `LeaveTypeService.java`
- Modify (pemakai status): `attendance/AttendanceService.java:149,242`, `dashboard/DashboardService.java:89`, `dashboard/DashboardDtos.java`, `team/TeamDtos.java`
- Test: `backend/src/test/java/com/biometricattendance/leave/LeaveTests.java` (ubah ekspektasi `MENUNGGU` → `PERLU_TINJAU_HR`), test jenis izin yang ada (bila belum ada, buat `leave/LeaveTypeTests.java`), `leave/LeaveMigrationTests.java` (baru)

**Interfaces:**
- Produces:
  - `enum LeaveStatus { DRAFT, PERLU_TINJAU_HR, PERLU_TINJAU_OWNER, DISETUJUI, DITOLAK, DIBATALKAN }`
  - Konstanta static `LeaveStatus.PENDING = EnumSet.of(PERLU_TINJAU_HR, PERLU_TINJAU_OWNER)` dan `LeaveStatus.ACTIVE = EnumSet.of(PERLU_TINJAU_HR, PERLU_TINJAU_OWNER, DISETUJUI)` (bila enum tidak bisa memegang EnumSet dirinya sendiri sebagai field, taruh di kelas `LeaveStatuses`; catat pilihannya)
  - `boolean LeaveStatus.isPending()`
  - `enum LetterTemplate { UMUM, IZIN, CUTI, SAKIT }`
  - `LeaveType`: `boolean requiresOwnerApproval`, `LetterTemplate letterTemplate`
  - `LeaveTypeRequest`/`LeaveTypeResponse` mendapat `requiresOwnerApproval` (boolean) dan `letterTemplate` (`LetterTemplate`; default `UMUM` bila null)
  - `LeaveService.label(LeaveStatus)` mengembalikan label kecil: "draft", "perlu ditinjau HR", "perlu ditinjau Owner", "disetujui", "ditolak", "dibatalkan"

- [ ] **Step 1: Tulis `LeaveMigrationTests` (gagal)**
  - `pendingStatusIsRenamedAndConstraintsAcceptNewStatuses`: lewat `JdbcTemplate`, insert baris `leave_requests` berstatus `PERLU_TINJAU_HR`, `PERLU_TINJAU_OWNER`, `DRAFT` (karyawan berbeda) → berhasil. Insert status `MENUNGGU` → gagal karena CHECK.
  - `draftsAreOutsideOverlapConstraint`: dua `DRAFT` satu karyawan dengan tanggal sama → boleh. `DRAFT` + `PERLU_TINJAU_HR` beririsan → boleh. Dua `PERLU_TINJAU_HR` beririsan → gagal (`leave_requests_no_overlap`).
  - `seedTypesGetTemplates`: jenis bernama `Cuti`/`Sakit`/`Izin` (bila ada dari seed V5) memiliki `letter_template` CUTI/SAKIT/IZIN dan `requires_owner_approval` true/true/false.
- [ ] **Step 2:** `./mvnw -q test -Dtest=LeaveMigrationTests` → FAIL.
- [ ] **Step 3: Tulis `V12__persetujuan_ganda_status.sql`**
  - Drop `leave_requests_status_ck` dan `leave_requests_no_overlap`.
  - `UPDATE leave_requests SET status='PERLU_TINJAU_HR' WHERE status='MENUNGGU'`.
  - Tambah CHECK status dengan enam nilai.
  - Buat ulang exclusion constraint dengan definisi sama seperti V5, tetapi `WHERE (status IN ('PERLU_TINJAU_HR','PERLU_TINJAU_OWNER','DISETUJUI'))`.
  - Kolom baru di `leave_requests` (boleh kosong kecuali disebut lain): `requires_owner_approval boolean`, `letter_template varchar(10)`, `contact_phone varchar(30)`, `leave_address varchar(300)`, `delegate_to varchar(150)`, `health_facility varchar(150)`, `verification_token varchar(40) UNIQUE`, `created_at timestamptz NOT NULL DEFAULT now()`.
  - `submitted_at` dan `submitted_by` menjadi boleh kosong.
  - Isi `created_at = submitted_at`. Untuk baris lama: `requires_owner_approval=false`, `letter_template='UMUM'`, `verification_token=replace(gen_random_uuid()::text,'-','')`.
  - Kolom baru di `leave_types`: `requires_owner_approval boolean NOT NULL DEFAULT false`, `letter_template varchar(10) NOT NULL DEFAULT 'UMUM'` + CHECK empat nilai.
  - Seed berdasarkan `lower(name)`: `cuti` → CUTI + true, `sakit` → SAKIT + true, `izin` → IZIN + false.
- [ ] **Step 4: Ubah kode ke status baru.**
  - `LeaveRequest`: konstruktor menerima `LeaveStatus initial`, plus getter/setter kolom baru.
  - `LeaveService`:
    - `ACTIVE` diganti konstanta enum.
    - Pemeriksaan `== MENUNGGU` diganti `isPending()` di `requirePending` dan `cancelMine`.
    - `attach` mengizinkan `DRAFT` dan status pending.
    - `pending()` dan `usage().pendingRequests` memakai `PENDING`.
  - `AttendanceService:149` memakai `ACTIVE`, dan `:242` menganggap status pending sebagai `PENDING`.
  - `DashboardService:89` menghitung `PENDING`.
  - Pengajuan baru di alur lama dibuat langsung `PERLU_TINJAU_HR`. Persetujuan masih satu langkah sampai Task 3.
- [ ] **Step 5: Perbarui `LeaveTests`** (`"MENUNGGU"` → `"PERLU_TINJAU_HR"`). Tambahkan `ownerSetsApprovalChainAndTemplateOnLeaveType`: PUT `/api/leave-types/{id}` dengan `requiresOwnerApproval:true, letterTemplate:"CUTI"`, lalu GET mengembalikan keduanya. `letterTemplate:"XYZ"` → 400.
- [ ] **Step 6:** `-Dtest='LeaveMigrationTests,LeaveTests,LeaveType*,Attendance*,Dashboard*,Team*'` → PASS.
- [ ] **Step 7: Selesai.** Laporkan; jangan commit.

---

### Task 2: Draft, isian templat, dan kirim

**Files:**
- Modify: `leave/LeaveDtos.java`, `leave/LeaveService.java`, `leave/MyLeaveController.java`, `leave/LeaveController.java`, `config/SecurityConfig.java` (pastikan `PUT`/`DELETE` di `/api/me/**` tercakup)
- Create: `leave/LetterFieldsValidator.java`
- Test: `leave/LeaveDraftTests.java`, `security/AccessControlTests.java`

**Interfaces:**
- Consumes: Task 1.
- Produces:
  - `record LeaveDraftRequest(Long employeeId, Long leaveTypeId, LocalDate startDate, LocalDate endDate, @Size(max=1000) String reason, @Size(max=30) String contactPhone, @Size(max=300) String leaveAddress, @Size(max=150) String delegateTo, @Size(max=150) String healthFacility, boolean draft)`. Record ini menggantikan `SubmitLeaveRequest`. Saat `draft=false`, validasi lengkap berlaku. Body lama tanpa `draft` dianggap kirim langsung, jadi tetap kompatibel.
  - `LeaveRequestResponse` mendapat `contactPhone, leaveAddress, delegateTo, healthFacility, LetterTemplate letterTemplate, boolean requiresOwnerApproval, Instant createdAt`. `submittedAt/submittedBy` null untuk draft. `verificationToken` **tidak** dikirim di response.
  - `LeaveService`: `createMine(LeaveDraftRequest)`, `updateMyDraft(Long id, LeaveDraftRequest)`, `deleteMyDraft(Long id)`, `submitMyDraft(Long id)`, dan `submitFor(LeaveDraftRequest)` untuk Owner/HR (selalu langsung kirim; `draft` diabaikan).
  - `LetterFieldsValidator.validate(LetterTemplate t, LeaveRequest r)` melempar `ApiException.badRequest(...)` dengan pesan per isian, misalnya "Alamat selama cuti wajib diisi".
  - Endpoint:
    - `POST /api/me/leave-requests` (201)
    - `PUT /api/me/leave-requests/{id}` (draft saja, 200)
    - `DELETE /api/me/leave-requests/{id}` (draft saja, 204)
    - `POST /api/me/leave-requests/{id}/submit` (200)

Aturan kirim (spec §5, §7):

- Jenis aktif, tanggal valid, rentang < 366 hari, tidak tumpang tindih dengan status aktif.
- Bekukan `requiresOwnerApproval` dan `letterTemplate` dari jenis ke pengajuan.
- Isi `submittedBy`, `submittedAt`, dan `verificationToken` (32 heksadesimal acak, `SecureRandom`).
- Status menjadi `PERLU_TINJAU_HR`. Audit `SUBMIT`, lalu event `SUBMITTED`.

Kewajiban isian per templat saat kirim:

| Templat | Isian wajib |
|---|---|
| Semua templat | `reason` |
| `CUTI` | `contactPhone` ("No. HP yang bisa dihubungi"), `leaveAddress` ("Alamat selama cuti") |

`contactPhone` bila diisi hanya boleh berisi digit, spasi, `+`, dan `-`, panjang 8–30.

Aturan draft:

- Simpan tanpa cek tumpang tindih dan tanpa validasi kewajiban.
- Bila `leaveTypeId` diisi, jenisnya harus ada dan aktif.
- Ubah dan hapus hanya bila status `DRAFT` (selain itu 409: "Hanya draft yang bisa diubah" / "Hanya draft yang bisa dihapus").
- Hapus draft menghapus lampirannya dari disk setelah commit. Audit `DELETE` berisi isi sebelumnya.

- [ ] **Step 1: Tulis `LeaveDraftTests` (gagal):**
  - `employeeSavesIncompleteDraftThenSubmits`: POST `draft:true` hanya dengan `leaveTypeId` → 201, status `DRAFT`, `submittedAt` null. PUT melengkapi isian → 200. POST submit → 200, status `PERLU_TINJAU_HR`, `submittedAt` terisi.
  - `submitValidatesTemplateFields`: jenis CUTI + draft tanpa `leaveAddress` → submit 400, detail memuat "Alamat selama cuti". `contactPhone:"abc"` → 400.
  - `draftsDoNotBlockOverlapOrRecapUntilSubmitted`:
    - Ada pengajuan aktif 2026-10-05..07. Draft dengan tanggal sama → 201, tetapi submit-nya 409.
    - Rekap hari kerja tanpa scan untuk karyawan yang hanya punya draft pada tanggal itu tidak memuat penanda `IZIN_MENUNGGU` (pakai endpoint/fixture rekap yang sama dengan `LeaveTests.onlyApprovedLeaveChangesDailyRecap`).
  - `typeSettingsAreFrozenAtSubmit`: jenis `requiresOwnerApproval:false` → kirim → Owner mengubah jenis menjadi `true` → GET pengajuan tetap `requiresOwnerApproval:false`.
  - `onlyDraftsCanBeEditedOrDeleted`: PUT/DELETE pada `PERLU_TINJAU_HR` → 409. DELETE draft → 204, lalu GET → 404, dan audit `DELETE` ada untuk `LEAVE_REQUEST`.
  - `managerRecordsOnBehalfStraightToHrReview`: POST `/api/leave-requests` dengan `draft:true` → status `PERLU_TINJAU_HR`.
  - `othersCannotTouchMyDraft`: karyawan lain melakukan PUT/DELETE/submit → 404.
- [ ] **Step 2:** `-Dtest=LeaveDraftTests` → FAIL.
- [ ] **Step 3: Implementasikan** sesuai Interfaces.
- [ ] **Step 4: `AccessControlTests`:** `PUT/DELETE /api/me/leave-requests/{MISSING}` dan `POST …/{MISSING}/submit` → anonim 401, dan role login lolos otorisasi sesuai pola baris `/api/me/leave-requests` yang ada.
- [ ] **Step 5:** `-Dtest='LeaveDraftTests,LeaveTests,AccessControlTests'` → PASS.
- [ ] **Step 6: Selesai.**

---

### Task 3: Persetujuan bertahap dan riwayat `leave_approvals`

**Files:**
- Create: `db/migration/V13__riwayat_persetujuan.sql`, `leave/LeaveApproval.java`, `leave/LeaveApprovalRepository.java`, `leave/ApprovalStage.java`, `leave/DecisionMethod.java`
- Modify: `leave/LeaveService.java`, `leave/LeaveDtos.java`, `leave/LeaveChangedEvent.java`, `leave/LeaveController.java`
- Test: `leave/LeaveApprovalTests.java`, `leave/LeaveMigrationTests.java`

**Interfaces:**
- Consumes: Task 1–2.
- Produces:
  - `enum ApprovalStage { HR, OWNER }` dan `enum DecisionMethod { WEB, WHATSAPP, LISAN, TERTULIS }`.
  - Entity `LeaveApproval` (`leave_approvals`), dengan field:
    - `id`, `leaveRequestId`, `ApprovalStage stage`, `boolean approved`,
    - `String decidedBy`, `Instant decidedAt`,
    - `DecisionMethod method`, `String recordedBy`, `Instant recordedAt`,
    - `String note`,
    - `String evidenceStorageKey`, `String evidenceOriginalName`, `String evidenceContentType`, `Long evidenceSizeBytes`, `String evidenceSha256`,
    - `String signatureStorageKey`.
    - UNIQUE `(leave_request_id, stage)`.
  - `LeaveApprovalRepository.findByLeaveRequestIdOrderByIdAsc(Long)` dan `findByLeaveRequestIdIn(Collection<Long>)`.
  - `record ApprovalResponse(ApprovalStage stage, boolean approved, String decidedBy, String decidedByName, Instant decidedAt, DecisionMethod method, String recordedBy, String recordedByName, Instant recordedAt, String note, boolean hasEvidence)`. `*Name` adalah `fullName` karyawan yang terhubung ke akun bila ada, selain itu username.
  - `LeaveRequestResponse` mendapat `List<ApprovalResponse> approvals` dan `ApprovalStage nextStage` (null bila tidak pending).
  - `LeaveChangedEvent.Action` mendapat `STAGE_APPROVED` dan `OWNER_DECISION_RECORDED` (yang kedua dipakai Task 4).
  - `pending()` sesuai role pemanggil:
    - HR melihat `PERLU_TINJAU_HR`,
    - Owner melihat `PERLU_TINJAU_OWNER` ditambah `PERLU_TINJAU_HR` dengan `requiresOwnerApproval=false`.
  - `PendingItem` mendapat `LeaveStatus status`.

Aturan (spec §5):

- **`approve(id, note)` saat `PERLU_TINJAU_HR`:**
  - HR → tahap `HR`.
  - Owner boleh hanya bila `!requiresOwnerApproval`, atau bila tidak ada akun `HR_FINANCE` aktif lain selain akun yang terhubung ke karyawan pemohon. Selain itu 403: "Tahap ini harus ditinjau HR/Finance lebih dulu".
  - Wajib surat (K-077) dicek di sini.
  - Hasil: `requiresOwnerApproval` → `PERLU_TINJAU_OWNER` dengan event `STAGE_APPROVED`; selain itu → `DISETUJUI` dengan event `APPROVED`.
- **`approve` saat `PERLU_TINJAU_OWNER`:** hanya Owner (HR → 403: "Menunggu persetujuan Owner"). Tahap `OWNER`, lalu `DISETUJUI`.
- Setiap tahap web menulis baris approval dengan `method=WEB` dan `recordedBy=decidedBy`. `decidedBy/decidedAt/decisionNote` di `leave_requests` diisi saat keputusan akhir.
- **`reject`** oleh role yang berhak di tahap itu → `DITOLAK`, dengan baris approval `approved=false`. Alasan wajib.
- **K-079** dicek di setiap tahap.
- **Bersamaan:** flush `@Version`, dan pelanggaran unique `(leave_request_id, stage)` → 409: "Pengajuan ini baru saja diputus; muat ulang halaman".
- **V13 untuk data lama:**
  - Setiap `DISETUJUI`/`DITOLAK` dengan `decided_by` terisi mendapat satu baris: stage `HR`, `approved` sesuai status, `decided_by`, `decided_at`, `method='WEB'`, `recorded_by=decided_by`, `recorded_at=decided_at`, `note=decision_note`.
- Penghitung akun HR lain dibuat sebagai bean kecil `HrReviewerCounter` (`long countOtherActiveHr(Long requesterEmployeeId)`) supaya bisa di-stub di test.

- [ ] **Step 1: Tulis `LeaveApprovalTests` (gagal):**
  - `doubleApprovalNeedsHrThenOwner` (TC-19):
    - Jenis `requiresOwnerApproval:true` → kirim → HR menyetujui → `PERLU_TINJAU_OWNER`, `nextStage:"OWNER"`, satu approval.
    - Rekap tanggal itu masih memuat penanda `IZIN_MENUNGGU`.
    - Owner menyetujui → `DISETUJUI`, `approvals[1].stage=="OWNER"`, dan rekap berstatus `IZIN`.
  - `ownerCannotSkipHrStageForDoubleApprovalType` → 403.
  - `hrCannotApproveOwnerStage` → 403.
  - `singleApprovalTypeIsDoneByHrOrOwner`: Owner menyetujui dari `PERLU_TINJAU_HR` → `DISETUJUI`, satu approval `HR` dengan `decidedBy` Owner.
  - `rejectionAtEitherStageEndsAsRejected`: HR menolak → `DITOLAK`. Pada pengajuan lain, HR menyetujui lalu Owner menolak → `DITOLAK`, dengan approvals [HR true, OWNER false].
  - `ownerMayDoHrStageWhenRequesterIsTheOnlyHr`: stub `HrReviewerCounter` → 0 (`@MockitoBean`). Owner menyetujui tahap HR → `PERLU_TINJAU_OWNER`, lalu tahap OWNER → `DISETUJUI`.
  - `concurrentStageApprovalsRecordOneStage`: dua akun HR berbeda bersamaan (pola `LeaveTests.concurrentDecisionsOnSameRequestOnlyOneWins`) → satu 200, satu 409, dan tepat satu baris approval.
  - `pendingListDependsOnRole` sesuai aturan di atas.
  - Di `LeaveMigrationTests`: `legacyDecisionsBecomeApprovalRows`. Jalankan Flyway pada schema PostgreSQL terpisah sampai `target=12`, insert pengajuan `DISETUJUI` dengan `decided_by` lewat JDBC, migrasikan ke 13, lalu harapkan satu baris `HR/WEB`.
- [ ] **Step 2:** → FAIL.
- [ ] **Step 3: Implementasikan.** `detailed()` dan daftar (`withAttachments`) memuat `approvals` dengan satu query untuk daftar.
- [ ] **Step 4:** `-Dtest='LeaveApprovalTests,LeaveTests,LeaveDraftTests,LeaveMigrationTests,Attendance*'` → PASS.
- [ ] **Step 5: Selesai.**

---

### Task 4: Keputusan Owner yang dicatat HR

**Files:**
- Modify: `leave/LeaveService.java`, `leave/LeaveController.java`, `leave/LeaveDtos.java` (pakai ulang `AttachmentStorage`)
- Test: `leave/OwnerDecisionTests.java`, `security/AccessControlTests.java`

**Interfaces:**
- Consumes: Task 3.
- Produces:
  - `POST /api/leave-requests/{id}/owner-decision` (multipart).
    - Bagian `decision`: JSON `OwnerDecisionRequest(boolean approved, @NotNull DecisionMethod method, @NotNull LocalDateTime decidedAt, @NotBlank @Size(max=1000) String note)`. `decidedAt` dibaca di zona `COMPANY_TIMEZONE`.
    - Bagian `evidence` (opsional).
    - Respons: `LeaveRequestResponse`.
  - `GET /api/leave-requests/{id}/approvals/{stage}/evidence` mengunduh bukti (`attachment` + `nosniff`) dan diaudit `VIEW` (entitas `LEAVE_EVIDENCE`).
  - `LeaveService.recordOwnerDecision(Long id, OwnerDecisionRequest req, MultipartFile evidence)` dan `LeaveService.downloadEvidence(Long id, ApprovalStage stage): AttachmentFile`.

Aturan (spec §6):

- Hanya `HR_FINANCE` (Owner → 403: "Owner memutus langsung lewat tombol Setujui atau Tolak").
- Status harus `PERLU_TINJAU_OWNER` (selain itu 409).
- K-079 berlaku.
- `method=WEB` → 400.
- `decidedAt` di masa depan, atau sebelum `decidedAt` tahap HR → 400.
- Bukti wajib untuk `WHATSAPP` dan `TERTULIS` (400: "Bukti wajib diunggah untuk keputusan lewat WhatsApp atau tertulis"). Validasi file sama dengan K-078.
- Baris approval:
  - `stage=OWNER`, `decidedBy="OWNER"`, `decidedByName="Owner"`,
  - `method` sesuai isian, `recordedBy` = username HR,
  - `signatureStorageKey` null.
- Status → `DISETUJUI`/`DITOLAK`, `decidedBy` di `leave_requests` = username HR pencatat.
- Audit `OWNER_DECISION`. Event `OWNER_DECISION_RECORDED`.

- [ ] **Step 1: Tulis `OwnerDecisionTests` (gagal):**
  - `hrRecordsOwnerApprovalGivenOnWhatsappWithEvidence` → `DISETUJUI`; `approvals[1]` berisi `method:"WHATSAPP"`, `recordedBy:"hr-absensi"`, `hasEvidence:true`; dan audit `OWNER_DECISION` ada.
  - `whatsappAndWrittenDecisionsNeedEvidence` → 400 tanpa file. `LISAN` tanpa file → 200.
  - `decisionTimeCannotBeInFutureOrBeforeHrStage` → 400 untuk keduanya.
  - `ownerCannotUseRecordingEndpointAndOnlyOwnerStageQualifies`: Owner → 403. Status `PERLU_TINJAU_HR` → 409.
  - `recordedRejectionEndsAsRejected` → `DITOLAK`.
  - `evidenceDownloadIsAuditedAndHiddenFromEmployees`: GET evidence oleh HR → 200 dengan `Content-Disposition: attachment` dan audit `VIEW`. Karyawan ke path manajer → 403.
- [ ] **Step 2:** → FAIL.
- [ ] **Step 3: Implementasikan.** Pakai pola pembersihan file `afterCompletion` yang sama seperti `attach`.
- [ ] **Step 4: `AccessControlTests`:** `POST /api/leave-requests/{MISSING}/owner-decision` → HR 404, Owner 403, karyawan 403, anonim 401. `GET …/approvals/OWNER/evidence` → HR/Owner 404, karyawan 403.
- [ ] **Step 5:** `-Dtest='OwnerDecisionTests,LeaveApprovalTests,AccessControlTests'` → PASS.
- [ ] **Step 6: Selesai.**

---

### Task 5: Spesimen tanda tangan per akun

**Files:**
- Create: `db/migration/V14__spesimen_tanda_tangan.sql`, paket `signature/`: `UserSignature.java`, `UserSignatureRepository.java`, `SignatureService.java`, `SignatureController.java`, `SignatureDtos.java`
- Modify: `leave/LeaveService.java` (pembekuan), `leave/LeaveRequest.java` (`applicantSignatureKey`). V14 juga menambah `leave_requests.applicant_signature_key varchar(100)`.
- Test: `signature/SignatureTests.java`, `security/AccessControlTests.java`

**Interfaces:**
- Produces:
  - Tabel `user_signatures(id bigserial, user_account_id UNIQUE FK, storage_key, sha256, updated_at)`.
  - `GET /api/me/signature` → `200 image/png` (milik sendiri), atau 404 bila belum ada.
  - `GET /api/me/signature/status` → `SignatureStatus(boolean exists, Instant updatedAt)`.
  - `PUT /api/me/signature` (multipart `file`, PNG/JPG ≤ 1 MB) → 200 `SignatureStatus`.
  - `DELETE /api/me/signature` → 204.
  - `SignatureService.freezeCopy(String username): String` (salin ke key baru; null bila tidak ada) dan `SignatureService.load(String key): byte[]`.
- Normalisasi:
  - `ImageIO` headless; skala proporsional ke dalam 600×200; output PNG `TYPE_INT_ARGB`.
  - Piksel dengan setiap kanal ≥ 245 menjadi transparan.
  - Jenis file dicek dari magic bytes. File yang bukan gambar valid → 400: "File tanda tangan harus PNG atau JPG".
- Audit:
  - `UPLOAD`/`DELETE` untuk entitas `USER_SIGNATURE`, berisi sha256 dan ukuran, tanpa isi gambar.
- Pembekuan:
  - Saat kirim, `applicantSignatureKey = freezeCopy(username pengirim)` hanya bila pengirim adalah karyawan pemohon sendiri.
  - Saat tahap web disetujui, `LeaveApproval.signatureStorageKey = freezeCopy(username penyetuju)`.

- [ ] **Step 1: Tulis `SignatureTests` (gagal):**
  - `uploadNormalizesToTransparentPngWithinBounds`: PNG 1200×600 berlatar putih → GET mengembalikan lebar ≤ 600, tinggi ≤ 200, dan alpha (0,0) = 0.
  - `rejectsNonImagesAndOversizedFiles` → 400 untuk keduanya.
  - `deleteRemovesSignature` → GET 404.
  - `nobodyCanReadAnotherAccountsSignature`: akun A mengunggah; akun B melakukan GET `/api/me/signature` → 404.
  - `approvalFreezesSignatureCopy`: HR punya spesimen dan menyetujui. HR mengganti spesimen. `signature_storage_key` di approval tetap menunjuk file dengan sha256 lama.
- [ ] **Step 2:** → FAIL.
- [ ] **Step 3: Implementasikan.** Simpan lewat `AttachmentStorage`.
- [ ] **Step 4: `AccessControlTests`:** keempat endpoint → anonim 401, dan ketiga role lolos otorisasi.
- [ ] **Step 5:** `-Dtest='SignatureTests,LeaveApprovalTests,AccessControlTests'` → PASS.
- [ ] **Step 6: Selesai.**

---

### Task 6: Surat PDF dan verifikasi publik

**Files:**
- Modify: `backend/pom.xml`. Tambah `com.github.librepdf:openpdf` dan `com.google.zxing:core` versi stabil terbaru dari Maven Central yang kompatibel dengan Java 21; catat versinya.
- Create:
  - `backend/src/main/resources/fonts/`: Poppins Bold + Open Sans Regular/SemiBold (TTF statis) + `OFL.txt`, diunduh dari `https://github.com/google/fonts` (`ofl/poppins`, `ofl/opensans`). Bila hanya variable font yang tersedia, cari TTF statis dari rilis resmi googlefonts; bila gagal, pakai Helvetica bawaan OpenPDF dan laporkan.
  - `leave/letter/LeaveLetterRenderer.java`, `leave/letter/LetterModel.java`
  - `leave/PublicLetterController.java`, `common/RateLimiter.java`
- Modify: `leave/MyLeaveController.java`, `leave/LeaveController.java`, `config/SecurityConfig.java` (`permitAll` untuk `GET /api/public/letters/**`), `application.properties` (`app.public-base-url=` kosong, `app.public-letters.rate-per-minute=30`)
- Test: `leave/LeaveLetterTests.java`, `security/AccessControlTests.java`

**Interfaces:**
- Consumes: Task 3 (approvals), Task 4 (method), Task 5 (`SignatureService.load`), Task 2 (isian templat).
- Produces:
  - `GET /api/me/leave-requests/{id}/letter.pdf` dan `GET /api/leave-requests/{id}/letter.pdf` → `application/pdf`, `Content-Disposition: attachment; filename="IZ-000123.pdf"`. Versi manajer diaudit `VIEW` (entitas `LEAVE_LETTER`).
  - `LeaveLetterRenderer.render(LetterModel model): byte[]`.
  - `GET /api/public/letters/{token}` → `PublicLetterResponse(String letterNumber, String employeeName, String branchName, String leaveTypeName, LocalDate startDate, LocalDate endDate, LeaveStatus status, List<PublicApproval> approvals)` dengan `PublicApproval(ApprovalStage stage, boolean approved, String decidedByName, Instant decidedAt, DecisionMethod method)`.
    - Tanpa alasan, isian templat, maupun lampiran.
    - Token tidak dikenal atau milik draft → 404: "Surat tidak ditemukan".
    - Lebih dari batas per menit per IP → 429.
  - `RateLimiter.tryAcquire(String key): boolean` (jendela tetap per menit, `ConcurrentHashMap`).

Isi PDF (spec §8):

- A4 dengan margin 18 mm.
- Kop "TAKKA STEEL" + nama cabang, nomor `IZ-%06d`.
- Judul per templat:
  - CUTI → "SURAT PERMOHONAN CUTI"
  - SAKIT → "SURAT KETERANGAN SAKIT"
  - IZIN → "SURAT PERMOHONAN IZIN"
  - UMUM → "SURAT PERMOHONAN IZIN / CUTI"
- Identitas: nama, no. karyawan, jabatan, cabang.
- Kalimat jenis + rentang + jumlah hari, diikuti isian templat yang terisi dengan label: "Keperluan", "No. HP", "Alamat selama cuti", "Tugas dilimpahkan kepada", "Faskes / dokter".
- Nama lampiran.
- Kotak tanda tangan:
  - "Pemohon", lalu "HR/Finance" + "Owner" (persetujuan ganda) atau satu "Disetujui oleh".
  - Isi kotak: gambar spesimen beku. Untuk tahap yang dicatat HR, teks "Disetujui melalui WhatsApp pada dd-MM-yyyy HH.mm, dicatat oleh <nama HR>" (kata cara sesuai method: WhatsApp/lisan/tertulis; "Ditolak" untuk penolakan). Tanpa spesimen, nama + "disetujui secara elektronik". Tahap yang belum diputus dibiarkan kosong.
- Cap diagonal abu-abu: `DRAFT` → "DRAFT", pending → "MENUNGGU PERSETUJUAN", `DITOLAK` → "DITOLAK", `DIBATALKAN` → "DIBATALKAN".
- QR ZXing 30 mm + teks URL `<base>/verifikasi/<token>`, hanya bila bukan draft. `<base>` = `app.public-base-url`, atau origin request bila kosong.
- Kaki surat: "Dibuat dari sistem absensi pada …" (zona `COMPANY_TIMEZONE`).

- [ ] **Step 1: Tulis `LeaveLetterTests` (gagal).** Gunakan `PdfReader` + `PdfTextExtractor` dari OpenPDF.
  - `draftLetterIsWatermarkedAndHasNoQr`: teks memuat "DRAFT" dan nama karyawan, tetapi tidak memuat "/verifikasi/".
  - `approvedDoubleLetterShowsBothStagesAndVerificationLink`: teks memuat "HR/Finance", "Owner", dan "/verifikasi/", tetapi tidak memuat "MENUNGGU PERSETUJUAN".
  - `recordedOwnerDecisionIsWrittenAsTextNotSignature`: teks memuat "Disetujui melalui WhatsApp" dan "dicatat oleh".
  - `cutiLetterShowsTemplateFields`: memuat "SURAT PERMOHONAN CUTI" dan "Alamat selama cuti".
  - `employeeCannotDownloadAnotherEmployeesLetter` → 404. `managerDownloadIsAudited` → audit `VIEW` `LEAVE_LETTER`.
  - `publicVerificationShowsMinimalData`: anonim → 200. JSON tidak memiliki `reason`, `leaveAddress`, maupun `attachments`. Token acak → 404. Draft → 404.
  - `publicVerificationIsRateLimited`: `@TestPropertySource(properties="app.public-letters.rate-per-minute=3")` di kelas test terpisah `PublicLetterRateLimitTests` → permintaan ke-4 → 429.
- [ ] **Step 2:** → FAIL.
- [ ] **Step 3: Implementasikan.** Font dimuat sekali dari classpath dengan `IDENTITY_H` + embedded.
- [ ] **Step 4: `AccessControlTests`:** `GET /api/public/letters/xxx` anonim → 404 (bukan 401). Kedua `letter.pdf` → anonim 401. Karyawan ke path manajer → 403.
- [ ] **Step 5:** `-Dtest='LeaveLetterTests,PublicLetterRateLimitTests,AccessControlTests'` → PASS. Lalu jalankan **seluruh suite backend** `./mvnw -q test` dan laporkan jumlah lulus/gagal.
- [ ] **Step 6: Selesai.**

---

### Task 7: Frontend dasar izin dan halaman manajer

**Files:**
- Modify: `frontend/src/lib/types.ts`, `lib/leave.ts`, `pages/LeaveTypesPage.tsx`, `pages/LeaveRequestsPage.tsx`, `pages/LeaveDetailPage.tsx` (bagian manajer), `components/LeaveNotifications.tsx`, `App.tsx` (route surat lama → `Navigate` ke detail), `components/LeaveRequestForm.tsx` (isian templat saat mencatat atas nama karyawan)
- Create: `components/leave/LeaveStatusBadge.tsx`, `components/leave/ApprovalTimeline.tsx`, `components/leave/OwnerDecisionDialog.tsx`, `components/leave/LetterDownloadButton.tsx`, `lib/leaveLetter.ts`
- Delete: `pages/LeaveLetterPage.tsx`
- Test: `pages/LeavePages.test.tsx` (perbarui), `components/leave/ApprovalTimeline.test.tsx`, `components/leave/OwnerDecisionDialog.test.tsx`

**Interfaces:**
- Consumes: JSON Task 1–6.
- Produces (dipakai Task 8–10):
  - `type LeaveStatus = 'DRAFT' | 'PERLU_TINJAU_HR' | 'PERLU_TINJAU_OWNER' | 'DISETUJUI' | 'DITOLAK' | 'DIBATALKAN'`
  - `type LetterTemplate = 'UMUM' | 'IZIN' | 'CUTI' | 'SAKIT'`
  - `type DecisionMethod = 'WEB' | 'WHATSAPP' | 'LISAN' | 'TERTULIS'`
  - `type ApprovalStage = 'HR' | 'OWNER'`
  - `interface LeaveApproval { stage: ApprovalStage; approved: boolean; decidedBy: string; decidedByName: string; decidedAt: string; method: DecisionMethod; recordedBy: string; recordedByName: string; recordedAt: string; note: string | null; hasEvidence: boolean }`
  - `LeaveRequest` ditambah `contactPhone`, `leaveAddress`, `delegateTo`, `healthFacility` (string | null), `letterTemplate`, `requiresOwnerApproval`, `createdAt`, `approvals`, `nextStage: ApprovalStage | null`. `submittedAt`/`submittedBy` boleh null. `LeaveType` ditambah `requiresOwnerApproval`, `letterTemplate`.
  - `LEAVE_STATUS_LABELS`, `LEAVE_STATUS_TONES`: DRAFT/DIBATALKAN → `neutral`, pending → `warning`, DISETUJUI → `success`, DITOLAK → `danger`.
  - `isPendingStatus(s)`.
  - `LETTER_FIELDS: Record<LetterTemplate, { reasonLabel: string; fields: Array<{ key: 'contactPhone' | 'leaveAddress' | 'delegateTo' | 'healthFacility'; label: string; required: boolean; inputMode?: 'tel' }> }>` sesuai spec §7:
    - reasonLabel UMUM "Alasan", IZIN "Keperluan", CUTI "Keperluan cuti", SAKIT "Keluhan / keterangan sakit".
    - Label isian "No. HP yang bisa dihubungi", "Alamat selama cuti", "Tugas dilimpahkan kepada", "Nama faskes / dokter".
  - `letterNumber(id): string` → `IZ-000123`.
  - `<LeaveStatusBadge status size? />`: ikon + teks.
  - `<ApprovalTimeline request />`: langkah Diajukan → Ditinjau HR → (Ditinjau Owner bila `requiresOwnerApproval`) → hasil akhir. Setiap langkah menampilkan nama, waktu, dan cara ("lewat web", atau "lewat WhatsApp, dicatat oleh <recordedByName>"). Langkah aktif ditandai "Sedang ditinjau".
  - `downloadLetter(mode: LeaveMode, id: number): Promise<Blob>`
  - `shareLetter(mode, request): Promise<'shared' | 'downloaded'>`: `navigator.canShare({files})` bila tersedia, selain itu unduh.
  - `<LetterDownloadButton mode request />`.

Halaman manajer:

- **Detail:**
  - "Setujui"/"Tolak" hanya bila tahap cocok: HR saat `nextStage==='HR'`; Owner saat `nextStage==='OWNER'` atau (`nextStage==='HR'` && `!requiresOwnerApproval`). Server tetap penentu, jadi tampilkan error 403-nya bila terjadi.
  - "Catat keputusan Owner" untuk HR saat `PERLU_TINJAU_OWNER` membuka `OwnerDecisionDialog` (keputusan, cara, tanggal+jam, catatan, bukti; bukti wajib untuk WhatsApp/Tertulis di klien).
  - Unduh bukti.
- **Daftar:** filter status baru.
- **Jenis Izin:** switch "Butuh persetujuan Owner" dan select "Templat surat".
- **Lonceng:** status per item. Event `OWNER_DECISION_RECORDED` memunculkan pop-up hanya untuk Owner.

- [ ] **Step 1: Tulis test (gagal):**
  - `ApprovalTimeline.test.tsx`:
    - "shows HR then Owner steps for double approval": `PERLU_TINJAU_OWNER` + satu approval HR → teks nama HR + "lewat web", dan langkah Owner bertanda "Sedang ditinjau".
    - "shows recorded owner decision": approval OWNER WHATSAPP → "lewat WhatsApp" dan "dicatat oleh".
  - `OwnerDecisionDialog.test.tsx`: WhatsApp tanpa file → "Bukti wajib diunggah" dan `fetch` tidak dipanggil. Dengan file → `fetch` ke `/api/leave-requests/5/owner-decision` dengan `FormData` yang memuat `decision` dan `evidence`.
  - `LeavePages.test.tsx`: HR melihat "Catat keputusan Owner" pada `PERLU_TINJAU_OWNER` tanpa tombol "Setujui"; Owner melihat "Setujui". `/izin/1/surat` diarahkan ke `/izin/1`.
- [ ] **Step 2:** `npx vitest run src/components/leave src/pages/LeavePages.test.tsx` → FAIL.
- [ ] **Step 3: Implementasikan.** Hapus `LeaveLetterPage.tsx` beserta rutenya.
- [ ] **Step 4:** `npx vitest run` (seluruh) + `npx tsc -b --noEmit` → PASS.
- [ ] **Step 5: Selesai.**

---

### Task 8: Navigasi karyawan, Beranda, dan Profil dengan tanda tangan

**Files:**
- Modify: `frontend/src/navigation.ts` (+ `navigation.test.ts`), `App.tsx`, `pages/ProfilePage.tsx`
- Create: `pages/HomePage.tsx`, `components/signature/SignaturePad.tsx`, `components/signature/SignatureCard.tsx`, `lib/signature.ts`
- Test: `pages/HomePage.test.tsx`, `components/signature/SignaturePad.test.tsx`

**Interfaces:**
- Consumes: Task 5 endpoints, Task 7 (`LeaveStatusBadge`, `isPendingStatus`), endpoint rekan kerja yang sudah dipakai `TeamPage.tsx`/`TeamMemberPage.tsx` (gunakan endpoint dan identitas diri yang sama seperti di sana dan di `ProfilePage`).
- Produces:
  - Menu karyawan (urutan, semuanya `mobile: true`): `/beranda` "Beranda" (ikon `House`), `/izin-saya` "Izin", `/rekan-kerja` "Rekan", `/profil` "Profil". `homePathFor('EMPLOYEE') === '/beranda'`.
  - `<SignaturePad onSave={(blob: Blob) => void} saving?: boolean />`: kanvas dengan pointer events, `touch-action: none`, tinggi 200 px, tombol "Hapus coretan" dan "Simpan" (nonaktif bila kosong).
  - `<SignatureCard />`: pratinjau `GET /api/me/signature` (blob URL), tombol "Gambar", "Unggah gambar", "Hapus".
  - `lib/signature.ts`: `fetchSignatureStatus()`, `uploadSignature(blob: Blob)`, `deleteSignature()`.

Beranda (spec §11):

- Sapaan "Halo, <nama depan>" dan tanggal hari ini.
- Kartu **Hari ini**: jam masuk, jam pulang, dan status diri sendiri hari ini.
- **Pengajuan berjalan**: maks. 3 berstatus DRAFT/pending, dengan badge dan link ke detail, ditambah link "Lihat semua".
- Tombol pill **Ajukan izin** → `/izin-saya/baru`.
- **Bulan ini**: hitungan Hadir/Izin/Tidak hadir bila data rentang sendiri tersedia lewat endpoint yang ada. Bila tidak, lewati bagian ini dan laporkan; jangan membuat endpoint baru.
- Skeleton, `ErrorAlert`, dan keadaan kosong "Belum ada pengajuan berjalan".

- [ ] **Step 1: Tulis test (gagal):**
  - `navigation.test.ts`: `homePathFor('EMPLOYEE')` → `'/beranda'`; path menu karyawan berurutan `['/beranda','/izin-saya','/rekan-kerja','/profil']`. Perbarui ekspektasi lama `/profil`.
  - `HomePage.test.tsx`: dengan mock API, ada sapaan, kartu Hari ini, dua pengajuan dengan label "Perlu ditinjau Owner" dan "Draft", dan link "Ajukan izin" ke `/izin-saya/baru`. Daftar kosong → "Belum ada pengajuan berjalan".
  - `SignaturePad.test.tsx`: "Simpan" nonaktif awalnya. Setelah pointerdown/move/up (mock `getContext` dan `toBlob`) → aktif, dan klik memanggil `onSave` dengan Blob.
- [ ] **Step 2:** → FAIL.
- [ ] **Step 3: Implementasikan.** Profil menampilkan `SignatureCard`.
- [ ] **Step 4:** `npx vitest run` + `npx tsc -b --noEmit` → PASS.
- [ ] **Step 5: Selesai.**

---

### Task 9: Wizard pengajuan, daftar, dan detail karyawan

**Files:**
- Modify: `frontend/src/pages/MyLeavePage.tsx`, `pages/LeaveDetailPage.tsx` (bagian `mode="mine"`), `App.tsx`
- Create: `pages/LeaveWizardPage.tsx`, `components/leave/wizard/StepType.tsx`, `StepDetails.tsx`, `StepAttachments.tsx`, `StepReview.tsx`, `lib/whatsapp.ts`
- Test: `pages/LeaveWizardPage.test.tsx`, `pages/MyLeavePage.test.tsx`, `lib/whatsapp.test.ts`, `pages/LeavePages.test.tsx`

**Interfaces:**
- Consumes: Task 2 endpoints, Task 6 (`letter.pdf`), Task 7 (`LETTER_FIELDS`, `LeaveStatusBadge`, `ApprovalTimeline`, `shareLetter`, `LetterDownloadButton`, `letterNumber`), Task 8 (`SignatureCard`).
- Produces:
  - Route `/izin-saya/baru` dan `/izin-saya/:id/ubah` → `LeaveWizardPage`.
  - `buildWhatsAppUrl(request: LeaveRequest, origin: string): string` → `'https://wa.me/?text=' + encodeURIComponent(\`Permohonan ${leaveTypeName} ${letterNumber(id)} a.n. ${employeeName}, ${formatLeaveRange(request)}. Mohon ditinjau: ${origin}/izin/${id}\`)`.

Wizard (spec §11):

- Indikator langkah "Jenis", "Tanggal & isian", "Lampiran & tanda tangan", "Pratinjau".
- **Langkah 1:**
  - Tombol jenis aktif ≥ 56 px dengan nama, keterangan, dan chip "Butuh persetujuan HR & Owner" / "Wajib surat".
  - "Lanjut" membuat draft (`POST {leaveTypeId, draft:true}`) bila belum ada id, lalu `navigate('/izin-saya/<id>/ubah', {replace:true})`. Bila sudah ada id, `PUT`.
- **Langkah 2:**
  - Tanggal mulai/selesai (`type="date"`) dengan jumlah hari kalender dihitung di klien.
  - Isian `LETTER_FIELDS[template]` (template diambil dari jenis terpilih); `inputMode="tel"` untuk No. HP.
  - Label di atas isian, error di bawahnya.
- **Langkah 3:** unggah lampiran (`ATTACHMENT_ACCEPT`), daftar lampiran, dan `SignatureCard` dengan keterangan "Tanda tangan dipakai di surat".
- **Langkah 4:**
  - Ringkasan isian, tombol "Lihat PDF" (blob di tab baru), "Simpan draft" (ke detail), dan **"Kirim"**.
  - Bila 400 menyebut isian langkah 2, atau bila 409 → kembali ke langkah 2 dengan pesan. Bila 400 soal lampiran → langkah 3.
- Setiap "Lanjut"/"Kembali" menyimpan draft (`PUT`).
- Transisi geser + pudar 200 ms, statis saat `prefers-reduced-motion`.

Daftar `/izin-saya`:

- Tab "Berjalan" (pending), "Draft", "Selesai".
- Baris berisi jenis, rentang, dan badge, dengan link ke detail. Ada tombol "Ajukan izin".
- Keadaan kosong per tab: "Belum ada pengajuan berjalan", "Belum ada draft", "Belum ada pengajuan selesai".

Detail karyawan:

- Badge besar dan `ApprovalTimeline`.
- Tombol per status:
  - DRAFT → "Ubah", "Hapus" (dialog konfirmasi), "Kirim".
  - Pending → "Kirim ke WhatsApp" (`window.open(buildWhatsAppUrl(request, window.location.origin), '_blank')`), "Bagikan PDF", "Tarik pengajuan".
  - Semua status → "Unduh PDF".

- [ ] **Step 1: Tulis test (gagal):**
  - `whatsapp.test.ts`: hasil diawali `https://wa.me/?text=` dan teks yang di-decode memuat `IZ-000123`, `Budi Santoso`, dan `https://absen.contoh/izin/123`.
  - `LeaveWizardPage.test.tsx`:
    - "creates draft after choosing type then shows CUTI fields": memilih "Cuti" → Lanjut → POST dengan `draft:true` dipanggil, lalu langkah 2 menampilkan "Alamat selama cuti" dan "No. HP yang bisa dihubungi".
    - "submit error returns to details step": submit 400 dengan detail "Alamat selama cuti wajib diisi" → langkah 2 tampil dengan pesan itu.
  - `MyLeavePage.test.tsx`: tab "Draft" hanya berisi DRAFT; tab kosong → "Belum ada draft".
  - `LeavePages.test.tsx` (mode mine): pending → "Kirim ke WhatsApp" dan "Tarik pengajuan"; DRAFT → "Ubah", "Hapus", "Kirim"; DISETUJUI → "Unduh PDF" tanpa "Tarik pengajuan".
- [ ] **Step 2:** → FAIL.
- [ ] **Step 3: Implementasikan.**
- [ ] **Step 4:** `npx vitest run` + `npx tsc -b --noEmit` → PASS.
- [ ] **Step 5: Selesai.**

---

### Task 10: Halaman verifikasi, Rekan Kerja di HP, dan dokumentasi

**Files:**
- Create: `frontend/src/pages/VerifyLetterPage.tsx`
- Modify:
  - `App.tsx` (route publik `/verifikasi/:token` di luar `RequireAuth`)
  - `pages/TeamPage.tsx` (di bawah `md`, baris berupa kartu ringkas + input "Cari nama"; kolom/data tetap K-083/K-084)
  - `frontend/vite.config.ts` bila proxy dev perlu
- Dokumen:
  - `docs/keputusan.md`: K-113 status dan persetujuan bertahap; K-114 PDF server (menggantikan sebagian K-082); K-115 spesimen tanda tangan dan pembekuan; K-116 verifikasi publik bertoken + batas laju; K-117 keputusan Owner dicatat HR; K-118 Beranda dan navigasi karyawan. Status "Disepakati pengguna 2026-10-24" dengan tautan ke spec. Tambahkan juga baris di tabel daftar.
  - `docs/aturan-bisnis.md` §4.8: status, templat, dan TC-19 diuji (`LeaveApprovalTests.doubleApprovalNeedsHrThenOwner`).
  - `docs/alur.md`: diagram izin diganti `stateDiagram` dari spec §5.
  - `docs/design-system.md`: garis waktu status, langkah wizard, navigasi karyawan.
  - `docs/rencana-pengerjaan.md`: bagian revisi meeting ditandai diimplementasikan.
- Test: `pages/VerifyLetterPage.test.tsx`, `pages/TeamPages.test.tsx` (perbarui)

**Interfaces:**
- Consumes: `GET /api/public/letters/{token}` (Task 6).

Halaman verifikasi:

- Tanpa kerangka aplikasi, dengan brand dan judul "Verifikasi Surat".
- Menampilkan nomor, nama, cabang, jenis, rentang, badge status besar, dan daftar persetujuan.
- 404 → "Surat tidak ditemukan. Periksa kembali kode QR atau link." 429 → "Terlalu banyak permintaan, coba lagi sebentar."
- Mendukung mode terang dan gelap.

- [ ] **Step 1: Tulis test (gagal):**
  - `VerifyLetterPage.test.tsx`: DISETUJUI → "IZ-000123", "Disetujui", dua baris persetujuan. 404 → teks tidak ditemukan. Dirender tanpa sesi login.
  - `TeamPages.test.tsx`: "Cari nama" menyaring baris.
- [ ] **Step 2:** → FAIL. **Step 3:** Implementasikan dan perbarui dokumen. **Step 4:** `npx vitest run` + `npx tsc -b --noEmit` → PASS. **Step 5: Selesai.**

---

### Task 11: Uji end-to-end dengan Playwright MCP (controller)

Dikerjakan controller karena butuh browser MCP dan aplikasi yang menyala.

- [ ] **Step 1:** Seluruh suite: backend (salinan) `./mvnw -q test`, dan frontend `npx vitest run` + `npm run build`. Catat jumlahnya.
- [ ] **Step 2:** Nyalakan aplikasi tanpa mengganggu proses pengguna. Periksa port yang sudah dipakai; jalankan salinan backend di port bebas (misalnya `SERVER_PORT=8090`, database dev yang sama atau database terpisah) dan Vite dev dengan proxy ke port itu.
- [ ] **Step 3: Skenario lewat Playwright MCP** (karyawan 375×812, manajer 1280×800):
  1. Owner: di Jenis Izin, Cuti = "Butuh persetujuan Owner" + templat CUTI. Siapkan akun HR dan karyawan.
  2. Karyawan: Beranda → Ajukan izin → Cuti → isi → gambar tanda tangan → Pratinjau → Simpan draft → Kirim → "Perlu ditinjau HR". "Kirim ke WhatsApp" menghasilkan URL `wa.me` yang benar.
  3. HR: lonceng → detail → Setujui → "Perlu ditinjau Owner".
  4. Pengajuan kedua: HR mencatat keputusan Owner lewat WhatsApp dengan bukti → Disetujui, dengan "dicatat oleh" di garis waktu.
  5. Owner menyetujui pengajuan pertama → Disetujui.
  6. Karyawan mengunduh PDF (`application/pdf`); `/verifikasi/<token>` tanpa login menampilkan Disetujui.
  7. Screenshot Beranda dan wizard dalam mode terang/gelap; tidak ada scroll horizontal di 375 px.
- [ ] **Step 4:** Setiap kegagalan diperbaiki lewat subagent perbaikan, lalu test dan skenario dijalankan ulang sampai lulus.
- [ ] **Step 5:** Laporkan hasil dan file yang berubah. Jangan commit.
