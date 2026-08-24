package net.mcverse.register.api;

import net.mcverse.register.integration.NicknameSnapshot;

import java.time.Instant;

public record NicknameSyncRequest(String minecraftUsername, String nickname, Instant observedAt) {

    public NicknameSyncRequest {
        nickname = NicknameSnapshot.normalize(nickname);
    }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"minecraftUsername\":\"").append(escapeJson(minecraftUsername)).append("\"");
        json.append(",");
        appendNullable(json, "nickname", nickname);
        if (observedAt != null) {
            json.append(",\"observedAt\":\"").append(observedAt).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    public String summary() {
        return "nickname=" + nickname;
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
