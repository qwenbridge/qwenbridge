# Class Diagrams

These diagrams were generated from the source on 2026-11-02 (QB-287). They show the record
components, interface methods and enum constants as declared. Private helpers and most nested
types are left out. Package prefixes are shortened: `core.` stands for `io.qwenbridge.core.`, and
server packages are written without `io.qwenbridge.`.

## 1. QueryIR 1.1 (`core.queryir`)

```mermaid
classDiagram
    class QueryIR {
      <<record>>
      IrVersion version
      Optional~String~ queryId
      String originalText
      LanguageTag language
      List~Intent~ intents
      List~Entity~ entities
      List~Constraint~ constraints
      List~SemanticSignal~ semanticSignals
      RetrievalHints retrievalHints
      List~SubQuery~ subqueries
      Map~String,Extension~ extensions
      Optional~TextCorrection~ correction
      Optional~QueryExpansion~ expansion
    }
    class Constraint {
      <<record>>
      String id
      String field
      Operator operator
      Optional~IrValue~ value
      Strength strength
      Provenance provenance
      String reason
      Optional~Span~ span
      Confidence confidence
      Optional~String~ scope
    }
    class Intent {
      <<record>>
      String id
      String type
      Confidence confidence
      Optional~Span~ span
    }
    class Operator {
      <<enum>>
      EQ NE LT LTE GT GTE BETWEEN
      IN NOT_IN CONTAINS NOT_CONTAINS EXISTS NOT_EXISTS
    }
    class Strength {
      <<enum>>
      HARD
      SOFT
    }
    class Provenance {
      <<enum>>
      USER_TEXT
      CONTEXT
      INFERRED
      DOMAIN_RULE
    }
    QueryIR "1" *-- "*" Intent
    QueryIR "1" *-- "*" Constraint
    QueryIR "1" *-- "*" Entity
    QueryIR "1" *-- "*" SubQuery
    QueryIR --> TextCorrection
    QueryIR --> QueryExpansion
    Constraint --> Operator
    Constraint --> Strength
    Constraint --> Provenance
    Constraint --> Span
    QueryIrValidator ..> QueryIR : validates
    QueryIrJson ..> QueryIR : reads and writes
```

`Span` points into `originalText`. A `HARD` constraint with provenance `USER_TEXT` must quote the
query. This rule is what "grounded" means.

## 2. Language detection

```mermaid
classDiagram
    class LanguageDetector {
      <<interface>>
      String id()
      LanguageDetection detect(String text)
    }
    class ScriptHeuristicLanguageDetector
    class LanguageDetection {
      <<record>>
      LanguageTag language
      Confidence confidence
      String detector
    }
    class LanguageTag {
      <<record>>
      String value
      UNDETERMINED$
    }
    class LanguageService {
      <<server · language>>
      LanguageAnalysis analyze(String, Optional~LanguageTag~)
    }
    class LanguageAnalysis {
      <<record · server>>
      LanguageDetection detection
      Optional~LanguageTag~ declared
      String primaryLanguage
      boolean mixed
      List~SegmentLanguage~ segments
      TextDirection direction
      boolean configuredLanguage
    }
    LanguageDetector <|.. ScriptHeuristicLanguageDetector
    LanguageDetector ..> LanguageDetection
    LanguageDetection --> LanguageTag
    LanguageService --> LanguageDetector : model and heuristic
    LanguageService ..> LanguageAnalysis
    LanguageStep --> LanguageService
```

## 3. Typo correction (`core.spelling`, server `spelling`)

