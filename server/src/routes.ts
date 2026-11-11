import crypto from 'crypto';
import { Router } from 'express';
import multer from 'multer';
import { z } from 'zod';
import { env } from './config/env';
import { requireAuth, requireRole } from './middleware/auth';
import { ApiError } from './utils/errors';
import * as auth from './services/auth.service';
import * as staffing from './services/staffing.service';
import { Payment } from './models';

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 5 * 1024 * 1024 } });

function pid(value: string | string[]): string {
  return Array.isArray(value) ? value[0] : value;
}

function send(res: { status: (code: number) => { json: (body: unknown) => void } }, message: string, data: unknown, status = 200) {
  res.status(status).json({ success: true, message, data });
}

function parse<T>(schema: z.ZodType<T>, value: unknown): T {
  const result = schema.safeParse(value);
  if (!result.success) {
    throw new ApiError(400, result.error.issues[0]?.message || 'Check the form and try again', 'VALIDATION_ERROR');
  }
  return result.data;
}

const mobile = z.string().min(8).max(20);
const password = z.string().min(8, 'Use at least 8 characters');

export const api = Router();

api.get('/health', (_req, res) => {
  send(res, 'ok', { service: 'hospital-sos', time: new Date().toISOString() });
});

api.post('/auth/register', async (req, res, next) => {
  try {
    const body = parse(z.object({
      fullName: z.string().min(2, 'Enter your full name'),
      mobile: mobile.optional(),
      email: z.string().email('Enter a valid email address'),
      password,
      healthcareRole: z.string().min(2, 'Enter your preferred healthcare role'),
      consent: z.boolean().refine((value) => value, 'Accept the terms and privacy policy'),
    }), req.body);
    const created = await auth.register(body);
    send(res, created.accessToken ? 'Account created' : 'OTP sent', created, 201);
  } catch (error) { next(error); }
});

api.post('/auth/send-otp', async (req, res, next) => {
  try {
    const body = parse(z.object({ mobile, purpose: z.enum(['REGISTER', 'LOGIN']) }), req.body);
    send(res, 'OTP sent', await auth.sendOtp(body.mobile, body.purpose));
  } catch (error) { next(error); }
});

api.post('/auth/verify-otp', async (req, res, next) => {
  try {
    const body = parse(z.object({ mobile, code: z.string().length(6), purpose: z.enum(['REGISTER', 'LOGIN']) }), req.body);
    send(res, 'Signed in', await auth.verifyOtp(body.mobile, body.code, body.purpose));
  } catch (error) { next(error); }
});

api.post('/auth/login', async (req, res, next) => {
  try {
    const body = parse(z.object({
      email: z.string().email().optional(),
      mobile: mobile.optional(),
      password: z.string().min(1),
    }).refine((value) => Boolean(value.email || value.mobile), 'Enter your email and password'), req.body);
    send(res, 'Signed in', await auth.login(body));
  } catch (error) { next(error); }
});

api.post('/auth/refresh', async (req, res, next) => {
  try {
    const body = parse(z.object({ refreshToken: z.string().min(10) }), req.body);
    send(res, 'Session refreshed', await auth.refresh(body.refreshToken));
  } catch (error) { next(error); }
});

api.post('/auth/logout', async (req, res, next) => {
  try {
    const body = parse(z.object({ refreshToken: z.string().optional() }), req.body);
    await auth.logout(body.refreshToken || '');
    send(res, 'Signed out', {});
  } catch (error) { next(error); }
});

api.get('/users/me', requireAuth, async (req, res, next) => {
  try { send(res, 'Account', await auth.me(req.auth!.id)); } catch (error) { next(error); }
});

api.patch('/users/me/preferences', requireAuth, async (req, res, next) => {
  try {
    const body = parse(z.object({
      jobs: z.boolean().optional(),
      offers: z.boolean().optional(),
      shifts: z.boolean().optional(),
      payments: z.boolean().optional(),
    }), req.body);
    send(res, 'Preferences saved', await auth.updatePreferences(req.auth!.id, body));
  } catch (error) { next(error); }
});

api.get('/workers/me/profile', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Profile', await staffing.getProfile(req.auth!.id)); } catch (error) { next(error); }
});

api.patch('/workers/me/profile', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Profile saved', await staffing.updateProfile(req.auth!.id, req.body || {})); } catch (error) { next(error); }
});

api.get('/workers/me/availability', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try {
    const profile = await staffing.getProfile(req.auth!.id);
    send(res, 'Availability', {
      weeklyAvailability: profile.profile.weeklyAvailability,
      preferredShifts: profile.profile.preferredShifts,
      unavailableDates: profile.profile.unavailableDates,
      preferredLocations: profile.profile.preferredLocations,
    });
  } catch (error) { next(error); }
});

