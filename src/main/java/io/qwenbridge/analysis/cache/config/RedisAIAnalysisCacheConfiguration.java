package io.qwenbridge.analysis.cache.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
@ConditionalOnProperty(
        prefix = "qwenbridge.analysis.cache",
        name = "type",
        havingValue = "redis"
)
public class RedisAIAnalysisCacheConfiguration {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            AIAnalysisCacheProperties properties
    ) {
        RedisStandaloneConfiguration redis =
                new RedisStandaloneConfiguration(
                        properties.redis().host(),
                        properties.redis().port()
                );

        LettuceClientConfiguration client =
                LettuceClientConfiguration.builder()
                        .commandTimeout(properties.redis().commandTimeout())
                        .shutdownTimeout(properties.redis().connectTimeout())
                        .build();

        return new LettuceConnectionFactory(redis, client);
    }
}
