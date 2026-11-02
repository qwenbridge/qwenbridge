package io.qwenbridge.core.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Carries constraints from one turn to the next (QB-144). A previous constraint stays, with its
 * original provenance and turn, unless the new turn clears its field or states a constraint that
 * replaces it:
 *
 * <ul>
 *   <li>EQ is replaced by any new EQ on the field;
 *   <li>NE is replaced only by an NE on the same value (exclusions accumulate);
 *   <li>an upper bound (LT, LTE) by a new EQ or upper bound; a lower bound (GT, GTE) by a new EQ or
 *       lower bound, so adding a minimum keeps an earlier maximum.
 * </ul>
 */
public final class ContextMerge {

  private ContextMerge() {}

  public static List<ContextConstraint> followUp(
      List<ContextConstraint> previous, List<ContextConstraint> stated, Set<String> cleared) {
    List<ContextConstraint> out = new ArrayList<>();
    for (ContextConstraint old : previous) {
      if (cleared.contains(old.field()) || replaced(old, stated)) {
        continue;
      }
      out.add(old);
    }
    out.addAll(stated);
    return List.copyOf(out);
  }

  static boolean replaced(ContextConstraint old, List<ContextConstraint> stated) {
    return stated.stream()
        .filter(c -> c.field().equals(old.field()))
        .anyMatch(
            c ->
                switch (old.operator()) {
                  case "EQ" -> c.operator().equals("EQ");
                  case "NE" -> c.operator().equals("NE") && c.value().equals(old.value());
                  case "LT", "LTE" -> Set.of("EQ", "LT", "LTE").contains(c.operator());
                  case "GT", "GTE" -> Set.of("EQ", "GT", "GTE").contains(c.operator());
                  default -> c.operator().equals(old.operator());
                });
  }
}
