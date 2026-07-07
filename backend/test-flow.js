const { execSync } = require('child_process');
const { PrismaClient } = require('@prisma/client');
const { Pool } = require('pg');
const { PrismaPg } = require('@prisma/adapter-pg');

const sn = 'BRYV181860187';

async function run() {
  console.log('--- TEST 1: Sending request WITHOUT device in DB ---');
  try {
    const curlCmd = `curl.exe -s -X POST "http://localhost:3000/iclock/cdata?SN=${sn}&table=ATTLOG&Stamp=1" -H "Content-Type: text/plain" --data "101\t2026-07-07 10:15:30\t0\t1\t0\t0"`;
    const res1 = execSync(curlCmd, { encoding: 'utf8' });
    console.log('Response:', res1);
  } catch(e) {
    console.log('Curl failed:', e.message);
  }
  
  console.log('\n--- INSERTING DEVICE INTO DB ---');
  
  // Connect Prisma
  const connectionString = "postgresql://root:rootpassword@localhost:5433/biometric_db?schema=public";
  const pool = new Pool({ connectionString });
  const adapter = new PrismaPg(pool);
  const prisma = new PrismaClient({ adapter });
  
  try {
    const tenant = await prisma.tenant.findFirst();
    let tenantId = 1;
    if (!tenant) {
      const newTenant = await prisma.tenant.create({ data: { id: 1, company_name: "Default Tenant" } });
      tenantId = newTenant.id;
    }

    const device = await prisma.fingerprintDevice.create({
      data: {
        tenant_id: tenantId,
        serial_number: sn,
        name: 'Main Door',
        ip_address: '172.35.3.102',
        is_active: true
      }
    });
    console.log('Device inserted successfully:', device);
  } catch(e) {
    if (e.code === 'P2002') {
      console.log('Device already exists.');
    } else {
      console.error('Error inserting device:', e);
    }
  }

  console.log('\n--- TEST 2: Sending request WITH device in DB ---');
  try {
    const curlCmd = `curl.exe -s -X POST "http://localhost:3000/iclock/cdata?SN=${sn}&table=ATTLOG&Stamp=1" -H "Content-Type: text/plain" --data "101\t2026-07-07 10:15:30\t0\t1\t0\t0"`;
    const res2 = execSync(curlCmd, { encoding: 'utf8' });
    console.log('Response:', res2);
  } catch(e) {
    console.log('Curl failed:', e.message);
  }

  console.log('\n--- CHECKING ATTENDANCE_RAW TABLE ---');
  const attendance = await prisma.attendanceRaw.findMany({
    where: { fingerprint_user_id: '101' }
  });
  console.log('Attendance Records found:', attendance);

  await prisma.$disconnect();
}

run();
