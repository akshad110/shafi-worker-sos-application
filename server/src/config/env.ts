import crypto from 'crypto';

function required(name: string): string {
  const value = process.env[name];
  if (!value || !value.trim()) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value.trim();
}

export function withDatabase(uri: string, dbName: string): string {
  const queryIndex = uri.indexOf('?');
  const base = queryIndex === -1 ? uri : uri.slice(0, queryIndex);
  const query = queryIndex === -1 ? '' : uri.slice(queryIndex);
  const schemeIndex = base.indexOf('://');
  if (schemeIndex === -1) {
    throw new Error('MONGODB_URI is not a valid connection string');
  }
  const rest = base.slice(schemeIndex + 3);
  const slash = rest.indexOf('/');
  const authority = slash === -1 ? rest : rest.slice(0, slash);
  return `${base.slice(0, schemeIndex + 3)}${authority}/${dbName}${query}`;
}

export const env = {
  get nodeEnv() {
    return process.env.NODE_ENV || 'development';
  },
  get isProduction() {
    return this.nodeEnv === 'production';
  },
  get port() {
    return Number(process.env.PORT || 4000);
  },
  get mongoUri() {
    const uri = required('MONGODB_URI');
    if (this.nodeEnv === 'test') {
      return withDatabase(uri, 'workersos_test');
    }
    return uri;
  },
  get jwtAccessSecret() {
    return required('JWT_ACCESS_SECRET');
  },
  get jwtRefreshSecret() {
    return required('JWT_REFRESH_SECRET');
  },
  get accessTokenMinutes() {
    return Number(process.env.ACCESS_TOKEN_MINUTES || 15);
  },
  get refreshTokenDays() {
    return Number(process.env.REFRESH_TOKEN_DAYS || 30);
  },
  get otpDebug() {
    return !this.isProduction && process.env.OTP_DEBUG === 'true';
  },
  get smsWebhookUrl() {
    return process.env.SMS_WEBHOOK_URL || '';
  },
  get corsOrigins() {
    const raw = process.env.CORS_ORIGINS || '';
    if (!raw.trim()) {
      return this.isProduction ? [] : ['http://localhost:3000', 'http://localhost:5173'];
    }
    return raw.split(',').map((item) => item.trim()).filter(Boolean);
  },
  get s3() {
    return {
      endpoint: process.env.S3_ENDPOINT || '',
      bucket: process.env.S3_BUCKET || '',
      accessKey: process.env.S3_ACCESS_KEY || '',
      secretKey: process.env.S3_SECRET_KEY || '',
      region: process.env.S3_REGION || 'auto',
    };
  },
  get firebaseJson() {
    return process.env.FIREBASE_SERVICE_ACCOUNT_JSON || '';
  },
  get whatsappToken() {
    return process.env.WHATSAPP_TOKEN || '';
  },
  get whatsappPhoneId() {
    return process.env.WHATSAPP_PHONE_ID || '';
  },
  get razorpayWebhookSecret() {
    return process.env.RAZORPAY_WEBHOOK_SECRET || '';
  },
};

export function assertEnv(): void {
  required('MONGODB_URI');
  const access = required('JWT_ACCESS_SECRET');
  const refresh = required('JWT_REFRESH_SECRET');
  if (access.length < 24 || refresh.length < 24) {
    throw new Error('JWT secrets must be at least 24 characters');
  }
  if (env.isProduction && process.env.OTP_DEBUG === 'true') {
    throw new Error('OTP_DEBUG cannot be enabled in production');
  }
  if (!env.mongoUri.startsWith('mongodb://') && !env.mongoUri.startsWith('mongodb+srv://')) {
    throw new Error('MONGODB_URI must use the mongodb scheme');
  }
}

export function safeLog(error: unknown): string {
  const message = error instanceof Error ? error.message : String(error);
  return message.replace(/mongodb(?:\+srv)?:\/\/\S+/gi, 'mongodb://***');
}

export function randomToken(bytes = 32): string {
  return crypto.randomBytes(bytes).toString('hex');
}
