-- Perbaikan susulan V13: pengajuan lama yang disetujui lalu dibatalkan belum mendapat riwayat tahap HR,
-- karena V13 hanya mengambil status DISETUJUI/DITOLAK.
-- Pembatalan memakai kolom terpisah (cancelled_*) sehingga decided_* tetap berisi keputusan sebelumnya.
-- Pada alur lama satu tahap hanya status MENUNGGU dan DISETUJUI yang bisa dibatalkan, jadi baris DIBATALKAN dengan
-- decided_by dan decided_at terisi pasti pernah DISETUJUI. Pembatalan tanpa keputusan tidak mendapat baris.
-- NOT EXISTS menjaga idempotensi terhadap pengajuan yang sudah punya riwayat.

INSERT INTO leave_approvals (leave_request_id, stage, approved, decided_by, decided_at, method, recorded_by,
                             recorded_at, note)
SELECT r.id, 'HR', TRUE, r.decided_by, r.decided_at, 'WEB', r.decided_by, r.decided_at, r.decision_note
FROM leave_requests r
WHERE r.status = 'DIBATALKAN' AND r.decided_by IS NOT NULL AND r.decided_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM leave_approvals a WHERE a.leave_request_id = r.id);
