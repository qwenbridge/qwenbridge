package io.qwenbridge.core.suggestion;

import io.qwenbridge.core.queryir.Constraint;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies typed changes to a query's constraints (QB-133). Every constraint the changes do not name
 * is kept exactly as it was (same id, value, provenance and span); exclusions are only ever removed
 * by an explicit REMOVE naming them. The caller re-validates the result.
 */
public final class Refinement {

  /** Constraint factory for added or replaced constraints. */
  @FunctionalInterface
  public interface Builder {
    Constraint build(Change change, int index);
  }

  private Refinement() {}

  public static List<Constraint> apply(
      List<Constraint> constraints, List<Change> changes, Builder builder) {
    List<Constraint> out = new ArrayList<>(constraints);
    for (int i = 0; i < changes.size(); i++) {
      Change change = changes.get(i);
      switch (change.op()) {
        case ADD -> out.add(builder.build(change, i));
        case REMOVE ->
            out.removeIf(
                c -> c.field().equals(change.field()) && c.operator() == change.operator());
        case REPLACE -> {
          int at = -1;
          for (int k = 0; k < out.size(); k++) {
            Constraint c = out.get(k);
            if (c.field().equals(change.field()) && c.operator() == change.operator()) {
              at = at < 0 ? k : at;
            }
          }
          out.removeIf(c -> c.field().equals(change.field()) && c.operator() == change.operator());
          Constraint replacement = builder.build(change, i);
          if (at < 0 || at > out.size()) {
            out.add(replacement);
          } else {
            out.add(at, replacement);
          }
        }
      }
    }
    return List.copyOf(out);
  }
}
