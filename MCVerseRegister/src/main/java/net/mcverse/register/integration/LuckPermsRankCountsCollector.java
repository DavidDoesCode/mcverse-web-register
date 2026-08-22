package net.mcverse.register.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class LuckPermsRankCountsCollector implements ServerStatsCollector {

    private final RankNameConfig ranks;
    private final Logger logger;
    private final LuckPerms luckPerms;

    public LuckPermsRankCountsCollector(RankNameConfig ranks, Logger logger) {
        this.ranks = ranks;
        this.logger = logger;
        LuckPerms api;
        try {
            api = LuckPermsProvider.get();
        } catch (IllegalStateException ignored) {
            api = null;
        }
        this.luckPerms = api;
    }

    @Override
    public String name() {
        return "luckperms-ranks";
    }

    @Override
    public boolean isAvailable() {
        return luckPerms != null;
    }

    @Override
    public void collect(ServerStatsSnapshot.Builder builder) {
        if (luckPerms == null) {
            return;
        }

        RankTally tally = new RankTally(ranks);
        Set<UUID> uniqueUsers = luckPerms.getUserManager().getUniqueUsers();
        for (UUID uuid : uniqueUsers) {
            User user = loadUser(uuid);
            if (user == null) {
                continue;
            }
            tally.accept(user.getPrimaryGroup(), inheritsCitizen(user));
        }

        builder.rankDefault(tally.defaultCount())
                .rankMember(tally.memberCount())
                .rankRegular(tally.regularCount())
                .rankCitizen(tally.citizenCount())
                .citizenAll(tally.citizenAll());
    }

    private User loadUser(UUID uuid) {
        User cached = luckPerms.getUserManager().getUser(uuid);
        if (cached != null) {
            return cached;
        }
        try {
            return luckPerms.getUserManager().loadUser(uuid).join();
        } catch (Exception e) {
            logger.warning("LuckPerms rank tally skipped uuid=" + uuid + " error=" + e.getMessage());
            return null;
        }
    }

    private boolean inheritsCitizen(User user) {
        String citizenRank = ranks.citizenRank();
        try {
            return user.getInheritedGroups(QueryOptions.defaultContextualOptions()).stream()
                    .map(Group::getName)
                    .anyMatch(citizenRank::equalsIgnoreCase);
        } catch (Exception ignored) {
            return user.getNodes(NodeType.INHERITANCE).stream()
                    .map(InheritanceNode::getGroupName)
                    .anyMatch(citizenRank::equalsIgnoreCase);
        }
    }
}
