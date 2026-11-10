import fs from 'fs';
import path from 'path';
import { GetObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';
import { env, safeLog } from '../config/env';
import { signValue } from '../utils/helpers';
import { ApiError } from '../utils/errors';

const root = path.resolve(process.cwd(), 'storage', 'private');

function s3Ready() {
  const cfg = env.s3;
  return Boolean(cfg.endpoint && cfg.bucket && cfg.accessKey && cfg.secretKey);
}

function client() {
  const cfg = env.s3;
  return new S3Client({
    region: cfg.region,
    endpoint: cfg.endpoint,
    credentials: { accessKeyId: cfg.accessKey, secretAccessKey: cfg.secretKey },
    forcePathStyle: true,
  });
}

export async function savePrivateObject(key: string, body: Buffer, mimeType: string) {
  if (s3Ready()) {
    await client().send(new PutObjectCommand({
      Bucket: env.s3.bucket,
      Key: key,
      Body: body,
      ContentType: mimeType,
    }));
    return;
  }
  const full = path.resolve(root, key);
  if (!full.startsWith(root)) {
    throw new ApiError(400, 'Invalid file path', 'INVALID_FILE');
  }
  fs.mkdirSync(path.dirname(full), { recursive: true });
  fs.writeFileSync(full, body);
}

export function readLocalObject(key: string): Buffer {
  const full = path.resolve(root, key);
  if (!full.startsWith(root) || !fs.existsSync(full)) {
    throw new ApiError(404, 'Document file was not found', 'FILE_NOT_FOUND');
  }
  return fs.readFileSync(full);
}

export async function accessUrl(documentId: string, storageKey: string) {
  if (s3Ready()) {
    try {
      const url = await getSignedUrl(client(), new GetObjectCommand({
        Bucket: env.s3.bucket,
        Key: storageKey,
      }), { expiresIn: 300 });
      return { url, expiresAt: new Date(Date.now() + 300000).toISOString() };
    } catch (error) {
      console.error(`Signed URL failed: ${safeLog(error)}`);
      throw new ApiError(502, 'Could not open the document', 'STORAGE_ERROR');
    }
  }
  const exp = Date.now() + 300000;
  const sig = signValue(`${documentId}.${exp}`, env.jwtAccessSecret);
  return {
    url: `/api/v1/documents/${documentId}/file?exp=${exp}&sig=${sig}`,
    expiresAt: new Date(exp).toISOString(),
  };
}

export function storageMode() {
  return s3Ready() ? 's3' : 'local-private';
}
