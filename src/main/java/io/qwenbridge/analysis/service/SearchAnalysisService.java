package io.qwenbridge.analysis.service;

import io.qwenbridge.analysis.model.SearchAnalysis;

public interface SearchAnalysisService {

    SearchAnalysis analyze(String query);

    default SearchAnalysis analyze(String query, String requestId) {
        return analyze(query);
    }
}
