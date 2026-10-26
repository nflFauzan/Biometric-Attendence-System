# Design System

Panduan tampilan web absensi dan penggajian: token warna, tipografi, layout responsif, dan aturan setiap komponen. Isinya menentukan gaya mana yang dipakai di elemen mana.

**Status: Disepakati (K-017), 2026-10-10; tema kaca terang/gelap ditambahkan 2026-10-14 (K-085, rincian di [rencana-tema-kaca.md](rencana-tema-kaca.md)).** Sudah diterapkan ke halaman Fase 1. Lihat [§14](#14-penerapan) untuk bagian yang belum diterapkan.

Sumber identitas brand: audit CSS, logo, dan teks situs [takkasteel.com](https://takkasteel.com) pada 2026-10-10. Katalog 35 gaya beserta pratinjaunya ada di halaman "Takka Absensi Design Directions" (artifact pribadi pemilik proyek).

## 1. Prinsip

1. **Data dulu, dekorasi belakangan.** HR/Finance membaca tabel setiap hari. Kerapian dan keterbacaan lebih penting daripada efek visual.
2. **Satu gaya dasar, aksen di tempat tertentu.** Gaya dasar dipakai di seluruh aplikasi. Gaya lain hanya boleh muncul di tempat yang disebut di [Peta gaya](#3-peta-gaya-per-elemen).
3. **Terasa milik Takka Steel.** Warna, font, dan tombol pill mengikuti situs. Motif garis vertikal dari logo menjadi ciri khas.
4. **Kaca yang tetap terbaca dan ringan (K-085).** Semua permukaan memakai kaca (`glass`, `glass-strong`, `glass-bar`) di atas latar statis. Teks minimal 4.5:1 diuji otomatis; kaca di dalam kaca tidak diberi blur lagi; "kurangi transparansi" membuat kaca solid.
5. **Status tidak hanya dibedakan dengan warna.** Setiap status punya ikon dan teks.
6. **Mobile-first.** Karyawan, terutama supir/kenek, membuka aplikasi dari HP. Pengelola membuka dari desktop maupun tablet.

## 2. Identitas brand Takka Steel

| Aspek | Temuan dari situs | Dipakai di aplikasi |
| --- | --- | --- |
| Warna utama | Skala kustom `steel`, utamanya `#104FA6` | Warna aksi utama, link, fokus |
| Warna aksen | `gold` `#F5CE02` | Penanda (garis, indikator aktif, angka sorotan). Tidak dipakai untuk teks di latar terang |
| Teks di tombol biru | `#EEEEEE` (bukan putih murni) | Sama |
| Font judul | Poppins 600–800 | Sama |
| Font isi | Open Sans 400–600 | Sama |
| Label kecil | HURUF KAPITAL, jarak huruf lebar | Label kolom tabel, eyebrow, label KPI |
| Tombol | Pill (radius penuh), Poppins 600 | Sama |
| Logo | Atap chevron emas (tekstur logam) + huruf T biru dari garis vertikal | Motif garis vertikal ("motif spandek") dan garis emas |
| Slogan | "Harga Minimum, Kualitas Premium" | Tidak ditampilkan di aplikasi, hanya acuan nada |

> Font wordmark di logo berupa gambar dan belum bisa dipastikan (paling mirip Poppins ExtraBold/Black). File logo vektor (SVG) perlu diminta ke klien, lihat [Pertanyaan terbuka](#13-pertanyaan-terbuka).

## 3. Peta gaya per elemen

Gaya dasar: **Corporate Clean**, yaitu permukaan terang dengan sedikit rona biru, biru steel untuk aksi, tombol pill, dan garis tipis tanpa bayangan.

| Elemen | Gaya | Keterangan |
| --- | --- | --- |
| Seluruh halaman kerja (latar, kartu, form) | Corporate Clean | Gaya dasar |
| Sidebar dan header aplikasi | Motif Spandek | Sidebar navy `steel-800` dengan garis vertikal tipis, item aktif ditandai garis emas |
| Tabel master data (karyawan, cabang, kategori, mesin, akun) | Corporate Clean, kerapatan **nyaman** | Baris 52px, lihat [§6.4](#64-tabel) |
| Tabel padat (rekap bulanan, penggajian, audit log, riwayat scan) | Enterprise Data-Dense, kerapatan **padat** | Baris 40px, header lengket, kolom pertama lengket |
| Tabel di HP (< 768px) | Daftar kartu | Tabel diubah menjadi kartu per baris |
| Dashboard | Bento Grid + Corporate Clean | Satu angka utama memakai Bold Typography |
| Layar pantau mesin / mode TV | Blueprint | Halaman khusus, selalu gelap |
| Halaman login | Steel Premium + kartu `glass-strong` | Kartu ikut tema terang/gelap |
| Kartu, panel, tabel, form, dialog, kerangka | Glassmorphism (K-085) | Tiga tingkat kaca, lihat [rencana-tema-kaca.md](rencana-tema-kaca.md) §2 |
| Peringatan yang butuh tindakan | Industrial Hazard (strip kuning-hitam tipis) | Scan tidak lengkap, mesin offline, periode belum final saat jatuh tempo |
| Badge status | Corporate Clean (pill lembut + ikon) | Lihat [§6.6](#66-badge-status) |
| Slip gaji, ekspor PDF, cetak | Monokrom Biru | Tetap jelas jika dicetak hitam putih |
| Empty state | Flat + motif spandek | Ilustrasi sederhana, tanpa foto |
| Mode gelap | Blueprint (tanpa grid di area tabel) | Lihat [§9](#9-mode-gelap) |
| Area karyawan di HP | Corporate Clean, ukuran besar | Area sentuh minimal 48px, teks minimal 16px |

**Tidak dipakai:** Liquid Glass, Neumorphism, Claymorphism, Neubrutalism, dan semua gaya dekoratif lain (Memphis, Y2K, Retro, Cyberpunk, dan lainnya). Alasannya ada di [§12](#12-gaya-yang-tidak-dipakai).

## 4. Token

### 4.1 Skala warna `steel` (diurutkan ulang)

Skala di situs tidak urut terang-ke-gelap (steel-900 situs lebih terang daripada steel-800 situs). Aplikasi memakai skala yang diurutkan ulang. Warna yang ditandai **tambahan** tidak ada di situs dan diturunkan dari palet brand.

| Token | Hex | Asal | Pemakaian |
| --- | --- | --- | --- |
| `steel-50` | `#F4F7FB` | situs steel-50 | Latar halaman |
| `steel-100` | `#E2EAF5` | situs steel-100 | Hover baris, latar chip, `--accent` shadcn |
| `steel-200` | `#C3D2EA` | situs steel-200 | Border input |
| `steel-300` | `#96B0D8` | situs steel-300 | Border fokus lembut, teks di latar gelap |
| `steel-400` | `#6488C0` | situs steel-400 | Ikon nonaktif, placeholder di latar gelap |
| `steel-500` | `#3D65A8` | situs steel-500 | Grafik seri kedua |
| `steel-600` | `#104FA6` | situs steel-900 | **Primary**: tombol utama, link, fokus |
| `steel-700` | `#0D3D85` | situs steel-950 | Hover tombol utama |
| `steel-800` | `#163260` | situs steel-800 | Sidebar, judul, badge "Final" |
| `steel-900` | `#0F2240` | tambahan | Latar kartu mode gelap |
| `steel-950` | `#050D1A` | situs (overlay `rgba(5,13,26)`) | Latar mode gelap, layar pantau |

### 4.2 Skala `gold`

| Token | Hex | Asal | Pemakaian |
| --- | --- | --- | --- |
| `gold-100` | `#FFF6C2` | tambahan | Sorotan baris scan baru (sesaat) |
| `gold-500` | `#F5CE02` | situs `gold` | Garis aktif sidebar, strip hazard, angka sorotan di latar gelap |
| `gold-700` | `#8A6D00` | tambahan | Teks emas di latar terang (kontras 4,9:1) |

**Aturan gold:** `gold-500` tidak boleh menjadi warna teks di latar putih (kontras 1,5:1). Di latar terang, teks di atas `gold-500` memakai `steel-800` (kontras 8,3:1).

### 4.3 Netral dan semantik

| Token | Terang | Gelap | Pemakaian |
| --- | --- | --- | --- |
| `background` | `#F4F7FB` | `#050D1A` | Latar halaman |
| `card` | `#FFFFFF` | `#0F2240` | Kartu, tabel, dialog |
| `foreground` | `#0F1F3A` | `#E6EDF7` | Teks utama |
| `muted-foreground` | `#4A5B78` | `#9FB0CB` | Teks sekunder (kontras 6,9:1 di putih) |
| `border` | `#D8E1EE` | `#1F3355` | Garis kartu dan tabel |
| `success` / bg | `#15803D` / `#DCFCE7` | `#4ADE80` / `#0F2E1C` | Tepat waktu, disetujui, online |
| `warning` / bg | `#92400E` / `#FEF3C7` | `#FCD34D` / `#33270A` | Terlambat, menunggu |
| `danger` / bg | `#B91C1C` / `#FEE2E2` | `#FCA5A5` / `#3A1414` | Tidak hadir, ditolak, offline, error |
| `info` / bg | `#0D3D85` / `#E2EAF5` | `#8DB4F0` / `#16294A` | Lembur, izin/cuti, informasi |

Warna `warning` (amber) berbeda dari `gold`. Gold adalah warna brand, bukan warna status.

### 4.4 Tipografi

Font di-*self-host* dengan paket `@fontsource/poppins` dan `@fontsource/open-sans`, tanpa Google Fonts CDN, supaya aplikasi tetap jalan di jaringan kantor yang terbatas.

| Token | Font | Ukuran / tinggi baris | Berat | Pemakaian |
| --- | --- | --- | --- | --- |
| `display` | Poppins | 36 / 40 | 800 | Angka utama dashboard |
| `h1` | Poppins | 24 / 32 (HP: 20 / 28) | 700 | Judul halaman |
| `h2` | Poppins | 18 / 26 | 600 | Judul kartu dan section |
| `h3` | Poppins | 16 / 24 | 600 | Judul dialog, sub-section |
| `body` | Open Sans | 14 / 22 (HP: 16 / 24) | 400 | Teks umum |
| `body-strong` | Open Sans | 14 / 22 | 600 | Nama karyawan di tabel |
| `small` | Open Sans | 12 / 18 | 400 | Keterangan, helper text |
| `label` | Poppins | 11 / 16, kapital, `letter-spacing: .08em` | 600 | Header kolom tabel, label KPI |
| `button` | Poppins | 14 / 20 | 600 | Tombol |

- Semua angka (jam, durasi, uang, jumlah) memakai `font-variant-numeric: tabular-nums` (`tabular-nums` di Tailwind) agar digit sejajar.
- ID teknis (serial mesin, ID scan) memakai `font-mono` bawaan sistem.
- Di HP, ukuran teks input minimal 16px agar iOS tidak melakukan zoom otomatis.

### 4.5 Jarak, radius, bayangan, gerak

| Token | Nilai | Catatan |
| --- | --- | --- |
| Skala jarak | Kelipatan 4px (Tailwind bawaan) | Jarak antar section 24px (HP 16px), antar kartu 16px |
| `radius-sm` | 6px | Checkbox, chip kecil |
| `radius` | 8px | Input, select |
| `radius-lg` | 12px | Kartu, tabel |
| `radius-xl` | 16px | Dialog, bottom sheet |
| `radius-full` | 9999px | Tombol, badge, avatar |
| Bayangan | Tidak ada pada kartu (border saja) | Bayangan hanya untuk lapisan di atas konten: dialog, dropdown, toast, header lengket |
| Gerak | 150ms `ease-out` | Semua animasi dimatikan jika `prefers-reduced-motion` |

### 4.6 Ikon

Memakai `lucide-react` (sudah terpasang), ukuran 16px di tabel/tombol dan 20px di navigasi. Ikon status yang dipakai: `CheckCircle2` (tepat waktu/disetujui), `Clock` (terlambat/menunggu), `AlertTriangle` (tidak lengkap), `XCircle` (tidak hadir/ditolak), `CalendarOff` (izin/cuti), `Moon` (lembur), `Lock` (final), `Pencil` (dikoreksi), `Wifi`/`WifiOff` (mesin).

## 5. Layout dan responsif

Breakpoint mengikuti Tailwind: `sm` 640, `md` 768, `lg` 1024, `xl` 1280.

| Lebar | Navigasi | Konten | Tabel |
| --- | --- | --- | --- |
| < 768px (HP) | Header atas + **bottom navigation** maksimal 5 item, sisanya di menu "Lainnya" (sheet dari bawah) | 1 kolom, gutter 16px | Daftar kartu |
| 768–1023px (tablet) | Sidebar ringkas 72px (ikon + label kecil) | 2 kolom, gutter 24px | Tabel dengan scroll horizontal, kolom pertama lengket |
| ≥ 1024px (desktop) | Sidebar penuh 248px | Grid 12 kolom, lebar konten maksimal 1440px, gutter 24px | Tabel penuh |

- Bottom navigation memperhitungkan `env(safe-area-inset-bottom)`.
- Tombol aksi utama di HP berada di bawah layar (lengket), bukan di pojok kanan atas.
- Dialog di HP berubah menjadi **bottom sheet** selebar layar.
- Tidak ada scroll horizontal di level halaman. Hanya kontainer tabel yang boleh scroll ke samping.

**Bottom navigation per role (HP):**

| Role | Item |
| --- | --- |
| Owner, HR/Finance | Dashboard · Absensi · Izin · Penggajian · Lainnya |
| Karyawan | Beranda · Izin · Absensi · Profil (4 item, tanpa menu "Lainnya"; halaman awal `/beranda`, K-118, K-127) |

## 6. Komponen

### 6.1 Tombol

| Varian | Tampilan | Pemakaian |
| --- | --- | --- |
| `default` (primary) | Latar `steel-600`, teks `#EEEEEE`, hover `steel-700` | Satu aksi utama per layar: Simpan, Setujui, Finalisasi |
| `secondary` | Latar `steel-100`, teks `steel-700` | Aksi kedua: Ekspor, Filter |
| `outline` | Border `steel-200`, latar putih | Batal, Kembali |
| `ghost` | Tanpa latar, hover `steel-100` | Aksi di baris tabel |
| `destructive` | Latar `danger` | Hapus, Tolak (selalu dengan konfirmasi) |
| `link` | Teks `steel-600`, garis bawah saat hover | Tautan di dalam teks |

- Semua tombol berbentuk pill (`rounded-full`), sesuai situs.
- Tinggi: desktop 36px, HP 44px. Tombol ikon di HP minimal 44×44px.
- Label berupa kata kerja yang jelas: "Setujui upah", bukan "OK".
- Tombol emas (`gold-500` dengan teks `steel-800`) **hanya** untuk satu aksi istimewa: "Finalisasi periode".

### 6.2 Form dan input

- Label di atas input, Open Sans 14 / 600. Tanda wajib isi berupa teks "(wajib)", bukan hanya bintang merah.
- Input setinggi 40px (HP 44px), radius 8px, border `steel-200`. Saat fokus: border `steel-600` + ring 3px `steel-600/25%`.
- Pesan error ditulis di bawah input dengan warna `danger` dan ikon. Isinya menjelaskan cara memperbaiki, misalnya "Username sudah dipakai. Coba username lain."
- Input uang memakai prefiks "Rp", rata kanan, angka tabular, dan pemisah ribuan otomatis.
- Input jam memakai format 24 jam (`07:58`). Zona waktu tidak diambil dari perangkat pengguna (lihat [kebutuhan.md](kebutuhan.md)), dan label "WIB" ditampilkan sesuai zona yang dikonfigurasi.
- Form panjang (karyawan, Pengaturan Aturan) dibagi per kelompok dengan judul `h3`. Di desktop memakai 2 kolom, di HP 1 kolom.
- Pengaturan Aturan selalu menampilkan input **"Berlaku mulai"** di bagian atas form.

### 6.3 Kartu

- Latar `card`, border 1px `border`, radius 12px, padding 20px (HP 16px), tanpa bayangan.
- Judul kartu `h2` dengan aksi opsional di kanan.
- Kartu tidak boleh berisi kartu lain. Untuk pengelompokan di dalam kartu, gunakan garis pemisah.

### 6.4 Tabel

Dua kerapatan, satu komponen (`table.tsx` shadcn + kelas pembungkus).

| Aspek | Nyaman (master data) | Padat (rekap, penggajian, audit) |
| --- | --- | --- |
| Tinggi baris | 52px | 40px |
| Ukuran teks | 14px | 13px |
| Padding sel | 16px horizontal | 12px horizontal |
| Header | Label kapital `label`, latar `steel-50` | Sama, **lengket** saat scroll |
| Kolom pertama | Normal | **Lengket** saat scroll horizontal |
| Garis | Garis horizontal antar baris | Garis horizontal + garis vertikal tipis antar kelompok kolom |

Aturan umum:

- Baris belang (zebra) tidak dipakai. Hover baris: latar `steel-100`.
- Angka, jam, durasi, dan uang **rata kanan** dengan angka tabular. Teks rata kiri. Badge status rata kiri.
- Kolom aksi berada di paling kanan: maksimal satu tombol ghost yang terlihat, sisanya di menu `…`.
- Baris yang dikoreksi menampilkan ikon `Pencil` dan tooltip "Dikoreksi oleh … pada …". Data asli dari mesin tetap bisa dilihat dari tooltip.
- Baris di periode yang sudah final menampilkan ikon `Lock`, dan aksi edit disembunyikan.
- Baris scan baru di monitoring real-time berlatar `gold-100` selama 2 detik, lalu memudar.
- Kolom total (penggajian) berada di baris paling bawah, lengket, dan tebal (Poppins 600).
- Pagination di bawah tabel: "1–25 dari 312", ukuran halaman 25/50/100. Rekap bulanan tidak dipaginasi (satu periode, satu layar).

**Tabel di HP (< 768px):** setiap baris menjadi kartu.

```
┌───────────────────────────────────┐
│ (BS) Budi Santoso      [✓ Tepat]  │  ← nama + badge status
│ Kantor · Cabang A                 │  ← info sekunder (small)
│ Masuk 07:58   Pulang 17:02   9j4m │  ← angka penting, tabular
└───────────────────────────────────┘
```

Tabel padat (rekap bulanan, penggajian) di HP menampilkan ringkasan per karyawan, lalu detailnya dibuka di halaman tersendiri.

**Contoh kolom per tabel:**

| Tabel | Kolom (urutan) | Kerapatan |
| --- | --- | --- |
| Karyawan | Nama + kategori · Cabang · Username · Status aktif · Aksi | Nyaman |
| Monitoring hari ini | Nama · Cabang · Masuk · Pulang · Terlambat · Lembur · Status | Nyaman |
| Rekap bulanan | Nama (lengket) · tanggal 1–31 (sel berwarna sesuai status) · Total hadir · Total terlambat · Total lembur | Padat |
| Penggajian | Nama (lengket) · Kategori · Gaji pokok · Upah harian disetujui · Lembur · Kasbon · BPJS · **Total** | Padat |
| Audit log | Waktu · Pengguna · Aksi · Data · Sebelum → Sesudah | Padat |

Sel tanggal di rekap bulanan berupa kotak 28px berisi satu huruf dengan warna status: **H** hadir, **T** terlambat, **L** tidak lengkap, **A** tidak hadir, **I** izin/cuti, dan **–** libur. Legenda selalu tampil di atas tabel.

### 6.5 Filter bar

- Ditaruh di atas tabel: cabang, kategori, periode/tanggal, dan pencarian nama.
- Di HP, filter dilipat menjadi satu tombol "Filter (2)" yang membuka bottom sheet.
- Filter aktif tampil sebagai chip yang bisa dihapus satu per satu.

### 6.6 Badge status

Bentuk pill, latar lembut + teks gelap + ikon, Poppins 11 / 600. Istilah mengikuti [aturan-bisnis.md](aturan-bisnis.md) dan [alur.md](alur.md).

| Konteks | Status | Warna | Ikon |
| --- | --- | --- | --- |
| Kehadiran harian | Tepat waktu | success | `CheckCircle2` |
| | Terlambat (+ menit) | warning | `Clock` |
| | Tidak lengkap, menunggu koreksi | danger + strip hazard di kartu/baris | `AlertTriangle` |
| | Tidak hadir | danger | `XCircle` |
| | Izin/cuti | info | `CalendarOff` |
| | Libur | netral (`steel-100` / `muted-foreground`) | — |
| | Lembur (+ durasi) | info | `Moon` |
| | Di bawah target jam | warning, model garis tepi (outline) | `TrendingDown` |
| | Dikoreksi | netral, model garis tepi | `Pencil` |
| Upah harian | Diusulkan | netral | — |
| | Disetujui | success | `CheckCircle2` |
| | Tidak disetujui | danger | `XCircle` |
| Izin/cuti | Draft | netral | `FilePen` |
| | Perlu ditinjau HR / Owner | warning | `Clock` |
| | Disetujui | success | `CheckCircle2` |
| | Ditolak | danger | `XCircle` |
| | Dibatalkan | netral, teks dicoret | `Ban` |
| Periode gaji | Draf | netral | — |
| | Final | solid `steel-800`, teks putih | `Lock` |
| Mesin | Online | titik hijau + "Online · 2 menit lalu" | `Wifi` |
| | Offline | danger + strip hazard | `WifiOff` |
| Data master | Aktif / Nonaktif | success / netral | — |

### 6.7 Alert dan peringatan

| Jenis | Tampilan | Contoh |
| --- | --- | --- |
| Info | Latar `info-bg`, ikon `Info` | "Aturan baru berlaku mulai 1 Nov 2026." |
| Berhasil | Toast hijau, hilang setelah 4 detik | "Koreksi tersimpan." |
| Error | Latar `danger-bg`, tetap tampil sampai ditutup | "Gagal menyimpan. Periode ini sudah final." |
| **Butuh tindakan** | Kartu dengan **strip hazard** 6px di atas (garis miring `gold-500`/`steel-950`), judul tebal, dan tombol aksi | "3 karyawan belum scan pulang kemarin. Koreksi sekarang" |

Strip hazard hanya dipakai untuk hal yang menunggu tindakan pengguna, agar tidak kehilangan makna.

### 6.8 Dialog, sheet, konfirmasi

- Desktop: dialog di tengah, lebar maksimal 520px (form panjang 720px), radius 16px.
- HP: bottom sheet dengan pegangan (handle) di atas, tombol aksi lengket di bawah.
- Aksi berisiko (hapus, tolak, finalisasi) wajib dikonfirmasi dengan menyebut akibatnya: "Finalisasi periode Oktober 2026? Setelah final, absensi dan gaji periode ini tidak bisa diubah."
- **Pemilih tanggal & jam** (`components/DatePickers.tsx`, mengacu pada referensi booking sheet dari video @dailyflutterui). Semua input tanggal/jam memakai komponen ini, bukan input bawaan browser. Field berupa tombol (ikon + "Sab, 24 Okt 2026" / "14.05" + chevron) yang membuka sheet berisi judul, konteks (label field), dan chip ringkasan navy di kanan atas.
  - `DatePicker`: kalender bulanan (Senin di awal, tanggal terpilih lingkaran `steel-800`) lalu tombol "Pilih". Judul bulan bisa diketuk untuk lompat ke tahun lalu bulan (tanggal lahir).
  - `TimePicker`: roda jam dan menit format 24 jam dengan pita sorotan, lalu "Pilih".
  - `DateTimePicker`: dua langkah (kalender → "Lanjut · Pilih jam" → roda jam), indikator titik Tanggal–Jam, tombol kembali kotak bergaris dan "Konfirmasi". `withSeconds` menambah roda detik ("Terima scan sejak").
  - Batas `min`/`max` menonaktifkan tanggal, bulan, dan tahun di luar rentang. Filter Dari–Sampai saling membatasi. Field opsional mendapat tombol "Kosongkan".
  - `FormDialog` otomatis memakai pemilih ini untuk field `date`, `time`, dan `datetime-local`. Nilai yang dikirim ke API tetap sama dengan input bawaan.
- **Form panjang bergaya sheet** (`EmployeeFormDialog`, K-136) memakai bahasa visual yang sama: pegangan di HP, judul + konteks, chip navy (No. karyawan), dan tombol aksi `primaryButton`/`outlineButton` dari `DatePickers`.
  - Isian dikelompokkan dalam bagian berjudul ikon + teks (bukan label kapital), dua kolom di desktop.
  - Nilai yang terisi otomatis ditampilkan sebagai kartu ringkasan (`rounded-xl`, latar `secondary`) dengan tombol "Ubah". Isian opsional dilipat di balik tombol bagian dengan chevron.
  - Pilihan 2–3 opsi memakai tombol berjejer (radio asli; terpilih = `steel-800`), bukan dropdown.

### 6.9 Navigasi

- **Sidebar desktop:** latar `steel-800`, motif garis vertikal tipis (opacity 6%) di bagian bawah, logo putih di atas. Item: ikon 20px + label Open Sans 14. Item aktif: latar `steel-700`, teks putih, garis kiri 3px `gold-500`. Hover: latar `steel-700/60%`.
- Menu dikelompokkan: **Operasional** (Dashboard, Absensi, Izin & cuti), **Keuangan** (Penggajian, Laporan), **Master data** (Karyawan, Cabang, Kategori, Mesin, Akun), **Sistem** (Pengaturan Aturan, Audit log). Menu yang tidak boleh diakses role tidak ditampilkan.
- **Header:** putih, garis bawah 1px. Kiri: judul halaman + breadcrumb. Kanan: nama, role, menu akun (Ganti password, Keluar). Garis emas 2px di bagian paling atas aplikasi.
- **Bottom navigation (HP):** putih, ikon 24px + label 11px, item aktif `steel-600` dengan titik emas di atas ikon.

### 6.9a Garis waktu status izin dan langkah form

**Garis waktu** (`ApprovalTimeline`, K-113): daftar vertikal Diajukan, Ditinjau HR, Ditinjau Owner (hanya untuk jenis persetujuan ganda), lalu hasil akhir. Setiap langkah punya penanda bulat 32px berisi ikon, sehingga status tidak hanya mengandalkan warna.

| Keadaan langkah | Penanda |
| --- | --- |
| Selesai | success, `Check`; detail "Disetujui oleh ... · waktu · cara (web/WhatsApp/lisan/tertulis)", plus "dicatat oleh ..." bila keputusan Owner dicatat HR |
| Ditolak | danger, `X` |
| Sedang ditinjau | warning, `Clock`, teks "Sedang ditinjau" |
| Akan datang | netral, `Circle`, teks redup |
| Dilewati (rantai berhenti karena ditolak atau dibatalkan) | netral, `Minus`, teks redup "Dilewati" |
| Dibatalkan | netral, `Ban`, alasan di bawahnya |

**Langkah form** (wizard pengajuan `/izin-saya/baru`, 4 langkah): indikator berupa bilah tipis dan nomor bulat per langkah, langkah aktif ditandai `aria-current="step"`. Di HP hanya label langkah aktif yang tampil. Langkah: Jenis, Tanggal & isian, Lampiran & tanda tangan, Pratinjau. Transisi antar langkah 200 ms (geser + pudar), statis saat `prefers-reduced-motion`. Draft tersimpan setiap Lanjut/Kembali; bila simpan gagal, Kembali tetap berpindah langkah dan galat tampil di langkah sebelumnya. Draft tanpa tanggal menampilkan "Tanggal belum diisi", bukan "0 hari".

**Halaman publik** (`/verifikasi/:token`): tanpa sidebar atau navigasi bawah; hanya wordmark, tombol tema, judul "Verifikasi Surat", dan satu kartu kaca berisi nomor, lencana status besar, identitas, dan daftar persetujuan. Mendukung mode terang dan gelap.

### 6.10 KPI tile (dashboard)

- Gaya aplikasi HP: di atas ada sapaan ("Selamat pagi, Owner") dan tanggal hari ini, lalu badge "Langsung" (titik berdenyut, mati bila `prefers-reduced-motion`).
- Satu kartu utama "Hadir hari ini" berukuran 2 kolom, berlatar gradasi `steel-600` → `steel-900` dengan garis emas di atas, motif spandek samar, angka besar "hadir / total", dan cincin persentase emas. Di bagian bawahnya ada strip tiga angka: Izin/cuti, Belum hadir, Belum lengkap.
- Kartu lain (Terlambat, Kandidat lembur) memakai kaca dengan ikon dalam lingkaran berwarna semantik, angka besar, label, dan keterangan.
- Kartu dan daftar dashboard memakai radius 16px (`rounded-2xl`), chip dan badge memakai pill. Judul section ditulis di luar kartu.
- "Perlu perhatian" hanya menampilkan butir yang nilainya lebih dari 0. Bila semuanya 0, yang tampil adalah "Semua beres".
- Scan terbaru ditampilkan dengan avatar inisial (ikon sidik jari untuk PIN tak dikenal) dan jam dalam bentuk pill.
- Kartu bisa diklik dan membuka daftar terkait.
- Grid dashboard wajib memakai kolom `minmax(0, 1fr)` (`grid-cols-1`/`grid-cols-2`) dan `min-w-0` agar isi panjang tidak melebarkan halaman di HP.

### 6.11 Status mesin

- Daftar per cabang: nama cabang, titik status, dan "terakhir terhubung" dalam waktu relatif ("2 menit lalu") dengan waktu absolut di tooltip.
- Mesin offline naik ke urutan paling atas dan memakai strip hazard.
- Layar pantau (mode TV) memakai gaya **Blueprint**: latar `steel-950` dengan grid `steel-400` 12%, angka besar `gold-500`, dan font besar agar terbaca dari jauh. Halaman ini tidak memakai sidebar.

### 6.12 Format data

| Data | Format | Contoh |
| --- | --- | --- |
| Uang | `Intl.NumberFormat('id-ID')` dengan prefiks "Rp", tanpa desimal kecuali aturan pembulatan menyatakan lain | Rp3.140.000 |
| Tanggal | Hari singkat + tanggal + bulan singkat + tahun | Sen, 12 Okt 2026 |
| Jam | 24 jam, tanpa detik di tabel, dengan detik di detail scan | 07:58 / 07:58:12 |
| Durasi | Jam dan menit | 9j 4m |
| Terlambat / lembur | Menit jika < 60, selain itu jam dan menit | +14m / 1j 20m |
| Kosong | Tanda pisah panjang, warna `muted-foreground` | — |

Zona waktu mengikuti konfigurasi server, bukan zona perangkat pengguna.

### 6.13 Empty, loading, error

- **Kosong:** ilustrasi flat kecil (motif garis spandek), satu kalimat penjelasan, dan satu aksi. Contoh: "Belum ada karyawan. Tambah karyawan pertama."
- **Loading:** skeleton sesuai bentuk konten (baris tabel, kartu). Spinner hanya di dalam tombol yang sedang memproses.
- **Error memuat:** pesan + tombol "Coba lagi". Data lama tetap tampil bila ada.
- **Offline (browser):** banner di atas: "Koneksi terputus. Data yang tampil mungkin belum terbaru."

## 7. Halaman per halaman

| Halaman | Gaya | Susunan |
| --- | --- | --- |
| Login | Steel Premium + kartu glass | Latar gradasi `steel-700` → `steel-950` dengan motif spandek dan garis emas. Kartu putih semi-transparan berisi logo, username, password, dan tombol pill. Di HP kartu memenuhi lebar layar |
| Ganti password wajib | Corporate Clean | Kartu tunggal di tengah, alasan ditampilkan di atas form |
| Dashboard | Bento, gaya aplikasi HP | Sapaan + tanggal. Baris 1: kartu utama Hadir (2 kolom) + Terlambat + Kandidat lembur. Baris 2: scan terbaru (7 kolom) di kiri; Perlu perhatian, Status mesin, dan Per cabang (5 kolom) di kanan. HP: 1 kolom dengan Perlu perhatian sebelum Scan terbaru (§6.10) |
| Monitoring absensi | Corporate Clean, tabel nyaman | Filter cabang + tanggal, tabel real-time, sorotan baris baru |
| Rekap bulanan | Enterprise, tabel padat | Matriks karyawan × tanggal + legenda + total |
| Koreksi scan | Corporate Clean, dialog | Kiri: scan asli dari mesin (hanya baca, latar `steel-50`). Kanan: nilai koreksi + alasan (wajib). Perbedaan disorot |
| Izin & cuti | Corporate Clean | Tab Menunggu / Disetujui / Ditolak, pratinjau dokumen di sheet samping |
| Penggajian | Enterprise, tabel padat | Header periode (badge Draf/Final), tabel per karyawan, baris total lengket, tombol emas "Finalisasi periode" |
| Slip gaji / ekspor PDF | Monokrom Biru | Hanya skala steel, tanpa latar penuh. Logo + garis emas tipis di atas |
| Laporan | Corporate Clean | Pilih jenis laporan → filter → pratinjau tabel → Ekspor Excel/PDF |
| Master data | Corporate Clean, `CrudPage` | Tabel nyaman + dialog form |
| Pengaturan Aturan | Corporate Clean | Riwayat versi aturan (timeline "berlaku mulai") di kiri, form versi baru di kanan. Versi yang dipakai periode final diberi ikon `Lock` |
| Audit log | Enterprise, tabel padat | Filter pengguna/aksi/tanggal, kolom "Sebelum → Sesudah" dengan perbedaan disorot |
| Area karyawan (HP) | Corporate Clean, ukuran besar | Beranda `/beranda`: sapaan, kartu Hari ini, pengajuan berjalan (maks. 3), tombol "Ajukan izin", ringkasan bulan ini. Izin: tab Berjalan/Draft/Selesai. Rekan: kartu ringkas + "Cari nama" di bawah `md` |
| Layar pantau mesin | Blueprint | Grid per cabang, selalu gelap, tanpa sidebar |

## 8. Motif brand

| Motif | Cara membuat | Dipakai di | Batas |
| --- | --- | --- | --- |
| Garis spandek | `repeating-linear-gradient(90deg, steel 0 4px, transparent 4px 12px)` dengan opacity 6–14% dan mask gradasi | Sidebar, login, header laporan, empty state | Tidak dipakai di belakang teks atau tabel |
| Garis emas | Garis 2–4px `gold-500` | Bagian atas aplikasi, item aktif sidebar, kop slip gaji | Satu garis per area |
| Strip hazard | `repeating-linear-gradient(-45deg, gold-500 0 8px, steel-950 8px 16px)` setinggi 6px | Kartu "butuh tindakan", mesin offline | Hanya untuk status yang menunggu tindakan |
| Chevron atap | Ambil dari file logo SVG | Favicon, splash, kop PDF | Tidak dibuat ulang manual, tunggu file asli |

## 9. Mode gelap

- Mengikuti pengaturan sistem perangkat, dengan tombol tema di header (Terang / Gelap / Ikut perangkat), disimpan di browser (K-085).
- Latar `steel-950` dengan cahaya biru/emas, kaca navy `steel-900` 55–75%, border putih 10%. Input tetap solid.
- Primary di mode gelap `#5B9AEE` dengan teks `steel-950` (kontras 6,7:1). Teks putih di atas biru terang tidak memenuhi standar kontras.
- `gold-500` boleh dipakai sebagai teks di mode gelap (kontras 12,7:1).
- Grid blueprint hanya di latar dashboard dan layar pantau, tidak di belakang tabel.
- Slip gaji dan PDF selalu memakai versi terang.

## 10. Aksesibilitas

- Kontras teks minimal 4,5:1 (WCAG AA). Teks besar dan ikon minimal 3:1.
- Fokus keyboard selalu terlihat: ring 3px `steel-600` (gelap: `#5B9AEE`).
- Area sentuh di HP minimal 44×44px (area karyawan 48px).
- Status selalu berupa warna + ikon + teks.
- Tabel memakai `<th scope>` yang benar. Kolom lengket tidak menutupi fokus.
- Animasi dimatikan saat `prefers-reduced-motion: reduce`.
- `lang="id"` di `index.html`.

## 11. Bahasa di antarmuka

- Bahasa Indonesia baku yang singkat. Nama menu dan istilah mengikuti dokumen bisnis: "Pengaturan Aturan", "upah harian", "tidak lengkap", "finalisasi".
- Tombol menyebut tindakannya: "Simpan koreksi", "Setujui upah", "Finalisasi periode".
- Pesan error menyebut masalah dan cara memperbaikinya, tanpa kata "maaf" dan tanpa kode teknis.
- Gunakan sebutan "karyawan", bukan "user" atau "pegawai".

## 12. Gaya yang tidak dipakai

| Gaya | Alasan |
| --- | --- |
| Liquid Glass | Efek pembiasan berat dan tidak menambah keterbacaan. Glassmorphism biasa dipakai sejak K-085 dengan pengaman kontras dan performa |
| Neumorphism | Kontras rendah, tombol sulit dibedakan dari kartu |
| Claymorphism | Terlalu ceria untuk karakter industri baja |
| Neubrutalism | Melelahkan untuk kerja harian. Fungsi penandanya sudah digantikan strip hazard |
| Skeuomorphism (brushed metal) | Cocok dengan tema baja, tetapi terasa kuno jika dipakai luas. Hanya inspirasi untuk login |
| Memphis, Y2K, Retro, Editorial, Organic, Pixel, Cyberpunk, Terminal, HUD | Keluar dari warna brand atau tidak cocok untuk data gaji |

## 13. Pertanyaan terbuka

- File logo vektor (SVG) dan versi putihnya, untuk sidebar, login, favicon, dan kop PDF. Situs hanya menyediakan WebP.
- Apakah klien punya panduan brand resmi (brand guideline) selain situs?
- Nama aplikasi yang tampil di header dan judul tab (sekarang "Biometric Attendance System").
- Apakah mode gelap dibutuhkan di versi pertama?

Pertanyaan ini juga dicatat di [pertanyaan-terbuka.md](pertanyaan-terbuka.md).

## 14. Penerapan

**Sudah diterapkan (2026-10-10)** ke semua halaman Fase 1:

- Token warna, font Poppins + Open Sans (subset latin, `@fontsource`), radius, utilitas `motif-spandek`, `strip-hazard`, dan `label-caps`.
- Kerangka aplikasi: sidebar navy (penuh di desktop, ringkas di tablet), bottom navigation + menu "Lainnya" di HP.
- `CrudPage`: tabel kerapatan nyaman di tablet/desktop, daftar kartu di HP, empty state, dan skeleton.
- `FormDialog`: bottom sheet di HP, label "(wajib)", dan error per field dengan ikon.
- Login (Steel Premium + kartu glass), Ganti password, Dashboard (Bento), Profil, dan Audit log (tabel padat).

**Belum diterapkan:** mode gelap (token sudah ada, tombol pilihannya belum), header dan kolom lengket pada tabel padat, logo resmi (masih tanda sementara di `components/Brand.tsx`), dan komponen untuk fase berikutnya (badge status absensi, rekap bulanan, penggajian, strip hazard pada peringatan).

**File yang berubah:**

| File | Perubahan |
| --- | --- |
| `frontend/src/index.css` | Token warna, font, radius, dan pemetaan `@theme inline` |
| `frontend/src/main.tsx` | Impor font `@fontsource` |
| `frontend/package.json` | Tambah `@fontsource/poppins` dan `@fontsource/open-sans` |
| `frontend/index.html` | `lang="id"`, judul, favicon |
| `frontend/src/components/AppLayout.tsx` | Sidebar navy, sidebar ringkas di tablet, bottom navigation di HP |
| `frontend/src/components/CrudPage.tsx` | Tabel → daftar kartu di HP, varian kerapatan |
| `frontend/src/components/ui/button.tsx`, `badge.tsx` | Perlu diubah: radius pill dan varian status. Ini pengecualian yang disengaja dari aturan "jangan edit `components/ui`" |

**Pemetaan token shadcn:** `--accent` di shadcn dipakai untuk latar **hover** (lihat `button.tsx` varian `outline`/`ghost`), jadi **tidak** diisi warna emas. Emas punya token sendiri (`--gold`).

```css
/* frontend/src/index.css (cuplikan) */
:root {
  --radius: 0.75rem;

  --background: #F4F7FB;
  --foreground: #0F1F3A;
  --card: #FFFFFF;
  --card-foreground: #0F1F3A;
  --popover: #FFFFFF;
  --popover-foreground: #0F1F3A;
  --primary: #104FA6;
  --primary-foreground: #EEEEEE;
  --secondary: #E2EAF5;
  --secondary-foreground: #0D3D85;
  --muted: #F4F7FB;
  --muted-foreground: #4A5B78;
  --accent: #E2EAF5;            /* hover, bukan emas */
  --accent-foreground: #0D3D85;
  --destructive: #B91C1C;
  --border: #D8E1EE;
  --input: #C3D2EA;
  --ring: #104FA6;

  --gold: #F5CE02;
  --gold-foreground: #163260;
  --sidebar: #163260;
  --success: #15803D;  --success-bg: #DCFCE7;
  --warning: #92400E;  --warning-bg: #FEF3C7;
  --danger:  #B91C1C;  --danger-bg:  #FEE2E2;
  --info:    #0D3D85;  --info-bg:    #E2EAF5;
}

.dark {
  --background: #050D1A;
  --foreground: #E6EDF7;
  --card: #0F2240;
  --card-foreground: #E6EDF7;
  --popover: #0F2240;
  --popover-foreground: #E6EDF7;
  --primary: #5B9AEE;
  --primary-foreground: #050D1A;
  --secondary: #16294A;
  --secondary-foreground: #E6EDF7;
  --muted: #0F2240;
  --muted-foreground: #9FB0CB;
  --accent: #16294A;
  --accent-foreground: #E6EDF7;
  --destructive: #F87171;
  --border: #1F3355;
  --input: #1F3355;
  --ring: #5B9AEE;

  --sidebar: #050D1A;
  --success: #4ADE80;  --success-bg: #0F2E1C;
  --warning: #FCD34D;  --warning-bg: #33270A;
  --danger:  #FCA5A5;  --danger-bg:  #3A1414;
  --info:    #8DB4F0;  --info-bg:    #16294A;
}

@theme inline {
  --font-sans: "Open Sans", system-ui, sans-serif;
  --font-heading: "Poppins", system-ui, sans-serif;
  --color-gold: var(--gold);
  --color-gold-foreground: var(--gold-foreground);
  --color-sidebar: var(--sidebar);
  --color-success: var(--success);
  --color-success-bg: var(--success-bg);
  --color-warning: var(--warning);
  --color-warning-bg: var(--warning-bg);
  --color-danger: var(--danger);
  --color-danger-bg: var(--danger-bg);
  --color-info: var(--info);
  --color-info-bg: var(--info-bg);
  /* ditambah pemetaan token shadcn yang sudah ada */
}
```

**Kriteria selesai:** semua halaman yang ada lolos pengecekan di lebar 375px, 768px, dan 1280px tanpa scroll horizontal di level halaman, kontras lolos AA, dan test frontend yang ada tetap lulus.
