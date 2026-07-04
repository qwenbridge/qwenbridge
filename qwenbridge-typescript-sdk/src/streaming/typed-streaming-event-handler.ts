import type { TypedStreamingEvent } from "./typed-streaming-event.js";

export type TypedStreamingEventHandler = (
  event: TypedStreamingEvent
) => void | Promise<void>;
