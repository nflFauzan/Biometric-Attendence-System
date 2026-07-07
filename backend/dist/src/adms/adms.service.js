"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var AdmsService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.AdmsService = void 0;
const common_1 = require("@nestjs/common");
const prisma_service_1 = require("../prisma/prisma.service");
let AdmsService = AdmsService_1 = class AdmsService {
    prisma;
    logger = new common_1.Logger(AdmsService_1.name);
    constructor(prisma) {
        this.prisma = prisma;
    }
    getHandshakeConfig(sn) {
        this.logger.log(`Handshake request from SN: ${sn}`);
        return `GET OPTION FROM: ${sn}\nStamp=9999\nOpStamp=9999\nErrorDelay=60\nDelay=30\nTransTimes=00:00;14:00\nTransInterval=1\nTransFlag=1111000000\nTimeZone=72\nRealtime=1\nEncrypt=0`;
    }
    async processAttendanceData(sn, rawData) {
        this.logger.log(`Received push data from SN: ${sn}`);
        const device = await this.prisma.fingerprintDevice.findUnique({
            where: { serial_number: sn },
        });
        if (!device) {
            this.logger.warn(`Unknown device SN: ${sn}. Ignoring data.`);
            return 'OK';
        }
        const lines = rawData.split('\n');
        const records = [];
        for (const line of lines) {
            const parts = line.trim().split('\t');
            if (parts.length >= 2) {
                const pin = parts[0];
                const timeStr = parts[1];
                const status = parts[2] ? parseInt(parts[2], 10) : 0;
                const scanTime = new Date(timeStr.replace(' ', 'T') + '+07:00');
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
            }
            catch (error) {
                this.logger.error(`Failed to insert attendance records: ${error.message}`);
            }
        }
        return 'OK';
    }
};
exports.AdmsService = AdmsService;
exports.AdmsService = AdmsService = AdmsService_1 = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [prisma_service_1.PrismaService])
], AdmsService);
//# sourceMappingURL=adms.service.js.map