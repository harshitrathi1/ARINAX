const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_1";
const BOB_UID = "bob_uid_2";
const MALLORY_UID = "mallory_uid_3";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildValidUserProfile(uid, username, visibility = "public", ts = new Date()) {
  return {
    userId: uid,
    username,
    displayName: username,
    bio: "Conqueror BGMI IGL",
    avatarUrl: "",
    visibility,
    primaryGame: "BGMI",
    primaryIgn: "AX_Slayer",
    primaryGameUid: "5123456789",
    primaryRank: "Conqueror",
    primaryLevel: 72,
    isVerified: true,
    verificationStatus: "VERIFIED",
    verificationCode: "AX-4821",
    eloRating: 1200,
    arenaPoints: 500,
    wins: 5,
    losses: 1,
    winStreak: 3,
    followersCount: 10,
    followingCount: 4,
    badges: ["Verified Player"],
    language: "English",
    playTime: "20:00 - 23:00 IST",
    createdAt: ts,
    updatedAt: ts,
  };
}

// 1. Unauthenticated access rejected
test("Unauthenticated user cannot read users, challenges, or chats", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("challenges").get());
  await assertFails(unauthDb.collection("chats").get());
});

// 2. Identity spoofing rejected
test("Authenticated user cannot create profile for another UID", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set(buildValidUserProfile(BOB_UID, "bob_gamer"))
  );
});

// 3. Valid profile creation and Shadow Update (Ghost Field) rejection
test("Owner can create valid profile, but ghost field injection fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const validProfile = buildValidUserProfile(ALICE_UID, "alice_pro");
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile));

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Updated bio",
      isSuperAdmin: true,
      updatedAt: new Date(),
    })
  );
});

// 4. Private profile isolation & Query alignment
test("Private profile cannot be read by non-owner, and unfiltered users list fails", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).set(buildValidUserProfile(ALICE_UID, "alice_priv", "private"));
    await db.collection("users").doc(BOB_UID).set(buildValidUserProfile(BOB_UID, "bob_pub", "public"));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  // Unfiltered list fails because rules are not filters
  await assertFails(bobDb.collection("users").get());
  // Aligned public query succeeds
  await assertSucceeds(bobDb.collection("users").where("visibility", "==", "public").get());
});

// 5. Challenge creation, outsider rejection, and terminal state lock
test("Challenge lifecycle: participants can update active challenge, outsiders and completed mutations fail", async () => {
  const now = new Date();
  const challengeData = {
    challengerId: ALICE_UID,
    challengerUsername: "alice_pro",
    challengerIgn: "AliceIGL",
    challengerRank: "Conqueror",
    opponentId: BOB_UID,
    opponentUsername: "bob_pub",
    opponentIgn: "BobSniper",
    opponentRank: "Ace Dominator",
    participantUids: [ALICE_UID, BOB_UID],
    game: "BGMI",
    mode: "1v1",
    mapName: "Warehouse TDM",
    weaponsRule: "M416 Only",
    rounds: 1,
    customNote: "No grenades",
    scheduledTimeLabel: "Tonight 9:00 PM",
    stakePoints: 100,
    status: "sent",
    challengerSubmittedScore: "",
    challengerProofNote: "",
    opponentSubmittedScore: "",
    opponentProofNote: "",
    winnerId: "",
    winnerUsername: "",
    finalScoreLabel: "",
    statusHistory: ["sent by alice_pro"],
    visibility: "public",
    createdAt: now,
    updatedAt: now,
  };

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection("challenges").doc("chal_1").set(challengeData));

  // Outsider Mallory cannot accept challenge
  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  await assertFails(
    malloryDb.collection("challenges").doc("chal_1").update({
      status: "accepted",
      updatedAt: new Date(),
    })
  );

  // Opponent Bob can accept challenge
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("challenges").doc("chal_1").update({
      status: "completed",
      winnerId: BOB_UID,
      winnerUsername: "bob_pub",
      finalScoreLabel: "2 - 1",
      updatedAt: new Date(),
    })
  );

  // Terminal state lock: once completed, Bob cannot mutate it again
  await assertFails(
    bobDb.collection("challenges").doc("chal_1").update({
      finalScoreLabel: "10 - 0",
      updatedAt: new Date(),
    })
  );
});

// 6. Chat membership isolation
test("Chat thread and messages are isolated strictly to memberUids", async () => {
  const now = new Date();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_ab").set({
      title: "Alice vs Bob",
      isSquad: false,
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: ["alice_pro", "bob_pub"],
      lastMessageText: "Ready for 1v1?",
      lastMessageSenderId: ALICE_UID,
      typingUserId: "",
      activeChallengeId: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  const malloryDb = testEnv.authenticatedContext(MALLORY_UID).firestore();
  await assertFails(malloryDb.collection("chats").doc("chat_ab").get());
  await assertFails(malloryDb.collection("chats").get());
  await assertFails(
    malloryDb.collection("chats").where("memberUids", "array-contains", ALICE_UID).get()
  );
});

// 7. Admin Email Spoofing test
test("Unverified admin email cannot update moderation reports", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("reports").doc("rep_1").set({
      reporterId: ALICE_UID,
      reporterUsername: "alice_pro",
      targetType: "USER",
      targetId: BOB_UID,
      reason: "Wallhack suspicion",
      status: "OPEN",
      createdAt: now,
      updatedAt: now,
    });
  });

  const spoofedAdminDb = testEnv
    .authenticatedContext("spoof_uid", {
      email: "indiafastgamers@gmail.com",
      email_verified: false,
    })
    .firestore();

  await assertFails(
    spoofedAdminDb.collection("reports").doc("rep_1").update({
      status: "RESOLVED",
      updatedAt: new Date(),
    })
  );

  const realAdminDb = testEnv
    .authenticatedContext("real_admin_uid", {
      email: "indiafastgamers@gmail.com",
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    realAdminDb.collection("reports").doc("rep_1").update({
      status: "RESOLVED",
      updatedAt: new Date(),
    })
  );
});
