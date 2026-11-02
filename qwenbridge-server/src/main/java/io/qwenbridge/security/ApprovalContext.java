package io.qwenbridge.security;

import jakarta.enterprise.context.RequestScoped;
import java.util.Optional;

/** The approval id the client sent with this request ({@value #HEADER}), if any (QB-075). */
@RequestScoped
public class ApprovalContext {

  public static final String HEADER = "X-QwenBridge-Approval";

  private String approvalId;

  public Optional<String> approvalId() {
    return Optional.ofNullable(approvalId);
  }

  void set(String approvalId) {
    this.approvalId = approvalId;
  }
}
