package org.pawling.eaglercity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.plugin.java.JavaPlugin;

public final class TownBuilder {
    private static final int ACCESS_VERSION = 2;

    private TownBuilder() {
    }

    public static int accessVersion() {
        return ACCESS_VERSION;
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

    /**
     * Retrofitting is intentionally separate from first-time generation so worlds
     * created by EaglerCity 1.0.0 gain reachable entrances without rebuilding or
     * wiping the existing cottages.
     */
    public static void ensureTownAccess(JavaPlugin plugin, World world, Location center) {
        int repaired = 0;
        for (CityProfession profile : CityProfession.values()) {
            Integer floorY = findCottageFloorY(world, center, profile);
            if (floorY == null) {
                plugin.getLogger().warning("Could not find " + profile.displayName()
                        + " workstation while retrofitting cottage access.");
                continue;
            }

            DoorInfo door = doorInfo(center, profile);
            buildEntranceRamp(world, door.x(), door.z(), floorY, door.outX(), door.outZ());
            repaired++;
        }

        plugin.getLogger().info("EaglerCity access retrofit checked "
                + repaired + " cottage entrance(s) in " + world.getName() + ".");
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
                    world.getBlockAt(x, y, z)
                            .setType(corner ? Material.OAK_LOG : Material.OAK_PLANKS, false);
                }
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.getBlockAt(x, baseY + 4, z).setType(Material.DARK_OAK_SLAB, false);
            }
        }

        world.getBlockAt(cx, baseY + 2, minZ).setType(Material.GLASS_PANE, false);
        world.getBlockAt(cx, baseY + 2, maxZ).setType(Material.GLASS_PANE, false);
        world.getBlockAt(minX, baseY + 2, cz).setType(Material.GLASS_PANE, false);
        world.getBlockAt(maxX, baseY + 2, cz).setType(Material.GLASS_PANE, false);

        DoorInfo door = doorInfo(townCenter, profile);
        world.getBlockAt(door.x(), baseY + 1, door.z()).setType(Material.AIR, false);
        world.getBlockAt(door.x(), baseY + 2, door.z()).setType(Material.AIR, false);
        world.getBlockAt(door.x(), baseY + 3, door.z()).setType(Material.LANTERN, false);

        // A three-wide terraced staircase makes elevated cottages reachable even
        // when the generation site is sloped.
        buildEntranceRamp(world, door.x(), door.z(), baseY, door.outX(), door.outZ());

        world.getBlockAt(cx - 1, baseY + 1, cz + 2).setType(profile.workstation(), false);
        world.getBlockAt(cx + 1, baseY + 1, cz + 2).setType(profile.workstation(), false);

        placeBed(world, minX + 1, baseY + 1, minZ + 1, BlockFace.SOUTH, Material.RED_BED);
        placeBed(world, maxX - 1, baseY + 1, minZ + 1, BlockFace.SOUTH, Material.YELLOW_BED);

        Material marker = switch (profile) {
            case FARMER -> Material.HAY_BLOCK;
            case FLETCHER -> Material.TARGET;
            case TOOLSMITH -> Material.ANVIL;
            case ARMORER -> Material.IRON_BLOCK;
            case LIBRARIAN -> Material.BOOKSHELF;
            case BUTCHER -> Material.BARREL;
            case MASON -> Material.STONE_BRICKS;
        };

        int markerX = door.x() + door.outX();
        int markerZ = door.z() + door.outZ();
        int markerY = GroundUtil.groundY(world, markerX, markerZ) + 1;
        world.getBlockAt(markerX, markerY, markerZ).setType(marker, false);
    }

    private static DoorInfo doorInfo(Location townCenter, CityProfession profile) {
        int cx = townCenter.getBlockX() + profile.xOffset();
        int cz = townCenter.getBlockZ() + profile.zOffset();
        int minX = cx - 3;
        int maxX = cx + 3;
        int minZ = cz - 3;
        int maxZ = cz + 3;

        if (Math.abs(profile.xOffset()) >= Math.abs(profile.zOffset())
                && profile.xOffset() != 0) {
            if (profile.xOffset() > 0) {
                return new DoorInfo(minX, cz, -1, 0);
            }
            return new DoorInfo(maxX, cz, 1, 0);
        }

        if (profile.zOffset() > 0) {
            return new DoorInfo(cx, minZ, 0, -1);
        }
        return new DoorInfo(cx, maxZ, 0, 1);
    }

    /**
     * Creates a reliable full-block staircase rather than decorative stair blocks.
     * Villager pathfinding is happiest with one-block vertical changes and a
     * three-block-wide route with clear headroom.
     */
    private static void buildEntranceRamp(
            World world,
            int doorX,
            int doorZ,
            int floorY,
            int outX,
            int outZ
    ) {
        int sideX = outZ;
        int sideZ = -outX;
        int previousTop = floorY;

        for (int step = 1; step <= 32; step++) {
            int centerX = doorX + outX * step;
            int centerZ = doorZ + outZ * step;
            int desiredTop = floorY - Math.max(0, step - 1);
            int ground = GroundUtil.groundY(world, centerX, centerZ);

            int topY;
            boolean reachedGround;
            if (ground >= desiredTop) {
                topY = ground;
                reachedGround = true;
            } else {
                topY = desiredTop;
                reachedGround = false;
            }

            // Avoid introducing a two-block vertical jump at the final connection.
            if (topY > previousTop + 1) {
                topY = previousTop + 1;
                reachedGround = false;
            }

            for (int width = -1; width <= 1; width++) {
                int x = centerX + sideX * width;
                int z = centerZ + sideZ * width;
                int columnGround = GroundUtil.groundY(world, x, z);

                for (int y = Math.min(columnGround + 1, topY); y < topY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.COBBLESTONE, false);
                }

                world.getBlockAt(x, topY, z).setType(Material.STONE_BRICKS, false);
                clearColumn(world, x, z, topY + 1, topY + 3);
            }

            // Gentle lighting along one side without placing obstacles in the
            // center lane used by villagers.
            if (step % 5 == 0 && !reachedGround) {
                int lx = centerX + sideX * 2;
                int lz = centerZ + sideZ * 2;
                int lightGround = GroundUtil.groundY(world, lx, lz);
                int postY = Math.max(lightGround + 1, topY);
                world.getBlockAt(lx, postY, lz).setType(Material.OAK_FENCE, false);
                world.getBlockAt(lx, postY + 1, lz).setType(Material.LANTERN, false);
            }

            previousTop = topY;
            if (reachedGround) {
                break;
            }
        }
    }

    private static Integer findCottageFloorY(
            World world,
            Location townCenter,
            CityProfession profile
    ) {
        int cx = townCenter.getBlockX() + profile.xOffset();
        int cz = townCenter.getBlockZ() + profile.zOffset();

        int[] xs = new int[]{cx - 1, cx + 1};
        int z = cz + 2;

        for (int x : xs) {
            for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                if (world.getBlockAt(x, y, z).getType() == profile.workstation()) {
                    return y - 1;
                }
            }
        }
        return null;
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

    private static void placeBed(
            World world,
            int footX,
            int y,
            int footZ,
            BlockFace facing,
            Material material
    ) {
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
                highest = Math.max(highest, GroundUtil.groundY(world, x, z) + 1);
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

    private static void fillFoundation(
            World world,
            int x,
            int z,
            int targetY,
            Material material
    ) {
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

    private record DoorInfo(int x, int z, int outX, int outZ) {
    }
}