api.patch('/workers/me/availability', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Availability saved', await staffing.updateAvailability(req.auth!.id, req.body || {})); } catch (error) { next(error); }
});

api.get('/workers/me/documents', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Documents', await staffing.listDocuments(req.auth!.id)); } catch (error) { next(error); }
});

api.post('/workers/me/documents', requireAuth, requireRole('WORKER'), upload.single('file'), async (req, res, next) => {
  try {
    if (!req.file) throw new ApiError(400, 'Choose a file to upload', 'FILE_REQUIRED');
    const type = String(req.body.type || 'OTHER');
    send(res, 'Document uploaded', await staffing.addDocument(req.auth!.id, req.file, type, req.body.expiresAt), 201);
  } catch (error) { next(error); }
});

api.get('/workers/me/documents/:documentId/url', requireAuth, async (req, res, next) => {
  try { send(res, 'Document link', await staffing.documentAccess(pid(req.params.documentId), req.auth!)); } catch (error) { next(error); }
});

api.get('/documents/:documentId/file', async (req, res, next) => {
  try {
    const file = await staffing.readSignedDocument(pid(req.params.documentId), String(req.query.exp || ''), String(req.query.sig || ''));
    res.setHeader('Content-Type', file.doc.mimeType);
    res.setHeader('Content-Disposition', `inline; filename="${file.doc.fileName}"`);
    res.send(file.bytes);
  } catch (error) { next(error); }
});

api.get('/workers/me/dashboard', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Dashboard', await staffing.dashboard(req.auth!.id)); } catch (error) { next(error); }
});

api.get('/jobs', async (req, res, next) => {
  try { send(res, 'Jobs', await staffing.listJobs(req.query as Record<string, string>)); } catch (error) { next(error); }
});

api.get('/jobs/recommended', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Recommended jobs', await staffing.recommendedJobs(req.auth!.id)); } catch (error) { next(error); }
});

api.get('/jobs/urgent', async (_req, res, next) => {
  try { send(res, 'Urgent staffing', await staffing.urgentJobs()); } catch (error) { next(error); }
});

api.get('/jobs/:jobId', requireAuth, async (req, res, next) => {
  try { send(res, 'Job', await staffing.getJob(pid(req.params.jobId), req.auth!.role === 'WORKER' ? req.auth!.id : undefined)); } catch (error) { next(error); }
});

api.post('/jobs/:jobId/applications', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try {
    const note = typeof req.body?.note === 'string' ? req.body.note.slice(0, 280) : '';
    send(res, 'Application submitted successfully', await staffing.applyToJob(req.auth!.id, pid(req.params.jobId), note), 201);
  } catch (error) { next(error); }
});

api.get('/workers/me/applications', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Applications', await staffing.listMyApplications(req.auth!.id, String(req.query.status || ''))); } catch (error) { next(error); }
});

api.get('/applications/:applicationId', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Application', await staffing.getApplication(pid(req.params.applicationId), req.auth!.id)); } catch (error) { next(error); }
});

api.post('/applications/:applicationId/withdraw', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Application withdrawn', await staffing.withdrawApplication(pid(req.params.applicationId), req.auth!.id)); } catch (error) { next(error); }
});

api.post('/applications/:applicationId/review', requireAuth, requireRole('HOSPITAL', 'ADMIN', 'SUPER_ADMIN'), async (req, res, next) => {
  try { send(res, 'Application in review', await staffing.reviewApplication(pid(req.params.applicationId), req.auth!)); } catch (error) { next(error); }
});

api.post('/applications/:applicationId/shortlist', requireAuth, requireRole('HOSPITAL', 'ADMIN', 'SUPER_ADMIN'), async (req, res, next) => {
  try { send(res, 'Worker shortlisted', await staffing.shortlistApplication(pid(req.params.applicationId), req.auth!)); } catch (error) { next(error); }
});

api.post('/applications/:applicationId/reject', requireAuth, requireRole('HOSPITAL', 'ADMIN', 'SUPER_ADMIN'), async (req, res, next) => {
  try { send(res, 'Application rejected', await staffing.rejectApplication(pid(req.params.applicationId), req.auth!, String(req.body?.note || ''))); } catch (error) { next(error); }
});

api.post('/applications/:applicationId/offer', requireAuth, requireRole('HOSPITAL', 'ADMIN', 'SUPER_ADMIN'), async (req, res, next) => {
  try { send(res, 'Offer sent', await staffing.offerApplication(pid(req.params.applicationId), req.auth!), 201); } catch (error) { next(error); }
});

api.get('/workers/me/placements', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Offers', await staffing.listMyPlacements(req.auth!.id)); } catch (error) { next(error); }
});

api.get('/placements/:placementId', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Offer', await staffing.getPlacement(pid(req.params.placementId), req.auth!.id)); } catch (error) { next(error); }
});

