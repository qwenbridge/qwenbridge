import { QwenBridgeTransportError } from "../errors.js";
import type { QwenBridgeClientOptions } from "../types.js";
import type { StreamingEvent } from "./streaming-event.js";
import type { StreamingEventHandler } from "./streaming-event-handler.js";
import { StreamingPayloadMapper } from "./streaming-payload-mapper.js";
import type { TypedStreamingEvent } from "./typed-streaming-event.js";
import type { TypedStreamingEventHandler } from "./typed-streaming-event-handler.js";

export class QwenBridgeStreamingClient {
  private readonly fetchFn: typeof fetch;
  private readonly payloadMapper = new StreamingPayloadMapper();

  public constructor(
    private readonly options: QwenBridgeClientOptions
  ) {
    this.fetchFn = options.fetch ?? globalThis.fetch;

    if (!this.fetchFn) {
      throw new QwenBridgeTransportError(
        "No fetch implementation is available. Use Node.js 20+ or provide options.fetch."
      );
    }
  }

  public get baseUrl(): string {
    return this.options.baseUrl;
  }

  public async stream(
    requestId: string,
    handler: StreamingEventHandler
  ): Promise<void> {
    await this.openAndParse(requestId, handler);
  }

  public async streamTyped(
    requestId: string,
    handler: TypedStreamingEventHandler
  ): Promise<void> {
    await this.openAndParse(requestId, async event => {
      const typedEvent: TypedStreamingEvent = {
        event: event.event,
        data: event.data,
        payload: this.payloadMapper.map(event)
      };

      await handler(typedEvent);
    });
  }

  private async openAndParse(
    requestId: string,
    handler: StreamingEventHandler
  ): Promise<void> {
    this.validateRequestId(requestId);

    const response = await this.openStream(requestId);

    if (!response.ok) {
      throw new QwenBridgeTransportError(
        "Failed to open QwenBridge SSE stream: HTTP " + response.status
      );
    }

    if (!response.body) {
      throw new QwenBridgeTransportError(
        "QwenBridge SSE stream response did not contain a readable body"
      );
    }

    await this.parseStream(response.body, handler);
  }

  private async openStream(requestId: string): Promise<Response> {
    try {
      return await this.fetchFn(this.streamEndpoint(requestId), {
        method: "GET",
        headers: {
          "Accept": "text/event-stream"
        }
      });
    } catch (error) {
      throw new QwenBridgeTransportError(
        "Failed to open QwenBridge SSE stream",
        error
      );
    }
  }

  private async parseStream(
    body: ReadableStream<Uint8Array>,
    handler: StreamingEventHandler
  ): Promise<void> {
    const reader = body.getReader();
    const decoder = new TextDecoder();

    let buffer = "";

    try {
      while (true) {
        const chunk = await reader.read();

        if (chunk.done) {
          buffer += decoder.decode();
          break;
        }

        buffer += decoder.decode(chunk.value, {
          stream: true
        });

        const parts = buffer.split(/\r?\n\r?\n/);
        buffer = parts.pop() ?? "";

        for (const part of parts) {
          const event = this.parseEvent(part);

          if (event) {
            await handler(event);
          }
        }
      }

      const finalEvent = this.parseEvent(buffer);

      if (finalEvent) {
        await handler(finalEvent);
      }
    } catch (error) {
      throw new QwenBridgeTransportError(
        "Failed to parse QwenBridge SSE stream",
        error
      );
    } finally {
      reader.releaseLock();
    }
  }

  private parseEvent(raw: string): StreamingEvent | null {
    if (!raw.trim()) {
      return null;
    }

    let eventName = "message";
    const dataLines: string[] = [];

    for (const line of raw.split(/\r?\n/)) {
      if (!line || line.startsWith(":")) {
        continue;
      }

      if (line.startsWith("event:")) {
        eventName = line.substring("event:".length).trim();
        continue;
      }

      if (line.startsWith("data:")) {
        dataLines.push(line.substring("data:".length).trim());
      }
    }

    if (dataLines.length === 0) {
      return null;
    }

    return {
      event: eventName,
      data: dataLines.join("\n")
    };
  }

  private streamEndpoint(requestId: string): string {
    const encodedRequestId = encodeURIComponent(requestId);

    return new URL(
      "/api/v1/search/stream/" + encodedRequestId,
      this.options.baseUrl
    ).toString();
  }

  private validateRequestId(requestId: string): void {
    if (!requestId || !requestId.trim()) {
      throw new TypeError("requestId must not be blank");
    }
  }
}
