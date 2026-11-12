import crypto from 'crypto';
import mongoose from 'mongoose';
import { ApiError } from '../utils/errors';
import { zonedDateTime } from '../utils/helpers';

type Doc = Record<string, any>;

function collection(name: string) {
  const db = mongoose.connection.db;
  if (!db) throw new ApiError(503, 'Database is not connected', 'DB_UNAVAILABLE');
  return db.collection(name);
}

function sharedId(): string {
  return crypto.randomBytes(4).toString('hex');
}

function asList(value: unknown): string[] {
  if (Array.isArray(value)) return value.map((item) => String(item)).filter(Boolean);
  if (typeof value === 'string' && value.trim()) return value.split(',').map((item) => item.trim()).filter(Boolean);
  return [];
}

function payUnit(unit: unknown): string {
  const value = String(unit || '').toLowerCase();
  if (value.includes('hour')) return 'PER_HOUR';
  if (value.includes('day')) return 'PER_DAY';
  return 'PER_SHIFT';
}

function shiftType(value: unknown): string {
  const raw = String(value || '').toUpperCase();
  if (raw.includes('NIGHT')) return 'NIGHT';
  if (raw.includes('DAY')) return 'DAY';
  return raw || 'CUSTOM';
}

export function isHospitalJob(doc: Doc | null | undefined): boolean {
  return Boolean(doc && typeof doc.id === 'string' && doc.amount !== undefined && doc.compensationMinor === undefined);
}

const TO_APP: Record<string, string> = {
  under_review: 'UNDER_REVIEW',
  shortlisted: 'SHORTLISTED',
  offered: 'OFFERED',
  confirmed: 'ACCEPTED',
  rejected: 'REJECTED',
  withdrawn: 'WITHDRAWN',
};

const TO_HOSPITAL: Record<string, string> = {
  UNDER_REVIEW: 'under_review',
  SUBMITTED: 'under_review',
  SHORTLISTED: 'shortlisted',
  OFFERED: 'offered',
  ACCEPTED: 'confirmed',
  REJECTED: 'rejected',
  WITHDRAWN: 'withdrawn',
};

export function toHospitalStatus(status: string): string {
  return TO_HOSPITAL[status] || status.toLowerCase();
}

export function toAppStatus(status: string): string {
  return TO_APP[status] || status.toUpperCase();
}

async function hospitalName(hospitalId: string): Promise<{ name: string; verified: boolean; city: string; state: string }> {
  const hospital = await collection('hospitals').findOne({ id: hospitalId });
  return {
    name: hospital?.name || 'Hospital',
    verified: Boolean(hospital?.verified),
    city: hospital?.city || '',
    state: hospital?.state || hospital?.stateName || '',
  };
}

export function presentJob(job: Doc, hospital?: { name: string; verified: boolean; city: string; state?: string }) {
  const place = hospital || { name: 'Hospital', verified: false, city: '', state: '' };
  const location = typeof job.location === 'string' ? job.location : '';
  const amount = Number(job.amount) || 0;
  let startAt: Date | null = null;
  let endAt: Date | null = null;
  if (job.date && job.startTime && job.endTime) {
    try {
      startAt = zonedDateTime(job.date, job.startTime);
      endAt = zonedDateTime(job.date, job.endTime);
      if (endAt.getTime() <= startAt.getTime()) endAt = new Date(endAt.getTime() + 24 * 60 * 60 * 1000);
    } catch {
      startAt = null;
      endAt = null;
    }
  }
  return {
    id: job.id,
    hospitalId: String(job.hospitalId || ''),
    hospitalName: place.name,
    hospitalVerified: place.verified,
    title: job.title || job.role || 'Shift',
    healthcareRole: job.role || '',
    department: job.department || '',
    city: place.city || location,
    state: place.state || job.state || job.stateName || '',
    address: location,
    shiftDate: job.date || '',
    startTime: job.startTime || '',
    endTime: job.endTime || '',
    startAt,
    endAt,
    shiftType: shiftType(job.shiftType),
    compensationMinor: Math.round(amount * 100),
    currency: job.currency || 'INR',
    paymentUnit: payUnit(job.paymentUnit),
    vacancies: Number(job.workersNeeded) || 1,
    filledCount: 0,
    urgent: Boolean(job.urgent),
    deadline: null,
    status: job.status === 'approved' ? 'OPEN' : String(job.status || '').toUpperCase(),
    distanceKm: null,
    latitude: null,
    longitude: null,
    description: job.description || '',
    qualifications: asList(job.qualifications),
    experienceYears: Number.parseInt(String(job.experience || '0'), 10) || 0,
    skills: asList(job.skills),
    certifications: asList(job.certifications),
    reportingInstructions: job.notes || '',
    geofenceEnabled: false,
    createdAt: job.createdAt ? new Date(job.createdAt) : new Date(),
  };
}

