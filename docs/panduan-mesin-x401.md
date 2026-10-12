# Panduan Uji Mesin Fingerprint Solution X401

**Status: draft 2026-10-12.** Mesin X401 dipakai sebagai **mesin uji sementara** sebelum alat rakitan selesai (K-054, K-055). Tujuannya agar alur scan → server → rekap → dashboard sudah teruji dengan mesin sungguhan, sehingga saat alat rakitan jadi, perubahan di web minimal.

> **Penting.** Kemampuan X401 di dokumen ini berasal dari sumber sekunder (listing toko dan blog support Solution), **bukan manual resmi**. Setiap kemampuan dianggap *belum terbukti* sampai terlihat di log tangkapan (Tahap 3). Hasil pengamatan dicatat di §7 dan Q-029.

## Ringkasan tahap

| Tahap | Isi | Bisa dikerjakan sekarang? |
|---|---|---|
| 1 | Cek kemampuan mesin (menu ADMS, firmware, nomor seri) | Ya |
| 2 | Sambungkan mesin dan laptop di satu jaringan | Ya |
| 3 | Tangkap request asli mesin dengan `tools/x401-capture` | Ya |
| 4 | Siapkan data uji di mesin (user + sidik jari) | Ya |
| 5 | Arahkan mesin ke backend sungguhan dan uji end-to-end | **Setelah Fase 3** (adapter ZK-Push) selesai |
| 6 | Skenario uji: kirim ulang, offline, scan ganda, jam mesin salah | Setelah Fase 3 |

Urutan praktis: kerjakan Tahap 1 → 2 → 4 → 3 (scan di Tahap 3 butuh user yang sudah terdaftar dari Tahap 4).

---

## Tahap 1 — Cek kemampuan mesin

**Sebelum mengubah apa pun, foto semua pengaturan awal** (mesin milik dosen dan harus bisa dikembalikan seperti semula).

> **Mesin uji ini sudah berisi data orang lain** (183 user, 512 sidik jari, 16.949 log absensi, lihat §7). Mengaktifkan ADMS bisa membuat mesin mengirim riwayat absensi lama ke server, dan menurut laporan pengguna lain, bisa mematikan mode tarik data (software absensi yang mungkin dipakai dosen). **Minta izin dosen sebelum mengaktifkan ADMS.** Untuk mengurangi risiko, alat penangkap hanya meminta data absensi (bukan data user/sidik jari) dan hanya mencatat 3 baris pertama setiap kiriman.

Nama menu bisa sedikit berbeda antar firmware. Masuk menu dengan tombol **M/OK** (login sebagai admin jika diminta).

1. **Info perangkat** — biasanya `Menu → System Info / Info Sistem → Device Info`. Catat:
   - Nomor seri (**Serial Number / SN**). Ini identitas mesin di server (`devices.serial_number`).
   - Versi firmware (**Firmware Version**) dan platform.
   - Kapasitas user, sidik jari, dan log yang terpakai.
2. **Menu komunikasi** — biasanya `Menu → Comm. / Komunikasi`. Cari apakah ada:
   - **Ethernet / Network** (IP Address, Subnet Mask, Gateway, DHCP).
   - **ADMS** atau **Cloud Server Setting** (Server Address, Server Port, Enable Domain Name, Proxy).
   - **Wi-Fi** (kemungkinan tidak ada di X401).
3. Foto layar setiap menu tersebut dan simpan sebagai bukti.

**Keputusan setelah Tahap 1:**
- Ada menu ADMS/Cloud Server → lanjut dengan mode push (rancangan utama).
- Tidak ada menu ADMS → berhenti di sini dan laporkan. Mesin hanya bisa ditarik datanya (pull lewat SDK/port 4370), yang tidak sesuai arsitektur kita (K-018, K-020). Rancangan perlu didiskusikan ulang.

## Tahap 2 — Sambungkan ke jaringan

Mesin dan laptop yang menjalankan backend harus **berada di satu jaringan**, dan mesin harus bisa membuka koneksi ke laptop.

### Pilihan A (disarankan): router rumah/kos atau switch sendiri

