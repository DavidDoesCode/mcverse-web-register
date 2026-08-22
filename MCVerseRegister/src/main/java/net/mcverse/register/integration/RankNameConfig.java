package net.mcverse.register.integration;

import org.bukkit.configuration.file.FileConfiguration;

public record RankNameConfig(String defaultRank, String memberRank, String regularRank, String citizenRank) {

    public static RankNameConfig fromConfig(FileConfiguration config) {
        return new RankNameConfig(
                config.getString("sync.server-stats.ranks.default", "default"),
                config.getString("sync.server-stats.ranks.member", "member"),
                config.getString("sync.server-stats.ranks.regular", "regular"),
                config.getString("sync.server-stats.ranks.citizen", "citizen")
        );
    }
}
