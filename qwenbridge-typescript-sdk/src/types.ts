import type { RetryPolicyOptions } from "./retry/retry-policy.js";

export interface QwenBridgeClientOptions {
  baseUrl: string;
  fetch?: typeof fetch;
  retry?: RetryPolicyOptions;
}

export interface SearchAnalyzeRequest {
  requestId?: string;
  query: string;
  declaredLanguage?: string;
  locale?: string;
}

/** One configured typo-correction language and its status (QB-291). */
export interface SpellingLanguage { configured: string; status: "SUPPORTED" | "PROVIDER_UNAVAILABLE" | "INVALID_IDENTIFIER"; providers: string[]; }
/** One token's correction or suggestion; offsets are into `normalized`. */
export interface SpellingCorrection {
  token: string; start: number; end: number; language: string;
  candidates: Array<{ form: string; language: string; score: number }>;
  replacement: string; confidence: number; applied: boolean; reason: string;
}
/**
 * Typo processing reported by the server (QB-291); present only when typo correction is enabled
 * for the caller's tenant. `original` is the query as sent.
 */
export interface Spelling {
  status: "DISABLED" | "UNCONFIGURED" | "NO_PROVIDER" | "LANGUAGE_NOT_ENABLED" | "NO_CHANGES" | "SUGGESTED" | "CORRECTED";
  mode: "DISABLED" | "SUGGEST_ONLY" | "SAFE_AUTO_CORRECT" | "AGGRESSIVE";
  original: string; normalized: string; corrected: string;
  languages: SpellingLanguage[]; detectedLanguage: string | null; activeLanguages: string[];
  corrections: SpellingCorrection[]; protectedTokens: number; explanations: string[];
}

/** Status of one configured target language (QB-286). */
export type ExpansionTargetStatus =
  | "SUCCESS" | "UNCHANGED" | "LOW_CONFIDENCE" | "UNSUPPORTED_LANGUAGE" | "UNSUPPORTED_LANGUAGE_PAIR"
  | "PROVIDER_UNAVAILABLE" | "SOURCE_UNDETERMINED" | "INVALID_LANGUAGE" | "FAILED";
/** One configured target language; `text` is present only when a variant was produced. */
export interface ExpansionTarget {
  requested: string; language?: string; sourceLanguage?: string; originalRef: string;
  status: ExpansionTargetStatus; text?: string; provider?: string;
  confidence: string; confidenceScore?: number; reason?: string;
  preservation?: { status: "PRESERVED" | "PARTIALLY_VERIFIED" | "NOT_PRESERVED"; issues: Array<{ kind: string; direction: string; count: number }>; unverified: string[] };
  identifiersPreserved: boolean; retrieval: boolean; elapsedMillis: number;
}
/**
 * Multilingual query expansion reported by the server (QB-286); present only when expansion is
 * enabled for the caller's tenant. One target per configured, non-duplicate language, in order.
 */
export interface Expansion {
  status: "COMPLETE" | "PARTIAL" | "FAILED" | "NOT_CONFIGURED" | "CANCELLED";
  policy: "ALL_TARGETS" | "STRICT_ALL_TARGETS" | "BEST_EFFORT_ALL_TARGETS";
  original: string; originalRef: string; sourceText: string;
  source: { language?: string; basis: "DETECTED" | "VOCABULARY" | "DECLARED" | "MIXED" | "UNDETERMINED"; mixed: boolean; mixedLanguages: string[]; confidence: string; confidenceScore?: number };
  configured: Array<{ configured: string; language?: string; resolution: string; note: string }>;
  requestedLanguages: string[]; successfulLanguages: string[]; unsupportedLanguages: string[];
  targets: ExpansionTarget[]; retrievalTexts: string[]; explanations: string[]; elapsedMillis: number;
}

export interface SearchAnalyzeResponse {
  requestId?: string;
  processingTimeMs?: number;
  originalQuery?: string;
  language?: string;
  intent?: string;
  decision?: string;
  confidence?: number;
  confidenceAssessment?: {
    value: number;
    method: string;
    calibrated: boolean;
    signals: Record<string, number>;
  };
  rewrites?: string[];
  threatReasons?: string[];
  semanticValidated?: boolean;
  semanticScore?: number;
  policyPassed?: boolean;
  policyViolations?: string[];
  executionPlan?: unknown;
  executionResult?: unknown;
  search?: unknown;
  cache?: unknown;
  pipelineTrace?: unknown[];
  /** QB-291: typo processing, when enabled. */
  spelling?: Spelling;
  /** QB-286: multilingual expansion, when enabled. */
  expansion?: Expansion;
}