```mermaid
classDiagram
    class CorrectionProvider {
      <<interface>>
      Descriptor descriptor()
      boolean knows(LanguageTag, String word)
      List~Candidate~ candidates(LanguageTag, String word, double maxDistance)
      boolean pair(LanguageTag, String, String)
    }
    class DictionaryCorrectionProvider
    class Lexicon
    class EditDistance
    class ProtectedTokens
    class TypoCorrector
    class TypoSettings {
      <<record>>
      boolean enabled
      List~String~ languages
      CorrectionMode mode
      double safeThreshold
      double aggressiveThreshold
    }
    class CorrectionMode {
      <<enum>>
      DISABLED
      SUGGEST_ONLY
      SAFE_AUTO_CORRECT
      AGGRESSIVE
    }
    class CorrectionResult {
      <<record>>
      Status status
      CorrectionMode mode
      String original
      String normalized
      String corrected
      List~LanguageSupport~ languages
      Optional~LanguageTag~ detectedLanguage
      List~String~ activeLanguages
      List~TokenCorrection~ corrections
      int protectedTokens
      List~String~ explanations
    }
    class TypoService {
      <<server>>
    }
    class TypoConfiguration {
      <<server>>
      TypoSettings application
      Map~String,TypoSettings~ tenants
      List~CorrectionProvider~ providers
    }
    CorrectionProvider <|.. DictionaryCorrectionProvider
    DictionaryCorrectionProvider --> Lexicon : per language and domain
    TypoCorrector --> CorrectionProvider
    TypoCorrector --> ProtectedTokens
    TypoCorrector --> EditDistance
    TypoCorrector ..> CorrectionResult
    TypoSettings --> CorrectionMode
    TypoService --> TypoConfiguration
    TypoService --> TypoCorrector
```

## 4. Multilingual expansion and translation

```mermaid
classDiagram
    class TranslationProvider {
      <<interface>>
      String id()
      TranslationCapabilities capabilities()
      TranslationResult translate(TranslationRequest)
      ProviderAvailability availability()
      Map~String,Double~ vocabularyEvidence(String)
    }
    class GlossaryTranslationProvider
    class ModelTranslationProvider {
      <<server · translation>>
    }
    class TranslationStage
    class QueryExpander
    class TargetLanguages
    class ExpansionSettings {
      <<record>>
      boolean enabled
      List~String~ targetLanguages
      ExpansionPolicy policy
      Map~String,String~ aliases
      double minConfidence
    }
    class ExpansionPolicy {
      <<enum>>
      ALL_TARGETS
      STRICT_ALL_TARGETS
      BEST_EFFORT_ALL_TARGETS
    }
    class ExpansionResult {
      <<record>>
      Status status
      ExpansionPolicy policy
      String original
      String sourceText
      SourceLanguage source
      List~TargetLanguage~ configured
      List~TargetResult~ targets
      List~String~ retrievalTexts
      long elapsedMillis
    }
    class TargetResult {
      <<record>>
      String requested
      Optional~LanguageTag~ language
      TargetStatus status
      Optional~String~ text
      Optional~String~ provider
      Confidence confidence
      boolean identifiersPreserved
      boolean retrieval
    }
    class ExpansionRetrieval
    class ExpansionService {
      <<server · multilingual>>
    }
    TranslationProvider <|.. GlossaryTranslationProvider
    TranslationProvider <|.. ModelTranslationProvider
    TranslationStage --> TranslationProvider
    QueryExpander --> TranslationStage
    QueryExpander --> TargetLanguages
    QueryExpander --> ExpansionSettings
    QueryExpander ..> ExpansionResult
    ExpansionResult *-- TargetResult
    ExpansionRetrieval ..> ExpansionResult : searches retrievalTexts, fuses
    ExpansionService --> QueryExpander
```

`TargetStatus` has the constants `SUCCESS`, `UNCHANGED`, `LOW_CONFIDENCE`,
`UNSUPPORTED_LANGUAGE`, `UNSUPPORTED_LANGUAGE_PAIR`, `PROVIDER_UNAVAILABLE`,
`SOURCE_UNDETERMINED`, `INVALID_LANGUAGE` and `FAILED`. A target without a translation carries no
text.

## 5. Retrieval SPI (`core.retrieval`)

