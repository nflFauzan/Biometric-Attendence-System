#!/usr/bin/env node
// Simulator mesin absensi yang berbicara protokol push ZKTeco (ADMS) seperti mesin uji Solution X401
// (format dari tangkapan asli, docs/panduan-mesin-x401.md §7). Data sintetis; tanpa dependency.
//
// Pemakaian:  node tools/device-simulator/simulate.mjs <perintah> [opsi]
//
// Perintah:
//   handshake                          mesin menyala dan meminta konfigurasi
//   heartbeat                          satu heartbeat (membawa INFO firmware)
//   scan --pin 9001 [--time "..."]     satu scan realtime (bawaan: sekarang)
//   offline --pin 9001 --count 3       3 scan ditahan selama "offline", lalu dikirim sekaligus
//   duplicate --pin 9001               kirim batch yang sama dua kali
//   history --count 500 --before 2026-01-01   riwayat lama (uji "Terima scan sejak")
//   bad-line --pin 9001                kiriman berisi satu baris rusak + satu baris valid
//   retry-until-ok --pin 9001          ulangi kirim seperti X401 sampai dibalas OK (uji server mati)
//   live --pin 9001 [--every 10]       heartbeat terus-menerus + scan setiap N detik (Ctrl+C untuk berhenti)
//
// Opsi umum:
//   --url http://localhost:8888        alamat backend (port 8888 = K-060)
//   --sn SIM-001                       nomor seri mesin (harus terdaftar di halaman Mesin)

const args = process.argv.slice(2);
const command = args[0];

function option(name, fallback) {
  const index = args.indexOf(`--${name}`);
  return index >= 0 && args[index + 1] !== undefined ? args[index + 1] : fallback;
}

const baseUrl = option('url', 'http://localhost:8888').replace(/\/$/, '');
const sn = option('sn', 'SIM-001');
const pin = option('pin', '9001');

const pad = (n) => String(n).padStart(2, '0');
/** Waktu lokal mesin "YYYY-MM-DD HH:MM:SS" tanpa zona, seperti X401. */
function deviceTime(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
const attlogLine = (p, time) => `${p}\t${time}\t0\t1`;
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function request(method, path, params, body) {
  const url = `${baseUrl}${path}?${new URLSearchParams({ SN: sn, ...params })}`;
  try {
    const res = await fetch(url, { method, body, headers: body ? { 'Content-Type': 'text/plain' } : undefined });
    const text = await res.text();
    console.log(`${method} ${path} ${JSON.stringify(params)} -> ${res.status} ${text.split('\r\n')[0]}`);
    return { ok: res.ok && text.trim() === 'OK', status: res.status, text };
  } catch (error) {
    console.log(`${method} ${path} -> gagal terhubung (${error.cause?.code ?? error.message})`);
    return { ok: false, status: 0, text: '' };
  }
}

const handshake = () => request('GET', '/iclock/cdata', { options: 'all', pushver: '2.32', language: '73' });
const heartbeat = () => request('GET', '/iclock/getrequest', {
  INFO: 'Simulator 1.0,1,1,1,127.0.0.1,10,-1,0,0,101',
});
const sendLines = (lines) => request('POST', '/iclock/cdata', { table: 'ATTLOG', Stamp: String(Date.now()) },
  lines.join('\n') + '\n');

async function main() {
  switch (command) {
    case 'handshake':
      console.log((await handshake()).text);
      break;
    case 'heartbeat':
      await heartbeat();
      break;
    case 'scan': {
      const time = option('time', deviceTime(new Date()));
      await sendLines([attlogLine(pin, time)]);
      break;
    }
    case 'offline': {
      const count = Number(option('count', '3'));
      const now = Date.now();
      const held = Array.from({ length: count }, (_, i) =>
        attlogLine(pin, deviceTime(new Date(now - (count - i) * 60_000))));
      console.log(`Mesin "offline": ${count} scan ditahan:`);
      held.forEach((line) => console.log(`  ${line.replaceAll('\t', '  ')}`));
      console.log('Mesin "online" lagi, mengirim semua scan yang ditahan...');
      await sendLines(held);
      break;
    }
    case 'duplicate': {
      const lines = [attlogLine(pin, deviceTime(new Date()))];
      await sendLines(lines);
      await sendLines(lines);
      console.log('Batch yang sama dikirim dua kali; server harus menyimpan satu baris saja.');
      break;
    }
    case 'history': {
      const count = Number(option('count', '500'));
      const before = new Date(`${option('before', '2026-01-01')}T08:00:00`);
      const lines = Array.from({ length: count }, (_, i) =>
        attlogLine(String(19100000 + (i % 50)), deviceTime(new Date(before.getTime() - (i + 1) * 3_600_000))));
      for (let i = 0; i < lines.length; i += 200) {
        await sendLines(lines.slice(i, i + 200)); // X401 mengirim riwayat 200 baris per request
      }
      break;
    }
    case 'bad-line':
      await sendLines(['baris-rusak-tanpa-tab', attlogLine(pin, deviceTime(new Date()))]);
      break;
    case 'retry-until-ok': {
      const lines = [attlogLine(pin, deviceTime(new Date()))];
      for (let attempt = 1; attempt <= 200; attempt++) {
        if ((await sendLines(lines)).ok) {
          console.log(`Diterima setelah ${attempt} percobaan.`);
          return;
        }
        await sleep(3000);
      }
      console.log('Menyerah setelah 200 percobaan.');
      process.exitCode = 1;
      break;
    }
    case 'live': {
      const every = Number(option('every', '10')) * 1000;
      await handshake();
      for (;;) {
        await heartbeat();
        await sendLines([attlogLine(pin, deviceTime(new Date()))]);
        await sleep(every);
      }
    }
    default:
      console.log('Perintah tidak dikenal. Lihat komentar di awal file untuk daftar perintah.');
      process.exitCode = 1;
  }
}

await main();
