package org.pawling.eaglercity;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Terrain helpers used by EaglerCity generation.
 *
 * Minecraft's motion-blocking heightmap can stop on a tree trunk. For a town
 * foundation that is usually the wrong answer, so we walk down through natural
 * tree materials until we reach the real supporting terrain.
 */
public final class GroundUtil {
    private GroundUtil() {
    }

    public static int groundY(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);

        while (y > world.getMinHeight() + 1) {
            Material material = world.getBlockAt(x, y, z).getType();
            if (!isTreeOrTallVegetation(material)) {
                break;
            }
            y--;
        }

        return y;
    }

    public static Material surfaceMaterial(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
        return world.getBlockAt(x, y, z).getType();
    }

    private static boolean isTreeOrTallVegetation(Material material) {
        String name = material.name();
        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_LEAVES")
                || material == Material.BAMBOO
                || material == Material.CACTUS
                || material == Material.SUGAR_CANE
                || material == Material.MUSHROOM_STEM
                || material == Material.VINE;
    }
}
