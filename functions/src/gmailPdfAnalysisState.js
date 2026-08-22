"use strict";

const PDF_ANALYSIS_STATES = Object.freeze({
  NO_PDF: "NO_PDF",
  PDF_ANALYSIS_FAILURE: "PDF_ANALYSIS_FAILURE",
  PDF_CLASSIFICATION_RESULT: "PDF_CLASSIFICATION_RESULT",
});

const PDF_CLASSIFICATION_RESULTS = Symbol.for("clickandsaveai.gmail.pdfClassificationResults");
const CANDIDATE_PDF_ANALYSIS_STATE = Symbol.for("clickandsaveai.gmail.candidatePdfAnalysisState");

function isPdfAnalysisState(value) {
  return Object.values(PDF_ANALYSIS_STATES).includes(value);
}

function tagCandidatePdfAnalysisState(candidate, state) {
  if (!candidate || typeof candidate !== "object" || !isPdfAnalysisState(state)) return candidate;
  Object.defineProperty(candidate, CANDIDATE_PDF_ANALYSIS_STATE, {
    value: state,
    configurable: true,
    enumerable: false,
    writable: false,
  });
  return candidate;
}

function candidatePdfAnalysisState(candidate) {
  const state = candidate?.[CANDIDATE_PDF_ANALYSIS_STATE];
  return isPdfAnalysisState(state) ? state : "";
}

function recordPdfClassificationResult(message, result) {
  if (!message || typeof message !== "object") return;
  const current = Array.isArray(message[PDF_CLASSIFICATION_RESULTS])
    ? message[PDF_CLASSIFICATION_RESULTS]
    : [];
  if (!Object.prototype.hasOwnProperty.call(message, PDF_CLASSIFICATION_RESULTS)) {
    Object.defineProperty(message, PDF_CLASSIFICATION_RESULTS, {
      value: current,
      configurable: true,
      enumerable: false,
      writable: false,
    });
  }
  current.push({
    state: PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT,
    documentClass: String(result?.documentClass || "UNKNOWN"),
    candidate: result?.candidate || null,
  });
}

function pdfClassificationResults(message) {
  return Array.isArray(message?.[PDF_CLASSIFICATION_RESULTS])
    ? [...message[PDF_CLASSIFICATION_RESULTS]]
    : [];
}

function hasPdfClassificationResult(message) {
  return pdfClassificationResults(message).length > 0;
}

function resolvePdfBodyCandidates({ pdfAttachmentCount = 0, pdfOutcomes = [], fallbackBody = null } = {}) {
  const outcomes = Array.isArray(pdfOutcomes) ? pdfOutcomes : [];
  const classifications = outcomes.filter((outcome) =>
    outcome?.state === PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT
  );
  if (classifications.length > 0) {
    return {
      pdfState: PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT,
      candidates: classifications
        .map((outcome) => outcome.candidate)
        .filter(Boolean)
        .map((candidate) => tagCandidatePdfAnalysisState(
          candidate,
          PDF_ANALYSIS_STATES.PDF_CLASSIFICATION_RESULT
        )),
    };
  }
  if (Number(pdfAttachmentCount || 0) <= 0) {
    return {
      pdfState: PDF_ANALYSIS_STATES.NO_PDF,
      candidates: fallbackBody
        ? [tagCandidatePdfAnalysisState(fallbackBody, PDF_ANALYSIS_STATES.NO_PDF)]
        : [],
    };
  }
  return {
    pdfState: PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE,
    candidates: fallbackBody
      ? [tagCandidatePdfAnalysisState(fallbackBody, PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE)]
      : [],
  };
}

module.exports = {
  PDF_ANALYSIS_STATES,
  candidatePdfAnalysisState,
  hasPdfClassificationResult,
  pdfClassificationResults,
  recordPdfClassificationResult,
  resolvePdfBodyCandidates,
  tagCandidatePdfAnalysisState,
};
