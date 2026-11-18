import dotenv from 'dotenv';
import mongoose from 'mongoose';

dotenv.config();
await mongoose.connect(process.env.MONGODB_URI, { serverSelectionTimeoutMS: 20000, tls: true });
const jobs = mongoose.connection.getClient().db('hospitalsos').collection('jobs');
const docs = await jobs.find({}).limit(8).toArray();
console.log('count sample', docs.length);
for (const job of docs) {
  const keys = Object.keys(job).filter((key) => !['password', 'passwordHash'].includes(key));
  console.log('---', job.id || job._id, job.title || job.role || '', 'status', job.status);
  console.log('keys', keys.join(', '));
  for (const key of keys) {
    if (/doc|require|cert|upload|file|attach/i.test(key)) {
      console.log(key, JSON.stringify(job[key]).slice(0, 800));
    }
  }
}
await mongoose.disconnect();
