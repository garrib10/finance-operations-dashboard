package dev.portfolio.finance.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerMapping;
import jakarta.servlet.FilterChain;

@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdFilterTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void noLeftovers() {
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "req-123_abc.DEF", "0123456789012345678901234567890123456789012345678901234567890123"})
    void keepsASafeInboundId(String inbound) {
        assertThat(CorrelationIdFilter.accept(inbound)).isEqualTo(inbound);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "has space", "tab\there", "line\nbreak", "carriage\rreturn", "nul\u0000byte",
            "semi;colon", "quote\"d", "<script>", "slash/path", "colon:id", "café", "😀",
            "01234567890123456789012345678901234567890123456789012345678901234"})
    void replacesAnUnsafeOrMissingIdWithANewUuid(String inbound) {
        String accepted = CorrelationIdFilter.accept(inbound);

        assertThat(accepted).isNotEqualTo(inbound).matches(UUID_PATTERN);
    }

    @Test
    void generatesADifferentIdForEachRequest() {
        assertThat(CorrelationIdFilter.accept(null)).isNotEqualTo(CorrelationIdFilter.accept(null));
    }

    @Test
    void putsTheIdInTheLogContextDuringTheRequestAndTheResponseHeaderAndClearsItAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(CorrelationIdFilter.HEADER, "inbound-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(seen.get()).isEqualTo("inbound-42");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("inbound-42");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsTheLogContextAfterAHandledErrorAndLogsItOnceWithoutTheMessage(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/transactions/5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/transactions/{id}");
            req.setAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE,
                    new IllegalStateException("PRIVATE jdbc:mysql://host user=secret"));
            ((MockHttpServletResponse) res).setStatus(503);
        };

        filter.doFilter(request, response, chain);

        // (The correlation ID itself is in the log context, printed by the application's log
        // pattern; see CorrelationIdIntegrationTest.)
        assertThat(output).contains("http.request.failed").contains("IllegalStateException")
                .contains("/api/transactions/{id}").contains("503")
                .doesNotContain("PRIVATE", "jdbc:", "secret", "/api/transactions/5");
        assertThat(output.toString().split("http.request.failed", -1)).hasSize(2); // Logged once.
    }

    @Test
    void clearsTheLogContextAfterAnUnhandledFailureAndStillRethrowsIt(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/budgets/9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> filter.doFilter(request, response,
                (req, res) -> {
                    throw new IllegalArgumentException("PRIVATE amount 123.45");
                }));

        assertThat(thrown).hasMessageContaining("PRIVATE"); // Unchanged for the container.
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).matches(UUID_PATTERN);
        assertThat(output).contains("http.request.failed").contains("IllegalArgumentException")
                .contains(CorrelationIdFilter.UNMATCHED_ROUTE).contains("500")
                .doesNotContain("PRIVATE", "123.45", "/api/budgets/9");
    }

    @Test
    void writesNothingForAnOrdinaryRequestAtProductionLevels(CapturedOutput output) throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/api/health"), new MockHttpServletResponse(),
                (req, res) -> { });

        assertThat(output).doesNotContain("http.request.completed", "http.request.failed");
    }

    @Test
    void sequentialRequestsNeverShareAnId() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();
        AtomicReference<String> secondSaw = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), first, (req, res) -> { });
        filter.doFilter(new MockHttpServletRequest(), second,
                (req, res) -> secondSaw.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(secondSaw.get()).isEqualTo(second.getHeader(CorrelationIdFilter.HEADER))
                .isNotEqualTo(first.getHeader(CorrelationIdFilter.HEADER));
    }

    @Test
    void redactsEveryMessageInAnExceptionChainButKeepsClassesAndFrames() {
        IllegalStateException original = new IllegalStateException("PRIVATE outer",
                new IllegalArgumentException("PRIVATE inner"));

        Throwable redacted = CorrelationIdFilter.redacted(original, 0);

        assertThat(redacted).hasMessage("java.lang.IllegalStateException (message removed)");
        assertThat(redacted.getStackTrace()).isEqualTo(original.getStackTrace());
        assertThat(redacted.getCause()).hasMessage("java.lang.IllegalArgumentException (message removed)");
        assertThat(redacted.getCause().getCause()).isNull();
    }

    @Test
    void stopsAtACycleOrTenLevels() {
        RuntimeException deep = new RuntimeException("PRIVATE 0");
        for (int i = 1; i < 15; i++) deep = new RuntimeException("PRIVATE " + i, deep);

        Throwable redacted = CorrelationIdFilter.redacted(deep, 0);
        int levels = 0;
        for (Throwable t = redacted; t != null; t = t.getCause()) {
            assertThat(t.getMessage()).doesNotContain("PRIVATE");
            levels++;
        }
        assertThat(levels).isEqualTo(11);
    }
}
