package io.qwenbridge.pack.tool;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The local pack distribution workflow (QB-278): generate a publisher key pair, package and sign a
 * pack, verify and inspect a package, and produce the install request, all offline with the JDK
 * alone. A package ({@code .qbpack}) is one JSON object with sorted, fixed fields; Ed25519 is
 * deterministic, so the same inputs always give the same bytes.
 *
 * <pre>
 * keygen  &lt;private-key-out&gt; &lt;public-key-out&gt;
 * pack    &lt;manifest.json&gt; &lt;pack.jar&gt; &lt;key-id&gt; &lt;private-key&gt; &lt;out.qbpack&gt; [metadata.json]
 * verify  &lt;package.qbpack&gt; &lt;key-id&gt; &lt;public-key&gt;
 * inspect &lt;package.qbpack&gt;
 * request &lt;package.qbpack&gt;     (the body for POST /api/v2/platform/packs)
 * </pre>
 *
 * The private key file stays with the publisher; servers trust only the public key ({@code
 * qwenbridge.packs.trusted-publishers}).
 */
public final class PackTool {

  public static final String FORMAT = "qwenbridge-pack/1";
  private static final String[] FIELDS = {
    "format", "keyId", "digest", "manifest", "jar", "signature", "metadata", "metadataSignature"
  };

  private PackTool() {}

  public static void main(String[] args) throws Exception {
    System.exit(run(args, System.out, System.err));
  }

  /** Runs one command; returns the process exit status. */
  public static int run(String[] args, PrintStream out, PrintStream err) {
    try {
      if (args.length == 0)
        throw new IllegalArgumentException("usage: keygen|pack|verify|inspect|request");
      switch (args[0]) {
        case "keygen" -> {
          need(args, 3);
          keygen(Path.of(args[1]), Path.of(args[2]));
          out.println("Key pair written; keep " + args[1] + " private.");
        }
        case "pack" -> {
          if (args.length != 6 && args.length != 7)
            throw new IllegalArgumentException("pack needs 5 or 6 arguments");
          pack(
              Path.of(args[1]),
              Path.of(args[2]),
              args[3],
              Path.of(args[4]),
              Path.of(args[5]),
              args.length == 7 ? Path.of(args[6]) : null);
          out.println("Package written: " + args[5]);
        }
        case "verify" -> {
          need(args, 4);
          out.println(verify(Path.of(args[1]), args[2], Path.of(args[3])));
        }
        case "inspect" -> {
          need(args, 2);
          inspect(Path.of(args[1])).forEach((k, v) -> out.println(k + ": " + v));
        }
        case "request" -> {
          need(args, 2);
          out.println(request(Path.of(args[1])));
        }
        default -> throw new IllegalArgumentException("unknown command " + args[0]);
      }
      return 0;
    } catch (IllegalArgumentException | IOException | GeneralSecurityException e) {
      err.println("error: " + e.getMessage());
      return 1;
    }
  }

  private static void need(String[] args, int n) {
    if (args.length != n)
      throw new IllegalArgumentException(args[0] + " needs " + (n - 1) + " arguments");
  }

