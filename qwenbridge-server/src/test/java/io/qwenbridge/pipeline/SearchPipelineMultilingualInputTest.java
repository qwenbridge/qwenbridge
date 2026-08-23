package io.qwenbridge.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.input.model.InputSource;
import io.qwenbridge.input.model.MultilingualInput;
import io.qwenbridge.model.SearchAnalyzeRequest;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SearchPipelineMultilingualInputTest {

  private final SearchPipeline pipeline = new SearchPipeline(null);

  @Test
  void shouldMapApiMetadataToMultilingualInput() {
    SearchAnalyzeRequest request =
        new SearchAnalyzeRequest("request-1", "میز ارزان", "fa", "fa-IR");

    MultilingualInput input = pipeline.toMultilingualInput(request);

    assertThat(input.originalText()).isEqualTo("میز ارزان");
    assertThat(input.declaredLanguageOptional()).contains("fa");
    assertThat(input.localeOptional()).contains(Locale.forLanguageTag("fa-IR"));
    assertThat(input.source()).isEqualTo(InputSource.API);
  }

  @Test
  void shouldSupportLegacyRequestWithoutLanguageMetadata() {
    SearchAnalyzeRequest request = new SearchAnalyzeRequest("request-1", "table");

    MultilingualInput input = pipeline.toMultilingualInput(request);

    assertThat(input.originalText()).isEqualTo("table");
    assertThat(input.declaredLanguageOptional()).isEmpty();
    assertThat(input.localeOptional()).isEmpty();
    assertThat(input.source()).isEqualTo(InputSource.API);
  }

  @Test
  void shouldPreserveDeclaredLanguageWithoutTreatingItAsDetectedLanguage() {
    SearchAnalyzeRequest request = new SearchAnalyzeRequest("request-1", "میز", "sv", "sv-SE");

    MultilingualInput input = pipeline.toMultilingualInput(request);

    assertThat(input.originalText()).isEqualTo("میز");
    assertThat(input.declaredLanguageOptional()).contains("sv");
    assertThat(input.localeOptional()).contains(Locale.forLanguageTag("sv-SE"));
  }
}
