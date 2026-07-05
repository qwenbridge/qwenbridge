package io.qwenbridge.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class QwenBridgeJavaSdkSmokeTest {

  @Test
  void shouldLoadSdkModule() {
    assertEquals("qwenbridge-java-sdk", "qwenbridge-java-sdk");
  }
}