api.post('/placements/:placementId/accept', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Offer accepted', await staffing.acceptPlacement(pid(req.params.placementId), req.auth!.id)); } catch (error) { next(error); }
});

api.post('/placements/:placementId/decline', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Offer declined', await staffing.declinePlacement(pid(req.params.placementId), req.auth!.id)); } catch (error) { next(error); }
});

api.get('/workers/me/shifts', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Shifts', await staffing.listMyShifts(req.auth!.id, String(req.query.status || ''))); } catch (error) { next(error); }
});

api.get('/shifts/:shiftId', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Shift', await staffing.getShift(pid(req.params.shiftId), req.auth!.id)); } catch (error) { next(error); }
});

api.post('/shifts/:shiftId/check-in', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try {
    const lat = req.body?.lat === undefined ? undefined : Number(req.body.lat);
    const lng = req.body?.lng === undefined ? undefined : Number(req.body.lng);
    send(res, 'Checked in', await staffing.checkIn(pid(req.params.shiftId), req.auth!.id, lat, lng));
  } catch (error) { next(error); }
});

api.post('/shifts/:shiftId/check-out', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try {
    const lat = req.body?.lat === undefined ? undefined : Number(req.body.lat);
    const lng = req.body?.lng === undefined ? undefined : Number(req.body.lng);
    send(res, 'Checked out', await staffing.checkOut(pid(req.params.shiftId), req.auth!.id, lat, lng));
  } catch (error) { next(error); }
});

api.post('/shifts/:shiftId/report-incident', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try {
    const body = parse(z.object({ category: z.string().min(2), description: z.string().min(4) }), req.body);
    send(res, 'Incident reported', await staffing.reportIncident(pid(req.params.shiftId), req.auth!.id, body.category, body.description));
  } catch (error) { next(error); }
});

api.post('/shifts/:shiftId/feedback', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try {
    const body = parse(z.object({ rating: z.number().min(1).max(5), comment: z.string().optional() }), req.body);
    send(res, 'Feedback saved', await staffing.addFeedback(pid(req.params.shiftId), req.auth!.id, body.rating, body.comment || ''));
  } catch (error) { next(error); }
});

api.get('/notifications', requireAuth, async (req, res, next) => {
  try { send(res, 'Notifications', await staffing.listNotifications(req.auth!.id)); } catch (error) { next(error); }
});

api.patch('/notifications/read-all', requireAuth, async (req, res, next) => {
  try { send(res, 'Notifications marked read', await staffing.markAllNotifications(req.auth!.id)); } catch (error) { next(error); }
});

api.patch('/notifications/:notificationId/read', requireAuth, async (req, res, next) => {
  try { send(res, 'Notification read', await staffing.markNotification(req.auth!.id, pid(req.params.notificationId))); } catch (error) { next(error); }
});

api.post('/notifications/register-device', requireAuth, async (req, res, next) => {
  try {
    const body = parse(z.object({ token: z.string().min(8), platform: z.string().optional() }), req.body);
    send(res, 'Device registered', await staffing.registerDevice(req.auth!.id, body.token, body.platform || 'android'));
  } catch (error) { next(error); }
});

api.get('/workers/me/earnings', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Earnings', await staffing.earnings(req.auth!.id)); } catch (error) { next(error); }
});

api.get('/workers/me/payments', requireAuth, requireRole('WORKER'), async (req, res, next) => {
  try { send(res, 'Payments', await staffing.earnings(req.auth!.id)); } catch (error) { next(error); }
});

api.post('/hospitals/register', async (req, res, next) => {
  try {
    const body = parse(z.object({
      organizationName: z.string().min(2),
      fullName: z.string().min(2),
      mobile,
      email: z.string().email(),
      password,
      city: z.string().min(2),
      address: z.string().min(4),
    }), req.body);
    send(res, 'Hospital registered. An admin must verify it before jobs can be published.', await staffing.createHospital(body), 201);
  } catch (error) { next(error); }
});

api.get('/hospitals/me', requireAuth, requireRole('HOSPITAL'), async (req, res, next) => {
  try { send(res, 'Hospital', await staffing.hospitalProfile(req.auth!.hospitalId!)); } catch (error) { next(error); }
});

api.get('/hospitals/me/jobs', requireAuth, requireRole('HOSPITAL'), async (req, res, next) => {
  try { send(res, 'Jobs', await staffing.hospitalJobs(req.auth!.hospitalId!)); } catch (error) { next(error); }
});

