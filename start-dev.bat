@echo off
setlocal
title Biometric Attendance System - Dev Launcher
chcp 65001 >nul

cd /d "%~dp0"

echo ========================================================
echo       Biometric Attendance System - Dev Launcher
echo ========================================================
echo.

:: 1. Periksa status Docker Desktop
echo [1/4] Memeriksa status Docker...
docker info >nul 2>&1
if errorlevel 1 (
    echo.
    echo [ERROR] Docker tidak terdeteksi atau belum berjalan!
    echo Silakan buka dan jalankan Docker Desktop terlebih dahulu, lalu coba lagi.
    echo.
    pause
    exit /b 1
)

:: 2. Siapkan file .env dan muat environment variables
echo [2/4] Menyiapkan environment variable...
if not exist ".env" (
    if exist ".env.example" (
        echo [INFO] File .env tidak ditemukan, membuat .env otomatis dari .env.example...
        copy .env.example .env >nul
    ) else (
        echo [WARNING] File .env dan .env.example tidak ditemukan.
    )
)

if exist ".env" (
    for /f "usebackq eol=# tokens=1* delims==" %%A in (".env") do (
        if not "%%A"=="" set "%%A=%%B"
    )
)

if not defined DB_PORT set "DB_PORT=5432"
if not defined DB_NAME set "DB_NAME=absensi"
if not defined DB_URL set "DB_URL=jdbc:postgresql://localhost:%DB_PORT%/%DB_NAME%"

:: 3. Jalankan container database PostgreSQL di Docker
echo [3/4] Menjalankan container Database di Docker...
:: Hentikan backend/frontend docker container jika sebelumnya berjalan via docker compose up
docker compose stop backend frontend >nul 2>&1
docker compose up -d db
if errorlevel 1 (
    echo.
    echo [ERROR] Gagal menjalankan container database Docker!
    echo.
    pause
    exit /b 1
)

:: Beri jeda singkat agar PostgreSQL siap menerima koneksi
ping -n 3 127.0.0.1 >nul

:: Cek dependensi frontend jika node_modules belum terpasang
if not exist "frontend\node_modules" (
    echo [INFO] Folder frontend\node_modules belum ada. Menjalankan npm install...
    pushd frontend
    call npm install
    popd
)

:: 4. Jalankan Backend dan Frontend di jendela terpisah
echo [4/4] Menjalankan Backend (Spring Boot) dan Frontend (Vite)...
start "BAS - Backend (Spring Boot)" cmd /k "title BAS - Backend (Spring Boot) && cd /d "%~dp0backend" && mvnw.cmd spring-boot:run"
start "BAS - Frontend (Vite)" cmd /k "title BAS - Frontend (Vite) && cd /d "%~dp0frontend" && npm run dev"

echo.
echo ========================================================
echo   [SUKSES] Semua service development berhasil dijalankan!
echo ========================================================
echo   - PostgreSQL Database : localhost:%DB_PORT%
echo   - Backend Spring Boot : http://localhost:8080
echo   - Frontend React/Vite : http://localhost:5173
echo.
echo Catatan:
echo   - Backend dan Frontend berjalan di jendela command prompt terpisah.
echo   - Jangan tutup jendela tersebut selama proses development.
echo   - Untuk mematikan seluruh service, jalankan stop-dev.bat.
echo ========================================================
echo.
echo Tekan sembarang tombol untuk menutup jendela launcher ini...
pause >nul
