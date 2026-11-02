package io.qwenbridge.commerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import io.qwenbridge.catalog.CatalogNormalizer;
import io.qwenbridge.connectors.CommerceBackends;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Reproducible demo datasets (QB-199, QB-203). With {@code qwenbridge.demo.datasets} (for example
 * {@code commerce-demo}), the named datasets from {@code classpath:datasets/<id>-<version>.json}
 * are loaded at startup through the Commerce pack's canonical catalog pipeline, for {@code
 * qwenbridge.demo.tenant} (default {@code public}, the anonymous tenant outside {@code %prod}).
 *
 * <p>Demo data goes only into the in-process {@code memory} backend: with any other backend
 * selected the server refuses to start, so invented products can never reach a real index.
 *
 * <p>QB-295: {@code qwenbridge.demo.dataset-files} loads datasets in the same JSON format from
 * files (deterministic test catalogs, for example the dual-SDK end-to-end catalog), with the same
 * memory-only rule. With {@code qwenbridge.demo.embed=true} each record's canonical product text is
 * embedded exactly as catalog synchronization does, so vector and hybrid retrieval can be exercised
 * without catalog sync; a record whose embedding fails is indexed for keyword retrieval only. Both
 * are off by default.
 */
@ApplicationScoped
public class DemoDatasets {
  private static final Logger LOG = Logger.getLogger(DemoDatasets.class);
  static final Map<String, String> AVAILABLE =
      Map.of("commerce-demo", "datasets/commerce-demo-1.0.json");

  /** One record of a dataset, already in the connector's raw shape. */
  public record Dataset(
      String id, String version, String connector, List<Map<String, Object>> records) {
    public Dataset {
      records = List.copyOf(records);
    }
  }

  @Inject CommerceBackends backends;
  @Inject ObjectMapper mapper;

  @ConfigProperty(name = "qwenbridge.demo.datasets")
  java.util.Optional<List<String>> datasets;

  @ConfigProperty(name = "qwenbridge.demo.tenant", defaultValue = "public")
  String tenant;

  @ConfigProperty(name = "qwenbridge.demo.dataset-files")
  java.util.Optional<List<String>> files;

  @ConfigProperty(name = "qwenbridge.demo.embed", defaultValue = "false")
  boolean embed;

  @Inject jakarta.enterprise.inject.Instance<io.qwenbridge.ai.service.AIService> ai;

  void load(@Observes StartupEvent event) {
    var ids = datasets.orElse(List.of());
    var paths = files.orElse(List.of());
    if (ids.isEmpty() && paths.isEmpty()) return;
    if (!"memory".equals(backends.selected()))
      throw new IllegalStateException(
          "qwenbridge.demo.datasets requires qwenbridge.commerce.backend=memory; demo data is never"
              + " written to a real backend");
    for (String id : ids) {
      var dataset = read(mapper, id);
      int loaded = index(dataset, tenant);
      LOG.infof(
          "Loaded demo dataset %s %s: %d records for tenant %s",
          id, dataset.version(), loaded, tenant);
    }
    for (String path : paths) {
      var dataset = readFile(mapper, java.nio.file.Path.of(path));
      int loaded = index(dataset, tenant);
      LOG.infof(
          "Loaded dataset file %s (%s %s): %d records for tenant %s",
          path, dataset.id(), dataset.version(), loaded, tenant);
    }
  }

  /** Reads a dataset file in the bundled datasets' format. */
  public static Dataset readFile(ObjectMapper mapper, java.nio.file.Path path) {
    try (InputStream in = java.nio.file.Files.newInputStream(path)) {
      return parse(mapper, mapper.readTree(in));
    } catch (IOException e) {
      throw new IllegalStateException("Dataset file " + path + " is unreadable", e);
    }
  }

  /** Reads a bundled dataset by id. */
  public static Dataset read(ObjectMapper mapper, String id) {
    String resource = AVAILABLE.get(id);
    if (resource == null)
      throw new IllegalArgumentException(
          "Unknown demo dataset " + id + "; available: " + AVAILABLE.keySet());
    try (InputStream in = DemoDatasets.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) throw new IllegalStateException("Dataset resource missing: " + resource);
      return parse(mapper, mapper.readTree(in));
    } catch (IOException e) {
      throw new IllegalStateException("Dataset " + id + " is unreadable", e);
    }
  }

  private static Dataset parse(ObjectMapper mapper, JsonNode root) {
    var records = new ArrayList<Map<String, Object>>();
    for (JsonNode record : root.path("records")) {
      @SuppressWarnings("unchecked")
      Map<String, Object> raw = mapper.convertValue(record, LinkedHashMap.class);
      records.add(raw);
    }
    return new Dataset(
        root.path("id").asText(),
        root.path("version").asText(),
        root.path("connector").asText(),
        records);
  }

  /** Indexes every record through the canonical pipeline; returns the number indexed. */
  public int index(Dataset dataset, String tenant) {
    var normalizer = new CatalogNormalizer();
    int count = 0;
    for (var raw : dataset.records()) {
      String id = String.valueOf(raw.get("id"));
      var product = normalizer.normalize(dataset.connector(), id, raw);
      var document =
          new java.util.HashMap<>(
              normalizer.document(product, product.variants().getFirst(), tenant));
      if (embed && ai != null && ai.isResolvable()) {
        try {
          var text =
              io.qwenbridge.catalog.canonical.ProductTextTemplate.render(
                  normalizer.canonical(dataset.connector(), id, raw));
          document.put(
              "embedding",
              ai.get().embed(new io.qwenbridge.ai.contract.EmbeddingRequest(text.text())).vector());
          document.put("productTextTemplate", text.template());
          document.put("productTextFingerprint", text.fingerprint());
        } catch (RuntimeException e) {
          LOG.warnf(
              "Dataset record %s indexed without an embedding: %s",
              id, e.getClass().getSimpleName());
        }
      }
      backends.upsert(tenant, id, document);
      count++;
    }
    return count;
  }
}
