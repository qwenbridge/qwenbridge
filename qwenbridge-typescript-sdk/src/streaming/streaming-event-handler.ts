import type { StreamingEvent } from "./streaming-event.js";

export type StreamingEventHandler = (event: StreamingEvent) => void | Promise<void>;
