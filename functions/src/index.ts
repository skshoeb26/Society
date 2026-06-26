import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentCreated, onDocumentUpdated } from "firebase-functions/v2/firestore";
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

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

// ─── PUSH NOTIFICATIONS ──────────────────────────────────────────────────

async function sendToTokens(
  tokens: (string | null | undefined)[],
  title: string,
  body: string,
  data: Record<string, string>
): Promise<void> {
  const cleanTokens = Array.from(new Set(tokens.filter((t): t is string => !!t)));
  if (cleanTokens.length === 0) return;
  await messaging.sendEachForMulticast({
    tokens: cleanTokens,
    notification: { title, body },
    data,
  });
}

async function getTokensForSociety(
  societyId: string,
  roles?: string[]
): Promise<(string | undefined)[]> {
  const snap = await db.collection("users").where("societyId", "==", societyId).get();
  return snap.docs
    .filter((d) => !roles || roles.includes(d.data().role))
    .map((d) => d.data().fcmToken as string | undefined);
}

async function getTokensForFlat(
  societyId: string,
  flatNo: string
): Promise<(string | undefined)[]> {
  const snap = await db.collection("users")
    .where("societyId", "==", societyId)
    .where("flatNo", "==", flatNo)
    .get();
  return snap.docs.map((d) => d.data().fcmToken as string | undefined);
}

async function getTokenForUser(uid: string): Promise<string | undefined> {
  const snap = await db.collection("users").doc(uid).get();
  return snap.data()?.fcmToken;
}

export const onNoticeCreated = onDocumentCreated(
  "societies/{societyId}/notices/{noticeId}",
  async (event) => {
    const notice = event.data?.data();
    if (!notice) return;
    const societyId = event.params.societyId;
    const tokens = await getTokensForSociety(societyId);
    await sendToTokens(tokens, `📢 ${notice.title}`, notice.content, {
      channel: "notices",
      societyId,
    });
  }
);

export const onComplaintCreated = onDocumentCreated(
  "societies/{societyId}/complaints/{complaintId}",
  async (event) => {
    const complaint = event.data?.data();
    if (!complaint) return;
    const societyId = event.params.societyId;
    const tokens = await getTokensForSociety(societyId, ["ADMIN", "COMMITTEE"]);
    await sendToTokens(
      tokens,
      `New complaint: ${complaint.title}`,
      `${complaint.flatNo} · ${complaint.raisedBy}`,
      { channel: "complaints", societyId }
    );
  }
);

export const onComplaintUpdated = onDocumentUpdated(
  "societies/{societyId}/complaints/{complaintId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after || before.status === after.status) return;

    const token = await getTokenForUser(after.raisedByUid);
    if (!token) return;
    await sendToTokens([token], `Update on: ${after.title}`, `Status: ${after.status}`, {
      channel: "complaints",
      societyId: event.params.societyId,
    });
  }
);

export const onVisitorCreated = onDocumentCreated(
  "societies/{societyId}/visitors/{visitorId}",
  async (event) => {
    const visitor = event.data?.data();
    // Pre-approved QR invites are created by the resident themselves, ahead
    // of arrival — nothing to notify them about until the guest is scanned in.
    if (!visitor || visitor.preApproved) return;
    const societyId = event.params.societyId;
    const tokens = await getTokensForFlat(societyId, visitor.visitingFlat);
    await sendToTokens(
      tokens,
      "Visitor at the gate",
      `${visitor.visitorName} is here to see Flat ${visitor.visitingFlat}`,
      { channel: "visitors", societyId }
    );
  }
);

export const onVisitorUpdated = onDocumentUpdated(
  "societies/{societyId}/visitors/{visitorId}",
  async (event) => {
    const before = event.data?.before.data();
    const after = event.data?.after.data();
    if (!before || !after || before.status === after.status) return;
    // Only walk-in decisions need to reach the gate — pre-approved guests
    // are already APPROVED at creation, so there's no decision to relay.
    if (after.preApproved || after.status === "PENDING") return;

    const societyId = event.params.societyId;
    const tokens = await getTokensForSociety(societyId, ["ADMIN", "SECURITY"]);
    const decision = after.status === "APPROVED" ? "approved" : "denied";
    await sendToTokens(
      tokens,
      "Visitor decision",
      `Flat ${after.visitingFlat} ${decision} ${after.visitorName}`,
      { channel: "visitors", societyId }
    );
  }
);

export const onMaintenanceBillCreated = onDocumentCreated(
  "societies/{societyId}/maintenanceBills/{billId}",
  async (event) => {
    const bill = event.data?.data();
    if (!bill) return;
    const token = await getTokenForUser(bill.residentUid);
    if (!token) return;
    await sendToTokens([token], "New maintenance bill", `₹${bill.amount} due for ${bill.month}`, {
      channel: "maintenance",
      societyId: event.params.societyId,
    });
  }
);
