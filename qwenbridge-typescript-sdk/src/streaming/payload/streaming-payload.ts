export type StreamingPayload =
    | ConnectedStreamingPayload
    | PipelineStreamingPayload
    | StageStreamingPayload
    | AITokenStreamingPayload
    | AICompletedStreamingPayload
    | AIFailedStreamingPayload
    | UnknownStreamingPayload;

export interface ConnectedStreamingPayload {
  kind: "connected";
  requestId: string;
  sessionId: string;
}

export interface PipelineStreamingPayload {
  kind: "pipeline.started" | "pipeline.completed" | "pipeline.failed" | "pipeline.stopped";
  requestId: string;
  event: string;
  stage: string;
  type: string;
  sequenceNumber: number;
  terminal: boolean;
  payload: Record<string, unknown>;
}

export interface StageStreamingPayload {
  kind: string;
  requestId: string;
  event: string;
  stage: string;
  type: string;
  sequenceNumber: number;
  terminal: boolean;
  payload: Record<string, unknown>;
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