package dev.portfolio.finance.monitoring;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.HandlerMapping;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gives every request one correlation ID (issue #20) and logs failed requests safely.
 *
 * <p>The ID comes from the {@value #HEADER} request header when it is 1–64 characters of
 * {@code A-Z a-z 0-9 . _ -}; anything else (missing, blank, too long, spaces, control
 * characters, other punctuation, non-ASCII) is ignored and a random UUID is used instead.
 * The request itself is never rejected for it. The ID is returned in the same response
 * header, including on errors, and is in the logging context ({@value #MDC_KEY}) for every
 * log line written while the request runs. It is removed in {@code finally}, so a reused
 * thread never carries it into the next request. It is never a credential and never a
 * metric tag.
 *
 * <p>Runs before Spring Security, so 401 and 403 responses carry the ID too. Work handed to
 * other threads does not inherit the ID (the application has no such work today).
 *
 * <p>Logging: a request that ends in a 5xx or an unhandled exception is logged once at ERROR
 * with the method, route template, status, duration, and exception class name: never the
 * URL, query string, headers, cookies, bodies, or exception message. Other requests are
 * logged at DEBUG (off in production): Railway's own HTTP logs and the
 * {@code http.server.requests} metric already cover routine traffic.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    /** Shown instead of a path when no controller route matched (404s, security rejections). */
    static final String UNMATCHED_ROUTE = "unmatched";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = accept(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        long started = System.nanoTime();
        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error ex) {
            failure = ex;
            throw ex;
        } finally {
            try {
                logCompletion(request, response, failure, (System.nanoTime() - started) / 1_000_000);
            } finally {
                MDC.remove(MDC_KEY);
            }
        }
    }

    /** The inbound ID when it is safe, otherwise a new random one. */
    static String accept(String inbound) {
        return inbound != null && SAFE_ID.matcher(inbound).matches() ? inbound : UUID.randomUUID().toString();
    }

    private static void logCompletion(HttpServletRequest request, HttpServletResponse response, Throwable failure,
                                      long durationMs) {
        int status = failure != null ? 500 : response.getStatus();
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern instanceof String template ? template : UNMATCHED_ROUTE;
        if (status >= 500) {
            Object handled = request.getAttribute(DispatcherServlet.EXCEPTION_ATTRIBUTE);
            Throwable cause = failure != null ? failure : handled instanceof Throwable t ? t : null;
            // The innermost cause's class (Spring wraps unhandled ones in ServletException).
            String exception = cause == null ? "none"
                    : NestedExceptionUtils.getMostSpecificCause(cause).getClass().getSimpleName();
            // In the text for readable local logs, and as fields for production JSON.
            log.atError()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("route", route)
                    .addKeyValue("status", status)
                    .addKeyValue("durationMs", durationMs)
                    .addKeyValue("exception", exception)
                    .log("http.request.failed method={} route={} status={} durationMs={} exception={}",
                            request.getMethod(), route, status, durationMs, exception);
        } else if (log.isDebugEnabled()) {
            log.atDebug()
                    .addKeyValue("method", request.getMethod())
                    .addKeyValue("route", route)
                    .addKeyValue("status", status)
                    .addKeyValue("durationMs", durationMs)
                    .log("http.request.completed method={} route={} status={} durationMs={}",
                            request.getMethod(), route, status, durationMs);
        }
    }
}
