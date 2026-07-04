package io.qwenbridge.pipeline.context;

public final class ContextKeys {

    private ContextKeys() {
    }

    public static final ContextKey<TenantContext> TENANT =
            ContextKey.of("tenant", TenantContext.class);

    public static final ContextKey<LocaleContext> LOCALE =
            ContextKey.of("locale", LocaleContext.class);

    public static final ContextKey<ExecutionHints> EXECUTION_HINTS =
            ContextKey.of("executionHints", ExecutionHints.class);

}