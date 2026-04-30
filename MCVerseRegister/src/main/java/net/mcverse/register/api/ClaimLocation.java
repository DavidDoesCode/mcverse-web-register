package net.mcverse.register.api;

public record ClaimLocation(String world, int x, int z) {

    public String toJson() {
        return "{\"world\":\"" + escapeJson(world) + "\",\"x\":" + x + ",\"z\":" + z + "}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
