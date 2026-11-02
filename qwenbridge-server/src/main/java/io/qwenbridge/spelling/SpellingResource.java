package io.qwenbridge.spelling;

import io.qwenbridge.core.spelling.CorrectionProvider;
import io.qwenbridge.core.spelling.TypoCorrector;
import io.qwenbridge.security.ApiIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The effective typo configuration for the caller's tenant (QB-291 A4, B): whether it is enabled,
 * the policy, each configured language's status, and the installed providers. Only the caller's own
 * tenant is visible.
 */
@Path("/api/v2/spelling")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class SpellingResource {

  @Inject TypoConfiguration configuration;
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
    out.put("mode", settings.effectiveMode().name());
    out.put("languages", TypoCorrector.languages(settings, configuration.providers()));
    List<Map<String, Object>> providers =
        configuration.providers().stream()
            .map(CorrectionProvider::descriptor)
            .map(
                d -> {
                  var p = new LinkedHashMap<String, Object>();
                  p.put("id", d.id());
                  p.put("languages", d.languages().stream().map(t -> t.value()).sorted().toList());
                  p.put("scripts", d.scripts().stream().map(Enum::name).sorted().toList());
                  p.put(
                      "capabilities", d.capabilities().stream().map(Enum::name).sorted().toList());
                  p.put("requirements", d.requirements());
                  p.put("entries", d.entries());
                  p.put("approximateBytes", d.approximateBytes());
                  return (Map<String, Object>) p;
                })
            .toList();
    out.put("providers", providers);
    return out;
  }
}
