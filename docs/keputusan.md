# Catatan Keputusan Proyek

## 1. Tujuan

Dokumen ini menjadi catatan resmi keputusan bisnis, arsitektur, teknologi, keamanan, dan implementasi untuk **Biometric Attendance & HR Management System**.

Keputusan yang telah disepakati tidak boleh dihapus. Jika sebuah keputusan berubah, buat entri baru yang menjelaskan perubahan tersebut dan merujuk ke keputusan sebelumnya.

### Status Keputusan

- **Disepakati:** sudah ditetapkan dan menjadi acuan implementasi.
- **Usulan:** direkomendasikan, tetapi belum menjadi keputusan final.
- **Menunggu Konfirmasi:** membutuhkan persetujuan atau informasi tambahan dari klien.
- **Digantikan:** tidak lagi menjadi keputusan aktif karena telah digantikan oleh keputusan baru.

Tanggal menggunakan format `YYYY-MM-DD`.

---

## 2. Daftar Keputusan

| ID | Tanggal | Keputusan | Status |
|---|---|---|---|
| K-001 | 2026-10-09 | Stack Spring Boot, React, PostgreSQL, modular monolith | Disepakati |
| K-002 | 2026-10-09 | Tiga role awal: Owner, HR/Finance, Karyawan | Disepakati |
| K-003 | 2026-10-09 | Perhitungan lembur berdasarkan waktu kerja setelah jam pulang normal, tanpa pengajuan awal sebagai prasyarat | Disepakati, diperjelas K-026 |
| K-004 | 2026-10-09 | Aturan kerja dan gaji dapat diatur Owner dengan tanggal berlaku | Disepakati |
| K-005 | 2026-10-09 | Sistem mengusulkan upah harian; pihak berwenang menentukan kelayakannya | Disepakati |
| K-006 | 2026-10-09 | Sistem digunakan untuk satu perusahaan, tanpa `tenant_id` pada versi awal | Disepakati |
| K-007 | 2026-10-09 | Integritas scan dan penyimpanan hasil harus dijaga | Disepakati, diperjelas K-019, K-020 |
| K-008 | 2026-10-09 | Pengembangan menggunakan simulator sampai prototipe mesin siap | Disepakati |
| K-009 | 2026-10-09 | Eksperimen backend NestJS lama diarsipkan sebelum dihapus | Disepakati |
| K-010 | 2026-10-09 | Maven sebagai build tool backend; Docker Compose untuk lingkungan lokal | Disepakati |
| K-011 | 2026-10-09 | Tim bekerja langsung pada branch `main` | Disepakati |
| K-012 | 2026-10-09 | Login menggunakan username yang tidak membedakan huruf besar/kecil | Disepakati |
| K-013 | 2026-10-09 | UI menggunakan Tailwind CSS dan shadcn/ui | Disepakati |
| K-014 | 2026-10-09 | Autentikasi menggunakan session cookie, Spring Session JDBC, CSRF, dan sesi 8 jam | Disepakati |
| K-015 | 2026-10-09 | Akun Owner awal dibuat melalui konfigurasi environment; akun baru wajib mengganti password | Disepakati |
| K-016 | 2026-10-09 | Pengelolaan akun dibatasi menurut role dan minimal satu Owner aktif dipertahankan | Disepakati |
| K-017 | 2026-10-10 | Design system mengikuti brand Takka Steel | Disepakati |
| K-018 | 2026-10-11 | Backend menjadi gateway antara perangkat, database, dan frontend | Disepakati |
| K-019 | 2026-10-11 | Raw scan tidak ditimpa oleh koreksi absensi | Disepakati |
| K-020 | 2026-10-11 | ACK sukses hanya diberikan setelah data scan tersimpan secara persisten | Disepakati |
| K-021 | 2026-10-11 | Offline queue, retry, dan RTC bergantung pada kemampuan perangkat | Menunggu Konfirmasi |
| K-022 | 2026-10-11 | Nilai uang menggunakan tipe data desimal dengan presisi yang memadai | Disepakati |
| K-023 | 2026-10-11 | Backend wajib memvalidasi hak akses setiap tindakan yang dilindungi | Disepakati |
| K-024 | 2026-10-11 | Data payroll, kasbon, akun, dan biometrik memerlukan pembatasan akses | Disepakati |
| K-025 | 2026-10-11 | Custom role dan pembatasan akses per cabang belum diputuskan | Menunggu Konfirmasi |
| K-026 | 2026-10-11 | Kandidat lembur hari kerja normal dimulai setelah pukul 17.00 | Disepakati |
| K-027 | 2026-10-11 | Lembur dihitung dari scan dan dapat diverifikasi atau dikoreksi secara terkontrol | Disepakati |
| K-028 | 2026-10-11 | Aturan kerja dapat berlaku global maupun khusus karyawan | Disepakati |
| K-029 | 2026-10-11 | Koreksi absensi disimpan terpisah dan diaudit | Disepakati |
| K-030 | 2026-10-11 | Detail kalender kerja, istirahat, pembulatan, dan scan tidak lengkap harus divalidasi | Menunggu Konfirmasi |
| K-031 | 2026-10-11 | Payroll mingguan untuk supir/kenek dan bulanan untuk staf lainnya | Disepakati |
| K-032 | 2026-10-11 | Gaji pokok bulanan dipisahkan dari komponen upah harian | Disepakati |
| K-033 | 2026-10-11 | Tarif memiliki tanggal efektif agar riwayat dapat dipertahankan | Disepakati |
| K-034 | 2026-10-11 | Payroll final dapat dibuka kembali melalui prosedur terkontrol | Disepakati |
| K-035 | 2026-10-11 | Payroll final menyimpan snapshot aturan, tarif, input, dan hasil | Disepakati |
| K-036 | 2026-10-11 | Reopen dan finalisasi ulang mempertahankan riwayat versi sebelumnya | Disepakati |
| K-037 | 2026-10-11 | Kasbon menggunakan ledger transaksi, bukan saldo yang diedit langsung | Disepakati |
| K-038 | 2026-10-11 | Bonus ditetapkan Owner; tidak ada rumus bonus otomatis pada versi pertama | Disepakati |
| K-039 | 2026-10-11 | BPJS, PPh 21, dan potongan lain diterapkan setelah kebijakan dikonfirmasi | Disepakati |
| K-040 | 2026-10-11 | Hasil payroll harus dapat ditelusuri kembali ke data sumber dan aturan | Disepakati |
| K-041 | 2026-10-11 | Kewenangan dan persetujuan pembukaan kembali payroll perlu diperinci | Menunggu Konfirmasi |
| K-042 | 2026-10-11 | Empat laporan payroll aktual menjadi baseline pengujian | Disepakati |
| K-043 | 2026-10-11 | Pengujian regresi wajib mencakup tarif historis dan payroll yang dibuka kembali | Disepakati |
| K-044 | 2026-10-11 | Sesi login langsung dicabut setelah password di-reset, akun dinonaktifkan, atau role diubah | Disepakati, memperjelas K-014/K-015/K-016 |
| K-045 | 2026-10-11 | Dokumentasi API memakai springdoc/OpenAPI, mati secara default dan hanya untuk Owner/HR-Finance | Disepakati |
| K-046 | 2026-10-12 | Prioritas aturan per kolom: Karyawan > Kategori > Global | Disepakati, memperjelas K-028 |
| K-047 | 2026-10-12 | Tidak ada nilai aturan bawaan; perhitungan ditolak sampai aturan wajib diatur | Disepakati |
| K-048 | 2026-10-12 | Jam istirahat opsional; bila kosong tidak ada pengurangan dan rekap diberi penanda | Disepakati. Nilai istirahat dijawab client di K-110 |
| K-049 | 2026-10-12 | Aturan kerja dan tarif hanya diubah Owner; kalender libur dikelola Owner dan HR/Finance | Disepakati |
| K-050 | 2026-10-12 | Versi aturan tidak boleh berlaku surut dan terkunci setelah tanggal berlakunya tiba | Disepakati |
| K-051 | 2026-10-12 | Kalender libur berlaku untuk semua cabang | Disepakati, sementara |
| K-052 | 2026-10-12 | Durasi absensi disimpan persis sampai detik tanpa pembulatan | Disepakati, sementara menunggu Q-008 |
| K-053 | 2026-10-12 | Kehadiran di hari libur/bukan hari kerja hanya dihitung total hadirnya dan ditandai perlu tinjauan | Disepakati, sementara menunggu Q-005 |
| K-054 | 2026-10-12 | Penerimaan scan memakai inti yang sama untuk semua mesin, ditambah adapter per protokol | Disepakati, memperjelas K-008/K-018 |
| K-055 | 2026-10-12 | Mesin Solution X401 (mode ADMS) dipakai sebagai mesin uji sementara, dengan batasan tercatat | Disepakati, sementara sampai alat rakitan siap; menunggu Q-029 |
| K-056 | 2026-10-12 | Satu PIN mesin per karyawan, berlaku di semua mesin | Disepakati, menjawab Q-031 |
| K-057 | 2026-10-12 | Scan lebih lama dari "Terima scan sejak" mesin di-ACK tetapi tidak disimpan | Disepakati, menjawab Q-032 |
| K-058 | 2026-10-12 | Rekap harian dihitung saat dibuka, tanpa tabel rekap | Disepakati |
| K-059 | 2026-10-12 | Koreksi absensi oleh Owner dan HR/Finance, alasan wajib, tanpa persetujuan kedua | Disepakati |
| K-060 | 2026-10-12 | Endpoint mesin juga dibuka di port 8888 | Disepakati, sementara (keypad X401) |
| K-061 | 2026-10-12 | ACK "OK" hanya setelah commit; duplikat diabaikan unique constraint | Disepakati |
| K-062 | 2026-10-12 | Baris scan rusak disimpan di rejected_scan_lines dan kiriman tetap di-ACK | Disepakati |
| K-063 | 2026-10-12 | Mesin hanya diminta mengirim data absensi, bukan data user/sidik jari | Disepakati |
| K-064 | 2026-10-12 | Mesin offline bila tidak ada kontak selama DEVICE_OFFLINE_AFTER (bawaan 5 menit) | Disepakati |
| K-065 | 2026-10-12 | Penanda scan: PIN tak dikenal, waktu di masa depan, cabang berbeda | Disepakati, sementara menunggu Q-022. Arti `CABANG_BERBEDA` diganti oleh K-106 (sekarang hanya "scan di cabang tambahan yang diizinkan") |
| K-066 | 2026-10-12 | Satu koreksi aktif per karyawan per tanggal; koreksi lama tetap tersimpan | Disepakati |
| K-067 | 2026-10-12 | Mesin tak terdaftar ditolak tanpa OK; nomor serinya ditampilkan untuk didaftarkan | Disepakati |
| K-068 | 2026-10-12 | Scan PIN tak dikenal dihubungkan ke karyawan saat PIN-nya diisi | Disepakati |
| K-069 | 2026-10-12 | Kolom jam/tanggal tanpa zona disimpan apa adanya (hapus hibernate.jdbc.time_zone) | Disepakati, perbaikan bug |
| K-070 | 2026-10-13 | Jenis izin/cuti adalah master data yang diatur Owner (wajib surat, dibayar/belum ditentukan, aktif) | Disepakati |
| K-071 | 2026-10-13 | Pengajuan izin berupa rentang tanggal hari penuh; izin per jam ditunda | Disepakati |
| K-072 | 2026-10-13 | Tanpa kuota cuti dulu; pemakaian per jenis per tahun ditampilkan saat menyetujui | Disepakati, menunggu Q-013 |
| K-073 | 2026-10-13 | Karyawan mengajukan sendiri; Owner/HR bisa mencatat atas nama karyawan; Owner/HR yang menyetujui | Disepakati. Penyetuju untuk jenis yang butuh persetujuan ganda diganti K-109 |
| K-074 | 2026-10-13 | Status Menunggu → Disetujui/Ditolak, dan Dibatalkan; tolak dan batal (oleh Owner/HR) wajib alasan | Disepakati |
| K-075 | 2026-10-13 | Pengajuan aktif satu karyawan tidak boleh tumpang tindih (exclusion constraint) | Disepakati |
| K-076 | 2026-10-13 | Izin disetujui → status rekap IZIN di hari kerja tanpa scan; ada scan → penanda HADIR_SAAT_IZIN; menunggu tidak berpengaruh | Disepakati |
| K-077 | 2026-10-13 | "Wajib surat" diperiksa saat persetujuan, bukan saat pengajuan | Disepakati |
| K-078 | 2026-10-13 | Lampiran PDF/JPG/PNG ≤ 5 MB, maks. 5 per pengajuan, dicek dari isi file, disimpan di volume disk | Disepakati |
| K-079 | 2026-10-13 | Owner/HR tidak bisa memutus pengajuan miliknya sendiri | Disepakati |
| K-080 | 2026-10-13 | Pengajuan boleh untuk tanggal lampau; rentang maks. 366 hari (batas teknis) | Disepakati, batas lampau menunggu Q-033 |
| K-081 | 2026-10-13 | Notifikasi langsung di dalam aplikasi untuk Owner/HR (pop-up SSE + lonceng); tanpa email/WA | Disepakati |
| K-082 | 2026-10-13 | Surat pengajuan siap cetak / simpan PDF lewat fitur cetak browser | Disepakati. Pembuatan PDF di server menggantikan sebagian (K-114); halaman cetak browser dihapus |
| K-083 | 2026-10-14 | Semua karyawan boleh melihat absensi semua karyawan aktif dengan kolom terbatas, tanpa data sensitif | Disepakati. Diganti K-127 |
| K-084 | 2026-10-14 | Status rekan kerja disederhanakan; izin tampil tanpa jenis, koreksi tidak terlihat | Disepakati |
| K-085 | 2026-10-14 | Tema kaca (glassmorphism) penuh, terang/gelap dengan tombol tema, responsif HP/tablet/desktop | Disepakati |
| K-086 | 2026-10-16 | Backend membuka port kedua untuk mesin (`DEVICE_EXTRA_PORT`) saat dijalankan di luar Docker lewat `start-dev.bat` | Disepakati |
| K-087 | 2026-10-17 | Alat rakitan memakai protokol sendiri `/device/v1` sebagai adapter kedua (K-054); mesin yang menghubungi server, server tidak pernah menghubungi mesin. Menjawab Q-030 | Disepakati |
| K-088 | 2026-10-17 | Kunci rahasia acak per alat rakitan, hanya tampil sekali, disimpan sebagai SHA-256; "Buat kode baru" langsung mencabut kunci lama | Disepakati (penggantian dua tahap menyusul, Rencana 6) |
| K-091 | 2026-10-18 | Pendaftaran sidik jari butuh centang persetujuan karyawan, sekali per karyawan, tercatat di audit; formulir kertas diurus klien | Disepakati |
| K-092 | 2026-10-17 | Kolom aturan "Jeda minimum antar scan (menit)": scan berdekatan dihitung satu kejadian; kosong = tanpa penggabungan | Disepakati |
| K-094 | 2026-10-18 | Maksimal 2 jari per karyawan; satu `ENROLL` aktif per mesin dan per karyawan + jari; batas pengambilan 30 detik, batas di mesin 60 detik + cadangan 15 detik | Disepakati |
| K-099 | 2026-10-17 | Rincian kontrak Rencana 2: endpoint terpisah per jenis mesin, jenis mesin tetap, hanya Owner yang menerbitkan kunci, 403 untuk alat rakitan nonaktif, konfirmasi "terhubung" per kunci baru | Disepakati |
| K-100 | 2026-10-18 | Rincian Rencana 3: template belum disimpan, perintah dikirim ulang lewat heartbeat, daftar `cancel`, laporan setelah batal diabaikan, unique index untuk satu `ENROLL` aktif | Disepakati |
| K-101 | 2026-10-18 | Batas waktu tanpa hasil dari server memakai kode `TIDAK_ADA_HASIL`, terpisah dari `WAKTU_HABIS` mesin; batal yang bentrok dicoba ulang sekali | Disepakati |
| K-102 | 2026-10-18 | Rincian Rencana 5: perintah `DELETE_TEMPLATE`/`DELETE_EMPLOYEE` ke semua alat rakitan yang pernah tersambung, hapus saat nonaktif atau setelah tanggal keluar lewat, PIN terkunci sampai semua mesin aktif selesai menghapus, "Coba lagi", peringatan kapasitas sensor (merinci K-090 dan K-093) | Disepakati |
| K-103 | 2026-10-20 | Cabang absen diatur per karyawan: sidik jari hanya didaftarkan di mesin cabang yang diizinkan; scan dari cabang lain disimpan tetapi tidak masuk rekap sampai ditinjau. Menjawab sebagian Q-022, mengganti sebagian K-065 | Disepakati, diterapkan 2026-10-22 |
| K-104 | 2026-10-20 | Rincian Rencana 6: paket pengaturan ringan berversi, `APPLY_NETWORK` (Wi-Fi + alamat server, password terenkripsi), `ROTATE_KEY` dua tahap, konfirmasi ulang password untuk tindakan berisiko (merinci K-089, K-096, K-098) | Keputusan Claude, menunggu tinjauan pengguna |
| K-105 | 2026-10-22 | Ekspor Excel/PDF mengikuti format laporan AS-IS client (dokumen-as-is.md); laporan absensi boleh sebelum Fase 5 | Disepakati (permintaan client) |
| K-106 | 2026-10-22 | Rincian cabang absen per karyawan (K-103): cabang tambahan, penanda `CABANG_TIDAK_DIIZINKAN`, peninjauan scan tertahan dengan alasan wajib, pencabutan cabang menghapus sidik jari. Mengganti arti `CABANG_BERBEDA` di K-065 | Keputusan Claude, disetujui pengguna 2026-10-22 |
| K-107 | 2026-10-23 | Sensor sidik jari alat rakitan adalah R307, menggantikan R503 | Disepakati (meeting client) |
| K-108 | 2026-10-23 | Setiap karyawan memiliki akun individual (Opsi A dokumen requirement v0.4); tidak ada akun/akses bersama | Disepakati (meeting client) |
| K-109 | 2026-10-23 | Persetujuan ganda HR/Finance lalu Owner untuk jenis izin yang ditandai Owner; jenis lain tetap satu persetujuan (K-073) | Disepakati (meeting client); rincian teknis di K-113 sampai K-118 (diimplementasikan 2026-10-24) |
| K-110 | 2026-10-23 | Jam kerja 08.00–17.00 dengan istirahat 12.00–13.00, sehingga 8 jam efektif; masuk 09.00 terhitung 7 jam. Nilai diisi lewat Pengaturan Aturan. Menjawab sebagian Q-002 | Disepakati (meeting client) |
| K-111 | 2026-10-23 | Hanya Owner yang mengubah penggajian (tarif, menjalankan, memfinalkan, membuka kembali); HR/Finance hanya melihat dan mereview | Disepakati (meeting client) |
| K-112 | 2026-10-23 | Alat rakitan dipasang di satu cabang dulu; dua cabang lain belum memakai sistem | Disepakati (meeting client) |
| K-113 | 2026-10-24 | Status pengajuan izin bertahap: Draft, Perlu ditinjau HR, Perlu ditinjau Owner, Disetujui, Ditolak, Dibatalkan; jenis dan templat dibekukan saat kirim | Disepakati pengguna 2026-10-24 ([spec](superpowers/specs/2026-10-24-persetujuan-ganda-surat-izin-design.md)) |
| K-114 | 2026-10-24 | Surat izin berupa PDF yang dibuat server (OpenPDF + ZXing), menggantikan sebagian K-082 | Disepakati pengguna 2026-10-24 ([spec](superpowers/specs/2026-10-24-persetujuan-ganda-surat-izin-design.md)) |
| K-115 | 2026-10-24 | Spesimen tanda tangan per akun; disalin (dibekukan) ke rekaman tahap saat disetujui di web | Disepakati pengguna 2026-10-24 ([spec](superpowers/specs/2026-10-24-persetujuan-ganda-surat-izin-design.md)) |
| K-116 | 2026-10-24 | Verifikasi surat publik lewat token acak 128-bit di QR, dengan data minimal dan batas laju per IP | Disepakati pengguna 2026-10-24 ([spec](superpowers/specs/2026-10-24-persetujuan-ganda-surat-izin-design.md)) |
| K-117 | 2026-10-24 | HR boleh mencatat keputusan Owner yang diberikan di luar web (WhatsApp, lisan, tertulis) dengan waktu, catatan, dan bukti | Disepakati pengguna 2026-10-24 ([spec](superpowers/specs/2026-10-24-persetujuan-ganda-surat-izin-design.md)) |
| K-118 | 2026-10-24 | Area karyawan mobile-first: Beranda `/beranda`, navigasi bawah Beranda/Izin/Rekan/Profil, alur pengajuan 4 langkah | Disepakati pengguna 2026-10-24 ([spec](superpowers/specs/2026-10-24-persetujuan-ganda-surat-izin-design.md)) |
| K-125 | 2026-10-25 | Owner boleh langsung menyetujui/menolak pengajuan jenis persetujuan ganda tanpa menunggu tahap HR; tercatat satu baris tahap Owner | Disepakati pengguna 2026-10-25 (review gap v0.6 baris 2); menggantikan bagian K-113 tentang HR tunggal |
| K-126 | 2026-10-25 | Karyawan bisa mengajukan ulang pengajuan yang ditolak sebagai draft baru (V16), sekali per pengajuan, tanpa menyalin lampiran | Disepakati pengguna 2026-10-25 (review gap v0.6 baris 3) |
| K-127 | 2026-10-25 | Karyawan hanya melihat absensinya sendiri (Absensi Saya `/absensi-saya`); daftar rekan kerja dihapus | Disepakati pengguna 2026-10-25 (review gap v0.6 baris 1); menggantikan K-083, K-084 (sebagian), dan menu Rekan di K-118 |
| K-128 | 2026-10-25 | ~~Tambah/ubah data karyawan dan cabang absen hanya Owner~~ Dibatalkan sebagian oleh K-135; HR/Finance tetap membaca dan mengurus sidik jari | Disepakati pengguna 2026-10-25 (review gap v0.6 baris 6); mengubah hak akses K-103 |
| K-129 | 2026-10-26 | Profil lengkap karyawan: email, jenis kelamin, tanggal lahir, alamat, catatan (V17, semuanya opsional) | Disepakati pengguna 2026-10-26 (data karyawan dari client) |
| K-130 | 2026-10-26 | ~~Impor karyawan dari template Excel client~~ **Dibatalkan 2026-10-26, diganti K-133.** PIN mesin = ID karyawan tetap berlaku | Disepakati pengguna 2026-10-26; dibatalkan pengguna di hari yang sama |
| K-131 | 2026-10-26 | Username akun `<nama>@takka.id`, password sementara `<nama>-<ID>-takka` (semula `<nama>-<ID>`), wajib diganti saat login pertama | Disepakati pengguna 2026-10-26 |
| K-132 | 2026-10-26 | Label role HR_FINANCE ditampilkan sebagai "Finance & Accounting" (kode role tetap) | Disepakati pengguna 2026-10-26 |
| K-133 | 2026-10-26 | Tambah karyawan sekaligus akun login, lalu pesan kredensial siap kirim (Salin / Kirim WA); reset password dan buat akun memakai pesan yang sama | Disepakati pengguna 2026-10-26; menggantikan K-130 |
| K-134 | 2026-10-26 | Tanggal masuk otomatis hari ini menurut zona cabang penempatan; form tambah tanpa tanggal masuk/keluar | Disepakati pengguna 2026-10-26 |
| K-135 | 2026-10-26 | Finance & Accounting kembali boleh menambah/mengubah data karyawan dan cabang absen; Aturan Kerja tetap hanya Owner | Disepakati pengguna 2026-10-26; membatalkan sebagian K-128 |
| K-136 | 2026-10-26 | Form karyawan dibagi per bagian: PIN mesin dan akun tampil sebagai ringkasan, data pribadi dilipat, pilihan sedikit berupa tombol berjejer | Disetujui pengguna 2026-10-26 ("langsung kerjakan"); hanya tampilan, kontrak API tetap |
| K-137 | 2026-10-27 | Akun login ikut nonaktif saat karyawannya dinonaktifkan dan ikut aktif saat karyawan diaktifkan kembali | Disepakati pengguna 2026-10-27 (FR-EMP-005, AC-LV-02); alternatif "pengaktifan manual" tidak dipilih |
| K-138 | 2026-10-27 | Batas percobaan login: 5 kali gagal berturut-turut per username → dikunci 15 menit (429), disimpan di memori | Disepakati pengguna 2026-10-27 (NFR-SEC-004); nilai bisa diubah lewat properti |

