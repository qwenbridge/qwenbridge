export interface QwenBridgeApiErrorBody {
  timestamp?: string;
  status: number;
  error?: string;
  code?: string;
  message: string;
  path?: string;
  requestId?: string;
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
