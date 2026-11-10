import dotenv from 'dotenv';
import http from 'http';
import { Server } from 'socket.io';
import { createApp } from './app';
import { assertEnv, safeLog } from './config/env';
import { connectDb } from './config/db';
import { env } from './config/env';
import { setIo, socketAuth } from './realtime';
import { expireStaleOffers, remindUpcomingShifts } from './services/staffing.service';

dotenv.config();

async function main() {
  try {
    assertEnv();
    await connectDb();
  } catch (error) {
    console.error(safeLog(error));
    process.exit(1);
  }
  const app = createApp();
  const server = http.createServer(app);
  const io = new Server(server, { cors: { origin: env.corsOrigins } });
  io.use(socketAuth);
  io.on('connection', (socket) => {
    const userId = socket.data.userId as string;
    socket.join(`user:${userId}`);
  });
  setIo(io);
  setInterval(() => {
    expireStaleOffers().catch((error) => console.error(safeLog(error)));
    remindUpcomingShifts().catch((error) => console.error(safeLog(error)));
  }, 60 * 1000);
  server.listen(env.port, () => {
    console.log(`Hospital SOS API listening on port ${env.port}`);
    console.log('Swagger UI at /api/docs');
  });
}

main();
