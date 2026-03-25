package net.mcverse.register.listeners;

import net.mcverse.register.MCVerseRegister;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final MCVerseRegister plugin;

    public PlayerListener(MCVerseRegister plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean registered = plugin.getApiClient().getPlayerStatus(player.getUniqueId());
                plugin.getRegistrationCache().setRegistered(player.getUniqueId(), registered);

                if (!registered) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendMessage(plugin.getMessageUtil().get("login-not-registered"));
                        }
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fetch registration status for "
                        + player.getName() + ": " + e.getMessage());
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getCooldownManager().remove(player.getUniqueId());
        plugin.getRegistrationCache().remove(player.getUniqueId());
    }
}
