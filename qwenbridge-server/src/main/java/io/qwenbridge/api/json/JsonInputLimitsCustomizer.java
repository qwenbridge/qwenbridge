package io.qwenbridge.api.json;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.qwenbridge.api.request.RequestLimitsConfig;
import jakarta.inject.Singleton;

/**
 * Parser-level structural limits (QB-005, QB-058). They apply while JSON is being read, so deeply
 * nested payloads are rejected before any object graph is built — independently of whether abuse
 * protection is enabled. String values are bounded at the upload ceiling (signed packs are the
 * largest legitimate strings), number literals at 64 characters and property names at 256, which
 * also bounds what upstream services (model, search) can make the parser hold.
 */
@Singleton
public class JsonInputLimitsCustomizer implements ObjectMapperCustomizer {

  private final int maxNestingDepth;
  private final int maxStringLength;
  private final int maxNumberLength;
  private final int maxNameLength;

  public JsonInputLimitsCustomizer(RequestLimitsConfig config) {
    this.maxNestingDepth = config.maxJsonNestingDepth();
    this.maxStringLength = config.maxJsonStringLength();
    this.maxNumberLength = config.maxJsonNumberLength();
    this.maxNameLength = config.maxJsonNameLength();
  }

  @Override
  public void customize(ObjectMapper mapper) {
    StreamReadConstraints current = mapper.getFactory().streamReadConstraints();
    mapper
        .getFactory()
        .setStreamReadConstraints(
            current
                .rebuild()
                .maxNestingDepth(maxNestingDepth)
                .maxStringLength(maxStringLength)
                .maxNumberLength(maxNumberLength)
                .maxNameLength(maxNameLength)
                .build());
  }
}
