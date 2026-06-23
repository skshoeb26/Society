# 🏠 Society Connect — Native Android App

A complete housing society management app built for Mumbai/India.
Replaces WhatsApp chaos with a clean, organized platform.

---

## 📦 Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | XML Layouts + Material Design 3 |
| Architecture | MVVM + Repository Pattern |
| Database | Room (SQLite) |
| Navigation | Jetpack Navigation Component |
| Notifications | AlarmManager + NotificationManager |
| Auth | SharedPreferences (local, no server needed v1) |
| Build | Gradle (Kotlin DSL) |

---

## 📁 Project Structure

```
SocietyConnect/
├── app/
│   └── src/main/
│       ├── java/com/societyconnect/
│       │   ├── ui/
│       │   │   ├── auth/           # Login, Register, Role Selection
│       │   │   ├── dashboard/      # Home Dashboard
│       │   │   ├── maintenance/    # Due tracker & payments
│       │   │   ├── complaints/     # Raise & track complaints
│       │   │   ├── notices/        # Notice board
│       │   │   ├── visitors/       # Gate entry log
│       │   │   └── emergency/      # Emergency contacts
│       │   ├── data/
│       │   │   ├── models/         # Room entities
│       │   │   └── repository/     # Data layer
│       │   └── utils/              # Helpers, extensions
│       └── res/
│           ├── layout/             # XML screen layouts
│           ├── drawable/           # Icons, backgrounds
│           ├── values/             # Colors, strings, themes
│           ├── menu/               # Bottom nav menu
│           └── navigation/         # Nav graph
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 Features

### 👤 Roles
- **Admin / Secretary** — Full access
- **Resident** — Personal dues, complaints, notices
- **Security Guard** — Visitor log only

### 💰 Maintenance
- Track monthly dues per flat
- Mark as paid / pending
- Send reminders
- Payment history

### 🔧 Complaints
- Raise complaint with category (water, lift, electricity, cleaning)
- Status tracking: Open → In Progress → Resolved
- Admin can assign and update

### 📋 Notices
- Post society notices
- Pin important notices
- All residents see them instantly

### 🚪 Visitor Log
- Security logs visitor name, flat no., time in/out
- Residents get notification when visitor arrives
- Full visitor history

### 🆘 Emergency Contacts
- Plumber, Electrician, Watchman
- Fire, Police, Ambulance
- One-tap call

---

## ⚙️ Setup Instructions

### Step 1 — Install Android Studio
Download from: https://developer.android.com/studio
- Minimum: Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 included

### Step 2 — Clone / Open Project
```bash
# Option A: Copy this folder
# Option B: Open Android Studio → Open → Select SocietyConnect folder
```

### Step 3 — Sync Gradle
- Android Studio will auto-sync
- If not: File → Sync Project with Gradle Files

### Step 4 — Run App
- Connect Android phone (Enable USB Debugging)
- Or use AVD Emulator (API 26+)
- Click ▶ Run

### Step 5 — Build APK
```
Build → Build Bundle(s) / APK(s) → Build APK(s)
```
APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Step 6 — Play Store Release
```
Build → Generate Signed Bundle / APK
→ Android App Bundle (.aab)
→ Create Keystore → Fill details → Build
```

---

## 📲 Play Store Checklist

- [ ] App name: "Society Connect - Housing Manager"
- [ ] Package: com.societyconnect (change to your domain)
- [ ] Min SDK: API 26 (Android 8.0) — covers 95%+ devices
- [ ] Target SDK: API 34 (Android 14)
- [ ] Screenshots: 2–8 phone screenshots required
- [ ] Icon: 512x512 PNG
- [ ] Short description (80 chars)
- [ ] Full description (4000 chars)
- [ ] Privacy Policy URL (required)
- [ ] One-time fee: $25 USD Google Play Developer account

---

## 💰 Monetization Ideas

| Model | How |
|---|---|
| Freemium | Free for <50 flats, paid for larger societies |
| Monthly SaaS | ₹499–₹1999/month per society |
| Setup Fee | ₹2000 one-time onboarding |
| WhatsApp alerts | Premium feature add-on |
