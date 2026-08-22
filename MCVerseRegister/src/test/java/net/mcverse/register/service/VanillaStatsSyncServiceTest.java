package net.mcverse.register.service;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;
import net.mcverse.register.api.MCVerseApiClient;
import net.mcverse.register.api.VanillaStatsSyncRequest;
import net.mcverse.register.integration.VanillaStatsSnapshot;
import net.mcverse.register.integration.VanillaStatsSnapshotter;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VanillaStatsSyncServiceTest {

    @TempDir
    Path tempDir;

    private MCVerseRegister plugin;
    private MCVerseApiClient apiClient;
    private VanillaStatsSnapshotter snapshotter;
    private Player player;
    private UUID uuid;
    private VanillaStatsSyncService service;

    @BeforeEach
    void setUp() {
        plugin = mock(MCVerseRegister.class);
        apiClient = mock(MCVerseApiClient.class);
        snapshotter = mock(VanillaStatsSnapshotter.class);
        player = mock(Player.class);
        uuid = UUID.randomUUID();

        YamlConfiguration config = new YamlConfiguration();
        config.set("sync.vanilla-stats.enabled", true);
        config.set("sync.vanilla-stats.min-interval-hours", 24L);
        config.set("sync.retry.max-attempts", 1);
        config.set("sync.retry.base-backoff-ms", 1L);
        config.set("sync.retry.max-backoff-ms", 1L);

        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("VanillaStatsSyncServiceTest"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return mock(BukkitTask.class);
        });
        when(player.getUniqueId()).thenReturn(uuid);

        Instant observedAt = Instant.parse("2026-08-21T22:15:00Z");
        VanillaStatsSnapshot snapshot = new VanillaStatsSnapshot(
                observedAt,
                "1.21.11",
                "Steve",
                observedAt,
                observedAt,
                null,
                null,
                Map.of("minecraft:custom", Map.of("minecraft:deaths", 1L))
        );
        when(snapshotter.snapshot(player)).thenReturn(snapshot);

        service = new VanillaStatsSyncService(plugin, apiClient, snapshotter);
    }

    @Test
    void throttleSkipsSecondQuitInside24h() throws Exception {
        when(apiClient.syncVanillaStats(eq(uuid), any(VanillaStatsSyncRequest.class)))
                .thenReturn(new ApiResponse(200, "{\"success\":true,\"updated\":true}"));

        service.handleQuit(player);
        service.handleQuit(player);

        verify(apiClient, times(1)).syncVanillaStats(eq(uuid), any(VanillaStatsSyncRequest.class));
        verify(snapshotter, times(1)).snapshot(player);
    }
}
