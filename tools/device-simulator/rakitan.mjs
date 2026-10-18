#!/usr/bin/env node
// Simulator alat rakitan ESP32 yang berbicara protokol /device/v1 (docs/protokol-alat-rakitan.md).
// Menggantikan firmware sampai mesin rakitan siap. Data sintetis; tanpa dependency.
//
// Pemakaian:  node tools/device-simulator/rakitan.mjs <perintah> --code BAS1.... [opsi]
//             (atau isi env BAS_CODE dengan kode pengaturan dari halaman Mesin)
//
// Perintah:
//   decode                              tampilkan isi kode pengaturan (tanpa menghubungi server)
//   setup                               tiru layar mesin saat pengaturan pertama: server, kunci, "Siap absen"
//   heartbeat                           satu heartbeat
//   scan --pin 9001 [--time ISO]        satu scan (bawaan: sekarang, dengan offset zona komputer)
//   offline --pin 9001 --count 3        3 scan dari antrean microSD dikirim dalam satu batch
//   duplicate --pin 9001                kirim batch yang sama dua kali (server harus menyimpan sekali)
//   bad-scan --pin 9001                 satu scan valid + satu scan tanpa offset
//   oversize                            201 scan dalam satu kiriman (harus 413)
//   wrong-key                           heartbeat dengan kunci salah (harus 401)
//   live --pin 9001 [--every 10]        heartbeat setiap 5 detik + scan setiap N detik (Ctrl+C untuk berhenti).
//        [--enroll-result BERHASIL]     juga mengerjakan perintah ENROLL dari heartbeat: progress 1–3 lalu hasil.
//        [--enroll-delay 2]             Hasil lain: kode error, mis. KUALITAS_BURUK atau JARI_SUDAH_TERDAFTAR
//        [--owner-pin 9002]             (dengan --owner-pin = PIN pemilik sidik jari yang sudah ada). Jeda antar
//                                       langkah dalam detik. Selama pendaftaran tidak ada scan absensi (kontrak §6).

import { randomUUID } from 'node:crypto';

const args = process.argv.slice(2);
const command = args[0];

function option(name, fallback) {
  const index = args.indexOf(`--${name}`);
  return index >= 0 && args[index + 1] !== undefined ? args[index + 1] : fallback;
}

/** Membaca kode BAS1.<base64url JSON>; sama dengan aturan firmware (kontrak §1). */
function decodeCode(code) {
  if (!code || !code.startsWith('BAS1.')) {
    return null;
  }
  try {
    const json = JSON.parse(Buffer.from(code.slice(5), 'base64url').toString('utf8'));
    if (typeof json.server !== 'string' || typeof json.device !== 'string' || typeof json.key !== 'string') {
      return null;
    }
    return { server: json.server.replace(/\/$/, ''), device: json.device, key: json.key };
  } catch {
    return null;
  }
}

const setup = decodeCode(option('code', process.env.BAS_CODE));
if (!setup) {
  console.log('Kode pengaturan tidak valid');
  process.exit(1);
}
const pin = option('pin', '9001');

