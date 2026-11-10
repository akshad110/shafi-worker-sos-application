# Hospital SOS Worker

Healthcare professionals use this Android app to find temporary hospital shifts, apply, accept placement offers, and check in for confirmed assignments. Hospitals and admins use the same API and the same MongoDB database.

The phone app is native Java and XML. The API is Node.js, Express, and TypeScript.

## What is already wired

- Worker registration, mobile OTP, password login, refresh tokens, and logout
- Profile, onboarding, availability, and private document upload
- Job search, urgent staffing, one application per job, and status history
- Hospital review, shortlist, and placement offer
- Accept or decline an offer, with a database transaction so accepted placements cannot exceed vacancies
- Shifts, server-side geofenced check-in, check-out, and a pending payment record
- In-app notifications, earnings that stay unpaid until a payment is confirmed
- Hospital and admin routes for the future portal and admin panel
- Swagger at `/api/docs` and a Postman collection in `server/postman`

Development seed data creates demo hospitals and open jobs. It does not create a worker login.

## Backend setup

Requires Node.js 20 or newer.

```bash
cd server
copy .env.example .env
npm install
npm run seed
npm run dev
```

Put the MongoDB Atlas connection string in `server/.env` as `MONGODB_URI`. Do not put it in the Android project. `.env` is gitignored.

`npm test` runs unit checks and an API flow against the `workersos_test` database on the same cluster.

Development-only seed logins, after `npm run seed`:

- Admin: `+919800000001` / `Admin#Demo1`
- Hospital: `+919800000002` / `Hospital#Demo1`

Change `SEED_ADMIN_PASSWORD` and `SEED_HOSPITAL_PASSWORD` before sharing a server.

### OTP

No SMS provider is configured. With `OTP_DEBUG=true` and `NODE_ENV` not `production`, the API returns `debugOtp` so the app can be tested. Set `OTP_DEBUG=false` in production. The server refuses to start if debug OTP is enabled in production. Optional: set `SMS_WEBHOOK_URL` to receive the code.

### Optional integrations

These stay inactive until their environment variables are set. The API does not pretend they succeeded.

- Firebase Cloud Messaging: `FIREBASE_SERVICE_ACCOUNT_JSON`. The Android app also needs a `google-services.json` before push delivery can be added. Notification history is stored in MongoDB either way.
- Private S3 storage: `S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`. Otherwise documents are stored under `server/storage/private` and opened with short-lived signed links.
- WhatsApp: `WHATSAPP_TOKEN` and `WHATSAPP_PHONE_ID`. Otherwise the message log records `SKIPPED`.
- Razorpay: `RAZORPAY_WEBHOOK_SECRET`. The webhook returns `RAZORPAY_NOT_CONFIGURED` until then. Completing a shift creates a `PENDING` payment. It is marked paid only by the webhook or `POST /api/v1/admin/payments/:id/status`.

Socket.IO is available for authenticated clients on the API port. The Android app refreshes from the API when a screen resumes, so a dropped socket does not leave the UI stuck.

## Android setup

Open the project in Android Studio. The app module is Java, XML, ViewBinding, and MVVM.

Debug builds call `http://10.0.2.2:4000/api/v1/`, which is the host machine from the Android emulator. For a physical phone, set this in the project `gradle.properties` or pass it to Gradle:

```text
API_BASE_URL=http://YOUR_COMPUTER_IP:4000/api/v1/
```

Release builds expect HTTPS and do not allow cleartext traffic. Replace `https://api.hospitalsos.example/api/v1/` in `app/build.gradle.kts` with the deployed API before shipping.

```bash
gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Location permission is requested only for check-in and check-out. The app does not track workers in the background. Navigation opens a maps intent. The Google Maps SDK is not embedded because no Maps API key was provided.

Tokens are stored with Android Keystore-backed encrypted preferences. Job, application, shift, and dashboard responses are cached in Room for offline reading. The server remains the source of truth.

## Production

- Set `NODE_ENV=production`, strong JWT secrets, `OTP_DEBUG=false`, and a real SMS provider.
- Restrict the Atlas user and network access. Keep TLS on.
- Put the API behind HTTPS and point the release app at that URL.
- Add Firebase config on both sides before relying on push notifications.
- Sign the release APK in Android Studio. The debug keystore is not a Play Store key.
- Do not commit `.env`, document files, or `google-services.json` if it contains a private project.

The hospital portal and admin panel are not in this repository. Their routes live under `/api/v1/hospitals` and `/api/v1/admin` and use the same users, jobs, applications, and placements.
