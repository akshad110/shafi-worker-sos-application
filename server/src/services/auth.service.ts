import crypto from 'crypto';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { OtpChallenge, Session, User, WorkerProfile } from '../models';
import { ApiError } from '../utils/errors';
import { hashToken, normalizeMobile } from '../utils/helpers';

function visibleMobile(mobile?: string | null) {
  if (!mobile || /^p[0-9a-f]{16}$/.test(mobile)) return '';
  return mobile;
}

function publicUser(user: {
  id: string;
  fullName: string;
  mobile: string;
  email: string;
  role: string;
  status: string;
  mobileVerified: boolean;
  hospitalId?: unknown;
}) {
  return {
    id: user.id,
    fullName: user.fullName,
    mobile: visibleMobile(user.mobile),
    email: user.email,
    role: user.role,
    status: user.status,
    mobileVerified: user.mobileVerified,
    hospitalId: user.hospitalId ? String(user.hospitalId) : null,
  };
}

export async function issueTokens(user: any) {
  const expiresAt = new Date(Date.now() + env.refreshTokenDays * 24 * 60 * 60 * 1000);
  const session = await Session.create({ userId: user._id, tokenHash: 'pending', expiresAt });
  const refreshToken = jwt.sign(
    { sub: user.id, sid: session.id, type: 'refresh' },
    env.jwtRefreshSecret,
    { expiresIn: env.refreshTokenDays * 24 * 60 * 60 },
  );
  session.tokenHash = hashToken(refreshToken);
  await session.save();
  const accessToken = jwt.sign(
    { sub: user.id, sid: session.id, role: user.role, type: 'access' },
    env.jwtAccessSecret,
    { expiresIn: env.accessTokenMinutes * 60 },
  );
  return {
    accessToken,
    refreshToken,
    expiresIn: env.accessTokenMinutes * 60,
    user: publicUser(user),
  };
}

async function issueOtp(mobile: string, purpose: 'REGISTER' | 'LOGIN') {
  const code = String(Math.floor(100000 + Math.random() * 900000));
  await OtpChallenge.create({
    mobile,
    purpose,
    codeHash: await bcrypt.hash(code, 8),
    expiresAt: new Date(Date.now() + 5 * 60 * 1000),
  });
  if (env.smsWebhookUrl) {
    try {
      await fetch(env.smsWebhookUrl, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ mobile, purpose, code }),
      });
    } catch {
      // Delivery failure is not logged with the code.
    }
  }
  return env.otpDebug ? code : undefined;
}

export async function register(input: {
  fullName: string;
  mobile?: string;
  email: string;
  password: string;
  healthcareRole: string;
  consent: boolean;
}) {
  if (!input.consent) {
    throw new ApiError(400, 'Accept the terms and privacy policy to continue', 'CONSENT_REQUIRED');
  }
  const providedMobile = Boolean(input.mobile && input.mobile.trim());
  const mobile = providedMobile ? normalizeMobile(input.mobile || '') : `p${crypto.randomBytes(8).toString('hex')}`;
  const email = input.email.toLowerCase().trim();
  const existing = await User.findOne({ $or: [{ mobile }, { email }] });
  if (existing) {
    throw new ApiError(409, 'An account with this email already exists', 'ACCOUNT_EXISTS');
  }
  const user = await User.create({
    role: 'WORKER',
    fullName: input.fullName.trim(),
    mobile,
    email,
    passwordHash: await bcrypt.hash(input.password, 10),
    status: providedMobile ? 'PENDING' : 'ACTIVE',
    mobileVerified: false,
  });
  await WorkerProfile.create({
    userId: user._id,
    healthcareRole: input.healthcareRole.trim(),
    consentAcceptedAt: new Date(),
    verificationStatus: 'INCOMPLETE',
  });
  if (providedMobile) {
    const debugOtp = await issueOtp(mobile, 'REGISTER');
    return { userId: user.id, mobile, debugOtp: debugOtp || null, accessToken: null };
  }
  const tokens = await issueTokens(user);
  return { ...tokens, userId: user.id, mobile: null, debugOtp: null };
}

export async function sendOtp(mobileInput: string, purpose: 'REGISTER' | 'LOGIN') {
  const mobile = normalizeMobile(mobileInput);
  const user = await User.findOne({ mobile });
  if (!user) {
    throw new ApiError(404, 'No account was found for this mobile number', 'NOT_FOUND');
  }
  if (user.status === 'SUSPENDED') {
    throw new ApiError(403, 'This account is suspended', 'ACCOUNT_SUSPENDED');
  }
  const debugOtp = await issueOtp(mobile, purpose);
  return { mobile, debugOtp: debugOtp || null };
}