const pad = (n) => String(n).padStart(2, '0');
/** Waktu ISO-8601 dengan offset zona komputer, presisi detik, mis. 2026-10-17T08:01:02+07:00. */
function isoWithOffset(date) {
  const offset = -date.getTimezoneOffset();
  const sign = offset >= 0 ? '+' : '-';
  const abs = Math.abs(offset);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T`
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
    + `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`;
}
const scan = (p, time) => ({ id: randomUUID(), pin: p, time, verify: 'FP' });
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function post(path, body, key = setup.key) {
  try {
    const res = await fetch(`${setup.server}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${key}` },
      body: JSON.stringify(body),
    });
    const text = await res.text();
    console.log(`POST ${path} -> ${res.status} ${text}`);
    let json = null;
    try {
      json = JSON.parse(text);
    } catch {
      // balasan bukan JSON
    }
    return { status: res.status, json };
  } catch (error) {
    console.log(`POST ${path} -> gagal terhubung (${error.cause?.code ?? error.message})`);
    return { status: 0, json: null };
  }
}

const heartbeatBody = () => ({
  firmware: 'sim-0.1.0',
  ip: '127.0.0.1',
  uptimeSeconds: Math.round(process.uptime()),
  pendingScans: 0,
  sensor: { used: 0, capacity: 150 },
});
const heartbeat = (key) => post('/device/v1/heartbeat', heartbeatBody(), key);
const sendScans = (scans) => post('/device/v1/scans', { scans });

// Pendaftaran sidik jari (kontrak §6) untuk `live`.
const enrollResult = option('enroll-result', 'BERHASIL');
const enrollDelay = Number(option('enroll-delay', '2')) * 1000;
const ownerPin = option('owner-pin', undefined);
const seen = new Set();
const cancelled = new Set();

function resultBody() {
  if (enrollResult === 'BERHASIL') {
    return { status: 'BERHASIL' };
  }
  return { status: 'GAGAL', error: enrollResult, ...(ownerPin ? { pin: ownerPin } : {}) };
}

/** Tiru layar mesin: tiga tempelan, lalu kirim hasil. Berhenti jika dibatalkan dari web. */
async function runEnroll(cmd) {
  console.log(`Layar: Daftarkan jari ${cmd.finger} — ${cmd.displayName}`);
  for (let step = 1; step <= 3; step++) {
    await sleep(enrollDelay);
    if (cancelled.has(cmd.id)) {
      console.log('Layar: Pendaftaran dibatalkan dari web');
      return;
    }
    console.log(`Layar: Tempelkan jari (${step}/3)`);
    const res = await post(`/device/v1/commands/${cmd.id}/progress`, { step });
    if (res.status === 409 || res.status === 404) {
      console.log('Layar: Perintah sudah tidak aktif; pendaftaran dihentikan');
      return;
    }
  }
  if (cancelled.has(cmd.id)) {
    console.log('Layar: Pendaftaran dibatalkan dari web');
    return;
  }
  // Hasil dikirim ulang sampai server menjawab; 200 untuk perintah yang sudah selesai juga berarti berhenti.
  for (;;) {
    const res = await post(`/device/v1/commands/${cmd.id}/result`, resultBody());
    if (res.status !== 0 && res.status < 500) {
      console.log(enrollResult === 'BERHASIL' ? 'Layar: Berhasil' : `Layar: Gagal (${enrollResult})`);
      return;
    }
    await sleep(5000);
  }
}

async function main() {
  switch (command) {
    case 'decode':
      console.log(`Server : ${setup.server}`);
      console.log(`ID     : ${setup.device}`);
      console.log(`Kunci  : ${setup.key.slice(0, 6)}… (${setup.key.length} karakter)`);
      break;
    case 'setup': {
      // Langkah Wi-Fi diwakili komputer ini; simulator mulai dari langkah server (kontrak §7).
      const res = await heartbeat();
      if (res.status === 0) {
        console.log(`✗ Server ${setup.server} tidak terjangkau. Laptop menyala? Satu jaringan?`);
        process.exitCode = 1;
        break;
      }
      console.log('✓ Server ditemukan');
      // Hanya kode error dari kontrak (§3) yang ditafsirkan; balasan lain berarti server bukan versi /device/v1.
      if (res.status === 401 && res.json?.error === 'KUNCI_DITOLAK') {
        console.log('✗ Kunci ditolak. Buat kode pengaturan baru di web.');
        process.exitCode = 1;
      } else if (res.status === 403 && res.json?.error === 'MESIN_NONAKTIF') {
        console.log('✗ Mesin dinonaktifkan di web.');
        process.exitCode = 1;
      } else if (res.status === 200) {
        console.log('✓ Kunci diterima');
        const time = res.json.serverTime.slice(11, 16);
        console.log(`Siap absen — ${res.json.deviceName} · ${res.json.branchName} · ${time}`);
      } else {
        console.log(`✗ Balasan tidak terduga: ${res.status}. Server sudah versi terbaru (ada /device/v1)?`);
        process.exitCode = 1;
      }
      break;
    }
    case 'heartbeat':
      await heartbeat();
      break;
    case 'scan':
      await sendScans([scan(pin, option('time', isoWithOffset(new Date())))]);
      break;
    case 'offline': {
      const count = Number(option('count', '3'));
      const now = Date.now();
      const held = Array.from({ length: count }, (_, i) => scan(pin, isoWithOffset(new Date(now - (count - i) * 60_000))));
      console.log(`Mesin "offline": ${count} scan disimpan di microSD, lalu dikirim sekaligus...`);
      await sendScans(held);
      break;
    }
    case 'duplicate': {
      const scans = [scan(pin, isoWithOffset(new Date()))];
      await sendScans(scans);
      const again = await sendScans(scans);
      console.log(`Kiriman ulang: duplicates = ${again.json?.duplicates}. Server harus menyimpan satu baris saja.`);
      break;
    }
    case 'bad-scan':
      await sendScans([
        scan(pin, isoWithOffset(new Date())),
        scan(pin, isoWithOffset(new Date()).slice(0, 19)), // tanpa offset: ditolak tetapi ID-nya tetap "accepted"
      ]);
      break;
    case 'oversize': {
      const now = Date.now();
      await sendScans(Array.from({ length: 201 }, (_, i) => scan(pin, isoWithOffset(new Date(now - i * 1000)))));
      break;
    }
    case 'wrong-key':
      await heartbeat('kunci-salah');
      break;
    case 'live': {
      const every = Number(option('every', '10')) * 1000;
      let last = 0;
      let enrolling = null;
      for (;;) {
        const res = await heartbeat();
        for (const id of res.json?.cancel ?? []) {
          cancelled.add(id);
        }
        for (const cmd of res.json?.commands ?? []) {
          // Server mengirim ulang perintah sampai ada hasil; mesin mengerjakan setiap ID sekali saja (kontrak §4).
          if (seen.has(cmd.id) || enrolling) {
            continue;
          }
          seen.add(cmd.id);
          if (cmd.type !== 'ENROLL') {
            console.log(`Perintah ${cmd.type} belum didukung simulator; diabaikan`);
            continue;
          }
          enrolling = runEnroll(cmd).finally(() => {
            enrolling = null;
          });
        }
        if (!enrolling && Date.now() - last >= every) {
          await sendScans([scan(pin, isoWithOffset(new Date()))]);
          last = Date.now();
        }
        await sleep(5000);
      }
    }
    default:
      console.log('Perintah tidak dikenal. Lihat komentar di awal file untuk daftar perintah.');
      process.exitCode = 1;
  }
}

await main();
