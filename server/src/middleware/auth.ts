import { NextFunction, Request, Response } from 'express';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { User } from '../models';
import { ApiError } from '../utils/errors';

export interface AuthUser {
  id: string;
  role: 'WORKER' | 'HOSPITAL' | 'ADMIN' | 'SUPER_ADMIN';
  hospitalId?: string;
  sessionId: string;
  fullName: string;
}

declare global {
  namespace Express {
    interface Request {
      auth?: AuthUser;
    }
  }
}

export async function requireAuth(req: Request, _res: Response, next: NextFunction) {
  try {
    const header = req.header('authorization') || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : '';
    if (!token) {
      throw new ApiError(401, 'Sign in to continue', 'UNAUTHENTICATED');
    }
    const payload = jwt.verify(token, env.jwtAccessSecret) as {
      sub?: string;
      sid?: string;
      role?: string;
      type?: string;
    };
    if (payload.type !== 'access' || !payload.sub || !payload.sid) {
      throw new ApiError(401, 'Session expired', 'TOKEN_INVALID');
    }
    const user = await User.findById(payload.sub);
    if (!user || user.status === 'SUSPENDED') {
      throw new ApiError(403, 'This account cannot sign in', 'ACCOUNT_SUSPENDED');
    }
    if (user.status !== 'ACTIVE') {
      throw new ApiError(403, 'Verify your account to continue', 'ACCOUNT_INACTIVE');
    }
    req.auth = {
      id: user.id,
      role: user.role as AuthUser['role'],
      hospitalId: user.hospitalId ? String(user.hospitalId) : undefined,
      sessionId: payload.sid,
      fullName: user.fullName,
    };
    next();
  } catch (error) {
    if (error instanceof ApiError) {
      next(error);
      return;
    }
    next(new ApiError(401, 'Session expired', 'TOKEN_INVALID'));
  }
}

export function requireRole(...roles: AuthUser['role'][]) {
  return (req: Request, _res: Response, next: NextFunction) => {
    if (!req.auth || !roles.includes(req.auth.role)) {
      next(new ApiError(403, 'You do not have access to this action', 'FORBIDDEN'));
      return;
    }
    next();
  };
}
