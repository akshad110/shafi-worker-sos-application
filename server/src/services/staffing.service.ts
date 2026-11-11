import mongoose from 'mongoose';
import path from 'path';
import {
  Application, AuditLog, Hospital, Job, Notification, Payment, Placement, Shift,
  Subscription, SubscriptionPlan, User, WorkerDocument, WorkerProfile,
} from '../models';
import { ApiError, isDuplicateKey } from '../utils/errors';
import {
  canApply, distanceMeters, missingProfileFields, normalizeMobile, profileCompletion, zonedDateTime,
} from '../utils/helpers';
import { env } from '../config/env';
import { signValue } from '../utils/helpers';
import { accessUrl, readLocalObject, savePrivateObject } from './storage';
import { notify, queueWhatsApp } from './notify';
import {
  createHospitalApplication, findHospitalApplication, hospitalApplicationsFor, hospitalShiftsFor,
  loadHospitalJob, loadPublishedJobs, paymentFor, presentApplication, presentShift, presentedHospitalShift, toAppStatus,
} from './hospital-data';

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function jobCard(job: any, distanceKm: number | null = null) {
  return {
    id: job.id,
    hospitalId: String(job.hospitalId),
    hospitalName: job.hospitalName,
    hospitalVerified: job.hospitalVerified,
    title: job.title,
    healthcareRole: job.healthcareRole,
    department: job.department,
    city: job.city,
    state: job.state || '',
    address: job.address,
    shiftDate: job.shiftDate,
    startTime: job.startTime,
    endTime: job.endTime,
    startAt: job.startAt,
    endAt: job.endAt,
    shiftType: job.shiftType,
    compensationMinor: job.compensationMinor,
    currency: job.currency,
    paymentUnit: job.paymentUnit,
    vacancies: job.vacancies,
    filledCount: job.filledCount,
    urgent: job.urgent,
    deadline: job.deadline,
    status: job.status,
    distanceKm,
    latitude: job.location?.coordinates?.length === 2 ? job.location.coordinates[1] : null,
    longitude: job.location?.coordinates?.length === 2 ? job.location.coordinates[0] : null,
  };
}

function jobDetail(job: any, distanceKm: number | null = null) {
  return {
    ...jobCard(job, distanceKm),
    description: job.description,
    qualifications: job.qualifications,
    experienceYears: job.experienceYears,
    skills: job.skills,
    certifications: job.certifications,
    reportingInstructions: job.reportingInstructions,
    geofenceEnabled: Boolean(job.geofenceMeters && job.location?.coordinates?.length === 2),
  };
}

async function loadWorker(userId: string) {
  const user = await User.findById(userId);
  const profile = await WorkerProfile.findOne({ userId });
  if (!user || !profile) {
    throw new ApiError(404, 'Worker profile not found', 'NOT_FOUND');
  }
  return { user, profile };
}

function profilePayload(user: any, profile: any) {
  const missing = missingProfileFields(user, profile);
  return {
    user: {
      id: user.id,
      fullName: user.fullName,
      mobile: user.mobile && /^p[0-9a-f]{16}$/.test(user.mobile) ? '' : user.mobile,
      email: user.email,
      role: user.role,
      status: user.status,
      mobileVerified: user.mobileVerified,
    },
    profile,
    completion: profileCompletion(user, profile),
    canApply: canApply(user, profile),
    missing,
    preferences: user.preferences,
  };
}

export async function getProfile(userId: string) {
  const { user, profile } = await loadWorker(userId);
  return profilePayload(user, profile);
}

export async function updateProfile(userId: string, patch: Record<string, unknown>) {
  const { user, profile } = await loadWorker(userId);
  if (typeof patch.fullName === 'string' && patch.fullName.trim()) {
    user.fullName = patch.fullName.trim();
  }
  if (typeof patch.mobile === 'string' && patch.mobile.trim()) {
    const mobile = normalizeMobile(patch.mobile);
    const taken = await User.findOne({ mobile, _id: { $ne: user._id } });
    if (taken) throw new ApiError(409, 'This mobile number is already in use', 'ACCOUNT_EXISTS');
    user.mobile = mobile;
  }
  const fields = [
    'healthcareRole', 'specialization', 'address', 'city',
    'emergencyContactName', 'emergencyContactPhone',
  ] as const;
  fields.forEach((field) => {
    if (typeof patch[field] === 'string') {
      (profile as any)[field] = String(patch[field]).trim();
    }
  });
  (['qualifications', 'skills', 'preferredDepartments', 'preferredShifts', 'preferredLocations'] as const).forEach((field) => {
    if (Array.isArray(patch[field])) {
      (profile as any)[field] = (patch[field] as unknown[]).map((item) => String(item).trim()).filter(Boolean);
    }
  });
  if (patch.experienceYears !== undefined && patch.experienceYears !== null && patch.experienceYears !== '') {
    profile.experienceYears = Number(patch.experienceYears);
  }
  if (patch.onboardingStep !== undefined) {
    profile.onboardingStep = Number(patch.onboardingStep);
  }
  if (patch.onboardingSubmitted === true) {
    profile.onboardingSubmitted = true;
    if (profile.verificationStatus === 'INCOMPLETE') {
      profile.verificationStatus = 'PENDING';
    }
  }
  await user.save();
  await profile.save();
  return profilePayload(user, profile);
}

export async function updateAvailability(userId: string, patch: Record<string, unknown>) {
  const { profile } = await loadWorker(userId);
  if (Array.isArray(patch.weeklyAvailability)) {
    profile.weeklyAvailability = patch.weeklyAvailability as never;
  }
  if (Array.isArray(patch.preferredShifts)) {
    profile.preferredShifts = (patch.preferredShifts as unknown[]).map(String);
  }
  if (Array.isArray(patch.unavailableDates)) {
    profile.unavailableDates = (patch.unavailableDates as unknown[]).map(String);
  }
  if (Array.isArray(patch.preferredLocations)) {
    profile.preferredLocations = (patch.preferredLocations as unknown[]).map(String);
  }
  await profile.save();
  return {
    weeklyAvailability: profile.weeklyAvailability,
    preferredShifts: profile.preferredShifts,
    unavailableDates: profile.unavailableDates,
    preferredLocations: profile.preferredLocations,
  };
}

async function openJobs() {
  return Job.find({
    status: 'OPEN',
    $or: [{ deadline: { $exists: false } }, { deadline: null }, { deadline: { $gt: new Date() } }],
  });
}

