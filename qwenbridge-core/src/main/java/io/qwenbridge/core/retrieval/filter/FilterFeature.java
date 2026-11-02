package io.qwenbridge.core.retrieval.filter;

/** Filter capabilities a provider declares and a plan requires (QB-103). */
public enum FilterFeature {
  EQUALITY,
  RANGE,
  IN,
  CONTAINS,
  EXISTS,
  NOT,
  OR
}
