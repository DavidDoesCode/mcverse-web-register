package net.mcverse.register.service;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;
import net.mcverse.register.api.MCVerseApiClient;
import net.mcverse.register.api.ServerStatsSyncRequest;
import net.mcverse.register.integration.ServerStatsCollector;
import net.mcverse.register.integration.ServerStatsCollectors;
import net.mcverse.register.integration.ServerStatsSnapshot;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerStatsSyncService {

    private static final String LAST_RUN_FILE = "server-stats-last-run.yml";

    private final MCVerseRegister plugin;
    private final MCVerseApiClient apiClient;
    private final List<ServerStatsCollector> asyncCollectors;
    private final List<ServerStatsCollector> mainThreadCollectors;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private BukkitTask scheduledTask;

    public ServerStatsSyncService(MCVerseRegister plugin) {
        this.plugin = plugin;
        this.apiClient = plugin.getApiClient();
        this.asyncCollectors = ServerStatsCollectors.resolveAsync(plugin);
        this.mainThreadCollectors = ServerStatsCollectors.resolveMainThread(plugin);
    }

    ServerStatsSyncService(
            MCVerseRegister plugin,
            MCVerseApiClient apiClient,
            List<ServerStatsCollector> asyncCollectors,
            List<ServerStatsCollector> mainThreadCollectors
    ) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.asyncCollectors = asyncCollectors;
        this.mainThreadCollectors = mainThreadCollectors;
    }

    public void start() {
        if (!enabled()) {
            plugin.getLogger().info("Server stats sync is disabled.");
            return;
        }
        Instant now = Instant.now();
        Duration delay = ServerStatsSchedule.delayUntilInitialTrigger(
                now, runAt(), zone(), loadLastSuccessfulAt(), catchUpOnStartup()
        );
        plugin.getLogger().info("Server stats sync scheduled in " + delay.toSeconds() + "s (run-at="
                + runAt() + " zone=" + zone() + ").");
        schedule(delay);
    }

    public void shutdown() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    /**
     * Force a snapshot for {@code /mcvadmin syncstats}. Does not rewrite the daily slot.
     */
    public boolean runForced() {
        return runSnapshot(false);
    }

    private void schedule(Duration delay) {
        long ticks = ServerStatsSchedule.toTicks(delay);
        scheduledTask = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, this::runScheduled, ticks);
    }

    private void runScheduled() {
        try {
            runSnapshot(true);
        } finally {
            if (enabled() && plugin.isEnabled()) {
                schedule(ServerStatsSchedule.delayUntilFollowingRunAt(Instant.now(), runAt(), zone()));
            }
        }
    }

    private boolean runSnapshot(boolean persistDailySuccess) {
        if (!enabled()) {
            plugin.getLogger().info("[server-stats] skipped reason=disabled");
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            plugin.getLogger().info("[server-stats] skipped reason=already_running");
            return false;
        }
        long started = System.currentTimeMillis();
        try {
            Instant observedAt = Instant.now();
            ServerStatsSnapshot.Builder builder = ServerStatsSnapshot.builder()
                    .observedAt(observedAt)
                    .weekStart(observedAt.minusSeconds(7L * 24L * 60L * 60L));

            collect(asyncCollectors, builder);
            hopToMainThread(() -> {
                collect(mainThreadCollectors, builder);
                return null;
            });

            ServerStatsSnapshot snapshot = builder.build();
            ServerStatsSyncRequest payload = new ServerStatsSyncRequest(snapshot);
            ApiResponse response = executeWithRetry(() -> apiClient.syncServerStats(payload));
            int status = response.getStatusCode();
            if (status == 404) {
                plugin.getLogger().warning("[server-stats] backend route missing (404); skipping until next run. payload={"
                        + payload.summary() + "}");
                return false;
            }
            if (status == 200) {
                plugin.getLogger().info("[server-stats] posted status=200 elapsedMs="
                        + (System.currentTimeMillis() - started) + " payload={" + payload.summary() + "}");
                if (persistDailySuccess) {
                    saveLastSuccessfulAt(observedAt);
                }
                return true;
            }
            plugin.getLogger().warning("[server-stats] failure status=" + status + " payload={" + payload.summary() + "}");
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("[server-stats] failure error=" + e.getMessage());
            return false;
        } finally {
            running.set(false);
        }
    }

    private void collect(List<ServerStatsCollector> collectors, ServerStatsSnapshot.Builder builder) {
        for (ServerStatsCollector collector : collectors) {
            if (!collector.isAvailable()) {
                continue;
            }
            try {
                collector.collect(builder);
            } catch (Exception e) {
                plugin.getLogger().warning("[server-stats] collector=" + collector.name() + " failed: " + e.getMessage());
            }
        }
    }

    private void hopToMainThread(Callable<Void> task) throws Exception {
        if (plugin.getServer().isPrimaryThread()) {
            task.call();
            return;
        }
        plugin.getServer().getScheduler().callSyncMethod(plugin, task).get();
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
        plugin.getLogger().info("[server-stats] retry attempt=" + (attempt + 1) + " reason=" + reason + " delayMs=" + delay);
        Thread.sleep(delay);
    }

    Instant loadLastSuccessfulAt() {
        File file = new File(plugin.getDataFolder(), LAST_RUN_FILE);
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String raw = yaml.getString("lastSuccessfulAt");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("[server-stats] invalid lastSuccessfulAt=" + raw);
            return null;
        }
    }

    void saveLastSuccessfulAt(Instant instant) {
        File file = new File(plugin.getDataFolder(), LAST_RUN_FILE);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("lastSuccessfulAt", instant.toString());
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[server-stats] failed to persist last run: " + e.getMessage());
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("sync.server-stats.enabled", true);
    }

    private boolean catchUpOnStartup() {
        return plugin.getConfig().getBoolean("sync.server-stats.catch-up-on-startup", true);
    }

    private LocalTime runAt() {
        String raw = plugin.getConfig().getString("sync.server-stats.run-at", "10:00");
        try {
            return LocalTime.parse(raw);
        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("[server-stats] invalid run-at=" + raw + "; using 10:00");
            return LocalTime.of(10, 0);
        }
    }

    private ZoneId zone() {
        String raw = plugin.getConfig().getString("sync.server-stats.timezone", "America/Chicago");
        if (raw == null || raw.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(raw);
        } catch (Exception e) {
            plugin.getLogger().warning("[server-stats] invalid timezone=" + raw + "; using JVM default");
            return ZoneId.systemDefault();
        }
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
