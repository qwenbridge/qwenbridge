package io.qwenbridge.security;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** QB-075, QB-077 over HTTP with approvals enforced. */
@QuarkusTest
@TestProfile(ApprovalEnforcementTest.Enforced.class)
class ApprovalEnforcementTest {

  static final String ALICE = "approvals-alice-test-only-credential";
  static final String BOB = "approvals-bob-approver-test-only-credential";
  static final String CAROL = "approvals-carol-both-test-only-credential";
  static final String DAN = "approvals-dan-other-tenant-test-only-cred";

  public static class Enforced implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "qwenbridge.policy.approvals-enforced",
          "true",
          "qwenbridge.security.authentication-required",
          "true",
          "qwenbridge.security.api-principals",
          "[{\"key\":\""
              + ALICE
              + "\",\"subject\":\"alice\",\"tenant\":\"acme\",\"roles\":[\"search\",\"license:write\"]},"
              + "{\"key\":\""
              + BOB
              + "\",\"subject\":\"bob\",\"tenant\":\"acme\",\"roles\":[\"approvals:grant\"]},"
              + "{\"key\":\""
              + CAROL
              + "\",\"subject\":\"carol\",\"tenant\":\"acme\",\"roles\":[\"license:write\",\"approvals:grant\"]},"
              + "{\"key\":\""
              + DAN
              + "\",\"subject\":\"dan\",\"tenant\":\"globex\",\"roles\":[\"approvals:grant\"]}]");
    }
  }

  private static Response downgrade(String key, String approval) {
    var request = given().header("Authorization", "Bearer " + key);
    if (approval != null) {
      request = request.header(ApprovalContext.HEADER, approval);
    }
    return request.delete("/api/v2/platform/license");
  }

  private static String approve(String key) {
    return given()
        .header("Authorization", "Bearer " + key)
        .contentType("application/json")
        .body(
            Map.of("operation", "license:write", "resourceType", "license", "validitySeconds", 300))
        .post("/api/v2/platform/approvals")
        .then()
        .statusCode(200)
        .extract()
        .path("id");
  }

  @Test
  void highRiskOperationNeedsAnApprovalFromSomeoneElseAndUsesItOnce() {
    Response without = downgrade(ALICE, null);
    assertThat(without.statusCode()).isEqualTo(403);
    assertThat(without.asString()).contains("Approval required");

    String approval = approve(BOB);
    assertThat(downgrade(ALICE, approval).statusCode()).isEqualTo(200);
    assertThat(downgrade(ALICE, approval).statusCode()).as("single use").isEqualTo(403);
  }

  @Test
  void selfApprovalAndOtherTenantApprovalsDoNotCount() {
    assertThat(downgrade(CAROL, approve(CAROL)).statusCode()).isEqualTo(403);
    assertThat(downgrade(ALICE, approve(DAN)).statusCode()).isEqualTo(403);
  }

  @Test
  void onlyApproversCanIssueApprovals() {
    given()
        .header("Authorization", "Bearer " + ALICE)
        .contentType("application/json")
        .body(Map.of("operation", "license:write", "resourceType", "license"))
        .post("/api/v2/platform/approvals")
        .then()
        .statusCode(403);
  }
}
