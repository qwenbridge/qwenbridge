package io.qwenbridge.platform;

import static org.assertj.core.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * QB-152: the Commerce pack can be disabled and enabled per tenant. While disabled, nothing reaches
 * it (409); other tenants and other packs are unaffected; enabling again restores it.
 */
class CommercePackLifecycleTest {
  @Test
  void commerceCanBeDisabledAndEnabledPerTenantWithoutAffectingOthers() throws Exception {
    try (var f = new PackAcceptanceTest.Fixture()) {
      String tenant = f.admin.tenant();
      var commerce =
          f.packs.list(tenant).stream()
              .filter(v -> v.manifest().id().equals("commerce"))
              .findFirst()
              .orElseThrow();
      assertThat(commerce.bundled()).isTrue();
      assertThat(commerce.active()).isTrue();
      assertThat(f.packs.<String>withPack(tenant, "commerce", p -> p.manifest().id()))
          .isEqualTo("commerce");

      f.packs.disable(f.admin, "commerce");
      assertThatThrownBy(() -> f.packs.<String>withPack(tenant, "commerce", p -> p.manifest().id()))
          .isInstanceOf(jakarta.ws.rs.ClientErrorException.class)
          .hasMessageContaining("disabled");
      assertThat(f.packs.list(tenant))
          .filteredOn(v -> v.manifest().id().equals("commerce"))
          .allMatch(v -> !v.active());
      // Another tenant keeps Commerce; the other bundled pack keeps working here.
      assertThat(f.packs.<String>withPack("tenant-b", "commerce", p -> p.manifest().id()))
          .isEqualTo("commerce");
      var others =
          f.packs.list(tenant).stream()
              .filter(v -> v.bundled() && !v.manifest().id().equals("commerce"))
              .toList();
      for (var other : others)
        assertThat(f.packs.<String>withPack(tenant, other.manifest().id(), p -> p.manifest().id()))
            .isEqualTo(other.manifest().id());
      // Without the scope nothing changes.
      var viewer = new io.qwenbridge.security.ApiIdentity.Principal("v", tenant, Set.of("search"));
      assertThatThrownBy(
              () -> f.packs.activate(viewer, "commerce", commerce.manifest().version(), Map.of()))
          .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);

      f.packs.activate(f.admin, "commerce", commerce.manifest().version(), Map.of());
      assertThat(f.packs.<String>withPack(tenant, "commerce", p -> p.manifest().id()))
          .isEqualTo("commerce");
    }
  }
}
