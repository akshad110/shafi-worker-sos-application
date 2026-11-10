import assert from 'node:assert/strict';
import path from 'path';
import test from 'node:test';
import dotenv from 'dotenv';
import bcrypt from 'bcryptjs';
import request from 'supertest';

dotenv.config({ path: path.join(__dirname, '../../.env') });
process.env.NODE_ENV = 'test';
process.env.OTP_DEBUG = 'true';

test('worker application, offer, and geofenced attendance', async () => {
  const { connectDb, disconnectDb } = await import('../config/db');
  const { createApp } = await import('../app');
  const { Application, Hospital, Job, Notification, OtpChallenge, Payment, Placement, Session, Shift, User, WorkerProfile } = await import('../models');
  const { zonedDateTime } = await import('../utils/helpers');
  await connectDb();
  await Promise.all([User.init(), Application.init(), Job.init(), Placement.init(), Shift.init(), Hospital.init()]);
  const app = createApp();
  const stamp = Date.now();
  const mobile = `+9197${String(stamp).slice(-8)}`;
  const email = `worker.${stamp}@flow.test`;
  try {
    const hospital = await Hospital.create({
      name: `Flow Test Hospital ${stamp}`,
      verified: true,
      city: 'Vadodara',
      address: 'Test Road, Vadodara',
      location: { type: 'Point', coordinates: [73.18, 22.3] },
      geofenceMeters: 300,
    });
    const hospitalUser = await User.create({
      role: 'HOSPITAL',
      fullName: 'Flow Hospital',
      mobile: `+9196${String(stamp).slice(-8)}`,
      email: `hospital.${stamp}@flow.test`,
      passwordHash: await bcrypt.hash('Hospital#Demo1', 10),
      mobileVerified: true,
      status: 'ACTIVE',
      hospitalId: hospital._id,
    });
    const startAt = new Date(Date.now() - 5 * 60 * 1000);
    const endAt = new Date(Date.now() + 8 * 60 * 60 * 1000);
    const job = await Job.create({
      hospitalId: hospital._id,
      hospitalName: hospital.name,
      hospitalVerified: true,
      title: 'ICU Registered Nurse',
      healthcareRole: 'Registered Nurse',
      department: 'ICU',
      description: 'Night cover',
      qualifications: ['GNM'],
      skills: ['ICU'],
      certifications: ['BLS'],
      shiftDate: '2026-11-10',
      startTime: '20:00',
      endTime: '08:00',
      startAt,
      endAt,
      shiftType: 'NIGHT',
      address: hospital.address,
      city: 'Vadodara',
      reportingInstructions: 'Nursing station',
      location: { type: 'Point', coordinates: [73.18, 22.3] },
      compensationMinor: 250000,
      currency: 'INR',
      paymentUnit: 'PER_SHIFT',
      vacancies: 1,
      urgent: true,
      deadline: endAt,
      status: 'OPEN',
      geofenceMeters: 300,
    });

    const registered = await request(app).post('/api/v1/auth/register').send({
      fullName: 'Sara Worker',
      mobile,
      email,
      password: 'Worker#Demo1',
      healthcareRole: 'Registered Nurse',
      consent: true,
    });
    assert.equal(registered.status, 201);
    assert.equal(typeof registered.body.data.debugOtp, 'string');

    const verified = await request(app).post('/api/v1/auth/verify-otp').send({
      mobile,
      code: registered.body.data.debugOtp,
      purpose: 'REGISTER',
    });
    assert.equal(verified.status, 200);
    const token = verified.body.data.accessToken as string;

    const blocked = await request(app)
      .post(`/api/v1/jobs/${job.id}/applications`)
      .set('Authorization', `Bearer ${token}`)
      .send({});
    assert.equal(blocked.status, 403);
    assert.equal(blocked.body.code, 'PROFILE_INCOMPLETE');

    const saved = await request(app)
      .patch('/api/v1/workers/me/profile')
      .set('Authorization', `Bearer ${token}`)
      .send({
        city: 'Vadodara',
        address: 'Test address',
        qualifications: ['GNM'],
        skills: ['ICU'],
        experienceYears: 3,
        onboardingSubmitted: true,
      });
    assert.equal(saved.status, 200);
    assert.equal(saved.body.data.canApply, true);

    const applied = await request(app)
      .post(`/api/v1/jobs/${job.id}/applications`)
      .set('Authorization', `Bearer ${token}`)
      .send({});
    assert.equal(applied.status, 201);
    const duplicate = await request(app)
      .post(`/api/v1/jobs/${job.id}/applications`)
      .set('Authorization', `Bearer ${token}`)
      .send({});
    assert.equal(duplicate.status, 409);
    assert.equal(duplicate.body.code, 'DUPLICATE_APPLICATION');

    const hospitalLogin = await request(app).post('/api/v1/auth/login').send({
      mobile: hospitalUser.mobile,
      password: 'Hospital#Demo1',
    });
    assert.equal(hospitalLogin.status, 200);
    const hospitalToken = hospitalLogin.body.data.accessToken as string;
    const applicationId = applied.body.data.id as string;

    const forbidden = await request(app)
      .get('/api/v1/admin/stats')
      .set('Authorization', `Bearer ${token}`);
    assert.equal(forbidden.status, 403);

    await request(app).post(`/api/v1/applications/${applicationId}/review`).set('Authorization', `Bearer ${hospitalToken}`).send({});
    await request(app).post(`/api/v1/applications/${applicationId}/shortlist`).set('Authorization', `Bearer ${hospitalToken}`).send({});
    const offer = await request(app).post(`/api/v1/applications/${applicationId}/offer`).set('Authorization', `Bearer ${hospitalToken}`).send({});
    assert.equal(offer.status, 201);
    const placementId = offer.body.data.id as string;

    const accepted = await request(app)
      .post(`/api/v1/placements/${placementId}/accept`)
      .set('Authorization', `Bearer ${token}`)
      .send({});
    assert.equal(accepted.status, 200);
    const closed = await Job.findById(job.id);
    assert.equal(closed?.filledCount, 1);
    assert.equal(closed?.status, 'CLOSED');

    const shift = await Shift.findOne({ workerId: (await User.findOne({ email }))!._id });
    assert.ok(shift);
    const outside = await request(app)
      .post(`/api/v1/shifts/${shift!.id}/check-in`)
      .set('Authorization', `Bearer ${token}`)
      .send({ lat: 28.6, lng: 77.2 });
    assert.equal(outside.status, 403);
    assert.equal(outside.body.code, 'OUTSIDE_GEOFENCE');

    const inside = await request(app)
      .post(`/api/v1/shifts/${shift!.id}/check-in`)
      .set('Authorization', `Bearer ${token}`)
      .send({ lat: 22.3, lng: 73.18 });
    assert.equal(inside.status, 200);
    assert.equal(inside.body.data.status, 'ACTIVE');

    const done = await request(app)
      .post(`/api/v1/shifts/${shift!.id}/check-out`)
      .set('Authorization', `Bearer ${token}`)
      .send({ lat: 22.3, lng: 73.18 });
    assert.equal(done.status, 200);
    const payment = await Payment.findOne({ shiftId: shift!._id });
    assert.equal(payment?.status, 'PENDING');
    assert.equal(zonedDateTime('2026-11-10', '20:00').toISOString(), '2026-11-10T14:30:00.000Z');
    await WorkerProfile.deleteMany({ userId: (await User.findOne({ email }))?._id });
    await Application.deleteMany({ jobId: job._id });
  } finally {
    const users = await User.find({ email: { $in: [email, `hospital.${stamp}@flow.test`] } });
    const ids = users.map((user) => user._id);
    await Notification.deleteMany({ userId: { $in: ids } });
    await Session.deleteMany({ userId: { $in: ids } });
    await Shift.deleteMany({ workerId: { $in: ids } });
    await Payment.deleteMany({ workerId: { $in: ids } });
    await Placement.deleteMany({ workerId: { $in: ids } });
    await Application.deleteMany({ workerId: { $in: ids } });
    await WorkerProfile.deleteMany({ userId: { $in: ids } });
    await Job.deleteMany({ hospitalName: `Flow Test Hospital ${stamp}` });
    await Hospital.deleteMany({ name: `Flow Test Hospital ${stamp}` });
    await OtpChallenge.deleteMany({ mobile: { $in: [mobile, `+9196${String(stamp).slice(-8)}`] } });
    await User.deleteMany({ _id: { $in: ids } });
    await disconnectDb();
  }
});
