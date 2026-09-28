package dev.portfolio.finance.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import dev.portfolio.finance.security.AllowedOrigins;
import dev.portfolio.finance.security.AuthRequestProtectionFilter;
import dev.portfolio.finance.security.JwtAuthenticationFilter;
import dev.portfolio.finance.security.RestAuthenticationEntryPoint;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class SecurityConfig {

    /** Auth endpoints that accept the refresh cookie or establish one. */
    private static final List<String> CREDENTIALED_AUTH_PATHS =
            List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    /** Exact normalized origins; startup fails on wildcard or malformed entries. */
    @Bean
    public AllowedOrigins allowedOrigins(@Value("${app.frontend-urls}") String frontendUrls) {
        return AllowedOrigins.parse(frontendUrls);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            AllowedOrigins allowedOrigins,
            JsonMapper jsonMapper
    ) throws Exception {

        http
                .cors(Customizer.withDefaults())

                // Business APIs authenticate only with bearer JWTs. The refresh cookie
                // is read only by refresh/logout, which AuthRequestProtectionFilter
                // guards with an exact Origin and custom header (docs/security-csrf.md).
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(
                                authenticationEntryPoint
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()

                        .requestMatchers(
                                "/api/health",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        )
                        .permitAll()

                        .anyRequest()
                        .authenticated()
                );

        // Runs before CORS so unapproved origins get the JSON 403 contract rather than
        // CORS's plain rejection. It only matches POST, so preflight still reaches CORS.
        http.addFilterBefore(
                new AuthRequestProtectionFilter(allowedOrigins, jsonMapper),
                CorsFilter.class
        );

        http.addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AllowedOrigins allowedOrigins) {
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        // Credentialed CORS is limited to the auth routes that use the refresh cookie.
        // Exact origins only: credentials are never combined with a wildcard.
        CorsConfiguration authConfiguration = new CorsConfiguration();
        authConfiguration.setAllowedOrigins(allowedOrigins.values());
        authConfiguration.setAllowCredentials(true);
        authConfiguration.setAllowedMethods(List.of("POST", "OPTIONS"));
        authConfiguration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                AuthRequestProtectionFilter.HEADER_NAME
        ));
        CREDENTIALED_AUTH_PATHS.forEach(path ->
                source.registerCorsConfiguration(path, authConfiguration));

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(allowedOrigins.values());

        // Business APIs use bearer tokens, never cookies, so credentials stay off.
        configuration.setAllowCredentials(false);

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type"
                )
        );

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}
