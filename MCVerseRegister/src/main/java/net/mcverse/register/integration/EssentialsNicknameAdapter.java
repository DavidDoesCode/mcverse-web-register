package net.mcverse.register.integration;

import com.earth2me.essentials.User;

import net.ess3.api.IEssentials;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public class EssentialsNicknameAdapter implements PlayerDataAdapter<NicknameSnapshot> {

    private final IEssentials essentials;

    public EssentialsNicknameAdapter() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Essentials");
        this.essentials = plugin instanceof IEssentials ess ? ess : null;
    }

    @Override
    public String adapterName() {
        return "essentials-nickname";
    }

    @Override
    public boolean isAvailable() {
        return essentials != null && essentials.isEnabled();
    }

    @Override
    public Optional<NicknameSnapshot> snapshot(Player player) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        User user = essentials.getUser(player);
        if (user == null) {
            return Optional.of(new NicknameSnapshot(null));
        }
        return Optional.of(new NicknameSnapshot(NicknameSnapshot.normalize(user.getNickname())));
    }
}
