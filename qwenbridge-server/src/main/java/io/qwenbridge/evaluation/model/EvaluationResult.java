package io.qwenbridge.evaluation.model;

public record EvaluationResult(
    int queryCount,
    double precisionAtK,
    double recallAtK,
    double meanReciprocalRank,
    double ndcgAtK) {}
