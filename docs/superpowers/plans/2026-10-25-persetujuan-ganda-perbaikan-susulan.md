# Perbaikan susulan persetujuan ganda (2026-10-25)

**Spec:** `docs/superpowers/specs/2026-10-24-persetujuan-ganda-surat-izin-design.md`
**Asal:** review akhir plan `2026-10-24-persetujuan-ganda-surat-izin.md` dengan vonis "Ready after fixes".

## Global Constraints

- V12–V14 sudah di-commit dan di-push (38b6ec9). **Jangan mengubah V1–V14.** Perubahan data baru masuk lewat `V15__...sql`.
- Jangan commit atau push. Jalankan test backend dari salinan di scratchpad (lihat shared-context).
- Setiap perubahan data dicatat lewat `AuditService` di transaksi yang sama. Error memakai `ApiException`/ProblemDetail, dengan pesan dalam Bahasa Indonesia.
- Field respons baru hanya **ditambahkan**, tidak ada yang diganti nama atau dihapus. Server tetap penentu hak; flag hanya untuk tampilan.
- Teks UI dalam Bahasa Indonesia, tanpa em-dash, target sentuh ≥ 44px, dan ikon `lucide-react`.

## Task 1: Flag keputusan per penampil (butir 1)

Masalah: Owner tidak melihat tombol Setujui/Tolak pada tahap HR untuk jenis persetujuan ganda ketika pemohon adalah satu-satunya HR/Finance aktif (K-079). Server sebenarnya mengizinkan (`LeaveService.actingStage`, `pending()`), tetapi `LeaveDetailPage.canDecide` menebak dari peran.

- Backend: `LeaveRequestResponse` mendapat `boolean canDecide` dan `boolean canRecordOwnerDecision`, yang dihitung untuk pemanggil saat ini **dengan aturan yang sama** dengan `approve/reject` (`requirePending`, `requireNotOwnRequest`, dan `actingStage`, termasuk HR tunggal) serta `recordOwnerDecision` (bukan Owner, status `PERLU_TINJAU_OWNER`, dan bukan pengajuan sendiri). Logikanya dipusatkan agar endpoint aksi dan flag tidak bisa berbeda. Pada endpoint karyawan (`/api/me/...`) kedua flag selalu `false`.
- Frontend: `LeaveDetailPage` memakai `data.canDecide` dan `data.canRecordOwnerDecision`, ditambah `mode === 'manager'`. Perbarui tipe di `src/lib/types.ts`.
- Test backend: Owner melihat `canDecide=true` pada tahap HR jenis ganda ketika pemohon adalah HR tunggal, dan `false` bila ada HR lain. HR melihat `false` untuk pengajuannya sendiri. HR melihat `canRecordOwnerDecision=true` pada `PERLU_TINJAU_OWNER`. Test frontend: tombol tampil mengikuti flag.

## Task 2: Kembali ke link setelah login (butir 2, spec §10)

Tamu yang membuka `/izin/123` (link WA) diarahkan ke `/login`. Setelah login berhasil, ia harus kembali ke `/izin/123`, termasuk query string-nya.

- Guard di `App.tsx` meneruskan lokasi asal (`state.from` atau `?next=`). Pilih yang tetap ada saat halaman dimuat ulang, yaitu `?next=` yang di-encode.
- `LoginPage` mengarahkan ke tujuan itu **hanya bila** nilainya path relatif yang aman (diawali satu `/`, bukan `//`, tanpa skema, dan bukan `/login`). Selain itu dipakai `homePathFor(role)`. Alur ganti password wajib (`/ganti-password`) tetap didahulukan dan tujuan aslinya dibawa ke sana.
- Bila peran yang login tidak berhak atas halaman tujuan, perilaku rute yang ada (`Navigate` ke beranda peran) tetap berlaku.
- Test frontend: guard menambahkan `next`, login kembali ke tujuan, `//evil.com` dan `https://x` ditolak, dan tanpa `next` diarahkan ke beranda peran.

## Task 3: Notifikasi permanen untuk Owner saat HR mencatat keputusannya (butir 3, spec §6 dan §11)

Saat ini Owner hanya mendapat toast SSE. Kalau Owner sedang tidak membuka aplikasi, pemberitahuan itu hilang.

