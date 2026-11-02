package io.qwenbridge.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * QB-204, QB-287: QwenBridge contains no Shopify code; Shopify is only an optional external
 * end-to-end target. The server's sources must stay free of it.
 */
class ShopifyIsolationTest {

  @Test
  void serverSourcesContainNoShopifyCode() throws IOException {
    List<Path> offenders;
    try (Stream<Path> files = Files.walk(Path.of("src", "main"))) {
      offenders =
          files
              .filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".java"))
              .filter(
                  p -> {
                    try {
                      return Files.readString(p).toLowerCase(Locale.ROOT).contains("shopify");
                    } catch (IOException e) {
                      throw new java.io.UncheckedIOException(e);
                    }
                  })
              .toList();
    }
    assertThat(offenders).isEmpty();
  }
}
