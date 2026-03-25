package net.mcverse.register.api;

import net.mcverse.register.MCVerseRegister;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class MCVerseApiClient {

    private final MCVerseRegister plugin;
    private final HttpClient httpClient;

    public MCVerseApiClient(MCVerseRegister plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(plugin.getConfig().getInt("request-timeout", 5000)))
                .build();
    }

    public ApiResponse registerPlayer(UUID uuid, String username, String email) throws Exception {
        String url = baseUrl() + "/api/v1/auth/register";

        String body = String.format(
                "{\"minecraftUuid\":\"%s\",\"minecraftUsername\":\"%s\",\"email\":\"%s\"}",
                uuid, escapeJson(username), escapeJson(email)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "MCVerseRegister/1.0.0")
                .timeout(Duration.ofMillis(timeout()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }

    public ApiResponse unregisterPlayer(UUID uuid) throws Exception {
        String url = baseUrl() + "/api/v1/auth/player/" + uuid;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "MCVerseRegister/1.0.0")
                .timeout(Duration.ofMillis(timeout()))
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new ApiResponse(response.statusCode(), response.body());
    }

    public boolean getPlayerStatus(UUID uuid) throws Exception {
        String url = baseUrl() + "/api/v1/auth/player/" + uuid;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "MCVerseRegister/1.0.0")
                .timeout(Duration.ofMillis(timeout()))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }

    private String baseUrl() {
        return plugin.getConfig().getString("api-base-url", "https://api.mcverse.city");
    }

    private int timeout() {
        return plugin.getConfig().getInt("request-timeout", 5000);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