```mermaid
classDiagram
    class SearchProvider {
      <<interface>>
      String id()
      ProviderCapabilities capabilities()
      SearchOutcome search(SearchRequest, CancellationToken)
    }
    class SearchRequest {
      <<record>>
      String tenant
      RetrievalMode mode
      Optional~String~ text
      Optional~QueryVector~ vector
      Filter filter
      int topK
      Duration timeout
    }
    class RetrievalMode {
      <<enum>>
      KEYWORD
      VECTOR
    }
    class SearchOutcome {
      <<sealed interface>>
    }
    class Success {
      <<record>>
      List~ScoredDocument~ hits
      long matched
      Duration took
    }
    class Unsupported {
      <<record>>
      String requirement
    }
    class Failed {
      <<record>>
      Failure failure
      String detail
    }
    class ScoredDocument {
      <<record>>
      String id
      int rank
      RetrievalScore score
      Map~String,FieldValue~ fields
    }
    class Filter {
      <<interface>>
      ALL$
    }
    class RankFusion
    SearchProvider ..> SearchRequest
    SearchProvider ..> SearchOutcome
    SearchOutcome <|-- Success
    SearchOutcome <|-- Unsupported
    SearchOutcome <|-- Failed
    Success *-- ScoredDocument
    SearchRequest --> RetrievalMode
    SearchRequest --> Filter
    RankFusion ..> ScoredDocument : fuses keyword and vector lists
```

`Failed.failure` is one of `TIMEOUT`, `CANCELLED`, `UNAVAILABLE` or `INVALID_REQUEST`. Hybrid
retrieval is two `SearchRequest`s, one per mode, fused by `RankFusion`.

## 6. Commerce backends (server `connectors`)

```mermaid
classDiagram
    class CommerceBackend {
      <<interface>>
      String id()
      Set~String~ modes()
      boolean configured()
      SearchResultSet search(Query)
      void upsert(String tenant, String id, Map document)
      void delete(String tenant, String id)
      void initialize()
      boolean readOnly()
      void validate(Query)
    }
    class Query {
      <<record>>
      String text
      Plan plan
      String mode
      List~Double~ embedding
      int limit
      int offset
    }
    class CommerceBackends {
      String selected
      CommerceBackend backend(String tenant, String id)
    }
    class MemoryCommerceBackend {
      id = memory
    }
    class OpenSearchBackend {
      id = opensearch
    }
    class SearchProvider {
      <<interface · core>>
    }
    class ConnectorProviders
    class NeutralFilterTranslator {
      openSearch(Filter)
      fields(Map)
    }
    class OpenSearchFilters {
      <<package-private>>
    }
    class Entitlements {
      <<platform>>
    }
    CommerceBackend <|.. MemoryCommerceBackend
    CommerceBackend <|.. OpenSearchBackend
    SearchProvider <|.. MemoryCommerceBackend
    SearchProvider <|.. OpenSearchBackend
    CommerceBackend *-- Query
    CommerceBackends o-- CommerceBackend : memory, opensearch
    CommerceBackends --> Entitlements : edition gate
    OpenSearchBackend --> NeutralFilterTranslator
    NeutralFilterTranslator --> OpenSearchFilters
    ConnectorProviders ..> CommerceBackend : adapts to SearchProvider
```

QB-287 removed the `elasticsearch`, `postgresql` and `mongodb` implementations. Those identifiers
now give "Unknown backend".

## 7. Connector SPI (`core.connector`)

