# Akun Karyawan dengan Pesan WhatsApp — Desain

Tanggal 2026-10-26. Status: menunggu persetujuan pengguna.

Desain ini menggantikan pendekatan impor karyawan dari Excel ([rencana 2026-10-26-profil-dan-impor-karyawan](../plans/2026-10-26-profil-dan-impor-karyawan.md), K-130). Fitur impor itu sudah diimplementasikan tetapi belum di-commit.

## 1. Maksud dan alasan

Client mengirim data 28 karyawan, tetapi pengguna tidak ingin memasukkannya lewat impor massal dari Excel. Karyawan akan dimasukkan satu per satu lewat form, sehingga form itu harus cepat dipakai:

- akun login dibuat dari form yang sama;
- kredensial langsung bisa dikirim ke karyawan lewat WhatsApp dengan pesan yang sudah jadi, mengikuti pola aplikasi lain milik pengguna.

Reset password memakai pola pesan yang sama.

Impor Excel yang sebenarnya dibutuhkan adalah untuk **rekap mesin absensi di cabang lain**, bukan data karyawan. Itu proyek terpisah yang menunggu format file dari client.

## 2. Tujuan

1. **Tambah karyawan sekaligus akun.** Form Tambah Karyawan punya kotak centang "Buatkan akun login". Bila dicentang, muncul dua field yang sudah terisi otomatis dan tetap bisa diubah:
   - **Username**, usulan `nama@takka.id`;
   - **Password sementara**, usulan `nama-ID-takka` (contoh `eni-006-takka`).

   Karyawan dan akunnya tersimpan bersama: keduanya berhasil atau tidak ada yang tersimpan.
2. **Pesan kredensial setelah akun dibuat.** Muncul jendela berisi pesan siap kirim dengan dua tombol:
   - **Salin pesan**: menyalin pesan ke clipboard.
   - **Kirim WA**: membuka WhatsApp di perangkat pengguna dengan pesan sudah terisi. Bila No. HP karyawan terisi, chat langsung menuju nomor itu. Bila kosong, pengguna memilih kontak sendiri.
3. **Reset password dengan pesan yang sama.** Password baru terisi usulan `nama-ID-takka` dan tetap bisa diubah. Setelah disimpan, muncul jendela pesan versi "password telah direset" dengan tombol Salin dan Kirim WA. Karyawan tetap wajib mengganti password saat login.
4. **Pesan yang sama juga muncul** saat membuat akun untuk karyawan yang sudah ada, baik dari menu Akun maupun dari tombol di detail karyawan.
5. **Form karyawan lebih ringkas.** Form tambah tidak memuat Tanggal masuk dan Tanggal keluar. Tanggal masuk otomatis diisi tanggal hari ini menurut zona waktu cabang penempatan.
6. **Finance & Accounting boleh menambah dan mengubah data karyawan**, termasuk cabang absen, serta membuat dan mereset akun karyawan. Aturan Kerja tetap hanya bisa diubah Owner (K-049).
7. **Fitur impor karyawan dari Excel dihapus seluruhnya.**

## 3. Bukan tujuan

- Impor rekap absensi dari mesin cabang lain. Ini proyek terpisah yang menunggu format.
- Mengirim WA otomatis dari server (WhatsApp API atau gateway). Pesan dikirim manual dari perangkat pengguna.
- Menampilkan ulang password lama. Password disimpan sebagai hash, jadi pesan hanya bisa dibuat **tepat setelah** akun dibuat atau password direset. Untuk mengirim ulang, lakukan reset lagi.
- Mengubah format username karyawan yang sudah punya akun.

## 4. Anti-tujuan (dianggap gagal walaupun fiturnya "jalan")

- Password tersimpan dalam bentuk yang bisa dibaca di database, log, audit log, atau localStorage.
- Karyawan tersimpan tanpa akun padahal "Buatkan akun login" dicentang, atau sebaliknya.
- Finance & Accounting bisa mengubah Aturan Kerja, atau membuat dan mereset akun Owner maupun akun Finance & Accounting lain.
- Tombol Kirim WA membuka nomor yang salah, misalnya `08…` dikirim tanpa diubah menjadi `628…`.

## 5. Keputusan pengguna

| # | Keputusan |
|---|---|
| 1 | Username `nama@takka.id` boleh pendek (`eni@takka.id`). Aturan minimal 8 karakter tidak berlaku untuk username. |
| 2 | Format password sementara `nama-ID-takka` dipakai untuk semua karyawan (opsi 3), sehingga panjangnya selalu ≥ 8. |
| 3 | Pesan WA mengikuti pola contoh dari pengguna, dengan sapaan dan penutup disesuaikan untuk Takka Steel (§6). |
| 4 | Link login di pesan = alamat aplikasi saat dipakai (produksi). |
| 5 | Finance & Accounting bisa menambah karyawan beserta akunnya, tetapi tidak bisa mengatur aturan kerja. |
| 6 | Tanggal masuk otomatis hari ini. Tanggal masuk dan keluar tidak diisi di form tambah. |
| 7 | Impor karyawan dari Excel dihapus. |