function scoreJob(job: any, profile: any) {
  let score = 0;
  if (profile?.healthcareRole && job.healthcareRole === profile.healthcareRole) score += 5;
  const skills = new Set((profile?.skills || []).map((item: string) => item.toLowerCase()));
  (job.skills || []).forEach((skill: string) => {
    if (skills.has(skill.toLowerCase())) score += 2;
  });
  if (job.urgent) score += 3;
  if (job.hospitalVerified) score += 1;
  if (profile?.city && job.city && profile.city.toLowerCase() === job.city.toLowerCase()) score += 2;
  return score;
}

export async function listJobs(query: Record<string, string>, workerId?: string) {
  const shared = await loadPublishedJobs();
  if (shared.length) {
    const page = Math.max(1, Number(query.page) || 1);
    const limit = Math.min(50, Math.max(1, Number(query.limit) || 20));
    let items = shared.slice();
    if (query.q) {
      const q = query.q.toLowerCase();
      items = items.filter((job) => [job.title, job.hospitalName, job.city, job.state, job.address, job.department, job.healthcareRole]
        .some((value) => String(value || '').toLowerCase().includes(q)));
    }
    if (query.role) items = items.filter((job) => job.healthcareRole === query.role);
    if (query.department) items = items.filter((job) => String(job.department).toLowerCase().includes(query.department.toLowerCase()));
    if (query.shiftType) items = items.filter((job) => job.shiftType === query.shiftType.toUpperCase());
    if (query.date) items = items.filter((job) => job.shiftDate === query.date);
    if (query.urgent === 'true') items = items.filter((job) => job.urgent);
    if (query.minPay) items = items.filter((job) => job.compensationMinor >= Number(query.minPay));
    if (query.maxPay) items = items.filter((job) => job.compensationMinor <= Number(query.maxPay));
    if (query.sort === 'compensation') items.sort((a, b) => b.compensationMinor - a.compensationMinor);
    else if (query.sort === 'relevance' && workerId) {
      const profile = await WorkerProfile.findOne({ userId: workerId });
      items.sort((a, b) => scoreJob(b, profile) - scoreJob(a, profile));
    }
    const slice = items.slice((page - 1) * limit, page * limit);
    return { items: slice, page, limit, total: items.length, hasMore: page * limit < items.length };
  }
  const page = Math.max(1, Number(query.page) || 1);
  const limit = Math.min(50, Math.max(1, Number(query.limit) || 20));
  const filter: Record<string, unknown> = {
    status: 'OPEN',
    $and: [{ $or: [{ deadline: { $exists: false } }, { deadline: null }, { deadline: { $gt: new Date() } }] }],
  };
  const and = filter.$and as unknown[];
  if (query.q) {
    const rx = new RegExp(escapeRegex(query.q), 'i');
    and.push({ $or: [{ title: rx }, { hospitalName: rx }, { city: rx }, { state: rx }, { address: rx }, { department: rx }, { healthcareRole: rx }] });
  }
  if (query.role) filter.healthcareRole = query.role;
  if (query.department) filter.department = new RegExp(escapeRegex(query.department), 'i');
  if (query.shiftType) filter.shiftType = query.shiftType;
  if (query.date) filter.shiftDate = query.date;
  if (query.urgent === 'true') filter.urgent = true;
  if (query.minPay) filter.compensationMinor = { ...(filter.compensationMinor as object || {}), $gte: Number(query.minPay) };
  if (query.maxPay) filter.compensationMinor = { ...(filter.compensationMinor as object || {}), $lte: Number(query.maxPay) };

  let jobs = await Job.find(filter).sort(query.sort === 'compensation' ? { compensationMinor: -1 } : { createdAt: -1 }).limit(300);
  const lat = query.lat ? Number(query.lat) : undefined;
  const lng = query.lng ? Number(query.lng) : undefined;
  const radius = query.radiusKm ? Number(query.radiusKm) : undefined;
  let decorated = jobs.map((job) => {
    let distanceKm: number | null = null;
    if (lat !== undefined && lng !== undefined && job.location?.coordinates?.length === 2) {
      const meters = distanceMeters(lat, lng, job.location.coordinates[1], job.location.coordinates[0]);
      distanceKm = Math.round((meters / 1000) * 10) / 10;
    }
    return { job, distanceKm };
  });
  if (radius && lat !== undefined && lng !== undefined) {
    decorated = decorated.filter((item) => item.distanceKm !== null && item.distanceKm <= radius);
  }
  if (query.sort === 'relevance' && workerId) {
    const profile = await WorkerProfile.findOne({ userId: workerId });
    decorated.sort((a, b) => scoreJob(b.job, profile) - scoreJob(a.job, profile));
  }
  const total = decorated.length;
  const slice = decorated.slice((page - 1) * limit, page * limit);
  return {
    items: slice.map((item) => jobCard(item.job, item.distanceKm)),
    page,
    limit,
    total,
    hasMore: page * limit < total,
  };
}

export async function getJob(jobId: string, workerId?: string) {
  const shared = await loadHospitalJob(jobId);
  if (shared) {
    const applied = workerId ? await findHospitalApplication(jobId, workerId) : null;
    let eligible = false;
    let eligibilityReason = '';
    if (workerId) {
      const { user, profile } = await loadWorker(workerId);
      eligible = canApply(user, profile) && !applied && shared.status === 'OPEN';
      if (applied) eligibilityReason = 'You have already applied for this job';
      else if (!canApply(user, profile)) eligibilityReason = `Complete your profile: ${missingProfileFields(user, profile).join(', ')}`;
      else if (shared.status !== 'OPEN') eligibilityReason = 'This job is no longer open';
    }
    return { ...shared, hasApplied: Boolean(applied), applicationId: applied?.id || null, eligible, eligibilityReason };
  }
  const job = await Job.findById(jobId);
  if (!job) throw new ApiError(404, 'Job not found', 'NOT_FOUND');
  const applied = workerId ? await Application.findOne({ jobId, workerId }) : null;
  let eligible = false;
  let eligibilityReason = '';
  if (workerId) {
    const { user, profile } = await loadWorker(workerId);
    eligible = canApply(user, profile) && !applied && job.status === 'OPEN';
    if (applied) eligibilityReason = 'You have already applied for this job';
    else if (!canApply(user, profile)) eligibilityReason = `Complete your profile: ${missingProfileFields(user, profile).join(', ')}`;
    else if (job.status !== 'OPEN') eligibilityReason = 'This job is no longer open';
  }
  return { ...jobDetail(job), hasApplied: Boolean(applied), applicationId: applied?.id || null, eligible, eligibilityReason };
}