---

## 3. Detail Keputusan Teknis

### K-001 — Stack Teknologi

**Keputusan:** sistem menggunakan Java LTS dengan Spring Boot untuk backend, React dengan Vite dan TypeScript untuk frontend, serta PostgreSQL sebagai database. Arsitektur aplikasi menggunakan modular monolith.

**Alasan:**
- Stack sesuai kemampuan tim.
- Backend dan frontend dapat dikembangkan secara terstruktur.
- Modular monolith menjaga pemisahan modul tanpa kompleksitas microservices sejak awal.
- PostgreSQL sesuai kebutuhan transaksi absensi, konfigurasi, dan payroll.

**Alternatif yang tidak dipilih:** NestJS sebagai backend utama pada implementasi baru.

### K-002 — Role Awal

**Keputusan:** tiga role awal adalah Owner, HR/Finance, dan Karyawan.

Hak akses harus diperiksa pada backend. Role tidak boleh hanya digunakan untuk menyembunyikan menu pada frontend.

Custom role dan pembatasan akses per cabang belum menjadi keputusan final.

### K-004 — Konfigurasi Aturan

**Keputusan:** Owner dapat mengatur aturan kerja dan komponen gaji dengan tanggal mulai berlaku.

Aturan harus mendukung konfigurasi global serta pengecualian khusus karyawan. Prioritas aturan harus didefinisikan secara konsisten.

### K-006 — Satu Perusahaan

**Keputusan:** versi awal digunakan untuk satu perusahaan dan tidak memerlukan `tenant_id`.

Jika kebutuhan berubah menjadi produk multi-perusahaan, arsitektur perlu dievaluasi kembali sebelum perubahan dilakukan.

### K-008 — Simulator Perangkat

**Keputusan:** backend dan frontend dikembangkan menggunakan simulator sampai prototipe mesin tersedia.

Simulator harus mengikuti kontrak integrasi yang dirancang dan tidak boleh dianggap sebagai bukti bahwa seluruh kemampuan hardware telah tersedia.

**Pembaruan 2026-10-12 (K-055):** selain simulator, mesin komersial Solution X401 dipakai sebagai mesin uji sementara. Simulator tetap wajib untuk test otomatis (offline, kirim ulang, scan ganda), karena mesin sungguhan tidak bisa dijalankan di test.

### K-009 — Pengarsipan Backend Lama

**Keputusan:** eksperimen backend NestJS lama diarsipkan menggunakan Git tag pada commit `26bed0a` sebelum folder lama dihapus.

Implementasi baru mengikuti stack yang telah disepakati.

### K-010 — Build dan Lingkungan Lokal

**Keputusan:** backend menggunakan Maven dan lingkungan lokal disediakan melalui Docker Compose.

Komponen yang direncanakan mencakup backend, frontend, dan PostgreSQL. Konfigurasi produksi, reverse proxy, dan TLS harus ditetapkan pada tahap deployment.

### K-011 — Strategi Branch

**Keputusan tim:** pengerjaan dilakukan langsung pada branch `main`, tanpa branch fitur.

Konsekuensi: anggota tim wajib menjaga working tree, menguji perubahan, melakukan commit dengan jelas, dan berkoordinasi sebelum perubahan besar. Jika risiko konflik meningkat, strategi branch perlu dievaluasi ulang melalui keputusan baru.

### K-012 — Login

**Keputusan:** login menggunakan username, bukan email atau nomor telepon. Username tidak membedakan huruf besar/kecil.

Normalisasi username harus diterapkan konsisten pada pembuatan akun, login, dan validasi keunikan.

### K-013 — UI Framework

**Keputusan:** frontend menggunakan Tailwind CSS dan shadcn/ui.

Komponen antarmuka harus mengikuti `design-system.md`, menjaga keterbacaan tabel operasional, dan mendukung layout responsif.

### K-014 — Sesi Autentikasi

**Keputusan:** autentikasi menggunakan session cookie dengan Spring Session JDBC, proteksi CSRF, dan durasi sesi 8 jam.

JWT tidak digunakan sebagai mekanisme autentikasi utama pada baseline ini karena pengelolaan pencabutan sesi lebih sederhana dengan pendekatan session.

### K-015 — Akun Owner Awal

**Keputusan:** akun Owner pertama dibuat melalui konfigurasi environment, yaitu `OWNER_USERNAME` dan `OWNER_INITIAL_PASSWORD`.

Password tidak boleh ditanam sebagai kredensial tetap di source code. Akun baru atau akun yang password-nya di-reset wajib mengganti password sebelum dapat menggunakan API lain yang dilindungi, sesuai mekanisme autentikasi yang dirancang.

Secret tidak boleh dimasukkan ke repository.

### K-016 — Pengelolaan Akun

**Keputusan:**
- HR/Finance dapat mengelola akun Karyawan.
- Pengelolaan akun Owner dan HR/Finance hanya untuk Owner.
- Audit log dapat diakses Owner.
- Sistem harus mempertahankan minimal satu Owner aktif.

Backend wajib menolak tindakan yang melanggar ketentuan ini.

### K-017 — Design System

**Keputusan:** tampilan mengikuti brand Takka Steel dengan palet biru steel dan gold, font Poppins serta Open Sans, gaya Corporate Clean, tabel Enterprise Data-Dense, dan mode gelap Blueprint.

Rincian visual terdapat di [`design-system.md`](./design-system.md).

### K-044 — Pencabutan Sesi

**Keputusan (B-03 di `technical-task-breakdown.md`):** semua sesi login milik sebuah akun langsung tidak berlaku ketika password akun itu di-reset, akun dinonaktifkan, atau role-nya diubah. Ketika pengguna mengganti password sendiri, sesi yang sedang dipakai tetap berlaku dan sesi di perangkat lain dicabut.

**Alasan:** tanpa pencabutan, sesi lama tetap bisa dipakai sampai 8 jam dengan hak akses lama, bertentangan dengan K-015 dan K-016.

**Implementasi:** baris sesi Spring Session JDBC dihapus berdasarkan username di transaksi yang sama dengan perubahan akun. Pengguna yang terdampak harus login ulang.

### K-045 — Dokumentasi API