- Backend: `PendingSummary` mendapat `List<RecordedDecisionItem> recordedForOwner` (selalu ada, kosong untuk non-Owner). Isinya keputusan tahap OWNER dengan `method != WEB` yang `recorded_at`-nya dalam 7 hari terakhir, diurutkan dari yang terbaru, maksimal `PENDING_LIST_LIMIT`. Field: `approvalId`, `requestId`, `employeeName`, `leaveTypeName`, `approved`, `method`, `recorderName` (nama karyawan yang terhubung ke akun pencatat bila ada, selain itu username), `recordedAt`, dan `status` pengajuan saat ini. Rentang 7 hari menjadi konstanta bernama, karena ini batas tampilan dan bukan aturan bisnis.
- Frontend `LeaveNotifications`: bagian kedua di popover berjudul "Keputusan Anda yang dicatat HR". Contoh teksnya: "Sari mencatat persetujuan Anda (WhatsApp)" atau "... penolakan ...", lengkap dengan link ke `/izin/:id` dan waktu. Badge = jumlah menunggu + jumlah item tercatat yang **belum dilihat**. Item yang "sudah dilihat" disimpan di `localStorage` per `approvalId` (bungkus try/catch; bila gagal, anggap belum dilihat) dan ditandai saat popover dibuka. Saat event SSE `OWNER_DECISION_RECORDED` datang, query pending di-invalidate.
- Test backend untuk isi dan batasnya (non-Owner kosong, WEB tidak ikut, dan item yang lebih tua dari 7 hari tidak ikut). Test frontend untuk bagian dan badge.

## Task 4: Migrasi lama (butir 4 dan 6)

- **Butir 6 (V15):** baris lama berstatus `DIBATALKAN` yang pernah disetujui atau ditolak sebelum dibatalkan tidak mendapat riwayat dari backfill V13, karena V13 hanya mengambil `DISETUJUI`/`DITOLAK`. Pelajari dulu `LeaveRequest.cancel` dan kolom V5/V12 untuk tahu cara mengenali baris seperti itu, yaitu kolom keputusan yang terisi dan tidak ditimpa saat batal. Buat `V15__riwayat_persetujuan_dibatalkan.sql` yang menyisipkan baris tahap HR (`method WEB`, pencatat = pemberi keputusan) hanya bila belum ada baris untuk pengajuan itu. Migrasi harus idempoten terhadap baris yang sudah punya riwayat. Bila ternyata data lama tidak bisa dikenali dengan aman, laporkan BLOCKED beserta alasannya.
- **Butir 4:** buat test migrasi pada schema terpisah. Isi data pada V11 (status `MENUNGGU`, `DISETUJUI`, `DITOLAK`, dan `DIBATALKAN` yang pernah disetujui), lalu migrasikan sampai versi terbaru. Assert: `MENUNGGU` menjadi `PERLU_TINJAU_HR`, `DISETUJUI`/`DITOLAK` mendapat satu baris HR (V13), `DIBATALKAN`-pernah-disetujui mendapat satu baris HR (V15), dan `DIBATALKAN` yang tidak pernah diputus tidak mendapat baris. Ikuti pola test migrasi yang sudah ada (cari `legacyDecisionsBecomeApprovalRows`).

## Task 5: Nama pencatat di PDF (butir 5)

Kolom Owner di surat untuk keputusan yang dicatat HR harus berbunyi "..., dicatat oleh <nama karyawan pencatat>". Saat ini yang tampil username atau "HR/Finance" walaupun akun pencatat terhubung ke karyawan.

- `LeaveLetterService.linkedEmployeeNames` juga mengumpulkan `recordedBy`, dan renderer memakai nama itu. Username baru dipakai bila akun tidak terhubung ke karyawan. Terapkan di kedua tempat pemanggilnya (surat terautentikasi dan verifikasi publik), tetapi verifikasi publik tetap tidak boleh membocorkan username (lihat perbaikan Task 6 plan lama: pakai label peran bila tidak ada nama).
- Test: PDF/LetterModel berisi nama karyawan pencatat, dan respons publik tanpa nama memakai label, bukan username.

## Task 6: Dokumen

- `docs/keputusan.md`: catat keputusan baru (flag per penampil, `?next=` aman, notifikasi tercatat 7 hari + tanda dilihat lokal, V15). Ikuti format dan penomoran K- yang sudah ada.
- `docs/aturan-bisnis.md` dan dokumen lain hanya bila perilakunya memang berubah.
