import type { StreamingPayload } from "./payload/streaming-payload.js";

export interface TypedStreamingEvent {
  event: string;
  data: string;
  payload: StreamingPayload;
}
