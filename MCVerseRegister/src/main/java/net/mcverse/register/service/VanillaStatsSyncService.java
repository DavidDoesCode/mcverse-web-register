package net.mcverse.register.service;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;
import net.mcverse.register.api.MCVerseApiClient;
import net.mcverse.register.api.VanillaStatsSyncRequest;
import net.mcverse.register.integration.BalanceSnapshot;
import net.mcverse.register.integration.GroupsSnapshot;
import net.mcverse.register.integration.PlayerDataAdapter;
import net.mcverse.register.integration.VanillaStatsSnapshot;
import net.mcverse.register.integration.VanillaStatsSnapshotter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VanillaStatsSyncService {

    private static final String LAST_SYNC_FILE = "vanilla-stats-last-sync.yml";

    private final MCVerseRegister plugin;
    private final MCVerseApiClient apiClient;
    private final VanillaStatsSnapshotter snapshotter;
    private final Map<UUID, Instant> lastSuccessfulAt = new ConcurrentHashMap<>();

    public VanillaStatsSyncService(
            MCVerseRegister plugin,
            PlayerDataAdapter<BalanceSnapshot> balanceAdapter,
            PlayerDataAdapter<GroupsSnapshot> groupsAdapter
    ) {
        this.plugin = plugin;
        this.apiClient = plugin.getApiClient();
        this.snapshotter = new VanillaStatsSnapshotter(balanceAdapter, groupsAdapter);
        loadLastSuccessful();
    }

    VanillaStatsSyncService(
            MCVerseRegister plugin,
            MCVerseApiClient apiClient,
            VanillaStatsSnapshotter snapshotter
    ) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.snapshotter = snapshotter;
        loadLastSuccessful();
    }

    /**
     * Must be called on the main thread during {@code PlayerQuitEvent}, before
     * registration cache cleanup. Snapshots immediately, then POSTs async.
     */
    public void handleQuit(Player player) {
        if (!enabled()) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Instant now = Instant.now();
        if (!hasElapsed(uuid, now)) {
            plugin.getLogger().info("[vanilla-stats] skipped uuid=" + uuid + " reason=throttle");
            return;
        }

        VanillaStatsSnapshot snapshot;
        try {
            snapshot = snapshotter.snapshot(player);
        } catch (Exception e) {
            plugin.getLogger().warning("[vanilla-stats] snapshot failed uuid=" + uuid + " error=" + e.getMessage());
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> postSnapshot(uuid, snapshot));
    }

    boolean hasElapsed(UUID uuid, Instant now) {
        Instant last = lastSuccessfulAt.get(uuid);
        if (last == null) {
            return true;
        }
        return Duration.between(last, now).compareTo(minInterval()) >= 0;
    }

    private void postSnapshot(UUID uuid, VanillaStatsSnapshot snapshot) {
        VanillaStatsSyncRequest payload = new VanillaStatsSyncRequest(snapshot);
        try {
            ApiResponse response = executeWithRetry(() -> apiClient.syncVanillaStats(uuid, payload));
            int status = response.getStatusCode();
            if (status == 404) {
                plugin.getLogger().warning("[vanilla-stats] backend route missing (404) uuid=" + uuid
                        + "; will retry next quit");
                return;
            }
            if (status == 200) {
                Instant successAt = snapshot.observedAt() == null ? Instant.now() : snapshot.observedAt();
                lastSuccessfulAt.put(uuid, successAt);
                saveLastSuccessful();
                plugin.getLogger().info("[vanilla-stats] posted uuid=" + uuid + " payload={" + payload.summary() + "}");
                return;
            }
            plugin.getLogger().warning("[vanilla-stats] failure uuid=" + uuid + " status=" + status);
        } catch (Exception e) {
            plugin.getLogger().warning("[vanilla-stats] failure uuid=" + uuid + " error=" + e.getMessage());
        }
    }

    private void loadLastSuccessful() {
        File file = new File(plugin.getDataFolder(), LAST_SYNC_FILE);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String raw = yaml.getString(key);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                lastSuccessfulAt.put(uuid, Instant.parse(raw));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                plugin.getLogger().warning("[vanilla-stats] skipped invalid last-sync entry key=" + key);
            }
        }
    }

    private void saveLastSuccessful() {
        File file = new File(plugin.getDataFolder(), LAST_SYNC_FILE);
        YamlConfiguration yaml = new YamlConfiguration();
        lastSuccessfulAt.forEach((uuid, instant) -> yaml.set(uuid.toString(), instant.toString()));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[vanilla-stats] failed to persist last sync: " + e.getMessage());
        }
    }

    private ApiResponse executeWithRetry(RetryableCall call) throws Exception {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                ApiResponse response = call.execute();
                if (!isRetryableStatus(response.getStatusCode()) || attempt >= maxAttempts()) {
                    return response;
                }
                waitForRetry(attempt, "status=" + response.getStatusCode());
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                if (!(cause instanceof IOException) || attempt >= maxAttempts()) {
                    throw e;
                }
                waitForRetry(attempt, "io_exception");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (IOException e) {
                if (attempt >= maxAttempts()) {
                    throw e;
                }
                waitForRetry(attempt, "io_exception");
            }
        }
    }

    private void waitForRetry(int attempt, String reason) throws InterruptedException {
        long delay = Math.min(baseBackoffMillis() * (1L << Math.max(0, attempt - 1)), maxBackoffMillis())
                + ThreadLocalRandom.current().nextLong(0L, 101L);
        plugin.getLogger().info("[vanilla-stats] retry attempt=" + (attempt + 1) + " reason=" + reason + " delayMs=" + delay);
        Thread.sleep(delay);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("sync.vanilla-stats.enabled", true);
    }

    private Duration minInterval() {
        long hours = Math.max(1L, plugin.getConfig().getLong("sync.vanilla-stats.min-interval-hours", 24L));
        return Duration.ofHours(hours);
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode >= 500 && statusCode <= 599;
    }

    private int maxAttempts() {
        return plugin.getConfig().getInt("sync.retry.max-attempts", 3);
    }

    private long baseBackoffMillis() {
        return plugin.getConfig().getLong("sync.retry.base-backoff-ms", 250L);
    }

    private long maxBackoffMillis() {
        return plugin.getConfig().getLong("sync.retry.max-backoff-ms", 2000L);
    }

    @FunctionalInterface
    interface RetryableCall {
        ApiResponse execute() throws Exception;
    }
}
