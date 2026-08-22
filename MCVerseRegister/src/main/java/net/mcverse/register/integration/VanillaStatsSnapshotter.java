package net.mcverse.register.integration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a Mojang-shaped vanilla stats snapshot. Must run on the main thread
 * while the {@link Player} from {@code PlayerQuitEvent} is still valid.
 */
public final class VanillaStatsSnapshotter {

    private final PlayerDataAdapter<BalanceSnapshot> balanceAdapter;
    private final PlayerDataAdapter<GroupsSnapshot> groupsAdapter;

    public VanillaStatsSnapshotter(
            PlayerDataAdapter<BalanceSnapshot> balanceAdapter,
            PlayerDataAdapter<GroupsSnapshot> groupsAdapter
    ) {
        this.balanceAdapter = balanceAdapter;
        this.groupsAdapter = groupsAdapter;
    }

    public VanillaStatsSnapshot snapshot(Player player) {
        Instant observedAt = Instant.now();
        Map<String, Map<String, Long>> stats = new LinkedHashMap<>();

        for (Statistic statistic : Registry.STATISTIC) {
            String category = categoryKey(statistic);
            switch (statistic.getType()) {
                case UNTYPED -> putIfPositive(stats, category, namespaced(statistic.getKey()), untypedValue(player, statistic));
                case BLOCK -> addMaterialStats(player, statistic, category, true, stats);
                case ITEM -> addMaterialStats(player, statistic, category, false, stats);
                case ENTITY -> addEntityStats(player, statistic, category, stats);
            }
        }

        stats.entrySet().removeIf(entry -> entry.getValue().isEmpty());

        Double balance = Optional.ofNullable(balanceAdapter)
                .filter(PlayerDataAdapter::isAvailable)
                .flatMap(adapter -> adapter.snapshot(player))
                .map(BalanceSnapshot::balance)
                .orElse(null);
        String primaryGroup = Optional.ofNullable(groupsAdapter)
                .filter(PlayerDataAdapter::isAvailable)
                .flatMap(adapter -> adapter.snapshot(player))
                .map(GroupsSnapshot::primaryGroup)
                .orElse(null);

        long firstPlayedMs = player.getFirstPlayed();
        Instant firstPlayed = firstPlayedMs > 0L ? Instant.ofEpochMilli(firstPlayedMs) : observedAt;

        return new VanillaStatsSnapshot(
                observedAt,
                Bukkit.getMinecraftVersion(),
                player.getName(),
                firstPlayed,
                observedAt,
                balance,
                primaryGroup,
                stats
        );
    }

    private void addMaterialStats(
            Player player,
            Statistic statistic,
            String category,
            boolean blocksOnly,
            Map<String, Map<String, Long>> stats
    ) {
        for (Material material : Registry.MATERIAL) {
            if (blocksOnly && !material.isBlock()) {
                continue;
            }
            if (!blocksOnly && !material.isItem()) {
                continue;
            }
            long value = typedMaterialValue(player, statistic, material);
            putIfPositive(stats, category, namespaced(material.getKey()), value);
        }
    }

    private void addEntityStats(
            Player player,
            Statistic statistic,
            String category,
            Map<String, Map<String, Long>> stats
    ) {
        for (EntityType entityType : Registry.ENTITY_TYPE) {
            long value = typedEntityValue(player, statistic, entityType);
            putIfPositive(stats, category, namespaced(entityType.getKey()), value);
        }
    }

    private static long untypedValue(Player player, Statistic statistic) {
        try {
            return player.getStatistic(statistic);
        } catch (IllegalArgumentException ignored) {
            return 0L;
        }
    }

    private static long typedMaterialValue(Player player, Statistic statistic, Material material) {
        try {
            return player.getStatistic(statistic, material);
        } catch (IllegalArgumentException ignored) {
            return 0L;
        }
    }

    private static long typedEntityValue(Player player, Statistic statistic, EntityType entityType) {
        try {
            return player.getStatistic(statistic, entityType);
        } catch (IllegalArgumentException ignored) {
            return 0L;
        }
    }

    private static void putIfPositive(
            Map<String, Map<String, Long>> stats,
            String category,
            String key,
            long value
    ) {
        if (value <= 0L || key == null) {
            return;
        }
        stats.computeIfAbsent(category, ignored -> new LinkedHashMap<>()).put(key, value);
    }

    static String categoryKey(Statistic statistic) {
        if (statistic.getType() == Statistic.Type.UNTYPED) {
            return "minecraft:custom";
        }
        return switch (statistic) {
            case MINE_BLOCK -> "minecraft:mined";
            case BREAK_ITEM -> "minecraft:broken";
            case CRAFT_ITEM -> "minecraft:crafted";
            case USE_ITEM -> "minecraft:used";
            case PICKUP -> "minecraft:picked_up";
            case DROP -> "minecraft:dropped";
            case KILL_ENTITY -> "minecraft:killed";
            case ENTITY_KILLED_BY -> "minecraft:killed_by";
            default -> namespaced(statistic.getKey());
        };
    }

    private static String namespaced(NamespacedKey key) {
        return key == null ? null : key.asString();
    }
}
