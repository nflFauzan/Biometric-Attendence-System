import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import * as express from 'express';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  
  // Parse incoming requests as plain text for ADMS endpoints
  app.use('/iclock/cdata', express.text({ type: '*/*' }));
  app.use('/iclock/devicecmd', express.text({ type: '*/*' }));

  await app.listen(process.env.PORT ?? 3000);
}
bootstrap();
