package net.mcverse.register.service;

public record UsernameSyncResult(boolean registered, boolean changed, boolean success) {

    public static UsernameSyncResult unregistered() {
        return new UsernameSyncResult(false, false, true);
    }

    public static UsernameSyncResult registeredInSync() {
        return new UsernameSyncResult(true, false, true);
    }

    public static UsernameSyncResult registeredChanged() {
        return new UsernameSyncResult(true, true, true);
    }

    public static UsernameSyncResult failure() {
        return new UsernameSyncResult(false, false, false);
    }
}
