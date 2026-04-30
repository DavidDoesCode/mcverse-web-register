package net.mcverse.register.api;

import net.mcverse.register.MCVerseRegister;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class MCVerseApiClient {

    private static final String USER_AGENT = "MCVerseRegister/1.0.0";
    private final MCVerseRegister plugin;

    public MCVerseApiClient(MCVerseRegister plugin) {
        this.plugin = plugin;
    }

    private HttpClient buildClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeout()))
                .build();
    }

    public ApiResponse registerPlayer(UUID uuid, String username, String email) throws Exception {
        String url = baseUrl() + "/api/v1/auth/register";
        plugin.getLogger().info("POST " + url);

        String body = String.format(
                "{\"minecraftUuid\":\"%s\",\"minecraftUsername\":\"%s\",\"email\":\"%s\"}",
                uuid, escapeJson(username), escapeJson(email)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMillis(timeout()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = buildClient().send(request, HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }

    public ApiResponse unregisterPlayer(UUID uuid) throws Exception {
        String url = baseUrl() + "/api/v1/auth/player/" + uuid;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMillis(timeout()))
                .DELETE()
                .build();

        HttpResponse<String> response = buildClient().send(request, HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }

    public boolean getPlayerStatus(UUID uuid) throws Exception {
        return getPlayer(uuid).getStatusCode() == 200;
    }

    public ApiResponse getPlayer(UUID uuid) throws Exception {
        String url = baseUrl() + "/api/v1/auth/player/" + uuid;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMillis(timeout()))
                .GET()
                .build();

        HttpResponse<String> response = buildClient().send(request, HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }

    public ApiResponse syncUsername(UUID uuid, UsernameSyncRequest requestBody) throws Exception {
        String url = baseUrl() + "/api/v1/sync/players/" + uuid + "/username";
        String body = requestBody.toJson();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMillis(timeout()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = buildClient().send(request, HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }

    public ApiResponse syncUsername(UUID uuid, String minecraftUsername) throws Exception {
        return syncUsername(uuid, new UsernameSyncRequest(minecraftUsername, Instant.now()));
    }

    private String baseUrl() {
        return plugin.getConfig().getString("api-base-url", "https://preview.mcverse.city");
    }

    private int timeout() {
        return plugin.getConfig().getInt("request-timeout", 5000);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
