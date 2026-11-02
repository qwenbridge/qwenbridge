package io.qwenbridge.commerce;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qwenbridge.security.ApiIdentity;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAuthorizedException;
import org.junit.jupiter.api.Test;

class ApiIdentityTest {
  @Test
  void credentialsBindToConfiguredTenantAndRolesRatherThanClientClaims() {
    var auth =
        new ApiIdentity(
            new ObjectMapper(),
            true,
            "[{\"key\":\"test-secret-with-at-least-24-characters\",\"subject\":\"alice\",\"tenant\":\"shopA\",\"roles\":[\"search\"]}]");
    var caller = auth.authenticate("Bearer test-secret-with-at-least-24-characters");
    assertThat(caller.tenant()).isEqualTo("shopA");
    caller.require("search");
    assertThatThrownBy(() -> caller.require("schema:write")).isInstanceOf(ForbiddenException.class);
    assertThatThrownBy(() -> auth.authenticate(null)).isInstanceOf(NotAuthorizedException.class);
    assertThatThrownBy(() -> auth.authenticate("Bearer forged"))
        .isInstanceOf(NotAuthorizedException.class);
  }

  @Test
  void anonymousModeHasNoAdministrativePrivilegesAndInvalidTokensStillFail() {
    var auth = new ApiIdentity(new ObjectMapper(), false, "[]");
    assertThat(auth.authenticate(null).tenant()).isEqualTo("public");
    assertThatThrownBy(() -> auth.authenticate("Bearer wrong"))
        .isInstanceOf(NotAuthorizedException.class);
    assertThatThrownBy(() -> new ApiIdentity(new ObjectMapper(), true, "[]"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
