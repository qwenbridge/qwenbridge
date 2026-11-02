# ADR 0029: Canonical Product Model, Catalog Mapping SPI and Versioned Product Text

## Status

Accepted (QwenBridge 1.0, Section 2 Phase 14: QB-092 – QB-098)


> **QB-287 (ADR 0058):** superseded in part. The Shopify mapping (moved to the Shopify module in QB-293, removed with it in QB-287) described here were removed from the repository; the generic contracts (CatalogMapping, CatalogConnectorFactory) remain the extension point for future adapters.

## Context

Before this change:

- `CatalogProduct` held plain strings, with no record of where a value came from.
- `CatalogNormalizer` hard-coded one JSON field layout, color and category
  aliases (`CatalogNormalizer:137-155`) and three stock states.
- The Shopify path mixed transport and field meaning: `HttpCatalogConnector`
  flattened GraphQL nodes into ad hoc keys, kept only size and color options
  and checked currency and inventory itself.
- Catalog sync embedded `title + " " + description` once per variant
  (`CatalogSynchronization:116-120`). The text was unversioned and repeated.

## Decision

All new types live in the Commerce pack (`qwenbridge-commerce-pack`). Core is
unchanged and never sees a product.

1. **Canonical product (QB-092).** `CanonicalProduct`, schema version 1.0,
   contains:
   - identity, title, description, brand, category and attributes;
   - one or more variants;
   - typed relations;
   - a `SourceRef`: connector, record id, mapping id and observation time.

   Every value carries a `Provenance`, which records:
   - the origin: SOURCE, NORMALIZED, DERIVED or INFERRED;
   - the source field path;
   - the source value, kept even when normalization changed it;
   - the rule applied.
2. **Mapping SPI (QB-093).** A `CatalogMapping` turns a `SourceRecord` into a
   `ProductDraft`: source values placed into canonical slots, each with its
   path. It returns a typed `MappingResult`: `Mapped`, or `Rejected` with path
   and message. It does not normalize, convert or invent anything.
   - The pack owns an immutable `CatalogMappings` registry. `http-json` is
     `JsonFeedMapping`, the existing layout; `shopify` is `ShopifyMapping`.
   - Duplicate mapping ids are rejected. An unknown connector kind uses the JSON
     feed mapping.
   - `HttpCatalogConnector` is now transport only. Each Shopify change carries
     the raw `shop`, `product` and `variant` nodes. The mapping reads the store
     currency and inventory, and maps every selected option except Shopify's
     `Title: Default Title` placeholder.
3. **Normalization with aliases (QB-094).** `CatalogVocabulary` holds:
   - categories with parents, which give a path such as `fashion > shoes`;
   - brands;
   - attribute names, for example `colour`, `Farbe` and `رنگ` → `color`;
   - attribute values, for example `noir`, `svart` and `مشکی` → `black`.

   How matching works:
   - **Keys.** Matching uses an NFKC, trimmed, whitespace-collapsed,
     lower-case key.
   - **Conflicts.** An alias that maps to two canonical terms fails when the
     vocabulary is built. Unknown parents and cycles fail too.
   - **Unknown values.** These keep the cleaned source value and are marked
     `recognized:false`. Category ids and case-insensitive attributes (color,
     condition) are lower-cased, as before.
   - **Name collisions.** When two source fields map to the same attribute, a
     field named exactly like the canonical attribute wins over an alias,
     whatever the order.

   `CommerceVocabulary.builtIn()` carries the aliases from the previous
   normalizer.
4. **Variants, money and availability (QB-095).**
   - **Prices.** A `Price` is an exact decimal in an ISO 4217 currency,
     validated against the currency's minor units. Digits in any script and the
     Arabic decimal separator are accepted. A thousands separator such as
     `1,500` is refused, not guessed.
   - **No implicit FX.** Comparing prices in different currencies throws
     `CurrencyMismatch`. `priceRanges()` returns one range per currency. A
     compare-at price must use the variant's currency.
   - **Availability.** The states are IN_STOCK, OUT_OF_STOCK, PREORDER,
     BACKORDER, DISCONTINUED and UNKNOWN.
     - Accepted inputs: `availability` strings (including schema.org URLs), the
       `inStock` flag, and a quantity.
     - Contradictions are rejected: a positive quantity that is out of stock,
       discontinued or on backorder; zero in stock; the flag against the state.
     - Missing data is UNKNOWN.
   - **Weight.** Stored in kilograms. Conversion from g or lb is DERIVED and
     records the factor.
