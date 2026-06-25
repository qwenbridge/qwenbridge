package io.omnisearch.language;

import org.springframework.stereotype.Service;

@Service
public class LanguageService {

    public String detect(String query) {
        if (query == null || query.isBlank()) {
            return "unknown";
        }

        if (query.matches(".*[\\u0600-\\u06FF].*")) {
            return "fa";
        }

        if (query.matches(".*[\\u3040-\\u30FF].*")) {
            return "ja";
        }

        if (query.matches(".*[\\u4E00-\\u9FFF].*")) {
            return "zh";
        }

        if (query.matches(".*[A-Za-z].*")) {
            return "en";
        }

        return "unknown";
    }
}
