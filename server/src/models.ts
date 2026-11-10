import mongoose, { Schema, Types } from 'mongoose';

const pointSchema = new Schema({
  type: { type: String, enum: ['Point'], default: 'Point' },
  coordinates: { type: [Number], required: true },
}, { _id: false });

const userSchema = new Schema({
  role: { type: String, enum: ['WORKER', 'HOSPITAL', 'ADMIN', 'SUPER_ADMIN'], required: true, index: true },
  fullName: { type: String, required: true, trim: true },
  mobile: { type: String, required: true, unique: true },
  email: { type: String, required: true, unique: true, lowercase: true, trim: true },
  passwordHash: { type: String },
  mobileVerified: { type: Boolean, default: false },
  status: { type: String, enum: ['PENDING', 'ACTIVE', 'SUSPENDED'], default: 'PENDING', index: true },
  hospitalId: { type: Schema.Types.ObjectId, ref: 'Hospital' },
  devices: [{
    token: { type: String, required: true },
    platform: { type: String, default: 'android' },
    updatedAt: { type: Date, default: Date.now },
  }],
  preferences: {
    jobs: { type: Boolean, default: true },
    offers: { type: Boolean, default: true },
    shifts: { type: Boolean, default: true },
    payments: { type: Boolean, default: true },
  },
}, { timestamps: true });

const sessionSchema = new Schema({
  userId: { type: Schema.Types.ObjectId, ref: 'User', required: true, index: true },
  tokenHash: { type: String, required: true },
  expiresAt: { type: Date, required: true },
  revokedAt: { type: Date },
  replacedBy: { type: Schema.Types.ObjectId },
}, { timestamps: true });

const otpSchema = new Schema({
  mobile: { type: String, required: true, index: true },
  purpose: { type: String, enum: ['REGISTER', 'LOGIN'], required: true },
  codeHash: { type: String, required: true },
  expiresAt: { type: Date, required: true },
  attempts: { type: Number, default: 0 },
  consumedAt: { type: Date },
}, { timestamps: true });

const workerProfileSchema = new Schema({
  userId: { type: Schema.Types.ObjectId, ref: 'User', required: true, unique: true },
  healthcareRole: { type: String, default: '' },
  specialization: { type: String, default: '' },
  qualifications: [{ type: String }],
  skills: [{ type: String }],
  experienceYears: { type: Number, min: 0, max: 60 },
  preferredDepartments: [{ type: String }],
  preferredShifts: [{ type: String }],
  preferredLocations: [{ type: String }],
  address: { type: String, default: '' },
  city: { type: String, default: '' },
  emergencyContactName: { type: String, default: '' },
  emergencyContactPhone: { type: String, default: '' },
  weeklyAvailability: [{
    day: { type: Number, min: 0, max: 6 },
    shifts: [{ type: String }],
  }],
  unavailableDates: [{ type: String }],
  onboardingStep: { type: Number, default: 1 },
  onboardingSubmitted: { type: Boolean, default: false },
  verificationStatus: {
    type: String,
    enum: ['INCOMPLETE', 'PENDING', 'VERIFIED', 'REJECTED'],
    default: 'INCOMPLETE',
  },
  consentAcceptedAt: { type: Date },
}, { timestamps: true });

const documentSchema = new Schema({
  workerId: { type: Schema.Types.ObjectId, ref: 'User', required: true, index: true },
  type: { type: String, required: true },
  fileName: { type: String, required: true },
  mimeType: { type: String, required: true },
  size: { type: Number, required: true },
  storageKey: { type: String, required: true },
  status: { type: String, enum: ['PENDING', 'VERIFIED', 'REJECTED', 'EXPIRED'], default: 'PENDING' },
  expiresAt: { type: Date },
  rejectionReason: { type: String, default: '' },
}, { timestamps: true });

const hospitalSchema = new Schema({
  name: { type: String, required: true, trim: true },
  verified: { type: Boolean, default: false },
  address: { type: String, default: '' },
  city: { type: String, default: '', index: true },
  location: { type: pointSchema },
  departments: [{ type: String }],
  contactPhone: { type: String, default: '' },
  geofenceMeters: { type: Number, default: 300 },
}, { timestamps: true });
hospitalSchema.index({ location: '2dsphere' });

