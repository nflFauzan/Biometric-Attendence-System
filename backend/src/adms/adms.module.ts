import { Module } from '@nestjs/common';
import { AdmsController } from './adms.controller';
import { AdmsService } from './adms.service';

@Module({
  controllers: [AdmsController],
  providers: [AdmsService],
})
export class AdmsModule {}
