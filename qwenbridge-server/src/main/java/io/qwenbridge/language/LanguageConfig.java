package io.qwenbridge.language;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.List;

/** Language processing configuration, {@code qwenbridge.language.*} (QB-004, QB-008). */
@ConfigMapping(prefix = "qwenbridge.language")
public interface LanguageConfig {

  /**
   * Languages the deployment is tuned and evaluated for, as BCP 47 tags. This does not restrict
   * input: any valid language is accepted and detected; it only marks results as configured.
   */
  @WithDefault("en,fa,sv")
  List<String> configured();

  /**
   * Whether the statistical model (Lingua) is used. When false, or when the model fails, the
   * deterministic script-based detector is used and ambiguous text stays undetermined.
   */
  @WithDefault("true")
  boolean modelEnabled();
}
