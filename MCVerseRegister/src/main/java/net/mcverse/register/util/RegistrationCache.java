package net.mcverse.register.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegistrationCache {

    private final Map<UUID, Boolean> cache = new HashMap<>();

    public boolean isRegistered(UUID uuid) {
        return Boolean.TRUE.equals(cache.get(uuid));
    }

    public boolean contains(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public void setRegistered(UUID uuid, boolean registered) {
        cache.put(uuid, registered);
    }

    public void remove(UUID uuid) {
        cache.remove(uuid);
    }
}
