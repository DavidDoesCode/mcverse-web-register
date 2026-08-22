package net.mcverse.register.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Wall-clock math for the daily server-stats job. Pure so it can be unit tested
 * without a Bukkit scheduler.
 */
public final class ServerStatsSchedule {

    public static final Duration CATCH_UP_DELAY = Duration.ofSeconds(60);

    private ServerStatsSchedule() {
    }

    public static boolean alreadySucceededToday(Instant now, Instant lastSuccessfulAt, ZoneId zone) {
        if (lastSuccessfulAt == null) {
            return false;
        }
        return lastSuccessfulAt.atZone(zone).toLocalDate().equals(now.atZone(zone).toLocalDate());
    }

    /**
     * Delay from plugin enable until the next automatic trigger: today's {@code runAt}
     * if it is still in the future; a short catch-up if today's slot was missed and
     * has not succeeded yet; otherwise tomorrow's {@code runAt}.
     */
    public static Duration delayUntilInitialTrigger(
            Instant now,
            LocalTime runAt,
            ZoneId zone,
            Instant lastSuccessfulAt,
            boolean catchUpOnStartup
    ) {
        if (alreadySucceededToday(now, lastSuccessfulAt, zone)) {
            return delayUntilFollowingRunAt(now, runAt, zone);
        }
        ZonedDateTime zoned = now.atZone(zone);
        ZonedDateTime todaySlot = zoned.toLocalDate().atTime(runAt).atZone(zone);
        if (zoned.isBefore(todaySlot)) {
            return Duration.between(zoned, todaySlot);
        }
        if (catchUpOnStartup) {
            return CATCH_UP_DELAY;
        }
        return delayUntilFollowingRunAt(now, runAt, zone);
    }

    /**
     * Delay until the next {@code runAt} strictly after {@code now} (never the slot
     * we may have just fired). Used after each scheduled attempt.
     */
    public static Duration delayUntilFollowingRunAt(Instant now, LocalTime runAt, ZoneId zone) {
        ZonedDateTime zoned = now.atZone(zone);
        ZonedDateTime todaySlot = zoned.toLocalDate().atTime(runAt).atZone(zone);
        ZonedDateTime nextSlot = zoned.isBefore(todaySlot) ? todaySlot : todaySlot.plusDays(1);
        return Duration.between(zoned, nextSlot);
    }

    public static long toTicks(Duration delay) {
        long millis = Math.max(50L, delay.toMillis());
        return Math.max(1L, (millis + 49L) / 50L);
    }
}
