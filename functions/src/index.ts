import { onCall, HttpsError } from "firebase-functions/v2/https";
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

initializeApp();
const db = getFirestore();

const VALID_FLAT_TYPES = ["1BHK", "2BHK", "3BHK", "SHOP"];
const PLAN_DAYS: Record<string, number> = { MONTHLY: 30, YEARLY: 365 };

function generateInviteCode(length = 6): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let code = "";
  for (let i = 0; i < length; i++) code += chars[Math.floor(Math.random() * chars.length)];
  return code;
}

export const createSociety = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  const name = (request.data?.name ?? "").trim();
  const flatNo = (request.data?.flatNo ?? "").trim().toUpperCase();
  const flatType = VALID_FLAT_TYPES.includes(request.data?.flatType) ? request.data.flatType : "1BHK";
  if (!name) throw new HttpsError("invalid-argument", "Society name is required.");
  if (!flatNo) throw new HttpsError("invalid-argument", "Flat number is required.");

  const userRef = db.collection("users").doc(uid);
  const existing = await userRef.get();
  if (existing.exists) throw new HttpsError("already-exists", "You're already part of a society.");

  const societyRef = db.collection("societies").doc();
  const inviteCode = generateInviteCode();
  const now = FieldValue.serverTimestamp();

  const defaultEmergencyContacts = [
    { name: "Police", phone: "100", type: "POLICE", isDefault: true },
    { name: "Fire Brigade", phone: "101", type: "FIRE", isDefault: true },
    { name: "Ambulance", phone: "108", type: "MEDICAL", isDefault: true },
    { name: "Women Helpline", phone: "1091", type: "POLICE", isDefault: true },
  ];

  await db.runTransaction(async (tx) => {
    tx.set(societyRef, {
      name,
      secretaryUid: uid,
      inviteCode,
      inviteRole: "RESIDENT",
      subscriptionActive: true,
      subscriptionPlan: "",
      subscriptionStartedAt: null,
      subscriptionExpiresAt: null,
      upiId: null,
      createdAt: now,
    });
    tx.set(userRef, {
      name: request.auth?.token.name ?? "",
      email: request.auth?.token.email ?? null,
      phone: null,
      role: "ADMIN",
      societyId: societyRef.id,
      flatNo,
      flatType,
      fcmToken: null,
      createdAt: now,
    });
    defaultEmergencyContacts.forEach((contact) => {
      tx.set(societyRef.collection("emergencyContacts").doc(), contact);
    });
  });

  return { societyId: societyRef.id, inviteCode };
});

export const redeemInviteCode = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  const inviteCode = (request.data?.inviteCode ?? "").trim().toUpperCase();
  const flatNo = (request.data?.flatNo ?? "").trim().toUpperCase();
  const flatType = VALID_FLAT_TYPES.includes(request.data?.flatType) ? request.data.flatType : "1BHK";
  if (!inviteCode) throw new HttpsError("invalid-argument", "Invite code is required.");
  if (!flatNo) throw new HttpsError("invalid-argument", "Flat number is required.");

  const userRef = db.collection("users").doc(uid);
  const existing = await userRef.get();
  if (existing.exists) throw new HttpsError("already-exists", "You're already part of a society.");

  const querySnap = await db.collection("societies").where("inviteCode", "==", inviteCode).limit(1).get();
  if (querySnap.empty) throw new HttpsError("not-found", "Invalid invite code.");
  const societyDoc = querySnap.docs[0];
  const society = societyDoc.data();

  await userRef.set({
    name: request.auth?.token.name ?? "",
    email: request.auth?.token.email ?? null,
    phone: null,
    role: society.inviteRole ?? "RESIDENT",
    societyId: societyDoc.id,
    flatNo,
    flatType,
    fcmToken: null,
    createdAt: FieldValue.serverTimestamp(),
  });

  return { societyId: societyDoc.id, societyName: society.name, role: society.inviteRole ?? "RESIDENT" };
});

async function requireAdmin(uid: string): Promise<string> {
  const userSnap = await db.collection("users").doc(uid).get();
  if (!userSnap.exists) throw new HttpsError("failed-precondition", "Profile not found.");
  const profile = userSnap.data()!;
  if (profile.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Only the secretary can manage the subscription.");
  }
  return profile.societyId as string;
}

export const activateSubscription = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  const plan = request.data?.plan;
  const days = PLAN_DAYS[plan];
  if (!days) throw new HttpsError("invalid-argument", "Invalid plan.");

  const societyId = await requireAdmin(uid);
  const now = Date.now();
  await db.collection("societies").doc(societyId).update({
    subscriptionActive: true,
    subscriptionPlan: plan,
    subscriptionStartedAt: now,
    subscriptionExpiresAt: now + days * 24 * 60 * 60 * 1000,
  });

  return { ok: true };
});

export const cancelSubscription = onCall(async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");
  const societyId = await requireAdmin(uid);
  await db.collection("societies").doc(societyId).update({ subscriptionActive: false });
  return { ok: true };
});
