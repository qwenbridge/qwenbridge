package io.qwenbridge.api;

import io.qwenbridge.model.SearchAnalyzeRequest;
import io.qwenbridge.model.SearchAnalyzeResponse;
import io.qwenbridge.pipeline.SearchPipeline;
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
