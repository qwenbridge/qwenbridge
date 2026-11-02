package io.qwenbridge.core.spelling;

import java.util.HashMap;
import java.util.Map;

/**
 * Weighted optimal-string-alignment distance over Unicode code points (QB-291 G). Insertions and
 * deletions cost 1, substitutions 1 (0.6 between keys adjacent on a known physical keyboard
 * layout), and adjacent transpositions 0.8. Comparison is case-insensitive. Keyboard layouts are
 * physical layouts, not languages: a Latin QWERTY layout (with the Nordic and German extra keys)
 * and the standard Persian layout. Unknown characters simply get the plain substitution cost.
 */
public final class EditDistance {

  private static final String[][] LAYOUTS = {
    {"qwertyuiopåü", "asdfghjklöäæø", "zxcvbnm"},
    {"ضصثقفغعهخحج", "شسیبلاتنمکگ", "ظطزرذدپو"}
  };

  private record Key(int layout, int row, int column) {}

  private static final Map<Integer, Key> KEYS = new HashMap<>();

  static {
    for (int l = 0; l < LAYOUTS.length; l++)
      for (int r = 0; r < LAYOUTS[l].length; r++) {
        int[] row = LAYOUTS[l][r].codePoints().toArray();
        for (int c = 0; c < row.length; c++) KEYS.putIfAbsent(row[c], new Key(l, r, c));
      }
  }

  private EditDistance() {}

  static boolean adjacentKeys(int a, int b) {
    Key x = KEYS.get(a);
    Key y = KEYS.get(b);
    return x != null
        && y != null
        && x.layout() == y.layout()
        && Math.abs(x.row() - y.row()) <= 1
        && Math.abs(x.column() - y.column()) <= 1;
  }

  /** The weighted distance, or a value above {@code limit} once it cannot be within it. */
  public static double between(String first, String second, double limit) {
    int[] a = first.codePoints().map(Character::toLowerCase).toArray();
    int[] b = second.codePoints().map(Character::toLowerCase).toArray();
    if (Math.abs(a.length - b.length) > limit) return limit + 1;
    double[][] d = new double[a.length + 1][b.length + 1];
    for (int i = 0; i <= a.length; i++) d[i][0] = i;
    for (int j = 0; j <= b.length; j++) d[0][j] = j;
    for (int i = 1; i <= a.length; i++) {
      double rowMin = Double.MAX_VALUE;
      for (int j = 1; j <= b.length; j++) {
        double substitution =
            a[i - 1] == b[j - 1] ? 0 : adjacentKeys(a[i - 1], b[j - 1]) ? 0.6 : 1.0;
        double best =
            Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + substitution);
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1])
          best = Math.min(best, d[i - 2][j - 2] + 0.8);
        d[i][j] = best;
        rowMin = Math.min(rowMin, best);
      }
      if (rowMin > limit) return limit + 1;
    }
    return d[a.length][b.length];
  }
}
