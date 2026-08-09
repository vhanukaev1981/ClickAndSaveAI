"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");

test("financial FCM notifications are explicitly private on Android", () => {
  const source = fs.readFileSync(path.join(__dirname, "../src/pushFunctions.js"), "utf8");
  assert.match(source, /visibility:\s*"private"/);
  assert.match(source, /channelId:\s*"savings_opportunities"/);
});
