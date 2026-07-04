export interface QwenBridgeClientOptions {
  baseUrl: string;
}

export interface SearchAnalyzeRequest {
  query: string;
  limit?: number;
}

export interface SearchAnalyzeResponse {
  answer: string;
  requestId?: string;
}
