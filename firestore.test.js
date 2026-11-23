const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_builder_1";
const BOB_UID = "bob_builder_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, timestamp = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    displayName: "Master Builder",
    totalBlocksMined: 0,
    totalBlocksPlaced: 0,
    totalItemsCrafted: 0,
    builderLevel: 1,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
}

function validWorldPayload(uid, timestamp = new Date(Date.now() - 5000)) {
  return {
    userId: uid,
    name: "Emerald Keep",
    biome: "EMERALD_VALLEY",
    gameMode: "SURVIVAL",
    seed: 424242,
    timeOfDay: 0.25,
    pickaxeTier: 1,
    blocksMined: 0,
    blocksPlaced: 0,
    playerX: 8.5,
    playerY: 14.0,
    playerZ: 8.5,
    blockDeltas: { "10,5,10": 6 },
    inventory: { PLANKS: 16, TORCH: 8, COBBLESTONE: 12 },
    hotbar: ["GRASS", "DIRT", "STONE", "COBBLESTONE", "WOOD_LOG", "PLANKS", "SAND", "BRICK", "GLASS"],
    createdAt: timestamp,
    updatedAt: timestamp,
  };
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

test("Unauthenticated users cannot read or write profiles or worlds", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("worlds").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("worlds")
      .doc("world_1")
      .set(validWorldPayload(ALICE_UID))
  );
});

