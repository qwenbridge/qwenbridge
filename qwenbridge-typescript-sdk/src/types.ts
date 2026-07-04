import type { RetryPolicyOptions } from "./retry/retry-policy.js";

export interface QwenBridgeClientOptions {
  baseUrl: string;
  fetch?: typeof fetch;
  retry?: RetryPolicyOptions;
}

export interface SearchAnalyzeRequest {
  requestId?: string;
  query: string;
}

export interface SearchAnalyzeResponse {
  requestId?: string;
  processingTimeMs?: number;
  originalQuery?: string;
  language?: string;
  intent?: string;
  decision?: string;
  confidence?: number;
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
}
