package org.pawling.eaglercity;

import org.bukkit.Material;
import org.bukkit.entity.Villager;

import java.util.Map;

public enum CityProfession {
    FARMER(
            "farmer", "Farmer", Villager.Profession.FARMER, Material.COMPOSTER, -14, -14,
            Map.of(
                    Material.WHEAT, 18,
                    Material.WHEAT_SEEDS, 12,
                    Material.CARROT, 10,
                    Material.POTATO, 10,
                    Material.BREAD, 8,
                    Material.COOKED_BEEF, 3,
                    Material.STICK, 6
            ),
            Map.of(Material.WHEAT_SEEDS, 2, Material.WHEAT, 2, Material.BREAD, 1),
            Map.of(Material.WHEAT_SEEDS, 2),
            Map.of(Material.WHEAT, 6, Material.CARROT, 3, Material.POTATO, 3, Material.BREAD, 2)
    ),
    FLETCHER(
            "fletcher", "Fletcher", Villager.Profession.FLETCHER, Material.FLETCHING_TABLE, 14, -14,
            Map.of(
                    Material.STICK, 16,
                    Material.FLINT, 8,
                    Material.FEATHER, 10,
                    Material.ARROW, 24,
                    Material.STRING, 8,
                    Material.BREAD, 4
            ),
            Map.of(Material.STICK, 3, Material.FLINT, 1, Material.FEATHER, 1),
            Map.of(Material.STICK, 4, Material.FLINT, 1, Material.FEATHER, 2),
            Map.of(Material.ARROW, 8, Material.STRING, 1)
    ),
    TOOLSMITH(
            "toolsmith", "Toolsmith", Villager.Profession.TOOLSMITH, Material.SMITHING_TABLE, -14, 0,
            Map.of(
                    Material.IRON_INGOT, 10,
                    Material.COAL, 8,
                    Material.STICK, 12,
                    Material.COBBLESTONE, 8,
                    Material.IRON_PICKAXE, 1,
                    Material.IRON_HOE, 1,
                    Material.BREAD, 4
            ),
            Map.of(Material.IRON_INGOT, 1, Material.COAL, 1, Material.STICK, 2),
            Map.of(Material.IRON_INGOT, 4, Material.COAL, 1, Material.STICK, 2),
            Map.of(Material.IRON_PICKAXE, 1, Material.IRON_HOE, 1)
    ),
    ARMORER(
            "armorer", "Armorer", Villager.Profession.ARMORER, Material.BLAST_FURNACE, 14, 0,
            Map.of(
                    Material.IRON_INGOT, 12,
                    Material.COAL, 8,
                    Material.LEATHER, 8,
                    Material.IRON_NUGGET, 16,
                    Material.IRON_HELMET, 1,
                    Material.BREAD, 4
            ),
            Map.of(Material.IRON_INGOT, 1, Material.COAL, 1),
            Map.of(Material.IRON_INGOT, 4, Material.COAL, 1, Material.LEATHER, 1),
            Map.of(Material.IRON_NUGGET, 8, Material.IRON_HELMET, 1)
    ),
    LIBRARIAN(
            "librarian", "Librarian", Villager.Profession.LIBRARIAN, Material.LECTERN, -14, 14,
            Map.of(
                    Material.PAPER, 16,
                    Material.BOOK, 8,
                    Material.LEATHER, 6,
                    Material.INK_SAC, 6,
                    Material.FEATHER, 6,
                    Material.BREAD, 4
            ),
            Map.of(Material.PAPER, 2, Material.INK_SAC, 1, Material.FEATHER, 1),
            Map.of(Material.PAPER, 3, Material.LEATHER, 1),
            Map.of(Material.BOOK, 2)
    ),
    BUTCHER(
            "butcher", "Butcher", Villager.Profession.BUTCHER, Material.SMOKER, 14, 14,
            Map.of(
                    Material.WHEAT, 8,
                    Material.COAL, 8,
                    Material.BEEF, 6,
                    Material.PORKCHOP, 6,
                    Material.COOKED_BEEF, 8,
                    Material.LEATHER, 8,
                    Material.BREAD, 4
            ),
            Map.of(Material.COAL, 1, Material.BEEF, 1, Material.PORKCHOP, 1),
            Map.of(Material.WHEAT, 2, Material.COAL, 1, Material.BEEF, 2),
            Map.of(Material.COOKED_BEEF, 3, Material.LEATHER, 2, Material.PORKCHOP, 2)
    ),
    MASON(
            "mason", "Mason", Villager.Profession.MASON, Material.STONECUTTER, 0, 18,
            Map.of(
                    Material.COBBLESTONE, 20,
                    Material.STONE, 16,
                    Material.CLAY_BALL, 12,
                    Material.BRICK, 12,
                    Material.COAL, 8,
                    Material.BREAD, 4
            ),
            Map.of(Material.COBBLESTONE, 4, Material.CLAY_BALL, 2, Material.COAL, 1),
            Map.of(Material.COBBLESTONE, 4, Material.CLAY_BALL, 4, Material.COAL, 1),
            Map.of(Material.STONE, 6, Material.BRICK, 4)
    );

    private final String id;
    private final String displayName;
    private final Villager.Profession profession;
    private final Material workstation;
    private final int xOffset;
    private final int zOffset;
    private final Map<Material, Integer> targets;
    private final Map<Material, Integer> dailyStock;
    private final Map<Material, Integer> consumes;
    private final Map<Material, Integer> produces;

    CityProfession(
            String id,
            String displayName,
            Villager.Profession profession,
            Material workstation,
            int xOffset,
            int zOffset,
            Map<Material, Integer> targets,
            Map<Material, Integer> dailyStock,
            Map<Material, Integer> consumes,
            Map<Material, Integer> produces
    ) {
        this.id = id;
        this.displayName = displayName;
        this.profession = profession;
        this.workstation = workstation;
        this.xOffset = xOffset;
        this.zOffset = zOffset;
        this.targets = Map.copyOf(targets);
        this.dailyStock = Map.copyOf(dailyStock);
        this.consumes = Map.copyOf(consumes);
        this.produces = Map.copyOf(produces);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Villager.Profession profession() {
        return profession;
    }

    public Material workstation() {
        return workstation;
    }

    public int xOffset() {
        return xOffset;
    }

    public int zOffset() {
        return zOffset;
    }

    public Map<Material, Integer> targets() {
        return targets;
    }

    public Map<Material, Integer> dailyStock() {
        return dailyStock;
    }

    public Map<Material, Integer> consumes() {
        return consumes;
    }

    public Map<Material, Integer> produces() {
        return produces;
    }

    public static CityProfession fromId(String id) {
        if (id == null) {
            return null;
        }
        for (CityProfession profile : values()) {
            if (profile.id.equalsIgnoreCase(id)) {
                return profile;
            }
        }
        return null;
    }

    public static CityProfession fromProfession(Villager.Profession profession) {
        for (CityProfession profile : values()) {
            if (profile.profession.equals(profession)) {
                return profile;
            }
        }
        return null;
    }
}