export async function loadPublishedJobs(): Promise<Doc[]> {
  const jobs = await collection('jobs').find({ status: 'approved' }).sort({ createdAt: -1 }).toArray();
  const ids = [...new Set(jobs.map((job) => String(job.hospitalId || '')).filter(Boolean))];
  const hospitals = ids.length ? await collection('hospitals').find({ id: { $in: ids } }).toArray() : [];
  const byId = new Map(hospitals.map((hospital) => [hospital.id, hospital]));
  return jobs.map((job) => {
    const hospital = byId.get(job.hospitalId);
    return presentJob(job, hospital ? {
      name: hospital.name,
      verified: Boolean(hospital.verified),
      city: hospital.city || '',
      state: hospital.state || hospital.stateName || '',
    } : undefined);
  });
}

export async function loadHospitalJob(jobId: string): Promise<Doc | null> {
  const job = await collection('jobs').findOne({ id: jobId });
  if (!isHospitalJob(job) || !job) return null;
  const place = await hospitalName(String(job.hospitalId || ''));
  return presentJob(job, place);
}

async function workerIdsFor(accountId: string): Promise<string[]> {
  const links = await collection('workerLinks').find({ accountId }).toArray();
  return links.map((link) => String(link.workerId));
}

export async function ensureHospitalWorker(accountId: string, hospitalId: string, user: Doc, profile: Doc) {
  const existing = await collection('workerLinks').findOne({ accountId, hospitalId });
  if (existing) {
    await collection('workers').updateOne({ id: existing.workerId }, {
      $set: {
        name: user.fullName,
        phone: user.mobile && !String(user.mobile).startsWith('p') ? user.mobile : '',
        role: profile.healthcareRole || '',
        qualifications: profile.qualifications || [],
        skills: profile.skills || [],
        experience: profile.experienceYears ? `${profile.experienceYears} years` : '',
        city: profile.city || '',
        availability: asList(profile.preferredShifts).join(', '),
      },
    });
    return existing.workerId as string;
  }
  const workerId = sharedId();
  await collection('workers').insertOne({
    id: workerId,
    hospitalId,
    name: user.fullName,
    phone: user.mobile && !String(user.mobile).startsWith('p') ? user.mobile : '',
    role: profile.healthcareRole || '',
    qualifications: profile.qualifications || [],
    skills: profile.skills || [],
    experience: profile.experienceYears ? `${profile.experienceYears} years` : '',
    verified: profile.verificationStatus === 'VERIFIED',
    city: profile.city || '',
    availability: asList(profile.preferredShifts).join(', '),
    sourceAccountId: accountId,
  });
  await collection('workerLinks').insertOne({ accountId, hospitalId, workerId });
  return workerId;
}

export async function findHospitalApplication(jobId: string, accountId: string) {
  const workerIds = await workerIdsFor(accountId);
  if (!workerIds.length) return null;
  return collection('applications').findOne({ jobId, workerId: { $in: workerIds } });
}

export async function createHospitalApplication(
  accountId: string,
  user: Doc,
  profile: Doc,
  job: Doc,
  extra?: { note?: string; documents?: Doc[] },
) {
  const workerId = await ensureHospitalWorker(accountId, job.hospitalId, user, profile);
  const duplicate = await collection('applications').findOne({ jobId: job.id, workerId });
  if (duplicate) throw new ApiError(409, 'You have already applied for this job', 'DUPLICATE_APPLICATION');
  const now = new Date().toISOString();
  const application = {
    id: sharedId(),
    hospitalId: job.hospitalId,
    jobId: job.id,
    workerId,
    status: 'under_review',
    appliedAt: now,
    timeline: [{
      status: 'under_review',
      at: now,
      note: extra?.note ? `Applied from the worker app. ${extra.note}` : 'Applied from the worker app',
    }],
    notes: [],
    documents: extra?.documents || [],
    sourceAccountId: accountId,
  };
  await collection('applications').insertOne(application);
  if (extra?.documents?.length) {
    await collection('workers').updateOne({ id: workerId }, { $set: { documents: extra.documents } });
  }
  return application;
}

