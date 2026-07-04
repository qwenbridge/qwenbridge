package io.qwenbridge.abuse;

public interface RateLimiter {
    RateLimitDecision consume(String policy, String subject, long limit, long cost);
}
