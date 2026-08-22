package net.mcverse.register.integration;

import org.bukkit.Bukkit;
import org.bukkit.World;

public class MinecraftDayCollector implements ServerStatsCollector {

    private final String worldName;

    public MinecraftDayCollector(String worldName) {
        this.worldName = worldName == null || worldName.isBlank() ? "world" : worldName;
    }

    @Override
    public String name() {
        return "minecraft-day";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean requiresMainThread() {
        return true;
    }

    @Override
    public void collect(ServerStatsSnapshot.Builder builder) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        builder.minecraftDay(world.getFullTime() / 24_000L);
    }
}