export async function recommendedJobs(workerId: string) {
  const shared = await loadPublishedJobs();
  if (shared.length) {
    const profile = await WorkerProfile.findOne({ userId: workerId });
    const appliedIds = new Set((await hospitalApplicationsFor(workerId)).map((item) => String(item.jobId)));
    return shared
      .filter((job) => !appliedIds.has(job.id))
      .map((job) => ({ job, score: scoreJob(job, profile) }))
      .sort((a, b) => b.score - a.score)
      .slice(0, 8)
      .map((item) => item.job);
  }
  const profile = await WorkerProfile.findOne({ userId: workerId });
  const applied = new Set((await Application.find({ workerId }).select('jobId')).map((item) => String(item.jobId)));
  const jobs = await openJobs();
  return jobs
    .map((job) => ({ job, score: scoreJob(job, profile) }))
    .filter((item) => !applied.has(item.job.id))
    .sort((a, b) => b.score - a.score || b.job.createdAt.getTime() - a.job.createdAt.getTime())
    .slice(0, 8)
    .map((item) => jobCard(item.job));
}

export async function urgentJobs() {
  const shared = await loadPublishedJobs();
  const urgent = shared.filter((job) => job.urgent);
  if (urgent.length || shared.length) return urgent;
  const jobs = await Job.find({
    status: 'OPEN',
    urgent: true,
    $or: [{ deadline: { $exists: false } }, { deadline: null }, { deadline: { $gt: new Date() } }],
  }).sort({ createdAt: -1 }).limit(20);
  return jobs.map((job) => jobCard(job));
}

async function applicationView(application: any) {
  const job = await Job.findById(application.jobId);
  const placement = await Placement.findOne({ applicationId: application._id });
  return {
    id: application.id,
    reference: application.reference,
    status: application.status,
    createdAt: application.createdAt,
    updatedAt: application.updatedAt,
    history: application.history,
    job: job ? jobCard(job) : null,
    placementId: placement?.id || null,
  };
}

export async function applyToJob(workerId: string, jobId: string, note?: string) {
  const { user, profile } = await loadWorker(workerId);
  if (!canApply(user, profile)) {
    throw new ApiError(403, 'Complete your profile before applying', 'PROFILE_INCOMPLETE');
  }
  const sharedRaw = await loadHospitalJob(jobId);
  if (sharedRaw) {
    const files = await WorkerDocument.find({ workerId }).sort({ createdAt: -1 }).limit(8);
    const application = await createHospitalApplication(workerId, user, profile, sharedRaw, {
      note: note?.trim(),
      documents: files.map((file) => ({
        id: file.id,
        type: file.type,
        fileName: file.fileName,
        mimeType: file.mimeType,
        status: file.status,
        storageKey: file.storageKey,
      })),
    });
    const view = presentApplication(application, sharedRaw);
    await notify({
      userId: workerId,
      type: 'APPLICATION_SUBMITTED',
      title: 'Application submitted',
      body: `${sharedRaw.title} at ${sharedRaw.hospitalName} is with the hospital.`,
      data: { screen: 'application', entityId: application.id },
    });
    return view;
  }
  const job = await Job.findById(jobId);
  if (!job || job.status !== 'OPEN') throw new ApiError(404, 'This job is not open', 'JOB_CLOSED');
  if (job.deadline && job.deadline.getTime() < Date.now()) {
    throw new ApiError(400, 'The application deadline has passed', 'DEADLINE_PASSED');
  }
  if (job.filledCount >= job.vacancies) {
    throw new ApiError(409, 'This job has no remaining vacancies', 'VACANCY_FILLED');
  }
  try {
    const application = await Application.create({
      jobId: job._id,
      workerId,
      hospitalId: job.hospitalId,
      status: 'SUBMITTED',
      reference: `HS${Date.now().toString(36).toUpperCase()}`,
      history: [{ status: 'SUBMITTED', at: new Date(), actorId: workerId, note: 'Application submitted' }],
    });
    await notify({
      userId: workerId,
      type: 'APPLICATION_SUBMITTED',
      title: 'Application submitted',
      body: `${job.title} at ${job.hospitalName} is with the hospital.`,
      data: { screen: 'application', entityId: application.id },
    });
    return applicationView(application);
  } catch (error) {
    if (isDuplicateKey(error)) {
      throw new ApiError(409, 'You have already applied for this job', 'DUPLICATE_APPLICATION');
    }
    throw error;
  }
}

export async function listMyApplications(workerId: string, status?: string) {
  const shared = await hospitalApplicationsFor(workerId);
  const sharedViews = [];
  for (const application of shared) {
    const job = await loadHospitalJob(String(application.jobId));
    const view = presentApplication(application, job);
    if (!status || status === 'ALL' || view.status === status) sharedViews.push(view);
  }
  const filter: Record<string, unknown> = { workerId };
  if (status && status !== 'ALL') filter.status = status;
  const items = await Application.find(filter).sort({ updatedAt: -1 });
  const own = await Promise.all(items.map(applicationView));
  return [...sharedViews, ...own];
}

export async function getApplication(applicationId: string, workerId: string) {
  const shared = (await hospitalApplicationsFor(workerId)).find((item) => item.id === applicationId);
  if (shared) {
    const job = await loadHospitalJob(String(shared.jobId));
    return presentApplication(shared, job);
  }
  const application = await Application.findById(applicationId);
  if (!application || String(application.workerId) !== workerId) {
    throw new ApiError(404, 'Application not found', 'NOT_FOUND');
  }
  return applicationView(application);
}

export async function withdrawApplication(applicationId: string, workerId: string) {
  const application = await Application.findById(applicationId);
  if (!application || String(application.workerId) !== workerId) {
    throw new ApiError(404, 'Application not found', 'NOT_FOUND');
  }
  if (!['SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED'].includes(application.status)) {
    throw new ApiError(409, 'This application can no longer be withdrawn', 'APPLICATION_LOCKED');
  }
  application.status = 'WITHDRAWN';
  application.history.push({ status: 'WITHDRAWN', at: new Date(), actorId: workerId as never, note: 'Withdrawn by worker' });
  await application.save();
  return applicationView(application);
}

async function hospitalApplication(applicationId: string, auth: { role: string; hospitalId?: string }) {
  const application = await Application.findById(applicationId);
  if (!application) throw new ApiError(404, 'Application not found', 'NOT_FOUND');
  if (auth.role === 'HOSPITAL' && String(application.hospitalId) !== auth.hospitalId) {
    throw new ApiError(403, 'This application belongs to another hospital', 'FORBIDDEN');
  }
  return application;
}

function pushStatus(application: any, status: string, actorId: string, note: string) {
  application.status = status;
  application.history.push({ status, at: new Date(), actorId, note });
}

