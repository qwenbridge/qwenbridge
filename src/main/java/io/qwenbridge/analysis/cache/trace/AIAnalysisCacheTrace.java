package io.qwenbridge.analysis.cache.trace;

public record AIAnalysisCacheTrace(
        boolean enabled,
        boolean hit,
        boolean miss,
        String key,
        String provider,
        String model,
        String version
) {
    public AIAnalysisCacheTrace {
        key = key == null ? "" : key;
        provider = provider == null ? "" : provider;
        model = model == null ? "" : model;
        version = version == null ? "" : version;
        miss = !hit;
    }

    public static AIAnalysisCacheTrace disabled() {
        return new AIAnalysisCacheTrace(
                false,
                false,
                true,
                "",
                "",
                "",
                ""
        );
    }

    public static AIAnalysisCacheTrace hit(
            String key,
            String provider,
            String model,
            String version
    ) {
        return new AIAnalysisCacheTrace(
                true,
                true,
                false,
                key,
                provider,
                model,
                version
        );
    }

    public static AIAnalysisCacheTrace miss(
            String key,
            String provider,
            String model,
            String version
    ) {
        return new AIAnalysisCacheTrace(
                true,
                false,
                true,
                key,
                provider,
                model,
                version
        );
    }
}