## 6. Isi pesan

Pesan memakai format WhatsApp (`*tebal*`, `_miring_`). Bagian `{…}` diisi oleh sistem.

**Akun baru**

```
*Halo Kak {nama lengkap}!*

Akun *{nama aplikasi}* Kakak sudah dibuat oleh Admin.

Berikut kredensial untuk masuk:
━━━━━━━━━━━━━━━━━━━━━
*Link Login*      : {origin}/login
*Username*        : {username}
*Password*        : {password}
━━━━━━━━━━━━━━━━━━━━━

*Catatan:*
Silakan login menggunakan password di atas, lalu Kakak akan diminta membuat password baru pilihan sendiri.

_Salam hangat,_
*Admin Takka Steel*
```

**Reset password.** Isinya sama dengan pesan akun baru, kecuali tiga hal:
- kalimat kedua menjadi "Password akun *{nama aplikasi}* Kakak telah direset oleh Admin.";
- "kredensial untuk masuk" menjadi "kredensial baru untuk masuk";
- label `*Password*` menjadi `*Password Baru*`.

Isian sistem *(semuanya keputusan Claude)*:
- `{nama aplikasi}` = "Sistem Absensi Takka Steel", disimpan sebagai satu konstanta di frontend. Nama aplikasi masih terbuka di Q-025.
- `{origin}` = `window.location.origin`. Di produksi nilainya sudah alamat aplikasi, jadi tidak perlu konfigurasi tambahan.
- `{nama lengkap}` = nama karyawan apa adanya. Untuk akun yang tidak terhubung ke karyawan (Owner, Finance & Accounting), dipakai username.

## 7. Perilaku rinci

### 7.1 Usulan username dan password

- **Username** diusulkan dari kata pertama nama, setelah melewati awalan `muhammad`, `muhamad`, `mohammad`, `mohamad`, `moh`, `muh`, `m`, `siti`. Hasilnya diubah ke huruf kecil `a-z0-9`, lalu ditambah `@takka.id`.
  - Contoh: Muhammad Dafa → `dafa@takka.id`, Siti Balqis → `balqis@takka.id`, M Rochmat → `rochmat@takka.id`.
  - Usulan dihitung **di frontend**, karena dipakai saat pengguna mengetik dan tidak butuh data server.
- **Password sementara** diusulkan dalam format `{bagian depan username}-{No. karyawan}-takka`.
- Kedua usulan ikut diperbarui saat Nama atau No. karyawan diketik, **selama field usulan itu belum diubah manual**.
- Server tetap memvalidasi:
  - pola username `[A-Za-z0-9._@-]{3,50}`;
  - username unik;
  - password 8–100 karakter.

  Username tidak wajib berakhiran `@takka.id`, supaya akun teknis seperti `owner` tetap sah.

### 7.2 Tambah karyawan dengan akun (backend)

- `POST /api/employees` menerima field opsional `account: { username, password, role }`.
- Bila `account` diisi, karyawan dan akun dibuat dalam **satu transaksi** lewat logika `UserAccountService` yang sudah ada, sehingga validasi, hash, `mustChangePassword = true`, dan audit tetap berlaku. Bila pembuatan akun gagal (misalnya username sudah dipakai → 409), karyawannya juga tidak tersimpan.
- `role` hanya boleh `EMPLOYEE` bila pemanggilnya Finance & Accounting. Owner boleh memilih role apa pun.
  - Pilihan role di form hanya tampil untuk Owner, dengan bawaan Karyawan.
  - Role Owner atau Finance & Accounting untuk karyawan tertentu (001, 003, 005) diatur Owner, di form ini atau di menu Akun.
- Respons tetap `EmployeeResponse`. Password **tidak** dikembalikan, karena frontend sudah memegangnya dari isian form.
- `joinDate` di `EmployeeRequest` menjadi opsional:
  - saat create, bila kosong, diisi tanggal hari ini menurut zona waktu cabang penempatan;
  - saat update, bila kosong, nilai lama dipertahankan.

### 7.3 Form karyawan (frontend)

- **Form tambah** tidak menampilkan Tanggal masuk dan Tanggal keluar. Kotak "Buatkan akun login" bawaannya tercentang. Setelah karyawan dengan akun tersimpan, jendela pesan (§7.5) terbuka.
- **PIN mesin** di form tambah terisi otomatis sama dengan No. karyawan (keputusan pengguna: PIN = ID) dan ikut berubah selama belum diubah manual, dengan cara yang sama seperti usulan username *(keputusan Claude: diisi di frontend, server tidak mengisi PIN otomatis)*.
- **Form ubah** tetap menampilkan **Tanggal keluar** dan **Aktif** untuk karyawan yang berhenti. Tanggal masuk tidak ditampilkan, tetapi nilainya dipertahankan. Bagian akun juga tidak ditampilkan *(keputusan Claude: akun karyawan yang sudah ada diurus di detail karyawan dan menu Akun)*.

