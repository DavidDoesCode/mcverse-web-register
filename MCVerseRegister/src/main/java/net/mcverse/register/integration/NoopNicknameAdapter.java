package net.mcverse.register.integration;

import org.bukkit.entity.Player;

import java.util.Optional;

public class NoopNicknameAdapter implements PlayerDataAdapter<NicknameSnapshot> {

    @Override
    public String adapterName() {
        return "essentials-nickname";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<NicknameSnapshot> snapshot(Player player) {
        return Optional.empty();
    }
}
