package io.omnisearch.api;

import io.omnisearch.model.SearchAnalyzeRequest;
import io.omnisearch.model.SearchAnalyzeResponse;
import io.omnisearch.pipeline.SearchPipeline;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
public class SearchAnalyzeController {

    private final SearchPipeline searchPipeline;

    public SearchAnalyzeController(SearchPipeline searchPipeline) {
        this.searchPipeline = searchPipeline;
    }

    @PostMapping("/analyze")
    public SearchAnalyzeResponse analyze(@Valid @RequestBody SearchAnalyzeRequest request) {
        return searchPipeline.analyze(request);
    }
}