test("Authenticated owner can create, read, and update own BuilderProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(validProfilePayload(ALICE_UID)));
  await assertSucceeds(profileRef.get());

  // Partial update: displayName
  await assertSucceeds(
    profileRef.update({
      displayName: "Voxel Architect",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Partial update: progression stats
  await assertSucceeds(
    profileRef.update({
      totalBlocksMined: 42,
      totalBlocksPlaced: 80,
      totalItemsCrafted: 12,
      builderLevel: 3,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field / shadow update
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject listing /users root collection
  await assertFails(aliceDb.collection("users").get());
});

test("Authenticated owner can create, list, save gameplay state, rename, and delete own worlds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const worldsCol = aliceDb.collection("users").doc(ALICE_UID).collection("worlds");

  // Listing empty subcollection succeeds (mirrors Kotlin observeWorlds)
  await assertSucceeds(worldsCol.get());

  // Create world (no @DocumentId `id` field)
  await assertSucceeds(worldsCol.doc("world_1").set(validWorldPayload(ALICE_UID)));
  await assertSucceeds(worldsCol.doc("world_1").get());
  await assertSucceeds(worldsCol.get());

  // Partial update: save gameplay state (matches WorldRepository.saveWorldState)
  await assertSucceeds(
    worldsCol.doc("world_1").update({
      gameMode: "CREATIVE",
      timeOfDay: 0.72,
      pickaxeTier: 2,
      blocksMined: 15,
      blocksPlaced: 28,
      playerX: 12.25,
      playerY: 16.5,
      playerZ: -4.75,
      blockDeltas: { "10,5,10": 6, "10,6,10": 15, "9,4,9": 0 },
      inventory: { PLANKS: 24, TORCH: 12, IRON_ORE: 4 },
      hotbar: ["PLANKS", "BRICK", "GLASS", "TORCH", "DIAMOND_ORE", "OBSIDIAN", "GRASS", "STONE", "WOOD_LOG"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Partial update: rename world (matches WorldRepository.renameWorld)
  await assertSucceeds(
    worldsCol.doc("world_1").update({
      name: "Sky Citadel",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Delete world
  await assertSucceeds(worldsCol.doc("world_1").delete());
});

test("Reject invalid world payloads, @DocumentId id injection, immutable seed/biome mutation, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const worldsCol = aliceDb.collection("users").doc(ALICE_UID).collection("worlds");

  // Reject `@DocumentId` `id` field inside body
  await assertFails(
    worldsCol.doc("world_bad_id").set({
      ...validWorldPayload(ALICE_UID),
      id: "world_bad_id",
    })
  );

  // Reject invalid biome enum
  await assertFails(
    worldsCol.doc("world_bad_biome").set({
      ...validWorldPayload(ALICE_UID),
      biome: "NETHER_DIMENSION",
    })
  );

  // Reject future timestamp
  await assertFails(
    worldsCol.doc("world_future").set(
      validWorldPayload(ALICE_UID, new Date(Date.now() + 60000))
    )
  );

  // Reject invalid pickaxe tier out of bounds
  await assertFails(
    worldsCol.doc("world_bad_tier").set({
      ...validWorldPayload(ALICE_UID),
      pickaxeTier: 99,
    })
  );

  // Create valid world then try mutating immutable seed
  await assertSucceeds(worldsCol.doc("world_ok").set(validWorldPayload(ALICE_UID)));
  await assertFails(
    worldsCol.doc("world_ok").update({
      seed: 999999,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation prevents Bob from reading, listing, or modifying Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("worlds")
      .doc("alice_world")
      .set(validWorldPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("worlds").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("worlds")
      .doc("alice_world")
      .get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("worlds")
      .doc("alice_world")
      .delete()
  );
});

// --- APP AUTO-UPDATE CONFIG (/app_config/update) TESTS ---

const ADMIN_EMAIL = "ddatabase61@gmail.com";

function validUpdateConfigPayload() {
  return {
    latestVersionCode: 2,
    latestVersionName: "1.1.0",
    apkUrl: "https://github.com/blockforge/releases/releases/download/v1.1.0/BlockForge-v1.1.0.apk",
    forceUpdate: false,
    releaseNotes: "नए रियल एस्टेट मैप, बेहतर कैमरा एंगल और स्मूथ गेमप्ले!",
  };
}

test("app_config/update: public read allowed for unauthenticated & regular users; writes denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("app_config").doc("update").set(validUpdateConfigPayload());
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  // Public get on /app_config/update succeeds for both guests and signed-in players
  await assertSucceeds(unauthDb.collection("app_config").doc("update").get());
  await assertSucceeds(aliceDb.collection("app_config").doc("update").get());

  // Listing /app_config or reading other docs is denied for non-admins
  await assertFails(unauthDb.collection("app_config").get());
  await assertFails(aliceDb.collection("app_config").get());
  await assertFails(unauthDb.collection("app_config").doc("secrets").get());

  // Writes and deletes denied for unauthenticated and regular users
  await assertFails(unauthDb.collection("app_config").doc("update").set(validUpdateConfigPayload()));
  await assertFails(aliceDb.collection("app_config").doc("update").set(validUpdateConfigPayload()));
  await assertFails(
    aliceDb.collection("app_config").doc("update").update({ latestVersionCode: 99 })
  );
  await assertFails(aliceDb.collection("app_config").doc("update").delete());
});

test("app_config/update: unverified spoofed admin email is rejected", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofedAdminDb.collection("app_config").doc("update").set(validUpdateConfigPayload())
  );
});

test("app_config/update: verified admin can create/update/delete valid config; invalid schema rejected", async () => {
  const adminDb = testEnv.authenticatedContext("admin_uid", {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const docRef = adminDb.collection("app_config").doc("update");

  // Valid create succeeds
  await assertSucceeds(docRef.set(validUpdateConfigPayload()));
  await assertSucceeds(docRef.get());

  // Valid partial update succeeds (e.g. pushing v1.2.0 force update)
  await assertSucceeds(
    docRef.update({
      latestVersionCode: 3,
      latestVersionName: "1.2.0",
      forceUpdate: true,
      apkUrl: "https://github.com/blockforge/releases/releases/download/v1.2.0/BlockForge-v1.2.0.apk",
    })
  );

  // Reject non-HTTPS apkUrl (security guard)
  await assertFails(
    docRef.update({
      apkUrl: "http://insecure.example.com/app.apk",
    })
  );

  // Reject ghost field injection
  await assertFails(
    docRef.update({
      latestVersionCode: 4,
      maliciousFlag: true,
    })
  );

  // Reject invalid versionCode (<= 0)
  await assertFails(
    docRef.update({
      latestVersionCode: 0,
    })
  );

  // Admin delete succeeds
  await assertSucceeds(docRef.delete());
});

