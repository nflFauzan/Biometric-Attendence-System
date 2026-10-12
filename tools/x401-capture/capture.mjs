#!/usr/bin/env node
// Alat penangkap request mesin X401 (mode ADMS / Cloud Server).
//
// Tujuan: melihat format ASLI yang dikirim mesin sebelum adapter backend dibuat,
// supaya kita tidak menebak protokol (CLAUDE.md: "Jangan mengarang kemampuan mesin").
//
// Tidak memakai dependency. Jalankan:  node tools/x401-capture/capture.mjs [--port 8081] [--ack]
//
// Perilaku bawaan AMAN: data absensi (POST /iclock/cdata) TIDAK dibalas "OK",
// sehingga mesin menganggap data belum terkirim dan tetap menyimpannya untuk dikirim
// ulang ke server sungguhan nanti. Pakai --ack hanya bila sengaja ingin menguji
// alur konfirmasi (mesin lalu menandai data itu sudah terkirim).

import { createServer } from 'node:http';
import { mkdirSync, appendFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { networkInterfaces } from 'node:os';

const args = process.argv.slice(2);
const portIndex = args.indexOf('--port');
const port = portIndex >= 0 ? Number(args[portIndex + 1]) : 8081;
const ack = args.includes('--ack');

const logDir = join(dirname(fileURLToPath(import.meta.url)), 'logs');
mkdirSync(logDir, { recursive: true });
const logFile = join(logDir, `capture-${new Date().toISOString().slice(0, 10)}.log`);

function log(text) {
  console.log(text);
  appendFileSync(logFile, text + '\n');
}

function printable(buf) {
  return buf.every((b) => b === 9 || b === 10 || b === 13 || (b >= 32 && b < 127));
}

// Balasan handshake yang umum pada protokol push ZKTeco. Nilai ini HANYA untuk
// pengamatan; format sebenarnya untuk X401 dipastikan dari log tangkapan.
// TransFlag sengaja hanya AttLog: tanpa OpLog/EnrollUser/EnrollFP agar mesin tidak
// mengirim data user atau template sidik jari (mesin uji berisi data orang lain).
function handshake(sn) {
  return [
    `GET OPTION FROM: ${sn}`,
    'ATTLOGStamp=None',
    'OPERLOGStamp=9999',
    'ATTPHOTOStamp=None',
    'ErrorDelay=30',
    'Delay=10',
    'TransTimes=00:00;14:05',
    'TransInterval=1',
    'TransFlag=TransData AttLog',
    'TimeZone=7',
    'Realtime=1',
    'Encrypt=None',
    '',
  ].join('\r\n');
}

const server = createServer((req, res) => {
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const body = Buffer.concat(chunks);
    const url = new URL(req.url, 'http://x');
    const sn = url.searchParams.get('SN') ?? '?';

    let status = 200;
    let reply = 'OK';
    if (url.pathname.endsWith('/cdata') && req.method === 'GET') {
      reply = handshake(sn);
    } else if (url.pathname.endsWith('/cdata') && req.method === 'POST' && !ack) {
      status = 503;
      reply = 'CAPTURE MODE: belum disimpan';
    }

    log('='.repeat(72));
    log(`${new Date().toISOString()}  dari ${req.socket.remoteAddress}`);
    log(`${req.method} ${req.url}`);
    for (const [k, v] of Object.entries(req.headers)) log(`  ${k}: ${v}`);
    if (body.length) {
      // Hanya 3 baris pertama yang dicatat: cukup untuk melihat format, tanpa
      // menyalin seluruh riwayat absensi orang lain ke laptop.
      const lines = printable(body) ? body.toString('utf8').split(/\r?\n/).filter(Boolean) : null;
      log(`--- body (${body.length} byte${lines ? `, ${lines.length} baris` : ''}) ---`);
      if (lines) {
        lines.slice(0, 3).forEach((l) => log(JSON.stringify(l)));
        if (lines.length > 3) log(`... ${lines.length - 3} baris lain tidak dicatat`);
      } else {
        log(body.subarray(0, 64).toString('hex') + (body.length > 64 ? ' ...' : ''));
      }
    }
    log(`--- balasan ${status} ---`);
    log(reply);

    res.writeHead(status, { 'Content-Type': 'text/plain' });
    res.end(reply);
  });
});

server.listen(port, '0.0.0.0', () => {
  const ips = Object.values(networkInterfaces())
    .flat()
    .filter((i) => i && i.family === 'IPv4' && !i.internal)
    .map((i) => i.address);
  console.log(`Penangkap X401 aktif di port ${port} (mode ${ack ? 'ACK' : 'AMAN, tanpa ACK'})`);
  console.log(`IP laptop ini: ${ips.join(', ')}`);
  console.log(`Log: ${logFile}`);
  console.log('Tekan Ctrl+C untuk berhenti.');
});
