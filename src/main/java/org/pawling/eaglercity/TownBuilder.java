package org.pawling.eaglercity;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.plugin.java.JavaPlugin;

public final class TownBuilder {
    private TownBuilder() {
    }

    public static void buildTown(JavaPlugin plugin, World world, Location center) {
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        int plazaY = highestBuildY(world, cx - 4, cx + 4, cz - 4, cz + 4);
        buildPlatform(world, cx - 4, cx + 4, cz - 4, cz + 4, plazaY, Material.STONE_BRICKS);
        world.getBlockAt(cx, plazaY + 1, cz).setType(Material.BELL, false);

        placeLamp(world, cx - 3, plazaY + 1, cz - 3);
        placeLamp(world, cx + 3, plazaY + 1, cz - 3);
        placeLamp(world, cx - 3, plazaY + 1, cz + 3);
        placeLamp(world, cx + 3, plazaY + 1, cz + 3);

        for (CityProfession profile : CityProfession.values()) {
            int bx = cx + profile.xOffset();
            int bz = cz + profile.zOffset();
            buildRoad(world, cx, cz, bx, bz);
            buildCottage(world, center, profile);
        }

        buildFarm(world, cx - 23, cz - 14);
        plugin.getLogger().info("EaglerCity town structures generated near "
                + cx + ", " + cz + " in " + world.getName() + ".");
    }

