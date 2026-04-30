package net.mcverse.register.integration;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;

public class VaultBalanceAdapter implements PlayerDataAdapter<BalanceSnapshot> {

    private final Economy economy;

    public VaultBalanceAdapter() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        this.economy = registration == null ? null : registration.getProvider();
    }

    @Override
    public String adapterName() {
        return "vault-balance";
    }

    @Override
    public boolean isAvailable() {
        return economy != null;
    }

    @Override
    public Optional<BalanceSnapshot> snapshot(Player player) {
        if (economy == null) {
            return Optional.empty();
        }
        return Optional.of(new BalanceSnapshot(economy.getBalance(player)));
    }
}
