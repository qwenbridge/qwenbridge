package io.qwenbridge.analysis.cache;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class DefaultCacheKeyBuilder implements CacheKeyBuilder {

    @Override
    public CacheKey build(
            String normalizedQuery,
            String provider,
            String model,
            String version
    ) {
        String raw = safe(provider)
                + "|"
                + safe(model)
                + "|"
                + safe(version)
                + "|"
                + safe(normalizedQuery);

        return new CacheKey(sha256(raw));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build AI analysis cache key", ex);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
