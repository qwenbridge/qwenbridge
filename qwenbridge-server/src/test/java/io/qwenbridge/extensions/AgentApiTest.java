package io.qwenbridge.extensions;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.qwenbridge.agent.AgentDefinition;
import io.qwenbridge.agent.ToolDefinition;
import io.qwenbridge.plugin.PluginManifest;
import io.qwenbridge.plugin.VersionRange;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * QB-225 – QB-230 over HTTP: the extension inventory, the agent catalog, and an agent run's start,
 * inspection and cancellation, each authorized as the caller; runs are visible only to the subject
 * who started them.
 */
@QuarkusTest
@TestProfile(AgentApiTest.Principals.class)
class AgentApiTest {

  static final String ALICE = "test-only-agents-alice-1234567890";
  static final String BOB = "test-only-agents-bob-12345678901234";
  static final String CAROL = "test-only-agents-carol-123456789012";

  public static class Principals implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      String roles = "[\"search\",\"agents:run\",\"agents:read\",\"plugins:read\"]";
      return Map.of(
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + ALICE
              + "\",\"subject\":\"alice\",\"tenant\":\"acme\",\"roles\":"
              + roles
              + "},{\"key\":\""
              + BOB
              + "\",\"subject\":\"bob\",\"tenant\":\"acme\",\"roles\":"
              + roles
              + "},{\"key\":\""
              + CAROL
              + "\",\"subject\":\"carol\",\"tenant\":\"acme\",\"roles\":[\"search\"]}]");
    }
  }

  @Inject PluginHost host;

  @BeforeEach
  void installDemoPlugin() {
    if (host.statuses().stream().anyMatch(s -> s.id().equals("api-demo"))) return;
    var manifest =
        new PluginManifest(
            "api-demo",
            "acme",
            "1.0.0",
            new VersionRange("1.0.0", "2.0.0"),
            new VersionRange("0.0.0", "99.0.0"),
            "test.ApiDemo",
            List.of(),
            Set.of("echo.read", "wait.read"),
            Set.of("echo", "waiter"));
    host.install(
        manifest,
        () ->
            c -> {
              var r = c.registrar();
              r.tool(
                  new ToolDefinition(
                      "echo.read",
                      "search",
                      ToolDefinition.Effect.READ,
                      "index",
                      "Echo",
                      Duration.ofSeconds(2)),
                  call -> Map.of("echo", call.input().getOrDefault("text", "")));
              r.tool(
                  new ToolDefinition(
                      "wait.read",
                      "search",
                      ToolDefinition.Effect.READ,
                      "index",
                      "Wait",
                      Duration.ofSeconds(20)),
                  call -> {
                    while (!call.cancelled().getAsBoolean()) Thread.sleep(20);
                    return Map.of();
                  });
              r.agent(
                  new AgentDefinition(
                      "echo",
                      "Echo agent",
                      Set.of("echo.read"),
                      Set.of(),
                      5,
                      Duration.ofSeconds(5)),
                  (ctx, in) -> Map.of("tool", ctx.tool("echo.read", in).output()));
              r.agent(
                  new AgentDefinition(
                      "waiter", "Waits", Set.of("wait.read"), Set.of(), 5, Duration.ofSeconds(20)),
                  (ctx, in) -> {
                    ctx.tool("wait.read", Map.of());
                    return Map.of();
                  });
            });
    host.activate("api-demo");
  }

  String start(String key, String agent) {
    return given()
        .header("Authorization", "Bearer " + key)
        .contentType("application/json")
        .body(Map.of("input", Map.of("text", "hello")))
        .post("/api/v2/agents/" + agent + "/runs")
        .then()
        .statusCode(202)
        .body("runId", notNullValue())
        .extract()
        .path("runId");
  }

  @Test
  void inventoryAndCatalogRequireTheirPermissions() {
    given()
        .header("Authorization", "Bearer " + ALICE)
        .get("/api/v2/extensions")
        .then()
        .statusCode(200)
        .body("find { it.id == 'api-demo' }.state", equalTo("STARTED"));
    given()
        .header("Authorization", "Bearer " + ALICE)
        .get("/api/v2/agents")
        .then()
        .statusCode(200)
        .body("sdkVersion", equalTo("1.0.0"))
        .body("agents.id", hasItem("echo"))
        .body("tools.id", hasItem("echo.read"));
    given()
        .header("Authorization", "Bearer " + CAROL)
        .get("/api/v2/extensions")
        .then()
        .statusCode(403);
    given().header("Authorization", "Bearer " + CAROL).get("/api/v2/agents").then().statusCode(403);
    given().get("/api/v2/agents").then().statusCode(403);
  }

  @Test
  void aRunIsStartedInspectedByItsOwnerOnlyAndCompletes() throws Exception {
    String runId = start(ALICE, "echo");
    String status = "RUNNING";
    for (int i = 0; i < 100 && status.equals("RUNNING"); i++) {
      Thread.sleep(50);
      status =
          given()
              .header("Authorization", "Bearer " + ALICE)
              .get("/api/v2/agents/runs/" + runId)
              .then()
              .statusCode(200)
              .extract()
              .path("status");
    }
    given()
        .header("Authorization", "Bearer " + ALICE)
        .get("/api/v2/agents/runs/" + runId)
        .then()
        .body("status", is("COMPLETED"))
        .body("output.tool.echo", is("hello"))
        .body("steps[0].status", is("COMPLETED"));
    given()
        .header("Authorization", "Bearer " + BOB)
        .get("/api/v2/agents/runs/" + runId)
        .then()
        .statusCode(404);
    given()
        .header("Authorization", "Bearer " + BOB)
        .delete("/api/v2/agents/runs/" + runId)
        .then()
        .statusCode(404);
    given()
        .header("Authorization", "Bearer " + CAROL)
        .contentType("application/json")
        .body(Map.of())
        .post("/api/v2/agents/echo/runs")
        .then()
        .statusCode(403);
    given()
        .header("Authorization", "Bearer " + ALICE)
        .contentType("application/json")
        .body(Map.of())
        .post("/api/v2/agents/no-such-agent/runs")
        .then()
        .statusCode(404);
  }

  @Test
  void aRunningAgentCanBeCancelled() throws Exception {
    String runId = start(ALICE, "waiter");
    Thread.sleep(200);
    given()
        .header("Authorization", "Bearer " + ALICE)
        .delete("/api/v2/agents/runs/" + runId)
        .then()
        .statusCode(200)
        .body("status", is("CANCELLED"));
  }
}