```mermaid
classDiagram
    class Connector {
      <<interface>>
      String id()
      ConfigSchema configSchema()
      ConnectorCapabilities capabilities()
      void start(ConnectorConfig, SecretResolver)
      boolean healthy()
      void stop()
      Optional~SearchProvider~ search()
    }
    class ConnectorRuntime {
      Connector connector
      CircuitBreaker breaker
      Duration timeout
      ConnectorState state
    }
    class ConnectorRegistry {
      Map~String,ConnectorRuntime~ runtimes
    }
    class CircuitBreaker
    class Resilient
    class RetryPolicy {
      <<record>>
    }
    class ConnectorResult {
      <<result>>
    }
    class ConnectorPackProvider {
      <<interface>>
      ConnectorPackManifest manifest()
      Connector create(String connectorId)
    }
    class ConnectorPacks
    class ConnectorPackManifest {
      <<record>>
    }
    class ConnectorPackService {
      <<server · extensions>>
    }
    class SecretResolver {
      <<interface>>
    }
    class EnvironmentSecrets
    ConnectorRuntime --> Connector
    ConnectorRuntime --> CircuitBreaker
    ConnectorRuntime --> Resilient
    Resilient --> RetryPolicy
    ConnectorRuntime ..> ConnectorResult
    ConnectorRegistry o-- ConnectorRuntime
    ConnectorPacks --> ConnectorPackProvider
    ConnectorPackProvider ..> Connector : creates
    ConnectorPackProvider --> ConnectorPackManifest
    ConnectorPackService --> ConnectorPacks
    SecretResolver <|.. EnvironmentSecrets
    Connector ..> SecretResolver
```

`ConnectorResult.Failure` has the constants `NOT_READY`, `ACCESS_DENIED`, `CIRCUIT_OPEN`,
`TIMEOUT` and `CANCELLED`. In the test suite the SPI is exercised through
`extensions/testpack/ExampleConnectorPack` (test scope only).

## 8. Packs and plugins (`qwenbridge-plugin-sdk`, server `platform`, `extensions`)

```mermaid
classDiagram
    class QwenBridgePack {
      <<interface>>
      PackManifest manifest()
      void start(Context)
      void close()
    }
    class DomainPack {
      <<interface>>
      Definition definition()
      Map normalize(String sourceId, Map input)
      Understanding understand(String original)
      List~Rule~ policies()
      Optional~QueryExtension~ queryExtension()
    }
    class ConnectorPack {
      <<interface>>
      Set~String~ modes()
      Page search(domain, text, clauses, mode, cursor, limit)
    }
    class PolicyPack {
      <<interface>>
    }
    class PackManifest {
      <<record>>
      String id
      String version
      int apiVersion
      Kind kind
      String publisher
      List~Dependency~ dependencies
      Set~String~ capabilities
      Map~String,ConfigField~ configuration
      Runtime runtime
    }
    class QwenBridgePlugin {
      <<interface>>
      Manifest manifest()
      Map invoke(String capability, Map input)
    }
    class CommercePack {
      <<qwenbridge-commerce-pack>>
    }
    class LibraryPack {
      <<qwenbridge-library-pack>>
    }
    class ManagedPacks {
      <<server · platform>>
    }
    class Entitlements {
      <<server · platform>>
    }
    class PlatformStore {
      <<server · platform>>
      memory | file | redis
    }
    class PluginHost {
      <<server · extensions>>
      URLClassLoader loader
    }
    class ExtensionRegistry {
      <<server · extensions>>
    }
    QwenBridgePack <|-- DomainPack
    QwenBridgePack <|-- ConnectorPack
    QwenBridgePack <|-- PolicyPack
    QwenBridgePack --> PackManifest
    DomainPack <|.. CommercePack
    DomainPack <|.. LibraryPack
    ManagedPacks --> QwenBridgePack : install, activate, rollback
    ManagedPacks --> Entitlements
    ManagedPacks --> PlatformStore
    Entitlements --> PlatformStore
    PluginHost --> QwenBridgePlugin
    PluginHost --> ExtensionRegistry
```

## 9. v2 search service (server `commerce`)