export async function hospitalApplicationsFor(accountId: string) {
  const workerIds = await workerIdsFor(accountId);
  const or: Doc[] = [{ sourceAccountId: accountId }];
  if (workerIds.length) or.push({ workerId: { $in: workerIds } });
  return collection('applications').find({ $or: or }).sort({ appliedAt: -1 }).toArray();
}

export async function hospitalShiftsFor(accountId: string) {
  const workerIds = await workerIdsFor(accountId);
  if (!workerIds.length) return [];
  return collection('shifts').find({ workerId: { $in: workerIds } }).sort({ date: 1, startTime: 1 }).toArray();
}

export async function linkedWorkerIds(accountId: string) {
  return workerIdsFor(accountId);
}

export async function rawReviewsFor(accountId: string) {
  const ids = await workerIdsFor(accountId);
  const or: Doc[] = [{ accountId }, { workerAccountId: accountId }, { workerId: accountId }];
  if (ids.length) {
    or.push({ workerId: { $in: ids } });
    or.push({ accountId: { $in: ids } });
  }
  const reviews = await collection('reviews').find({ $or: or }).sort({ createdAt: -1 }).toArray();
  const people = ids.length ? await collection('workers').find({ id: { $in: ids } }).toArray() : [];
  return { reviews, people };
}

export async function saveReview(filter: Doc, fields: Doc) {
  const now = new Date();
  await collection('reviews').updateOne(filter, {
    $set: { ...fields, updatedAt: now },
    $setOnInsert: { id: sharedId(), createdAt: now },
  }, { upsert: true });
}

export async function accountForHospitalWorker(workerId: string) {
  const link = await collection('workerLinks').findOne({ workerId });
  return link?.accountId ? String(link.accountId) : '';
}

export function presentApplication(application: Doc, job: Doc | null) {
  const history = Array.isArray(application.timeline) ? application.timeline.map((item: Doc) => ({
    status: toAppStatus(String(item.status || '')),
    at: item.at,
    note: item.note || '',
  })) : [];
  return {
    id: application.id,
    reference: application.id,
    status: toAppStatus(String(application.status || 'under_review')),
    createdAt: application.appliedAt,
    updatedAt: history.length ? history[history.length - 1].at : application.appliedAt,
    history,
    job: job ? {
      id: job.id,
      title: job.title,
      hospitalName: job.hospitalName,
      department: job.department,
      shiftDate: job.shiftDate,
      startTime: job.startTime,
      endTime: job.endTime,
      compensationMinor: job.compensationMinor,
      currency: job.currency,
      paymentUnit: job.paymentUnit,
      city: job.city,
    } : null,
    placement: null,
  };
}

export function attendanceMark(shift: Doc): string {
  const raw = shift.attendance;
  const fromValue = (value: unknown): string => {
    const text = String(value || '').toLowerCase();
    if (text.includes('late')) return 'LATE';
    if (text.includes('half')) return 'HALF';
    if (text.includes('absent') || text === 'a') return 'ABSENT';
    if (text.includes('present') || text === 'p') return 'PRESENT';
    return '';
  };
  if (typeof raw === 'string') {
    const mark = fromValue(raw);
    if (mark) return mark;
  }
  if (Array.isArray(raw)) {
    const row = raw.find((item) => item && item.date && item.date === shift.date) || raw[0];
    if (row) {
      const mark = fromValue(row.status || row.mark || row.value);
      if (mark) return mark;
    }
  } else if (raw && typeof raw === 'object') {
    const mark = fromValue(raw.status || raw.mark || raw.value);
    if (mark) return mark;
  }
  const status = String(shift.status || '').toLowerCase();
  if (status === 'cancelled' || status === 'canceled') return 'ABSENT';
  if (shift.checkIn?.at) {
    const at = new Date(shift.checkIn.at).getTime();
    const start = shift.startAt ? new Date(shift.startAt).getTime() : 0;
    if (start && at > start + 15 * 60 * 1000) return 'LATE';
    return 'PRESENT';
  }
  return 'UNMARKED';
}

