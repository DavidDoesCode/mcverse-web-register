package net.mcverse.register.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerStatsScheduleTest {

    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final LocalTime RUN_AT = LocalTime.of(10, 0);

    @Test
    void waitsUntilTodaysRunAtWhenItIsStillInTheFuture() {
        Instant now = Instant.parse("2026-08-21T14:00:00Z");
        Duration delay = ServerStatsSchedule.delayUntilInitialTrigger(now, RUN_AT, CHICAGO, null, true);
        Instant expectedSlot = Instant.parse("2026-08-21T15:00:00Z");
        assertEquals(Duration.between(now, expectedSlot), delay);
    }

    @Test
    void catchUpWhenTodaysSlotWasMissedAndLastSuccessIsYesterday() {
        Instant now = Instant.parse("2026-08-21T16:00:00Z");
        Instant yesterdaySuccess = Instant.parse("2026-08-20T15:05:00Z");
        Duration delay = ServerStatsSchedule.delayUntilInitialTrigger(now, RUN_AT, CHICAGO, yesterdaySuccess, true);
        assertEquals(ServerStatsSchedule.CATCH_UP_DELAY, delay);
    }

    @Test
    void alreadyRanTodaySkipsUntilTomorrowRunAt() {
        Instant now = Instant.parse("2026-08-21T16:00:00Z");
        Instant todaySuccess = Instant.parse("2026-08-21T15:05:00Z");
        assertTrue(ServerStatsSchedule.alreadySucceededToday(now, todaySuccess, CHICAGO));
        Duration delay = ServerStatsSchedule.delayUntilInitialTrigger(now, RUN_AT, CHICAGO, todaySuccess, true);
        Instant tomorrowSlot = Instant.parse("2026-08-22T15:00:00Z");
        assertEquals(Duration.between(now, tomorrowSlot), delay);
    }

    @Test
    void followingRunAtAfterTodaysSlotIsTomorrow() {
        Instant now = Instant.parse("2026-08-21T15:00:05Z");
        Duration delay = ServerStatsSchedule.delayUntilFollowingRunAt(now, RUN_AT, CHICAGO);
        Instant tomorrowSlot = Instant.parse("2026-08-22T15:00:00Z");
        assertEquals(Duration.between(now, tomorrowSlot), delay);
    }

    @Test
    void alreadySucceededTodayIsFalseWhenLastRunIsNull() {
        assertFalse(ServerStatsSchedule.alreadySucceededToday(Instant.parse("2026-08-21T16:00:00Z"), null, CHICAGO));
    }
}
