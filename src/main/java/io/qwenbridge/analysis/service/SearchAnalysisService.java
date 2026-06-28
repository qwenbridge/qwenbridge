package io.qwenbridge.analysis.service;

import io.qwenbridge.analysis.model.SearchAnalysis;

public interface SearchAnalysisService {
    SearchAnalysis analyze(String query);
}
