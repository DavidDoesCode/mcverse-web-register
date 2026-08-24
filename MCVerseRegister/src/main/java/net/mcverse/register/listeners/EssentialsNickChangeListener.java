package net.mcverse.register.listeners;

import net.ess3.api.IUser;
import net.ess3.api.events.NickChangeEvent;
import net.mcverse.register.service.PlayerStateSyncService;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public class EssentialsNickChangeListener implements Listener {

    private final PlayerStateSyncService syncService;

    public EssentialsNickChangeListener(PlayerStateSyncService syncService) {
        this.syncService = syncService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNickChange(NickChangeEvent event) {
        IUser target = event.getController();
        if (target == null) {
            return;
        }

        UUID uuid = target.getUUID();
        String ign = target.getName();
        if (uuid == null || ign == null || ign.isBlank()) {
            return;
        }

        syncService.syncNicknameNow(uuid, ign, event.getValue(), "nick-change");
    }
}
