package net.mcverse.register.service;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;
import net.mcverse.register.api.MCVerseApiClient;
import net.mcverse.register.api.ServerStatsSyncRequest;
import net.mcverse.register.integration.ServerStatsCollector;
import net.mcverse.register.integration.ServerStatsSnapshot;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerStatsSyncServiceTest {

    @TempDir
    Path tempDir;

    private MCVerseRegister plugin;
    private MCVerseApiClient apiClient;
    private YamlConfiguration config;

    @BeforeEach
    void setUp() {
        plugin = mock(MCVerseRegister.class);
        apiClient = mock(MCVerseApiClient.class);
        config = new YamlConfiguration();
        config.set("sync.retry.max-attempts", 1);
        config.set("sync.retry.base-backoff-ms", 1L);
        config.set("sync.retry.max-backoff-ms", 1L);

        Server server = mock(Server.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ServerStatsSyncServiceTest"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.isPrimaryThread()).thenReturn(true);
    }

    @Test
    void postsWhenEnabled() throws Exception {
        config.set("sync.server-stats.enabled", true);
        when(apiClient.syncServerStats(any())).thenReturn(new ApiResponse(200, "{\"success\":true,\"updated\":true}"));

        ServerStatsCollector filler = new ServerStatsCollector() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public void collect(ServerStatsSnapshot.Builder builder) {
                builder.playersJoined(10L).rankCitizen(2).citizenAll(4);
            }
        };

        ServerStatsSyncService service = new ServerStatsSyncService(plugin, apiClient, List.of(filler), List.of());
        assertTrue(service.runForced());
        verify(apiClient, times(1)).syncServerStats(any(ServerStatsSyncRequest.class));
    }

    @Test
    void skipsPostWhenDisabled() throws Exception {
        config.set("sync.server-stats.enabled", false);
        ServerStatsSyncService service = new ServerStatsSyncService(plugin, apiClient, List.of(), List.of());
        assertFalse(service.runForced());
        verify(apiClient, never()).syncServerStats(any());
    }
}
