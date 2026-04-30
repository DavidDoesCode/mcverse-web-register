package net.mcverse.register.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncPayloadRequestTest {

    @Test
    void balancePayloadContainsRequiredFields() {
        String json = new BalanceSyncRequest(12345.67, Instant.parse("2026-04-30T15:00:00Z")).toJson();
        assertTrue(json.contains("\"balance\":12345.67"));
        assertTrue(json.contains("\"observedAt\":\"2026-04-30T15:00:00Z\""));
    }

    @Test
    void groupsPayloadIncludesPrimaryAndGroups() {
        String json = new GroupsSyncRequest("vip", List.of("default", "vip"), Instant.parse("2026-04-30T15:00:00Z")).toJson();
        assertTrue(json.contains("\"primaryGroup\":\"vip\""));
        assertTrue(json.contains("\"groups\":[\"default\",\"vip\"]"));
    }

    @Test
    void simpleClansPayloadHandlesNullables() {
        String json = new SimpleClansSyncRequest(null, "MCVerse", null, null).toJson();
        assertTrue(json.contains("\"clanTag\":null"));
        assertTrue(json.contains("\"clanName\":\"MCVerse\""));
        assertTrue(json.contains("\"clanRole\":null"));
    }

    @Test
    void claimsPayloadContainsCountsAndLocations() {
        GriefPreventionClaimsSyncRequest request = new GriefPreventionClaimsSyncRequest(
                3,
                2500,
                1000,
                750,
                List.of(new ClaimLocation("world", 10, 10)),
                Instant.parse("2026-04-30T15:00:00Z")
        );
        String json = request.toJson();
        assertTrue(json.contains("\"claimCount\":3"));
        assertTrue(json.contains("\"remainingClaimBlocks\":750"));
        assertTrue(json.contains("\"claims\":[{\"world\":\"world\",\"x\":10,\"z\":10}]"));
    }
}
