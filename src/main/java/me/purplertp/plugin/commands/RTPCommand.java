package me.purplertp.plugin.commands;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.gui.RTPMenu;
import me.purplertp.plugin.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RTPCommand implements CommandExecutor {

    private final PurpleRTP plugin;
    private final RTPMenu menu;

    public RTPCommand(PurpleRTP plugin) {
        this.plugin = plugin;
        this.menu = new RTPMenu(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!player.hasPermission("purplertp.use")) {
            actionbar(player, "&cYou don't have permission to use RTP.");
            return true;
        }

        if (!plugin.getConfig().getBoolean("ENABLED", true)) {
            String msg = plugin.getConfig().getString("MESSAGES.DISABLED", "&cRTP is disabled.");
            actionbar(player, msg);
            return true;
        }

        java.util.List<String> deniedWorlds = plugin.getConfig().getStringList("DENIED-WORLDS");
        if (deniedWorlds.contains(player.getWorld().getName())) {
            actionbar(player, "&cRTP is not allowed in this world.");
            return true;
        }

        menu.open(player);
        return true;
    }

    private void actionbar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(MessageUtils.format(message)));
    }
}
