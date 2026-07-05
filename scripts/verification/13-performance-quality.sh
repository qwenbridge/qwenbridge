#!/usr/bin/env bash

v9_quality_performance_scripts_validation() {
  [[ -f "scripts/performance/v9-k6-load-test.js" ]] \
    && [[ -f "scripts/performance/k6-security-input-test.js" ]] \
    && [[ -f "scripts/performance/k6-language-quality-test.js" ]] \
    && [[ -f "scripts/performance/k6-retrieval-quality-test.js" ]] \
    && [[ -f "scripts/performance/k6-resilience-test.js" ]] \
    && grep -q "security_inputs" scripts/performance/k6-security-input-test.js \
    && grep -q "language_quality" scripts/performance/k6-language-quality-test.js \
    && grep -q "retrieval_quality" scripts/performance/k6-retrieval-quality-test.js \
    && grep -q "api_resilience" scripts/performance/k6-resilience-test.js
}
