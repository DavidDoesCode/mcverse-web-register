package net.mcverse.register.integration;

import java.time.Instant;
import java.util.Map;

public record VanillaStatsSnapshot(
        Instant observedAt,
        String minecraftVersion,
        String minecraftUsername,
        Instant firstPlayed,
        Instant lastSeen,
        Double balance,
        String primaryGroup,
        Map<String, Map<String, Long>> stats
) {
}
