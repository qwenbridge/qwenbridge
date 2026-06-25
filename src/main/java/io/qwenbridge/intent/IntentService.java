package io.qwenbridge.intent;

import org.springframework.stereotype.Service;

@Service
public class IntentService {
    public String detect(String query) {
        return "PRODUCT_SEARCH";
    }
}
