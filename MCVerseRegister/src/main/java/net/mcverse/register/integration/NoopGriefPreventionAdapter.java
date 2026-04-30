package net.mcverse.register.integration;

import org.bukkit.entity.Player;

import java.util.Optional;

public class NoopGriefPreventionAdapter implements PlayerDataAdapter<ClaimsSnapshot> {

    @Override
    public String adapterName() {
        return "griefprevention-claims";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<ClaimsSnapshot> snapshot(Player player) {
        return Optional.empty();
    }
}
