# REST API

## POST /api/v1/search/analyze

Analyzes a search-box query and returns the pipeline decision.

## Request

Method: POST

Path:

    /api/v1/search/analyze

Content-Type:

    application/json

Body:

    {
      "query": "میز"
    }

## Successful Response

    {
      "requestId": "uuid",
      "processingTimeMs": 1,
      "originalQuery": "میز",
      "language": "fa",
      "intent": "PRODUCT_SEARCH",
      "decision": "REWRITE",
      "confidence": 0.94,
      "rewrites": ["desk", "table", "office desk"],
      "threatReasons": [],
      "semanticValidated": true,
      "semanticScore": 0.96,
      "policyPassed": true,
      "policyViolations": [],
      "pipelineTrace": []
    }

## Decision Types

- ALLOW
- REWRITE
- CLARIFY
- BLOCK

## Threat Example

Request:

    {
      "query": "desk union select password from users"
    }

Expected result:

    {
      "decision": "BLOCK",
      "threatReasons": ["SQL_INJECTION"]
    }

## Validation

Blank query returns HTTP 400.
