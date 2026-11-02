package io.qwenbridge.catalog;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@jakarta.enterprise.context.ApplicationScoped
public class CatalogServices {
  @Produces
  @Singleton
  CatalogNormalizer normalizer() {
    return new CatalogNormalizer();
  }
}