export async function reviewApplication(applicationId: string, auth: { id: string; role: string; hospitalId?: string }) {
  const application = await hospitalApplication(applicationId, auth);
  if (application.status !== 'SUBMITTED') {
    throw new ApiError(409, 'Only a new application can move to review', 'INVALID_STATE');
  }
  pushStatus(application, 'UNDER_REVIEW', auth.id, 'Hospital started review');
  await application.save();
  await AuditLog.create({ actorId: auth.id, action: 'APPLICATION_REVIEW', entityType: 'Application', entityId: application.id });
  return applicationView(application);
}

export async function shortlistApplication(applicationId: string, auth: { id: string; role: string; hospitalId?: string }) {
  const application = await hospitalApplication(applicationId, auth);
  if (!['SUBMITTED', 'UNDER_REVIEW'].includes(application.status)) {
    throw new ApiError(409, 'This application cannot be shortlisted', 'INVALID_STATE');
  }
  pushStatus(application, 'SHORTLISTED', auth.id, 'Shortlisted');
  await application.save();
  const job = await Job.findById(application.jobId);
  await notify({
    userId: String(application.workerId),
    type: 'APPLICATION_SHORTLISTED',
    title: 'You were shortlisted',
    body: `${job?.title || 'A job'} shortlisted your profile. This is not a confirmed shift yet.`,
    data: { screen: 'application', entityId: application.id },
  });
  await AuditLog.create({ actorId: auth.id, action: 'APPLICATION_SHORTLIST', entityType: 'Application', entityId: application.id });
  return applicationView(application);
}

export async function rejectApplication(applicationId: string, auth: { id: string; role: string; hospitalId?: string }, note: string) {
  const application = await hospitalApplication(applicationId, auth);
  if (['ACCEPTED', 'WITHDRAWN', 'REJECTED'].includes(application.status)) {
    throw new ApiError(409, 'This application cannot be rejected', 'INVALID_STATE');
  }
  pushStatus(application, 'REJECTED', auth.id, note || 'Not selected');
  await application.save();
  const job = await Job.findById(application.jobId);
  await notify({
    userId: String(application.workerId),
    type: 'APPLICATION_REJECTED',
    title: 'Application update',
    body: `${job?.hospitalName || 'The hospital'} did not select this application.`,
    data: { screen: 'application', entityId: application.id },
  });
  await AuditLog.create({ actorId: auth.id, action: 'APPLICATION_REJECT', entityType: 'Application', entityId: application.id });
  return applicationView(application);
}

export async function offerApplication(applicationId: string, auth: { id: string; role: string; hospitalId?: string }) {
  const application = await hospitalApplication(applicationId, auth);
  if (application.status !== 'SHORTLISTED') {
    throw new ApiError(409, 'Shortlist the worker before sending an offer', 'INVALID_STATE');
  }
  const job = await Job.findById(application.jobId);
  if (!job) throw new ApiError(404, 'Job not found', 'NOT_FOUND');
  if (job.filledCount >= job.vacancies) {
    throw new ApiError(409, 'This job has no remaining vacancies', 'VACANCY_FILLED');
  }
  const placement = await Placement.create({
    applicationId: application._id,
    jobId: job._id,
    workerId: application.workerId,
    hospitalId: job.hospitalId,
    status: 'OFFERED',
    expiresAt: new Date(Date.now() + 12 * 60 * 60 * 1000),
    compensationMinor: job.compensationMinor,
    currency: job.currency,
    instructions: job.reportingInstructions,
  });
  pushStatus(application, 'OFFERED', auth.id, 'Placement offer sent');
  await application.save();
  await notify({
    userId: String(application.workerId),
    type: 'PLACEMENT_OFFERED',
    title: 'Placement offer',
    body: `${job.hospitalName} offered ${job.title}. Review it before it expires.`,
    data: { screen: 'placement', entityId: placement.id },
  });
  const worker = await User.findById(application.workerId);
  await queueWhatsApp(worker?.mobile || '', 'PLACEMENT_OFFER', `${job.hospitalName} offered you ${job.title} on ${job.shiftDate}. Open Hospital SOS to accept or decline.`);
  await AuditLog.create({ actorId: auth.id, action: 'PLACEMENT_OFFER', entityType: 'Placement', entityId: placement.id });
  return placementView(placement);
}

function placementView(placement: any, job?: any) {
  return {
    id: placement.id,
    applicationId: String(placement.applicationId),
    status: placement.status,
    expiresAt: placement.expiresAt,
    compensationMinor: placement.compensationMinor,
    currency: placement.currency,
    instructions: placement.instructions,
    acceptedAt: placement.acceptedAt,
    declinedAt: placement.declinedAt,
    job: job ? jobCard(job) : undefined,
  };
}

export async function listMyPlacements(workerId: string) {
  const items = await Placement.find({ workerId }).sort({ createdAt: -1 });
  const jobs = await Job.find({ _id: { $in: items.map((item) => item.jobId) } });
  const byId = new Map(jobs.map((job) => [job.id, job]));
  return items.map((item) => placementView(item, byId.get(String(item.jobId))));
}

export async function getPlacement(placementId: string, workerId: string) {
  const placement = await Placement.findById(placementId);
  if (!placement || String(placement.workerId) !== workerId) {
    throw new ApiError(404, 'Offer not found', 'NOT_FOUND');
  }
  const job = await Job.findById(placement.jobId);
  return placementView(placement, job);
}

