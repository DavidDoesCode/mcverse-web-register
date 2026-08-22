package net.mcverse.register.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import net.mcverse.register.integration.ServerStatsSnapshot;
import net.mcverse.register.integration.VanillaStatsSnapshot;

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

    @Test
    void serverStatsPayloadIncludesPrimaryCitizenAndCitizenAll() {
        Instant observedAt = Instant.parse("2026-08-21T20:00:00Z");
        ServerStatsSnapshot snapshot = ServerStatsSnapshot.builder()
                .observedAt(observedAt)
                .weekStart(Instant.parse("2026-08-14T20:00:00Z"))
                .playersJoined(1234L)
                .rankDefault(800)
                .rankMember(300)
                .rankRegular(90)
                .rankCitizen(44)
                .citizenAll(120)
                .economyTotal(1234567.89)
                .planRegularPlayers(120)
                .averageTps(19.87)
                .build();
        String json = new ServerStatsSyncRequest(snapshot).toJson();
        assertTrue(json.contains("\"rankCounts\":{"));
        assertTrue(json.contains("\"citizen\":44"));
        assertTrue(json.contains("\"citizenAll\":120"));
        assertTrue(json.contains("\"playersJoined\":1234"));
        assertTrue(json.contains("\"observedAt\":\"2026-08-21T20:00:00Z\""));
    }

    @Test
    void vanillaStatsPayloadOmitsZerosAndEmptyCategories() {
        Instant observedAt = Instant.parse("2026-08-21T22:15:00Z");
        VanillaStatsSnapshot snapshot = new VanillaStatsSnapshot(
                observedAt,
                "1.21.11",
                "Steve",
                Instant.parse("2024-03-01T18:00:00Z"),
                observedAt,
                1520.75,
                "citizen",
                Map.of(
                        "minecraft:custom", Map.of(
                                "minecraft:deaths", 10L,
                                "minecraft:jump", 0L
                        ),
                        "minecraft:mined", Map.of(),
                        "minecraft:killed", Map.of("minecraft:zombie", 80L)
                )
        );
        String json = new VanillaStatsSyncRequest(snapshot).toJson();
        assertTrue(json.contains("\"minecraft:deaths\":10"));
        assertTrue(json.contains("\"minecraft:zombie\":80"));
        assertTrue(!json.contains("\"minecraft:jump\""));
        assertTrue(!json.contains("\"minecraft:mined\""));
        assertTrue(json.contains("\"minecraftUsername\":\"Steve\""));
        assertTrue(json.contains("\"primaryGroup\":\"citizen\""));
    }
}
