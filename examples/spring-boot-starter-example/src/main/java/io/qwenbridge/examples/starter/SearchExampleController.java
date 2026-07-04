package io.qwenbridge.examples.starter;

import io.qwenbridge.sdk.QwenBridgeClient;
import io.qwenbridge.sdk.search.SearchAnalyzeRequest;
import io.qwenbridge.sdk.search.SearchAnalyzeResponse;
import io.qwenbridge.sdk.streaming.QwenBridgeStreamingClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/example")
public class SearchExampleController {

    private final QwenBridgeClient client;
    private final QwenBridgeStreamingClient streamingClient;

    public SearchExampleController(
            QwenBridgeClient client,
            QwenBridgeStreamingClient streamingClient
    ) {
        this.client = client;
        this.streamingClient = streamingClient;
    }

    @GetMapping("/analyze")
    public SearchAnalyzeResponse analyze(@RequestParam String query) {
        return client.analyze(
                SearchAnalyzeRequest.withRequestId(
                        UUID.randomUUID().toString(),
                        query
                )
        );
    }

    @GetMapping("/beans")
    public Map<String, String> beans() {
        return Map.of(
                "client", client.getClass().getName(),
                "streamingClient", streamingClient.getClass().getName()
        );
    }
}