export async function acceptPlacement(placementId: string, workerId: string) {
  const session = await mongoose.startSession();
  let result: any;
  let acceptedNow = false;
  try {
    await session.withTransaction(async () => {
      const placement = await Placement.findById(placementId).session(session);
      if (!placement || String(placement.workerId) !== workerId) {
        throw new ApiError(404, 'Offer not found', 'NOT_FOUND');
      }
      if (placement.status === 'ACCEPTED') {
        result = placement;
        return;
      }
      if (placement.status !== 'OFFERED') {
        throw new ApiError(409, 'This offer is no longer available', 'INVALID_STATE');
      }
      if (placement.expiresAt.getTime() < Date.now()) {
        placement.status = 'EXPIRED';
        await placement.save({ session });
        throw new ApiError(409, 'This offer has expired', 'OFFER_EXPIRED');
      }
      const job = await Job.findById(placement.jobId).session(session);
      if (!job || job.filledCount >= job.vacancies) {
        throw new ApiError(409, 'This job has no remaining vacancies', 'VACANCY_FILLED');
      }
      job.filledCount += 1;
      if (job.filledCount >= job.vacancies) job.status = 'CLOSED';
      await job.save({ session });
      placement.status = 'ACCEPTED';
      placement.acceptedAt = new Date();
      await placement.save({ session });
      const application = await Application.findById(placement.applicationId).session(session);
      if (application) {
        pushStatus(application, 'ACCEPTED', workerId, 'Worker accepted the offer');
        await application.save({ session });
      }
      const existingShift = await Shift.findOne({ placementId: placement._id }).session(session);
      if (!existingShift) {
        await Shift.create([{
          placementId: placement._id,
          jobId: job._id,
          workerId,
          hospitalId: job.hospitalId,
          status: 'UPCOMING',
          startAt: job.startAt,
          endAt: job.endAt,
        }], { session });
      }
      if (job.status === 'CLOSED') {
        const others = await Placement.find({ jobId: job._id, status: 'OFFERED', _id: { $ne: placement._id } }).session(session);
        for (const other of others) {
          other.status = 'EXPIRED';
          await other.save({ session });
          const otherApp = await Application.findById(other.applicationId).session(session);
          if (otherApp && otherApp.status === 'OFFERED') {
            pushStatus(otherApp, 'OFFER_EXPIRED', workerId, 'Vacancy filled');
            await otherApp.save({ session });
          }
        }
      }
      result = placement;
      acceptedNow = true;
    });
  } finally {
    await session.endSession();
  }
  const placement = await Placement.findById(placementId);
  const job = placement ? await Job.findById(placement.jobId) : null;
  if (acceptedNow && placement && job) {
    const shift = await Shift.findOne({ placementId: placement._id });
    await notify({
      userId: workerId,
      type: 'PLACEMENT_ACCEPTED',
      title: 'Shift confirmed',
      body: `${job.title} at ${job.hospitalName} is confirmed.`,
      data: { screen: 'shift', entityId: shift ? shift.id : placement.id },
    });
  }
  return placementView(placement, job);
}

export async function declinePlacement(placementId: string, workerId: string) {
  const placement = await Placement.findById(placementId);
  if (!placement || String(placement.workerId) !== workerId) {
    throw new ApiError(404, 'Offer not found', 'NOT_FOUND');
  }
  if (placement.status === 'DECLINED') return placementView(placement, await Job.findById(placement.jobId));
  if (placement.status !== 'OFFERED') {
    throw new ApiError(409, 'This offer is no longer available', 'INVALID_STATE');
  }
  placement.status = 'DECLINED';
  placement.declinedAt = new Date();
  await placement.save();
  const application = await Application.findById(placement.applicationId);
  if (application) {
    pushStatus(application, 'DECLINED', workerId, 'Worker declined the offer');
    await application.save();
  }
  return placementView(placement, await Job.findById(placement.jobId));
}

async function shiftView(shift: any) {
  const job = await Job.findById(shift.jobId);
  const payment = await Payment.findOne({ shiftId: shift._id });
  const attendanceMark = shift.status === 'CANCELLED'
    ? 'ABSENT'
    : shift.checkIn?.at
      ? (shift.startAt && new Date(shift.checkIn.at).getTime() > new Date(shift.startAt).getTime() + 15 * 60 * 1000 ? 'LATE' : 'PRESENT')
      : 'UNMARKED';
  const pay = paymentFor(job?.compensationMinor || 0, attendanceMark, shift.status, payment ? payment.amountMinor : null);
  return {
    id: shift.id,
    status: shift.status,
    startAt: shift.startAt,
    endAt: shift.endAt,
    checkIn: shift.checkIn?.at ? shift.checkIn : null,
    checkOut: shift.checkOut?.at ? shift.checkOut : null,
    feedbackRating: shift.feedbackRating || null,
    feedbackComment: shift.feedbackComment || '',
    incidents: shift.incidents || [],
    attendanceMark,
    payment: {
      ...pay,
      id: payment?.id,
      paidAt: payment?.paidAt,
      reference: payment?.reference,
    },
    job: job ? {
      ...jobDetail(job),
      supervisorName: job.supervisorName,
      supervisorPhone: job.supervisorPhone,
    } : null,
  };
}

export async function listMyShifts(workerId: string, status?: string) {
  const shared = await hospitalShiftsFor(workerId);
  const sharedViews = [];
  for (const shift of shared) {
    const place = shift.hospitalId ? await loadHospitalJob(String(shift.jobId || '')) : null;
    const view = presentShift(shift, place ? { name: place.hospitalName, city: place.city } : undefined, place);
    if (!place && shift.hospitalId) {
      const hospital = await mongoose.connection.db?.collection('hospitals').findOne({ id: shift.hospitalId });
      if (hospital) {
        view.hospitalName = hospital.name;
        view.city = hospital.city || view.city;
      }
    }
    if (!status || status === 'ALL' || view.status === status) sharedViews.push(view);
  }
  const filter: Record<string, unknown> = { workerId };
  if (status && status !== 'ALL') filter.status = status;
  const items = await Shift.find(filter).sort({ startAt: 1 });
  const own = await Promise.all(items.map(shiftView));
  return [...sharedViews, ...own];
}

export async function getShift(shiftId: string, workerId: string) {
  if (!mongoose.isValidObjectId(shiftId)) {
    const shared = await presentedHospitalShift(workerId, shiftId);
    if (!shared) throw new ApiError(404, 'Shift not found', 'NOT_FOUND');
    return shared;
  }
  const shift = await Shift.findById(shiftId);
  if (!shift || String(shift.workerId) !== workerId) throw new ApiError(404, 'Shift not found', 'NOT_FOUND');
  return shiftView(shift);
}

export async function checkIn(shiftId: string, workerId: string, lat?: number, lng?: number) {
  const shift = await Shift.findById(shiftId);
  if (!shift || String(shift.workerId) !== workerId) throw new ApiError(404, 'Shift not found', 'NOT_FOUND');
  if (shift.checkIn?.at) return shiftView(shift);
  if (shift.status !== 'UPCOMING' && shift.status !== 'ACTIVE') {
    throw new ApiError(409, 'This shift cannot be checked in', 'INVALID_STATE');
  }
  const now = Date.now();
  if (now < shift.startAt.getTime() - 90 * 60 * 1000 || now > shift.endAt.getTime()) {
    throw new ApiError(409, 'Check-in is only open near the shift time', 'CHECKIN_WINDOW');
  }
  const job = await Job.findById(shift.jobId);
  let inside = true;
  if (job?.geofenceMeters && job.location?.coordinates?.length === 2) {
    if (lat === undefined || lng === undefined) {
      throw new ApiError(400, 'Location is required to check in for this shift', 'LOCATION_REQUIRED');
    }
    const meters = distanceMeters(lat, lng, job.location.coordinates[1], job.location.coordinates[0]);
    inside = meters <= job.geofenceMeters;
    if (!inside) {
      throw new ApiError(403, 'You are outside the hospital check-in area', 'OUTSIDE_GEOFENCE');
    }
  }
  shift.checkIn = { at: new Date(), lat, lng, insideGeofence: inside };
  shift.status = 'ACTIVE';
  await shift.save();
  await notify({
    userId: workerId,
    type: 'CHECK_IN',
    title: 'Checked in',
    body: `Check-in recorded for ${job?.hospitalName || 'your shift'}.`,
    data: { screen: 'shift', entityId: shift.id },
  });
  return shiftView(shift);
}

