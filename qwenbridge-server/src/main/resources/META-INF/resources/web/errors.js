export class QwenBridgeApiError extends Error {
    body;
    constructor(body) {
        super(body.message);
        this.body = body;
        this.name = "QwenBridgeApiError";
    }
    get status() {
        return this.body.status;
    }
    get code() {
        return this.body.code;
    }
    get requestId() {
        return this.body.requestId;
    }
    /** v2 error category (for example `DEPENDENCY`), undefined for v1 responses. */
    get category() {
        return this.body.category;
    }
    /** Whether the server says the same request may succeed later (v2 only). */
    get retryable() {
        return this.body.retryable;
    }
}
export class QwenBridgeTransportError extends Error {
    cause;
    constructor(message, cause) {
        super(message);
        this.cause = cause;
        this.name = "QwenBridgeTransportError";
    }
}
/** The server speaks a contract version this SDK does not support (QB-189). */
export class QwenBridgeIncompatibleServerError extends Error {
    constructor(message) {
        super(message);
        this.name = "QwenBridgeIncompatibleServerError";
    }
}
