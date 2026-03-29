package me.purplertp.plugin;

import me.purplertp.plugin.commands.RTPAdminCommand;
import me.purplertp.plugin.commands.RTPCommand;
import me.purplertp.plugin.gui.RTPMenuListener;
import me.purplertp.plugin.managers.CooldownManager;
import me.purplertp.plugin.managers.LocationPoolManager;
import me.purplertp.plugin.managers.RTPManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PurpleRTP extends JavaPlugin {

    private static PurpleRTP instance;

    private CooldownManager cooldownManager;
    private RTPManager rtpManager;
    private LocationPoolManager locationPoolManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        cooldownManager    = new CooldownManager(this);
        locationPoolManager = new LocationPoolManager(this);
        rtpManager         = new RTPManager(this);

        // Start pre-generating location pools for all configured worlds
        locationPoolManager.startPoolFilling();

        getCommand("rtp").setExecutor(new RTPCommand(this));
        getCommand("rtpadmin").setExecutor(new RTPAdminCommand(this));
        getServer().getPluginManager().registerEvents(new RTPMenuListener(this), this);

        getLogger().info("PurpleRTP v2 enabled! Location pools warming up...");
    }

    @Override
    public void onDisable() {
        if (cooldownManager != null) cooldownManager.saveCooldowns();
        if (locationPoolManager != null) locationPoolManager.shutdown();
        getLogger().info("PurpleRTP disabled.");
    }

    public static PurpleRTP getInstance() {
        return instance;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public RTPManager getRtpManager() {
        return rtpManager;
    }

    public LocationPoolManager getLocationPoolManager() {
        return locationPoolManager;
    }
}
