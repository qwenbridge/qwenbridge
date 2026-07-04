export type StreamingPayload =
  | ConnectedStreamingPayload
  | AITokenStreamingPayload
  | AICompletedStreamingPayload
  | AIFailedStreamingPayload
  | UnknownStreamingPayload;

export interface ConnectedStreamingPayload {
  kind: "connected";
  requestId: string;
  sessionId: string;
}

export interface AITokenStreamingPayload {
  kind: "ai.token";
  requestId: string;
  tokenIndex: number;
  content: string;
  terminal: boolean;
}

export interface AICompletedStreamingPayload {
  kind: "ai.completed";
  requestId: string;
  tokenCount: number;
  terminal: boolean;
}

export interface AIFailedStreamingPayload {
  kind: "ai.failed";
  requestId: string;
  code: string;
  message: string;
  terminal: boolean;
}

export interface UnknownStreamingPayload {
  kind: "unknown";
  event: string;
  raw: string;
  parsed?: unknown;
}
