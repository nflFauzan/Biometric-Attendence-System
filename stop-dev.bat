@echo off
setlocal
title Biometric Attendance System - Stop Dev
chcp 65001 >nul

cd /d "%~dp0"

echo ========================================================
echo       Biometric Attendance System - Stop Dev
echo ========================================================
echo.

:: 1. Hentikan Frontend (Vite / Port 5173)
echo [1/3] Menghentikan Frontend (Port 5173)...
taskkill /FI "WINDOWTITLE eq BAS - Frontend*" /T /F >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :5173 ^| findstr LISTENING') do (
    taskkill /PID %%a /T /F >nul 2>&1
)

:: 2. Hentikan Backend (Spring Boot / Port 8080)
echo [2/3] Menghentikan Backend (Port 8080)...
taskkill /FI "WINDOWTITLE eq BAS - Backend*" /T /F >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do (
    taskkill /PID %%a /T /F >nul 2>&1
)

:: Hentikan sisa proses terkait jika masih ada di background
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { ($_.CommandLine -like '*mvnw*spring-boot:run*' -or $_.CommandLine -like '*vite*') -and $_.ProcessId -ne $PID } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" >nul 2>&1

:: 3. Hentikan container Docker
echo [3/3] Menghentikan container Docker...
docker info >nul 2>&1
if not errorlevel 1 (
    docker compose down >nul 2>&1
)

echo.
echo ========================================================
echo   [SELESAI] Semua service development telah dihentikan!
echo ========================================================
echo.
ping -n 3 127.0.0.1 >nul
