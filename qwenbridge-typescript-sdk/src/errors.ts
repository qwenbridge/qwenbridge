/** The server's error body (QB-181). `category` and `retryable` are sent by `/api/v2` endpoints. */
export interface QwenBridgeApiErrorBody {
  timestamp?: string;
  status: number;
  error?: string;
  code?: string;
  message: string;
  path?: string;
  requestId?: string;
  category?: string;
  retryable?: boolean;
}

export class QwenBridgeApiError extends Error {
  public constructor(
    public readonly body: QwenBridgeApiErrorBody
  ) {
    super(body.message);
    this.name = "QwenBridgeApiError";
  }

  public get status(): number {
    return this.body.status;
  }

  public get code(): string | undefined {
    return this.body.code;
  }

  public get requestId(): string | undefined {
    return this.body.requestId;
  }

  /** v2 error category (for example `DEPENDENCY`), undefined for v1 responses. */
  public get category(): string | undefined {
    return this.body.category;
  }

  /** Whether the server says the same request may succeed later (v2 only). */
  public get retryable(): boolean | undefined {
    return this.body.retryable;
  }
}

export class QwenBridgeTransportError extends Error {
  public constructor(
    message: string,
    public readonly cause?: unknown
  ) {
    super(message);
    this.name = "QwenBridgeTransportError";
  }
}

/** The server speaks a contract version this SDK does not support (QB-189). */
export class QwenBridgeIncompatibleServerError extends Error {
  public constructor(message: string) {
    super(message);
    this.name = "QwenBridgeIncompatibleServerError";
  }
}
