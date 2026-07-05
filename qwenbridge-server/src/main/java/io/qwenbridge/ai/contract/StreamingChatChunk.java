package io.qwenbridge.ai.contract;

public record StreamingChatChunk(String content, boolean done) {}
