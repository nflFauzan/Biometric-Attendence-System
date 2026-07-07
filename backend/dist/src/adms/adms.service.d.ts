import { PrismaService } from '../prisma/prisma.service';
export declare class AdmsService {
    private prisma;
    private readonly logger;
    constructor(prisma: PrismaService);
    getHandshakeConfig(sn: string): string;
    processAttendanceData(sn: string, rawData: string): Promise<string>;
}
