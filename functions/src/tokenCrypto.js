"use strict";

const crypto = require("node:crypto");

function decodeKey(encodedKey) {
  if (typeof encodedKey !== "string" || !encodedKey.trim()) {
    throw new Error("OAUTH_TOKEN_ENCRYPTION_KEY is not configured");
  }
  const key = Buffer.from(encodedKey, "base64");
  if (key.length !== 32) {
    throw new Error("OAUTH_TOKEN_ENCRYPTION_KEY must be a base64-encoded 32-byte key");
  }
  return key;
}

function encryptToken(plainText, encodedKey) {
  if (typeof plainText !== "string" || !plainText) {
    throw new Error("Cannot encrypt an empty token");
  }
  const key = decodeKey(encodedKey);
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", key, iv);
  const ciphertext = Buffer.concat([cipher.update(plainText, "utf8"), cipher.final()]);
  const authTag = cipher.getAuthTag();

  return {
    version: 1,
    algorithm: "AES-256-GCM",
    iv: iv.toString("base64"),
    ciphertext: ciphertext.toString("base64"),
    authTag: authTag.toString("base64"),
  };
}

function decryptToken(payload, encodedKey) {
  if (!payload || payload.version !== 1 || payload.algorithm !== "AES-256-GCM") {
    throw new Error("Unsupported encrypted token format");
  }
  const key = decodeKey(encodedKey);
  const decipher = crypto.createDecipheriv(
    "aes-256-gcm",
    key,
    Buffer.from(payload.iv, "base64")
  );
  decipher.setAuthTag(Buffer.from(payload.authTag, "base64"));
  const plainText = Buffer.concat([
    decipher.update(Buffer.from(payload.ciphertext, "base64")),
    decipher.final(),
  ]);
  return plainText.toString("utf8");
}

module.exports = { encryptToken, decryptToken };
