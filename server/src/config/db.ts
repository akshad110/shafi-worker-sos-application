import mongoose from 'mongoose';
import { env, safeLog } from './env';

export async function connectDb(): Promise<void> {
  mongoose.set('strictQuery', true);
  mongoose.set('autoIndex', false);
  try {
    await mongoose.connect(env.mongoUri, {
      serverSelectionTimeoutMS: 10000,
      tls: true,
    });
    console.log('MongoDB connected');
  } catch (error) {
    console.error(`MongoDB connection failed: ${safeLog(error)}`);
    throw error;
  }
}

export async function disconnectDb(): Promise<void> {
  await mongoose.disconnect();
}
