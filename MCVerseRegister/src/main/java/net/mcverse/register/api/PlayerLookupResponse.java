package net.mcverse.register.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PlayerLookupResponse(boolean success, boolean registered, PlayerLookupPlayer player) {

    private static final Pattern REGISTERED_PATTERN = Pattern.compile("\"registered\"\\s*:\\s*(true|false)");
    private static final Pattern SUCCESS_PATTERN = Pattern.compile("\"success\"\\s*:\\s*(true|false)");
    private static final Pattern PLAYER_PATTERN = Pattern.compile("\"player\"\\s*:\\s*\\{([\\s\\S]*?)\\}");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("\"minecraftUsername\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"");
    private static final Pattern UUID_PATTERN = Pattern.compile("\"minecraftUuid\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"");

    public static PlayerLookupResponse fromApiResponse(ApiResponse response) {
        String body = response.getBody() == null ? "" : response.getBody();
        boolean success = extractBoolean(body, SUCCESS_PATTERN);
        boolean registered = extractBoolean(body, REGISTERED_PATTERN);

        PlayerLookupPlayer player = null;
        Matcher playerMatcher = PLAYER_PATTERN.matcher(body);
        if (playerMatcher.find()) {
            String playerBody = playerMatcher.group(1);
            String minecraftUsername = extractString(playerBody, USERNAME_PATTERN);
            String minecraftUuid = extractString(playerBody, UUID_PATTERN);
            if (minecraftUsername != null || minecraftUuid != null) {
                player = new PlayerLookupPlayer(minecraftUsername, minecraftUuid);
            }
        }

        return new PlayerLookupResponse(success, registered, player);
    }

    private static boolean extractBoolean(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }

    private static String extractString(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