export function presentShift(shift: Doc, hospital?: { name: string; city: string }, job?: Doc | null) {
  const status = String(shift.status || '').toLowerCase();
  const mapped = status === 'active' ? 'ACTIVE' : status === 'upcoming' ? 'UPCOMING' : status === 'cancelled' || status === 'canceled' ? 'CANCELLED' : 'COMPLETED';
  const hospitalName = hospital?.name || job?.hospitalName || 'Hospital';
  const city = hospital?.city || job?.city || '';
  const address = typeof shift.location === 'string' ? shift.location : (job?.address || '');
  const shiftDate = shift.date || job?.shiftDate || '';
  const startTime = shift.startTime || job?.startTime || '';
  const endTime = shift.endTime || job?.endTime || '';
  const department = shift.department || job?.department || '';
  const role = shift.role || job?.healthcareRole || job?.title || 'Shift';
  const fullMinor = job?.compensationMinor ?? Math.round((Number(shift.amount) || 0) * 100);
  const mark = attendanceMark(shift);
  const stored = storedPayMinor(shift);
  const payment = paymentFor(fullMinor, mark, mapped, stored);
  return {
    id: shift.id,
    status: mapped,
    attendanceMark: mark,
    hospitalName,
    department,
    city,
    address,
    shiftDate,
    startTime,
    endTime,
    compensationMinor: fullMinor,
    currency: 'INR',
    paymentUnit: payUnit(shift.paymentUnit || job?.paymentUnit),
    job: {
      id: job?.id || shift.jobId || '',
      title: job?.title || role,
      hospitalName,
      healthcareRole: role,
      department,
      city,
      address,
      shiftDate,
      startTime,
      endTime,
      compensationMinor: fullMinor,
      currency: 'INR',
      paymentUnit: payUnit(shift.paymentUnit || job?.paymentUnit),
    },
    payment,
  };
}

function storedPayMinor(shift: Doc): number | null {
  const raw = shift.attendance;
  const direct = [shift.paidAmountMinor, shift.paymentAmountMinor, raw?.amountMinor];
  for (const value of direct) {
    if (typeof value === 'number' && !Number.isNaN(value)) return Math.round(value);
  }
  const rupees = [shift.paidAmount, shift.paymentAmount, raw?.amount, raw?.pay];
  for (const value of rupees) {
    if (typeof value === 'number' && !Number.isNaN(value)) return Math.round(value * 100);
  }
  return null;
}

export function paymentFor(fullMinor: number, mark: string, status: string, stored: number | null) {
  const waiting = (status === 'UPCOMING' || status === 'ACTIVE') && (!mark || mark === 'UNMARKED');
  if (waiting) {
    return {
      recorded: false,
      amountMinor: 0,
      currency: 'INR',
      status: 'NOT_RECORDED',
      note: 'Payment is recorded after the hospital marks attendance.',
    };
  }
  const amount = stored != null ? stored : shareOf(fullMinor, mark);
  let note = 'Present, so the full shift pay is recorded.';
  if (mark === 'ABSENT') note = 'Absent, so no payment. ₹0.';
  else if (mark === 'HALF') note = 'Half day, so half the shift pay is recorded.';
  else if (mark === 'LATE') note = 'Late, so 75% of the shift pay is recorded.';
  else if (mark === 'UNMARKED') note = 'The shift is complete. Attendance is not marked yet.';
  if (stored != null) note = `${note} The hospital stored this amount.`;
  return {
    recorded: mark !== 'UNMARKED',
    amountMinor: amount,
    currency: 'INR',
    status: amount === 0 ? 'NO_PAY' : 'RECORDED',
    note,
  };
}

function shareOf(fullMinor: number, mark: string): number {
  if (mark === 'ABSENT') return 0;
  if (mark === 'HALF') return Math.round(fullMinor / 2);
  if (mark === 'LATE') return Math.round(fullMinor * 0.75);
  if (mark === 'PRESENT') return fullMinor;
  return 0;
}

export async function presentedHospitalShift(accountId: string, shiftId: string) {
  const shifts = await hospitalShiftsFor(accountId);
  const shift = shifts.find((item) => item.id === shiftId);
  if (!shift) return null;
  const place = shift.jobId ? await loadHospitalJob(String(shift.jobId)) : null;
  return presentShift(shift, place ? { name: place.hospitalName, city: place.city } : undefined, place);
}