**Keputusan (B-04):** dokumentasi API memakai springdoc-openapi. Dokumentasi mati secara default (`OPENAPI_ENABLED=false`) dan hanya dinyalakan untuk development. Saat menyala, `/v3/api-docs` dan Swagger UI tetap hanya bisa dibuka oleh Owner dan HR/Finance yang sudah login.

**Alasan:** NFR-MAINT-003 membutuhkan kontrak API yang terdokumentasi, tetapi daftar endpoint tidak perlu terbuka di production.

### K-046 — Prioritas Aturan per Kolom

**Keputusan (2026-10-12, dipilih pengguna):** setiap parameter aturan diambil dari level paling spesifik yang mengisinya: Karyawan, lalu Kategori, lalu Global. Parameter yang dikosongkan mewarisi level di atasnya. Di dalam satu level, versi dengan tanggal berlaku terakhir yang tidak melewati tanggal kejadian menggantikan versi lama level itu sepenuhnya.

**Alasan:** pengecualian untuk satu karyawan cukup mengisi parameter yang berbeda, tanpa menyalin seluruh aturan. Sistem menampilkan asal setiap nilai (level, versi, tanggal berlaku) sehingga perhitungan dapat ditelusuri (K-040).

### K-047 — Tanpa Nilai Aturan Bawaan

**Keputusan (2026-10-12, dipilih pengguna):** migration tidak mengisi nilai aturan. Owner mengisi aturan Global lewat aplikasi. Selama jam masuk, jam pulang, toleransi keterlambatan, batas mulai lembur, atau hari kerja belum diatur untuk suatu tanggal, perhitungan ditolak dengan pesan "Aturan belum diatur: …".

**Alasan:** nilai "acuan awal" di `aturan-bisnis.md` §3.1 belum final; menanamnya di migration sama dengan hardcode aturan bisnis.

### K-048 — Istirahat Opsional

**Keputusan (2026-10-12, dipilih pengguna):** jam mulai dan selesai istirahat boleh kosong (keduanya diisi atau keduanya kosong). Bila kosong, jam kerja normal tidak dikurangi istirahat dan rekap diberi penanda `ISTIRAHAT_BELUM_DIATUR`. Bila diisi, irisan jam kerja normal dengan jam istirahat dikurangkan.

**Status:** sementara sampai Q-002 dijawab.

### K-049 — Hak Akses Aturan dan Kalender Libur

**Keputusan (2026-10-12, dipilih pengguna):** aturan kerja dan tarif hanya dapat ditambah, diubah, atau dihapus oleh Owner (sesuai K-004); HR/Finance dapat melihat. Kalender libur dikelola Owner dan HR/Finance. Karyawan tidak memiliki akses. Diterapkan di `SecurityConfig` dan diuji `AccessControlTests`.

### K-050 — Versi Aturan Tidak Berlaku Surut

**Keputusan (2026-10-12):** versi aturan baru hanya boleh berlaku mulai hari ini atau sesudahnya. Versi yang tanggal berlakunya sudah tiba terkunci: tidak bisa diubah atau dihapus. Perubahan dilakukan dengan membuat versi baru. Versi yang belum berlaku boleh diubah atau dihapus, dan setiap perubahan tercatat di audit log. "Hari ini" mengikuti zona waktu perusahaan (`COMPANY_TIMEZONE`, bawaan `Asia/Jakarta`).

**Alasan:** menjamin perubahan aturan tidak mengubah hasil tanggal sebelumnya (K-033). Prosedur koreksi aturan berlaku surut dicatat sebagai Q-026.

### K-051 — Kalender Libur Semua Cabang

**Keputusan (2026-10-12):** satu kalender libur berlaku untuk semua cabang. Libur khusus cabang dicatat sebagai Q-027.

### K-052 — Durasi Tanpa Pembulatan

**Keputusan (2026-10-12):** keterlambatan, jam kerja normal, pulang lebih awal, kandidat lembur, dan total kehadiran dihitung persis sampai detik. Tidak ada pembulatan sampai Q-008 dijawab.

### K-053 — Kehadiran di Hari Libur

**Keputusan (2026-10-12):** pada tanggal di kalender libur atau di luar hari kerja, sistem hanya menghitung total kehadiran (jam pulang − jam masuk), tidak memisahkan jam normal, keterlambatan, atau kandidat lembur, dan memberi penanda `PERLU_TINJAUAN_HARI_LIBUR`.

**Alasan:** perlakuan kerja di hari libur belum diketahui (Q-005); `aturan-bisnis.md` §3.4 melarang memakai satu rumus lembur untuk semua kasus.

---

## 4. Detail Keputusan Integrasi Perangkat

### K-018 — Backend sebagai Gateway

**Keputusan:** komunikasi perangkat dilakukan melalui backend, bukan langsung dari frontend atau database.

Backend bertanggung jawab untuk validasi, autentikasi perangkat sesuai protokol, penyimpanan kejadian scan, pencegahan duplikasi, dan pemrosesan data absensi.

### K-019 — Raw Scan Immutable

**Keputusan:** scan asli tidak boleh ditimpa oleh koreksi absensi.

Koreksi harus disimpan sebagai perubahan terpisah yang mencatat nilai sebelum dan sesudah, pelaku, waktu, serta alasan koreksi.

### K-020 — ACK Setelah Penyimpanan

**Keputusan:** backend hanya boleh memberikan respons sukses setelah kejadian scan tersimpan secara persisten.

Jika penyimpanan gagal, backend tidak boleh memberikan ACK sukses. Mekanisme retry harus dirancang agar pengiriman ulang tidak membuat scan ganda.

### K-021 — Offline dan Sinkronisasi

**Status:** Menunggu Konfirmasi.

Penyimpanan offline, retry otomatis, RTC, dan sinkronisasi setelah koneksi pulih bergantung pada spesifikasi perangkat serta firmware. Implementasi tidak boleh mengasumsikan kemampuan tersebut sebelum divalidasi.

### K-054 — Inti Penerimaan Scan dan Adapter per Protokol

**Keputusan (2026-10-12, disetujui pengguna):** penerimaan scan dibagi dua lapis.

- **Inti penerimaan scan** (sama untuk semua mesin): validasi mesin terdaftar dan aktif, simpan scan asli (K-019), cegah duplikat, balas sukses hanya setelah tersimpan (K-020), lalu teruskan ke rekap harian dan dashboard.
- **Adapter per protokol**: penerjemah kecil dari format mesin ke format inti. Adapter pertama adalah **ZK-Push** (protokol ADMS mesin Solution/ZKTeco) untuk X401. Alat rakitan memakai adapter sendiri, atau meniru protokol ZK-Push (Q-030).

**Alasan:** rekap, dashboard, koreksi, dan halaman web tidak bergantung pada jenis mesin. Mengganti X401 dengan alat rakitan cukup menambah adapter, bukan mengubah web.

**Alternatif yang dipertimbangkan:** membuat endpoint khusus X401 saja (ditolak: harus dibongkar saat alat rakitan siap), atau langsung memaksa alat rakitan memakai protokol ZK-Push (ditunda ke Q-030 karena protokol itu lemah dari sisi keamanan, lihat K-055).

### K-055 — Solution X401 sebagai Mesin Uji Sementara

**Keputusan (2026-10-12, disetujui pengguna):** mesin Solution X401 milik dosen dipakai untuk menguji alur scan sebelum alat rakitan selesai, lewat mode ADMS/Cloud Server (mesin mengirim ke server). Langkah uji ada di [`panduan-mesin-x401.md`](./panduan-mesin-x401.md).

**Batasan yang diterima selama uji** (tidak berlaku untuk alat rakitan):

| Aturan | Penanganan sementara pada X401 |
|---|---|
| ID unik per scan | Kunci duplikat = nomor seri mesin + PIN + waktu scan |
| Kredensial unik per mesin | Hanya nomor seri yang terdaftar dan aktif yang diterima; hanya di jaringan lokal |
| TLS | Uji hanya di jaringan lokal, tidak diekspos ke internet |
| Waktu scan tanpa zona waktu | Ditafsirkan dengan zona waktu cabang mesin |

Mesin mengidentifikasi karyawan dengan PIN (User ID di mesin). Karyawan perlu dipetakan ke PIN per mesin; rinciannya diputuskan di rencana Fase 3 (Q-031).

**Syarat:** kemampuan X401 (ADMS, format data, kirim ulang) baru dianggap ada setelah terbukti dari log tangkapan (`tools/x401-capture`), bukan dari brosur (Q-029).

### K-023 — Otorisasi Backend

**Keputusan:** semua tindakan yang memerlukan hak akses harus divalidasi pada backend.

Penyembunyian menu atau tombol di frontend bukan mekanisme keamanan yang memadai.

### K-024 — Data Sensitif

**Keputusan:** data payroll, kasbon, akun, dan biometrik harus memiliki pembatasan akses yang sesuai.

Detail penyimpanan template sidik jari, kebijakan retensi, dan prosedur penghapusan harus ditetapkan setelah desain hardware dan kebijakan privasi dikonfirmasi.

### K-025 — Custom Role dan Akses Cabang

**Status:** Menunggu Konfirmasi.

Versi awal menggunakan tiga role. Kebutuhan membuat role khusus dan membatasi pengguna berdasarkan cabang perlu divalidasi sebelum desain otorisasi final dikunci.

### K-056 sampai K-068 — Absensi Fase 3

Dipilih/disetujui pengguna 2026-10-12. Rincian teknis, schema, dan pengujiannya ada di [`rencana-fase-3.md`](./rencana-fase-3.md); aturan operasionalnya di [`aturan-bisnis.md`](./aturan-bisnis.md) §4.7.

- **K-056:** kolom `device_pin` (teks, unik) di karyawan. Mesin rakitan cukup mengirim PIN yang sama.
- **K-057:** setiap mesin punya `accept_scans_from` (bawaan: saat didaftarkan). Scan lebih lama dibalas OK agar mesin berhenti mengirim, tidak disimpan, dan hanya dihitung (`ignored_scan_count`). Alasan: riwayat mesin uji X401 berisi 16.951 data orang di luar proyek.
- **K-058:** rekap = mesin perhitungan Fase 2 atas scan asli + koreksi aktif + aturan berlaku, dihitung saat dibuka. Tidak ada data rekap basi; snapshot baru disimpan saat payroll final (Fase 5, K-035).
- **K-059:** koreksi oleh Owner dan HR/Finance; persetujuan kedua (COR-008) belum diterapkan.
- **K-060:** `docker-compose` memetakan `8888:8080` (`DEVICE_PORT`) karena tombol 0 di keypad X401 rusak. Di produksi diganti reverse proxy (Fase 7).
- **K-061:** satu kiriman ATTLOG = satu transaksi; `insert … on conflict do nothing` atas (mesin, PIN, waktu). Balasan `OK` dikirim setelah transaksi selesai; gagal simpan → HTTP 500 `ERROR`, mesin mengirim ulang (terbukti di uji X401).
- **K-062:** baris yang tidak bisa dibaca disimpan apa adanya di `rejected_scan_lines`; kiriman tetap di-ACK agar scan valid dalam kiriman yang sama tidak tertahan.
- **K-063:** handshake `TransFlag=TransData AttLog`. Tabel lain yang tetap datang dibalas OK tanpa disimpan.
- **K-064:** `DEVICE_OFFLINE_AFTER` (bawaan 5 menit) adalah parameter teknis, bukan aturan bisnis.
- **K-065:** `PIN_TIDAK_DIKENAL` (tidak masuk rekap sampai dihubungkan), `WAKTU_DI_MASA_DEPAN` (lebih dari `SCAN_FUTURE_TOLERANCE`, bawaan 5 menit), `CABANG_BERBEDA` (tetap masuk rekap, hanya ditandai sampai Q-022 dijawab). Arti `CABANG_BERBEDA` diganti oleh K-106: sekarang hanya "scan di cabang tambahan yang diizinkan"; scan di cabang yang tidak diizinkan memakai `CABANG_TIDAK_DIIZINKAN`.
- **K-066:** koreksi baru mengisi `superseded_at` koreksi lama; audit mencatat jam masuk/pulang sebelum dan sesudah beserta alasan.
- **K-067:** mesin tak terdaftar/nonaktif dibalas HTTP 401 `UNKNOWN DEVICE`; nomor serinya disimpan di memori (bukan database) dan ditampilkan di halaman Mesin.
- **K-068:** satu-satunya perubahan pada baris scan asli adalah mengisi `employee_id` (dan menghapus penanda `PIN_TIDAK_DIKENAL`) untuk scan yang PIN-nya baru dipetakan, dalam transaksi yang sama dengan perubahan karyawan, tercatat sebagai `LINK_SCANS` di audit. Waktu, PIN, dan baris mentah tidak berubah.

### K-069 — Penyimpanan Jam Tanpa Zona Waktu (perbaikan bug)

**Keputusan (2026-10-12):** properti `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` dihapus.

**Alasan:** properti itu menggeser nilai kolom `TIME`/`TIMESTAMP` tanpa zona sebesar selisih zona JVM. Di database dev, aturan Global yang diisi 08:00 lewat aplikasi tersimpan sebagai 01:00 (JVM berzona WIB), dan aturan yang ditulis langsung ke database terbaca 7 jam bergeser. Selama semua baca/tulis lewat aplikasi di zona yang sama hasilnya tampak benar, tetapi laporan/ekspor SQL, server dengan zona berbeda, atau jam koreksi dan jam lokal mesin akan salah. Kolom `TIMESTAMPTZ` (`Instant`) tetap disimpan dalam UTC oleh Hibernate. Dijaga test regresi `TimeColumnStorageTests`.

**Dampak data:** data aturan di database dev yang dibuat sebelum perbaikan tersimpan bergeser 7 jam dan perlu diisi ulang. Belum ada data produksi.

---

## 5. Detail Keputusan Absensi dan Lembur

### K-026 — Batas Mulai Lembur

**Keputusan:** pada hari kerja normal, kandidat lembur dimulai setelah pukul 17.00, tanpa mensyaratkan delapan jam kerja normal telah terpenuhi.

Contoh: jika karyawan bekerja pukul 15.00–18.00, kandidat durasi lemburnya adalah satu jam, sebelum penyesuaian lain yang mungkin berlaku.

Keputusan ini menentukan batas waktu awal, bukan tarif atau nominal pembayaran lembur.

### K-027 — Verifikasi Lembur

**Keputusan:** sistem menghitung kandidat lembur dari catatan scan yang valid dan menyediakan proses verifikasi atau koreksi terkontrol.

Nominal final mengikuti tarif yang berlaku serta aturan yang telah disetujui. Kebutuhan persetujuan sebelum lembur dilakukan belum menjadi prasyarat yang disepakati.

### K-028 — Aturan Global dan Per Karyawan

**Keputusan:** sistem mendukung aturan global dan pengecualian khusus karyawan.

Setiap aturan harus memiliki tanggal efektif. Jika aturan khusus karyawan tersedia, prioritas terhadap aturan global harus diterapkan konsisten.

### K-029 — Koreksi Absensi

**Keputusan:** koreksi absensi tidak mengubah raw scan dan harus memiliki audit trail.

Koreksi yang berdampak pada payroll final mengikuti prosedur pembukaan kembali atau penyesuaian yang diizinkan.

### K-030 — Detail Perhitungan Absensi

**Status:** Menunggu Konfirmasi.

Hal yang belum dikunci:
- Hari kerja dan kalender hari libur.
- Jam serta perlakuan istirahat.
- Pembulatan durasi.
- Penanganan scan tidak lengkap.
- Penentuan scan masuk dan pulang.
- Dampak keterlambatan dan pulang cepat terhadap penghasilan.

