package io.qwenbridge.core.connector;

/** Resolves secret references (QB-156). Failures name the reference, never a value. */
@FunctionalInterface
public interface SecretResolver {
  Secret resolve(SecretRef reference);
}