### 7.4 Reset password dan buat akun untuk karyawan lama

- **Menu Akun:**
  - Dialog Reset password mengisi usulan password baru yang tetap bisa diubah. Setelah berhasil, muncul jendela pesan versi reset.
  - Membuat akun dari menu ini juga membuka jendela pesan.
- **Detail karyawan, bagian "Akun web":**
  - Tombol **Reset password** membuka dialog yang sama.
  - Bila karyawan belum punya akun, tombol **Buat akun** mengisi usulan username dan password, lalu membuka jendela pesan versi akun baru. Tombol ini menggantikan tautan "Buat di menu Akun".
- Untuk akun yang tidak terhubung ke karyawan, usulan password memakai format `{bagian depan username}-takka`, dan nomor WA dibiarkan kosong.

### 7.5 Jendela pesan

- Pesan ditampilkan di kotak teks hanya-baca, dengan tombol **Salin pesan** dan **Kirim WA**.
- **Salin pesan** memakai `navigator.clipboard.writeText`, lalu menampilkan konfirmasi "Pesan disalin". Bila clipboard tidak tersedia, teks dipilih otomatis agar bisa disalin manual.
- **Kirim WA** membuka `https://wa.me/{nomor}?text={pesan ter-encode}` di tab baru, atau `https://wa.me/?text=…` bila nomor kosong. Nomor diubah dulu ke format internasional:
  - buang semua karakter selain angka;
  - awalan `0` → `62`;
  - awalan `62` tetap;
  - awalan `8` → `628…`.
- Jendela menampilkan peringatan: "Password ini hanya ditampilkan sekali. Untuk mengirim ulang, reset password."
- Pesan dan password hanya ada di memori halaman. Keduanya tidak disimpan di localStorage dan tidak dikirim lagi ke server.

### 7.6 Hak akses (membatalkan sebagian K-128)

| Aksi | Owner | Finance & Accounting |
|---|---|---|
| Tambah/ubah karyawan, cabang absen | ✅ | ✅ (kembali seperti sebelum K-128) |
| Buat/reset akun Karyawan | ✅ | ✅ (sudah ada) |
| Buat/reset akun Owner atau Finance & Accounting | ✅ | ❌ (sudah ada) |
| Ubah Aturan Kerja | ✅ | ❌ (K-049, tetap) |

Celah yang diterima pengguna: Finance & Accounting memilih **kategori** karyawan, sehingga secara tidak langsung menentukan aturan kerja kategori mana yang berlaku. Aturan kerja per karyawan tetap hanya bisa diubah Owner.

### 7.7 Penghapusan impor Excel

**Dihapus:**
- `EmployeeImportParser`, `EmployeeImportService`, `EmployeeImportController`, `EmployeeImportDtos`, beserta test `EmployeeImportParserTests` dan `EmployeeImportTests`;
- dependency `poi-ooxml`;
- aturan `/api/employee-imports/**` di `SecurityConfig` dan barisnya di `AccessControlTests`;
- `EmployeeImportPage` beserta rutenya, tombol "Impor dari Excel", dan tipe `Import*`.

**Tetap dipakai:**
- V17 (kolom profil) dan `Gender`;
- regex username yang membolehkan `@`, beserta test-nya;
- label "Finance & Accounting";
- `ApiException.withProperty` dan `ApiError.problem` *(keputusan Claude: kecil dan berguna untuk keperluan umum; builder boleh mengembalikannya bila lebih suka)*.

Di dokumen, K-130 ditandai **dibatalkan** dan diganti keputusan baru. K-129, K-131, dan K-132 tetap berlaku; format password di K-131 diperbarui menjadi `nama-ID-takka`.

## 8. Batasan

- Fitur ini tidak melibatkan uang. Tanggal masuk dihitung dengan zona waktu cabang yang ditulis eksplisit.
- Setiap perubahan data dicatat di audit log dalam transaksi yang sama. Password tidak pernah masuk audit log.
- Setiap endpoint yang hak aksesnya berubah wajib diperbarui di `AccessControlTests`.
- Pesan error ditulis dalam Bahasa Indonesia, berformat ProblemDetail.
- Commit dilakukan pengguna sendiri. Test backend dijalankan dari salinan repo di scratchpad.

## 9. Diserahkan ke builder

- Tata letak jendela pesan dan posisi kotak centang akun di form, mengikuti `docs/design-system.md` dan komponen yang sudah ada (`FormDialog`, `Dialog`).
- Apakah `FormDialog` diperluas dengan field bersyarat, atau form karyawan dibuat sebagai komponen tersendiri.
- Nama fungsi util frontend: usulan username/password, pembuat pesan, dan normalisasi nomor WA.
- Teks konfirmasi dan pesan error kecil di UI.

## 10. Pertanyaan terbuka

- Nama aplikasi resmi untuk dipakai di pesan (Q-025). Sementara memakai "Sistem Absensi Takka Steel".
- Impor rekap dari mesin cabang lain, yang menunggu format file dari client.