1. Colok kabel LAN mesin ke router yang juga dipakai laptop (laptop boleh via Wi-Fi atau kabel ke router yang sama).
2. Di mesin: `Comm. → Ethernet`, isi IP statis di subnet router, misalnya:
   - IP Address: `192.168.1.201`
   - Subnet Mask: `255.255.255.0`
   - Gateway: `192.168.1.1` (alamat router)
   - Atau aktifkan DHCP lalu catat IP yang didapat.
3. Pastikan IP itu tidak dipakai perangkat lain.

### Pilihan B: kabel langsung laptop ↔ mesin (dipakai saat ini)

Kondisi 2026-10-12: kabel sudah tersambung langsung, laptop `172.35.3.50` (mask `255.255.248.0`), mesin `172.35.3.102`. Keduanya sudah satu jaringan, jadi IP tidak perlu diubah. Langkah di bawah hanya untuk menyiapkan dari awal.

1. Colok kabel LAN langsung dari mesin ke port LAN laptop.
2. Laptop (Windows): `Settings → Network & internet → Ethernet → IP assignment → Edit → Manual`, IPv4 `192.168.10.1`, mask `255.255.255.0`, gateway kosong.
3. Mesin: IP `192.168.10.201`, mask `255.255.255.0`, gateway `192.168.10.1`.

### Hindari: jaringan kampus/kantor

Jaringan kampus sering memblokir koneksi antar-perangkat (client isolation) dan port selain 80/443. Kalau mesin tidak bisa dijangkau di jaringan itu, pakai pilihan A atau B.

### Cek koneksi dari laptop

```powershell
ipconfig                       # cari IPv4 laptop di jaringan yang sama dengan mesin
ping 192.168.1.201             # ganti dengan IP mesin
```

Ping harus dibalas. Jika X401 punya web server, membuka `http://<IP-mesin>` di browser juga menampilkan halaman login mesin.

### Buka firewall Windows untuk port penangkap dan backend

Jalankan **PowerShell sebagai Administrator** satu kali:

```powershell
New-NetFirewallRule -DisplayName "Uji X401 (ADMS)" -Direction Inbound -Protocol TCP -LocalPort 8080,8081 -Action Allow -Profile Private
```

Pastikan profil jaringan yang dipakai adalah **Private**, bukan Public (`Settings → Network & internet → <jaringan> → Network profile type`). Hapus aturan ini setelah selesai uji:

```powershell
Remove-NetFirewallRule -DisplayName "Uji X401 (ADMS)"
```

## Tahap 3 — Tangkap request asli mesin

Sebelum adapter backend dibuat, kita lihat dulu **apa yang benar-benar dikirim mesin**. Alat `tools/x401-capture/capture.mjs` (Node, tanpa dependency) mencatat setiap request ke file log.

1. Jalankan di laptop, dari folder repo:

   ```powershell
   node tools/x401-capture/capture.mjs --port 8081
   ```

   Alat menampilkan IP laptop dan lokasi log (`tools/x401-capture/logs/`, tidak masuk git).

2. Di mesin: `Comm. → ADMS / Cloud Server Setting`:
   - Server Address: IP laptop di jaringan yang sama dengan mesin (misalnya `192.168.1.11`)
   - Server Port: `8081`
   - Enable Domain Name: **Off**
   - Proxy: **Off**
3. Simpan, lalu tunggu ±1 menit. Banyak firmware perlu restart agar pengaturan ADMS aktif. Ikon koneksi server di layar mesin biasanya berubah ketika tersambung.
4. Lakukan 1–2 scan sidik jari dan amati log.

**Yang diharapkan muncul di log** (pola umum protokol push ZKTeco, *belum terbukti untuk X401*):

| Request | Arti |
|---|---|
| `GET /iclock/cdata?SN=...&options=all...` | Handshake: mesin meminta konfigurasi. |
| `GET /iclock/getrequest?SN=...` | Heartbeat berkala; server bisa menitipkan perintah. |
| `POST /iclock/cdata?SN=...&table=ATTLOG&Stamp=...` | Data scan. Body per baris dipisah tab: PIN, waktu, status, metode verifikasi, ... |
| `POST /iclock/cdata?SN=...&table=OPERLOG...` | Log operasi (user baru, sidik jari didaftarkan, dll.). |
| `POST /iclock/devicecmd?SN=...` | Hasil perintah dari server. |

