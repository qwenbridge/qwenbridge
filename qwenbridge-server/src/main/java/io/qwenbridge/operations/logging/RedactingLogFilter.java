package io.qwenbridge.operations.logging;

import io.quarkus.logging.LoggingFilter;
import io.qwenbridge.core.security.Sanitizer;
import java.util.logging.Filter;
import java.util.logging.LogRecord;
import org.jboss.logmanager.ExtLogRecord;

/**
 * Masks secrets and neutralizes log-line forgery in every log message before a handler writes it
 * (QB-060, QB-061). Enabled with {@code quarkus.log.console.filter=qwenbridge-redaction}.
 *
 * <p>The formatted message is replaced by its {@link Sanitizer.Sink#LOG} form: credentials, tokens
 * and card numbers masked; line breaks and other control or bidirectional characters escaped;
 * length bounded. Exception stack traces are written by the handler and are not rewritten here;
 * exception messages must therefore never carry secrets (error responses never include them).
 */
@LoggingFilter(name = RedactingLogFilter.NAME)
public final class RedactingLogFilter implements Filter {

  public static final String NAME = "qwenbridge-redaction";

  @Override
  public boolean isLoggable(LogRecord record) {
    String formatted =
        record instanceof ExtLogRecord ext ? ext.getFormattedMessage() : record.getMessage();
    if (formatted == null) {
      return true;
    }
    String safe = Sanitizer.forSink(Sanitizer.Sink.LOG, formatted);
    if (record instanceof ExtLogRecord ext) {
      ext.setMessage(safe, ExtLogRecord.FormatStyle.NO_FORMAT);
    } else {
      record.setMessage(safe);
    }
    record.setParameters(null);
    return true;
  }
}
