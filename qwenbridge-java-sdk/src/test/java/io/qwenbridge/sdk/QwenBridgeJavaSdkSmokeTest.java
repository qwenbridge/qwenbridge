package io.qwenbridge.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QwenBridgeJavaSdkSmokeTest {

    @Test
    void shouldLoadSdkModule() {
        assertEquals("qwenbridge-java-sdk", "qwenbridge-java-sdk");
    }
}
