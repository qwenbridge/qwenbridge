package io.qwenbridge.operations.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Duration;

/**
 * Server-side latency distributions (QB-211). Every HTTP server timer and every {@code
 * qwenbridge.*} timer publishes a percentile histogram, so P50/P95/P99 are computed by the metrics
 * backend over any window (for example {@code histogram_quantile(0.95, rate(..._bucket[5m]))}),
 * from 1 ms to 60 s. Measurement boundaries are documented in docs/evaluation/metrics.md.
 */
@Singleton
public class LatencyHistograms {

  @Produces
  @Singleton
  public MeterFilter percentileHistograms() {
    return new MeterFilter() {
      @Override
      public DistributionStatisticConfig configure(
          Meter.Id id, DistributionStatisticConfig config) {
        if (id.getType() != Meter.Type.TIMER) return config;
        String name = id.getName();
        if (!name.startsWith("qwenbridge.") && !name.equals("http.server.requests")) return config;
        return DistributionStatisticConfig.builder()
            .percentilesHistogram(true)
            .minimumExpectedValue((double) Duration.ofMillis(1).toNanos())
            .maximumExpectedValue((double) Duration.ofSeconds(60).toNanos())
            .build()
            .merge(config);
      }
    };
  }
}
