package io.qwenbridge.ranking.policy;

import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.ranking.model.RankingScore;

public interface RankingPolicy {

  RankingScore score(SearchHit hit);
}
