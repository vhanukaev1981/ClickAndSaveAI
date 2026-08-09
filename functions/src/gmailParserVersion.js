"use strict";

// One source of truth for every Gmail ingestion path. Bump only when stored
// Gmail import records must be re-evaluated under materially changed parsing rules.
const ACTIVE_GMAIL_PARSER_VERSION = 6;

module.exports = {
  ACTIVE_GMAIL_PARSER_VERSION,
};
