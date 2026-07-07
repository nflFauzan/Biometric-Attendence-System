import { AdmsService } from './adms.service';
import type { Request } from 'express';
export declare class AdmsController {
    private readonly admsService;
    private readonly logger;
    constructor(admsService: AdmsService);
    handleHandshake(sn: string, req: Request): string;
    handleData(sn: string, table: string, body: string): Promise<string>;
    handleGetRequest(sn: string): string;
    handleDeviceCmd(sn: string, body: string): string;
}
