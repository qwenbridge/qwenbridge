package io.qwenbridge.execution.provider.spi;

import io.qwenbridge.pipeline.ExecutionContext;

public interface SearchProviderResolver {

    SearchProvider resolve(ExecutionContext context);
}