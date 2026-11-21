const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PARENT_UID = "parent_uid_123";
const CHILD_UID = "child_uid_456";
const STRANGER_UID = "stranger_uid_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

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

test("Unauthenticated user cannot read or list users, screen_streams, or app_usage_stats", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(PARENT_UID).get());
  await assertFails(unauthDb.collection("screen_streams").get());
  await assertFails(unauthDb.collection("app_usage_stats").get());
});

test("User profile strictly isolates reads/writes to owner and blocks ghost fields", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    parentDb.collection("users").doc(PARENT_UID).set({
      userId: PARENT_UID,
      displayName: "Parent User",
      role: "parent",
      familyId: "fam-1",
      supabaseUserId: "supa-1",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Stranger cannot read parent profile
  await assertFails(strangerDb.collection("users").doc(PARENT_UID).get());

  // Ghost field must be rejected
  await assertFails(
    parentDb.collection("users").doc(PARENT_UID).update({
      displayName: "Hacker",
      isVerified: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Screen stream allows paired family access, supports live frame streaming, and blocks strangers", async () => {
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    childDb.collection("screen_streams").doc("child_1").set({
      childId: "child_1",
      ownerUid: CHILD_UID,
      memberUids: [CHILD_UID, PARENT_UID],
      childName: "Aarav",
      foregroundApp: "YouTube",
      frameBase64: "ZmFrZWpwZWc=",
      width: 360,
      height: 720,
      isStreaming: true,
      watchRequested: true,
      requestedByUid: PARENT_UID,
      fps: 1.0,
      capturedAtMs: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Parent can query with memberUids filter
  await assertSucceeds(
    parentDb.collection("screen_streams")
      .where("childId", "==", "child_1")
      .where("memberUids", "array-contains", PARENT_UID)
      .get()
  );

  // Parent can toggle watchRequested
  await assertSucceeds(
    parentDb.collection("screen_streams").doc("child_1").update({
      watchRequested: false,
      requestedByUid: PARENT_UID,
      updatedAt: pastTimestamp(),
    })
  );

  // Unfiltered list query must fail
  await assertFails(parentDb.collection("screen_streams").get());

  // Stranger cannot read or update
  await assertFails(strangerDb.collection("screen_streams").doc("child_1").get());
  await assertFails(
    strangerDb.collection("screen_streams").doc("child_1").update({
      watchRequested: true,
      updatedAt: pastTimestamp(),
    })
  );
});

test("App usage stats enforce member authorization and numeric bounds", async () => {
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    childDb.collection("app_usage_stats").doc("stat_1").set({
      subjectUserId: "child_1",
      ownerUid: CHILD_UID,
      memberUids: [CHILD_UID, PARENT_UID],
      role: "child",
      dateKey: "2026-11-13",
      packageName: "com.google.android.youtube",
      appName: "YouTube",
      category: "Entertainment",
      totalTimeMinutes: 45,
      foregroundTimeMs: 2700000,
      launchCount: 6,
      isBlocked: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Parent can list with memberUids filter
  await assertSucceeds(
    parentDb.collection("app_usage_stats")
      .where("subjectUserId", "==", "child_1")
      .where("memberUids", "array-contains", PARENT_UID)
      .get()
  );

  // Stranger denied
  await assertFails(strangerDb.collection("app_usage_stats").doc("stat_1").get());
  await assertFails(
    strangerDb.collection("app_usage_stats")
      .where("memberUids", "array-contains", CHILD_UID)
      .get()
  );

  // Out-of-bounds minutes (> 1440) denied
  await assertFails(
    childDb.collection("app_usage_stats").doc("stat_bad").set({
      subjectUserId: "child_1",
      ownerUid: CHILD_UID,
      memberUids: [CHILD_UID],
      role: "child",
      dateKey: "2026-11-13",
      packageName: "com.example.bad",
      appName: "Bad",
      category: "Gaming",
      totalTimeMinutes: 9999,
      foregroundTimeMs: 500000,
      launchCount: 1,
      isBlocked: false,
      createdAt: pastTimestamp(),
      updatedAt: pastTimestamp(),
    })
  );
});
