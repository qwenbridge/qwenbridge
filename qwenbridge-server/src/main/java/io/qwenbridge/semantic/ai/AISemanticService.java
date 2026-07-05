package io.qwenbridge.semantic.ai;

import io.qwenbridge.semantic.SemanticAnalysis;

public interface AISemanticService {

  SemanticAnalysis analyze(String query);
}
