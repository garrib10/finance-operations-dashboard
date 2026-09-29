package dev.portfolio.finance.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Deterministic UTC clock that tests can move forward or backward. */
public final class MutableClock extends Clock {

    private Instant instant;

    public MutableClock(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration duration) {
        instant = instant.plus(duration);
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("MutableClock is UTC only");
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
