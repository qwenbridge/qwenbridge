package io.qwenbridge.streaming.event;

import io.qwenbridge.event.model.PipelineEventType;
import io.qwenbridge.event.model.PipelineStage;
import org.springframework.stereotype.Component;

@Component
public class PipelineEventTerminalPolicy {

  public boolean isTerminal(PipelineStage stage, PipelineEventType type) {
    if (stage != PipelineStage.PIPELINE) {
      return false;
    }

    return type == PipelineEventType.COMPLETED
        || type == PipelineEventType.FAILED
        || type == PipelineEventType.STOPPED;
  }

  public boolean isFailure(PipelineStage stage, PipelineEventType type) {
    return stage == PipelineStage.PIPELINE && type == PipelineEventType.FAILED;
  }
}