    private static void buildCottage(World world, Location townCenter, CityProfession profile) {
        int cx = townCenter.getBlockX() + profile.xOffset();
        int cz = townCenter.getBlockZ() + profile.zOffset();
        int minX = cx - 3;
        int maxX = cx + 3;
        int minZ = cz - 3;
        int maxZ = cz + 3;
        int baseY = highestBuildY(world, minX, maxX, minZ, maxZ);

        buildPlatform(world, minX, maxX, minZ, maxZ, baseY, Material.SPRUCE_PLANKS);

        for (int y = baseY + 1; y <= baseY + 3; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                    if (!edge) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                        continue;
                    }

                    boolean corner = (x == minX || x == maxX) && (z == minZ || z == maxZ);
                    world.getBlockAt(x, y, z).setType(corner ? Material.OAK_LOG : Material.OAK_PLANKS, false);
                }
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.getBlockAt(x, baseY + 4, z).setType(Material.DARK_OAK_SLAB, false);
            }
        }

        // Windows on each side.
        world.getBlockAt(cx, baseY + 2, minZ).setType(Material.GLASS_PANE, false);
        world.getBlockAt(cx, baseY + 2, maxZ).setType(Material.GLASS_PANE, false);
        world.getBlockAt(minX, baseY + 2, cz).setType(Material.GLASS_PANE, false);
        world.getBlockAt(maxX, baseY + 2, cz).setType(Material.GLASS_PANE, false);

        // Open a two-block doorway on the wall facing the plaza. An open doorway is
        // more reliable for villager pathfinding than forcing a door state.
        int doorX = cx;
        int doorZ = cz;
        if (Math.abs(profile.xOffset()) >= Math.abs(profile.zOffset()) && profile.xOffset() != 0) {
            doorX = profile.xOffset() > 0 ? minX : maxX;
        } else {
            doorZ = profile.zOffset() > 0 ? minZ : maxZ;
        }
        world.getBlockAt(doorX, baseY + 1, doorZ).setType(Material.AIR, false);
        world.getBlockAt(doorX, baseY + 2, doorZ).setType(Material.AIR, false);
        world.getBlockAt(doorX, baseY + 3, doorZ).setType(Material.LANTERN, false);

        // Two workstations support the two default residents per profession.
        world.getBlockAt(cx - 1, baseY + 1, cz + 2).setType(profile.workstation(), false);
        world.getBlockAt(cx + 1, baseY + 1, cz + 2).setType(profile.workstation(), false);

        placeBed(world, minX + 1, baseY + 1, minZ + 1, BlockFace.SOUTH, Material.RED_BED);
        placeBed(world, maxX - 1, baseY + 1, minZ + 1, BlockFace.SOUTH, Material.YELLOW_BED);

        // Small visual identity marker by the entrance.
        Material marker = switch (profile) {
            case FARMER -> Material.HAY_BLOCK;
            case FLETCHER -> Material.TARGET;
            case TOOLSMITH -> Material.ANVIL;
            case ARMORER -> Material.IRON_BLOCK;
            case LIBRARIAN -> Material.BOOKSHELF;
            case BUTCHER -> Material.BARREL;
            case MASON -> Material.STONE_BRICKS;
        };
        int markerX = doorX;
        int markerZ = doorZ;
        if (doorX == minX) {
            markerX--;
        } else if (doorX == maxX) {
            markerX++;
        } else if (doorZ == minZ) {
            markerZ--;
        } else {
            markerZ++;
        }
        int markerY = GroundUtil.groundY(world, markerX, markerZ) + 1;
        world.getBlockAt(markerX, markerY, markerZ).setType(marker, false);
    }

    private static void buildFarm(World world, int cx, int cz) {
        int minX = cx - 4;
        int maxX = cx + 4;
        int minZ = cz - 3;
        int maxZ = cz + 3;
        int baseY = highestBuildY(world, minX, maxX, minZ, maxZ);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                clearColumn(world, x, z, baseY + 1, baseY + 3);
                fillFoundation(world, x, z, baseY, Material.DIRT);

                if (x == cx) {
                    world.getBlockAt(x, baseY, z).setType(Material.WATER, false);
                    continue;
                }

                world.getBlockAt(x, baseY, z).setType(Material.FARMLAND, false);
                Material crop = switch (Math.floorMod(x + z, 3)) {
                    case 0 -> Material.WHEAT;
                    case 1 -> Material.CARROTS;
                    default -> Material.POTATOES;
                };
                Block cropBlock = world.getBlockAt(x, baseY + 1, z);
                cropBlock.setType(crop, false);
                if (cropBlock.getBlockData() instanceof Ageable ageable) {
                    ageable.setAge(ageable.getMaximumAge());
                    cropBlock.setBlockData(ageable, false);
                }
            }
        }

        for (int x = minX - 1; x <= maxX + 1; x++) {
            setFenceAtSurface(world, x, minZ - 1);
            setFenceAtSurface(world, x, maxZ + 1);
        }
        for (int z = minZ; z <= maxZ; z++) {
            setFenceAtSurface(world, minX - 1, z);
            setFenceAtSurface(world, maxX + 1, z);
        }
    }

    private static void setFenceAtSurface(World world, int x, int z) {
        int y = GroundUtil.groundY(world, x, z) + 1;
        world.getBlockAt(x, y, z).setType(Material.OAK_FENCE, false);
    }

    private static void placeBed(World world, int footX, int y, int footZ, BlockFace facing, Material material) {
        int headX = footX + facing.getModX();
        int headZ = footZ + facing.getModZ();

        Bed foot = (Bed) material.createBlockData();
        foot.setFacing(facing);
        foot.setPart(Bed.Part.FOOT);

        Bed head = (Bed) material.createBlockData();
        head.setFacing(facing);
        head.setPart(Bed.Part.HEAD);

        world.getBlockAt(footX, y, footZ).setBlockData(foot, false);
        world.getBlockAt(headX, y, headZ).setBlockData(head, false);
    }

    private static void buildRoad(World world, int fromX, int fromZ, int toX, int toZ) {
        int x = fromX;
        int z = fromZ;

        while (x != toX) {
            roadPatch(world, x, z);
            x += Integer.compare(toX, x);
        }
        while (z != toZ) {
            roadPatch(world, x, z);
            z += Integer.compare(toZ, z);
        }
        roadPatch(world, toX, toZ);
    }

    private static void roadPatch(World world, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                int y = GroundUtil.groundY(world, x, z);
                Material top = world.getBlockAt(x, y, z).getType();
                if (top == Material.WATER || top == Material.LAVA) {
                    continue;
                }
                world.getBlockAt(x, y, z).setType(Material.GRAVEL, false);
                world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
            }
        }
    }

    private static void placeLamp(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.OAK_FENCE, false);
        world.getBlockAt(x, y + 1, z).setType(Material.OAK_FENCE, false);
        world.getBlockAt(x, y + 2, z).setType(Material.LANTERN, false);
    }

    private static int highestBuildY(World world, int minX, int maxX, int minZ, int maxZ) {
        int highest = world.getMinHeight() + 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                highest = Math.max(highest,
                        GroundUtil.groundY(world, x, z) + 1);
            }
        }
        return Math.min(highest, world.getMaxHeight() - 8);
    }

    private static void buildPlatform(
            World world,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int y,
            Material floor
    ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                clearColumn(world, x, z, y + 1, y + 5);
                fillFoundation(world, x, z, y, Material.COBBLESTONE);
                world.getBlockAt(x, y, z).setType(floor, false);
            }
        }
    }

    private static void fillFoundation(World world, int x, int z, int targetY, Material material) {
        int surface = GroundUtil.groundY(world, x, z);
        int from = Math.min(surface + 1, targetY);
        for (int y = from; y <= targetY; y++) {
            world.getBlockAt(x, y, z).setType(material, false);
        }
    }

    private static void clearColumn(World world, int x, int z, int minY, int maxY) {
        for (int y = minY; y <= maxY && y < world.getMaxHeight(); y++) {
            world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
    }
}
