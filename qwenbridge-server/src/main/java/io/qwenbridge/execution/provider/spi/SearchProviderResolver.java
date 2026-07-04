package io.qwenbridge.execution.provider.spi;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.pipeline.ExecutionContext;

public interface SearchProviderResolver {

    SearchProvider resolve(ExecutionContext context);

    SearchProvider resolve(SearchBackend backend);
}
