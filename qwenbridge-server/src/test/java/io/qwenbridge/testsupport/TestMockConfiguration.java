package io.qwenbridge.testsupport;

import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import io.qwenbridge.operations.health.OperationalHealthService;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestMockConfiguration {

    @Bean
    @Primary
    AIService aiService() {
        return Mockito.mock(AIService.class);
    }

    @Bean
    @Primary
    OpenSearchClient openSearchClient() {
        return Mockito.mock(OpenSearchClient.class);
    }

    @Bean
    @Primary
    SearchAnalysisService searchAnalysisService() {
        return Mockito.mock(SearchAnalysisService.class);
    }

    @Bean
    @Primary
    OperationalHealthService operationalHealthService() {
        return Mockito.mock(OperationalHealthService.class);
    }

    @Bean
    @Primary
    AIAnalysisCache aiAnalysisCache() {
        return Mockito.mock(AIAnalysisCache.class);
    }
}
