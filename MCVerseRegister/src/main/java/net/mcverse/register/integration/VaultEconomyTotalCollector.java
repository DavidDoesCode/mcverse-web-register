package net.mcverse.register.integration;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

public class VaultEconomyTotalCollector implements ServerStatsCollector {

    private final Logger logger;
    private final Economy economy;

    public VaultEconomyTotalCollector(Logger logger) {
        this.logger = logger;
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        this.economy = registration == null ? null : registration.getProvider();
    }

    @Override
    public String name() {
        return "vault-economy-total";
    }

    @Override
    public boolean isAvailable() {
        return economy != null;
    }

    @Override
    public void collect(ServerStatsSnapshot.Builder builder) {
        if (economy == null) {
            return;
        }

        double total = 0.0D;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            try {
                total += economy.getBalance(player);
            } catch (Exception e) {
                logger.warning("Vault balance skipped player=" + player.getName() + " error=" + e.getMessage());
            }
        }
        builder.economyTotal(total);
    }
}
