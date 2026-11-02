package io.qwenbridge.pack;

import java.util.*;

public interface ConnectorPack extends QwenBridgePack {
  record Page(List<Map<String, Object>> documents, String nextCursor) {
    public Page {
      documents = List.copyOf(documents.stream().map(Map::copyOf).toList());
    }
  }

  Set<String> modes();

  Page search(
      String domain,
      String text,
      List<DomainPack.Clause> clauses,
      String mode,
      String cursor,
      int limit);
}
