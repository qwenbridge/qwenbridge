package io.qwenbridge.normalization.service;

import io.qwenbridge.normalization.model.NormalizedInput;

public interface InputNormalizer {
  NormalizedInput normalize(String input);
}
