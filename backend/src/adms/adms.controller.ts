import { Controller, Get, Post, Query, Body, Req, Logger, HttpCode, HttpStatus } from '@nestjs/common';
import { AdmsService } from './adms.service';
import type { Request } from 'express';

@Controller('iclock')
export class AdmsController {
  private readonly logger = new Logger(AdmsController.name);

  constructor(private readonly admsService: AdmsService) {}

  @Get('cdata')
  handleHandshake(@Query('SN') sn: string, @Req() req: Request) {
    this.logger.log(`GET /iclock/cdata - SN: ${sn}`);
    return this.admsService.getHandshakeConfig(sn);
  }

  @Post('cdata')
  @HttpCode(HttpStatus.OK)
  async handleData(
    @Query('SN') sn: string,
    @Query('table') table: string,
    @Body() body: string,
  ) {
    this.logger.log(`POST /iclock/cdata - SN: ${sn}, table: ${table}`);
    
    if (table === 'ATTLOG') {
      return this.admsService.processAttendanceData(sn, body);
    }
    
    // Fallback for other tables like USER, OPERLOG
    return 'OK';
  }

  @Get('getrequest')
  handleGetRequest(@Query('SN') sn: string) {
    this.logger.log(`GET /iclock/getrequest - SN: ${sn}`);
    return 'OK';
  }

  @Post('devicecmd')
  @HttpCode(HttpStatus.OK)
  handleDeviceCmd(@Query('SN') sn: string, @Body() body: string) {
    this.logger.log(`POST /iclock/devicecmd - SN: ${sn}, body: ${body}`);
    return 'OK';
  }
}
