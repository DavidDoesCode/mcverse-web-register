package net.mcverse.register.integration;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.PlayerData;

import net.mcverse.register.api.ClaimLocation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GriefPreventionAdapter implements PlayerDataAdapter<ClaimsSnapshot> {

    private final GriefPrevention griefPrevention;

    public GriefPreventionAdapter() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        this.griefPrevention = plugin instanceof GriefPrevention gp ? gp : null;
    }

    @Override
    public String adapterName() {
        return "griefprevention-claims";
    }

    @Override
    public boolean isAvailable() {
        return griefPrevention != null && griefPrevention.isEnabled();
    }

    @Override
    public Optional<ClaimsSnapshot> snapshot(Player player) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        PlayerData data = griefPrevention.dataStore.getPlayerDataFromStorage(player.getUniqueId());
        List<Claim> claims = griefPrevention.dataStore.getPlayerClaims(player.getUniqueId());

        List<ClaimLocation> claimLocations = new ArrayList<>();
        for (Claim claim : claims) {
            Location lesserCorner = claim.getLesserBoundaryCorner();
            claimLocations.add(new ClaimLocation(
                    lesserCorner.getWorld() == null ? "unknown" : lesserCorner.getWorld().getName(),
                    lesserCorner.getBlockX(),
                    lesserCorner.getBlockZ()
            ));
        }

        return Optional.of(new ClaimsSnapshot(
                claims.size(),
                data.accruedClaimBlocks,
                data.bonusClaimBlocks,
                data.getRemainingClaimBlocks(),
                claimLocations
        ));
    }
}
