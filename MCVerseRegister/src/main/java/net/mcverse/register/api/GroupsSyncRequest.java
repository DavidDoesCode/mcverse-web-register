package net.mcverse.register.api;

import java.time.Instant;
import java.util.List;

public record GroupsSyncRequest(String primaryGroup, List<String> groups, Instant observedAt) {

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        if (primaryGroup == null) {
            json.append("\"primaryGroup\":null");
        } else {
            json.append("\"primaryGroup\":\"").append(escapeJson(primaryGroup)).append("\"");
        }
        json.append(",\"groups\":[");
        List<String> safeGroups = groups == null ? List.of() : groups;
        for (int i = 0; i < safeGroups.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("\"").append(escapeJson(safeGroups.get(i))).append("\"");
        }
        json.append("]");
        if (observedAt != null) {
            json.append(",\"observedAt\":\"").append(observedAt).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    public String summary() {
        int count = groups == null ? 0 : groups.size();
        return "primaryGroup=" + primaryGroup + ",groupsCount=" + count;
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
