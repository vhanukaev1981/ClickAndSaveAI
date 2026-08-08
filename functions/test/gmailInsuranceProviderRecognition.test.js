"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { parseGmailMessage } = require("../src/gmailParser");

function b64url(value) {
  return Buffer.from(value, "utf8")
    .toString("base64")
    .replace(/=/g, "")
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
}

function message({ id, from, subject = "החשבון החודשי שלך", body = "Amount due 602.00" }) {
  return {
    id,
    snippet: "",
    payload: {
      mimeType: "multipart/alternative",
      headers: [
        { name: "Subject", value: subject },
        { name: "From", value: from },
        { name: "Date", value: "Fri, 8 Aug 2026 08:00:00 +0300" },
      ],
      parts: [{ mimeType: "text/plain", body: { data: b64url(body) } }],
    },
  };
}

test("strong insurance sender signals identify provider and insurance category", () => {
  const cases = [
    ["Harel <billing@harel.example>", "הראל"],
    ["The Phoenix <billing@fnx.example>", "הפניקס"],
    ["Migdal <billing@migdal.example>", "מגדל"],
    ["Clal Insurance <billing@clal.example>", "כלל"],
    ["Menora <billing@menora.example>", "מנורה מבטחים"],
    ["AIG <billing@aig.example>", "AIG"],
    ["Libra <billing@libra.example>", "ליברה"],
    ["weSure <billing@wesure.example>", "weSure"],
  ];

  for (const [from, expectedProvider] of cases) {
    const parsed = parseGmailMessage(message({
      id: `insurance-${expectedProvider}`,
      from,
    }));
    assert.ok(parsed, `expected ${expectedProvider} invoice to be parsed`);
    assert.equal(parsed.providerName, expectedProvider);
    assert.equal(parsed.category, "ביטוח");
    assert.equal(parsed.monthlyCost, 602);
  }
});

test("insurance brand word in arbitrary body is not a strong provider signal", () => {
  const parsed = parseGmailMessage(message({
    id: "generic-phoenix-body",
    from: "Shop <receipt@shop.example>",
    subject: "Receipt",
    body: "Phoenix rewards event. Total due 88.00 ₪",
  }));

  assert.equal(parsed, null);
});