Nilai-nilai tersebut harus dicatat dalam konfigurasi atau keputusan bisnis yang disetujui, bukan ditebak oleh developer.


### K-092 — Jeda Minimum Antar Scan

**Keputusan (2026-10-16, disetujui pengguna; diterapkan 2026-10-17):** Pengaturan Aturan mendapat kolom *Jeda minimum antar scan (menit)*, berlaku per tanggal efektif dan diwariskan per kolom seperti parameter lain (K-046). Saat menentukan jam masuk dan pulang, scan yang berjarak kurang dari jeda dari scan pertama kelompoknya dihitung satu kejadian. Jam masuk adalah scan pertama kelompok pertama, dan jam pulang adalah scan pertama kelompok terakhir. Kolom yang kosong berarti tanpa penggabungan (K-047). Scan asli tetap tersimpan dan tampil di detail absensi. Nilai yang diterima adalah 1–240 menit; batas 240 adalah batas validasi teknis.

**Alasan:** rekap memakai scan pertama dan terakhir, sehingga scan beruntun (misalnya 08.00.00, 08.00.04, 08.00.08 tanpa scan pulang) terbaca sebagai hadir lengkap selama 8 detik, padahal seharusnya tidak lengkap (TC-17). Celah ini berlaku untuk X401 maupun alat rakitan. Rancangan lengkap ada di [`superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md`](./superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md) §8.

**Alternatif yang dipertimbangkan:** jeda dihitung dari scan sebelumnya (ditolak: scan tanpa henti tidak pernah membuka kelompok baru), hanya memberi penanda "perlu ditinjau" tanpa penggabungan (ditolak: rekap tetap salah sampai ditinjau manual), atau nilai jeda ditanam di kode (ditolak: aturan bisnis tidak boleh di-hardcode).

### K-087, K-088, K-099 — Alat Rakitan: Protokol, Kunci, dan Pengaturan Pertama

**Keputusan (2026-10-17, disetujui pengguna; diterapkan di Rencana 2):** alat rakitan ESP32 berbicara protokol `/device/v1` (kontrak lengkap di [`protokol-alat-rakitan.md`](./protokol-alat-rakitan.md)), adapter kedua di samping ZK-Push yang memakai inti penerimaan scan yang sama (K-054).

- **K-087 Protokol sendiri.** Mesin mengirim heartbeat setiap 5 detik dan scan dengan ID UUID buatan mesin serta waktu ber-offset. Balasan scan memuat daftar `accepted`; mesin menghapus scan dari microSD hanya jika ID-nya ada di daftar itu.
- **K-088 Kunci per mesin.** Saat Owner menambah alat rakitan, server membuat ID `RK-XXXXXX` dan kunci 32 byte acak. Kunci dikirim ke browser sekali, disusun menjadi *kode pengaturan* `BAS1.…` bersama alamat server, lalu ditempel di halaman pengaturan mesin. Server hanya menyimpan SHA-256 kunci; kunci tidak masuk audit log. "Buat kode baru" langsung mencabut kunci lama.
- **K-099 Rincian kontrak** (keputusan Claude yang disetujui pengguna):
  - `/iclock/**` hanya menerima mesin `ZK_PUSH` dan `/device/v1/**` hanya `RAKITAN_V1`, supaya ID yang terlihat di layar alat rakitan tidak bisa dipakai lewat `/iclock` tanpa kunci.
  - Jenis mesin dan ID alat rakitan tidak bisa diubah setelah dibuat.
  - Hanya Owner yang menambah alat rakitan dan menerbitkan kunci. HR/Finance tetap bisa menambah mesin ZK-Push dan mengubah nama, cabang, serta status semua mesin.
  - Alat rakitan nonaktif dibalas 403 `MESIN_NONAKTIF` (bukan 401 seperti K-067) agar layar mesin memberi pesan yang benar, dan tidak memperbarui "terakhir terlihat". Kunci salah dan mesin tak dikenal sama-sama 401.
  - Konfirmasi "Mesin … baru saja terhubung" muncul pada kontak pertama yang berhasil dengan setiap kunci baru (kolom `devices.key_first_used_at`).
  - Scan rusak disimpan di `rejected_scan_lines` (K-062) dan ID-nya tetap masuk `accepted`, supaya antrean mesin tidak tersangkut.
  - Isi heartbeat (`uptimeSeconds`, `pendingScans`, `sensor`) disimpan ringkas di `last_info`.
  - Duplikat dicegah dua aturan unik: mesin + ID scan, dan mesin + PIN + waktu (K-061). X401 tidak berubah karena ID scannya selalu kosong.

**Alasan:** alat rakitan tidak punya tombol, jadi pengaturan pertama harus cukup dengan menempel satu kode dari HP; kunci per mesin menutup celah protokol X401 yang tidak memakai secret (K-055).

**Alternatif yang dipertimbangkan:** meniru protokol ZK-Push (ditolak: tanpa autentikasi dan tanpa ID scan), kunci disimpan terenkripsi agar bisa ditampilkan ulang (ditolak: kunci yang hilang cukup diganti), dan alat rakitan nonaktif dibalas 401 (ditolak: layar mesin tidak bisa membedakan kunci salah dan mesin nonaktif). Penggantian kunci dua tahap tanpa memutus mesin dan konfirmasi ulang password (K-098) dikerjakan di Rencana 6.

### K-091, K-094, K-100 — Pendaftaran Sidik Jari dari Web

**Keputusan (2026-10-17, disetujui pengguna: "approve semua"; diterapkan di Rencana 3 pada 2026-10-18):** admin mendaftarkan sidik jari dari Detail Karyawan → **Sidik Jari**, lewat perintah `ENROLL` ke alat rakitan (kontrak [`protokol-alat-rakitan.md`](./protokol-alat-rakitan.md) §4 dan §6; spec §16).

- **K-091 Persetujuan.** Tombol pendaftaran terkunci sampai centang *"Karyawan sudah menyetujui penyimpanan sidik jari"* dicentang. Persetujuan dicatat sekali per karyawan (siapa dan kapan, kolom `employees.fingerprint_consent_*`) dan masuk audit `FINGERPRINT_CONSENT`. Centang berikutnya tidak mengubah catatan. Formulir persetujuan kertas diurus klien.
- **K-094 Batasan pendaftaran.**
  - Maksimal 2 jari per karyawan (jari ke-1 dan ke-2).
  - Satu `ENROLL` aktif (`MENUNGGU`/`DIKIRIM`) per mesin dan per karyawan + jari, dijaga partial unique index sehingga dua klik bersamaan menghasilkan satu perintah dan pesan *"Mesin sedang dipakai untuk pendaftaran."* atau *"Jari ini sedang didaftarkan."*
  - Batas waktu adalah parameter teknis di `application.properties`, bukan aturan bisnis: belum diambil 30 detik → `KEDALUWARSA`; mesin menunggu jari 60 detik; tanpa hasil 75 detik (60 + 15 cadangan) → `GAGAL` + `TIDAK_ADA_HASIL` (K-101). Pemeriksaan berjalan setiap 5 detik dan sebelum membuat perintah baru.
  - Syarat: karyawan aktif, PIN mesin terisi, persetujuan tercatat, alat rakitan aktif dan Online. X401 tidak mendukung pendaftaran dari web.
- **K-100 Rincian Rencana 3** (keputusan Claude yang disetujui pengguna):
  - **Template belum disimpan.** Server hanya mencatat jari mana, di mesin mana, kapan, dan dari perintah mana (`employee_fingerprints`). Field `template` di laporan hasil diabaikan dan tidak masuk database, log, maupun audit. Penyimpanan terenkripsi (K-089) menyusul di Rencana 4 jika uji R503 lulus (Q-035).
  - **Antrean umum `device_commands`** dengan ID UUID; isi perintah hanya PIN, jari, dan nama tampilan.
  - **Pengiriman ulang lewat heartbeat** sampai ada hasil; mesin mengerjakan setiap ID sekali. Pembatalan dikirim lewat daftar `cancel` (2 menit setelah dibatalkan).
  - **Laporan setelah batal diabaikan.** Hasil untuk perintah yang tidak aktif dibalas 200 tanpa efek, supaya mesin berhenti mengirim; status tetap `DIBATALKAN`. Jika batal dan hasil bentrok, yang tersimpan lebih dulu yang berlaku: hasil lebih dulu → status mengikuti hasil dan admin mendapat *"Perintah sudah selesai."* (K-101). Laporan langkah untuk perintah tidak aktif dibalas 409 `PERINTAH_TIDAK_AKTIF`.
  - **Pendaftaran ulang** jari yang sama mengganti catatan lama (satu baris per karyawan + jari).
  - **Audit.** Mulai, batal, diambil mesin, langkah, hasil, dan kedaluwarsa dicatat. Pelaku laporan mesin `mesin <ID mesin>`, kedaluwarsa `sistem`.
  - **Web** mengikuti status lewat SSE (event `DEVICE_COMMAND`) dengan polling 3 detik sebagai cadangan. Teks status dibuat server.
  - Di luar Rencana 3: kunci PIN (K-093), hapus sidik jari, penyalinan ke mesin lain, dan jenis perintah lain.

**Alasan:** sensor R503 belum diuji, jadi penyimpanan template ditunda tanpa menahan alur pendaftaran. Heartbeat 5 detik sudah ada dan cukup cepat untuk pendaftaran interaktif, tanpa koneksi dari server ke mesin (K-087).

**Alternatif yang dipertimbangkan:** menyimpan template sekarang (ditolak: format dan kemampuan R503 belum terbukti), perintah dikirim sekali saja (ditolak: balasan heartbeat yang hilang membuat perintah tersangkut), dan menerima hasil setelah batal (ditolak: admin sudah melihat "dibatalkan"; selisih isi sensor dibereskan lewat rekonsiliasi Rencana 4).

### K-101 — Perapian Rencana 3

- **Kode batas waktu server.** Batas waktu tanpa hasil dari server memakai kode `TIDAK_ADA_HASIL` (*"Mesin tidak merespons. Periksa koneksi mesin."*), terpisah dari `WAKTU_HABIS` yang dikirim mesin saat tidak ada jari (*"Tidak ada jari yang ditempelkan. Ulangi pendaftaran."*). Dengan begitu pesan web menunjuk penyebab yang benar. Kontrak mesin tidak berubah. Data development lama berkode `WAKTU_HABIS` dari server tidak dimigrasi.
- **Batal yang bentrok.** Batal yang bentrok dengan laporan mesin (`@Version`) dicoba ulang sekali, dan yang tersimpan lebih dulu yang berlaku. Kalau masih bentrok setelah itu, admin mendapat pesan umum "Data baru saja diubah…".
- **Audit catatan jari.** Pendaftaran dan pendaftaran ulang jari dicatat di audit (`FINGERPRINT_ENROLLED`, `FINGERPRINT_REENROLLED`) dengan isi sebelum dan sesudahnya, tanpa template.
- **Update kolom yang berubah saja.** `Employee` hanya menulis kolom yang berubah (`@DynamicUpdate`), agar persetujuan sidik jari dan edit HR yang bersamaan tidak saling menimpa.

**Alasan:** kekurangan yang ditemukan di review akhir Rencana 3 (M1, M3, M4, M5).

**Alternatif yang dipertimbangkan:** `@Version` pada `Employee` (ditolak: edit HR yang bersamaan dengan persetujuan akan gagal padahal kolomnya berbeda), dan satu kode `WAKTU_HABIS` untuk keduanya (ditolak: pesan koneksi menyesatkan saat jari tidak ditempelkan).

### K-102 — Rincian Rencana 5: hapus sidik jari, kunci PIN, dan kapasitas sensor

Merinci K-090 dan K-093. Rincian lengkap ada di spec §17 ([pendaftaran-sidik-jari-web-design.md](superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md)).

- **Keputusan pengguna (2026-10-18):**
  - **A.** PIN tetap terkunci sampai semua mesin melaporkan penghapusan selesai: tidak bisa diganti, dikosongkan, atau diberikan ke karyawan lain.
  - **B.** Menonaktifkan mesin membuka kunci PIN; perintah hapus untuk mesin nonaktif tidak dihitung.
  - **Tanggal keluar.** Sidik jari dihapus saat karyawan dinonaktifkan atau saat tanggal keluarnya tiba, bukan saat tanggal keluar diisi.
- **Perintah hapus.** `DELETE_TEMPLATE` (PIN + jari) dan `DELETE_EMPLOYEE` (PIN). Satu baris per alat rakitan yang pernah tersambung, aktif maupun nonaktif; mesin yang belum pernah tersambung dan X401 dilewati. Perintah hapus tidak kedaluwarsa, tidak bisa dibatalkan, tanpa laporan langkah, dan idempoten di mesin.
- **Hapus satu jari.** Catatan jari langsung dihapus (audit `FINGERPRINT_DELETED`), lalu perintah dibuat per mesin (audit `DELETE_REQUEST`). Ditolak jika jari itu sedang didaftarkan.
- **Karyawan keluar.** Tanggal keluar adalah hari kerja terakhir; penghapusan berjalan mulai 00.00 keesokan harinya menurut zona waktu cabang. Perubahan karyawan diproses di transaksi yang sama; tanggal keluar yang tiba belakangan ditangkap pemeriksaan terjadwal setiap jam (pelaku `sistem`). Penanda `employees.fingerprints_removed_at` mencegah pengulangan dan dikosongkan saat karyawan aktif kembali.
- **Kunci PIN.** PIN *menunggu penghapusan* jika ada `DELETE_*` dengan PIN itu di mesin aktif berstatus `MENUNGGU`, `DIKIRIM`, atau `GAGAL`. Pemeriksaan kunci berjalan sebelum penghapusan, sehingga mengganti PIN dan menonaktifkan sekaligus ditolak tanpa menghapus apa pun. `ENROLL` ke mesin yang masih punya penghapusan untuk PIN itu ditolak. Tambahan 2026-10-20 (review akhir): PIN juga terkunci selama karyawan punya `ENROLL` yang masih `MENUNGGU`/`DIKIRIM`, karena perintah itu membawa PIN lama ke mesin.
- **"Coba lagi".** Hanya `DELETE_*` berstatus `GAGAL`; perintah yang sama (ID sama) kembali `MENUNGGU` (audit `COMMAND_RETRY`). Kontrak mesin: ID boleh dilupakan setelah hasilnya diterima server, jadi ID yang muncul lagi dikerjakan ulang.
- **Kapasitas sensor.** `sensor.used`/`capacity` terakhir dari heartbeat disimpan di `devices`; peringatan di atas 90% (`app.device.sensor-warning-percent`, parameter teknis).
- **Catatan X401.** Bagian Sidik Jari mengingatkan untuk menghapus langsung di mesin X401 jika ada mesin X401 aktif.

**Alasan:** template belum disimpan server dan penyalinan antar mesin belum ada (Rencana 4), jadi server tidak tahu pasti mesin mana yang menyimpan jari.

**Alternatif yang dipertimbangkan:** membuka kunci PIN segera setelah catatan di server dihapus (ditolak: jari lama di mesin bisa tercatat atas nama pemilik PIN baru), dan menghapus saat tanggal keluar diisi (ditolak pengguna: karyawan masih bekerja sampai hari terakhirnya).

### K-103 — Cabang absen per karyawan

**Keputusan (2026-10-20, jawaban client disampaikan pengguna):** client memiliki 25 karyawan dan 3 cabang dengan 1 mesin per cabang. Cabang tempat karyawan boleh absen diatur dari web, per karyawan.

