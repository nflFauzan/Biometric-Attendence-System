# Biometric Attendance System

Sistem absensi fingerprint dan penggajian: mesin fingerprint rakitan di setiap cabang mengirim data scan ke server, lalu server menghitung absensi dan gaji yang ditampilkan di web.

- `backend/`: Spring Boot (Java 21, Maven), PostgreSQL, Flyway
- `frontend/`: React + Vite + TypeScript
- `docs/`: dokumentasi proyek, mulai dari [docs/README.md](docs/README.md)

## Menjalankan

Kebutuhan: Docker. Untuk development lokal juga dibutuhkan Java 21 dan Node 24.

```bash
cp .env.example .env          # lalu isi password database
docker compose up --build     # web: http://localhost:3000, API: http://localhost:8080
```

## Login pertama

Saat backend pertama kali jalan, akun Owner dibuat otomatis dari `OWNER_USERNAME` dan `OWNER_INITIAL_PASSWORD` di `.env`. Login dengan akun itu di http://localhost:3000, lalu ganti password (wajib). Setelah itu Owner bisa membuat akun HR/Finance dan karyawan di menu **Akun**.

Development lokal (hot reload):

### Cara Cepat (Windows)

Tersedia skrip batch otomatis di root direktori yang bisa langsung di-double click atau dijalankan via terminal:

1. **`start-dev.bat`** (Mulai Semua Service):
   - Memastikan Docker Desktop sedang aktif.
   - Otomatis membuat dan membaca file `.env` jika belum ada.
   - Menjalankan container database PostgreSQL di Docker (`docker compose up -d db`).
   - Otomatis menjalankan `npm install` jika `frontend/node_modules` belum ada.
   - Menjalankan Backend Spring Boot (`./mvnw spring-boot:run`) dan Frontend (`npm run dev`) di jendela terminal terpisah agar log mudah dipantau.
   - **URL Akses:**
     - Frontend Web: http://localhost:5173
     - Backend API: http://localhost:8080 (Health check: http://localhost:8080/actuator/health)
     - Database PostgreSQL: `localhost:5432`

2. **`stop-dev.bat`** (Hentikan Semua Service):
   - Menghentikan proses Frontend (port 5173).
   - Menghentikan proses Backend (port 8080).
   - Menghentikan container database Docker secara bersih.

---

### Cara Manual (Terminal / Linux / macOS)

```bash
docker compose up db                                     # database saja
cd backend && ./mvnw spring-boot:run                     # butuh DB_USERNAME, DB_PASSWORD, OWNER_USERNAME, OWNER_INITIAL_PASSWORD di environment
cd frontend && npm install && npm run dev                # http://localhost:5173
```

## Test

```bash
cd backend && ./mvnw test      # butuh Docker (Testcontainers)
cd frontend && npm test
```

Eksperimen lama (backend NestJS untuk mesin milik dosen) diarsipkan di git tag `archive/zkteco-adms-experiment`.
