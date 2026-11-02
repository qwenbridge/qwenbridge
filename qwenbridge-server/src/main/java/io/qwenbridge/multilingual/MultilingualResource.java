package io.qwenbridge.multilingual;

import io.qwenbridge.core.expansion.TargetLanguage;
import io.qwenbridge.core.translation.TranslationProvider;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The effective multilingual expansion configuration for the caller's tenant (QB-286): whether it
 * is enabled, the policy, each configured target with its resolution and the providers that produce
 * it, the installed providers with their declared capabilities and availability, and the bounds.
 * Only the caller's own tenant is visible.
 */
@Path("/api/v2/multilingual")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MultilingualResource {

  @Inject ExpansionConfiguration configuration;
  @Context ContainerRequestContext context;

  @GET
  public Map<String, Object> configuration() {
    var caller = ApiIdentity.caller(context);
    caller.require("search");
    var settings = configuration.settings(caller.tenant());
    var out = new LinkedHashMap<String, Object>();
    out.put("tenant", caller.tenant());
    out.put("scope", configuration.tenantSpecific(caller.tenant()) ? "tenant" : "application");
    out.put("enabled", settings.enabled());
    out.put("policy", settings.policy().name());
    out.put("minConfidence", settings.minConfidence());
    out.put("aliases", settings.aliases());
    List<Map<String, Object>> targets = new ArrayList<>();
    for (TargetLanguage t : settings.resolved()) {
      var m = new LinkedHashMap<String, Object>();
      m.put("configured", t.configured());
      t.language().ifPresent(l -> m.put("language", l.value()));
      m.put("resolution", t.resolution().name());
      m.put("note", t.note());
      if (t.usable())
        m.put(
            "providers",
            configuration.providers().stream()
                .filter(p -> p.capabilities().supportsTarget(t.language().orElseThrow()))
                .map(TranslationProvider::id)
                .toList());
      targets.add(m);
    }
    out.put("targets", targets);
    out.put("providers", configuration.providers().stream().map(this::provider).toList());
    var limits = configuration.limits();
    out.put(
        "limits",
        Map.of(
            "perLanguageTimeoutMs", limits.perLanguage().toMillis(),
            "timeoutMs", limits.overall().toMillis(),
            "maxParallel", limits.maxParallel(),
            "maxConcurrentTranslations", configuration.maxConcurrentTranslations()));
    return out;
  }

  private Map<String, Object> provider(TranslationProvider p) {
    var caps = p.capabilities();
    var m = new LinkedHashMap<String, Object>();
    m.put("id", p.id());
    m.put("status", p.availability().state().name());
    m.put("detail", p.availability().detail());
    m.put("sources", caps.sources().stream().sorted().toList());
    m.put("targets", caps.targets().stream().sorted().toList());
    m.put("pairs", caps.pairs().stream().sorted().toList());
    m.put("detectsSource", caps.detectsSource());
    m.put("maxInputChars", caps.maxInputChars());
    m.put("requirements", p.requirements());
    m.put("metadata", p.metadata());
    return m;
  }
}
