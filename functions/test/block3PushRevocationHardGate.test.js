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

test("completed sign-out is fail-closed on push revocation failure", () => {
  const auth = androidSource("data/repository/AuthRepository.kt");
  const signOut = auth.split("suspend fun signOut()")[1] || "";
  assert.match(signOut, /revokeCurrentDeviceBeforeSignOut\(\)/);
  assert.match(
    signOut,
    /getOrThrow\(\)|throwOnFailure|isFailure|exceptionOrNull/,
    "AuthRepository must consume the revocation result before Firebase Auth sign-out"
  );
  const revokeAt = signOut.indexOf("revokeCurrentDeviceBeforeSignOut")
  const firebaseSignOutAt = signOut.indexOf("signOut()")
  assert.ok(revokeAt >= 0 && firebaseSignOutAt > revokeAt)
});
