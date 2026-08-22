package net.mcverse.register.api;

import net.mcverse.register.integration.VanillaStatsSnapshot;

import java.time.Instant;
import java.util.Map;

public record VanillaStatsSyncRequest(VanillaStatsSnapshot snapshot) {

    public String toJson() {
        VanillaStatsSnapshot stats = snapshot;
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendString(json, "observedAt", instant(stats.observedAt()), true);
        appendString(json, "minecraftVersion", stats.minecraftVersion(), false);
        appendString(json, "minecraftUsername", stats.minecraftUsername(), false);
        appendString(json, "firstPlayed", instant(stats.firstPlayed()), false);
        appendString(json, "lastSeen", instant(stats.lastSeen()), false);
        json.append(",\"balance\":");
        if (stats.balance() == null) {
            json.append("null");
        } else {
            json.append(stats.balance());
        }
        json.append(",\"primaryGroup\":");
        if (stats.primaryGroup() == null) {
            json.append("null");
        } else {
            json.append('"').append(escapeJson(stats.primaryGroup())).append('"');
        }
        json.append(",\"stats\":{");
        boolean firstCategory = true;
        Map<String, Map<String, Long>> categories = stats.stats() == null ? Map.of() : stats.stats();
        for (Map.Entry<String, Map<String, Long>> category : categories.entrySet()) {
            if (category.getValue() == null || category.getValue().isEmpty()) {
                continue;
            }
            if (!firstCategory) {
                json.append(',');
            }
            firstCategory = false;
            json.append('"').append(escapeJson(category.getKey())).append("\":{");
            boolean firstStat = true;
            for (Map.Entry<String, Long> stat : category.getValue().entrySet()) {
                if (stat.getValue() == null || stat.getValue() <= 0L) {
                    continue;
                }
                if (!firstStat) {
                    json.append(',');
                }
                firstStat = false;
                json.append('"').append(escapeJson(stat.getKey())).append("\":").append(stat.getValue());
            }
            json.append('}');
        }
        json.append("}}");
        return json.toString();
    }

    public String summary() {
        int categories = snapshot.stats() == null ? 0 : snapshot.stats().size();
        return "username=" + snapshot.minecraftUsername() + ",categories=" + categories;
    }

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private static void appendString(StringBuilder json, String key, String value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(key).append("\":");
        if (value == null) {
            json.append("null");
        } else {
            json.append('"').append(escapeJson(value)).append('"');
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