export async function verifyOtp(mobileInput: string, code: string, purpose: 'REGISTER' | 'LOGIN') {
  const mobile = normalizeMobile(mobileInput);
  const challenge = await OtpChallenge.findOne({ mobile, purpose, consumedAt: null }).sort({ createdAt: -1 });
  if (!challenge || challenge.expiresAt.getTime() < Date.now()) {
    throw new ApiError(400, 'This OTP has expired. Request a new one.', 'OTP_EXPIRED');
  }
  if (challenge.attempts >= 5) {
    throw new ApiError(429, 'Too many incorrect attempts. Request a new OTP.', 'OTP_LOCKED');
  }
  const matches = await bcrypt.compare(code, challenge.codeHash);
  challenge.attempts += 1;
  if (!matches) {
    await challenge.save();
    throw new ApiError(400, 'Incorrect OTP', 'OTP_INVALID');
  }
  challenge.consumedAt = new Date();
  await challenge.save();
  const user = await User.findOne({ mobile });
  if (!user) {
    throw new ApiError(404, 'No account was found for this mobile number', 'NOT_FOUND');
  }
  if (user.status === 'SUSPENDED') {
    throw new ApiError(403, 'This account is suspended', 'ACCOUNT_SUSPENDED');
  }
  user.mobileVerified = true;
  if (user.status === 'PENDING') {
    user.status = 'ACTIVE';
  }
  await user.save();
  return issueTokens(user);
}

export async function login(input: { email?: string; mobile?: string; password: string }) {
  const email = input.email?.toLowerCase().trim();
  const user = email
    ? await User.findOne({ email })
    : await User.findOne({ mobile: normalizeMobile(input.mobile || '') });
  if (!user || !user.passwordHash) {
    throw new ApiError(401, 'Invalid email or password', 'INVALID_CREDENTIALS');
  }
  const matches = await bcrypt.compare(input.password, user.passwordHash);
  if (!matches) {
    throw new ApiError(401, 'Invalid email or password', 'INVALID_CREDENTIALS');
  }
  if (user.status === 'SUSPENDED') {
    throw new ApiError(403, 'This account is suspended', 'ACCOUNT_SUSPENDED');
  }
  if (user.status !== 'ACTIVE') {
    throw new ApiError(403, 'Verify your mobile number before signing in', 'MOBILE_UNVERIFIED');
  }
  return issueTokens(user);
}

export async function refresh(refreshToken: string) {
  let payload: { sub?: string; sid?: string; type?: string };
  try {
    payload = jwt.verify(refreshToken, env.jwtRefreshSecret) as { sub?: string; sid?: string; type?: string };
  } catch {
    throw new ApiError(401, 'Session expired', 'TOKEN_INVALID');
  }
  if (payload.type !== 'refresh' || !payload.sub || !payload.sid) {
    throw new ApiError(401, 'Session expired', 'TOKEN_INVALID');
  }
  const session = await Session.findById(payload.sid);
  if (!session || session.revokedAt || session.expiresAt.getTime() < Date.now()) {
    throw new ApiError(401, 'Session expired', 'TOKEN_INVALID');
  }
  if (session.tokenHash !== hashToken(refreshToken)) {
    throw new ApiError(401, 'Session expired', 'TOKEN_INVALID');
  }
  const user = await User.findById(payload.sub);
  if (!user || user.status !== 'ACTIVE') {
    throw new ApiError(403, 'This account cannot sign in', 'ACCOUNT_SUSPENDED');
  }
  session.revokedAt = new Date();
  const next = await issueTokens(user);
  session.replacedBy = undefined;
  await session.save();
  return next;
}

export async function logout(refreshToken: string) {
  try {
    const payload = jwt.verify(refreshToken, env.jwtRefreshSecret) as { sid?: string; type?: string };
    if (payload.type === 'refresh' && payload.sid) {
      await Session.findByIdAndUpdate(payload.sid, { revokedAt: new Date() });
    }
  } catch {
    // Logging out an already invalid session is still a success.
  }
}

export async function me(userId: string) {
  const user = await User.findById(userId);
  if (!user) {
    throw new ApiError(404, 'Account not found', 'NOT_FOUND');
  }
  return publicUser(user);
}

export async function updatePreferences(userId: string, prefs: { jobs?: boolean; offers?: boolean; shifts?: boolean; payments?: boolean }) {
  const user = await User.findById(userId);
  if (!user) {
    throw new ApiError(404, 'Account not found', 'NOT_FOUND');
  }
  const current = user.preferences || { jobs: true, offers: true, shifts: true, payments: true };
  user.preferences = {
    jobs: prefs.jobs ?? current.jobs,
    offers: prefs.offers ?? current.offers,
    shifts: prefs.shifts ?? current.shifts,
    payments: prefs.payments ?? current.payments,
  };
  await user.save();
  return user.preferences;
}