export async function checkOut(shiftId: string, workerId: string, lat?: number, lng?: number) {
  const shift = await Shift.findById(shiftId);
  if (!shift || String(shift.workerId) !== workerId) throw new ApiError(404, 'Shift not found', 'NOT_FOUND');
  if (shift.status === 'COMPLETED' && shift.checkOut?.at) return shiftView(shift);
  if (!shift.checkIn?.at || shift.status !== 'ACTIVE') {
    throw new ApiError(409, 'Check in before checking out', 'INVALID_STATE');
  }
  const job = await Job.findById(shift.jobId);
  shift.checkOut = { at: new Date(), lat, lng };
  shift.status = 'COMPLETED';
  await shift.save();
  const existing = await Payment.findOne({ shiftId: shift._id });
  if (!existing && job) {
    await Payment.create({
      workerId,
      hospitalId: job.hospitalId,
      shiftId: shift._id,
      placementId: shift.placementId,
      amountMinor: job.compensationMinor,
      currency: job.currency,
      status: 'PENDING',
      kind: 'WORKER_EARNING',
    });
  }
  await notify({
    userId: workerId,
    type: 'SHIFT_COMPLETED',
    title: 'Shift completed',
    body: 'Attendance is complete. Payment stays pending until the hospital confirms it.',
    data: { screen: 'shift', entityId: shift.id },
  });
  return shiftView(shift);
}

export async function reportIncident(shiftId: string, workerId: string, category: string, description: string) {
  const shift = await Shift.findById(shiftId);
  if (!shift || String(shift.workerId) !== workerId) throw new ApiError(404, 'Shift not found', 'NOT_FOUND');
  shift.incidents.push({ category, description, at: new Date() });
  await shift.save();
  return shiftView(shift);
}

export async function addFeedback(shiftId: string, workerId: string, rating: number, comment: string) {
  const shift = await Shift.findById(shiftId);
  if (!shift || String(shift.workerId) !== workerId) throw new ApiError(404, 'Shift not found', 'NOT_FOUND');
  if (shift.status !== 'COMPLETED') throw new ApiError(409, 'Feedback is available after the shift', 'INVALID_STATE');
  shift.feedbackRating = rating;
  shift.feedbackComment = comment;
  await shift.save();
  return shiftView(shift);
}

export async function dashboard(userId: string) {
  const { user, profile } = await loadWorker(userId);
  const applications = await Application.find({ workerId: userId });
  const sharedStatuses = (await hospitalApplicationsFor(userId)).map((item) => toAppStatus(String(item.status || '')));
  const statuses = [...applications.map((item) => item.status), ...sharedStatuses];
  const counts = {
    total: statuses.length,
    underReview: statuses.filter((item) => item === 'UNDER_REVIEW' || item === 'SUBMITTED').length,
    shortlisted: statuses.filter((item) => item === 'SHORTLISTED').length,
    offered: statuses.filter((item) => item === 'OFFERED').length,
    rejected: statuses.filter((item) => item === 'REJECTED' || item === 'DECLINED').length,
    confirmed: statuses.filter((item) => item === 'ACCEPTED').length,
  };
  const now = new Date();
  const upcoming = await Shift.findOne({
    workerId: userId,
    status: { $in: ['UPCOMING', 'ACTIVE'] },
    endAt: { $gte: now },
  }).sort({ startAt: 1 });
  const docs = await WorkerDocument.find({
    workerId: userId,
    expiresAt: { $ne: null, $lte: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000) },
    status: { $ne: 'REJECTED' },
  });
  for (const doc of docs) {
    const recent = await Notification.findOne({
      userId,
      type: 'CERTIFICATION_EXPIRY',
      'data.entityId': doc.id,
      createdAt: { $gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000) },
    });
    if (!recent) {
      await notify({
        userId,
        type: 'CERTIFICATION_EXPIRY',
        title: 'Document expiring',
        body: `${doc.type} needs renewal.`,
        data: { screen: 'documents', entityId: doc.id },
      });
    }
  }
  const activity = await Notification.find({ userId }).sort({ createdAt: -1 }).limit(8);
  return {
    greetingName: user.fullName,
    verificationStatus: profile.verificationStatus,
    completion: profileCompletion(user, profile),
    healthcareRole: profile.healthcareRole,
    city: profile.city,
    canApply: canApply(user, profile),
    missing: missingProfileFields(user, profile),
    expiringDocuments: docs.map((doc) => ({ id: doc.id, type: doc.type, expiresAt: doc.expiresAt, status: doc.status })),
    counts,
    upcomingShift: upcoming ? await shiftView(upcoming) : null,
    recommendedJobs: await recommendedJobs(userId),
    recentActivity: activity.map((item) => ({
      id: item.id,
      type: item.type,
      title: item.title,
      body: item.body,
      read: item.read,
      createdAt: item.createdAt,
      data: item.data,
    })),
  };
}

export async function listNotifications(userId: string) {
  const items = await Notification.find({ userId }).sort({ createdAt: -1 }).limit(100);
  const unread = items.filter((item) => !item.read).length;
  return {
    unread,
    items: items.map((item) => ({
      id: item.id,
      type: item.type,
      title: item.title,
      body: item.body,
      read: item.read,
      createdAt: item.createdAt,
      data: item.data,
    })),
  };
}

export async function markNotification(userId: string, notificationId: string) {
  const item = await Notification.findOne({ _id: notificationId, userId });
  if (!item) throw new ApiError(404, 'Notification not found', 'NOT_FOUND');
  item.read = true;
  await item.save();
  return { id: item.id, read: true };
}

export async function markAllNotifications(userId: string) {
  await Notification.updateMany({ userId, read: false }, { read: true });
  return { read: true };
}

export async function registerDevice(userId: string, token: string, platform: string) {
  const user = await User.findById(userId);
  if (!user) throw new ApiError(404, 'Account not found', 'NOT_FOUND');
  await User.updateOne({ _id: userId }, { $pull: { devices: { token } } });
  await User.updateOne({ _id: userId }, { $push: { devices: { token, platform, updatedAt: new Date() } } });
  return { registered: true };
}

