package dev.portfolio.finance.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Exact browser-origin allowlist shared by CORS and auth request protection.
 * Entries are normalized once (lowercase scheme/host, default port omitted);
 * matching is exact set membership with no substring, suffix, or wildcard rules.
 */
public final class AllowedOrigins {

    private final Set<String> origins;

    private AllowedOrigins(Set<String> origins) {
        this.origins = Collections.unmodifiableSet(origins);
    }

    /** Fails startup on wildcard, empty, or non-origin entries without echoing them. */
    public static AllowedOrigins parse(String commaSeparated) {
        Set<String> result = new LinkedHashSet<>();
        String[] entries = commaSeparated == null ? new String[0] : commaSeparated.split(",");
        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i].trim();
            if (entry.isEmpty()) {
                continue;
            }
            String candidate = entry.endsWith("/") ? entry.substring(0, entry.length() - 1) : entry;
            result.add(normalize(candidate).orElseThrow(() -> new IllegalStateException(
                    "app.frontend-urls (FRONTEND_URLS) contains an entry that is not an exact http(s) origin")));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("app.frontend-urls (FRONTEND_URLS) must list at least one origin");
        }
        return new AllowedOrigins(result);
    }

    public List<String> values() {
        return new ArrayList<>(origins);
    }

    /** True only for a single exact serialized origin in the allowlist. */
    public boolean isAllowedOrigin(String headerValue) {
        return headerValue != null && normalize(headerValue).filter(origins::contains).isPresent();
    }

    /** Derives the origin of an absolute http(s) Referer and checks it exactly. */
    public boolean isAllowedReferer(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(headerValue);
            if (!uri.isAbsolute() || uri.getRawUserInfo() != null || uri.getHost() == null) {
                return false;
            }
            return origins.contains(serialize(uri));
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    /** Accepts only scheme://host[:port] with nothing else, as browsers serialize Origin. */
    static Optional<String> normalize(String value) {
        if (value.isEmpty() || value.contains("*") || value.contains(",")
                || !value.equals(value.strip())) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(value);
            boolean bareOrigin = uri.isAbsolute() && uri.getHost() != null
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
            return bareOrigin ? Optional.ofNullable(serialize(uri)) : Optional.empty();
        } catch (URISyntaxException ex) {
            return Optional.empty();
        }
    }

    private static String serialize(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return null;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || (scheme.equals("http") && port == 80)
                || (scheme.equals("https") && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }
}
