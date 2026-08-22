"use strict";

const PDF_ANALYSIS_STATES = Object.freeze({
  NO_PDF: "NO_PDF",
  PDF_ANALYSIS_FAILURE: "PDF_ANALYSIS_FAILURE",
  PDF_CLASSIFICATION_RESULT: "PDF_CLASSIFICATION_RESULT",
});

const PDF_CLASSIFICATION_RESULTS = Symbol.for("clickandsaveai.gmail.pdfClassificationResults");

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
      candidates: classifications.map((outcome) => outcome.candidate).filter(Boolean),
    };
  }
  if (Number(pdfAttachmentCount || 0) <= 0) {
    return {
      pdfState: PDF_ANALYSIS_STATES.NO_PDF,
      candidates: fallbackBody ? [fallbackBody] : [],
    };
  }
  return {
    pdfState: PDF_ANALYSIS_STATES.PDF_ANALYSIS_FAILURE,
    candidates: fallbackBody ? [fallbackBody] : [],
  };
}

module.exports = {
  PDF_ANALYSIS_STATES,
  hasPdfClassificationResult,
  pdfClassificationResults,
  recordPdfClassificationResult,
  resolvePdfBodyCandidates,
};
