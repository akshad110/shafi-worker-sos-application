import { NextFunction, Request, Response } from 'express';
import { safeLog } from '../config/env';
import { ApiError } from '../utils/errors';

export function notFound(_req: Request, res: Response) {
  res.status(404).json({
    success: false,
    message: 'The requested resource was not found',
    code: 'NOT_FOUND',
  });
}

export function errorHandler(error: unknown, _req: Request, res: Response, _next: NextFunction) {
  if (error instanceof ApiError) {
    res.status(error.status).json({
      success: false,
      message: error.message,
      code: error.code,
    });
    return;
  }
  console.error(safeLog(error));
  res.status(500).json({
    success: false,
    message: 'Something went wrong',
    code: 'INTERNAL',
  });
}
