import crypto from 'crypto';

export function hashToken(value: string): string {
  return crypto.createHash('sha256').update(value).digest('hex');
}

export function signValue(value: string, secret: string): string {
  return crypto.createHmac('sha256', secret).update(value).digest('hex');
}

export function rupeesToMinor(rupees: number): number {
  return Math.round(rupees * 100);
}

export function minorToRupees(minor: number): number {
  return Math.round(minor) / 100;
}

export function distanceMeters(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const toRad = (deg: number) => (deg * Math.PI) / 180;
  const earth = 6371000;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2
    + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * earth * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function normalizeMobile(input: string): string {
  const digits = input.replace(/[^\d+]/g, '');
  const only = digits.replace(/\+/g, '');
  if (/^\d{10}$/.test(only)) {
    return `+91${only}`;
  }
  if (/^91\d{10}$/.test(only)) {
    return `+${only}`;
  }
  if (digits.startsWith('+') && only.length >= 10 && only.length <= 15) {
    return `+${only}`;
  }
  throw new Error('Enter a valid mobile number');
}

export function dayKey(date: Date, timeZone = 'Asia/Kolkata'): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date);
}

export function zonedDateTime(day: string, time: string, timeZone = 'Asia/Kolkata'): Date {
  const [year, month, date] = day.split('-').map(Number);
  const [hour, minute] = time.split(':').map(Number);
  const utcGuess = new Date(Date.UTC(year, month - 1, date, hour, minute, 0));
  const formatter = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
  const parts = formatter.formatToParts(utcGuess);
  const read = (type: string) => Number(parts.find((part) => part.type === type)?.value || 0);
  const asUtc = Date.UTC(read('year'), read('month') - 1, read('day'), read('hour'), read('minute'), read('second'));
  const offset = asUtc - utcGuess.getTime();
  return new Date(utcGuess.getTime() - offset);
}

const PROFILE_CHECKS: Array<(user: ProfileUser, profile: ProfileShape) => boolean> = [
  (user) => Boolean(user.fullName),
  (user) => Boolean(user.email),
  (_user, profile) => Boolean(profile.healthcareRole),
  (_user, profile) => Boolean(profile.city),
  (_user, profile) => (profile.qualifications || []).length > 0,
  (_user, profile) => (profile.skills || []).length > 0,
  (_user, profile) => profile.experienceYears !== undefined && profile.experienceYears !== null,
  (_user, profile) => Boolean(profile.address),
  (_user, profile) => Boolean(profile.onboardingSubmitted),
];

export interface ProfileUser {
  fullName?: string;
  mobile?: string;
  mobileVerified?: boolean;
  email?: string;
  status?: string;
}

export interface ProfileShape {
  healthcareRole?: string;
  city?: string;
  qualifications?: string[];
  skills?: string[];
  experienceYears?: number | null;
  address?: string;
  onboardingSubmitted?: boolean;
}

export function missingProfileFields(user: ProfileUser, profile: ProfileShape): string[] {
  const missing: string[] = [];
  if (!user.fullName) missing.push('full name');
  if (!profile.healthcareRole) missing.push('healthcare role');
  if (!profile.city) missing.push('city');
  if (!(profile.qualifications || []).length) missing.push('qualifications');
  if (!profile.onboardingSubmitted) missing.push('profile submission');
  return missing;
}

export function profileCompletion(user: ProfileUser, profile: ProfileShape): number {
  const done = PROFILE_CHECKS.filter((check) => check(user, profile)).length;
  return Math.round((done / PROFILE_CHECKS.length) * 100);
}

export function canApply(user: ProfileUser, profile: ProfileShape): boolean {
  return user.status === 'ACTIVE'
    && Boolean(profile.onboardingSubmitted)
    && Boolean(profile.healthcareRole)
    && Boolean(profile.city)
    && (profile.qualifications || []).length > 0;
}
