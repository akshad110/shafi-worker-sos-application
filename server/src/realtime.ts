import { Server } from 'socket.io';
import jwt from 'jsonwebtoken';
import { env } from './config/env';

let io: Server | null = null;

export function setIo(server: Server) {
  io = server;
}

export function emitToUser(userId: string, event: string, payload: unknown) {
  io?.to(`user:${userId}`).emit(event, payload);
}

export function socketAuth(socket: { handshake: { auth?: { token?: string } }; data: { userId?: string } }, next: (err?: Error) => void) {
  try {
    const token = socket.handshake.auth?.token || '';
    const payload = jwt.verify(token, env.jwtAccessSecret) as { sub?: string; type?: string };
    if (payload.type !== 'access' || !payload.sub) {
      next(new Error('UNAUTHENTICATED'));
      return;
    }
    socket.data.userId = payload.sub;
    next();
  } catch {
    next(new Error('UNAUTHENTICATED'));
  }
}
