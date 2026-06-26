# REST API

## Overview

The QwenBridge REST API exposes the AI-native search pipeline through a
single endpoint.

The pipeline performs:

-   Language detection
-   Intent analysis
-   Policy evaluation
-   Threat analysis
-   Query rewrite
-   Semantic analysis
-   AI decision making
-   Execution plan generation
-   Execution execution
-   Confidence scoring
-   Pipeline tracing

------------------------------------------------------------------------

# Analyze Search Query

**Method**

``` http
POST /api/search/analyze
```

**Content-Type**

``` text
application/json
```

------------------------------------------------------------------------

# Request

``` json
{
  "query": "wireless gaming mouse"
}
```

------------------------------------------------------------------------

# Processing Pipeline

``` text
Query
 │
 ▼
Language
 │
Intent
 │
Policy
 │
Threat
 │
Rewrite
 │
Semantic
 │
Decision
 │
Execution Plan
 │
Execution Engine
 │
Execution Result
```

------------------------------------------------------------------------

# Successful Response

``` json
{
  "requestId": "uuid",
  "processingTimeMs": 14,
  "originalQuery": "wireless gaming mouse",
  "language": "en",
  "intent": {},
  "rewrite": {},
  "semantic": {},
  "decision": {},
  "executionPlan": {
    "steps": [
      {
        "order": 1,
        "operation": "VECTOR_SEARCH",
        "description": "Search vector index"
      }
    ]
  },
  "executionResult": {
    "executed": true,
    "operations": [
      "VECTOR_SEARCH"
    ],
    "results": [
      "vector-search-placeholder-result"
    ],
    "reason": "Execution plan executed successfully."
  },
  "confidence": {},
  "pipelineTrace": []
}
```

------------------------------------------------------------------------

# Decision Types

-   DIRECT_ANSWER
-   KEYWORD_SEARCH
-   VECTOR_SEARCH
-   HYBRID_SEARCH

------------------------------------------------------------------------

# Execution Operations

-   DIRECT_ANSWER
-   KEYWORD_SEARCH
-   VECTOR_SEARCH
-   HYBRID_SEARCH
-   FACET
-   RERANK
-   RETURN_RESULTS

------------------------------------------------------------------------

# Validation

Invalid request:

``` json
{
  "query": ""
}
```

Returns:

``` text
HTTP 400 Bad Request
```

------------------------------------------------------------------------

# HTTP Status Codes

    Status Description
  -------- -------------------------
       200 Successful request
       400 Validation failed
       500 Unexpected server error

------------------------------------------------------------------------

# Future Endpoints

-   POST /api/search/analyze
-   POST /api/search/chat
-   POST /api/search/rewrite
-   POST /api/search/semantic
