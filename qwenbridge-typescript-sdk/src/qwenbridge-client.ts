import {
  QwenBridgeApiError,
  QwenBridgeTransportError,
  type QwenBridgeApiErrorBody
} from "./errors.js";
import type {
  QwenBridgeClientOptions,
  SearchAnalyzeRequest,
  SearchAnalyzeResponse
} from "./types.js";

const REQUEST_ID_HEADER = "X-Request-Id";

export class QwenBridgeClient {
  private readonly fetchFn: typeof fetch;

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

  public async analyze(
    request: SearchAnalyzeRequest
  ): Promise<SearchAnalyzeResponse> {
    this.validateAnalyzeRequest(request);

    const headers: Record<string, string> = {
      "Accept": "application/json",
      "Content-Type": "application/json"
    };

    if (request.requestId?.trim()) {
      headers[REQUEST_ID_HEADER] = request.requestId.trim();
    }

    let response: Response;

    try {
      response = await this.fetchFn(this.endpoint("/api/v1/search/analyze"), {
        method: "POST",
        headers,
        body: JSON.stringify(request)
      });
    } catch (error) {
      throw new QwenBridgeTransportError(
        "Failed to call QwenBridge API",
        error
      );
    }

    if (response.ok) {
      return await this.readJson<SearchAnalyzeResponse>(response);
    }

    throw new QwenBridgeApiError(
      await this.readApiError(response)
    );
  }

  private endpoint(path: string): string {
    return new URL(path, this.options.baseUrl).toString();
  }

  private validateAnalyzeRequest(request: SearchAnalyzeRequest): void {
    if (!request) {
      throw new TypeError("request must not be null or undefined");
    }

    if (!request.query || !request.query.trim()) {
      throw new TypeError("query must not be blank");
    }
  }

  private async readJson<T>(response: Response): Promise<T> {
    try {
      return await response.json() as T;
    } catch (error) {
      throw new QwenBridgeTransportError(
        "Failed to parse QwenBridge API response",
        error
      );
    }
  }

  private async readApiError(response: Response): Promise<QwenBridgeApiErrorBody> {
    try {
      return await response.json() as QwenBridgeApiErrorBody;
    } catch {
      return {
        status: response.status,
        error: response.statusText,
        message: "QwenBridge API returned HTTP " + response.status
      };
    }
  }
}
