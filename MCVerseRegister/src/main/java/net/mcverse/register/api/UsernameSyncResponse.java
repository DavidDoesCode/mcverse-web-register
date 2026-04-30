package net.mcverse.register.api;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record UsernameSyncResponse(boolean success, boolean changed) {

    private static final Pattern SUCCESS_PATTERN = Pattern.compile("\"success\"\\s*:\\s*(true|false)");
    private static final Pattern CHANGED_PATTERN = Pattern.compile("\"changed\"\\s*:\\s*(true|false)");

    public static UsernameSyncResponse fromApiResponse(ApiResponse response) {
        String body = response.getBody() == null ? "" : response.getBody();
        return new UsernameSyncResponse(extractBoolean(body, SUCCESS_PATTERN), extractBoolean(body, CHANGED_PATTERN));
    }

    private static boolean extractBoolean(String body, Pattern pattern) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }
}
