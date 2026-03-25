package net.mcverse.register.commands;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegisterCommand implements CommandExecutor {

    private final MCVerseRegister plugin;

    public RegisterCommand(MCVerseRegister plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageUtil().get("players-only"));
            return true;
        }

        if (!player.hasPermission("mcverse.register")) {
            player.sendMessage(plugin.getMessageUtil().get("no-permission"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(plugin.getMessageUtil().get("register-usage"));
            return true;
        }

        String email = args[0];

        if (!isValidEmail(email)) {
            player.sendMessage(plugin.getMessageUtil().get("register-invalid-email"));
            return true;
        }

        if (plugin.getRegistrationCache().isRegistered(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageUtil().get("register-already-registered"));
            return true;
        }

        if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId())
                && !player.hasPermission("mcverse.register.bypass-cooldown")) {
            long remaining = plugin.getCooldownManager().getRemainingSeconds(player.getUniqueId());
            player.sendMessage(plugin.getMessageUtil().get("register-cooldown")
                    .replace("{seconds}", String.valueOf(remaining)));
            return true;
        }

        plugin.getCooldownManager().setCooldown(player.getUniqueId());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ApiResponse response = plugin.getApiClient().registerPlayer(
                        player.getUniqueId(), player.getName(), email
                );

                plugin.getLogger().info("Registration attempt by " + player.getName()
                        + " (" + email + "): HTTP " + response.getStatusCode());

                // Cache is NOT updated here — email confirmation is required first.
                // Registration status is resolved from the backend on the player's next login.
                String message = messageForResponse(response);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(message));

            } catch (Exception e) {
                plugin.getLogger().warning("Registration failed for " + player.getName() + ": " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.getMessageUtil().get("register-error")));
            }
        });

        return true;
    }

    private String messageForResponse(ApiResponse response) {
        String key = switch (response.getStatusCode()) {
            case 201 -> "register-success-new";
            case 200 -> "register-success-update";
            case 409 -> "register-conflict";
            case 422 -> "register-invalid-input";
            case 429 -> "register-rate-limited";
            case 403 -> "register-forbidden";
            default  -> "register-error";
        };
        return plugin.getMessageUtil().get(key);
    }

    private boolean isValidEmail(String email) {
        if (email.length() < 5 || email.length() > 255) return false;
        int atIndex = email.indexOf('@');
        if (atIndex < 1) return false;
        int dotIndex = email.indexOf('.', atIndex);
        return dotIndex > atIndex + 1 && dotIndex < email.length() - 1;
    }
}
