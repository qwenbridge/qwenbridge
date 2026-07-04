# QwenBridge Logging

Production profile uses JSON console logging. Local/test profiles keep the standard Spring console format.

Core fields: `timestamp`, `level`, `logger`, `thread`, `message`, `service`, `requestId`, and `traceId`.

Redaction policy:

- Do not log raw secrets, API keys, Redis credentials, OpenSearch credentials or provider URLs with credentials.
- Do not log full prompts or generated tokens at INFO/WARN/ERROR.
- Query/prompt/token logging, when needed for development, must remain DEBUG-only and sanitized.
- Error logs should prefer stable `errorCode`, `stage`, `provider`, `durationMs`, `requestId`, and `traceId` fields over raw exception detail.

Troubleshooting examples:

- Find one request flow: `requestId=<value>`
- Find one distributed trace: `traceId=<value>`
- Find dependency degradation: `message contains degradation OR outcome=failure`
