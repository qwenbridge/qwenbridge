# Pipeline

The pipeline executes independent processing steps.

User Query

↓

LanguageStep

↓

IntentStep

↓

ThreatStep

↓

RewriteStep

↓

SemanticStep

↓

PolicyStep

↓

ConfidenceStep

↓

DecisionStep

↓

Search Engine

Every step returns its own Result object.

ExecutionContext stores results by type.

PipelineEngine never knows internal business logic.
