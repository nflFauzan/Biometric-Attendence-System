import { Module } from '@nestjs/common';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { PrismaModule } from './prisma/prisma.module';
import { AdmsModule } from './adms/adms.module';

@Module({
  imports: [PrismaModule, AdmsModule],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
