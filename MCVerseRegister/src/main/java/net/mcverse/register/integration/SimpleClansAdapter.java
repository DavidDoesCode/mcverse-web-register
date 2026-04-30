package net.mcverse.register.integration;

import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public class SimpleClansAdapter implements PlayerDataAdapter<ClanSnapshot> {

    private final SimpleClans simpleClans;

    public SimpleClansAdapter() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("SimpleClans");
        this.simpleClans = plugin instanceof SimpleClans sc ? sc : null;
    }

    @Override
    public String adapterName() {
        return "simpleclans";
    }

    @Override
    public boolean isAvailable() {
        return simpleClans != null && simpleClans.isEnabled();
    }

    @Override
    public Optional<ClanSnapshot> snapshot(Player player) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        ClanPlayer clanPlayer = simpleClans.getClanManager().getClanPlayer(player.getUniqueId());
        if (clanPlayer == null) {
            return Optional.of(new ClanSnapshot(null, null, null));
        }

        Clan clan = clanPlayer.getClan();
        if (clan == null) {
            return Optional.of(new ClanSnapshot(null, null, null));
        }

        String role = clanPlayer.getRole() == null ? null : clanPlayer.getRole().name();
        return Optional.of(new ClanSnapshot(clan.getTag(), clan.getName(), role));
    }
}
