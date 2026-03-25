package net.mcverse.register.util;

import net.mcverse.register.MCVerseRegister;
import org.bukkit.ChatColor;

public class MessageUtil {

    private final MCVerseRegister plugin;

    public MessageUtil(MCVerseRegister plugin) {
        this.plugin = plugin;
    }

    /** Returns the prefix + message for the given key, with color codes translated. */
    public String get(String key) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bMCVerse&8] &r");
        String message = plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }
}
