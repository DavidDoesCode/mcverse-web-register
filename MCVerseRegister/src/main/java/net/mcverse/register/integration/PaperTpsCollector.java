package net.mcverse.register.integration;

import org.bukkit.Bukkit;

public class PaperTpsCollector implements ServerStatsCollector {

    @Override
    public String name() {
        return "paper-tps";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void collect(ServerStatsSnapshot.Builder builder) {
        if (builder.averageTps() != null) {
            return;
        }
        double[] tps = Bukkit.getTPS();
        if (tps == null || tps.length == 0) {
            return;
        }
        builder.averageTps(tps[0]);
    }
}
