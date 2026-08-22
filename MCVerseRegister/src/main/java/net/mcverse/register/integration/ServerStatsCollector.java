package net.mcverse.register.integration;

/**
 * Collects a slice of server-wide stats into a snapshot builder.
 * Implementations that touch Bukkit world or GriefPrevention claim APIs
 * must return {@code true} from {@link #requiresMainThread()}.
 */
public interface ServerStatsCollector {

    String name();

    boolean isAvailable();

    default boolean requiresMainThread() {
        return false;
    }

    void collect(ServerStatsSnapshot.Builder builder);
}
