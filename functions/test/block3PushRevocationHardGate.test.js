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

test("completed sign-out observes push revocation failure but still signs out", () => {
  const auth = androidSource("data/repository/AuthRepository.kt");
  const signOut = auth.split("suspend fun signOut()")[1] || "";
  assert.match(signOut, /revokeCurrentDeviceBeforeSignOut\(\)/);
  assert.match(signOut, /exceptionOrNull\(\)/);
  assert.doesNotMatch(signOut, /getOrThrow\(\)/);
  const revokeAt = signOut.indexOf("revokeCurrentDeviceBeforeSignOut");
  const observeFailureAt = signOut.indexOf("exceptionOrNull()");
  const firebaseSignOutAt = signOut.indexOf("getFirebaseAuthSafe()?.signOut()");
  assert.ok(revokeAt >= 0 && observeFailureAt > revokeAt);
  assert.ok(firebaseSignOutAt > observeFailureAt);
});
