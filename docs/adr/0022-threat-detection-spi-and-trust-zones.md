# ADR 0022: Threat Detection SPI, Severity Policy and Trust Zones

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 10: QB-064 – QB-070)

## Context

Threat detection existed only in the server:

- It used 12 YAML pattern detectors and correlation rules.
- Each rule carried a hand-set `score` and `confidence`. The scores, not the
  severities, decided the outcome (≥ 0.70 blocks, ≥ 0.30 needs review).
- A missing or empty rule file loaded zero rules silently (audit S4).
- Rules covered English only.
- There was no notion of where a text came from: retrieved documents reached
  the model reranker without inspection.
- There was no measurement of detection quality.

## Decision

1. **SPI (QB-064).** `core.threat` defines a domain-neutral contract:

   | Type | Holds |
   |---|---|
   | `ThreatDetector` | `id`, `source` (RULE or MODEL), `detect(ThreatInput)` |
   | `ThreatInput` | the text, its `TrustZone` and an optional language |
   | `ThreatSignal` | category, severity, typed `Confidence`, source, detector, rule, evidence |
   | `ThreatAction` | `ALLOW`, `REVIEW`, `QUARANTINE`, `BLOCK` |
   | `ThreatAssessment` | the action, all signals, the model status, failed detectors |

   - Evidence is the detector's description and never a copy of the input.
   - Detectors report signals only; `ThreatPolicy` decides the action.
   - An action is a content-safety decision, never an authorization: `ALLOW`
     means "nothing requires action". Permission is decided by the policy
     engine in Phase 11.
2. **Rule baseline (QB-065).** Rules are mandatory and fail closed:
   - A `ThreatEngine` cannot be built without rule detectors.
   - A failing detector blocks user input (or quarantines content).
   - A missing, empty or invalid rule or correlation file is an error, not
     zero rules (audit S4).
   - Core adds `PromptInjectionDetector`: instruction override and
     system-prompt disclosure in English, Swedish and Persian, plus chat
     template and role markers.
   - The server registers the Swedish, Persian and marker rules alongside the
     YAML rules (`MultilingualPromptInjectionDetector`).
3. **Model assistance (QB-066).** A model detector is optional. Its signals pass
   through the same policy and can only make the action stricter. A model
   signal that claims to be a rule is rejected. In the v1 pipeline, the model
   safety decision (`modelThreatDecision`) becomes a MODEL signal with
   `MODEL_DECISION` confidence.
4. **Trust zones (QB-067).** `TrustZone` has four values: `USER_INPUT`,
   `RETRIEVED_CONTENT`, `TOOL_OUTPUT` and `MODEL_OUTPUT`. Only operator-authored
   instructions are trusted, and they are not inputs. The boundary does not
   depend on detection: untrusted text reaches models only as quoted JSON data,
   with constrained output. Detection adds quarantine on top:
   - The Commerce reranker scans every retrieved document in
     `RETRIEVED_CONTENT`.
   - A flagged document is kept out of the model prompt. It keeps its baseline
     position after the model-ranked documents.
   - A role line ("system: …") counts only in content, never in a user's own
     query.
5. **Severity and confidence (QB-068).**
   - `ThreatPolicy` maps zone and severity to an action. The default policy
     reproduces the released outcomes exactly: in user input HIGH and CRITICAL
     block, MEDIUM needs review, LOW is allowed; in other zones MEDIUM and above
     are quarantined. Every shipped rule's score matched its severity's
     threshold, so the change is behaviour-neutral.
   - The policy is configurable, but its mandatory floors cannot be lowered:
     HIGH and CRITICAL always block user input and always at least quarantine
     content.
   - Hand-set YAML confidences are reported as `HEURISTIC` with no number. Only
     a real model score may carry one.
   - Correlations still escalate.
6. **Model unavailable (QB-069).** Rules decide alone and the assessment says
   so:

   | `modelStatus` | Meaning |
   |---|---|
   | `NOT_CONFIGURED` | No model is configured. |
   | `UNAVAILABLE` (`degraded: true`) | A model is configured but failed. |
   | `NOT_EVALUATED` | Rules blocked first, so the model was never asked. |

   The pipeline's separate execution control still stops filters the model was
   supposed to check. That is not a threat decision. The v1 response carries
   `threatAssessment`.
7. **Measurement (QB-070).** The seed set `threat/threat-eval-v1.jsonl` has 58
   attacks and 60 ordinary queries in en/sv/fa, plus mixed and other
   languages. `ThreatEvaluationTest` runs the production rule pipeline with no
   model and writes `target/threat-eval-report.json`.
   - **Before fixes:** precision 0.946, recall 0.914, FPR 0.050, FNR 0.086.
   - **After fixing two English article gaps and scoping the role-line rule:**
     precision 0.965, recall 0.948, FPR 0.033, FNR 0.052. These fixes were
     informed by this set, so the after-numbers are not an unbiased estimate.
   - **Remaining misses:** `admin' OR '1'='1`, `&& rm -rf /`, and full-width SQL
     (canonicalization removes the full-width evidence before rules run).
   - **Remaining false positives:** "act as a guide…" and "C# programming book".
   - Regression floors sit just below the measured values. They are not quality
     claims; real-world rates need a representative, independently labelled
     dataset.

## Consequences

- Detection is pluggable: a model or a classifier can be added through the SPI
  without changing the safety properties.
- A broken rules deployment now fails at load time rather than silently
  allowing everything.
- Swedish and Persian instruction-override queries are now blocked.
- Retrieved documents with injection markers no longer reach the reranking
  model.
