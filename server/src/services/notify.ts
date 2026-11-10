import { env, safeLog } from '../config/env';
import { MessageLog, Notification, User } from '../models';
import { emitToUser } from '../realtime';

type Notice = {
  userId: string;
  type: string;
  title: string;
  body: string;
  data?: Record<string, string>;
};

let firebaseReady = false;
let firebaseApp: any = null;

async function messaging() {
  if (firebaseApp || firebaseReady) {
    return firebaseApp;
  }
  firebaseReady = true;
  if (!env.firebaseJson) {
    return null;
  }
  try {
    const loaded = await import('firebase-admin');
    const admin = (loaded as any).default || loaded;
    const cred = JSON.parse(env.firebaseJson);
    firebaseApp = admin.initializeApp({ credential: admin.credential.cert(cred) });
    return firebaseApp;
  } catch (error) {
    console.error(`Firebase was not started: ${safeLog(error)}`);
    firebaseApp = null;
    return null;
  }
}

export async function notify(notice: Notice) {
  const doc = await Notification.create({
    userId: notice.userId,
    type: notice.type,
    title: notice.title,
    body: notice.body,
    data: notice.data || {},
  });
  emitToUser(notice.userId, 'notification', {
    id: doc.id,
    type: notice.type,
    title: notice.title,
    body: notice.body,
    data: notice.data || {},
  });
  const user = await User.findById(notice.userId);
  const tokens = (user?.devices || []).map((device) => device.token).filter(Boolean);
  const app = await messaging();
  if (!app || tokens.length === 0) {
    return doc;
  }
  try {
    const result = await app.messaging().sendEachForMulticast({
      tokens,
      notification: { title: notice.title, body: notice.body },
      data: notice.data || {},
    });
    const invalid: string[] = [];
    result.responses.forEach((item: { success: boolean; error?: { code?: string } }, index: number) => {
      if (!item.success && item.error?.code === 'messaging/registration-token-not-registered') {
        invalid.push(tokens[index]);
      }
    });
    if (invalid.length && user) {
      await User.updateOne({ _id: user._id }, { $pull: { devices: { token: { $in: invalid } } } });
    }
  } catch (error) {
    console.error(`Push delivery failed: ${safeLog(error)}`);
  }
  return doc;
}

export async function queueWhatsApp(to: string, template: string, body: string) {
  if (!env.whatsappToken || !env.whatsappPhoneId || !to) {
    await MessageLog.create({ channel: 'WHATSAPP', to: to || 'unknown', template, status: 'SKIPPED', detail: 'WhatsApp is not configured' });
    return;
  }
  try {
    const response = await fetch(`https://graph.facebook.com/v20.0/${env.whatsappPhoneId}/messages`, {
      method: 'POST',
      headers: {
        authorization: `Bearer ${env.whatsappToken}`,
        'content-type': 'application/json',
      },
      body: JSON.stringify({
        messaging_product: 'whatsapp',
        to: to.replace('+', ''),
        type: 'text',
        text: { body },
      }),
    });
    await MessageLog.create({
      channel: 'WHATSAPP',
      to,
      template,
      status: response.ok ? 'SENT' : 'FAILED',
      detail: response.ok ? '' : `HTTP ${response.status}`,
    });
  } catch (error) {
    await MessageLog.create({ channel: 'WHATSAPP', to, template, status: 'FAILED', detail: safeLog(error) });
  }
}
