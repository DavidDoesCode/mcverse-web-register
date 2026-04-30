package net.mcverse.register.service;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;
import net.mcverse.register.api.MCVerseApiClient;
import net.mcverse.register.integration.BalanceSnapshot;
import net.mcverse.register.integration.ClanSnapshot;
import net.mcverse.register.integration.ClaimsSnapshot;
import net.mcverse.register.integration.GroupsSnapshot;
import net.mcverse.register.integration.PlayerDataAdapter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStateSyncServiceTest {

    private MCVerseApiClient apiClient;
    private Player player;
    private PlayerDataAdapter<BalanceSnapshot> balanceAdapter;
    private PlayerDataAdapter<GroupsSnapshot> groupsAdapter;
    private PlayerDataAdapter<ClanSnapshot> clansAdapter;
    private PlayerDataAdapter<ClaimsSnapshot> claimsAdapter;
    private PlayerStateSyncService service;

    @BeforeEach
    void setUp() {
        MCVerseRegister plugin = mock(MCVerseRegister.class);
        apiClient = mock(MCVerseApiClient.class);
        player = mock(Player.class);

        balanceAdapter = staticAdapter(new BalanceSnapshot(100.0D));
        groupsAdapter = staticAdapter(new GroupsSnapshot("vip", List.of("default", "vip")));
        clansAdapter = staticAdapter(new ClanSnapshot("MCV", "MCVerse", "MEMBER"));
        claimsAdapter = staticAdapter(new ClaimsSnapshot(1, 100, 50, 20, List.of()));

        YamlConfiguration config = new YamlConfiguration();
        config.set("sync.min-sync-interval-ms", 0L);
        config.set("sync.unlinked-cache-ttl-ms", 600_000L);
        config.set("sync.retry.max-attempts", 1);
        config.set("sync.retry.base-backoff-ms", 1L);
        config.set("sync.retry.max-backoff-ms", 1L);

        when(plugin.getApiClient()).thenReturn(apiClient);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PlayerStateSyncServiceTest"));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        service = new PlayerStateSyncService(plugin, balanceAdapter, groupsAdapter, clansAdapter, claimsAdapter);
    }

    @Test
    void fanoutCallsAllCategoryEndpoints() throws Exception {
        when(apiClient.syncBalance(any(), any())).thenReturn(new ApiResponse(200, "{\"success\":true,\"registered\":true,\"updated\":true}"));
        when(apiClient.syncGroups(any(), any())).thenReturn(new ApiResponse(200, "{\"success\":true,\"registered\":true,\"updated\":true}"));
        when(apiClient.syncSimpleClans(any(), any())).thenReturn(new ApiResponse(200, "{\"success\":true,\"registered\":true,\"updated\":true}"));
        when(apiClient.syncGriefPreventionClaims(any(), any())).thenReturn(new ApiResponse(200, "{\"success\":true,\"registered\":true,\"updated\":true}"));

        service.syncPlayer(player, "join");

        verify(apiClient, times(1)).syncBalance(any(), any());
        verify(apiClient, times(1)).syncGroups(any(), any());
        verify(apiClient, times(1)).syncSimpleClans(any(), any());
        verify(apiClient, times(1)).syncGriefPreventionClaims(any(), any());
    }

    @Test
    void unlinked404SuppressesFurtherSyncWithinTtl() throws Exception {
        when(apiClient.syncBalance(any(), any())).thenReturn(new ApiResponse(404, "{\"success\":false,\"registered\":false}"));
        when(apiClient.syncGroups(any(), any())).thenReturn(new ApiResponse(200, "{\"success\":true,\"registered\":true,\"updated\":true}"));
        when(apiClient.syncSimpleClans(any(), any())).thenReturn(new ApiResponse(200, "{\"success\":true,\"registered\":true,\"updated\":true}"));
        when(apiClient.syncGriefPreventionClaims(any(), any())).thenReturn(new ApiResponse(200, "{\"success\":true,\"registered\":true,\"updated\":true}"));

        service.syncPlayer(player, "join");
        service.syncPlayer(player, "join");

        verify(apiClient, times(1)).syncBalance(any(), any());
        verify(apiClient, times(1)).syncGroups(any(), any());
        verify(apiClient, times(1)).syncSimpleClans(any(), any());
        verify(apiClient, times(1)).syncGriefPreventionClaims(any(), any());
    }

    @Test
    void missingAdaptersSkipCategoryCalls() throws Exception {
        when(apiClient.syncBalance(any(), any())).thenReturn(new ApiResponse(200, "{}"));

        PlayerStateSyncService partialService = new PlayerStateSyncService(
                mockPluginForPartial(),
                staticAdapter(new BalanceSnapshot(25.0D)),
                unavailableAdapter(),
                unavailableAdapter(),
                unavailableAdapter()
        );

        partialService.syncPlayer(player, "join");

        verify(apiClient, times(1)).syncBalance(any(), any());
        verify(apiClient, never()).syncGroups(eq(player.getUniqueId()), any());
        verify(apiClient, never()).syncSimpleClans(eq(player.getUniqueId()), any());
        verify(apiClient, never()).syncGriefPreventionClaims(eq(player.getUniqueId()), any());
    }

    private MCVerseRegister mockPluginForPartial() {
        MCVerseRegister plugin = mock(MCVerseRegister.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("sync.min-sync-interval-ms", 0L);
        config.set("sync.unlinked-cache-ttl-ms", 600_000L);
        config.set("sync.retry.max-attempts", 1);
        config.set("sync.retry.base-backoff-ms", 1L);
        config.set("sync.retry.max-backoff-ms", 1L);

        when(plugin.getApiClient()).thenReturn(apiClient);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("PlayerStateSyncServiceTest"));
        return plugin;
    }

    private static <T> PlayerDataAdapter<T> staticAdapter(T value) {
        return new PlayerDataAdapter<>() {
            @Override
            public String adapterName() {
                return "static";
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public Optional<T> snapshot(Player player) {
                return Optional.of(value);
            }
        };
    }

    private static <T> PlayerDataAdapter<T> unavailableAdapter() {
        return new PlayerDataAdapter<>() {
            @Override
            public String adapterName() {
                return "none";
            }

            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public Optional<T> snapshot(Player player) {
                return Optional.empty();
            }
        };
    }
}
