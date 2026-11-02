package io.qwenbridge.core.queryir;

import java.util.regex.Pattern;

/**
 * The single naming rule for schema-defined names used in QueryIR and domain schemas: field paths,
 * entity types, intent types and semantic concepts. A name is up to 8 dot-separated segments; each
 * starts with a lowercase letter and may use camelCase or snake_case ({@code price}, {@code
 * inStock}, {@code memory_usage}, {@code latency.p99}, {@code kubernetes.pod}).
 */
public final class Names {

  public static final Pattern PATTERN =
      Pattern.compile("^[a-z][A-Za-z0-9_]{0,63}(\\.[a-z][A-Za-z0-9_]{0,63}){0,7}$");

  private Names() {}

  public static boolean valid(String name) {
    return name != null && PATTERN.matcher(name).matches();
  }
}
