package net.mcverse.register.integration;

import org.bukkit.entity.Player;

import java.util.Optional;

public class NoopBalanceAdapter implements PlayerDataAdapter<BalanceSnapshot> {

    @Override
    public String adapterName() {
        return "vault-balance";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<BalanceSnapshot> snapshot(Player player) {
        return Optional.empty();
    }
}
