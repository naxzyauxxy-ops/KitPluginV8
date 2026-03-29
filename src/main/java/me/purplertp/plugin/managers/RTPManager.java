package me.purplertp.plugin.managers;

import me.purplertp.plugin.PurpleRTP;
import me.purplertp.plugin.utils.MessageUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RTPManager {

    private final PurpleRTP plugin;
    private final Set<UUID> inRtp = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public RTPManager(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    public Set<UUID> getPlayersInRtp() { return inRtp; }
    public boolean isInRtp(UUID uuid)  { return inRtp.contains(uuid); }
    public void cancelRtp(UUID uuid)   { inRtp.remove(uuid); }

    public void randomTeleport(Player player, String worldName) {

        if (!plugin.getConfig().getBoolean("ENABLED", true)) {
            actionbar(player, plugin.getConfig().getString("MESSAGES.DISABLED", "&cRTP is disabled."));
            return;
        }

        int maxPlayers = plugin.getConfig().getInt("SETTINGS.PLAYERS-IN-RTP", 150);
        if (inRtp.size() >= maxPlayers) {
            actionbar(player, plugin.getConfig().getString("MESSAGES.MAX-PLAYERS", "&cToo many players using RTP."));
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            actionbar(player, plugin.getConfig().getString("MESSAGES.WORLD-NOT-EXIST", "&cWorld not found."));
            return;
        }

        if (!player.hasPermission("purplertp.bypass.cooldown")) {
            CooldownManager cm = plugin.getCooldownManager();
            if (cm.isOnCooldown(player.getUniqueId(), worldName)) {
                long remaining = cm.getRemainingCooldown(player.getUniqueId(), worldName);
                String msg = plugin.getConfig().getString("MESSAGES.COOLDOWN", "&cWait {remaining}s.")
                        .replace("{remaining}", String.valueOf(remaining));
                actionbar(player, msg);
                return;
            }
        }

        String path     = "WORLD-SETTINGS." + worldName + ".";
        int cooldown    = plugin.getConfig().getInt(path + "COOLDOWN", 0);
        int countdown   = plugin.getConfig().getInt("SETTINGS.COUNTDOWN", 5);
        int centerX     = plugin.getConfig().getInt(path + "CENTER-X", 0);
        int centerZ     = plugin.getConfig().getInt(path + "CENTER-Z", 0);
        int minRadius   = plugin.getConfig().getInt(path + "MIN-RADIUS", 1000);
        int maxRadius   = plugin.getConfig().getInt(path + "MAX-RADIUS", 20000);
        int maxAttempts = plugin.getConfig().getInt("SETTINGS.MAX-ATTEMPTS", 25);

        inRtp.add(player.getUniqueId());

        Location poolLoc = plugin.getLocationPoolManager().pollLocation(worldName);

        if (poolLoc != null) {
            // Got a location instantly from pool —
            // start countdown immediately AND preload chunks in background at the same time
            preloadChunksAsync(player, poolLoc);
            runCountdown(player, poolLoc, worldName, cooldown, countdown);
        } else {
            // Pool empty — search async, then start countdown
            actionbar(player, plugin.getConfig().getString("MESSAGES.SEARCHING", "&dSearching..."));

            BukkitRunnable searchTicker = new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isOnline() || !inRtp.contains(player.getUniqueId())) { cancel(); return; }
                    actionbar(player, plugin.getConfig().getString("MESSAGES.SEARCHING", "&dSearching..."));
                }
            };
            searchTicker.runTaskTimer(plugin, 0L, 20L);

            final World finalWorld = world;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Location found = findSafeLocation(finalWorld, centerX, centerZ, minRadius, maxRadius, maxAttempts);
                searchTicker.cancel();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || !inRtp.contains(player.getUniqueId())) return;
                    if (found == null) {
                        inRtp.remove(player.getUniqueId());
                        actionbar(player, plugin.getConfig().getString("MESSAGES.MAX-ATTEMPTS", "&cNo safe location found.")
                                .replace("{attempts}", String.valueOf(maxAttempts)));
                        return;
                    }
                    preloadChunksAsync(player, found);
                    runCountdown(player, found, worldName, cooldown, countdown);
                });
            });
        }
    }

    /**
     * Fire-and-forget chunk preload using Paper's native async chunk API.
     * After loading, force-sends chunks to the player's client so there's
     * no waiting when they arrive. Overworld gets a bigger radius since
     * overworld chunks take longer to render client-side.
     */
    private void preloadChunksAsync(Player player, Location dest) {
        int cx = dest.getBlockX() >> 4;
        int cz = dest.getBlockZ() >> 4;
        World w = dest.getWorld();

        // Overworld needs a bigger preload radius due to surface complexity
        int radius = w.getEnvironment() == World.Environment.NORMAL ? 4 : 3;

        for (int ox = -radius; ox <= radius; ox++) {
            for (int oz = -radius; oz <= radius; oz++) {
                final int fx = cx + ox;
                final int fz = cz + oz;
                w.getChunkAtAsync(fx, fz).thenAccept(chunk -> {
                    // Force-send the chunk to the player's client as soon as it's loaded
                    if (player.isOnline()) {
                        player.sendChunk(chunk);
                    }
                });
            }
        }
    }

    /**
     * Countdown runs immediately on the main thread.
     * Teleport at 0 is a plain sync call — no futures, no callbacks.
     */
    private void runCountdown(Player player, Location dest,
                               String worldName, int cooldown, int countdown) {
        double startX = player.getLocation().getX();
        double startZ = player.getLocation().getZ();
        Location startLoc = player.getLocation().clone();

        new BukkitRunnable() {
            int secondsLeft = countdown;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    inRtp.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                double dx = Math.abs(player.getLocation().getX() - startX);
                double dz = Math.abs(player.getLocation().getZ() - startZ);
                if (dx > 0.333 || dz > 0.333) {
                    inRtp.remove(player.getUniqueId());
                    cancel();
                    actionbar(player, "&cTeleport cancelled &7— &cdon't move!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                if (secondsLeft > 0) {
                    actionbar(player, "&fTeleporting in &b" + secondsLeft + "&f...");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.5f);
                    startLoc.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                            startLoc.clone().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.05);
                    secondsLeft--;
                } else {
                    inRtp.remove(player.getUniqueId());
                    cancel();
                    player.teleport(dest);
                    player.playSound(dest, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.2f);
                    if (cooldown > 0) {
                        plugin.getCooldownManager().setCooldown(player.getUniqueId(), worldName, cooldown);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private Location findSafeLocation(World world, int centerX, int centerZ,
                                      int minRadius, int maxRadius, int maxAttempts) {
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int ox, oz;
            do {
                ox = rng.nextInt(-maxRadius, maxRadius + 1);
                oz = rng.nextInt(-maxRadius, maxRadius + 1);
            } while (Math.sqrt((double) ox * ox + (double) oz * oz) < minRadius);

            int x = centerX + ox;
            int z = centerZ + oz;
            try {
                Chunk chunk = world.getChunkAtAsync(x >> 4, z >> 4).get();

                Location loc = null;
                if (world.getEnvironment() == World.Environment.NETHER) {
                    // Scan downward from below the bedrock roof
                    for (int y = 120; y > world.getMinHeight() + 2; y--) {
                        Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5);
                        if (isSafe(candidate)) { loc = candidate; break; }
                    }
                } else {
                    int y = world.getHighestBlockYAt(x, z);
                    loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                    if (!isSafe(loc)) loc = null;
                }

                if (loc != null) {
                    loc.setYaw(rng.nextFloat() * 360f);
                    return loc;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean isSafe(Location loc) {
        org.bukkit.block.Block feet   = loc.getBlock();
        org.bukkit.block.Block head   = feet.getRelative(0, 1, 0);
        org.bukkit.block.Block ground = feet.getRelative(0, -1, 0);
        if (!feet.getType().isAir())     return false;
        if (!head.getType().isAir())     return false;
        if (!ground.getType().isSolid()) return false;
        Material g = ground.getType();
        if (g == Material.WATER)  return false;
        if (g == Material.LAVA)   return false;
        if (g == Material.FIRE)   return false;
        if (g == Material.CACTUS) return false;
        if (loc.getY() <= loc.getWorld().getMinHeight() + 1) return false;
        return true;
    }

    private void actionbar(Player player, String message) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(MessageUtils.format(message))
        );
    }
}
