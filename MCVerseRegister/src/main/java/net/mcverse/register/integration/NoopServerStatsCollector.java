package net.mcverse.register.integration;

public class NoopServerStatsCollector implements ServerStatsCollector {

    private final String name;
    private final boolean mainThread;

    public NoopServerStatsCollector(String name) {
        this(name, false);
    }

    public NoopServerStatsCollector(String name, boolean mainThread) {
        this.name = name;
        this.mainThread = mainThread;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean requiresMainThread() {
        return mainThread;
    }

    @Override
    public void collect(ServerStatsSnapshot.Builder builder) {
        // Missing softdep: leave fields null so the rest of the snapshot still posts.
    }
}