export async function earnings(userId: string) {
  const payments = await Payment.find({ workerId: userId, kind: 'WORKER_EARNING' }).sort({ createdAt: -1 });
  const totalPaidMinor = payments.filter((item) => item.status === 'PAID').reduce((sum, item) => sum + item.amountMinor, 0);
  const totalPendingMinor = payments.filter((item) => item.status === 'PENDING').reduce((sum, item) => sum + item.amountMinor, 0);
  const shifts = await Shift.find({ _id: { $in: payments.map((item) => item.shiftId) } });
  const jobs = await Job.find({ _id: { $in: shifts.map((item) => item.jobId) } });
  const shiftJob = new Map(shifts.map((shift) => [String(shift._id), jobs.find((job) => job.id === String(shift.jobId))]));
  return {
    currency: 'INR',
    totalPaidMinor,
    totalPendingMinor,
    items: payments.map((payment) => {
      const job = shiftJob.get(String(payment.shiftId));
      return {
        id: payment.id,
        status: payment.status,
        amountMinor: payment.amountMinor,
        currency: payment.currency,
        paidAt: payment.paidAt,
        reference: payment.reference,
        title: job?.title || 'Assignment',
        hospitalName: job?.hospitalName || '',
        shiftDate: job?.shiftDate || '',
      };
    }),
  };
}

export async function addDocument(workerId: string, file: { originalname: string; mimetype: string; size: number; buffer: Buffer }, type: string, expiresAt?: string) {
  const allowed = ['application/pdf', 'image/jpeg', 'image/png'];
  if (!allowed.includes(file.mimetype)) {
    throw new ApiError(400, 'Upload a PDF, JPG, or PNG', 'INVALID_FILE_TYPE');
  }
  if (file.size > 5 * 1024 * 1024) {
    throw new ApiError(400, 'Files must be 5 MB or smaller', 'FILE_TOO_LARGE');
  }
  const ext = path.extname(file.originalname || '').toLowerCase() || (file.mimetype === 'application/pdf' ? '.pdf' : '.jpg');
  const key = `${workerId}/${Date.now()}-${Math.random().toString(16).slice(2)}${ext}`;
  await savePrivateObject(key, file.buffer, file.mimetype);
  const doc = await WorkerDocument.create({
    workerId,
    type,
    fileName: path.basename(file.originalname || 'document'),
    mimeType: file.mimetype,
    size: file.size,
    storageKey: key,
    status: 'PENDING',
    expiresAt: expiresAt ? new Date(expiresAt) : undefined,
  });
  return {
    id: doc.id,
    type: doc.type,
    fileName: doc.fileName,
    status: doc.status,
    expiresAt: doc.expiresAt,
    createdAt: doc.createdAt,
  };
}

export async function listDocuments(workerId: string) {
  const docs = await WorkerDocument.find({ workerId }).sort({ createdAt: -1 });
  return docs.map((doc) => ({
    id: doc.id,
    type: doc.type,
    fileName: doc.fileName,
    status: doc.status,
    expiresAt: doc.expiresAt,
    rejectionReason: doc.rejectionReason,
    createdAt: doc.createdAt,
  }));
}

export async function documentAccess(documentId: string, auth: { id: string; role: string; hospitalId?: string }) {
  const doc = await WorkerDocument.findById(documentId);
  if (!doc) throw new ApiError(404, 'Document not found', 'NOT_FOUND');
  if (auth.role === 'WORKER' && String(doc.workerId) !== auth.id) {
    throw new ApiError(403, 'You cannot open this document', 'FORBIDDEN');
  }
  if (auth.role === 'HOSPITAL') {
    const related = await Application.findOne({ workerId: doc.workerId, hospitalId: auth.hospitalId });
    if (!related) throw new ApiError(403, 'You cannot open this document', 'FORBIDDEN');
  }
  const link = await accessUrl(doc.id, doc.storageKey);
  return link;
}

export function readSignedDocument(documentId: string, exp: string, sig: string) {
  const expected = signValue(`${documentId}.${exp}`, env.jwtAccessSecret);
  if (!exp || !sig || Number(exp) < Date.now() || expected !== sig) {
    throw new ApiError(403, 'This document link has expired', 'LINK_EXPIRED');
  }
  return WorkerDocument.findById(documentId).then((doc) => {
    if (!doc) throw new ApiError(404, 'Document not found', 'NOT_FOUND');
    return { doc, bytes: readLocalObject(doc.storageKey) };
  });
}

export async function createHospital(input: {
  organizationName: string;
  fullName: string;
  mobile: string;
  email: string;
  password: string;
  city: string;
  address: string;
}) {
  const { normalizeMobile } = await import('../utils/helpers');
  const bcrypt = await import('bcryptjs');
  const mobile = normalizeMobile(input.mobile);
  const email = input.email.toLowerCase();
  const existing = await User.findOne({ $or: [{ mobile }, { email }] });
  if (existing) throw new ApiError(409, 'An account with this mobile or email already exists', 'ACCOUNT_EXISTS');
  const hospital = await Hospital.create({
    name: input.organizationName.trim(),
    city: input.city.trim(),
    address: input.address.trim(),
    verified: false,
  });
  const user = await User.create({
    role: 'HOSPITAL',
    fullName: input.fullName.trim(),
    mobile,
    email,
    passwordHash: await bcrypt.hash(input.password, 10),
    status: 'ACTIVE',
    mobileVerified: true,
    hospitalId: hospital._id,
  });
  return { hospitalId: hospital.id, userId: user.id };
}

export async function hospitalProfile(hospitalId: string) {
  const hospital = await Hospital.findById(hospitalId);
  if (!hospital) throw new ApiError(404, 'Hospital not found', 'NOT_FOUND');
  const subscription = await Subscription.findOne({ hospitalId, status: 'ACTIVE' }).sort({ endsAt: -1 });
  return { hospital, subscription };
}

