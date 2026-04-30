package net.mcverse.register.api;

import java.time.Instant;

public record SimpleClansSyncRequest(String clanTag, String clanName, String clanRole, Instant observedAt) {

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        appendNullable(json, "clanTag", clanTag);
        json.append(",");
        appendNullable(json, "clanName", clanName);
        json.append(",");
        appendNullable(json, "clanRole", clanRole);
        if (observedAt != null) {
            json.append(",\"observedAt\":\"").append(observedAt).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    public String summary() {
        return "clanTag=" + clanTag + ",clanName=" + clanName + ",clanRole=" + clanRole;
    }

    private void appendNullable(StringBuilder json, String key, String value) {
        if (value == null) {
            json.append("\"").append(key).append("\":null");
            return;
        }
        json.append("\"").append(key).append("\":\"").append(escapeJson(value)).append("\"");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