  public static void keygen(Path privateOut, Path publicOut)
      throws IOException, GeneralSecurityException {
    var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    Files.writeString(
        privateOut, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n");
    Files.writeString(
        publicOut, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n");
  }

  static String digest(byte[] bytes) throws GeneralSecurityException {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  static byte[] packBytes(String manifest, String digest) {
    return ("qwenbridge-pack-v1\n" + manifest + "\n" + digest).getBytes(StandardCharsets.UTF_8);
  }

  static byte[] metadataBytes(String digest, String metadata) {
    return ("qwenbridge-pack-metadata-v1\n" + digest + "\n" + metadata)
        .getBytes(StandardCharsets.UTF_8);
  }

  private static PrivateKey privateKey(Path file) throws IOException, GeneralSecurityException {
    return KeyFactory.getInstance("Ed25519")
        .generatePrivate(
            new PKCS8EncodedKeySpec(Base64.getDecoder().decode(Files.readString(file).strip())));
  }

  private static PublicKey publicKey(Path file) throws IOException, GeneralSecurityException {
    return KeyFactory.getInstance("Ed25519")
        .generatePublic(
            new X509EncodedKeySpec(Base64.getDecoder().decode(Files.readString(file).strip())));
  }

  private static String sign(PrivateKey key, byte[] bytes) throws GeneralSecurityException {
    var s = Signature.getInstance("Ed25519");
    s.initSign(key);
    s.update(bytes);
    return Base64.getEncoder().encodeToString(s.sign());
  }

  private static boolean verifies(PublicKey key, byte[] bytes, String signature)
      throws GeneralSecurityException {
    var s = Signature.getInstance("Ed25519");
    s.initVerify(key);
    s.update(bytes);
    try {
      return s.verify(Base64.getDecoder().decode(signature));
    } catch (IllegalArgumentException | java.security.SignatureException e) {
      return false;
    }
  }

  public static void pack(
      Path manifestFile, Path jarFile, String keyId, Path privateKey, Path out, Path metadataFile)
      throws IOException, GeneralSecurityException {
    String manifest = Files.readString(manifestFile).strip();
    var publisher = Pattern.compile("\"publisher\"\\s*:\\s*\"([^\"]*)\"").matcher(manifest);
    if (!publisher.find() || !publisher.group(1).equals(keyId))
      throw new IllegalArgumentException("the manifest publisher must equal the key id " + keyId);
    byte[] jar = Files.readAllBytes(jarFile);
    String digest = digest(jar);
    var key = privateKey(privateKey);
    Map<String, String> fields = new LinkedHashMap<>();
    fields.put("format", FORMAT);
    fields.put("keyId", keyId);
    fields.put("digest", digest);
    fields.put("manifest", manifest);
    fields.put("jar", Base64.getEncoder().encodeToString(jar));
    fields.put("signature", sign(key, packBytes(manifest, digest)));
    if (metadataFile != null) {
      String metadata = Files.readString(metadataFile).strip();
      fields.put("metadata", metadata);
      fields.put("metadataSignature", sign(key, metadataBytes(digest, metadata)));
    }
    Files.writeString(out, json(fields) + "\n");
  }

  /** Checks digest and signatures against one trusted public key; throws when anything fails. */
  public static String verify(Path packageFile, String keyId, Path publicKeyFile)
      throws IOException, GeneralSecurityException {
    var p = read(packageFile);
    if (!FORMAT.equals(p.get("format")))
      throw new IllegalArgumentException("unsupported package format");
    if (!keyId.equals(p.get("keyId")))
      throw new IllegalArgumentException("package is signed by key " + p.get("keyId"));
    byte[] jar = Base64.getDecoder().decode(p.get("jar"));
    String digest = digest(jar);
    if (!digest.equals(p.get("digest")))
      throw new IllegalArgumentException("digest mismatch: the JAR was changed");
    var key = publicKey(publicKeyFile);
    if (!verifies(key, packBytes(p.get("manifest"), digest), p.get("signature")))
      throw new IllegalArgumentException("invalid package signature");
    if (p.get("metadata") != null
        && !verifies(key, metadataBytes(digest, p.get("metadata")), p.get("metadataSignature")))
      throw new IllegalArgumentException("invalid metadata signature");
    return "OK " + digest;
  }

  /** Metadata only: nothing is executed and no key is needed. */
  public static Map<String, String> inspect(Path packageFile) throws IOException {
    var p = read(packageFile);
    Map<String, String> out = new LinkedHashMap<>();
    for (String field : new String[] {"id", "version", "kind", "publisher", "apiVersion"}) {
      var m =
          Pattern.compile("\"" + field + "\"\\s*:\\s*\"?([^\",}]*)")
              .matcher(p.getOrDefault("manifest", ""));
      out.put(field, m.find() ? m.group(1).strip() : "");
    }
    out.put("format", p.get("format"));
    out.put("keyId", p.get("keyId"));
    out.put("digest", p.get("digest"));
    out.put("metadata", p.get("metadata") == null ? "none" : "signed");
    return out;
  }

  /** The install request body: the package without its format and digest fields. */
  public static String request(Path packageFile) throws IOException {
    var p = read(packageFile);
    Map<String, String> body = new LinkedHashMap<>();
    for (String field :
        new String[] {"keyId", "manifest", "jar", "signature", "metadata", "metadataSignature"})
      if (p.get(field) != null) body.put(field, p.get(field));
    return json(body);
  }

  static Map<String, String> read(Path packageFile) throws IOException {
    String text = Files.readString(packageFile).strip();
    if (text.length() > 4_000_000) throw new IllegalArgumentException("package too large");
    return parse(text);
  }

  static String json(Map<String, String> fields) {
    var b = new StringBuilder("{");
    for (var e : fields.entrySet()) {
      if (b.length() > 1) b.append(',');
      b.append(quote(e.getKey())).append(':').append(quote(e.getValue()));
    }
    return b.append('}').toString();
  }

  static String quote(String s) {
    var b = new StringBuilder("\"");
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
          else b.append(c);
        }
      }
    }
    return b.append('"').toString();
  }

  /** Parses a flat JSON object of string values, the only shape a package has. */
  static Map<String, String> parse(String text) {
    Map<String, String> out = new LinkedHashMap<>();
    int[] i = {skip(text, 0)};
    expect(text, i, '{');
    i[0] = skip(text, i[0]);
    if (text.charAt(i[0]) == '}') return out;
    while (true) {
      i[0] = skip(text, i[0]);
      String key = string(text, i);
      i[0] = skip(text, i[0]);
      expect(text, i, ':');
      i[0] = skip(text, i[0]);
      String value;
      if (text.startsWith("null", i[0])) {
        value = null;
        i[0] += 4;
      } else value = string(text, i);
      if (!java.util.Arrays.asList(FIELDS).contains(key))
        throw new IllegalArgumentException("unknown package field " + key);
      out.put(key, value);
      i[0] = skip(text, i[0]);
      if (text.charAt(i[0]) == ',') {
        i[0]++;
        continue;
      }
      expect(text, i, '}');
      if (skip(text, i[0]) != text.length()) throw new IllegalArgumentException("trailing content");
      return out;
    }
  }

  private static int skip(String t, int i) {
    while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++;
    return i;
  }

  private static void expect(String t, int[] i, char c) {
    if (i[0] >= t.length() || t.charAt(i[0]) != c)
      throw new IllegalArgumentException("malformed package");
    i[0]++;
  }

  private static String string(String t, int[] i) {
    expect(t, i, '"');
    var b = new StringBuilder();
    while (i[0] < t.length()) {
      char c = t.charAt(i[0]++);
      if (c == '"') return b.toString();
      if (c != '\\') {
        b.append(c);
        continue;
      }
      char e = t.charAt(i[0]++);
      switch (e) {
        case '"', '\\', '/' -> b.append(e);
        case 'n' -> b.append('\n');
        case 'r' -> b.append('\r');
        case 't' -> b.append('\t');
        case 'b' -> b.append('\b');
        case 'f' -> b.append('\f');
        case 'u' -> {
          b.append((char) Integer.parseInt(t.substring(i[0], i[0] + 4), 16));
          i[0] += 4;
        }
        default -> throw new IllegalArgumentException("malformed package");
      }
    }
    throw new IllegalArgumentException("malformed package");
  }
}
