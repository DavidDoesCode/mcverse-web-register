package net.mcverse.register.integration;

import org.bukkit.entity.Player;

import java.util.Optional;

public class NoopSimpleClansAdapter implements PlayerDataAdapter<ClanSnapshot> {

    @Override
    public String adapterName() {
        return "simpleclans";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<ClanSnapshot> snapshot(Player player) {
        return Optional.empty();
    }
}