Kalau log tetap kosong: cek ulang IP dan port di mesin, firewall (Tahap 2), dan apakah mesin perlu restart.

**Mode aman:** secara bawaan alat ini **tidak** membalas `OK` untuk data scan (membalas HTTP 503). Mesin menganggap data belum terkirim dan menyimpannya untuk dikirim ulang ke server sungguhan nanti. Jadi tidak ada scan yang "hilang" selama penangkapan. Opsi `--ack` hanya dipakai bila sengaja ingin mengamati perilaku setelah konfirmasi.

**Catat dari log** (isi ke §7):
- Path dan parameter persis (`SN`, `pushver`, `table`, `Stamp`, dll.).
- Format baris ATTLOG: urutan kolom, format tanggal, apakah ada zona waktu.
- Seberapa sering heartbeat datang (dasar status online/offline).
- Apakah mesin mengirim ulang setelah dibalas 503 (dasar uji kirim ulang).
- Apakah ada header atau parameter rahasia (untuk autentikasi mesin).

Kirim file log ke tim untuk dipakai menyusun adapter dan simulator di Fase 3.

## Tahap 4 — Siapkan data uji di mesin

1. Di mesin: `Menu → User Mgt. → New User`.
   - **User ID / PIN**: angka, misalnya `1`. PIN inilah yang dikirim mesin di setiap scan; di web nanti PIN dipetakan ke karyawan (K-055, Q-031).
   - Daftarkan sidik jari (biasanya 2–3 kali tempel jari yang sama).
2. Pakai data uji, bukan data karyawan klien. Contoh: PIN `1` = "Budi Test" (karyawan `K-001` di database dev).
3. Atur tanggal dan jam mesin: `Menu → System → Date/Time`. Selisih jam mesin langsung memengaruhi keterlambatan; catat selisihnya terhadap jam laptop.

## Tahap 5 — Uji end-to-end dengan backend (setelah Fase 3)

Adapter ZK-Push sudah tersedia (Fase 3). Langkah:

1. Jalankan sistem: `docker compose up --build -d`. Backend mendengarkan di port `8080` dan, untuk mesin, di port **`8888`** (K-060, karena tombol 0 keypad rusak).
2. Login web sebagai Owner → **Mesin Absensi** → tambah mesin dengan **serial number persis** `BRYV181860187` dan cabang uji. Biarkan **Terima scan sejak** kosong agar riwayat lama mesin (data dosen) tidak disimpan (K-057). Bila mesin sudah mencoba terhubung sebelum didaftarkan, serial number-nya tampil di banner "Mesin belum terdaftar mencoba terhubung".
3. Di **Karyawan**, isi **PIN mesin** karyawan uji sama dengan User ID di mesin (mis. `9001`).
4. Di mesin: `Comm. → ADMS`, alamat server **`172.35.3.55`**, port **`8888`**. Restart mesin bila diminta.
5. Buka **Dashboard**: status mesin berubah "Online" dan badge "Langsung" menyala. Scan sidik jari: scan harus muncul di "Scan terbaru" dalam hitungan detik tanpa memuat ulang halaman.
6. Buka **Absensi Harian** → klik nama karyawan uji: jam masuk, keterlambatan, dan jam normal dihitung dengan aturan kerja yang berlaku (Fase 2). Scan dari PIN yang belum diisi tampil di **Scan Tak Dikenal**.

Tanpa mesin, alur yang sama bisa dicoba dengan simulator (protokol sama, data sintetis):

```powershell
node tools/device-simulator/simulate.mjs handshake --sn SIM-001
node tools/device-simulator/simulate.mjs scan --sn SIM-001 --pin 9001
node tools/device-simulator/simulate.mjs offline --sn SIM-001 --pin 9001 --count 3
node tools/device-simulator/simulate.mjs duplicate --sn SIM-001 --pin 9001
node tools/device-simulator/simulate.mjs live --sn SIM-001 --pin 9001 --every 10
```

Mesin `SIM-001` harus didaftarkan dulu di halaman Mesin.

## Tahap 6 — Skenario uji dengan mesin sungguhan

