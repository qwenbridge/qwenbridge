package io.qwenbridge.pipeline.result;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.input.model.InputSource;
import io.qwenbridge.input.model.MultilingualInput;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class RequestContextTest {

  @Test
  void shouldUseClientProvidedRequestId() {
    RequestContext context = RequestContext.of("client-request-1", "table");

    assertThat(context.requestId()).isEqualTo("client-request-1");
    assertThat(context.originalQuery()).isEqualTo("table");
  }

  @Test
  void shouldGenerateRequestIdWhenClientRequestIdIsBlank() {
    RequestContext context = RequestContext.of("", "table");

    assertThat(context.requestId()).isNotBlank();
    assertThat(context.originalQuery()).isEqualTo("table");
  }

  @Test
  void shouldWrapLegacyStringInputAsMultilingualInput() {
    RequestContext context = RequestContext.of("request-1", "میز ارزان");

    assertThat(context.input()).isNotNull();
    assertThat(context.input().originalText()).isEqualTo("میز ارزان");
    assertThat(context.originalQuery()).isEqualTo("میز ارزان");
    assertThat(context.input().declaredLanguageOptional()).isEmpty();
    assertThat(context.input().localeOptional()).isEmpty();
    assertThat(context.input().source()).isEqualTo(InputSource.UNKNOWN);
  }

  @Test
  void shouldPreserveMultilingualInputMetadata() {
    MultilingualInput input =
        MultilingualInput.of(
            "Jag vill ha en lätt laptop", "sv", Locale.forLanguageTag("sv-SE"), InputSource.SDK);

    RequestContext context = RequestContext.of("request-2", input);

    assertThat(context.requestId()).isEqualTo("request-2");
    assertThat(context.input()).isSameAs(input);
    assertThat(context.originalQuery()).isEqualTo("Jag vill ha en lätt laptop");
    assertThat(context.input().declaredLanguageOptional()).contains("sv");
    assertThat(context.input().localeOptional()).contains(Locale.forLanguageTag("sv-SE"));
    assertThat(context.input().source()).isEqualTo(InputSource.SDK);
  }

  @Test
  void shouldPreserveOriginalWhitespaceAndUnicode() {
    String original = "  لپ‌تاپ خوب  ";

    RequestContext context = RequestContext.of("request-3", original);

    assertThat(context.originalQuery()).isEqualTo(original);
    assertThat(context.input().originalText()).isEqualTo(original);
  }
}
