package net.mcverse.register.integration;

import org.bukkit.entity.Player;

import java.util.Optional;

public interface PlayerDataAdapter<T> {

    String adapterName();

    boolean isAvailable();

    Optional<T> snapshot(Player player);
}
