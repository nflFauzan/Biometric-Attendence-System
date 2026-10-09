# Alur Utama

Langkah yang bergantung pada kemampuan mesin yang belum diketahui ditandai **[mesin]**.

## 1. Pendaftaran karyawan dan sidik jari

```mermaid
flowchart TD
  A[HR/Finance membuat data karyawan<br/>cabang, kategori, gaji] --> B[Buat akun web karyawan]
  B --> C["Daftarkan sidik jari di mesin cabang [mesin]"]
  C --> D[Mesin melaporkan ID sidik jari]
  D --> E[Server menyimpan relasi<br/>karyawan - mesin - ID sidik jari]
  E --> F{Scan uji cocok?}
  F -- Tidak --> C
  F -- Ya --> G[Karyawan siap absen]
```

## 2. Dari scan sampai dashboard

```mermaid
flowchart TD
  A[Karyawan scan di mesin cabang] --> B["Mesin mencocokkan sidik jari [mesin]"]
  B --> C[Mesin kirim data scan ke server<br/>dengan ID unik per scan]
  C --> D{Server berhasil menyimpan?}
  D -- Tidak / tidak ada koneksi --> E["Mesin menyimpan sementara<br/>lalu mengirim ulang [mesin]"]
  E --> C
  D -- Ya --> F[Server membalas OK ke mesin]
  F --> G[Hitung ulang rekap hari itu]
  G --> H[Dashboard diperbarui]
```

Server **hanya** membalas OK setelah data benar-benar tersimpan. Kalau scan yang sama terkirim dua kali, scan itu disimpan sekali saja.

## 3. Rekap harian

```mermaid
flowchart TD
  A[Scan hari itu] --> B{Jumlah scan}
  B -- 0 --> C{Ada izin/cuti disetujui?}
  C -- Ya --> C1[Status izin/cuti]
  C -- Tidak --> C2[Status tidak hadir]
  B -- 1 --> D[Status tidak lengkap<br/>menunggu koreksi]
  B -- 2 atau lebih --> E[Masuk = scan pertama<br/>Pulang = scan terakhir]
  E --> F[Hitung terlambat, jam kerja, lembur<br/>pakai aturan yang berlaku hari itu]
  F --> G{Di bawah target jam?}
  G -- Ya --> H[Tandai untuk HR]
  G -- Tidak --> I[Simpan rekap]
  H --> I
```

## 4. Koreksi scan

```mermaid
flowchart TD
  A[HR/Finance/Owner memilih hari] --> B{Periode gaji sudah final?}
  B -- Ya --> X[Tidak bisa dikoreksi]
  B -- Tidak --> C[Isi jam yang benar + alasan wajib]
  C --> D[Simpan sebagai koreksi<br/>scan asli dari mesin tidak diubah]
  D --> E[Catat di audit log]
  E --> F[Hitung ulang rekap]
```

Cara membuka kembali periode yang sudah final masih **usulan** dan belum dirancang.

## 5. Gaji per periode

```mermaid
flowchart TD
  A[Periode berakhir<br/>mingguan: supir/kenek, bulanan: kantor] --> B[Sistem menyusun draf]
  B --> C[HR/Finance/Owner meninjau]
  C --> C1[Pilih hari yang upah hariannya dibayar]
  C --> C2[Tetapkan nominal lembur]
  C --> C3[Koreksi scan yang lupa]
  C1 & C2 & C3 --> D[Hitung total dikurangi kasbon dan BPJS]
  D --> E{Sudah benar?}
  E -- Belum --> C
  E -- Sudah --> F[Finalisasi: periode dikunci]
  F --> G[Ekspor Excel/PDF]
```

## 6. Izin dan cuti

```mermaid
flowchart TD
  A[Karyawan mengisi jenis, tanggal,<br/>alasan, dan surat] --> B[Status menunggu]
  B --> C{HR/Finance/Owner menyetujui?}
  C -- Tidak --> D[Ditolak dengan alasan]
  C -- Ya --> E[Hari terkait ditandai izin/cuti di rekap]
```

Jenis izin/cuti dan perlakuannya terhadap gaji **belum diketahui**.

## 7. Karyawan keluar

```mermaid
flowchart TD
  A[Catat tanggal berhenti] --> B[Nonaktifkan akun web]
  B --> C["Hapus sidik jari dari semua mesin [mesin]"]
  C --> D{Mesin mengonfirmasi?}
  D -- Tidak --> C
  D -- Ya --> E[Gaji periode terakhir]
  E --> F[Riwayat absensi dan gaji tetap disimpan]
```
