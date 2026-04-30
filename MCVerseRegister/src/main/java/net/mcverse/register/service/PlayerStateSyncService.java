package net.mcverse.register.service;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;
import net.mcverse.register.api.BalanceSyncRequest;
import net.mcverse.register.api.GriefPreventionClaimsSyncRequest;
import net.mcverse.register.api.GroupsSyncRequest;
import net.mcverse.register.api.MCVerseApiClient;
import net.mcverse.register.api.PlayerStateSyncResponse;
import net.mcverse.register.api.SimpleClansSyncRequest;
import net.mcverse.register.integration.BalanceSnapshot;
import net.mcverse.register.integration.ClanSnapshot;
import net.mcverse.register.integration.ClaimsSnapshot;
import net.mcverse.register.integration.GroupsSnapshot;
import net.mcverse.register.integration.PlayerDataAdapter;
import net.mcverse.register.util.ExpiringUuidCache;

import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerStateSyncService {

    private static final String CATEGORY_BALANCE = "balance";
    private static final String CATEGORY_GROUPS = "groups";
    private static final String CATEGORY_SIMPLECLANS = "simpleclans";
    private static final String CATEGORY_CLAIMS = "griefprevention-claims";

    private final MCVerseRegister plugin;
    private final MCVerseApiClient apiClient;
    private final PlayerDataAdapter<BalanceSnapshot> balanceAdapter;
    private final PlayerDataAdapter<GroupsSnapshot> groupsAdapter;
    private final PlayerDataAdapter<ClanSnapshot> clansAdapter;
    private final PlayerDataAdapter<ClaimsSnapshot> claimsAdapter;
    private final ExpiringUuidCache unlinkedCache = new ExpiringUuidCache();
    private final Map<String, Long> lastSyncedAt = new ConcurrentHashMap<>();

    public PlayerStateSyncService(
            MCVerseRegister plugin,
            PlayerDataAdapter<BalanceSnapshot> balanceAdapter,
            PlayerDataAdapter<GroupsSnapshot> groupsAdapter,
            PlayerDataAdapter<ClanSnapshot> clansAdapter,
            PlayerDataAdapter<ClaimsSnapshot> claimsAdapter
    ) {
        this.plugin = plugin;
        this.apiClient = plugin.getApiClient();
        this.balanceAdapter = balanceAdapter;
        this.groupsAdapter = groupsAdapter;
        this.clansAdapter = clansAdapter;
        this.claimsAdapter = claimsAdapter;
    }

    public void syncPlayer(Player player, String trigger) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (unlinkedCache.containsActive(uuid, now)) {
            plugin.getLogger().info("[diag-sync] skipped uuid=" + uuid + " reason=unlinked_cache trigger=" + trigger);
            return;
        }

        CompletableFuture<?> balanceFuture = syncBalance(player, trigger, now);
        CompletableFuture<?> groupsFuture = syncGroups(player, trigger, now);
        CompletableFuture<?> clansFuture = syncSimpleClans(player, trigger, now);
        CompletableFuture<?> claimsFuture = syncClaims(player, trigger, now);
        CompletableFuture.allOf(balanceFuture, groupsFuture, clansFuture, claimsFuture).join();
    }

    private CompletableFuture<Void> syncBalance(Player player, String trigger, long now) {
        if (!shouldSync(player.getUniqueId(), CATEGORY_BALANCE, now)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> balanceAdapter.snapshot(player).ifPresent(snapshot -> {
            BalanceSyncRequest payload = new BalanceSyncRequest(snapshot.balance(), Instant.now());
            performSync(player.getUniqueId(), CATEGORY_BALANCE, payload.summary(), () -> apiClient.syncBalance(player.getUniqueId(), payload), trigger);
        }));
    }

    private CompletableFuture<Void> syncGroups(Player player, String trigger, long now) {
        if (!shouldSync(player.getUniqueId(), CATEGORY_GROUPS, now)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> groupsAdapter.snapshot(player).ifPresent(snapshot -> {
            GroupsSyncRequest payload = new GroupsSyncRequest(snapshot.primaryGroup(), snapshot.groups(), Instant.now());
            performSync(player.getUniqueId(), CATEGORY_GROUPS, payload.summary(), () -> apiClient.syncGroups(player.getUniqueId(), payload), trigger);
        }));
    }

    private CompletableFuture<Void> syncSimpleClans(Player player, String trigger, long now) {
        if (!shouldSync(player.getUniqueId(), CATEGORY_SIMPLECLANS, now)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> clansAdapter.snapshot(player).ifPresent(snapshot -> {
            SimpleClansSyncRequest payload = new SimpleClansSyncRequest(
                    snapshot.clanTag(),
                    snapshot.clanName(),
                    snapshot.clanRole(),
                    Instant.now()
            );
            performSync(player.getUniqueId(), CATEGORY_SIMPLECLANS, payload.summary(), () -> apiClient.syncSimpleClans(player.getUniqueId(), payload), trigger);
        }));
    }

    private CompletableFuture<Void> syncClaims(Player player, String trigger, long now) {
        if (!shouldSync(player.getUniqueId(), CATEGORY_CLAIMS, now)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> claimsAdapter.snapshot(player).ifPresent(snapshot -> {
            GriefPreventionClaimsSyncRequest payload = new GriefPreventionClaimsSyncRequest(
                    snapshot.claimCount(),
                    snapshot.accruedClaimBlocks(),
                    snapshot.bonusClaimBlocks(),
                    snapshot.remainingClaimBlocks(),
                    snapshot.claims(),
                    Instant.now()
            );
            performSync(player.getUniqueId(), CATEGORY_CLAIMS, payload.summary(), () -> apiClient.syncGriefPreventionClaims(player.getUniqueId(), payload), trigger);
        }));
    }

    private void performSync(UUID uuid, String category, String payloadSummary, RetryableCall call, String trigger) {
        try {
            ApiResponse response = executeWithRetry(category, call);
            int statusCode = response.getStatusCode();
            if (statusCode == 404) {
                unlinkedCache.put(uuid, unlinkedCacheTtlMillis(), System.currentTimeMillis());
                plugin.getLogger().info("[diag-sync] result uuid=" + uuid + " category=" + category + " trigger=" + trigger + " status=404 unlinked=true");
                return;
            }
            if (statusCode == 422) {
                plugin.getLogger().warning("[diag-sync] validation uuid=" + uuid + " category=" + category
                        + " trigger=" + trigger + " status=422 payload={" + payloadSummary + "}");
                return;
            }
            if (statusCode == 200) {
                PlayerStateSyncResponse parsed = PlayerStateSyncResponse.fromApiResponse(response);
                plugin.getLogger().info("[diag-sync] result uuid=" + uuid + " category=" + category
                        + " trigger=" + trigger + " status=200 updated=" + parsed.updated());
                return;
            }
            plugin.getLogger().warning("[diag-sync] failure uuid=" + uuid + " category=" + category
                    + " trigger=" + trigger + " status=" + statusCode);
        } catch (Exception e) {
            plugin.getLogger().warning("[diag-sync] failure uuid=" + uuid + " category=" + category
                    + " trigger=" + trigger + " error=" + e.getMessage());
        }
    }

    private ApiResponse executeWithRetry(String category, RetryableCall call) throws Exception {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                ApiResponse response = call.execute();
                if (!isRetryableStatus(response.getStatusCode()) || attempt >= maxAttempts()) {
                    return response;
                }
                waitForRetry(category, attempt, "status=" + response.getStatusCode());
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                if (!(cause instanceof IOException) || attempt >= maxAttempts()) {
                    throw e;
                }
                waitForRetry(category, attempt, "io_exception");
            } catch (Exception e) {
                if (!(e instanceof IOException) || attempt >= maxAttempts()) {
                    throw e;
                }
                waitForRetry(category, attempt, "io_exception");
            }
        }
    }

    private void waitForRetry(String category, int attempt, String reason) throws InterruptedException {
        long delay = Math.min(baseBackoffMillis() * (1L << Math.max(0, attempt - 1)), maxBackoffMillis())
                + ThreadLocalRandom.current().nextLong(0L, 101L);
        plugin.getLogger().info("[diag-sync] retry category=" + category
                + " attempt=" + (attempt + 1)
                + " reason=" + reason
                + " delayMs=" + delay);
        Thread.sleep(delay);
    }

    private boolean shouldSync(UUID uuid, String category, long now) {
        String key = uuid + ":" + category;
        long minInterval = minSyncIntervalMillis();
        Long last = lastSyncedAt.get(key);
        if (last != null && now - last < minInterval) {
            return false;
        }
        lastSyncedAt.put(key, now);
        return true;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode >= 500 && statusCode <= 599;
    }

    private long minSyncIntervalMillis() {
        return plugin.getConfig().getLong("sync.min-sync-interval-ms", 30_000L);
    }

    private long unlinkedCacheTtlMillis() {
        return plugin.getConfig().getLong("sync.unlinked-cache-ttl-ms", 300_000L);
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
