package net.mcverse.register.util;

import net.mcverse.register.MCVerseRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final MCVerseRegister plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public CooldownManager(MCVerseRegister plugin) {
        this.plugin = plugin;
    }

    public boolean isOnCooldown(UUID uuid) {
        Long expiry = cooldowns.get(uuid);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public void setCooldown(UUID uuid) {
        int seconds = plugin.getConfig().getInt("register-cooldown", 60);
        cooldowns.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
    }

    public long getRemainingSeconds(UUID uuid) {
        Long expiry = cooldowns.get(uuid);
        if (expiry == null) return 0;
        return Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
    }

    public void remove(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