| No | Skenario | Cara | Hasil yang diharapkan |
|---|---|---|---|
| X-01 | Scan normal | Scan 1 kali | 1 baris scan tersimpan, dashboard berubah dalam hitungan detik |
| X-02 | Mesin offline lalu online | Cabut kabel LAN, scan 3 kali, colok lagi | Ketiga scan tiba setelah tersambung, waktu scan sesuai waktu tempel jari (bukan waktu tiba) |
| X-03 | Server mati | Matikan backend (`docker compose stop backend`), scan, nyalakan lagi | Scan dikirim ulang oleh mesin dan tersimpan tepat 1 kali |
| X-04 | Kirim ganda | Scan berulang dalam 1 menit, ulangi X-03 | Tidak ada baris ganda untuk scan yang sama |
| X-05 | Mesin tidak terdaftar | Nonaktifkan mesin di web, lalu scan | Server menolak dan tidak membalas sukses; mesin menyimpan scan untuk dikirim ulang |
| X-06 | PIN belum dipetakan | Scan dengan user PIN `99` yang tidak terhubung ke karyawan | Scan tetap tersimpan (scan asli tidak dibuang) dan ditandai perlu ditinjau |
| X-07 | Jam mesin salah | Majukan jam mesin 1 jam, scan | Selisih terdeteksi dan diberi penanda (rincian menunggu Q-020/Q-022) |
| X-08 | Status mesin | Cabut kabel LAN dan tunggu | Status mesin berubah menjadi "Offline" setelah batas waktu heartbeat |

Hasil setiap skenario dicatat beserta tanggal dan versi firmware. Skenario yang sama juga diotomatiskan memakai simulator (K-008), karena mesin sungguhan tidak bisa dipakai di test otomatis.

## 7. Hasil pengamatan X401

Diisi setelah Tahap 1 dan Tahap 3. **Jangan diisi dengan tebakan.**

| Hal | Hasil | Tanggal | Bukti |
|---|---|---|---|
| Nomor seri | `BRYV181860187` | 2026-10-12 | foto SisInfo → Alat |
| Versi firmware | `Ver 6.5.4 (build 162)`, algoritma `Finger VX10.0`, vendor `solution`, dibuat 2018-05-07 | 2026-10-12 | foto SisInfo → Alat |
| MAC address | `00:17:61:20:03:9f` | 2026-10-12 | foto + tabel ARP laptop |
| Isi mesin saat dipinjam | 183 user, 512 sidik jari, 16.949 log absensi (bukan data proyek) | 2026-10-12 | foto SisInfo → Records |
| Ada menu ADMS / Cloud Server? | Ada; alamat server bisa berupa IP atau URL | 2026-10-12 | laporan pengguna |
| Jaringan | Kabel langsung laptop ↔ mesin; mesin `172.35.3.102`, laptop `172.35.3.50/21` (statis); link 100 Mbps | 2026-10-12 | ping sweep + ARP |
| Web server bawaan | Ada (`Server: ZK Web Server`, halaman login `/csl/login`) → mesin OEM ZKTeco | 2026-10-12 | `curl http://172.35.3.102/` |
| Port terbuka | 80 (web), 4370 (protokol pull ZK) | 2026-10-12 | uji koneksi TCP |
| Keypad | Tombol angka **0 rusak/tidak bisa ditekan**. Laptop diberi IP kedua tanpa angka 0, `172.35.3.55` (`netsh interface ipv4 add address name="Ethernet" address=172.35.3.55 mask=255.255.248.0`), dan alat penangkap memakai port `8888`. Backend di Fase 3 juga harus bisa dijangkau di port tanpa angka 0 | 2026-10-12 | laporan pengguna |
| Ada Wi-Fi? | | | foto menu |
| Protokol | Push ZKTeco, `pushver=2.32`, `User-Agent: iClock Proxy/1.09`, HTTP biasa | 2026-10-12 | log tangkapan |
| Handshake | `GET /iclock/cdata?SN=BRYV181860187&options=all&pushver=2.32&language=73` → dibalas teks opsi (`GET OPTION FROM: <SN>` …) | 2026-10-12 | log tangkapan |
| Heartbeat | `GET /iclock/getrequest?SN=<SN>`; heartbeat pertama membawa `INFO=<firmware>,<jml user>,<jml sidik jari>,<jml log>,<IP>,<versi algoritma>,...` (contoh: `Ver 6.5.4(build 162),183,512,16951,172.35.3.102,10,-1,0,0,101`) | 2026-10-12 | log tangkapan |
| Interval heartbeat | Teramati ±107 detik antar `getrequest` saat ada data gagal terkirim (opsi `Delay=10`). Perlu diamati lagi saat normal | 2026-10-12 | log tangkapan |
| Format baris ATTLOG | `POST /iclock/cdata?SN=<SN>&table=ATTLOG&Stamp=<angka>`; body teks, satu scan per baris, 4 kolom dipisah tab: `PIN`, `YYYY-MM-DD HH:MM:SS` (jam lokal mesin, **tanpa zona waktu**), `status`, `verifikasi` (`1` = sidik jari). Contoh sintetis: `9001\t2026-10-12 08:01:02\t0\t1` | 2026-10-12 | log tangkapan |
| Ukuran batch | Riwayat dikirim 200 baris per request (±6,6 KB); scan baru dikirim terpisah (1 baris) | 2026-10-12 | log tangkapan |
| PIN | Bisa sampai 8 digit (mis. format mirip NIM). Kolom PIN di server harus teks, bukan angka kecil | 2026-10-12 | log tangkapan |
| Riwayat lama | Begitu ADMS aktif dengan `ATTLOGStamp=None`, mesin langsung mengirim **seluruh riwayat** (mulai 2020) | 2026-10-12 | log tangkapan |
| Mengirim ulang setelah balasan gagal? | **Ya.** Setelah HTTP 503, mesin mencoba lagi 3× dalam ±1 detik, lalu menunggu ±100 detik dan mengulang. Data yang belum di-ACK tidak hilang | 2026-10-12 | log tangkapan |
| `Stamp` | Berbeda antara batch riwayat dan scan baru; **bukan ID per scan**. Kunci duplikat tetap SN + PIN + waktu (K-055) | 2026-10-12 | log tangkapan |
| Akurasi jam mesin | Scan 13:25:54 WIB tiba di laptop; jam mesin cocok dengan jam laptop (selisih < 1 menit) | 2026-10-12 | log tangkapan |
| Ada autentikasi/secret dari mesin? | **Tidak.** Hanya parameter `SN`; tidak ada header atau token rahasia | 2026-10-12 | log tangkapan |
| Mendukung HTTPS? | Belum diketahui; yang teramati HTTP biasa | | menu / log |

