package net.mcverse.register.service;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;
import net.mcverse.register.api.MCVerseApiClient;
import net.mcverse.register.api.PlayerLookupPlayer;
import net.mcverse.register.api.PlayerLookupResponse;
import net.mcverse.register.api.UsernameSyncRequest;
import net.mcverse.register.api.UsernameSyncResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class UsernameSyncService {

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 250L;
    private static final long MAX_BACKOFF_MS = 2000L;

    private final MCVerseRegister plugin;
    private final MCVerseApiClient apiClient;
    private final SleepStrategy sleepStrategy;
    private final JitterStrategy jitterStrategy;

    public UsernameSyncService(MCVerseRegister plugin) {
        this(plugin, plugin.getApiClient(), millis -> Thread.sleep(millis), () -> ThreadLocalRandom.current().nextLong(0L, 101L));
    }

    UsernameSyncService(MCVerseRegister plugin, MCVerseApiClient apiClient, SleepStrategy sleepStrategy, JitterStrategy jitterStrategy) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.sleepStrategy = sleepStrategy;
        this.jitterStrategy = jitterStrategy;
    }

    public UsernameSyncResult syncIfNeeded(UUID uuid, String currentMinecraftUsername) {
        plugin.getLogger().info("[username-sync] check started uuid=" + uuid + " username=" + currentMinecraftUsername);

        try {
            ApiResponse lookupApiResponse = executeWithRetry("lookup", () -> apiClient.getPlayer(uuid));

            if (lookupApiResponse.getStatusCode() == 404) {
                plugin.getLogger().info("[username-sync] unregistered skip uuid=" + uuid + " status=404");
                return UsernameSyncResult.unregistered();
            }

            if (lookupApiResponse.getStatusCode() != 200) {
                plugin.getLogger().warning("[username-sync] check failed uuid=" + uuid
                        + " status=" + lookupApiResponse.getStatusCode());
                return UsernameSyncResult.failure();
            }

            PlayerLookupResponse lookupResponse = PlayerLookupResponse.fromApiResponse(lookupApiResponse);
            if (!lookupResponse.registered()) {
                plugin.getLogger().info("[username-sync] unregistered skip uuid=" + uuid + " registered=false");
                return UsernameSyncResult.unregistered();
            }

            PlayerLookupPlayer backendPlayer = lookupResponse.player();
            String backendUsername = backendPlayer == null ? null : backendPlayer.minecraftUsername();
            if (backendUsername == null) {
                plugin.getLogger().warning("[username-sync] check failed uuid=" + uuid + " reason=missing-backend-username");
                return UsernameSyncResult.failure();
            }

            if (backendUsername.equals(currentMinecraftUsername)) {
                plugin.getLogger().info("[username-sync] sync success uuid=" + uuid + " changed=false");
                return UsernameSyncResult.registeredInSync();
            }

            plugin.getLogger().info("[username-sync] mismatch detected uuid=" + uuid
                    + " backendUsername=" + backendUsername
                    + " currentUsername=" + currentMinecraftUsername);

            ApiResponse syncApiResponse = executeWithRetry(
                    "sync",
                    () -> apiClient.syncUsername(uuid, new UsernameSyncRequest(currentMinecraftUsername, Instant.now()))
            );

            if (syncApiResponse.getStatusCode() == 404) {
                plugin.getLogger().info("[username-sync] unregistered skip uuid=" + uuid + " status=404");
                return UsernameSyncResult.unregistered();
            }

            if (syncApiResponse.getStatusCode() != 200) {
                plugin.getLogger().warning("[username-sync] sync failed uuid=" + uuid
                        + " status=" + syncApiResponse.getStatusCode());
                return UsernameSyncResult.failure();
            }

            UsernameSyncResponse syncResponse = UsernameSyncResponse.fromApiResponse(syncApiResponse);
            plugin.getLogger().info("[username-sync] sync success uuid=" + uuid + " changed=" + syncResponse.changed());
            return syncResponse.changed() ? UsernameSyncResult.registeredChanged() : UsernameSyncResult.registeredInSync();
        } catch (Exception e) {
            plugin.getLogger().warning("[username-sync] failure uuid=" + uuid + " error=" + e.getMessage());
            return UsernameSyncResult.failure();
        }
    }

    private ApiResponse executeWithRetry(String operationName, RetryableCall call) throws Exception {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                ApiResponse response = call.execute();
                if (!isRetryableStatus(response.getStatusCode()) || attempt >= MAX_ATTEMPTS) {
                    return response;
                }
                waitForRetry(operationName, attempt, "status=" + response.getStatusCode());
            } catch (IOException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                waitForRetry(operationName, attempt, "io_exception");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
    }

    private void waitForRetry(String operationName, int attempt, String reason) throws InterruptedException {
        long delay = Math.min(BASE_BACKOFF_MS * (1L << Math.max(0, attempt - 1)), MAX_BACKOFF_MS) + jitterStrategy.nextJitter();
        plugin.getLogger().info("[username-sync] retry scheduled operation=" + operationName
                + " attempt=" + (attempt + 1)
                + " reason=" + reason
                + " delayMs=" + delay);
        sleepStrategy.sleep(delay);
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode >= 500 && statusCode <= 599;
    }

    @FunctionalInterface
    interface RetryableCall {
        ApiResponse execute() throws Exception;
    }

    @FunctionalInterface
    interface SleepStrategy {
        void sleep(long millis) throws InterruptedException;
    }

    @FunctionalInterface
    interface JitterStrategy {
        long nextJitter();
    }
}
