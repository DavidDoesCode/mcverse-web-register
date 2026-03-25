package net.mcverse.register.commands;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnregisterCommand implements CommandExecutor {

    private final MCVerseRegister plugin;

    public UnregisterCommand(MCVerseRegister plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().get("players-only"));
            return true;
        }

        if (!player.hasPermission("mcverse.unregister")) {
            player.sendMessage(plugin.getMessageUtil().get("no-permission"));
            return true;
        }

        if (!plugin.getRegistrationCache().isRegistered(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageUtil().get("unregister-not-registered"));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ApiResponse response = plugin.getApiClient().unregisterPlayer(player.getUniqueId());

                plugin.getLogger().info("Unregister attempt by " + player.getName()
                        + ": HTTP " + response.getStatusCode());

                if (response.getStatusCode() == 200) {
                    plugin.getRegistrationCache().setRegistered(player.getUniqueId(), false);
                }

                String message = messageForResponse(response);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(message));

            } catch (Exception e) {
                plugin.getLogger().warning("Unregister failed for " + player.getName() + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.getMessageUtil().get("unregister-error")));
            }
        });

        return true;
    }

    private String messageForResponse(ApiResponse response) {
        String key = switch (response.getStatusCode()) {
            case 200 -> "unregister-success";
            case 404 -> "unregister-not-registered";
            case 429 -> "unregister-rate-limited";
            case 403 -> "unregister-forbidden";
            default  -> "unregister-error";
        };
        return plugin.getMessageUtil().get(key);
    }
}
