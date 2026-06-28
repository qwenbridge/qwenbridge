package io.qwenbridge.analysis.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "qwenbridge.analysis.cache")
public class AIAnalysisCacheProperties {

    private boolean enabled = true;
    private String type = "redis";
    private String keyPrefix = "qwenbridge:analysis";
    private String version = "v4";
    private Duration ttl = Duration.ofMinutes(10);
    private Redis redis = new Redis();

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String type() {
        return type;
    }

    public void setType(String type) {
        this.type = safe(type, "redis");
    }

    public String keyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = safe(keyPrefix, "qwenbridge:analysis");
    }

    public String version() {
        return version;
    }

    public void setVersion(String version) {
        this.version = safe(version, "v4");
    }

    public Duration ttl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl == null ? Duration.ofMinutes(10) : ttl;
    }

    public Redis redis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis == null ? new Redis() : redis;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static class Redis {
        private String host = "localhost";
        private int port = 6379;
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration commandTimeout = Duration.ofSeconds(2);

        public String host() {
            return host;
        }

        public void setHost(String host) {
            this.host = safe(host, "localhost");
        }

        public int port() {
            return port;
        }

        public void setPort(int port) {
            this.port = port <= 0 ? 6379 : port;
        }

        public Duration connectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        }

        public Duration commandTimeout() {
            return commandTimeout;
        }

        public void setCommandTimeout(Duration commandTimeout) {
            this.commandTimeout = commandTimeout == null ? Duration.ofSeconds(2) : commandTimeout;
        }
    }
}
