import assert from 'node:assert/strict';
import test from 'node:test';
import { safeLog } from '../config/env';
import { distanceMeters, missingProfileFields, normalizeMobile, profileCompletion, zonedDateTime } from '../utils/helpers';

test('mobile numbers normalize to E.164', () => {
  assert.equal(normalizeMobile('9876543210'), '+919876543210');
  assert.equal(normalizeMobile('+91 98765 43210'), '+919876543210');
});

test('profile completion and eligibility gaps are explicit', () => {
  const user = { fullName: 'Sara', mobile: '+9198', mobileVerified: true, email: 's@example.com', status: 'ACTIVE' };
  const profile = {
    healthcareRole: '',
    city: '',
    qualifications: [],
    skills: [],
    experienceYears: null,
    address: '',
    onboardingSubmitted: false,
  };
  assert.equal(profileCompletion(user, profile) > 0, true);
  assert.deepEqual(missingProfileFields(user, profile), ['healthcare role', 'city', 'qualifications', 'profile submission']);
});

test('distance is zero at the same point', () => {
  assert.ok(distanceMeters(22.3, 73.18, 22.3, 73.18) < 1);
});

test('IST shift time converts to UTC', () => {
  assert.equal(zonedDateTime('2026-11-10', '20:00').toISOString(), '2026-11-10T14:30:00.000Z');
});

test('logs do not keep a connection string', () => {
  const cleaned = safeLog(new Error('failed mongodb+srv://user:secret@cluster.mongodb.net/workersos'));
  assert.equal(cleaned.includes('secret'), false);
  assert.equal(cleaned.includes('mongodb://***'), true);
});
