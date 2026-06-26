# Firebase Setup

This project is wired for Firebase (Auth, Firestore, Storage, Messaging, Crashlytics,
Analytics, Functions) but ships with **placeholder credentials** so CI and local builds
compile without real secrets. Follow this once, on your actual Firebase project, to make
the app functional.

## 1. Create / select the Firebase project

You said you already have a Firebase/GCP project with billing enabled (required for
Cloud Functions on the Blaze plan). Open it at https://console.firebase.google.com.

## 2. Register the Android app

1. Project settings → Your apps → Add app → Android.
2. Package name: `com.societyconnect` (must match `applicationId` in `app/build.gradle.kts` exactly).
3. App nickname: anything, e.g. "Society Connect".
4. SHA-1 signing certificate: required for Google Sign-In and Phone Auth (Play Integrity).
   - Debug SHA-1: `./gradlew signingReport` (look under the `debug` variant), or
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`.
   - Release SHA-1: from whatever keystore `RELEASE_KEYSTORE_PATH` points at in CI.
   - Add both — debug now, release before you ship a signed build.
5. Download the generated `google-services.json` and replace
   `app/google-services.json` in this repo with it (overwrite the placeholder).
   This file is **not** treated as a secret here — it's restricted by package name +
   SHA-1 fingerprint and by the Firestore/Storage security rules below, so it's safe to
   commit. Commit the real file once you have it.

## 3. Enable Auth providers

Authentication → Sign-in method → enable:
- **Phone** (for resident/security OTP login).
- **Google** (for faster sign-in where the phone number isn't required).

For Phone Auth in production, also configure the SHA-1 above and consider enabling
**App Check** later (Phase 2+) to stop OTP abuse/quota draining from outside the app.

## 4. Create the Firestore database

Firestore Database → Create database → **production mode** → pick a region close to
your users (e.g. `asia-south1` for India).

The collection structure is created automatically by the app/Cloud Functions the first
time data is written — you don't need to manually create collections. See
`FIRESTORE_SCHEMA.md` for the full schema.

## 5. Deploy security rules and indexes

From your machine (not CI, since this pushes to your live project):

```bash
npm install -g firebase-tools
firebase login
```

Edit `.firebaserc` and replace `REPLACE_WITH_YOUR_FIREBASE_PROJECT_ID` with your real
Firebase project ID (Project settings → General → Project ID).

```bash
firebase deploy --only firestore:rules,firestore:indexes,storage
```

## 6. Cloud Storage

Storage → Get started → production mode, same region as Firestore. The rules deployed
in step 5 (`storage.rules`) already scope every path to `/societies/{societyId}/...`
and check the caller's society membership.

## 7. Cloud Functions

Not yet implemented in this repo (`functions/` directory doesn't exist yet — coming in
a later task: `createSociety` and `redeemInviteCode` callables, plus FCM push triggers).
Once that code lands, deploy with:

```bash
firebase deploy --only functions
```

This requires the Blaze (pay-as-you-go) plan, which you already have via billing.

## 8. Cloud Messaging (push notifications)

No manual console setup needed for FCM itself — it activates automatically once the
real `google-services.json` is in place. Android 13+ requires the runtime
`POST_NOTIFICATIONS` permission, which the app already requests.

## 9. Crashlytics & Analytics

Crashlytics and Analytics auto-enable once the real `google-services.json` is present
and the app reports at least one session — no manual console step required beyond
having created the project.

## 10. What you do NOT need to do manually

- Create Firestore collections/documents — created on first write.
- Create Storage folders — created on first upload.
- Configure Remote Config — deferred to a later phase.
- Set up App Check — deferred to a later phase.
- Set up Razorpay/payment gateway accounts — Phase 1 uses manual UPI deep links, no
  payment gateway account required yet.

## Summary checklist

- [ ] Register Android app (`com.societyconnect`) in Firebase console, add debug + release SHA-1.
- [ ] Replace `app/google-services.json` placeholder with the real downloaded file.
- [ ] Enable Phone + Google sign-in providers.
- [ ] Create Firestore database (production mode, nearest region).
- [ ] Set real project ID in `.firebaserc`.
- [ ] `firebase deploy --only firestore:rules,firestore:indexes,storage`.
- [ ] Create Cloud Storage bucket (production mode, same region).
