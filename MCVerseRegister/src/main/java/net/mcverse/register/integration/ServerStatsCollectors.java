package net.mcverse.register.integration;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Logger;

public final class ServerStatsCollectors {

    private ServerStatsCollectors() {
    }

    public static List<ServerStatsCollector> resolveAsync(JavaPlugin plugin) {
        Logger logger = plugin.getLogger();
        long planActivePlaytimeMs = plugin.getConfig().getLong("sync.server-stats.plan-active-playtime-ms", 1_800_000L);
        RankNameConfig ranks = RankNameConfig.fromConfig(plugin.getConfig());

        return List.of(
                resolvePlan(logger, planActivePlaytimeMs),
                resolveLuckPerms(ranks, logger),
                resolveVault(logger),
                new PaperTpsCollector()
        );
    }

    public static List<ServerStatsCollector> resolveMainThread(JavaPlugin plugin) {
        String worldName = plugin.getConfig().getString("sync.server-stats.main-world", "world");
        return List.of(
                resolveGriefPrevention(),
                new MinecraftDayCollector(worldName)
        );
    }

    private static ServerStatsCollector resolvePlan(Logger logger, long planActivePlaytimeMs) {
        try {
            PlanServerStatsCollector collector = new PlanServerStatsCollector(logger, planActivePlaytimeMs);
            if (collector.isAvailable()) {
                logger.info("Server stats: PLAN collector enabled.");
                return collector;
            }
        } catch (NoClassDefFoundError ignored) {
            // Plan API classes are absent when the plugin is not installed.
        }
        logger.info("Server stats: PLAN not available; PLAN metrics omitted.");
        return new NoopServerStatsCollector("plan");
    }

    private static ServerStatsCollector resolveLuckPerms(RankNameConfig ranks, Logger logger) {
        LuckPermsRankCountsCollector collector = new LuckPermsRankCountsCollector(ranks, logger);
        if (collector.isAvailable()) {
            logger.info("Server stats: LuckPerms rank collector enabled.");
            return collector;
        }
        logger.info("Server stats: LuckPerms not available; rank counts omitted.");
        return new NoopServerStatsCollector("luckperms-ranks");
    }

    private static ServerStatsCollector resolveVault(Logger logger) {
        VaultEconomyTotalCollector collector = new VaultEconomyTotalCollector(logger);
        if (collector.isAvailable()) {
            logger.info("Server stats: Vault economy total collector enabled.");
            return collector;
        }
        logger.info("Server stats: Vault not available; economy total omitted.");
        return new NoopServerStatsCollector("vault-economy-total");
    }

    private static ServerStatsCollector resolveGriefPrevention() {
        GriefPreventionAreaCollector collector = new GriefPreventionAreaCollector();
        if (collector.isAvailable()) {
            return collector;
        }
        return new NoopServerStatsCollector("griefprevention-area", true);
    }
}
