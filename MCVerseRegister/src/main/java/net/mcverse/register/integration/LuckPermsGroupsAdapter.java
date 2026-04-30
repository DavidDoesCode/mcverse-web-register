package net.mcverse.register.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class LuckPermsGroupsAdapter implements PlayerDataAdapter<GroupsSnapshot> {

    private final LuckPerms luckPerms;

    public LuckPermsGroupsAdapter() {
        LuckPerms api;
        try {
            api = LuckPermsProvider.get();
        } catch (IllegalStateException ignored) {
            api = null;
        }
        this.luckPerms = api;
    }

    @Override
    public String adapterName() {
        return "luckperms-groups";
    }

    @Override
    public boolean isAvailable() {
        return luckPerms != null;
    }

    @Override
    public Optional<GroupsSnapshot> snapshot(Player player) {
        if (luckPerms == null) {
            return Optional.empty();
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return Optional.of(new GroupsSnapshot(null, List.of()));
        }

        List<String> groups = user.getNodes(NodeType.INHERITANCE).stream()
                .map(InheritanceNode::getGroupName)
                .distinct()
                .toList();

        return Optional.of(new GroupsSnapshot(user.getPrimaryGroup(), groups));
    }
}
