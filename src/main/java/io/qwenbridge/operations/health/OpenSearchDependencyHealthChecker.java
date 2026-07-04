package io.qwenbridge.operations.health;

import io.qwenbridge.execution.provider.opensearch.OpenSearchProperties;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchClient;
import org.springframework.stereotype.Component;

@Component
public class OpenSearchDependencyHealthChecker implements DependencyHealthChecker {

    private final OpenSearchClient client;
    private final OpenSearchProperties properties;

    public OpenSearchDependencyHealthChecker(OpenSearchClient client, OpenSearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public DependencyHealth check() {
        long started = System.nanoTime();
        try {
            client.webClient().get().uri("/").retrieve().toBodilessEntity().block(properties.connectTimeout().plus(properties.readTimeout()));
            return DependencyHealth.up("opensearch", durationMs(started));
        } catch (Exception ex) {
            return DependencyHealth.degraded("opensearch", "unavailable", durationMs(started));
        }
    }

    private long durationMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
