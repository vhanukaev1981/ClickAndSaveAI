"use strict";

// One source of truth for every Gmail ingestion path. A parser-version bump must
// never cause another six-month mailbox scan after initialBackfillCompleted=true.
const ACTIVE_GMAIL_PARSER_VERSION = 7;

module.exports = {
  ACTIVE_GMAIL_PARSER_VERSION,
};