```mermaid
classDiagram
    class CommerceResource {
      <<REST /api/v2>>
      search()
      stream()
      refine()
      suggestions()
      feedback()
      recovery()
      catalogSync()
    }
    class CommerceSearchService {
      SearchResponse search(SearchRequest, Principal)
      SearchResponse searchInContext(...)
      SearchResponse refine(...)
      SearchResponse executeApproved(...)
    }
    class CommercePlanning
    class CommerceCompiler {
      validate(schema, tenant, constraints)
    }
    class CommerceInterpretation
    class CommerceReranker
    class ZeroResultRecovery
    class CommerceSuggestions
    class CommerceEvidenceStore
    class CommerceSchemaRegistry
    class CatalogSynchronization {
      synchronize(Principal)
    }
    class TypoService
    class ExpansionService
    class ThreatService
    class CommerceBackends
    CommerceResource --> CommerceSearchService
    CommerceResource --> CatalogSynchronization
    CommerceSearchService --> TypoService
    CommerceSearchService --> ExpansionService
    CommerceSearchService --> ThreatService
    CommerceSearchService --> CommerceSchemaRegistry
    CommerceSearchService --> CommerceCompiler
    CommerceSearchService --> CommerceInterpretation
    CommerceSearchService --> CommercePlanning
    CommerceSearchService --> CommerceReranker
    CommerceSearchService --> ZeroResultRecovery
    CommerceSearchService --> CommerceSuggestions
    CommerceSearchService --> CommerceEvidenceStore
    CommercePlanning --> CommerceBackends
    CatalogSynchronization --> CommerceBackends
```

`CommerceSearchService` still holds an `OpenSearchProvider` field, and `CatalogSynchronization`
holds an `OpenSearchApiClient` sink as a fallback. Both are listed in the
[remediation proposals](remediation-proposals.md#1-extract-opensearch-into-an-optional-connector-pack).

## 10. Legacy v1 pipeline (server `pipeline`)

```mermaid
classDiagram
    class PipelineStep~T~ {
      <<interface>>
      String name()
      PipelineStage stage()
      int order()
      Class~T~ resultType()
      T execute(ExecutionContext)
      boolean publishEvents()
    }
    class PipelineEngine {
      List~PipelineStep~ steps
    }
    class SearchPipeline
    class ExecutionContext
    class LanguageStep { order 10 }
    class NormalizationStep { order 15 }
    class ThreatStep { order 20 }
    class AIAnalysisStep { order 25 }
    class ModelSafetyStep { order 26 }
    class TranslationStep { order 27 }
    class ExpansionStep { order 28 }
    class IntentStep { order 30 }
    class QueryIrStep { order 31 }
    class ConstraintValidationStep { order 32 }
    class RewriteStep { order 40 }
    class SemanticStep { order 50 }
    class PolicyStep { order 70 }
    class DecisionStep { order 80 }
    class ConfidenceStep { order 90 }
    SearchPipeline --> PipelineEngine
    PipelineEngine o-- PipelineStep : sorted by order
    PipelineEngine --> ExecutionContext
    PipelineStep <|.. LanguageStep
    PipelineStep <|.. NormalizationStep
    PipelineStep <|.. ThreatStep
    PipelineStep <|.. AIAnalysisStep
    PipelineStep <|.. ModelSafetyStep
    PipelineStep <|.. TranslationStep
    PipelineStep <|.. ExpansionStep
    PipelineStep <|.. IntentStep
    PipelineStep <|.. QueryIrStep
    PipelineStep <|.. ConstraintValidationStep
    PipelineStep <|.. RewriteStep
    PipelineStep <|.. SemanticStep
    PipelineStep <|.. PolicyStep
    PipelineStep <|.. DecisionStep
    PipelineStep <|.. ConfidenceStep
```

The steps are CDI beans collected with `@All` and run in `order()` sequence. QB-287 removed the
orphaned per-step AI services: `DecisionService`, `IntentService`, `RewriteService`,
`SemanticService` and their `ai` implementations. No step injected them; the steps take their
results from `AIAnalysisStep`.
