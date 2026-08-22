package net.mcverse.register.api;

import net.mcverse.register.integration.ServerStatsSnapshot;

import java.time.Instant;

public record ServerStatsSyncRequest(ServerStatsSnapshot snapshot) {

    public String toJson() {
        ServerStatsSnapshot stats = snapshot;
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendInstant(json, "observedAt", stats.observedAt(), true);
        appendInstant(json, "weekStart", stats.weekStart(), false);
        appendNumber(json, "playersJoined", stats.playersJoined());
        json.append(",\"rankCounts\":{");
        appendNumber(json, "default", stats.rankDefault(), true);
        appendNumber(json, "member", stats.rankMember(), false);
        appendNumber(json, "regular", stats.rankRegular(), false);
        appendNumber(json, "citizen", stats.rankCitizen(), false);
        json.append('}');
        appendNumber(json, "citizenAll", stats.citizenAll());
        appendNumber(json, "economyTotal", stats.economyTotal());
        appendNumber(json, "planRegularPlayers", stats.planRegularPlayers());
        appendNumber(json, "totalPlaytimeMs", stats.totalPlaytimeMs());
        appendNumber(json, "minecraftDay", stats.minecraftDay());
        appendNumber(json, "averageTps", stats.averageTps());
        appendNumber(json, "playerKillsAllTime", stats.playerKillsAllTime());
        appendNumber(json, "deathsAllTime", stats.deathsAllTime());
        appendNumber(json, "mobKillsAllTime", stats.mobKillsAllTime());
        appendNumber(json, "claimedArea", stats.claimedArea());
        appendNumber(json, "playerKillsThisWeek", stats.playerKillsThisWeek());
        appendNumber(json, "deathsThisWeek", stats.deathsThisWeek());
        appendNumber(json, "mobKillsThisWeek", stats.mobKillsThisWeek());
        json.append('}');
        return json.toString();
    }

    public String summary() {
        return "playersJoined=" + snapshot.playersJoined()
                + ",citizenAll=" + snapshot.citizenAll()
                + ",rankCitizen=" + snapshot.rankCitizen();
    }

    private static void appendInstant(StringBuilder json, String key, Instant value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(key).append("\":");
        if (value == null) {
            json.append("null");
        } else {
            json.append('"').append(value).append('"');
        }
    }

    private static void appendNumber(StringBuilder json, String key, Number value) {
        appendNumber(json, key, value, false);
    }

    private static void appendNumber(StringBuilder json, String key, Number value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(key).append("\":");
        if (value == null) {
            json.append("null");
        } else {
            json.append(value);
        }
    }
}
