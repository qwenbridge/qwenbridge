package io.qwenbridge.execution.provider.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.RewriteResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchRequestFactoryTest {

  @Test
  void shouldCreateRequestFromOriginalQueryWhenRewriteDoesNotExist() {
    ExecutionContext context = new ExecutionContext("iphone");

    SearchRequest request = SearchRequestFactory.from(context);

    assertThat(request.query()).isEqualTo("iphone");
  }

  @Test
  void shouldCreateRequestFromRewrittenQueryWhenAvailable() {
    ExecutionContext context = new ExecutionContext("iphon");

    context.store(RewriteResult.class, new RewriteResult(true, "qwen", List.of("iphone")));

    SearchRequest request = SearchRequestFactory.from(context);

    assertThat(request.query()).isEqualTo("iphone");
  }
}
