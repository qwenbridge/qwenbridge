import type { StreamingEvent } from "./streaming-event.js";
import type {
  AICompletedStreamingPayload,
  AIFailedStreamingPayload,
  AITokenStreamingPayload,
  ConnectedStreamingPayload,
  PipelineStreamingPayload,
  StageStreamingPayload,
  StreamingPayload,
  UnknownStreamingPayload
} from "./payload/streaming-payload.js";

export class StreamingPayloadMapper {
  public map(event: StreamingEvent): StreamingPayload {
    const parsed = this.safeParse(event.data);

    if (event.event === "stream.connected" && this.isObject(parsed)) {
      return {
        kind: "connected",
        requestId: this.stringValue(parsed.requestId),
        sessionId: this.stringValue(parsed.sessionId)
      } satisfies ConnectedStreamingPayload;
    }

    if (event.event === "ai.token" && this.isObject(parsed)) {
      return {
        kind: "ai.token",
        requestId: this.stringValue(parsed.requestId),
        tokenIndex: this.numberValue(parsed.tokenIndex),
        content: this.stringValue(parsed.content),
        terminal: this.booleanValue(parsed.terminal)
      } satisfies AITokenStreamingPayload;
    }

    if (event.event === "ai.completed" && this.isObject(parsed)) {
      return {
        kind: "ai.completed",
        requestId: this.stringValue(parsed.requestId),
        tokenCount: this.numberValue(parsed.tokenCount),
        terminal: this.booleanValue(parsed.terminal)
      } satisfies AICompletedStreamingPayload;
    }

    if (event.event === "ai.failed" && this.isObject(parsed)) {
      return {
        kind: "ai.failed",
        requestId: this.stringValue(parsed.requestId),
        code: this.stringValue(parsed.code),
        message: this.stringValue(parsed.message),
        terminal: this.booleanValue(parsed.terminal)
      } satisfies AIFailedStreamingPayload;
    }

    if (this.isObject(parsed) && this.isKnownRuntimeEvent(event.event)) {
      const terminal =
          event.event === "pipeline.completed"
          || event.event === "pipeline.failed"
          || event.event === "pipeline.stopped";

      const mapped = {
        kind: event.event,
        requestId: this.stringValue(parsed.requestId),
        event: this.stringValue(parsed.event) || event.event,
        stage: this.stringValue(parsed.stage),
        type: this.stringValue(parsed.type),
        sequenceNumber: this.numberValue(parsed.sequenceNumber),
        terminal,
        payload: this.objectValue(parsed.payload)
      };

      if (event.event.startsWith("pipeline.")) {
        const pipelinePayload = mapped as PipelineStreamingPayload;
        return pipelinePayload;
      }

      const stagePayload = mapped as StageStreamingPayload;
      return stagePayload;
    }

    return {
      kind: "unknown",
      event: event.event,
      raw: event.data,
      parsed
    } satisfies UnknownStreamingPayload;
  }

  private safeParse(data: string): unknown {
    try {
      return JSON.parse(data);
    } catch {
      return undefined;
    }
  }

  private isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
  }

  private stringValue(value: unknown): string {
    return typeof value === "string" ? value : "";
  }

  private numberValue(value: unknown): number {
    return typeof value === "number" && Number.isFinite(value) ? value : 0;
  }

  private booleanValue(value: unknown): boolean {
    return typeof value === "boolean" ? value : false;
  }

  private isKnownRuntimeEvent(eventName: string): boolean {
    return /^pipeline\.(started|completed|failed|stopped)$/.test(eventName)
        || /^(language|normalization|threat|ai_analysis|intent|rewrite|semantic|policy|decision|confidence)\.(started|completed|failed|skipped|checked|detected)$/.test(eventName);
  }

  private objectValue(value: unknown): Record<string, unknown> {
    return this.isObject(value) ? value : {};
  }

}
