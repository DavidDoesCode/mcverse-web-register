package net.mcverse.register.integration;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class GriefPreventionAreaCollector implements ServerStatsCollector {

    private final GriefPrevention griefPrevention;

    public GriefPreventionAreaCollector() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        this.griefPrevention = plugin instanceof GriefPrevention gp ? gp : null;
    }

    @Override
    public String name() {
        return "griefprevention-area";
    }

    @Override
    public boolean isAvailable() {
        return griefPrevention != null && griefPrevention.isEnabled();
    }

    @Override
    public boolean requiresMainThread() {
        return true;
    }

    @Override
    public void collect(ServerStatsSnapshot.Builder builder) {
        if (!isAvailable()) {
            return;
        }

        long area = 0L;
        for (Claim claim : griefPrevention.dataStore.getClaims()) {
            if (claim.parent != null) {
                continue;
            }
            area += claim.getArea();
        }
        builder.claimedArea(area);
    }
}
