# Catatan Keputusan

Format: setiap keputusan berisi tanggal, status (**Disepakati** / **Usulan** / **Dibatalkan**), alasan, dan alternatif yang tidak dipilih. Keputusan yang sudah disepakati tidak dihapus. Kalau berubah, tulis keputusan baru yang menggantikannya.

| ID | Tanggal | Keputusan | Status | Alasan / alternatif |
| --- | --- | --- | --- | --- |
| K-001 | 2026-10-09 | Stack: Spring Boot + React + PostgreSQL, modular monolith | Disepakati | Seluruh tim menguasai Java dan frontend JS, dan tim yang sama merawat sistem. Alternatif NestJS (dipakai di eksperimen lama) tidak dipilih |
| K-002 | 2026-10-09 | Tiga role: Owner, HR/Finance (satu role), Karyawan | Disepakati | Keputusan klien |
| K-003 | 2026-10-09 | Lembur otomatis setelah jam pulang, tanpa pengajuan; dihitung per menit; nominal akhir ditetapkan HR/Finance/Owner | Disepakati | Keputusan klien |
| K-004 | 2026-10-09 | Semua aturan kerja dan gaji diatur Owner lewat modul Pengaturan Aturan, dengan tanggal berlaku | Disepakati | Aturan klien belum pasti dan bisa berubah; menghindari hardcode |
| K-005 | 2026-10-09 | Upah harian diusulkan sistem per hari masuk; HR/Finance/Owner memutuskan dibayar atau tidak | Disepakati | Keputusan klien |
| K-006 | 2026-10-09 | Sistem untuk satu klien, tanpa kolom `tenant_id` | Disepakati | Belum ada rencana SaaS. Kalau kelak dibutuhkan, jalur termurah adalah satu database per klien |
| K-007 | 2026-10-09 | Prinsip integritas data: scan asli tidak diubah, OK hanya setelah tersimpan, periode final dikunci | Disepakati | Kelemahan eksperimen lama: tetap membalas OK walaupun penyimpanan gagal |
| K-008 | 2026-10-09 | Web dan server dikembangkan dengan simulator mesin sampai prototipe mesin siap | Disepakati | Komponen mesin belum pasti |
| K-009 | 2026-10-09 | Eksperimen lama (`backend/` NestJS) diarsipkan dengan git tag di commit `26bed0a` sebelum dihapus | Disepakati | Riwayat repo sederhana: satu branch dan working tree bersih |
| K-010 | 2026-10-09 | Build tool Maven; seluruh sistem bisa dijalankan dengan Docker Compose (database, backend, frontend) | Disepakati | Pilihan tim |
| K-011 | 2026-10-09 | Pengerjaan langsung di branch `main`, tanpa branch fitur | Disepakati | Permintaan tim agar tidak berpindah-pindah branch |
