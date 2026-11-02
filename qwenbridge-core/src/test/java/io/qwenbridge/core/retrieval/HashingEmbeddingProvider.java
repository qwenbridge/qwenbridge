package io.qwenbridge.core.retrieval;

import io.qwenbridge.core.embedding.EmbeddingModel;
import io.qwenbridge.core.embedding.EmbeddingProvider;
import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.retrieval.memory.Tokenizer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * A deterministic test embedding (QB-116): character trigrams of each token hashed into a fixed
 * number of dimensions. The vectors are real (texts sharing word fragments are close, unrelated
 * texts are not), reproducible and need no model. It is a test fixture, not a semantic model.
 */
final class HashingEmbeddingProvider implements EmbeddingProvider {

  private final EmbeddingModel model;

  HashingEmbeddingProvider(String version, int dimensions) {
    this.model =
        new EmbeddingModel(new EmbeddingSpace("hashing-trigram", version, dimensions), 64, 4000);
  }

  @Override
  public String id() {
    return "hashing";
  }

  @Override
  public EmbeddingModel model() {
    return model;
  }

  @Override
  public EmbeddingResult embed(List<String> texts, Duration timeout) {
    List<double[]> out = new ArrayList<>();
    int dims = model.space().dimensions();
    for (String text : texts) {
      double[] v = new double[dims];
      for (String token : Tokenizer.tokens(text)) {
        String padded = "#" + token + "#";
        for (int i = 0; i + 3 <= padded.length(); i++) {
          byte[] gram = padded.substring(i, i + 3).getBytes(StandardCharsets.UTF_8);
          int h = 0x811c9dc5;
          for (byte b : gram) {
            h = (h ^ (b & 0xff)) * 0x01000193;
          }
          v[Math.floorMod(h, dims)] += 1;
        }
      }
      if (java.util.Arrays.stream(v).allMatch(x -> x == 0)) {
        v[0] = 1e-9;
      }
      out.add(v);
    }
    return new EmbeddingResult.Embedded(out, model.space());
  }
}