- **Bentuk.** Di Detail Karyawan ada daftar centang cabang yang boleh dipakai. Cabang penempatan selalu tercentang.
- **Hak akses.** Owner dan Finance & Accounting boleh mengubahnya (K-135; sempat hanya Owner menurut K-128). Setiap perubahan dicatat di audit log.
- **Penegakan utama.** Sidik jari hanya didaftarkan (`ENROLL`) ke alat rakitan di cabang yang diizinkan, sehingga mesin cabang lain tidak mengenali karyawan itu.
- **Pengaman di server.** Scan dari mesin di cabang yang tidak diizinkan (misalnya lewat X401, atau jari yang belum terhapus) tetap disimpan apa adanya, tetapi **tidak masuk rekap** sampai ditinjau. Ini mengganti perilaku `CABANG_BERBEDA` di K-065, yang saat ini tetap memasukkan scan ke rekap.

**Sebelumnya belum diputuskan** (sudah diputuskan di K-106):
- cara meninjau scan yang tertahan, dan hasilnya (masuk rekap atau tetap diabaikan);
- apa yang terjadi pada sidik jari di mesin saat sebuah cabang dicabut dari daftar (misalnya `DELETE_EMPLOYEE` ke mesin cabang itu, mengikuti K-102);
- perlakuan scan lama sebelum pengaturan ini berlaku.

Ketiganya sudah diputuskan di K-106.

**Alasan:** client ingin sebagian karyawan bisa absen di cabang lain dan sebagian tidak. Menolak di tingkat pendaftaran sidik jari lebih kuat daripada hanya memberi penanda, dan pengaman di server tetap diperlukan untuk X401 dan jari yang belum terhapus.

**Alternatif yang dipertimbangkan:** tetap menerima scan dan hanya menandainya (ditolak: karyawan yang tidak diizinkan tetap tercatat hadir), menolak total tanpa peninjauan (ditolak: tidak ada jalan untuk kasus sah), dan satu sakelar "semua cabang / cabang penempatan" (ditolak: kurang fleksibel jika cabang bertambah).

### K-104 — Rincian Rencana 6: pengaturan mesin jarak jauh

Merinci K-089, K-096, dan K-098. Pengguna meminta langsung dikerjakan ("langsung eksekusi saja"), jadi rincian ini **keputusan Claude** yang bisa diubah pengguna. Rincian lengkap ada di spec §18 ([pendaftaran-sidik-jari-web-design.md](superpowers/specs/2026-10-16-pendaftaran-sidik-jari-web-design.md)); kontrak mesin di [protokol-alat-rakitan.md](protokol-alat-rakitan.md) §4 dan §6b.

- **Pengaturan ringan.**
  - Tabel `device_settings`, satu baris per alat rakitan, dibuat saat pertama kali disimpan dari web. Isinya: judul layar, sapaan, tampilan nama, kecerahan, volume buzzer, jeda "Sudah tercatat", dan interval heartbeat (2–20 detik).
  - Selama belum ada baris, versi 0 dan mesin memakai bawaan firmware. Bawaan ini sama dengan `app.device.settings.default.*`.
  - Setiap simpan yang mengubah nilai menaikkan versi per mesin. "Pengaturan semua mesin" hanya mengenai alat rakitan aktif dan hanya menaikkan versi mesin yang nilainya berubah.
  - Paket dikirim di heartbeat jika versi server > 0 dan **berbeda** dari versi mesin. Dengan begitu mesin yang versinya lebih baru (setelah database dipulihkan) tetap menerima paket.
  - Versi yang dilaporkan mesin hanya dicatat jika 0 ≤ versi ≤ versi server.
  - Batas waktu pendaftaran dan zona waktu tidak masuk paket, karena sudah dikirim di tempat lain.
  - Batas panjang teks masih sementara (Q-037).
- **Konfirmasi ulang password.**
  - `APPLY_NETWORK`, "Ganti kunci", dan "Buat kode baru" wajib membawa `confirmPassword`.
  - Password salah dibalas 400 *"Password salah."*, bukan 401.
  - Membatalkan perintah tidak butuh password.
- **Perintah berisiko** (`APPLY_NETWORK`, `ROTATE_KEY`).
  - Maksimal satu yang aktif per mesin (unique index).
  - Tidak dikirim selama ada `ENROLL` aktif, dan `ENROLL` ditolak selama perintah berisiko aktif.
  - Tidak kedaluwarsa selama `MENUNGGU`. Owner bisa membatalkan hanya selama `MENUNGGU`.
  - Hasil `DIKIRIM` tanpa laporan: jaringan gagal `TIDAK_ADA_HASIL` setelah 10 menit; kunci gagal `KUNCI_BARU_TIDAK_DIPAKAI` 24 jam setelah perintah dibuat.
- **Jaringan.**
  - Setiap permintaan menjadi paket jaringan (1–5 Wi-Fi berurutan dan/atau alamat server). Password Wi-Fi disimpan AES-256-GCM dan didekripsi hanya saat balasan heartbeat dibuat.
  - "Jaringan yang berlaku" dihitung terpisah: Wi-Fi dari paket `BERHASIL` terbaru yang memuat Wi-Fi, dan alamat server dari paket `BERHASIL` terbaru yang memuat alamat. Dengan begitu, permintaan yang hanya mengganti server tidak menghapus Wi-Fi yang berlaku.
  - Password yang dikosongkan untuk SSID yang sudah berlaku berarti memakai password tersimpan.
- **Kunci dua tahap.**
  - Kunci baru disimpan sebagai hash dan sebagai sandi terenkripsi (heartbeat perlu mengirimnya ulang). Selama transisi, kedua kunci diterima.
  - Kontak pertama dengan kunci baru memindahkannya secara atomik, sekali walau dua heartbeat datang bersamaan. Request yang kalah balapan tetap diterima lewat kunci utama yang baru. Setelah itu kunci lama ditolak.
  - Mesin menyimpan kedua kunci sampai kunci baru dibalas 200. Tanpa balasan, kunci baru dicoba lagi; jika dibalas 401, mesin mencoba kunci lama. Tujuannya agar balasan yang hilang tidak mengunci mesin keluar.
  - Laporan `BERHASIL` dari mesin dibalas 200 tanpa efek, karena yang menyelesaikan perintah adalah pemakaian kunci baru.
  - "Buat kode baru" membuang kunci tertunda dan membatalkan `ROTATE_KEY`, termasuk yang sudah dikirim. Mesin diberi tahu lewat `cancel`.
- **Kunci enkripsi server.**
  - `DATA_ENCRYPTION_KEY` (32 byte base64) dibaca dari environment. Kunci ini tidak disimpan di database dan tidak di-commit.
  - Tanpa kunci ini, jaringan dan ganti kunci ditolak, sedangkan fitur lain tetap berjalan.
  - Paket yang tidak bisa didekripsi (kunci server diganti) tidak dikirim. Paket itu dicatat di log dan gagal sendiri lewat batas waktu, sehingga heartbeat tidak gagal 500 terus-menerus.
- **Audit.**
  - Aksi baru: `UPDATE_SETTINGS`, `NETWORK_REQUEST`, `ROTATE_KEY_REQUEST`, `KEY_ROTATED`.
  - Audit tidak pernah memuat password Wi-Fi, kunci, atau hash kunci. Snapshot jaringan hanya berisi SSID dan alamat server.
- **Hak akses.** Pengaturan ringan: Owner dan HR/Finance. Jaringan dan kunci: hanya Owner. Ringkasan perintah jaringan/kunci terakhir ada di tampilan jaringan khusus Owner, bukan di daftar mesin.

**Alasan:** pengaturan ringan aman dikirim ulang kapan saja, jadi cukup paket berversi. Perubahan jaringan dan kunci bisa memutus mesin dari server, jadi dibuat sebagai perintah yang dicoba dulu oleh mesin, dibatasi satu per mesin, dan dilindungi password.

**Alternatif yang dipertimbangkan:**
- Mengirim paket hanya jika versi mesin lebih lama. Ditolak, karena mesin yang versinya lebih baru setelah database dipulihkan tidak akan pernah diperbarui.
- Mengganti kunci dalam satu langkah. Ditolak, karena mesin yang belum menerima kunci baru akan langsung terputus.
- Menyimpan password Wi-Fi tanpa enkripsi. Ditolak, mengikuti K-089.

### K-105 — Ekspor laporan mengikuti format dokumen AS-IS

**Keputusan (2026-10-22, permintaan client disampaikan pengguna):** web harus bisa menghasilkan **Excel dan PDF** dengan format seperti laporan yang dipakai client sekarang. Laporan-laporan itu dirinci di [dokumen-as-is.md](dokumen-as-is.md) (AS-1 sampai AS-7):

- absensi dan upah mingguan supir/kenek;
- lembur mingguan;
- gaji bulanan staf;
- rekap lembur bulanan;
- laporan detail absensi (jam scan per tanggal);
- pengajuan dana gaji per toko.

Ketujuh dokumen ini memperluas laporan baseline di K-042. Form Lemburan tulis tangan (AS-3) adalah sumber data, bukan laporan yang harus diekspor.

- **Urutan.** Laporan yang hanya butuh data absensi (detail absensi dan kisi kehadiran) bisa dibuat sebelum Fase 5. Laporan upah, gaji, dan pengajuan dana dibuat bersama atau setelah Fase 5.
- **Yang belum diputuskan:**
  - Library ekspor, diputuskan saat rencana ekspor dibuat (PRD menyebut Apache POI untuk Excel).
  - Tata letak persisnya, menunggu file Excel asli dari client.
  - Setiap indikasi aturan di dokumen AS-IS, yang tetap berstatus belum dikonfirmasi (Q-038–Q-044).
- **Audit.** Setiap ekspor dicatat di audit log, sesuai rencana Fase 6 dan AUD-009.

**Alasan:** client sudah terbiasa dengan format ini, termasuk untuk pengesahan Director dan pengajuan dana ke bagian akuntansi. Laporan yang mirip memudahkan peralihan dan rekonsiliasi dengan hasil manual.

### K-106 — Rincian cabang absen per karyawan

Merinci K-103 dan mengganti arti `CABANG_BERBEDA` di K-065. Pengguna meminta rancangan disusun langsung, jadi rincian ini **keputusan Claude**, dan kelima keputusan di bagian akhir sudah disetujui pengguna pada 2026-10-22. Rincian lengkap ada di [spec](superpowers/specs/2026-10-22-cabang-absen-per-karyawan-design.md).

- **Cabang yang diizinkan** = cabang penempatan ditambah cabang tambahan (tabel `employee_extra_branches`, migration `V11__cabang_absen.sql`). Cabang penempatan tidak disimpan di tabel itu, sehingga selalu diizinkan dan tidak bisa dicabut.
- **Data lama.** Setelah migration, karyawan lama hanya diizinkan di cabang penempatannya sampai HR mencentang cabang tambahan.
- **Penempatan diganti.** Cabang lama otomatis menjadi cabang tambahan (tidak ada jari yang dihapus), dan cabang baru dikeluarkan dari daftar tambahan bila ada. Cabang penempatan yang dikirim di `extraBranchIds` diabaikan.
- **Penanda scan.** `CABANG_TIDAK_DIIZINKAN` diberikan saat scan diterima jika cabang mesin tidak diizinkan. `CABANG_BERBEDA` sekarang hanya berarti "scan di cabang tambahan yang diizinkan": tetap masuk rekap, penanda hanya informasi. Satu scan tidak pernah membawa kedua penanda.
- **PIN diberikan belakangan (K-068).** Scan `PIN_TIDAK_DIKENAL` yang dihubungkan ke karyawan ikut diberi `CABANG_TIDAK_DIIZINKAN` bila mesinnya tidak diizinkan.
- **Tidak berlaku surut.** Mengubah daftar cabang tidak menilai ulang scan yang sudah tersimpan.
- **Peninjauan.** Hasil disimpan di tabel terpisah `scan_reviews` (`DITERIMA` atau `DIABAIKAN`, alasan wajib maksimal 500 karakter, peninjau, waktu, `@Version`). Scan asli tidak pernah diubah. Scan tertahan = berpenanda `CABANG_TIDAK_DIIZINKAN` dan belum `DITERIMA` (belum ditinjau atau diabaikan). Keputusan boleh diubah; nilai lama tersimpan di audit log. Dua peninjau bersamaan: satu berhasil, satu 409. Pengaman ini hanya untuk pengiriman yang bersamaan: tinjauan dari halaman yang sudah basi (permintaan tidak membawa versi) menimpa keputusan terbaru, dan keputusan sebelumnya tetap ada di audit log.
- **Rekap.** Scan tertahan dibuang sebelum perhitungan harian, sehingga Absensi, Rekan Kerja, dashboard, dan penggajian konsisten. Detail hari tetap menampilkan scan itu berlabel "Tertahan" atau "Diabaikan", dan hari diberi penanda `ADA_SCAN_TERTAHAN` bila ada yang belum ditinjau. Penanda hari ini hanya muncul bila hasil hitung hari itu ada: jika aturan kerja belum diatur (rekap belum bisa dihitung), penanda tidak muncul, tetapi status scan (Tertahan/Diabaikan) tetap terlihat di daftar scan.
- **Mencabut cabang** (dalam satu transaksi): `ENROLL` aktif di mesin cabang itu dibatalkan, catatan jari di mesin cabang itu dilupakan dan `DELETE_TEMPLATE` dikirim ke mesin itu saja (mekanisme K-102). Web meminta konfirmasi lebih dulu, misalnya "2 sidik jari di mesin Takka Cilong akan dihapus."
- **Pendaftaran.** `ENROLL` ke mesin di cabang yang tidak diizinkan ditolak 400; pilihan mesin di form hanya menampilkan cabang yang diizinkan.
- **Audit.** Perubahan cabang dicatat sebagai `UPDATE` karyawan dengan snapshot datar (field karyawan ditambah `extraBranchIds`); saat pencabutan, bagian `after` memuat `removedFingerprints`. Aksi audit baru `SCAN_REVIEW`, satu per scan, berisi keputusan lama dan baru. Setelah commit, `AttendanceChangedEvent` dikirim agar Absensi dan dashboard diperbarui.
- **Endpoint.** `GET` dan `PUT /api/employees/{id}/branches` (isi `{ "extraBranchIds": [...] }`), `GET /api/attendance/held-scans?from&to&status` (bawaan 7 hari; status `BELUM_DITINJAU`, `DITERIMA`, atau `DIABAIKAN`), dan `POST /api/attendance/held-scans/review` (`{ scanIds, decision, reason }`, maksimal 200 scan). Semua untuk Owner dan HR/Finance; karyawan 403.
- **Pesan error (400, Bahasa Indonesia).** "Karyawan tidak diizinkan absen di cabang mesin ini.", "Alasan wajib diisi.", dan "Scan ini tidak tertahan karena cabang."
- **Tampilan.** Kartu "Cabang absen" di Detail Karyawan (penempatan terkunci), halaman `/absensi/scan-tertahan` (filter tanggal 30 hari dan status, pilih banyak, alasan wajib), dan kartu dashboard "Scan tertahan" yang menaut ke halaman itu.

**Keputusan Claude yang disetujui pengguna (2026-10-22):**
1. Cabang penempatan lama tetap diizinkan saat penempatan diganti, dan baru dicabut jika HR menghapus centangnya.
2. Tidak berlaku surut.
3. Alasan wajib, baik untuk menerima maupun mengabaikan scan tertahan.
4. Mencabut cabang langsung menghapus sidik jari di mesin cabang itu, setelah dikonfirmasi di layar.
5. Setelah migration, semua karyawan hanya diizinkan di cabang penempatannya sampai HR mencentang cabang tambahan.

