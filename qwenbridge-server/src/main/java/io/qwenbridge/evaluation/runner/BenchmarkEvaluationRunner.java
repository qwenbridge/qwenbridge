package io.qwenbridge.evaluation.runner;

import io.qwenbridge.evaluation.model.BenchmarkEvaluationReport;

import java.io.Reader;
import java.util.List;
import java.util.Map;

public interface BenchmarkEvaluationRunner {

    BenchmarkEvaluationReport run(
            Reader benchmarkReader,
            Map<String, List<String>> rankedResultsByQueryId,
            int k
    );
}
