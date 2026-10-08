package org.pawling.eaglercity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the architecture, protection and persistent discovery coordinates.
 * Other plugins read World PDC keys eaglercity:undercity_{x,y,z,ready}.
 * The optional Zombie, Lucky Chest and Elevator plugins never have to link to
 * EaglerCity at compile time. All world modifications run on the server thread.
 */
public final class Undercity implements Listener {
    private static final int LENGTH = 40, WIDTH = 30, HEIGHT = 20;
    private final EaglerCityPlugin plugin;
    private final TownManager towns;
    private final File file;
    private final YamlConfiguration saved;
    private final Set<UUID> building = new HashSet<>();
    private final NamespacedKey keyX, keyY, keyZ, keyReady;

    public Undercity(EaglerCityPlugin plugin, TownManager towns) {
        this.plugin = plugin;
        this.towns = towns;
        this.file = new File(plugin.getDataFolder(), "undercity.yml");
        this.saved = YamlConfiguration.loadConfiguration(file);
        keyX = new NamespacedKey(plugin, "undercity_x");
        keyY = new NamespacedKey(plugin, "undercity_y");
        keyZ = new NamespacedKey(plugin, "undercity_z");
        keyReady = new NamespacedKey(plugin, "undercity_ready");
    }

    public void ensure(World world) {
        if (!towns.isEnabledWorld(world) || !plugin.getConfig().getBoolean("undercity.enabled", true)) return;
        Location city = towns.getTownCenter(world);
        if (city == null || building.contains(world.getUID())) return;
        String path = "worlds." + world.getUID();
        int cx = city.getBlockX(), cz = city.getBlockZ();
        int floor = city.getBlockY() - 29; // Cavern ceiling is exactly ten blocks below the city floor.
        if (floor < Math.max(5, world.getMinHeight() + 3) || floor + HEIGHT >= Math.min(255, world.getMaxHeight())) {
            plugin.getLogger().warning("Undercity requires a safe client-compatible Y range under " + world.getName());
            return;
        }
        if (saved.getBoolean(path + ".built")
                && saved.getInt(path + ".x") == cx && saved.getInt(path + ".y") == floor
                && saved.getInt(path + ".z") == cz) {
            publish(world, cx, floor, cz);
            return;
        }
        if (!plugin.getConfig().getBoolean("undercity.generate-without-zombies", false)
                && !Bukkit.getPluginManager().isPluginEnabled("EaglerZombiesFall26")) return;
        building.add(world.getUID());
        final int total = LENGTH * WIDTH * HEIGHT;
        new BukkitRunnable() {
            int cursor = 0;
            @Override public void run() {
                int limit = Math.min(total, cursor + 800);
                while (cursor < limit) {
                    int index = cursor++;
                    int dx = index % LENGTH - 20;
                    int dz = (index / LENGTH) % WIDTH - 15;
                    int dy = index / (LENGTH * WIDTH);
                    boolean wall = dx == -20 || dx == 19 || dz == -15 || dz == 14;
                    Material material = dy == 0 ? Material.STONE_BRICKS
                            : dy == HEIGHT - 1 ? Material.DEEPSLATE_BRICKS
                            : wall ? Material.STONE_BRICKS : Material.AIR;
                    world.getBlockAt(cx + dx, floor + dy, cz + dz).setType(material, false);
                }
                if (cursor < total) return;
                try {
                    buildTemple(world, cx, floor, cz);
                    buildFrontSpawners(world, cx, floor, cz);
                    buildBridges(world, cx, floor, cz);
                    saved.set(path + ".built", true);
                    saved.set(path + ".x", cx);
                    saved.set(path + ".y", floor);
                    saved.set(path + ".z", cz);
                    saved.save(file);
                    publish(world, cx, floor, cz);
                    plugin.getLogger().info("Undercity ready below " + world.getName()
                            + " city, cavern 40x30x20, 16x16x16 temple.");
                } catch (IOException | RuntimeException ex) {
                    plugin.getLogger().severe("Undercity build did not finish: " + ex.getMessage());
                } finally {
                    building.remove(world.getUID());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void publish(World world, int x, int y, int z) {
        world.getPersistentDataContainer().set(keyX, PersistentDataType.INTEGER, x);
        world.getPersistentDataContainer().set(keyY, PersistentDataType.INTEGER, y);
        world.getPersistentDataContainer().set(keyZ, PersistentDataType.INTEGER, z);
        world.getPersistentDataContainer().set(keyReady, PersistentDataType.INTEGER, 1);
    }

    private static void cube(World w, int cx, int y, int cz,
                             int x1, int x2, int y1, int y2, int z1, int z2, Material m) {
        for (int dx = x1; dx <= x2; dx++)
            for (int dz = z1; dz <= z2; dz++)
                for (int dy = y1; dy <= y2; dy++)
                    w.getBlockAt(cx + dx, y + dy, cz + dz).setType(m, false);
    }

    private void buildTemple(World w, int x, int y, int z) {
        // Four solid tiers make a true 16-block-high stepped pyramid. Its lower
        // four-block tier provides enough volume for 3-high halls and chambers.
        for (int tier = 0; tier < 4; tier++) {
            int inset = tier * 2;
            cube(w, x, y, z, -8 + inset, 7 - inset, tier * 4, tier * 4 + 3,
                    -7 + inset, 8 - inset, tier % 2 == 0
                            ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
        }
        // Central 2-wide by 3-high hall, face NORTH toward the lift.
        cube(w, x, y, z, -1, 0, 1, 3, -7, 6, Material.AIR);
        // Exactly four 3x3 spawner rooms, each with its own one-wide 3-high approach.
        for (int branchZ : new int[] {-3, 1}) {
            cube(w, x, y, z, -3, -2, 1, 3, branchZ, branchZ, Material.AIR);
            cube(w, x, y, z, 1, 2, 1, 3, branchZ, branchZ, Material.AIR);
            cube(w, x, y, z, -6, -4, 1, 3, branchZ - 1, branchZ + 1, Material.AIR);
            cube(w, x, y, z, 3, 5, 1, 3, branchZ - 1, branchZ + 1, Material.AIR);
            for (int roomX : new int[] {-5, 4}) {
                // Chiseled blocks are markers; EaglerZombies replaces these with configured spawners.
                set(w, x, y, z, roomX, 1, branchZ, Material.CHISELED_STONE_BRICKS);
            }
        }
        // The rear-left spawner room conceals the loot branch.
        cube(w, x, y, z, -5, -5, 1, 3, 3, 4, Material.AIR);
        cube(w, x, y, z, -6, -4, 1, 2, 5, 7, Material.AIR);
        set(w, x, y, z, -5, 3, 6, Material.SEA_LANTERN);
        set(w, x, y, z, -6, 2, 6, Material.TORCH);
        set(w, x, y, z, -4, 2, 6, Material.TORCH);
        // Two-high locked iron door, toggleable only by a real player's click.
        set(w, x, y, z, -5, 1, 5, Material.IRON_DOOR);
        Door bottom = (Door) Bukkit.createBlockData(Material.IRON_DOOR);
        bottom.setFacing(org.bukkit.block.BlockFace.SOUTH);
        bottom.setHalf(Bisected.Half.BOTTOM);
        Door top = (Door) bottom.clone(); top.setHalf(Bisected.Half.TOP);
        w.getBlockAt(x - 5, y + 1, z + 5).setBlockData(bottom, false);
        w.getBlockAt(x - 5, y + 2, z + 5).setBlockData(top, false);
        // LuckyChests turns this tagged block into one of two ultimate rewards on first opening.
        Block treasure = w.getBlockAt(x - 5, y + 1, z + 6);
        treasure.setType(Material.CHEST, false);
        if (treasure.getState() instanceof org.bukkit.block.Chest chest) {
            chest.getPersistentDataContainer().set(
                    new NamespacedKey("luckychests", "lucky_chest_type"),
                    PersistentDataType.STRING, "ULTIMATE");
            chest.getPersistentDataContainer().set(
                    new NamespacedKey("luckychests", "lucky_chest_opened"),
                    PersistentDataType.BYTE, (byte) 0);
            if (!Bukkit.getPluginManager().isPluginEnabled("LuckyChests")) {
                chest.getBlockInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
            }
            chest.update(true, false);
        }
        set(w, x, y, z, -1, 0, -7, Material.CHISELED_STONE_BRICKS);
        set(w, x, y, z, 0, 0, -7, Material.CHISELED_STONE_BRICKS);
    }

    private static void set(World w, int x, int y, int z, int dx, int dy, int dz, Material m) {
        w.getBlockAt(x + dx, y + dy, z + dz).setType(m, false);
    }

    private void buildFrontSpawners(World w, int x, int y, int z) {
        for (int sx : new int[] {-10, 9}) {
            cube(w, x, y, z, sx - 2, sx + 2, 0, 0, -12, -8, Material.MOSSY_STONE_BRICKS);
            // Four short columns and an open-ended stone canopy.
            for (int dx : new int[] {-2, 2})
                for (int dz : new int[] {-2, 2})
                    cube(w, x, y, z, sx + dx, sx + dx, 1, 3, -10 + dz, -10 + dz, Material.COBBLESTONE);
            cube(w, x, y, z, sx - 2, sx + 2, 4, 4, -12, -8, Material.STONE_BRICKS);
            set(w, x, y, z, sx, 1, -10, Material.CHISELED_STONE_BRICKS);
        }
    }

    private void buildBridges(World w, int x, int y, int z) {
        int elevation = 8;
        // Elevated two-wide perimeter beams, with a few torch-lit crossovers.
        for (int dz : new int[] {-12, 11}) {
            cube(w, x, y, z, -17, 16, elevation, elevation, dz, dz + 1, Material.OAK_PLANKS);
            for (int dx = -16; dx <= 16; dx += 8)
                set(w, x, y, z, dx, elevation + 1, dz, Material.TORCH);
        }
        for (int dx : new int[] {-17, 15})
            cube(w, x, y, z, dx, dx + 1, elevation, elevation, -12, 12, Material.OAK_PLANKS);
        for (int dx : new int[] {-16, 16})
            for (int dz : new int[] {-11, 11}) {
                cube(w, x, y, z, dx, dx, 1, 7, dz, dz, Material.COBBLESTONE);
                // Simple ladder ascent on the side of each support (one solid block backing).
                for (int h = 1; h <= 7; h++) {
                    Block ladder = w.getBlockAt(x + dx + (dx < 0 ? 1 : -1), y + h, z + dz);
                    ladder.setType(Material.LADDER, false);
                    org.bukkit.block.data.Directional data = (org.bukkit.block.data.Directional) ladder.getBlockData();
                    data.setFacing(dx < 0 ? org.bukkit.block.BlockFace.EAST : org.bukkit.block.BlockFace.WEST);
                    ladder.setBlockData(data, false);
                }
            }
    }

    private boolean inside(World w, int x, int y, int z) {
        if (w.getPersistentDataContainer().getOrDefault(keyReady, PersistentDataType.INTEGER, 0) != 1) return false;
        int cx = w.getPersistentDataContainer().getOrDefault(keyX, PersistentDataType.INTEGER, Integer.MAX_VALUE);
        int cy = w.getPersistentDataContainer().getOrDefault(keyY, PersistentDataType.INTEGER, Integer.MAX_VALUE);
        int cz = w.getPersistentDataContainer().getOrDefault(keyZ, PersistentDataType.INTEGER, Integer.MAX_VALUE);
        return x >= cx - 20 && x <= cx + 19 && z >= cz - 15 && z <= cz + 14
                && y >= cy && y < cy + HEIGHT;
    }

    private boolean inside(Block block) {
        return inside(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (inside(event.getBlock())) { event.setCancelled(true); event.getPlayer().sendActionBar("The Undercity cannot be mined."); }
    }
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (inside(event.getBlockPlaced())) { event.setCancelled(true); event.getPlayer().sendActionBar("Undercity structures are protected."); }
    }
    @EventHandler(ignoreCancelled = true)
    public void onFill(PlayerBucketFillEvent e) { if (inside(e.getBlock())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onEmpty(PlayerBucketEmptyEvent e) { if (inside(e.getBlock())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent e) { if (inside(e.getBlock())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) { e.blockList().removeIf(this::inside); }
    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(this::inside); }
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> inside(b) || inside(b.getRelative(e.getDirection())))) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> inside(b) || inside(b.getRelative(e.getDirection())))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTreasureDoor(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked.getType() != Material.IRON_DOOR || !inside(clicked)) return;
        Block doorBlock = clicked;
        Door doorData = (Door) doorBlock.getBlockData();
        if (doorData.getHalf() == Bisected.Half.TOP) doorBlock = clicked.getRelative(org.bukkit.block.BlockFace.DOWN);
        if (!(doorBlock.getBlockData() instanceof Door bottom)) return;
        if (bottom.getFacing() != org.bukkit.block.BlockFace.SOUTH) return;
        // Require the exact treasure door relative to the published center.
        int cx = clicked.getWorld().getPersistentDataContainer().getOrDefault(keyX, PersistentDataType.INTEGER, 0);
        int cy = clicked.getWorld().getPersistentDataContainer().getOrDefault(keyY, PersistentDataType.INTEGER, 0);
        int cz = clicked.getWorld().getPersistentDataContainer().getOrDefault(keyZ, PersistentDataType.INTEGER, 0);
        if (doorBlock.getX() != cx - 5 || doorBlock.getY() != cy + 1 || doorBlock.getZ() != cz + 5) return;
        event.setCancelled(true);
        Block lower = doorBlock;
        Block upper = doorBlock.getRelative(org.bukkit.block.BlockFace.UP);
        bottom.setOpen(true);
        lower.setBlockData(bottom, false);
        if (upper.getBlockData() instanceof Door top) {
            top.setOpen(true);
            upper.setBlockData(top, false);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (lower.getBlockData() instanceof Door closed) {
                closed.setOpen(false); lower.setBlockData(closed, false);
            }
            if (upper.getBlockData() instanceof Door closed) {
                closed.setOpen(false); upper.setBlockData(closed, false);
            }
        }, 35L);
    }
}