const jobSchema = new Schema({
  hospitalId: { type: Schema.Types.ObjectId, ref: 'Hospital', required: true, index: true },
  hospitalName: { type: String, required: true },
  hospitalVerified: { type: Boolean, default: false },
  title: { type: String, required: true },
  healthcareRole: { type: String, required: true, index: true },
  department: { type: String, required: true },
  description: { type: String, default: '' },
  qualifications: [{ type: String }],
  experienceYears: { type: Number, default: 0 },
  skills: [{ type: String }],
  certifications: [{ type: String }],
  shiftDate: { type: String, required: true, index: true },
  startTime: { type: String, required: true },
  endTime: { type: String, required: true },
  startAt: { type: Date, required: true },
  endAt: { type: Date, required: true },
  shiftType: { type: String, enum: ['DAY', 'NIGHT', 'TWELVE_HOUR', 'CUSTOM'], default: 'CUSTOM' },
  address: { type: String, default: '' },
  city: { type: String, default: '' },
  reportingInstructions: { type: String, default: '' },
  location: { type: pointSchema },
  compensationMinor: { type: Number, required: true, min: 0 },
  currency: { type: String, default: 'INR' },
  paymentUnit: { type: String, enum: ['PER_SHIFT', 'PER_HOUR', 'PER_DAY'], default: 'PER_SHIFT' },
  vacancies: { type: Number, required: true, min: 1 },
  filledCount: { type: Number, default: 0, min: 0 },
  urgent: { type: Boolean, default: false, index: true },
  deadline: { type: Date },
  status: { type: String, enum: ['DRAFT', 'OPEN', 'CLOSED', 'CANCELLED'], default: 'OPEN', index: true },
  geofenceMeters: { type: Number, default: 300 },
  supervisorName: { type: String, default: '' },
  supervisorPhone: { type: String, default: '' },
  seedKey: { type: String },
}, { timestamps: true });
jobSchema.index({ status: 1, urgent: 1, shiftDate: 1 });
jobSchema.index({ location: '2dsphere' });
jobSchema.index({ seedKey: 1 }, { unique: true, sparse: true });

const historySchema = new Schema({
  status: { type: String, required: true },
  at: { type: Date, default: Date.now },
  actorId: { type: Schema.Types.ObjectId },
  note: { type: String, default: '' },
}, { _id: false });

