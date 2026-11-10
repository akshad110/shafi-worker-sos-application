import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import swaggerUi from 'swagger-ui-express';
import { env } from './config/env';
import { errorHandler, notFound } from './middleware/error';
import { openApiSpec } from './openapi';
import { api } from './routes';

export function createApp() {
  const app = express();
  app.disable('x-powered-by');
  app.use(helmet({ contentSecurityPolicy: false }));
  app.use(cors({ origin: env.corsOrigins.length ? env.corsOrigins : false }));
  app.use((req, res, next) => {
    if (req.path === '/api/v1/payments/razorpay/webhook') {
      express.raw({ type: '*/*', limit: '1mb' })(req, res, next);
      return;
    }
    express.json({ limit: '1mb' })(req, res, next);
  });
  app.use(morgan(env.isProduction ? 'combined' : 'dev'));
  app.use('/api/v1/auth', rateLimit({ windowMs: 15 * 60 * 1000, limit: 40, standardHeaders: true, legacyHeaders: false }));
  app.use(rateLimit({ windowMs: 60 * 1000, limit: 300, standardHeaders: true, legacyHeaders: false }));
  app.get('/health', (_req, res) => {
    res.json({ success: true, message: 'ok', data: { service: 'hospital-sos' } });
  });
  app.use('/api/docs', swaggerUi.serve, swaggerUi.setup(openApiSpec));
  app.use('/api/v1', api);
  app.use(notFound);
  app.use(errorHandler);
  return app;
}
