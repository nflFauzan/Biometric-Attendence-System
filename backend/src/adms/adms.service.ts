import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class AdmsService {
  private readonly logger = new Logger(AdmsService.name);

  constructor(private prisma: PrismaService) {}

  getHandshakeConfig(sn: string): string {
    this.logger.log(`Handshake request from SN: ${sn}`);
    return `GET OPTION FROM: ${sn}\nStamp=9999\nOpStamp=9999\nErrorDelay=60\nDelay=30\nTransTimes=00:00;14:00\nTransInterval=1\nTransFlag=1111000000\nTimeZone=72\nRealtime=1\nEncrypt=0`;
  }

  async processAttendanceData(sn: string, rawData: string): Promise<string> {
    this.logger.log(`Received push data from SN: ${sn}`);

    // Check if device is valid
    const device = await this.prisma.fingerprintDevice.findUnique({
      where: { serial_number: sn },
    });

    if (!device) {
      this.logger.warn(`Unknown device SN: ${sn}. Ignoring data.`);
      return 'OK'; // Still return OK so the device stops trying
    }

    // Process rawData (ATTLOG)
    // Format: PIN\tTime\tStatus\tVerify_Type\tWorkCode\tReserved
    const lines = rawData.split('\n');
    const records: any[] = [];

    for (const line of lines) {
      const parts = line.trim().split('\t');
      if (parts.length >= 2) {
        const pin = parts[0];
        const timeStr = parts[1]; // YYYY-MM-DD HH:mm:ss
        const status = parts[2] ? parseInt(parts[2], 10) : 0;
        
        // Parse time string to Date.
        // Assuming time from device is local time. If the device sends "2023-10-25 08:15:30",
        // we can store it properly or just construct a Date assuming a specific offset if necessary.
        // For simplicity, treating as local timezone or UTC by replacing space with T and appending Z
        // depending on how Postgres is configured.
        const scanTime = new Date(timeStr.replace(' ', 'T') + '+07:00'); // Assuming WIB for example

        if (!isNaN(scanTime.getTime())) {
          records.push({
            tenant_id: device.tenant_id,
            device_id: device.id,
            fingerprint_user_id: pin,
            scan_time: scanTime,
            raw_status: status,
          });
        }
      }
    }

    if (records.length > 0) {
      try {
        await this.prisma.attendanceRaw.createMany({
          data: records,
          skipDuplicates: true, 
        });
        this.logger.log(`Inserted ${records.length} attendance records for SN: ${sn}`);
      } catch (error: any) {
        this.logger.error(`Failed to insert attendance records: ${error.message}`);
      }
    }

    return 'OK';
  }
}
