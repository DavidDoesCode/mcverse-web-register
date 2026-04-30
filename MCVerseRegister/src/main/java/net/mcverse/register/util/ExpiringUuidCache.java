package net.mcverse.register.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExpiringUuidCache {

    private final Map<UUID, Long> expiryByUuid = new ConcurrentHashMap<>();

    public boolean containsActive(UUID uuid, long nowMillis) {
        Long expiry = expiryByUuid.get(uuid);
        if (expiry == null) {
            return false;
        }
        if (expiry <= nowMillis) {
            expiryByUuid.remove(uuid);
            return false;
        }
        return true;
    }

    public void put(UUID uuid, long ttlMillis, long nowMillis) {
        expiryByUuid.put(uuid, nowMillis + Math.max(0L, ttlMillis));
    }

    public void remove(UUID uuid) {
        expiryByUuid.remove(uuid);
    }
}