**Alasan:** menolak di tingkat pendaftaran lebih kuat daripada penanda saja, dan pengaman di server tetap perlu untuk X401 dan jari yang belum terhapus. Hasil peninjauan dipisah dari scan asli supaya scan asli tidak pernah berubah, dan alasan wajib menjaga jejak audit.

**Alternatif yang dipertimbangkan:** menilai ulang scan lama setiap daftar cabang berubah (ditolak: rekap masa lalu berubah diam-diam), mengubah penanda pada baris scan saat ditinjau (ditolak: scan asli harus utuh), dan mencabut cabang tanpa menghapus jari (ditolak: jari lama tetap tercatat dan terus tertahan).

### Fase 4 — Izin dan Cuti (K-070–K-082)

Rincian teknis ada di [rencana-fase-4.md](rencana-fase-4.md). Keputusan dipilih pengguna pada 2026-10-13.

- **K-070 Jenis izin diatur Owner.** Nama, wajib surat, dibayar (boleh "belum ditentukan"), keterangan, aktif. Contoh awal *Sakit*, *Cuti*, *Izin* hanya data awal, bukan aturan. Jenis tidak dihapus, hanya dinonaktifkan. Kolom "dibayar" belum dipakai sampai Fase 5.
- **K-071 Hari penuh.** Izin datang terlambat/pulang cepat sementara lewat koreksi absensi (K-059).
- **K-072 Tanpa kuota.** Sistem tidak membatasi; pemakaian per jenis per tahun (hari kalender dan hari kerja, rentang dipotong ke tahun tersebut) ditampilkan sebagai informasi.
- **K-073 Pengaju.** Karyawan lewat `/api/me/leave-requests` (karyawan diambil dari akun, bukan dari input; milik orang lain dibalas 404). Owner/HR lewat `/api/leave-requests` dengan `employeeId`.
- **K-074 Status.** Menunggu → Disetujui / Ditolak (alasan wajib). Pengaju bisa membatalkan selama Menunggu; Owner/HR bisa membatalkan yang Menunggu atau Disetujui dengan alasan. Persetujuan bersamaan dicegah `@Version`.
- **K-075 Tidak tumpang tindih.** Dijaga di service (pesan jelas) dan oleh constraint `EXCLUDE USING gist` untuk status Menunggu/Disetujui, sehingga aman walau dua pengajuan dikirim bersamaan.
- **K-076 Efek ke rekap.** Hanya di hari kerja: izin Disetujui tanpa scan/koreksi → status `IZIN`; ada scan/koreksi → rekap tetap dari scan + penanda `HADIR_SAAT_IZIN`; Menunggu → status tidak berubah + penanda `IZIN_MENUNGGU` (LEAVE-006). Hari libur/bukan hari kerja tidak berubah. Dampak ke gaji belum diterapkan.
- **K-077 Wajib surat saat persetujuan.** Pengajuan bisa dikirim lebih dulu (surat dokter menyusul).
- **K-078 Lampiran.** Jenis dideteksi dari *magic bytes*, nama file di disk UUID acak, hash SHA-256 dicatat, unduhan selalu `attachment` + `nosniff`. Lampiran tidak bisa dihapus dan hanya bisa ditambah selama Menunggu. Setiap pembukaan lampiran oleh Owner/HR dicatat di audit (`VIEW`); isi file tidak pernah masuk audit.
- **K-079 Pemisahan tugas.** Akun Owner/HR yang terhubung ke data karyawan tidak bisa memutus pengajuan karyawan itu.
- **K-080 Tanggal lampau.** Diizinkan tanpa batas hari; rentang maks. 366 hari kalender (CHECK di database).
- **K-081 Notifikasi.** Event SSE `LEAVE` dikirim setelah commit; Owner/HR melihat pop-up dalam hitungan detik dan lonceng berisi pengajuan yang menunggu (terbaru di atas). Karyawan melihat hasil keputusan di halaman "Izin & Cuti Saya".
- **K-082 Surat cetak/PDF.** Halaman surat tanpa kerangka aplikasi, ukuran A4, dicetak lewat browser ("Simpan sebagai PDF"); tidak ada library PDF di server. Kop surat masih umum dan bisa disesuaikan dengan contoh dari klien.

### Daftar rekan kerja untuk karyawan (K-083–K-084)

> **Diganti K-127 (2026-10-25):** karyawan hanya melihat absensinya sendiri. Bentuk data terbatas K-084 tetap dipakai untuk Absensi Saya.

Rincian teknis ada di [rencana-rekan-kerja.md](rencana-rekan-kerja.md). Dipilih pengguna pada 2026-10-14 (hasil rapat dengan tim).

- **K-083 Absensi rekan kerja.** Semua pengguna yang login (termasuk Karyawan) boleh melihat absensi semua karyawan aktif: No. karyawan, nama, cabang, jabatan, status, jam masuk/pulang, terlambat, dan lembur. Data sensitif (No. HP, PIN mesin, kategori, tanggal masuk/keluar, jenis/alasan/surat izin, koreksi, data gaji) tidak dikirim, karena respons paket `team` di backend hanya berisi kolom yang diizinkan; pembatasan tidak bergantung pada frontend.
- **K-084 Status sederhana.** Hadir / Belum lengkap / Tidak hadir / Izin / Libur / Belum diatur. Izin tampil tanpa jenis; izin yang masih menunggu dan "hadir saat izin" tidak ditampilkan sebagai izin; jam hasil koreksi tampil tanpa keterangan koreksi. Jam dan durasi dikirim dalam menit penuh (dibulatkan ke bawah), karena koreksi selalu berdetik 0 sedangkan scan asli tidak; tanpa pembulatan, koreksi bisa ditebak dari detiknya. Tanggal yang belum terjadi ditolak (400), agar tidak tampil "Tidak hadir" palsu dan izin mendatang tidak terlihat.

### Tema kaca (K-085)

Rincian di [rencana-tema-kaca.md](rencana-tema-kaca.md). Dipilih pengguna 2026-10-14.

- **K-085 Glassmorphism penuh.** Semua kartu, panel, tabel, form, dialog, dan kerangka memakai kaca dengan blur asli di semua perangkat, di atas latar gradasi statis. Mode terang dan gelap dengan tombol tema di header (Terang / Gelap / Ikut perangkat). Menggantikan larangan blur di K-017 poin 4; warna brand, font, dan logo K-017 tetap. Pengaman: kontras teks minimal 4.5:1 diuji otomatis dari token, tanpa blur bertumpuk, kaca solid untuk "kurangi transparansi", input dan surat cetak tetap solid.

### Port mesin di mode development (K-086)

Dipilih pengguna 2026-10-16, agar uji dengan mesin X401 yang dibawa dan dicolok kabel langsung ke laptop tetap jalan saat backend dijalankan lewat `start-dev.bat` (lihat [panduan-mesin-x401.md](panduan-mesin-x401.md)).

- **K-086 Port kedua opsional.** Bila `app.device.extra-port` (`DEVICE_EXTRA_PORT`) diisi, backend membuka konektor Tomcat kedua di port itu yang melayani aplikasi yang sama (`config/DevicePortConfig`). `start-dev.bat` mengisinya dengan `DEVICE_PORT` (bawaan `8888`), sehingga mesin tidak perlu diatur ulang antara mode Docker (`8888:8080`, K-060) dan mode development. Kosong = tidak ada port tambahan; Docker dan produksi tidak memakainya. Alternatif `netsh portproxy` ditolak karena butuh hak Administrator dan bentrok dengan port 8888 milik Docker.

### Hasil meeting client 2026-10-23 (K-107–K-112)

Meeting dengan client membahas dokumen requirement HLX-TKS-ATT-REQ-001 v0.4 (21 Oktober 2026). Revisi yang disepakati dicatat di sini. Rincian K-109 sampai K-112 dipilih pengguna pada 2026-10-24.

- **K-107 Sensor R307.** Alat rakitan memakai sensor R307, bukan R503. Kontrak `/device/v1` tidak bergantung pada jenis sensor, jadi server tidak berubah. Kemampuan R307 (kapasitas, unggah/unduh template, pencarian 1:N, password modul) harus dibuktikan lewat uji prototipe (Q-035) sebelum penyalinan template (K-089) diputuskan. Sebutan R503 di K-100 dan spec lama dibaca sebagai "sensor alat rakitan".
- **K-108 Akun individual untuk setiap karyawan.** Client memilih Opsi A di dokumen requirement (Bagian 5.4): setiap karyawan login dengan akunnya sendiri dan mengajukan izin lewat web. Opsi akses bersama (Opsi C) dan aturan terkaitnya (BR-015, BR-019, FR-USR-006, FR-AUD-002) tidak dipakai. Kode sudah mendukung akun yang terhubung ke data karyawan (K-073). K-083 (daftar rekan kerja tanpa data sensitif) tetap berlaku.
- **K-109 Persetujuan ganda per jenis izin.**
  - Owner menandai di master Jenis Izin (K-070) jenis yang butuh persetujuan Owner. Penanda ini data, bukan aturan di kode. Menurut meeting, Cuti dan Sakit butuh persetujuan ganda.
  - Urutannya HR/Finance dulu (verifikasi dan review), lalu Owner memberi persetujuan akhir. Penolakan oleh HR atau Owner langsung membuat pengajuan Ditolak.
  - Jenis tanpa penanda tetap mengikuti K-073: cukup satu persetujuan Owner atau HR/Finance.
  - Rincian teknis (status antara, pengajuan milik HR sendiri menurut K-079, pembatalan, notifikasi, migration) diputuskan di rencana implementasi.
- **K-110 Jam kerja efektif.**
  - Jadwal 08.00–17.00 dengan istirahat 12.00–13.00 menghasilkan 8 jam kerja efektif.
  - Karyawan yang masuk 09.00 dan pulang 17.00 terhitung 7 jam.
  - Mesin perhitungan sudah memotong irisan jam istirahat dan tidak menghitung waktu sebelum jam masuk (K-048, aturan-bisnis §3.3), jadi tidak perlu kode baru. Owner mengisi nilainya di Pengaturan Aturan, dan nilai ini tidak ditanam di kode (K-047).
  - Skenario ini menjadi TC-18.
- **K-111 Penggajian hanya diubah Owner.** Owner satu-satunya yang mengubah tarif, menjalankan perhitungan, memfinalkan, dan membuka kembali payroll. HR/Finance hanya melihat dan mereview input serta hasil, sesuai FR-PAY-002 dan BR-021. Keputusan ini memperluas K-049 ke modul payroll Fase 5 dan menjawab "siapa" di Q-014. Alasan wajib dan prosedur koreksi tetap terbuka.
- **K-112 Satu cabang dulu.** Alat rakitan dipasang di satu cabang dulu. Dua cabang lain belum memakai sistem dan tetap memakai cara lama sampai alatnya dipasang. Cabang mana yang dipilih dan perlakuan karyawan di dua cabang lainnya dicatat sebagai Q-045.

**Alternatif yang dipertimbangkan (K-109):** persetujuan ganda hanya untuk Cuti dan Sakit yang ditanam di kode (ditolak karena aturan bisnis tidak boleh di-hardcode, dan jenis izin sudah berupa master data), serta urutan bebas (ditolak karena proses minimum di dokumen requirement menempatkan review HR sebelum Owner).


### Persetujuan ganda dan surat izin (K-113–K-118)

Rincian teknis K-109, disepakati pengguna pada 2026-10-24 dan diimplementasikan pada tanggal yang sama. Rancangan lengkap ada di [spec](superpowers/specs/2026-10-24-persetujuan-ganda-surat-izin-design.md); alur status di [alur.md](alur.md) bagian 8. Migration: V12 (status dan kolom baru), V13 (`leave_approvals`), V14 (`user_signatures` dan `leave_requests.applicant_signature_key`).

- **K-113 Status dan persetujuan bertahap.**
  - Status: `DRAFT`, `PERLU_TINJAU_HR`, `PERLU_TINJAU_OWNER`, `DISETUJUI`, `DITOLAK`, `DIBATALKAN`. Status lama `MENUNGGU` dimigrasi ke `PERLU_TINJAU_HR`; `DISETUJUI` lama tetap dengan riwayat satu tahap dari `decided_by`/`decided_at`.
  - Draft tidak berpengaruh ke rekap dan pengecekan tumpang tindih. Status aktif (K-075, K-076) adalah `PERLU_TINJAU_HR`, `PERLU_TINJAU_OWNER`, dan `DISETUJUI`.
  - Kebutuhan persetujuan Owner dan templat surat (`UMUM`, `IZIN`, `CUTI`, `SAKIT`) adalah kolom master Jenis Izin yang diatur Owner, lalu **dibekukan di pengajuan saat dikirim**. Mengubah jenis belakangan tidak mengubah rantai yang sedang berjalan.
  - Jenis satu persetujuan: HR/Finance atau Owner memutus di tahap HR (K-073). Jenis persetujuan ganda: HR/Finance dulu, lalu Owner; penolakan di tahap mana pun langsung Ditolak.
  - K-079 berlaku di setiap tahap: akun yang terhubung ke karyawan pemohon tidak memutus, mencatat keputusan Owner, atau menyetujui pengajuannya sendiri. ~~Bila pemohon adalah satu-satunya HR aktif (`HrReviewerCounter`), Owner boleh mengerjakan tahap HR; riwayat mencatatnya, dan tahap Owner tetap harus dilakukan terpisah.~~ Diganti K-125: Owner memutus langsung di tahap Owner.
  - Karyawan menarik pengajuan selama `PERLU_TINJAU_*` (alasan opsional). Pembatalan oleh HR/Owner wajib beralasan (K-074). Hapus draft bersifat permanen dan dicatat di audit (`DELETE`) dengan isi sebelumnya.
  - Audit baru: `SUBMIT`, `STAGE_APPROVE`, `OWNER_DECISION`, dan `VIEW` pada `LEAVE_LETTER` dan `LEAVE_EVIDENCE`.
- **K-114 PDF dibuat server (menggantikan sebagian K-082).**
  - PDF A4 dibuat dengan OpenPDF 2.4.0 dan QR dengan ZXing 3.5.4; font Poppins dan Open Sans (lisensi OFL) di-embed.
  - Alasan: spesimen tanda tangan HR/Owner tidak boleh dikirim mentah ke browser karyawan, nomor dan QR harus konsisten, dan "Simpan sebagai PDF" di HP tidak andal untuk dikirim lewat WhatsApp.
  - Surat belum final diberi cap diagonal (`DRAFT`, `MENUNGGU PERSETUJUAN`, `DITOLAK`, `DIBATALKAN`); surat Disetujui tanpa cap. Halaman cetak lama `LeaveLetterPage` dihapus, dan rute lamanya dialihkan ke detail pengajuan.
  - Akses: pemilik lewat `/api/me`, Owner/HR lewat endpoint manajer, karyawan lain mendapat 404. Unduhan Owner/HR diaudit (`VIEW`).
  - Tombol "Kirim ke WhatsApp" membuka `wa.me` tanpa nomor dengan link ke halaman persetujuan, dan "Bagikan PDF" memakai Web Share API. Tidak ada integrasi WhatsApp API dan tidak ada nomor WA tersimpan.
