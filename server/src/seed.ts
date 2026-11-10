import dotenv from 'dotenv';
import bcrypt from 'bcryptjs';
import { assertEnv, safeLog } from './config/env';
import { connectDb, disconnectDb } from './config/db';
import { Hospital, Job, SubscriptionPlan, User } from './models';
import { zonedDateTime } from './utils/helpers';

dotenv.config();

function shift(dayOffset: number, start: string, end: string) {
  const day = new Date();
  day.setDate(day.getDate() + dayOffset);
  const shiftDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).format(day);
  const startAt = zonedDateTime(shiftDate, start);
  let endAt = zonedDateTime(shiftDate, end);
  if (endAt.getTime() <= startAt.getTime()) endAt = new Date(endAt.getTime() + 24 * 60 * 60 * 1000);
  return { shiftDate, startAt, endAt };
}

async function main() {
  assertEnv();
  await connectDb();
  const adminPassword = process.env.SEED_ADMIN_PASSWORD || 'Admin#Demo1';
  const hospitalPassword = process.env.SEED_HOSPITAL_PASSWORD || 'Hospital#Demo1';

  await SubscriptionPlan.updateOne(
    { code: 'professional' },
    { code: 'professional', name: 'Professional', priceMinor: 499900, currency: 'INR', jobPostsPerMonth: 40, active: true },
    { upsert: true },
  );

  const hospitals = [
    {
      name: 'City Memorial Hospital',
      city: 'Vadodara',
      address: 'Race Course, Vadodara',
      coordinates: [73.165, 22.315],
      contactPhone: '+912650000001',
    },
    {
      name: 'Riverside Emergency Centre',
      city: 'Vadodara',
      address: 'Alkapuri, Vadodara',
      coordinates: [73.172, 22.311],
      contactPhone: '+912650000002',
    },
    {
      name: 'Greenfield General Hospital',
      city: 'Vadodara',
      address: 'Manjalpur, Vadodara',
      coordinates: [73.198, 22.278],
      contactPhone: '+912650000003',
    },
  ];

  const hospitalIds: Record<string, string> = {};
  for (const item of hospitals) {
    const hospital = await Hospital.findOneAndUpdate(
      { name: item.name },
      {
        name: item.name,
        verified: true,
        city: item.city,
        address: item.address,
        contactPhone: item.contactPhone,
        departments: ['ICU', 'Emergency', 'General Ward', 'Radiology'],
        location: { type: 'Point', coordinates: item.coordinates },
        geofenceMeters: 400,
      },
      { upsert: true, returnDocument: 'after' },
    );
    hospitalIds[item.name] = hospital.id;
  }

  await User.updateOne(
    { email: 'admin.demo@hospitalsos.local' },
    {
      role: 'ADMIN',
      fullName: 'Hospital SOS Admin',
      mobile: '+919800000001',
      email: 'admin.demo@hospitalsos.local',
      passwordHash: await bcrypt.hash(adminPassword, 10),
      mobileVerified: true,
      status: 'ACTIVE',
    },
    { upsert: true },
  );

  await User.updateOne(
    { email: 'hospital.demo@hospitalsos.local' },
    {
      role: 'HOSPITAL',
      fullName: 'City Memorial Staffing',
      mobile: '+919800000002',
      email: 'hospital.demo@hospitalsos.local',
      passwordHash: await bcrypt.hash(hospitalPassword, 10),
      mobileVerified: true,
      status: 'ACTIVE',
      hospitalId: hospitalIds['City Memorial Hospital'],
    },
    { upsert: true },
  );

  const jobs = [
    {
      seedKey: 'demo-icu-night',
      hospitalName: 'City Memorial Hospital',
      title: 'ICU Registered Nurse',
      healthcareRole: 'Registered Nurse',
      department: 'ICU',
      shiftType: 'NIGHT',
      start: '20:00',
      end: '08:00',
      dayOffset: 1,
      compensationMinor: 250000,
      urgent: true,
      vacancies: 2,
      description: 'Night ICU cover for a temporary vacancy. Report 15 minutes early at the nursing station.',
      qualifications: ['GNM or BSc Nursing', 'Valid nursing registration'],
      skills: ['ICU', 'Ventilator care'],
      certifications: ['BLS'],
    },
    {
      seedKey: 'demo-er-day',
      hospitalName: 'Riverside Emergency Centre',
      title: 'Emergency Room Nurse',
      healthcareRole: 'Registered Nurse',
      department: 'Emergency',
      shiftType: 'TWELVE_HOUR',
      start: '08:00',
      end: '20:00',
      dayOffset: 1,
      compensationMinor: 220000,
      urgent: true,
      vacancies: 1,
      description: 'Emergency bay cover. BLS and ACLS preferred.',
      qualifications: ['GNM or BSc Nursing'],
      skills: ['Emergency', 'Triage'],
      certifications: ['BLS', 'ACLS'],
    },
    {
      seedKey: 'demo-ward',
      hospitalName: 'Greenfield General Hospital',
      title: 'General Ward Nurse',
      healthcareRole: 'Registered Nurse',
      department: 'General Ward',
      shiftType: 'DAY',
      start: '09:00',
      end: '17:00',
      dayOffset: 2,
      compensationMinor: 180000,
      urgent: false,
      vacancies: 3,
      description: 'Ward rounds and admissions. One meal is provided on duty.',
      qualifications: ['GNM or BSc Nursing'],
      skills: ['Ward nursing'],
      certifications: ['BLS'],
    },
    {
      seedKey: 'demo-lab',
      hospitalName: 'City Memorial Hospital',
      title: 'Lab Technician',
      healthcareRole: 'Lab Technician',
      department: 'Laboratory',
      shiftType: 'DAY',
      start: '08:00',
      end: '16:00',
      dayOffset: 2,
      compensationMinor: 160000,
      urgent: false,
      vacancies: 1,
      description: 'Sample collection and routine lab support for the day shift.',
      qualifications: ['DMLT or BSc MLT'],
      skills: ['Phlebotomy', 'Sample handling'],
      certifications: [],
    },
    {
      seedKey: 'demo-radio',
      hospitalName: 'Riverside Emergency Centre',
      title: 'Radiology Technician',
      healthcareRole: 'Radiology Technician',
      department: 'Radiology',
      shiftType: 'DAY',
      start: '10:00',
      end: '18:00',
      dayOffset: 3,
      compensationMinor: 200000,
      urgent: false,
      vacancies: 1,
      description: 'X-ray support for the outpatient and emergency lists.',
      qualifications: ['Diploma in radiography'],
      skills: ['X-ray'],
      certifications: [],
    },
  ];

  for (const item of jobs) {
    const times = shift(item.dayOffset, item.start, item.end);
    const hospitalId = hospitalIds[item.hospitalName];
    const hospital = hospitals.find((entry) => entry.name === item.hospitalName)!;
    await Job.updateOne(
      { seedKey: item.seedKey },
      {
        seedKey: item.seedKey,
        hospitalId,
        hospitalName: item.hospitalName,
        hospitalVerified: true,
        title: item.title,
        healthcareRole: item.healthcareRole,
        department: item.department,
        description: item.description,
        qualifications: item.qualifications,
        experienceYears: 1,
        skills: item.skills,
        certifications: item.certifications,
        shiftDate: times.shiftDate,
        startTime: item.start,
        endTime: item.end,
        startAt: times.startAt,
        endAt: times.endAt,
        shiftType: item.shiftType,
        address: hospital.address,
        city: hospital.city,
        reportingInstructions: 'Carry your registration and a government photo ID. Report to the department desk.',
        location: { type: 'Point', coordinates: hospital.coordinates },
        compensationMinor: item.compensationMinor,
        currency: 'INR',
        paymentUnit: 'PER_SHIFT',
        vacancies: item.vacancies,
        urgent: item.urgent,
        deadline: times.startAt,
        status: 'OPEN',
        geofenceMeters: 400,
        supervisorName: 'Duty supervisor',
        supervisorPhone: hospital.contactPhone,
      },
      { upsert: true },
    );
  }

  console.log('Development seed completed. Demo hospitals, jobs, one admin, and one hospital user are ready.');
  console.log('No worker account was created. Register one in the Android app.');
  await disconnectDb();
}

main().catch(async (error) => {
  console.error(safeLog(error));
  await disconnectDb().catch(() => undefined);
  process.exit(1);
});
