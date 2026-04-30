package net.mcverse.register.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PlayerStateSyncResponse(boolean success, boolean registered, boolean updated) {

    private static final Pattern SUCCESS_PATTERN = Pattern.compile("\"success\"\\s*:\\s*(true|false)");
    private static final Pattern REGISTERED_PATTERN = Pattern.compile("\"registered\"\\s*:\\s*(true|false)");
    private static final Pattern UPDATED_PATTERN = Pattern.compile("\"updated\"\\s*:\\s*(true|false)");

    public static PlayerStateSyncResponse fromApiResponse(ApiResponse response) {
        String body = response.getBody() == null ? "" : response.getBody();
        return new PlayerStateSyncResponse(
                extractBoolean(body, SUCCESS_PATTERN),
                extractBoolean(body, REGISTERED_PATTERN),
                extractBoolean(body, UPDATED_PATTERN)
        );
    }

    private static boolean extractBoolean(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }
}
