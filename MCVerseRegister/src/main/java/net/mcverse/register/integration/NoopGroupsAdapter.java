package net.mcverse.register.integration;

import org.bukkit.entity.Player;

import java.util.Optional;

public class NoopGroupsAdapter implements PlayerDataAdapter<GroupsSnapshot> {

    @Override
    public String adapterName() {
        return "luckperms-groups";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<GroupsSnapshot> snapshot(Player player) {
        return Optional.empty();
    }
}
