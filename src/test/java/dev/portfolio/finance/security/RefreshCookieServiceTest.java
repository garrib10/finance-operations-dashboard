package dev.portfolio.finance.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import dev.portfolio.finance.config.AuthSessionProperties;
import dev.portfolio.finance.config.AuthSessionProperties.SameSite;
import dev.portfolio.finance.security.RefreshCookieService.PresentedRefreshCookie;
import dev.portfolio.finance.support.MutableClock;
import jakarta.servlet.http.Cookie;

class RefreshCookieServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
    private static final String TOKEN = "A".repeat(43);

    private final MutableClock clock = new MutableClock(NOW);

    private static AuthSessionProperties properties(boolean secure, SameSite sameSite) {
        return new AuthSessionProperties(Duration.ofMinutes(5), Duration.ofDays(30), secure, sameSite,
                true, Duration.ofDays(7));
    }

    private RefreshCookieService local() {
        return new RefreshCookieService(properties(false, SameSite.LAX), clock);
    }

    private RefreshCookieService secure() {
        return new RefreshCookieService(properties(true, SameSite.LAX), clock);
    }

    @Test
    void localCookieHasExactApprovedAttributes() {
        assertThat(local().issue(TOKEN, NOW.plus(Duration.ofDays(30)))).isEqualTo(
                "fintrack_refresh=" + TOKEN + "; Max-Age=2592000; Expires=Tue, 27 Oct 2026 12:00:00 GMT"
                        + "; Path=/api/auth; HttpOnly; SameSite=Lax");
    }

    @Test
    void secureCookieHasExactApprovedAttributes() {
        assertThat(secure().issue(TOKEN, NOW.plus(Duration.ofDays(30)))).isEqualTo(
                "__Secure-fintrack_refresh=" + TOKEN + "; Max-Age=2592000; Expires=Tue, 27 Oct 2026 12:00:00 GMT"
                        + "; Path=/api/auth; Secure; HttpOnly; SameSite=Lax");
    }

    @Test
    void neverSetsDomainAndUsesZeroPaddedDates() {
        clock.set(Instant.parse("2026-10-01T08:00:00Z"));
        String header = secure().issue(TOKEN, Instant.parse("2026-10-05T09:30:00Z"));

        assertThat(header).doesNotContainIgnoringCase("domain")
                .contains("Expires=Mon, 05 Oct 2026 09:30:00 GMT", "Max-Age=351000");
    }

    @Test
    void lifetimeIsTheRemainingAbsoluteLifetimeNotAFreshThirtyDays() {
        Instant expiresAt = NOW.plus(Duration.ofDays(30));
        clock.advance(Duration.ofDays(29).plusHours(23));

        String header = local().issue(TOKEN, expiresAt);

        assertThat(header).contains("Max-Age=3600", "Expires=Tue, 27 Oct 2026 12:00:00 GMT");
    }

    @Test
    void partialSecondsRoundDownSoCookieNeverOutlivesSession() {
        Instant expiresAt = NOW.plusSeconds(10).plusMillis(900);
        assertThat(local().issue(TOKEN, expiresAt)).contains("Max-Age=10", "Expires=Sun, 27 Sep 2026 12:00:10 GMT");
    }

    @Test
    void pastExpirationProducesImmediatelyExpiredCookie() {
        assertThat(local().issue(TOKEN, NOW.minusSeconds(5))).contains("Max-Age=0");
    }

    @Test
    void clearingMatchesIssuanceScope() {
        assertThat(local().clear()).isEqualTo("fintrack_refresh=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT"
                + "; Path=/api/auth; HttpOnly; SameSite=Lax");
        assertThat(secure().clear()).isEqualTo("__Secure-fintrack_refresh=; Max-Age=0"
                + "; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/api/auth; Secure; HttpOnly; SameSite=Lax");
    }

    @Test
    void strictSameSiteIsHonoredWhenConfigured() {
        RefreshCookieService strict = new RefreshCookieService(properties(true, SameSite.STRICT), clock);
        assertThat(strict.issue(TOKEN, NOW.plusSeconds(60))).endsWith("; SameSite=Strict");
        assertThat(strict.clear()).endsWith("; SameSite=Strict");
    }

    @Test
    void readsExactlyOneCookieWithTheConfiguredName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("fintrack_refresh", TOKEN),
                new Cookie("__Secure-fintrack_refresh", "ignored-other-variant"));

        PresentedRefreshCookie presented = local().read(request);

        assertThat(presented.state()).isEqualTo(PresentedRefreshCookie.State.PRESENT);
        assertThat(presented.value()).isEqualTo(TOKEN);
        assertThat(presented.toString()).doesNotContain(TOKEN);
    }

    @Test
    void missingCookieIsAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(local().read(request).state()).isEqualTo(PresentedRefreshCookie.State.ABSENT);
        request.setCookies(new Cookie("__Secure-fintrack_refresh", TOKEN));
        assertThat(local().read(request).state()).isEqualTo(PresentedRefreshCookie.State.ABSENT);
    }

    @Test
    void duplicateCookiesAreNeverResolvedToOneValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("fintrack_refresh", TOKEN), new Cookie("fintrack_refresh", "B".repeat(43)));

        PresentedRefreshCookie presented = local().read(request);

        assertThat(presented.state()).isEqualTo(PresentedRefreshCookie.State.DUPLICATE);
        assertThat(presented.value()).isNull();
    }

    @Test
    void blankCookieIsPresentedToStrictDecoder() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("fintrack_refresh", ""));

        PresentedRefreshCookie presented = local().read(request);

        assertThat(presented.state()).isEqualTo(PresentedRefreshCookie.State.PRESENT);
        assertThat(presented.value()).isEmpty();
    }

    @Test
    void readsRawHeaderValueWithoutContainerUnquoting() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cookie", "fintrack_refresh=\"" + TOKEN + "\"");

        PresentedRefreshCookie presented = local().read(request);

        assertThat(presented.state()).isEqualTo(PresentedRefreshCookie.State.PRESENT);
        assertThat(presented.value()).isEqualTo("\"" + TOKEN + "\"");
    }

    @Test
    void detectsDuplicatesAcrossSeparateCookieHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cookie", "a=1; fintrack_refresh=" + TOKEN);
        request.addHeader("Cookie", "fintrack_refresh=" + TOKEN);

        assertThat(local().read(request).state()).isEqualTo(PresentedRefreshCookie.State.DUPLICATE);
    }

    @Test
    void matchesNameExactlyAndKeepsValueVerbatim() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cookie", "Fintrack_refresh=x; xfintrack_refresh=y; =z; novalue; fintrack_refresh="
                + TOKEN + "=");

        PresentedRefreshCookie presented = local().read(request);

        assertThat(presented.state()).isEqualTo(PresentedRefreshCookie.State.PRESENT);
        assertThat(presented.value()).isEqualTo(TOKEN + "=");
    }
}
