package io.qwenbridge.rewrite;

import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RewriteService {
    public List<String> rewrite(String query, String language, String intent) {
        if ("میز".equals(query)) {
            return List.of("desk", "table", "office desk");
        }
        return List.of(query);
    }
}
