package net.mcverse.register.commands;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;

public class AdminCommand implements CommandExecutor {

    private final MCVerseRegister plugin;

    public AdminCommand(MCVerseRegister plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("mcverse.admin")) {
            sender.sendMessage(plugin.getMessageUtil().get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> handleStatus(sender, args);
            case "remove" -> handleRemove(sender, args);
            default -> sendUsage(sender);
        }

        return true;
    }

    // /mcvadmin status            — list all online players
    // /mcvadmin status <player>   — show a specific player's status
    private void handleStatus(CommandSender sender, String[] args) {
        if (args.length == 1) {
            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            if (online.isEmpty()) {
                sender.sendMessage(plugin.getMessageUtil().format("&7No players online."));
                return;
            }

            sender.sendMessage(plugin.getMessageUtil().format("&8[&bMCVerse&8] &7Online player registration status:"));
            for (Player p : online) {
                boolean registered = plugin.getRegistrationCache().isRegistered(p.getUniqueId());
                String statusColor = registered ? "&a" : "&c";
                String statusLabel = registered ? "Registered" : "Not registered";
                sender.sendMessage(plugin.getMessageUtil().format(
                        "  &f" + p.getName() + " &8— " + statusColor + statusLabel
                ));
            }
            return;
        }

        // Specific player
        String targetName = args[1];
        Player online = Bukkit.getPlayerExact(targetName);

        if (online != null) {
            boolean registered = plugin.getRegistrationCache().isRegistered(online.getUniqueId());
            String statusColor = registered ? "&a" : "&c";
            String statusLabel = registered ? "Registered" : "Not registered";
            sender.sendMessage(plugin.getMessageUtil().format(
                    "&8[&bMCVerse&8] &f" + online.getName() + " &8— " + statusColor + statusLabel
            ));
        } else {
            // Player is offline — fetch status from backend
            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage(plugin.getMessageUtil().format(
                        "&8[&bMCVerse&8] &cPlayer &f" + targetName + " &chas never joined this server."));
                return;
            }

            sender.sendMessage(plugin.getMessageUtil().format(
                    "&8[&bMCVerse&8] &7Checking status for offline player &f" + offline.getName() + "&7..."));

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    boolean registered = plugin.getApiClient().getPlayerStatus(offline.getUniqueId());
                    String statusColor = registered ? "&a" : "&c";
                    String statusLabel = registered ? "Registered" : "Not registered";
                    String message = plugin.getMessageUtil().format(
                            "&8[&bMCVerse&8] &f" + offline.getName() + " &8— " + statusColor + statusLabel);
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
                } catch (Exception e) {
                    plugin.getLogger().warning("Admin status check failed for " + targetName + ": " + e.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () ->
                            sender.sendMessage(plugin.getMessageUtil().get("register-error")));
                }
            });
        }
    }

    // /mcvadmin remove <player>
    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageUtil().format("&eUsage: /mcvadmin remove <player>"));
            return;
        }

        String targetName = args[1];
        Player online = Bukkit.getPlayerExact(targetName);

        if (online != null) {
            removePlayer(sender, online.getUniqueId(), online.getName(), true);
        } else {
            @SuppressWarnings("deprecation")
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (!offline.hasPlayedBefore()) {
                sender.sendMessage(plugin.getMessageUtil().format(
                        "&8[&bMCVerse&8] &cPlayer &f" + targetName + " &chas never joined this server."));
                return;
            }
            removePlayer(sender, offline.getUniqueId(), offline.getName(), false);
        }
    }

    private void removePlayer(CommandSender sender, java.util.UUID uuid, String name, boolean isOnline) {
        sender.sendMessage(plugin.getMessageUtil().format(
                "&8[&bMCVerse&8] &7Removing registration for &f" + name + "&7..."));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ApiResponse response = plugin.getApiClient().unregisterPlayer(uuid);

                plugin.getLogger().info("Admin remove for " + name + " by " + sender.getName()
                        + ": HTTP " + response.getStatusCode());

                String message = switch (response.getStatusCode()) {
                    case 200 -> plugin.getMessageUtil().format(
                            "&8[&bMCVerse&8] &aSuccessfully removed registration for &f" + name + "&a.");
                    case 404 -> plugin.getMessageUtil().format(
                            "&8[&bMCVerse&8] &e" + name + " is not registered on MCVerse.");
                    case 403 -> plugin.getMessageUtil().get("register-forbidden");
                    default  -> plugin.getMessageUtil().get("unregister-error");
                };

                if (response.getStatusCode() == 200) {
                    plugin.getRegistrationCache().setRegistered(uuid, false);
                }

                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));

            } catch (Exception e) {
                plugin.getLogger().warning("Admin remove failed for " + name + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.getMessageUtil().get("unregister-error")));
            }
        });
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(plugin.getMessageUtil().format("&8[&bMCVerse Admin&8]"));
        sender.sendMessage(plugin.getMessageUtil().format("  &b/mcvadmin status &8— &7List online players' registration status"));
        sender.sendMessage(plugin.getMessageUtil().format("  &b/mcvadmin status <player> &8— &7Check a specific player's status"));
        sender.sendMessage(plugin.getMessageUtil().format("  &b/mcvadmin remove <player> &8— &7Remove a player's registration"));
    }
}
