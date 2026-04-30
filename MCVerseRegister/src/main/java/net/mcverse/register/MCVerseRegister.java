package net.mcverse.register;

import net.mcverse.register.api.MCVerseApiClient;
import net.mcverse.register.commands.AdminCommand;
import net.mcverse.register.integration.BalanceSnapshot;
import net.mcverse.register.integration.ClanSnapshot;
import net.mcverse.register.integration.ClaimsSnapshot;
import net.mcverse.register.integration.GriefPreventionAdapter;
import net.mcverse.register.integration.GroupsSnapshot;
import net.mcverse.register.integration.LuckPermsGroupsAdapter;
import net.mcverse.register.integration.NoopBalanceAdapter;
import net.mcverse.register.integration.NoopGriefPreventionAdapter;
import net.mcverse.register.integration.NoopGroupsAdapter;
import net.mcverse.register.integration.NoopSimpleClansAdapter;
import net.mcverse.register.integration.PlayerDataAdapter;
import net.mcverse.register.integration.SimpleClansAdapter;
import net.mcverse.register.integration.VaultBalanceAdapter;
import net.mcverse.register.commands.RegisterCommand;
import net.mcverse.register.commands.UnregisterCommand;
import net.mcverse.register.listeners.PlayerListener;
import net.mcverse.register.service.PlayerStateSyncService;
import net.mcverse.register.service.UsernameSyncService;
import net.mcverse.register.util.CooldownManager;
import net.mcverse.register.util.MessageUtil;
import net.mcverse.register.util.RegistrationCache;

import org.bukkit.plugin.java.JavaPlugin;

public class MCVerseRegister extends JavaPlugin {

    private MCVerseApiClient apiClient;
    private CooldownManager cooldownManager;
    private RegistrationCache registrationCache;
    private MessageUtil messageUtil;
    private UsernameSyncService usernameSyncService;
    private PlayerStateSyncService playerStateSyncService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.apiClient = new MCVerseApiClient(this);
        this.cooldownManager = new CooldownManager(this);
        this.registrationCache = new RegistrationCache();
        this.messageUtil = new MessageUtil(this);
        this.usernameSyncService = new UsernameSyncService(this);
        this.playerStateSyncService = new PlayerStateSyncService(
                this,
                resolveBalanceAdapter(),
                resolveGroupsAdapter(),
                resolveSimpleClansAdapter(),
                resolveGriefPreventionAdapter()
        );

        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("unregister").setExecutor(new UnregisterCommand(this));
        getCommand("mcvadmin").setExecutor(new AdminCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        scheduleDiagnosticReconciliation();

        getLogger().info("MCVerseRegister v" + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MCVerseRegister disabled.");
    }

    public MCVerseApiClient getApiClient() { return apiClient; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public RegistrationCache getRegistrationCache() { return registrationCache; }
    public MessageUtil getMessageUtil() { return messageUtil; }
    public UsernameSyncService getUsernameSyncService() { return usernameSyncService; }
    public PlayerStateSyncService getPlayerStateSyncService() { return playerStateSyncService; }

    private PlayerDataAdapter<BalanceSnapshot> resolveBalanceAdapter() {
        VaultBalanceAdapter adapter = new VaultBalanceAdapter();
        if (adapter.isAvailable()) {
            getLogger().info("Diagnostics: Vault balance adapter enabled.");
            return adapter;
        }
        getLogger().info("Diagnostics: Vault not available; balance sync disabled.");
        return new NoopBalanceAdapter();
    }

    private PlayerDataAdapter<GroupsSnapshot> resolveGroupsAdapter() {
        LuckPermsGroupsAdapter adapter = new LuckPermsGroupsAdapter();
        if (adapter.isAvailable()) {
            getLogger().info("Diagnostics: LuckPerms groups adapter enabled.");
            return adapter;
        }
        getLogger().info("Diagnostics: LuckPerms not available; groups sync disabled.");
        return new NoopGroupsAdapter();
    }

    private PlayerDataAdapter<ClanSnapshot> resolveSimpleClansAdapter() {
        SimpleClansAdapter adapter = new SimpleClansAdapter();
        if (adapter.isAvailable()) {
            getLogger().info("Diagnostics: SimpleClans adapter enabled.");
            return adapter;
        }
        getLogger().info("Diagnostics: SimpleClans not available; simpleclans sync disabled.");
        return new NoopSimpleClansAdapter();
    }

    private PlayerDataAdapter<ClaimsSnapshot> resolveGriefPreventionAdapter() {
        GriefPreventionAdapter adapter = new GriefPreventionAdapter();
        if (adapter.isAvailable()) {
            getLogger().info("Diagnostics: GriefPrevention adapter enabled.");
            return adapter;
        }
        getLogger().info("Diagnostics: GriefPrevention not available; claims sync disabled.");
        return new NoopGriefPreventionAdapter();
    }

    private void scheduleDiagnosticReconciliation() {
        if (!getConfig().getBoolean("sync.reconciliation.enabled", false)) {
            return;
        }

        long intervalSeconds = Math.max(60L, getConfig().getLong("sync.reconciliation.interval-seconds", 300L));
        long intervalTicks = intervalSeconds * 20L;

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            getServer().getOnlinePlayers().forEach(player -> playerStateSyncService.syncPlayer(player, "scheduled"));
        }, intervalTicks, intervalTicks);
    }
}
