package me.purplertp.plugin.managers;

import me.purplertp.plugin.PurpleRTP;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class LocationPoolManager {

    private final PurpleRTP plugin;
    private final Map<String, ConcurrentLinkedQueue<Location>> pools = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> filling = new ConcurrentHashMap<>();
    private BukkitTask refillTask;

    public LocationPoolManager(PurpleRTP plugin) {
        this.plugin = plugin;
    }

    public void startPoolFilling() {
        int intervalTicks = plugin.getConfig().getInt("SETTINGS.POOL-REFILL-INTERVAL", 40);

        refillTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Set<String> worldKeys = plugin.getConfig().getConfigurationSection("WORLD-SETTINGS").getKeys(false);
            int poolSize = plugin.getConfig().getInt("SETTINGS.POOL-SIZE", 20);
            int batch    = plugin.getConfig().getInt("SETTINGS.POOL-FILL-BATCH", 5);

            for (String worldName : worldKeys) {
                World world = Bukkit.getWorld(worldName);
                if (world == null) continue;

                ConcurrentLinkedQueue<Location> pool = pools.computeIfAbsent(worldName, k -> new ConcurrentLinkedQueue<>());
                AtomicBoolean isFilling              = filling.computeIfAbsent(worldName, k -> new AtomicBoolean(false));

                int needed = poolSize - pool.size();
                if (needed <= 0) continue;
                if (!isFilling.compareAndSet(false, true)) continue;

                String path    = "WORLD-SETTINGS." + worldName + ".";
                int maxRadius  = plugin.getConfig().getInt(path + "MAX-RADIUS", 20000);
                int minRadius  = plugin.getConfig().getInt(path + "MIN-RADIUS", 1000);
                int centerX    = plugin.getConfig().getInt(path + "CENTER-X", 0);
                int centerZ    = plugin.getConfig().getInt(path + "CENTER-Z", 0);
                int maxAttempts = plugin.getConfig().getInt("SETTINGS.MAX-ATTEMPTS", 25);

                int toGenerate = Math.min(needed, batch);
                int generated  = 0;

                for (int i = 0; i < toGenerate * maxAttempts && generated < toGenerate; i++) {
                    Location loc = tryFindSafeLocation(world, centerX, centerZ, minRadius, maxRadius, maxAttempts);
                    if (loc != null) {
                        pool.add(loc);
                        generated++;
                    }
                }

                isFilling.set(false);
            }
        }, 60L, intervalTicks);
    }

    public Location pollLocation(String worldName) {
        ConcurrentLinkedQueue<Location> pool = pools.get(worldName);
        if (pool == null || pool.isEmpty()) return null;
        return pool.poll();
    }

    public int poolSize(String worldName) {
        ConcurrentLinkedQueue<Location> pool = pools.get(worldName);
        return pool == null ? 0 : pool.size();
    }

    public void shutdown() {
        if (refillTask != null) refillTask.cancel();
    }

    private Location tryFindSafeLocation(World world, int centerX, int centerZ,
                                         int minRadius, int maxRadius, int maxAttempts) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int offsetX, offsetZ;
            do {
                offsetX = rng.nextInt(-maxRadius, maxRadius + 1);
                offsetZ = rng.nextInt(-maxRadius, maxRadius + 1);
            } while (Math.sqrt((double) offsetX * offsetX + (double) offsetZ * offsetZ) < minRadius);

            int x = centerX + offsetX;
            int z = centerZ + offsetZ;

            try {
                // Use Paper's async chunk future — blocks only until this one chunk is ready
                // which is fast since the world is pre-generated (data already on disk)
                Chunk chunk = world.getChunkAtAsync(x >> 4, z >> 4).get();

                Location loc = getSafeY(world, x, z, rng);
                if (loc != null) return loc;
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Pool: chunk failed at " + x + "," + z, e);
            }
        }
        return null;
    }

    /**
     * Gets a safe Y for the given X/Z, handling Nether correctly.
     * In the Nether, scans downward from y=120 to avoid the bedrock roof.
     * In other worlds, uses getHighestBlockYAt then verifies safety.
     */
    private Location getSafeY(World world, int x, int z, ThreadLocalRandom rng) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            // Scan downward from below the roof
            for (int y = 120; y > world.getMinHeight() + 2; y--) {
                Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
                if (isSafe(loc)) {
                    loc.setYaw(rng.nextFloat() * 360f);
                    return loc;
                }
            }
            return null;
        } else {
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
            loc.setYaw(rng.nextFloat() * 360f);
            return isSafe(loc) ? loc : null;
        }
    }

    private boolean isSafe(Location loc) {
        Block feet   = loc.getBlock();
        Block head   = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

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
}
