package io.qwenbridge.intent.ai;

import io.qwenbridge.intent.IntentAnalysis;

public interface AIIntentService {

  IntentAnalysis analyze(String query);
}
