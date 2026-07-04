import type {
  QwenBridgeClientOptions,
  SearchAnalyzeRequest,
  SearchAnalyzeResponse
} from "./types.js";

export class QwenBridgeClient {
  public constructor(
    private readonly options: QwenBridgeClientOptions
  ) {}

  public async analyze(
    _request: SearchAnalyzeRequest
  ): Promise<SearchAnalyzeResponse> {
    throw new Error(
      "QwenBridgeClient.analyze is not implemented yet."
    );
  }

  public get baseUrl(): string {
    return this.options.baseUrl;
  }
}
