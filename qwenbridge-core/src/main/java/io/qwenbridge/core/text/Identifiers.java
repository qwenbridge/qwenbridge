package io.qwenbridge.core.text;

import java.util.regex.Pattern;

/** Shared patterns for exact identifiers in free text (QB-024, QB-029). */
public final class Identifiers {

  public static final Pattern IPV4 =
      Pattern.compile(
          "(?<![\\d.])(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?![\\d.])");

  public static final Pattern UUID =
      Pattern.compile(
          "(?i)(?<![0-9a-f-])[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?![0-9a-f-])");

  public static final Pattern QUOTED =
      Pattern.compile("\"([^\"]{1,128})\"|«([^»]{1,128})»|“([^”]{1,128})”");

  /** Mixed letters and digits with at least 2 digits, e.g. SKU-1234, XPS9320, pod-7f9c2. */
  public static final Pattern CODE =
      Pattern.compile(
          "(?<![\\p{L}\\p{N}])(?=[\\p{L}\\p{N}_-]*\\p{L})(?=(?:[\\p{L}_-]*\\p{N}){2})[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{2,63}(?![\\p{L}\\p{N}])");

  private Identifiers() {}

  public static boolean containsAny(String text) {
    return IPV4.matcher(text).find()
        || UUID.matcher(text).find()
        || QUOTED.matcher(text).find()
        || CODE.matcher(text).find();
  }
}
