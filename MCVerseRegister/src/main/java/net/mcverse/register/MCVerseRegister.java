package net.mcverse.register;

import net.mcverse.register.api.MCVerseApiClient;
import net.mcverse.register.commands.AdminCommand;
import net.mcverse.register.commands.RegisterCommand;
import net.mcverse.register.commands.UnregisterCommand;
import net.mcverse.register.listeners.PlayerListener;
import net.mcverse.register.util.CooldownManager;
import net.mcverse.register.util.MessageUtil;
import net.mcverse.register.util.RegistrationCache;

import org.bukkit.plugin.java.JavaPlugin;

public class MCVerseRegister extends JavaPlugin {

    private MCVerseApiClient apiClient;
    private CooldownManager cooldownManager;
    private RegistrationCache registrationCache;
    private MessageUtil messageUtil;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.apiClient = new MCVerseApiClient(this);
        this.cooldownManager = new CooldownManager(this);
        this.registrationCache = new RegistrationCache();
        this.messageUtil = new MessageUtil(this);

        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("unregister").setExecutor(new UnregisterCommand(this));
        getCommand("mcvadmin").setExecutor(new AdminCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

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
}