## 8. Batasan X401 terhadap aturan sistem

Hal berikut tercatat di K-055. Batasan ini hanya berlaku untuk mesin uji dan **tidak menurunkan** syarat untuk alat rakitan.

| Aturan sistem | Kemungkinan pada X401 | Penanganan sementara |
|---|---|---|
| ID unik per scan (K-020) | Protokol tidak mengirim ID scan | Kunci duplikat = nomor seri + PIN + waktu scan |
| Kredensial unik per mesin (arsitektur §10.3) | Mesin hanya dikenali dari nomor seri | Hanya nomor seri yang terdaftar dan aktif yang diterima; hanya untuk jaringan lokal |
| TLS di jaringan publik | Kemungkinan hanya HTTP | Uji hanya di jaringan lokal, tidak diekspos ke internet |
| Wi-Fi | Kemungkinan hanya LAN kabel | Tidak memengaruhi logika server |
| Waktu scan dengan zona waktu | Waktu lokal mesin tanpa zona | Ditafsirkan dengan zona waktu cabang mesin (`Asia/Jakarta`) |

## 9. Keselamatan data dan pengembalian mesin

- Jangan memakai sidik jari atau data karyawan klien di mesin uji.
- Sebelum mengembalikan mesin ke dosen, kembalikan pengaturan ADMS/Ethernet seperti foto awal, dan hapus user uji serta log bila diminta (`Menu → Data Mgt.`).
- File log tangkapan bisa berisi PIN dan waktu scan; jangan di-commit (`logs/` sudah ada di `.gitignore`).

## Sumber

- [Solution X401 — IndoTrading](https://en.indotrading.com/megabintangcctv/solution-x401-p248247.aspx)
- [SDK Solution Attendance Management — Solution Support](https://sol-itsupport.blogspot.com/p/solution-sdk.html)
- [Solution ADMS Server — Solution Support](https://sol-itsupport.blogspot.com/p/solution-adms-server.html)