const applicationSchema = new Schema({
  jobId: { type: Schema.Types.ObjectId, ref: 'Job', required: true },
  workerId: { type: Schema.Types.ObjectId, ref: 'User', required: true, index: true },
  hospitalId: { type: Schema.Types.ObjectId, ref: 'Hospital', required: true },
  status: {
    type: String,
    enum: ['SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'OFFERED', 'ACCEPTED', 'DECLINED', 'REJECTED', 'WITHDRAWN', 'OFFER_EXPIRED'],
    default: 'SUBMITTED',
    index: true,
  },
  history: [historySchema],
  reference: { type: String, required: true },
}, { timestamps: true });
applicationSchema.index({ jobId: 1, workerId: 1 }, { unique: true });

const placementSchema = new Schema({
  applicationId: { type: Schema.Types.ObjectId, ref: 'Application', required: true, unique: true },
  jobId: { type: Schema.Types.ObjectId, ref: 'Job', required: true },
  workerId: { type: Schema.Types.ObjectId, ref: 'User', required: true, index: true },
  hospitalId: { type: Schema.Types.ObjectId, ref: 'Hospital', required: true },
  status: { type: String, enum: ['OFFERED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELLED'], default: 'OFFERED' },
  expiresAt: { type: Date, required: true },
  compensationMinor: { type: Number, required: true },
  currency: { type: String, default: 'INR' },
  instructions: { type: String, default: '' },
  acceptedAt: { type: Date },
  declinedAt: { type: Date },
}, { timestamps: true });

const shiftSchema = new Schema({
  placementId: { type: Schema.Types.ObjectId, ref: 'Placement', required: true, unique: true },
  jobId: { type: Schema.Types.ObjectId, ref: 'Job', required: true },
  workerId: { type: Schema.Types.ObjectId, ref: 'User', required: true, index: true },
  hospitalId: { type: Schema.Types.ObjectId, ref: 'Hospital', required: true },
  status: { type: String, enum: ['UPCOMING', 'ACTIVE', 'COMPLETED', 'CANCELLED'], default: 'UPCOMING', index: true },
  startAt: { type: Date, required: true },
  endAt: { type: Date, required: true },
  checkIn: {
    at: Date,
    lat: Number,
    lng: Number,
    insideGeofence: Boolean,
  },
  checkOut: {
    at: Date,
    lat: Number,
    lng: Number,
  },
  feedbackRating: { type: Number, min: 1, max: 5 },
  feedbackComment: { type: String, default: '' },
  incidents: [{
    category: String,
    description: String,
    at: { type: Date, default: Date.now },
  }],
  reminderSent: { type: Boolean, default: false },
}, { timestamps: true });

const notificationSchema = new Schema({
  userId: { type: Schema.Types.ObjectId, ref: 'User', required: true, index: true },
  type: { type: String, required: true },
  title: { type: String, required: true },
  body: { type: String, required: true },
  read: { type: Boolean, default: false, index: true },
  data: { type: Schema.Types.Mixed, default: {} },
}, { timestamps: true });

const planSchema = new Schema({
  code: { type: String, required: true, unique: true },
  name: { type: String, required: true },
  priceMinor: { type: Number, required: true },
  currency: { type: String, default: 'INR' },
  jobPostsPerMonth: { type: Number, default: 20 },
  active: { type: Boolean, default: true },
}, { timestamps: true });

const subscriptionSchema = new Schema({
  hospitalId: { type: Schema.Types.ObjectId, ref: 'Hospital', required: true, index: true },
  planId: { type: Schema.Types.ObjectId, ref: 'SubscriptionPlan', required: true },
  status: { type: String, enum: ['ACTIVE', 'EXPIRED', 'CANCELLED'], default: 'ACTIVE' },
  startsAt: { type: Date, required: true },
  endsAt: { type: Date, required: true },
}, { timestamps: true });

const paymentSchema = new Schema({
  workerId: { type: Schema.Types.ObjectId, ref: 'User', index: true },
  hospitalId: { type: Schema.Types.ObjectId, ref: 'Hospital' },
  shiftId: { type: Schema.Types.ObjectId, ref: 'Shift' },
  placementId: { type: Schema.Types.ObjectId, ref: 'Placement' },
  amountMinor: { type: Number, required: true },
  currency: { type: String, default: 'INR' },
  status: { type: String, enum: ['PENDING', 'PAID', 'FAILED'], default: 'PENDING', index: true },
  kind: { type: String, enum: ['WORKER_EARNING', 'HOSPITAL_SUBSCRIPTION'], default: 'WORKER_EARNING' },
  paidAt: { type: Date },
  reference: { type: String, default: '' },
}, { timestamps: true });

const messageLogSchema = new Schema({
  channel: { type: String, enum: ['WHATSAPP', 'EMAIL', 'SMS'], required: true },
  to: { type: String, required: true },
  template: { type: String, required: true },
  status: { type: String, enum: ['SENT', 'SKIPPED', 'FAILED'], required: true },
  detail: { type: String, default: '' },
}, { timestamps: true });

const auditSchema = new Schema({
  actorId: { type: Schema.Types.ObjectId, ref: 'User' },
  action: { type: String, required: true, index: true },
  entityType: { type: String, required: true },
  entityId: { type: String, default: '' },
  meta: { type: Schema.Types.Mixed, default: {} },
}, { timestamps: true });

export const User = mongoose.model('User', userSchema, 'workerAccounts');
export const Session = mongoose.model('Session', sessionSchema, 'workerSessions');
export const OtpChallenge = mongoose.model('OtpChallenge', otpSchema);
export const WorkerProfile = mongoose.model('WorkerProfile', workerProfileSchema);
export const WorkerDocument = mongoose.model('WorkerDocument', documentSchema);
export const Hospital = mongoose.model('Hospital', hospitalSchema);
export const Job = mongoose.model('Job', jobSchema);
export const Application = mongoose.model('Application', applicationSchema);
export const Placement = mongoose.model('Placement', placementSchema);
export const Shift = mongoose.model('Shift', shiftSchema);
export const Notification = mongoose.model('Notification', notificationSchema, 'workerNotifications');
export const SubscriptionPlan = mongoose.model('SubscriptionPlan', planSchema);
export const Subscription = mongoose.model('Subscription', subscriptionSchema);
export const Payment = mongoose.model('Payment', paymentSchema);
export const MessageLog = mongoose.model('MessageLog', messageLogSchema);
export const AuditLog = mongoose.model('AuditLog', auditSchema);

export type Id = Types.ObjectId | string;
