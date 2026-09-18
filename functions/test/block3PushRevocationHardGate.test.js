"use strict";

const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const assert = require("node:assert/strict");

function androidSource(relativePath) {
  return fs.readFileSync(
    path.join(__dirname, "..", "..", "app", "src", "main", "java", "com", "example", relativePath),
    "utf8"
  );
}

test("completed sign-out blocks only when both revocation paths fail", () => {
  const auth = androidSource("data/repository/AuthRepository.kt");
  const signOut = auth.split("suspend fun signOut()")[1] || "";
  assert.match(signOut, /revokeCurrentDeviceBeforeSignOut\(\)/);
  assert.match(signOut, /getOrThrow\(\)/);
  const revokeAt = signOut.indexOf("revokeCurrentDeviceBeforeSignOut");
  const hardGateAt = signOut.indexOf("getOrThrow()");
  const firebaseSignOutAt = signOut.indexOf("getFirebaseAuthSafe()?.signOut()");
  assert.ok(revokeAt >= 0 && hardGateAt > revokeAt);
  assert.ok(firebaseSignOutAt > hardGateAt);

  const lifecycle = androidSource("PushTokenLifecycle.kt");
  assert.match(lifecycle, /backendRevoked \|\| localDeleted/);
  assert.match(lifecycle, /Result\.failure\(failure\)/);
});
