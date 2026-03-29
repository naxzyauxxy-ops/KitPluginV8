package me.purplertp.plugin.gui;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RTPMenu {

    private final PurpleRTP plugin;

    public RTPMenu(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        String title = MessageUtils.format(plugin.getConfig().getString("RTP-MENU.TITLE", "&8Random Teleport"));
        int size     = plugin.getConfig().getInt("RTP-MENU.SIZE", 27);
        Inventory inv = Bukkit.createInventory(null, size, title);

        // Placeholder glass panes
        if (plugin.getConfig().getBoolean("RTP-MENU.PLACEHOLDER", true)) {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta paneMeta = pane.getItemMeta();
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
            for (int i = 0; i < size; i++) inv.setItem(i, pane);
        }

        // World buttons
        for (String key : plugin.getConfig().getConfigurationSection("RTP-MENU.BUTTONS").getKeys(false)) {
            String base = "RTP-MENU.BUTTONS." + key + ".";

            if (!plugin.getConfig().getBoolean(base + "ENABLED", true)) continue;

            Material mat = Material.getMaterial(plugin.getConfig().getString(base + "MATERIAL", "GRASS_BLOCK"));
            if (mat == null) mat = Material.GRASS_BLOCK;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();

            String displayName = MessageUtils.format(plugin.getConfig().getString(base + "DISPLAY-NAME", key));
            meta.setDisplayName(displayName);

            String worldName = plugin.getConfig().getString(base + "WORLD", "world");
            int poolReady    = plugin.getLocationPoolManager().poolSize(worldName);
            int ping         = player.getPing();
            int playersInWorld = (int) Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.getWorld().getName().equals(worldName))
                    .count();

            List<String> lore = new ArrayList<>();
            for (String line : plugin.getConfig().getStringList(base + "LORE")) {
                lore.add(MessageUtils.format(line
                        .replace("{players}", String.valueOf(playersInWorld))
                        .replace("{ping}", String.valueOf(ping))
                        .replace("{pool}", String.valueOf(poolReady))
                ));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);

            int slot = plugin.getConfig().getInt(base + "SLOT", 13);
            inv.setItem(slot, item);
        }

        player.openInventory(inv);
    }
}
