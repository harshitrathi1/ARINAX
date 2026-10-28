# ArenaX — Social Esports Network for Mobile Gamers (BGMI & Free Fire)

## 1. Architecture Overview & Folder Structure

ArenaX is built with a clean, modular MVVM + Repository architecture backed by **Firebase Authentication (Google Sign-In via Jetpack Credential Manager)**, **Cloud Firestore (Enterprise Edition)**, **Firebase App Check**, and **Firebase AI Logic (`gemini-3.8-flash` Vision)**:

```text
/
├── firebase-blueprint.json          # Firestore schema IR (Entities & Collection paths)
├── firestore.rules                  # Zero-trust ABAC Firestore security rules
├── firestore.test.js                # Tier 1 Node.js @firebase/rules-unit-testing suite
├── firebase.json                    # Local emulator configuration bound to named DB
├── security_spec.md                 # Data invariants & Dirty Dozen adversarial payloads
└── app/src/
    ├── main/java/com/example/
    │   ├── MainActivity.kt          # AppCheck init, Auth gate, 5-tab BottomBar & FAB
    │   ├── core/
    │   │   ├── AppCheckConfig.kt    # Synchronous Debug & Release reCAPTCHA App Check setup
    │   │   └── FirestoreErrorHandler.kt # Structured JSON error logging, ProfanityFilter, YouTubeParser
    │   ├── data/
    │   │   ├── model/ArenaModels.kt # @DocumentId & @ServerTimestamp Firestore models
    │   │   └── repository/ArenaRepository.kt # Real-time snapshots() Flows, Elo engine, Anti-fraud
    │   └── ui/
    │       ├── auth/AuthScreen.kt   # CredentialManager Google Sign-In UI
    │       ├── components/ArenaComponents.kt # Challenge sheet, Score modal, Post creator, Badges
    │       ├── screens/
    │       │   ├── FeedAndExploreScreens.kt  # Home Feed (Photo, YouTube IFrame, Result cards) & Explore
    │       │   ├── ArenaAndChatScreens.kt    # Active Challenges, Leaderboards, Missions & Squad Chat
    │       │   └── ProfileAndAdminScreens.kt # Gemini Vision Verification, Profile, Notifications & Admin
    │       ├── theme/               # Dark Cyberpunk Neon Purple/Cyan theme & Rajdhani/Space Grotesk fonts
    │       └── viewmodel/ArenaViewModel.kt   # StateFlows + Gemini 3.8 Flash Vision OCR
    └── test/java/com/example/
        ├── base/FirestoreEmulatorTestBase.kt
        └── data/repository/ArenaRepositoryRuleTest.kt # Tier 2 Robolectric JVM integration tests
```

## 2. Cloud Functions Reference (For Production Blaze Deployment)

In the AI Studio Android runtime, **Firebase AI Logic (`Firebase.ai`)** with **Firebase App Check** securely calls `gemini-3.8-flash` without exposing any API key in client code, and **Firestore Security Rules** enforce strict state transitions and terminal state locking (`!(existing().status in ['completed', 'declined', 'expired', 'cancelled'])`).

When deploying accompanying scheduled background jobs on the Firebase **Blaze Plan**, deploy the following `functions/index.js` for 24-hour challenge expiry and FCM push fan-out:

```javascript
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentWritten } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore("ai-studio-android-arenax-28ded8e4-06f8-41ad-8ce7-6c521b63e0ac");

// 1. Auto-expire unaccepted challenges after 24 hours
exports.expireStaleChallenges = onSchedule("every 60 minutes", async () => {
  const cutoff = new Date(Date.now() - 24 * 60 * 60 * 1000);
  const snap = await db.collection("challenges")
    .where("status", "in", ["sent", "countered"])
    .where("createdAt", "<=", cutoff)
    .get();

  const batch = db.batch();
  snap.docs.forEach((doc) => {
    batch.update(doc.ref, {
      status: "expired",
      statusHistory: FieldValue.arrayUnion("expired automatically after 24h"),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  await batch.commit();
});

// 2. Push Notification trigger on new ArenaNotification documents
exports.sendChallengePushNotification = onDocumentWritten(
  {
    document: "notifications/{notificationId}",
    database: "ai-studio-android-arenax-28ded8e4-06f8-41ad-8ce7-6c521b63e0ac",
  },
  async (event) => {
    const data = event.data?.after?.data();
    if (!data) return;
    const tokenSnap = await db.collection("fcmTokens").doc(data.recipientId).get();
    const fcmToken = tokenSnap.data()?.token;
    if (!fcmToken) return;
    await getMessaging().send({
      token: fcmToken,
      notification: { title: data.title, body: data.body },
      data: { type: data.type, referenceId: data.referenceId || "" },
    });
  }
);
```

## 3. Setup, Local Rules Verification & Deployment

1. **Firebase Provisioning**: Already provisioned in AI Studio with Google Sign-In (`CredentialManager`), Firestore Enterprise database (`ai-studio-android-arenax-28ded8e4-06f8-41ad-8ce7-6c521b63e0ac`), and Firebase AI Logic + App Check.
2. **Run Local Security Rules Tests**:
   - Tier 1 (JavaScript): `FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js`
   - Tier 2 (Robolectric Kotlin): `gradle :app:testDebugUnitTest --tests '*Rule*'`
3. **Optional Manual Console Extensions**: For Phone OTP, Firebase Storage raw buckets, or FCM background device tokens, enable those providers in the Firebase Console (requires Blaze plan for Cloud Functions).

## 4. Known Limitations & Next Steps (Phase 3 & 4)
- **Current Scope**: Phase 1 & Phase 2 features (Google Auth, Onboarding with Gemini Vision screenshot verification & anti-fraud code check, Profiles, Follow/Unfollow, Real-time 1:1 & Squad Chat with Sword Challenge button, Full Challenge State Machine & Elo Leaderboard, Photo & YouTube Feed, Explore filters, Notifications, and Admin Moderation Queue) are complete.
- **Next Steps**: Reply **"continue"** to build Phase 3 (Squad Finder, Bracket Tournaments, Sensitivity/Settings Vault, and 24h Stories) and Phase 4 (AI Coach & Arena Coins Store).