- **K-115 Spesimen tanda tangan.**
  - Satu spesimen per akun, digambar di kanvas atau diunggah (PNG/JPG, maks. 1 MB, jenis dicek dari isi file), dinormalkan menjadi PNG transparan maksimal 600x200.
  - Tidak ada endpoint untuk melihat spesimen akun lain; pemilik hanya melihat miliknya. Perubahan diaudit tanpa isi gambar.
  - **Dibekukan sebagai salinan** saat pengajuan dikirim (pemohon) dan pada setiap persetujuan di web (penyetuju). Mengganti spesimen belakangan tidak mengubah surat lama. Tanpa spesimen, kotak berisi nama dan "disetujui secara elektronik". Ini bukan tanda tangan elektronik tersertifikasi.
- **K-116 Verifikasi publik bertoken.**
  - `GET /api/public/letters/{token}` tanpa login; token acak 128-bit dibuat saat kirim dan dimuat di QR (`<base>/verifikasi/<token>`).
  - Data minimal: nomor, nama, cabang, jenis, rentang, status, dan daftar persetujuan. Nama hanya `fullName` karyawan yang terhubung, atau "HR/Finance" dan "Owner"; username tidak pernah tampil. Alasan, isian rinci, dan lampiran tidak tampil.
  - Batas laju 30 permintaan per menit per IP, di memori (`app.public-letters.rate-per-minute`). Terlampaui menghasilkan 429. Alamat dasar link dari `app.public-base-url` (env `PUBLIC_BASE_URL`); kosong berarti origin pengunduh.
- **K-117 Keputusan Owner dicatat HR.**
  - Hanya di `PERLU_TINJAU_OWNER`, oleh HR/Finance yang bukan pemohon. Isian: setuju/tolak, cara (`WHATSAPP`, `LISAN`, `TERTULIS`), waktu keputusan, catatan wajib, dan bukti.
  - Bukti wajib untuk WhatsApp dan Tertulis, opsional untuk Lisan. Waktu keputusan tidak boleh di masa depan dan tidak boleh sebelum tahap HR; pembandingan pada ketelitian menit.
  - Riwayat mencatat Owner sebagai pemberi keputusan dan HR sebagai pencatat. Owner mendapat notifikasi di aplikasi. Spesimen Owner **tidak** ditempel; surat menulis "Disetujui melalui WhatsApp pada ..., dicatat oleh ...". Bukti hanya bisa dibuka HR/Owner dan setiap pembukaan diaudit.
- **K-118 Beranda dan navigasi karyawan.**
  - Halaman awal role Karyawan menjadi `/beranda` (menggantikan `/profil` di `homePathFor`): sapaan, kartu Hari ini, pengajuan berjalan (maks. 3), tombol Ajukan izin, ringkasan bulan ini dari data K-083. Tidak ada data gaji.
  - Navigasi bawah di HP berisi 4 item: Beranda, Izin, Rekan, Profil (Rekan diganti Absensi oleh K-127). Desktop tetap sidebar. Pengajuan lewat alur 4 langkah (Jenis, Tanggal & isian, Lampiran & tanda tangan, Pratinjau) dengan draft tersimpan setiap berpindah langkah.
  - Rekan Kerja di HP menampilkan kartu ringkas dan pencarian "Cari nama"; kolom dan batasan data tetap K-083/K-084. Karyawan tidak mendapat notifikasi langsung (K-081 tetap untuk Owner/HR).
- **K-119 Tombol keputusan mengikuti flag dari server.**
  - Respons detail izin untuk penampil Owner/HR memuat `canDecide` dan `canRecordOwnerDecision`, dihitung dengan aturan yang sama dengan endpoint aksi (bukan pemohon, status menunggu, tahap yang berjalan, termasuk K-079 untuk HR tunggal). Halaman detail hanya menampilkan tombol bila flag benar, tanpa menebak dari role di klien. Endpoint karyawan dan daftar selalu mengirim flag salah.
- **K-120 Notifikasi keputusan Owner yang dicatat HR bersifat permanen.**
  - Lonceng Owner menampilkan bagian "Keputusan Anda yang dicatat HR": keputusan tahap Owner dengan cara WhatsApp/Lisan/Tertulis dalam 7 hari terakhir, terbaru dulu. Tanda "sudah dilihat" disimpan di browser (`localStorage`, kunci per pengguna `absensi.recordedSeen.<username>`), bukan di server. Setiap baris menampilkan status pengajuan saat ini (mis. Dibatalkan). Nama pencatat memakai nama karyawan yang terhubung ke akunnya, atau "HR/Finance" bila tidak ada (tidak pernah username, sejalan dengan K-123).
  - Alasan: tanpa perubahan schema. Bila nanti perlu lintas perangkat, pindahkan ke tabel notifikasi. Melengkapi K-117.
- **K-121 Kembali ke halaman semula setelah login.**
  - Pengguna yang belum login diarahkan ke `/login?next=<path>`; setelah login (dan setelah ganti password wajib) ia dikembalikan ke `next` bila berupa path relatif aman (satu garis miring, bukan `//`, bukan skema, bukan `/login` atau `/ganti-password`). Selain itu ke halaman awal role.
  - Logout manual tidak membawa `next`; sesi kedaluwarsa membawanya.
- **K-122 Riwayat persetujuan untuk pengajuan lama yang dibatalkan.**
  - Migration V15 mengisi `leave_approvals` untuk pengajuan lama berstatus Dibatalkan yang sebelumnya sudah disetujui (V13 hanya mengambil Disetujui dan Ditolak). V13 tidak diubah karena sudah di-push.
- **K-123 Nama pencatat di surat PDF.**
  - Kolom Owner yang dicatat HR menulis "dicatat oleh <nama karyawan pencatat>"; bila akun tidak terhubung ke karyawan, memakai label peran "HR/Finance". Username tidak pernah tampil, termasuk di verifikasi publik.
- **K-124 Halaman Profil untuk semua role.**
  - `/profil` dibuka untuk Owner dan HR/Finance agar mereka bisa mengatur spesimen tanda tangan (K-115) yang ditempel saat menyetujui di web. Sebelumnya hanya role Karyawan yang punya halaman ini, sehingga persetujuan Owner/HR selalu tanpa gambar.
  - Akun yang tidak terhubung ke karyawan melihat username, peran, dan keterangan "Akun ini tidak terhubung ke data karyawan", tanpa meminta `/api/me/employee`. Menu Profil Owner/HR ada di sidebar kelompok "Saya" dan di menu "Lainnya" pada HP; bottom navigation tidak berubah. Tanpa perubahan backend atau schema (spec persetujuan ganda sudah menyebut semua akun).

**Alternatif yang dipertimbangkan:** cetak lewat browser tetap dipakai (ditolak: tanda tangan harus berasal dari server dan HP sulit menyimpan PDF yang andal); menempel spesimen Owner untuk keputusan yang dicatat HR (ditolak: sama dengan memalsukan tanda tangan); verifikasi hanya untuk pengguna login (ditolak: penerima PDF lewat WhatsApp belum tentu punya akun).


### Keputusan review gap v0.6 (K-125–K-128)

Jawaban pengguna pada 2026-10-25 atas tabel "Ketidaksesuaian dokumen dan kode" di dokumen review gap requirement v0.6. Rencana: [2026-10-25-keputusan-review-gap-v06.md](superpowers/plans/2026-10-25-keputusan-review-gap-v06.md).

- **K-125 Owner memutus langsung tanpa tahap HR** (baris 2).
  - Untuk pengajuan `PERLU_TINJAU_HR` pada jenis persetujuan ganda, Owner boleh langsung **Setujui** atau **Tolak**. Keputusan disimpan sebagai satu baris riwayat tahap `OWNER` (metode `WEB`), lalu status menjadi `DISETUJUI` atau `DITOLAK`. Tahap HR tidak mendapat baris riwayat.
  - Satu pengguna tidak pernah memberi dua persetujuan (FR-LV-008). Jalur "Owner mengerjakan tahap HR bila pemohon satu-satunya HR" di K-113 tidak diperlukan lagi dan diganti aturan ini.
  - Surat pendukung tetap wajib (K-077) selama pengajuan belum melewati tahap HR, siapa pun yang menyetujui.
  - Jenis satu persetujuan tidak berubah: Owner memutus di tahap HR (K-073).
  - HR menyetujui tahap HR bersamaan dengan Owner memutus langsung: penguncian versi pengajuan membuat yang kalah mendapat 409.
  - Surat PDF: kotak "HR/Finance" berisi teks "Disetujui langsung oleh Owner" atau "Ditolak langsung oleh Owner" beserta waktu keputusan, tanpa nama dan gambar tanda tangan. Garis waktu menandai langkah "Ditinjau HR" dengan teks yang sama.
  - Lonceng Owner tidak berubah: pengajuan tahap HR jenis persetujuan ganda tidak dimasukkan (kecuali pemohon HR tunggal), agar tidak terlalu ramai. Owner memutus langsung dari menu Izin & Cuti.
- **K-126 Ajukan ulang pengajuan yang ditolak** (baris 3).
  - Di detail pengajuan sendiri berstatus `DITOLAK`, karyawan menekan **Ajukan ulang**. Server (`POST /api/me/leave-requests/{id}/resubmit`) membuat draft baru yang menyalin jenis, tanggal, alasan, dan isian templat, dengan `resubmitted_from_id` menunjuk pengajuan lama, lalu karyawan dibawa ke wizard untuk memeriksa, mengunggah lampiran, dan mengirim.
  - Lampiran tidak disalin. Pengajuan lama tidak berubah. Pengecekan saat kirim (tumpang tindih, jenis aktif, isian templat) memakai jalur kirim draft biasa.
  - Satu pengajuan yang ditolak hanya bisa diajukan ulang sekali (unique index V16, klik ganda dibalas 409). Draft hasil ajukan ulang yang dihapus membebaskan pengajuan lama untuk diajukan ulang lagi; pengajuan ulang yang ditolak lagi diajukan ulang dari pengajuan yang baru itu.
  - Jenis izin yang sudah dinonaktifkan: 409 "Jenis izin ini sudah tidak aktif". Audit draft baru: `RESUBMIT` dengan catatan "Ajukan ulang dari #<id>".
  - Detail pengajuan menampilkan "Pengajuan ulang dari" dengan tautan ke nomor surat pengajuan lama.
- **K-127 Karyawan hanya melihat absensinya sendiri** (baris 1; FR-USR-002, BR-018, AC-USR-01).
  - Endpoint dan halaman rekan kerja (`/api/team/**`, `/rekan-kerja`) dihapus; alamat lama dialihkan ke Absensi Saya.
  - Halaman **Absensi Saya** `/absensi-saya` menampilkan riwayat harian milik sendiri per bulan dari `GET /api/me/attendance?from=&to=` (bawaan: awal bulan sampai hari ini). Bentuk data sama dengan riwayat rekan sebelumnya (K-084: tanpa jenis izin, tanpa sumber koreksi, jam dalam menit penuh, tanpa data sensitif).
  - Akun tanpa data karyawan atau karyawan nonaktif: 404 "Data karyawan untuk akun ini". Tanggal di masa depan: 400.
  - Beranda mengambil kartu Hari ini dan Bulan ini dari endpoint yang sama. Navigasi bawah karyawan: Beranda, Izin, Absensi, Profil.
- **K-128 Data karyawan dan cabang absen hanya diubah Owner** (baris 6). **Dibatalkan sebagian oleh K-135 (2026-10-26):** Finance & Accounting kembali boleh menambah/mengubah karyawan dan cabang absen; uraian di bawah dipertahankan sebagai riwayat.
  - `POST /api/employees`, `PUT /api/employees/{id}`, dan `PUT /api/employees/{id}/branches` hanya untuk Owner. HR/Finance mendapat 403.
  - HR/Finance tetap membaca daftar dan detail karyawan serta mengurus sidik jari (`/fingerprint-consent`, `/fingerprints/**`), sesuai catatan 7 requirement v0.6.
  - Alasan pengguna: HR tidak boleh memanipulasi aturan kerja lewat data karyawan, karena kategori dan aturan per karyawan melekat ke data karyawan. Aturan kerja sendiri sudah khusus Owner (K-049).
  - Master Kategori dan Cabang tetap bisa diubah HR/Finance (dikonfirmasi pengguna 2026-10-25).
  - Frontend: daftar karyawan tanpa tombol Tambah/Ubah dan kartu Cabang absen dalam mode hanya baca untuk selain Owner ("Hanya Owner yang dapat mengubah cabang absen.").

### Data karyawan dari client (K-129–K-136)

Client mengirim template "Data Karyawan" berisi 28 karyawan (Takka 1: 4, Takka 2: 18, Takka Cilong: 6) pada 2026-10-26. File aslinya berisi data pribadi, sehingga diabaikan git dan tidak disalin ke repo. Rencana awal: [2026-10-26-profil-dan-impor-karyawan.md](superpowers/plans/2026-10-26-profil-dan-impor-karyawan.md). Impor Excel kemudian dibatalkan dan diganti input lewat form dengan akun dan pesan WA: [spec](superpowers/specs/2026-10-26-akun-karyawan-pesan-wa-design.md), [rencana](superpowers/plans/2026-10-26-akun-karyawan-pesan-wa.md).

- **K-129 Profil lengkap karyawan.** Migration V17 menambah kolom email, jenis kelamin (`LAKI_LAKI`, `PEREMPUAN`, `TIDAK_DISEBUTKAN`), tanggal lahir, alamat, dan catatan pada tabel `employees`. Semua opsional, bisa diedit Owner di form karyawan, dan tampil di detail karyawan serta Profil Saya. Validasinya sama dengan form: format email, tanggal lahir sebelum hari ini, alamat dan catatan maksimal 500 karakter.
- **K-130 Impor karyawan dari template client** (`/karyawan/impor`, hanya Owner). **Dibatalkan 2026-10-26 dan sudah dihapus dari kode** (endpoint, halaman, dan dependency Apache POI). Pengguna memilih memasukkan karyawan satu per satu lewat form (K-133). Impor Excel yang dibutuhkan adalah rekap mesin cabang lain, yang menunggu format dari client. Uraian di bawah hanya riwayat.
  - Dua langkah tanpa state di server: `POST /api/employee-imports/preview` (file .xlsx + tanggal mulai) lalu `POST /api/employee-imports` (baris yang sudah diperiksa). Server selalu memvalidasi ulang. Satu baris bermasalah saja membuat seluruh impor ditolak (409, daftar masalah per baris di ProblemDetail `rows`).
  - Kolom template dicocokkan dari teks judulnya. Isian dirapikan: spasi berlebih, nomor HP tanpa 0 di depan atau berawalan 62, tanggal lahir berupa serial Excel, dan baris kedua alamat dipindah ke catatan.
  - Unit `Office` → kategori Kantor (bulanan, termasuk staf Gudang); `Supir` dan `Kenek` → kategori Supir/Kenek. Cabang dicocokkan dari namanya.
  - Tanggal masuk semua karyawan impor = tanggal mulai pemakaian sistem yang dipilih Owner (bawaan tanggal 1 bulan berjalan). Perhitungan dimulai pada bulan itu; tanggal masuk sebenarnya tidak diminta ke client.
  - PIN mesin = ID karyawan (`001`, `002`, …). Mesin rakitan diatur penuh dari web, sehingga PIN bisa disesuaikan saat integrasi bila perlu.
  - Status Akun `Aktif` / `Nonaktif` / `Belum dibuat` menentukan status karyawan dan akunnya; `Belum dibuat` tidak dibuatkan akun.
  - Setiap karyawan dan akun dicatat di audit log seperti lewat form, ditambah satu entri `IMPORT` (`EMPLOYEE_IMPORT`). Password tidak pernah masuk audit log.
