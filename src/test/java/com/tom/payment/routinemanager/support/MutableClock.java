package com.tom.payment.routinemanager.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * The tests' "now". Starts at Wednesday 2026-11-18 09:00 (system zone) and is
 * reset after every test by {@link BaseComponentTest}.
 */
public class MutableClock extends Clock {

    public static final LocalDate DEFAULT_TODAY = LocalDate.of(2026, 11, 18);
    private static final LocalTime TIME_OF_DAY = LocalTime.of(9, 0);

    private final ZoneId zone;
    private volatile Instant now;

    public MutableClock() {
        this(ZoneId.systemDefault());
    }

    private MutableClock(ZoneId zone) {
        this.zone = zone;
        reset();
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId otherZone) {
        return Clock.fixed(now, otherZone);
    }

    public LocalDate today() {
        return LocalDate.ofInstant(now, zone);
    }

    /** Moves to 09:00 on the given day. */
    public void setToday(LocalDate date) {
        now = date.atTime(TIME_OF_DAY).atZone(zone).toInstant();
    }

    public void setInstant(Instant instant) {
        now = instant;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void reset() {
        setToday(DEFAULT_TODAY);
    }
}