export async function createJob(hospitalId: string, actorId: string, input: any) {
  const hospital = await Hospital.findById(hospitalId);
  if (!hospital) throw new ApiError(404, 'Hospital not found', 'NOT_FOUND');
  if (!hospital.verified) throw new ApiError(403, 'The hospital must be verified before publishing jobs', 'HOSPITAL_UNVERIFIED');
  const startAt = zonedDateTime(input.shiftDate, input.startTime);
  let endAt = zonedDateTime(input.shiftDate, input.endTime);
  if (endAt.getTime() <= startAt.getTime()) {
    endAt = new Date(endAt.getTime() + 24 * 60 * 60 * 1000);
  }
  const job = await Job.create({
    hospitalId: hospital._id,
    hospitalName: hospital.name,
    hospitalVerified: hospital.verified,
    title: input.title,
    healthcareRole: input.healthcareRole,
    department: input.department,
    description: input.description || '',
    qualifications: input.qualifications || [],
    experienceYears: input.experienceYears || 0,
    skills: input.skills || [],
    certifications: input.certifications || [],
    shiftDate: input.shiftDate,
    startTime: input.startTime,
    endTime: input.endTime,
    startAt,
    endAt,
    shiftType: input.shiftType || 'CUSTOM',
    address: input.address || hospital.address,
    city: input.city || hospital.city,
    reportingInstructions: input.reportingInstructions || '',
    location: input.lng !== undefined && input.lat !== undefined ? { type: 'Point', coordinates: [input.lng, input.lat] } : hospital.location,
    compensationMinor: input.compensationMinor,
    currency: input.currency || 'INR',
    paymentUnit: input.paymentUnit || 'PER_SHIFT',
    vacancies: input.vacancies,
    urgent: Boolean(input.urgent),
    deadline: input.deadline ? new Date(input.deadline) : startAt,
    status: 'OPEN',
    geofenceMeters: input.geofenceMeters ?? hospital.geofenceMeters ?? 300,
    supervisorName: input.supervisorName || '',
    supervisorPhone: input.supervisorPhone || '',
  });
  await AuditLog.create({ actorId, action: 'JOB_CREATE', entityType: 'Job', entityId: job.id });
  return jobDetail(job);
}

export async function hospitalJobs(hospitalId: string) {
  const jobs = await Job.find({ hospitalId }).sort({ createdAt: -1 });
  return jobs.map((job) => jobCard(job));
}

export async function hospitalApplications(hospitalId: string) {
  const items = await Application.find({ hospitalId }).sort({ updatedAt: -1 });
  return Promise.all(items.map(applicationView));
}

export async function verifyHospital(hospitalId: string, actorId: string) {
  const hospital = await Hospital.findById(hospitalId);
  if (!hospital) throw new ApiError(404, 'Hospital not found', 'NOT_FOUND');
  hospital.verified = true;
  await hospital.save();
  await Job.updateMany({ hospitalId }, { hospitalVerified: true });
  await AuditLog.create({ actorId, action: 'HOSPITAL_VERIFY', entityType: 'Hospital', entityId: hospital.id });
  return hospital;
}

export async function reviewDocument(documentId: string, actorId: string, status: string, reason: string) {
  const doc = await WorkerDocument.findById(documentId);
  if (!doc) throw new ApiError(404, 'Document not found', 'NOT_FOUND');
  doc.status = status as never;
  doc.rejectionReason = status === 'REJECTED' ? reason : '';
  await doc.save();
  await notify({
    userId: String(doc.workerId),
    type: 'CREDENTIAL_VERIFICATION',
    title: 'Document update',
    body: status === 'VERIFIED' ? `${doc.type} was verified.` : `${doc.type} needs attention.`,
    data: { screen: 'documents', entityId: doc.id },
  });
  await AuditLog.create({ actorId, action: 'DOCUMENT_REVIEW', entityType: 'WorkerDocument', entityId: doc.id, meta: { status } });
  return { id: doc.id, status: doc.status };
}

export async function markPayment(paymentId: string, actorId: string, status: 'PAID' | 'FAILED', reference: string) {
  const payment = await Payment.findById(paymentId);
  if (!payment) throw new ApiError(404, 'Payment not found', 'NOT_FOUND');
  payment.status = status;
  payment.reference = reference;
  payment.paidAt = status === 'PAID' ? new Date() : undefined;
  await payment.save();
  if (payment.workerId) {
    await notify({
      userId: String(payment.workerId),
      type: 'PAYMENT_STATUS',
      title: status === 'PAID' ? 'Payment confirmed' : 'Payment update',
      body: status === 'PAID' ? 'A completed assignment was marked paid.' : 'A payment could not be confirmed.',
      data: { screen: 'earnings', entityId: payment.id },
    });
  }
  await AuditLog.create({ actorId, action: 'PAYMENT_STATUS', entityType: 'Payment', entityId: payment.id, meta: { status } });
  return payment;
}

export async function adminStats() {
  const [workers, hospitals, openJobs, applications, placements, shifts] = await Promise.all([
    User.countDocuments({ role: 'WORKER' }),
    Hospital.countDocuments(),
    Job.countDocuments({ status: 'OPEN' }),
    Application.countDocuments(),
    Placement.countDocuments({ status: 'ACCEPTED' }),
    Shift.countDocuments({ status: { $in: ['UPCOMING', 'ACTIVE'] } }),
  ]);
  return { workers, hospitals, openJobs, applications, acceptedPlacements: placements, activeOrUpcomingShifts: shifts };
}

export async function auditLogs() {
  return AuditLog.find().sort({ createdAt: -1 }).limit(100);
}

export async function expireStaleOffers() {
  const stale = await Placement.find({ status: 'OFFERED', expiresAt: { $lt: new Date() } });
  for (const placement of stale) {
    placement.status = 'EXPIRED';
    await placement.save();
    const application = await Application.findById(placement.applicationId);
    if (application && application.status === 'OFFERED') {
      pushStatus(application, 'OFFER_EXPIRED', String(placement.workerId), 'Offer expired');
      await application.save();
      await notify({
        userId: String(placement.workerId),
        type: 'OFFER_EXPIRED',
        title: 'Offer expired',
        body: 'A placement offer expired before it was accepted.',
        data: { screen: 'application', entityId: application.id },
      });
    }
  }
}

export async function remindUpcomingShifts() {
  const soon = new Date(Date.now() + 2 * 60 * 60 * 1000);
  const shifts = await Shift.find({ status: 'UPCOMING', reminderSent: false, startAt: { $lte: soon, $gte: new Date() } });
  for (const shift of shifts) {
    const job = await Job.findById(shift.jobId);
    await notify({
      userId: String(shift.workerId),
      type: 'SHIFT_REMINDER',
      title: 'Shift starting soon',
      body: `${job?.title || 'Your shift'} at ${job?.hospitalName || 'the hospital'} starts soon.`,
      data: { screen: 'shift', entityId: shift.id },
    });
    shift.reminderSent = true;
    await shift.save();
  }
}

export async function subscriptionFor(hospitalId: string) {
  const subscription = await Subscription.findOne({ hospitalId }).sort({ createdAt: -1 });
  const plan = subscription ? await SubscriptionPlan.findById(subscription.planId) : null;
  return { subscription, plan };
}