api.post('/hospitals/me/jobs', requireAuth, requireRole('HOSPITAL'), async (req, res, next) => {
  try {
    const body = parse(z.object({
      title: z.string().min(2),
      healthcareRole: z.string().min(2),
      department: z.string().min(2),
      description: z.string().optional(),
      qualifications: z.array(z.string()).optional(),
      experienceYears: z.number().optional(),
      skills: z.array(z.string()).optional(),
      certifications: z.array(z.string()).optional(),
      shiftDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
      startTime: z.string().regex(/^\d{2}:\d{2}$/),
      endTime: z.string().regex(/^\d{2}:\d{2}$/),
      shiftType: z.enum(['DAY', 'NIGHT', 'TWELVE_HOUR', 'CUSTOM']).optional(),
      address: z.string().optional(),
      city: z.string().optional(),
      reportingInstructions: z.string().optional(),
      lat: z.number().optional(),
      lng: z.number().optional(),
      compensationMinor: z.number().int().positive(),
      currency: z.string().optional(),
      paymentUnit: z.enum(['PER_SHIFT', 'PER_HOUR', 'PER_DAY']).optional(),
      vacancies: z.number().int().positive(),
      urgent: z.boolean().optional(),
      deadline: z.string().optional(),
      geofenceMeters: z.number().optional(),
      supervisorName: z.string().optional(),
      supervisorPhone: z.string().optional(),
    }), req.body);
    send(res, 'Job published', await staffing.createJob(req.auth!.hospitalId!, req.auth!.id, body), 201);
  } catch (error) { next(error); }
});

api.get('/hospitals/me/applications', requireAuth, requireRole('HOSPITAL'), async (req, res, next) => {
  try { send(res, 'Applications', await staffing.hospitalApplications(req.auth!.hospitalId!)); } catch (error) { next(error); }
});

api.get('/hospitals/me/subscription', requireAuth, requireRole('HOSPITAL'), async (req, res, next) => {
  try { send(res, 'Subscription', await staffing.subscriptionFor(req.auth!.hospitalId!)); } catch (error) { next(error); }
});

api.get('/admin/stats', requireAuth, requireRole('ADMIN', 'SUPER_ADMIN'), async (_req, res, next) => {
  try { send(res, 'Stats', await staffing.adminStats()); } catch (error) { next(error); }
});

api.get('/admin/audit', requireAuth, requireRole('ADMIN', 'SUPER_ADMIN'), async (_req, res, next) => {
  try { send(res, 'Audit', await staffing.auditLogs()); } catch (error) { next(error); }
});

api.post('/admin/hospitals/:hospitalId/verify', requireAuth, requireRole('ADMIN', 'SUPER_ADMIN'), async (req, res, next) => {
  try { send(res, 'Hospital verified', await staffing.verifyHospital(pid(req.params.hospitalId), req.auth!.id)); } catch (error) { next(error); }
});

api.post('/admin/documents/:documentId/review', requireAuth, requireRole('ADMIN', 'SUPER_ADMIN'), async (req, res, next) => {
  try {
    const body = parse(z.object({ status: z.enum(['VERIFIED', 'REJECTED', 'EXPIRED']), reason: z.string().optional() }), req.body);
    send(res, 'Document reviewed', await staffing.reviewDocument(pid(req.params.documentId), req.auth!.id, body.status, body.reason || ''));
  } catch (error) { next(error); }
});

api.post('/admin/payments/:paymentId/status', requireAuth, requireRole('ADMIN', 'SUPER_ADMIN'), async (req, res, next) => {
  try {
    const body = parse(z.object({ status: z.enum(['PAID', 'FAILED']), reference: z.string().min(2) }), req.body);
    send(res, 'Payment updated', await staffing.markPayment(pid(req.params.paymentId), req.auth!.id, body.status, body.reference));
  } catch (error) { next(error); }
});

api.post('/payments/razorpay/webhook', async (req, res, next) => {
  try {
    if (!env.razorpayWebhookSecret) {
      throw new ApiError(503, 'Razorpay is not configured', 'RAZORPAY_NOT_CONFIGURED');
    }
    const raw = Buffer.isBuffer(req.body) ? req.body : Buffer.from(JSON.stringify(req.body || {}));
    const expected = crypto.createHmac('sha256', env.razorpayWebhookSecret).update(raw).digest('hex');
    const signature = req.header('x-razorpay-signature') || '';
    if (signature !== expected) {
      throw new ApiError(400, 'Invalid webhook signature', 'INVALID_SIGNATURE');
    }
    const event = JSON.parse(raw.toString('utf8')) as { event?: string; payload?: { payment?: { entity?: { notes?: { paymentId?: string }; id?: string } } } };
    const paymentId = event.payload?.payment?.entity?.notes?.paymentId;
    if (event.event === 'payment.captured' && paymentId) {
      const payment = await Payment.findById(paymentId);
      if (payment && payment.status !== 'PAID') {
        payment.status = 'PAID';
        payment.paidAt = new Date();
        payment.reference = event.payload?.payment?.entity?.id || '';
        await payment.save();
      }
    }
    send(res, 'Webhook received', {});
  } catch (error) { next(error); }
});