5. **Semantic attributes (QB-096).**
   - **Kinds.** An `AttributeValue` is TEXT, NUMBER or BOOLEAN.
   - **Certainty.** Values read from the source are CERTAIN. An inferable
     attribute (color) that the source states nowhere, neither on the product
     nor on any variant, is read from the title using whole-word alias phrases:
     - one value found: INFERRED and UNCERTAIN;
     - several values found: AMBIGUOUS, with alternatives and no value.

     No numeric confidence is invented. An inferred value never overrides a
     stated one and is not projected into the version 1 contract or the
     embedding text.
6. **Product text (QB-097).** `ProductTextTemplate` version
   `commerce-product-text/1` renders labelled lines in a fixed order: title,
   brand, category path, asserted attributes sorted by name, distinct asserted
   variant options sorted, and the first 1000 code points of the description.
   - **Determinism.** The fingerprint is SHA-256 over the template id and the
     text. The same product always gives the same text and fingerprint,
     whatever the field order or observation time.
   - **Exclusions.** Price, currency, stock, identifiers and provenance are left
     out. They change often and filters already match them exactly.
   - **Sync.** Catalog sync now embeds this text once per product, not once per
     variant. It stores `productTextTemplate` and `productTextFingerprint` on
     each indexed document, so a template change can be detected and
     re-embedded.
7. **Compatibility.** `CatalogNormalizer.normalize` and
   `POST /api/v2/catalog/normalize` keep the version 1 `CatalogProduct` shape,
   projected from the canonical product by `LegacyCatalogView`:
   - Only asserted attributes are projected.
   - DISCONTINUED → OUT_OF_STOCK.
   - PREORDER and BACKORDER → UNKNOWN, since version 1 cannot express them and
     must not claim stock.

   The new endpoint `POST /api/v2/catalog/canonical?connector=` (requires
   `catalog:write`) previews the canonical product, price ranges and product
   text, and stores nothing.
8. **Tests (QB-098).**
   - **Pack (14):**
     - provenance on every normalized value;
     - alias precedence in both orders;
     - exact money per currency, mismatch errors, Persian digits and thousands
       separators;
     - availability mapping and four contradictions;
     - derived weights;
     - inference: uncertain, ambiguous, blocked by a stated option, whole words
       only;
     - typed mapping rejections with paths;
     - Shopify raw nodes: store currency, oversold stock, missing inventory,
       placeholder option;
     - registry immutability;
     - vocabulary conflicts;
     - the exact version 1 text layout;
     - determinism, price and stock independence, code-point truncation.
   - **Server:**
     - the REST preview, the unchanged version 1 contract, 400 and 403;
     - Shopify adapter to canonical;
     - sync documents carry the template id.

## Consequences

- **Behaviour changes:**
  - Known brands are written in their canonical spelling (`NIKE` → `Nike`).
  - Attribute-name aliases are merged (`colour` → `color`).
  - Shopify variants keep all their options.
  - Indexed documents gain the two product-text fields.
  - A non-object `attributes` value and invalid dates or quantities now return
    typed 400 errors; before, they were ignored or raised an unchecked error.
- **Re-embedding.** Documents indexed before this change were embedded from a
  different text. They are re-embedded on their next sync. The fingerprint makes
  stale embeddings detectable.
- **Inference limits.** Inference matches whole words only, so a title in a
  script written without spaces (Chinese, Japanese) yields no inference.
- **Next.** Commerce retrieval (QB-085 – QB-091) indexes canonical products
  through the unified SPI and the planner.