- **K-131 Format akun karyawan.**
  - Username `<nama>@takka.id`, dengan nama panggilan huruf kecil. Usulan otomatis memakai kata pertama nama setelah awalan Muhammad/M/Siti (`dafa`, `rochmat`, `balqis`); Owner bisa mengubahnya di pratinjau. Username kini boleh memuat `@`.
  - Password sementara `<nama>-<ID>-takka` (contoh `eni-006-takka`; semula `<nama>-<ID>`), wajib diganti saat login pertama, dan hanya ditampilkan sekali di jendela pesan setelah akun dibuat atau direset. Dengan akhiran `-takka`, password selalu minimal 8 karakter, sehingga username boleh pendek (`eni@takka.id`).
  - Risiko yang diterima pengguna: password awal mudah ditebak rekan kerja, sehingga sebaiknya dibagikan tepat sebelum akun dipakai.
  - Role dari data client: 001 Owner; 003 dan 005 Finance & Accounting; lainnya Karyawan. Akun Owner bawaan dari environment tetap dipertahankan sebagai akun teknis tim pengembang. Owner (001) ikut dihitung absensi dan gajinya.
- **K-133 Tambah karyawan sekaligus akun, dengan pesan WA.**
  - Form Tambah Karyawan punya centang "Buatkan akun login" (tercentang bawaan). Username, password sementara, dan PIN mesin terisi usulan dari nama dan No. karyawan, mengikuti ketikan selama belum diubah manual. Role selain Karyawan hanya bisa dipilih Owner.
  - `POST /api/employees` menerima `account: {username, password, role}` opsional. Karyawan dan akun dibuat dalam satu transaksi lewat `UserAccountService`; bila akun gagal (mis. username sudah dipakai, 409), karyawan juga tidak tersimpan. Password tidak pernah dikembalikan dan tidak masuk audit log.
  - Setelah akun dibuat atau password direset, muncul jendela pesan berisi kredensial dengan tombol **Salin pesan** dan **Kirim WA** (`wa.me`, nomor HP dirapikan ke `62…`; tanpa nomor bila HP kosong). Pesan hanya ada di memori halaman dan tidak bisa ditampilkan ulang; untuk mengirim ulang, reset password.
  - Detail karyawan punya tombol **Buat akun** dan **Reset password**; menu Akun juga memakai usulan dan pesan yang sama. Nama aplikasi di pesan sementara "Sistem Absensi Takka Steel" (Q-025).
- **K-134 Tanggal masuk otomatis.** `joinDate` di `EmployeeRequest` opsional: saat tambah diisi tanggal hari ini menurut zona waktu cabang penempatan; saat ubah nilai lama dipertahankan. Form tambah tidak menampilkan tanggal masuk dan keluar; form ubah tetap punya Tanggal keluar dan Aktif.
- **K-135 Hak tulis Finance & Accounting.** `POST /api/employees`, `PUT /api/employees/{id}`, dan `PUT /api/employees/{id}/branches` kembali untuk Owner dan Finance & Accounting. Finance & Accounting tetap tidak bisa membuat/mereset akun Owner atau Finance & Accounting (403) dan tidak bisa mengubah Aturan Kerja (K-049). Celah yang diterima pengguna: Finance & Accounting memilih kategori karyawan, sehingga secara tidak langsung menentukan aturan kerja kategori mana yang berlaku.
- **K-136 Form karyawan ringkas** (`components/EmployeeFormDialog.tsx`, bergaya sheet pemilih tanggal).
  - Yang selalu tampil hanya **Identitas** (No. karyawan, Nama lengkap, No. HP, Jabatan) dan **Penempatan** (Cabang, Kategori), dua kolom di desktop.
  - **PIN mesin** dan **akun login** (username + password sementara) tampil sebagai kartu ringkasan berisi nilai usulan, dengan tombol **Ubah** untuk membuka field aslinya. Role akun (khusus Owner) dan jenis kelamin berupa tombol pilihan berjejer.
  - **Data pribadi** (jenis kelamin, tanggal lahir, email, alamat, catatan) dilipat saat tambah dan terbuka saat ubah; alamat dan catatan berupa textarea.
  - Jabatan menyarankan jabatan yang sudah pernah dipakai, tetapi tetap bisa diketik baru. Cabang/kategori langsung terpilih bila hanya ada satu yang aktif.
  - Bila server menolak field yang sedang tersembunyi, bagiannya terbuka sendiri agar pesan error terlihat.
  - Tidak dipilih: kategori otomatis dari jabatan, karena aturan pemetaannya belum dikonfirmasi client.
### Keamanan akun (K-137–K-138, 2026-10-27)

- **K-137 Akun ikut status karyawan.** Saat `PUT /api/employees/{id}` mengubah Aktif, akun yang terhubung ikut diubah di transaksi yang sama (`UserAccountService.followEmployeeStatus`) dan dicatat sebagai audit `UPDATE` pada `USER_ACCOUNT`.
  - Dinonaktifkan: semua sesi akun itu dicabut, sehingga karyawan yang sedang login langsung keluar.
  - Diaktifkan kembali: akun ikut aktif (pilihan pengguna). Risiko yang diterima: akun yang sengaja dinonaktifkan terpisah di menu Akun ikut aktif lagi bila karyawannya dinonaktifkan lalu diaktifkan.
  - Karyawan tanpa akun tidak terpengaruh. Akun milik pengguna sendiri atau Owner aktif terakhir tidak bisa dinonaktifkan lewat jalur ini (400, pesan sama dengan menu Akun).
  - Finance & Accounting hanya bisa memicu perubahan ini untuk akun role Karyawan. Bila karyawannya terhubung ke akun Owner atau Finance & Accounting, perubahan status ditolak 403 dan karyawan tidak jadi diubah (aturan sama dengan menu Akun, K-135).
  - Form ubah karyawan menampilkan pemberitahuan "(bila ada)" saat Aktif diubah, karena form tidak tahu apakah akunnya ada.
- **K-138 Batas percobaan login** (`auth/LoginAttemptLimiter`).
  - Setelah `app.login.max-failures` (5) kali gagal berturut-turut, username itu ditolak selama `app.login.lock-minutes` (15) menit, termasuk bila passwordnya benar: **429** "Terlalu banyak percobaan masuk. Coba lagi dalam N menit." (N dibulatkan ke atas).
  - Username dinormalisasi (trim, huruf kecil) dan dibatasi 50 karakter (400 bila lebih). Username yang tidak ada ikut dihitung agar tidak bisa dipakai menebak username. Login berhasil mengosongkan hitungan.
  - Gagal hanya dihitung dalam satu masa kunci: bila gagal terakhir sudah lebih dari 15 menit lalu, hitungan mulai dari nol.
  - Permintaan paralel bisa sedikit melewati batas 5 karena pemeriksaan dilakukan sebelum password dicek; diterima karena bcrypt lambat dan pengguna sedikit.
  - Saat kunci mulai berlaku dicatat di audit (`LOGIN_LOCKED`, entitas `AUTH`, aktor = username yang dicoba).
  - Disimpan di memori satu proses seperti K-116: kunci hilang saat server di-restart. Penyerang bisa sengaja mengunci akun orang lain selama 15 menit; diterima karena jumlah pengguna kecil.

- **K-132 Label "Finance & Accounting".** Semua teks yang dilihat pengguna (menu, pesan error, notifikasi, label tahap izin, dan surat PDF) memakai "Finance & Accounting" menggantikan "HR/Finance" dan "HR". Kode role `HR_FINANCE` dan tahap `HR` di API tidak berubah, sehingga data lama tetap valid.

---

## 6. Detail Keputusan Payroll

### K-031 — Periode Payroll

**Keputusan:**
- Supir dan kenek menggunakan payroll mingguan.
- Karyawan lainnya menggunakan payroll bulanan.

Tanggal mulai dan akhir periode serta tanggal pembayaran masih harus mengikuti kalender payroll perusahaan yang dikonfirmasi.

### K-032 — Gaji Pokok dan Upah Harian

**Keputusan:** gaji pokok bulanan dan upah harian adalah komponen berbeda.

Sistem harus menyimpan keduanya secara terpisah dan menampilkan dasar perhitungan masing-masing. Dampak ketidakhadiran terhadap gaji pokok masih menunggu kebijakan perusahaan.

### K-033 — Tarif dengan Tanggal Efektif

**Keputusan:** perubahan tarif harus memiliki tanggal mulai berlaku.

Perhitungan periode historis menggunakan tarif yang sesuai dengan periode tersebut. Perubahan tarif untuk periode baru tidak boleh mengubah hasil payroll historis secara otomatis.

### K-034 — Pembukaan Kembali Payroll

**Keputusan:** payroll final dapat dibuka kembali melalui prosedur terkontrol.

Pembukaan kembali tidak boleh menghapus hasil final sebelumnya. Kewenangan, alasan wajib, dan persetujuan tambahan perlu ditentukan bersama klien.

### K-035 — Snapshot Payroll

**Keputusan:** setiap payroll yang difinalisasi harus menyimpan snapshot atau versi yang setara.

Snapshot sekurang-kurangnya mencakup:
- Periode dan identitas karyawan.
- Komponen penghasilan serta potongan.
- Tarif dan aturan yang digunakan.
- Input absensi dan koreksi yang relevan.
- Durasi serta nominal lembur yang disetujui.
- Nominal bonus dan komponen tambahan.
- Hasil perhitungan bruto, potongan, dan bersih.
- Waktu finalisasi dan identitas pelaku.

### K-036 — Riwayat Reopen dan Finalisasi Ulang

**Keputusan:** pembukaan kembali dan finalisasi ulang harus mempertahankan riwayat versi sebelumnya.

Contoh: tarif upah harian Rp80.000 berlaku pada September, kemudian Rp100.000 berlaku mulai Oktober. Payroll September yang dibuka kembali pada Oktober tetap menggunakan tarif historis September, kecuali terdapat koreksi retroaktif resmi yang disetujui dan tercatat.

### K-037 — Ledger Kasbon

**Keputusan:** kasbon menggunakan ledger transaksi.

Saldo dihitung dari transaksi kasbon dan pelunasan. Sistem tidak boleh mengganti saldo secara langsung tanpa transaksi yang dapat diaudit.

Batas cicilan dan aturan pemotongan payroll menunggu konfirmasi.

### K-038 — Bonus

**Keputusan:** nominal bonus ditetapkan oleh Owner. Pada versi pertama, sistem tidak menghitung bonus otomatis berdasarkan KPI atau rumus yang belum disepakati.

### K-039 — Pajak dan Potongan

**Keputusan:** BPJS, PPh 21, dan potongan lain hanya diterapkan setelah kebijakan perusahaan dikonfirmasi.

Jangan menambahkan komponen potongan secara otomatis hanya karena lazim digunakan oleh sistem payroll lain.

### K-040 — Keterlacakan Payroll

**Keputusan:** setiap hasil payroll harus dapat ditelusuri kembali ke sumber input dan aturan yang digunakan.

Pengguna berwenang harus dapat memeriksa dasar perhitungan tanpa mengubah snapshot historis.

### K-041 — Kewenangan Pembukaan Kembali

**Status:** Menunggu Konfirmasi.

Perlu ditentukan:
- Role yang dapat mengajukan pembukaan kembali.
- Role yang dapat menyetujui.
- Alasan wajib.
- Perlakuan jika payroll sudah dibayarkan.
- Mekanisme koreksi dan penyesuaian pada pembayaran berikutnya jika diperlukan.

### K-042 — Laporan Baseline

**Keputusan:** empat laporan aktual perusahaan menjadi baseline rekonsiliasi dan pengujian:

1. Absensi dan payroll mingguan supir/kenek.
2. Laporan lembur supir/kenek.
3. Absensi dan payroll bulanan staf.
4. Laporan lembur bulanan staf.

Contoh nyata keempat laporan ini, ditambah laporan detail absensi dan pengajuan dana per toko, dirinci di [dokumen-as-is.md](dokumen-as-is.md) (K-105).

### K-043 — Regression Test Payroll

**Keputusan:** pengujian regresi wajib mencakup perubahan tarif, perhitungan ulang payroll historis, pembukaan kembali, dan finalisasi ulang.

Perubahan pada konfigurasi periode baru tidak boleh mengubah snapshot payroll historis tanpa proses koreksi resmi.

---

## 7. Keputusan yang Masih Terbuka

Hal-hal berikut tidak boleh dianggap sudah disepakati:

| Topik | Yang perlu dipastikan | Dokumen acuan |
|---|---|---|
| Jadwal kerja | Hari kerja, jam istirahat, kalender hari libur | `pertanyaan-terbuka.md` |
| Absensi | Penentuan scan masuk/pulang, pembulatan, scan tidak lengkap | `pertanyaan-terbuka.md` |
| Lembur | Tarif, pembulatan, perlakuan hari libur | `pertanyaan-terbuka.md` |
| Payroll | Cutoff, prorata, dampak ketidakhadiran | `pertanyaan-terbuka.md` |
| Potongan | BPJS, PPh 21, cicilan kasbon | `pertanyaan-terbuka.md` |
| Bonus | Komponen dan aturan selain nominal yang ditetapkan Owner | `pertanyaan-terbuka.md` |
| Reopen payroll | Kewenangan, persetujuan, koreksi setelah pembayaran | `pertanyaan-terbuka.md` |
| Role | Custom role dan akses per cabang | `pertanyaan-terbuka.md` |
| Hardware | MCU, sensor, protokol, enrollment, penghapusan | `pertanyaan-terbuka.md` |
| Offline | Antrean lokal, RTC, retry, sinkronisasi | `pertanyaan-terbuka.md` |
| Privasi | Lokasi template biometrik, retensi, penghapusan | `pertanyaan-terbuka.md` |
| Deployment | Hosting, jaringan cabang, TLS, zona waktu | `pertanyaan-terbuka.md` |

---

## 8. Prosedur Mengubah Keputusan

Jika keputusan berubah:

1. Jangan menghapus entri lama.
2. Tambahkan ID keputusan baru.
3. Tulis tanggal, status, alasan, dan alternatif yang dipertimbangkan.
4. Cantumkan ID keputusan yang digantikan.
5. Perbarui dokumen terkait, seperti `aturan-bisnis.md`, `alur.md`, `arsitektur.md`, atau `kebutuhan.md`.
6. Perbarui test case yang terdampak.
7. Pastikan implementasi dan dokumentasi konsisten sebelum perubahan dirilis.

Format yang disarankan:

| ID | Tanggal | Keputusan | Status | Alasan / alternatif |
|---|---|---|---|---|
| K-XXX | YYYY-MM-DD | Isi keputusan | Disepakati / Usulan / Menunggu Konfirmasi / Digantikan | Alasan, alternatif, dan referensi keputusan sebelumnya |

---

## 9. Dokumen Terkait

- [`README.md`](./README.md)
- [`PRD.md`](./PRD.md)
- [`kebutuhan.md`](./kebutuhan.md)
- [`aturan-bisnis.md`](./aturan-bisnis.md)
- [`alur.md`](./alur.md)
- [`arsitektur.md`](./arsitektur.md)
- [`pertanyaan-terbuka.md`](./pertanyaan-terbuka.md)
- [`rencana-pengerjaan.md`](./rencana-pengerjaan.md)
- [`design-system.md`](./design-system.md)