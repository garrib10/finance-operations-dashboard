package dev.portfolio.finance.config;

import org.apache.catalina.Engine;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import dev.portfolio.finance.monitoring.CorrelationIdFilter;

/** Monitoring wiring (issue #20). */
@Configuration
public class MonitoringConfig {

    /**
     * Tomcat's logger for exceptions thrown out of the application's servlet, for example
     * {@code org.apache.catalina.core.ContainerBase.[Tomcat].[localhost].[/].[dispatcherServlet]}.
     * Built from the running server, because the engine name can differ (Tomcat-1 when a JVM
     * starts more than one), and brackets cannot be written in a properties file.
     */
    static String servletErrorLoggerName(String engineName, String hostName) {
        return "org.apache.catalina.core.ContainerBase.[" + engineName + "].[" + hostName + "].[/].[dispatcherServlet]";
    }

    /**
     * Tomcat logs an unhandled exception with its full message (which can hold request data)
     * and without the correlation ID. CorrelationIdFilter already logs the same failure with
     * the ID and a message-free stack trace, so Tomcat's copy is switched off once the server
     * has started. Only this one logger: Tomcat's startup and other container errors still log.
     */
    @Bean
    public ApplicationListener<WebServerInitializedEvent> quietTomcatServletErrorLog(LoggingSystem loggingSystem) {
        return event -> {
            if (event.getWebServer() instanceof TomcatWebServer tomcat) {
                Engine engine = tomcat.getTomcat().getEngine();
                loggingSystem.setLogLevel(servletErrorLoggerName(engine.getName(), engine.getDefaultHost()), LogLevel.OFF);
            }
        };
    }

    /** First of all filters, so every response (401s and 403s included) carries the ID. */
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
