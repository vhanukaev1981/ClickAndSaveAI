"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

const pushSource = fs.readFileSync(
  path.join(__dirname, "..", "src", "pushFunctions.js"),
  "utf8"
);
const androidSource = fs.readFileSync(
  path.join(
    __dirname,
    "..",
    "..",
    "app",
    "src",
    "main",
    "java",
    "com",
    "example",
    "ClickAndSaveMessagingService.kt"
  ),
  "utf8"
);

const { getApps, initializeApp } = require("firebase-admin/app");
if (getApps().length === 0) initializeApp({ projectId: "clickandsaveai-test" });

const push = require("../src/pushFunctions");

test("normalizePlatform accepts android, ios, case-insensitive, and defaults safely", () => {
  const normalize = push._normalizePlatform;
  assert.equal(typeof normalize, "function");

  // Defaults when missing or empty
  assert.equal(normalize(undefined), "android");
  assert.equal(normalize(null), "android");
  assert.equal(normalize(""), "android");

  // Allowed platforms
  assert.equal(normalize("android"), "android");
  assert.equal(normalize("Android"), "android");
  assert.equal(normalize("ANDROID"), "android");
  assert.equal(normalize("  android  "), "android");

  assert.equal(normalize("ios"), "ios");
  assert.equal(normalize("iOS"), "ios");
  assert.equal(normalize("IOS"), "ios");
  assert.equal(normalize("  ios  "), "ios");

  // Unsupported platforms throw invalid-argument
  assert.throws(
    () => normalize("web"),
    (err) => err?.code === "invalid-argument" && err?.message?.includes("Unsupported platform")
  );
  assert.throws(
    () => normalize("windows"),
    (err) => err?.code === "invalid-argument"
  );
  assert.throws(
    () => normalize("macos"),
    (err) => err?.code === "invalid-argument"
  );
  assert.throws(
    () => normalize(123),
    (err) => err?.code === "invalid-argument"
  );
  assert.throws(
    () => normalize(true),
    (err) => err?.code === "invalid-argument"
  );
  assert.throws(
    () => normalize({}),
    (err) => err?.code === "invalid-argument"
  );
});

test("normalizeToken validates token length and structure", () => {
  const normalizeToken = push._normalizeToken;
  assert.equal(typeof normalizeToken, "function");

  // Valid tokens
  const validToken = "a".repeat(32);
  assert.equal(normalizeToken(validToken), validToken);
  assert.equal(normalizeToken(`  ${validToken}  `), validToken);

  // Short tokens
  assert.throws(
    () => normalizeToken("short"),
    (err) => err?.code === "invalid-argument"
  );

  // Empty or non-string
  assert.throws(
    () => normalizeToken(""),
    (err) => err?.code === "invalid-argument"
  );
  assert.throws(
    () => normalizeToken(null),
    (err) => err?.code === "invalid-argument"
  );

  // Tokens with internal whitespace
  assert.throws(
    () => normalizeToken("a".repeat(15) + " " + "b".repeat(15)),
    (err) => err?.code === "invalid-argument"
  );
});

test("registerPushToken enforces App Check, authentication, active account, and stores normalized platform", () => {
  const registerSection = pushSource
    .split("exports.registerPushToken = onCall(")[1]
    ?.split("exports.unregisterPushToken = onCall(")[0] || "";

  assert.match(registerSection, /enforceAppCheck:\s*true/);
  assert.match(registerSection, /const uid = requireAuth\(request\)/);
  assert.match(registerSection, /await assertActiveAccount\(uid\)/);
  assert.match(registerSection, /const token = normalizeToken\(request\.data\?\.token\)/);
  assert.match(registerSection, /const platform = normalizePlatform\(request\.data\?\.platform\)/);
  assert.match(registerSection, /platform,/);
});

test("sendPushToUser delivers multiplatform multicast payload with both android and apns blocks", () => {
  const sendSection = pushSource
    .split("async function sendPushToUser(")[1]
    ?.split("exports.registerPushToken = onCall(")[0] || "";

  // Android payload intact
  assert.match(sendSection, /android:\s*\{/);
  assert.match(sendSection, /priority:\s*"high"/);
  assert.match(sendSection, /channelId:\s*"savings_opportunities"/);

  // Apple APNs payload present
  assert.match(sendSection, /apns:\s*\{/);
  assert.match(sendSection, /payload:\s*\{/);
  assert.match(sendSection, /aps:\s*\{/);
  assert.match(sendSection, /sound:\s*"default"/);

  // General notification & data payload intact
  assert.match(sendSection, /notification:\s*\{\s*title,\s*body\s*\}/);
  assert.match(sendSection, /data:\s*Object\.fromEntries\(/);

  // Does not invent badge count
  assert.doesNotMatch(sendSection, /badge:/);
});

test("Android ClickAndSaveMessagingService registers push token with explicit platform parameter", () => {
  assert.match(
    androidSource,
    /getHttpsCallable\("registerPushToken"\)\s*\.call\(mapOf\("token" to token,\s*"platform" to "android"\)\)/
  );
});

test("pushFunctions exports required callable handlers and internal helpers", () => {
  assert.ok(push.registerPushToken, "registerPushToken callable must be exported");
  assert.ok(push.unregisterPushToken, "unregisterPushToken callable must be exported");
  assert.ok(push.sendTestPush, "sendTestPush callable must be exported");
  assert.ok(push._sendPushToUser, "_sendPushToUser helper must be exported");
  assert.ok(push._normalizePlatform, "_normalizePlatform helper must be exported");
  assert.ok(push._normalizeToken, "_normalizeToken helper must be exported");
});
