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

Development lokal (hot reload):

```bash
docker compose up db                                     # database saja
cd backend && ./mvnw spring-boot:run                     # butuh DB_USERNAME dan DB_PASSWORD di environment
cd frontend && npm install && npm run dev                # http://localhost:5173
```

## Test

```bash
cd backend && ./mvnw test      # butuh Docker (Testcontainers)
cd frontend && npm test
```

Eksperimen lama (backend NestJS untuk mesin milik dosen) diarsipkan di git tag `archive/zkteco-adms-experiment`.
