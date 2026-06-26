# Firestore Data Model (Phase 1)

Multi-tenant: every shared document lives under `/societies/{societyId}`, so
isolation is structural, not just rule-based. A user document points at
exactly one society (Phase 1 assumption — see "Future" below).

## Collections

### `/users/{uid}`
Doc ID = Firebase Auth UID. One user = one society membership for Phase 1.

| field | type | notes |
|---|---|---|
| name | string | |
| phone | string | E.164, also the Auth phone identity |
| email | string? | present if signed in with Google |
| role | string | `ADMIN`\|`TREASURER`\|`COMMITTEE`\|`RESIDENT`\|`SECURITY` |
| societyId | string | FK to `/societies/{societyId}` |
| flatNo | string | |
| flatType | string | `1BHK`\|`2BHK`\|`3BHK`\|`SHOP` |
| fcmToken | string? | latest FCM registration token, written by client, read by Cloud Functions |
| createdAt | Timestamp | |

### `/societies/{societyId}`
Doc ID auto-generated (not the society name — avoids name-collision issues
the old Room schema had with `name` as primary key).

| field | type | notes |
|---|---|---|
| name | string | |
| secretaryUid | string | the ADMIN who created it / owns billing |
| inviteCode | string | current redeemable code |
| inviteRole | string | role granted to whoever redeems the current code |
| subscriptionActive | boolean | |
| subscriptionPlan | string | |
| subscriptionStartedAt / subscriptionExpiresAt | Timestamp? | |
| upiId | string? | secretary-set UPI VPA, used to build manual payment deep links |
| createdAt | Timestamp | |

### `/societies/{societyId}/notices/{noticeId}`
`title, content, postedByUid, postedByName, isPinned, createdAt`

### `/societies/{societyId}/complaints/{complaintId}`
`title, description, category, flatNo, raisedByUid, raisedByName, status (OPEN|IN_PROGRESS|RESOLVED), adminComment, createdAt, updatedAt`

### `/societies/{societyId}/maintenanceBills/{billId}`
`flatNo, residentUid, residentName, amount, month, dueDate, isPaid, paidOn, utrReference?, createdAt`

### `/societies/{societyId}/visitors/{visitorId}`
`visitorName, visitingFlat, purpose, vehicleNo, loggedByUid, loggedByName, checkIn, checkOut, status (PENDING_APPROVAL|APPROVED|DENIED|CHECKED_OUT), qrToken`

### `/societies/{societyId}/emergencyContacts/{contactId}`
`name, phone, type, isDefault`

### `/societies/{societyId}/recurringConfig/default`
Single doc (doc ID literally `default`). `isEnabled, mode, sameAmount, amount1BHK, amount2BHK, amount3BHK, amountShop, dueDay, lastGeneratedMonth`

## Why subcollections under `/societies/{id}` instead of top-level collections with a `societyId` field

Structural isolation: a security rule that says "you may only read/write
under a societyId that matches your own user doc" is one rule applied once
via `match /societies/{societyId}/{document=**}`, rather than a per-field
check repeated on every top-level collection. It also means composite
indexes are automatically scoped per-society (Firestore indexes subcollections
independently per parent), which keeps index cardinality manageable as the
number of societies grows into the thousands.

## Signup is server-side, not direct client writes

`createSociety` and `redeemInviteCode` are Cloud Functions (callable), not
direct Firestore writes from the app. Reasons:
- Invite code lookup needs a query across all societies before the caller
  has a `societyId` — that can't be exposed as an open Firestore query
  without letting anyone list all societies.
- Uniqueness/validation (e.g. one secretary per society, code collisions)
  needs a transaction that's safer to run with admin privileges server-side.

See `/functions/src/auth.ts`.

## Future (not Phase 1)

- Multi-society membership per user (owns flats in 2 societies) → would
  need `societyMemberships: [...]` array on `/users/{uid}` instead of a
  single `societyId`. Deferred until there's a real customer who needs it.
- Phase 2+ adds sibling subcollections (`ledger`, `agms`, `events`,
  `bookings`, `assets`, `vendors`, `staff`, `inventory`, `documents`,
  `polls`, `auditLogs`) under the same `/societies/{id}` parent — additive,
  no rework of this schema needed.
