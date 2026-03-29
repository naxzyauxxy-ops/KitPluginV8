package me.purplertp.plugin.gui;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

public class RTPMenuListener implements Listener {

    private final PurpleRTP plugin;

    // Store the formatted title once so comparison is always consistent
    private String cachedTitle = null;

    public RTPMenuListener(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    private String getTitle() {
        if (cachedTitle == null) {
            cachedTitle = MessageUtils.format(
                plugin.getConfig().getString("RTP-MENU.TITLE", "&8Random Teleport")
            );
        }
        return cachedTitle;
    }

    public void invalidateCache() {
        cachedTitle = null;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only intercept chest-type inventories to avoid blocking player inventory
        if (event.getView().getTopInventory().getType() != InventoryType.CHEST) return;

        // Only cancel if this is actually our RTP menu
        if (!event.getView().getTitle().equals(getTitle())) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() == null) return;
        // Only handle clicks in the top inventory (the GUI), not the player's inventory
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        for (String key : plugin.getConfig().getConfigurationSection("RTP-MENU.BUTTONS").getKeys(false)) {
            String base = "RTP-MENU.BUTTONS." + key + ".";
            if (!plugin.getConfig().getBoolean(base + "ENABLED", true)) continue;

            int slot = plugin.getConfig().getInt(base + "SLOT", -1);
            if (slot != event.getRawSlot()) continue;

            String worldName = plugin.getConfig().getString(base + "WORLD", "world");
            player.closeInventory();

            if (plugin.getRtpManager().isInRtp(player.getUniqueId())) {
                player.sendActionBar(MessageUtils.format("&cYou are already teleporting!"));
                return;
            }

            plugin.getRtpManager().randomTeleport(player, worldName);
            return;
        }
    }
}
