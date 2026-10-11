package dev.portfolio.finance.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The production log format (logging.structured.format.console=logstash, issue #20): each
 * event, stack trace included, is one JSON object with the level, logger, message, the
 * correlation ID, and the request's key-value fields.
 */
class StructuredLoggingFormatTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private JsonNode encode(LoggingEvent event) {
        LoggerContext context = new LoggerContext();
        context.putObject(Environment.class.getName(), new MockEnvironment());
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setFormat("logstash");
        encoder.setContext(context);
        encoder.start();
        String line = new String(encoder.encode(event), StandardCharsets.UTF_8);
        assertThat(line.strip()).doesNotContain("\n"); // One event, one line.
        return mapper.readTree(line);
    }

    private static LoggingEvent event(Level level, String message, Throwable error) {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger(CorrelationIdFilter.class);
        LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, level, message, error, null);
        event.setMDCPropertyMap(Map.of(CorrelationIdFilter.MDC_KEY, "req-123"));
        return event;
    }

    @Test
    void writesTheLevelLoggerMessageCorrelationIdAndRequestFields() {
        LoggingEvent event = event(Level.ERROR, "http.request.failed", null);
        event.addKeyValuePair(new KeyValuePair("route", "/api/budgets/{id}"));
        event.addKeyValuePair(new KeyValuePair("status", 500));
        event.addKeyValuePair(new KeyValuePair("durationMs", 12L));

        JsonNode json = encode(event);

        assertThat(json.get("level").asString()).isEqualTo("ERROR");
        assertThat(json.get("logger_name").asString()).isEqualTo(CorrelationIdFilter.class.getName());
        assertThat(json.get("message").asString()).isEqualTo("http.request.failed");
        assertThat(json.get(CorrelationIdFilter.MDC_KEY).asString()).isEqualTo("req-123");
        assertThat(json.get("route").asString()).isEqualTo("/api/budgets/{id}");
        assertThat(json.has("@timestamp")).isTrue();
    }

    @Test
    void keepsAStackTraceInsideTheSameJsonLine() {
        JsonNode json = encode(event(Level.ERROR, "failure", new IllegalStateException("boom")));

        assertThat(json.get("stack_trace").asString()).contains("IllegalStateException").contains("\n\tat ");
    }
}
