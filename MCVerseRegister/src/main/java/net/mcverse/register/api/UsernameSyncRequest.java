package net.mcverse.register.api;

import java.time.Instant;

public record UsernameSyncRequest(String minecraftUsername, Instant observedAt) {

    public String toJson() {
        if (observedAt == null) {
            return "{\"minecraftUsername\":\"" + escapeJson(minecraftUsername) + "\"}";
        }

        return "{\"minecraftUsername\":\"" + escapeJson(minecraftUsername)
                + "\",\"observedAt\":\"" + observedAt + "\"}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
