package org.pawling.eaglercity;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TownManager {
    private final JavaPlugin plugin;
    private final org.bukkit.NamespacedKey residentKey;
    private final org.bukkit.NamespacedKey golemKey;
    private final org.bukkit.NamespacedKey profileKey;

    private final File stateFile;
    private final YamlConfiguration state;
    private final Map<UUID, Location> townCenters = new HashMap<>();
    private final Map<UUID, Long> panicUntil = new HashMap<>();

    public TownManager(
            JavaPlugin plugin,
            org.bukkit.NamespacedKey residentKey,
            org.bukkit.NamespacedKey golemKey,
            org.bukkit.NamespacedKey profileKey
    ) {
        this.plugin = plugin;
        this.residentKey = residentKey;
        this.golemKey = golemKey;
        this.profileKey = profileKey;

        plugin.getDataFolder().mkdirs();
        this.stateFile = new File(plugin.getDataFolder(), "state.yml");
        this.state = YamlConfiguration.loadConfiguration(stateFile);
    }

    public void initialize() {
        for (World world : plugin.getServer().getWorlds()) {
            if (isEnabledWorld(world)) {
                ensureTown(world);
            }
        }
    }

    public boolean isEnabledWorld(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }
        List<String> enabled = plugin.getConfig().getStringList("enabled-worlds");
        return enabled.isEmpty() || enabled.contains(world.getName());
    }

    public Location ensureTown(World world) {
        if (!isEnabledWorld(world)) {
            return null;
        }

        Location existing = loadCenter(world);
        if (existing != null) {
            townCenters.put(world.getUID(), existing);
            return existing;
        }

        Location center = chooseTownSite(world);
        TownBuilder.buildTown(plugin, world, center);
        saveCenter(world, center);
        townCenters.put(world.getUID(), center);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            repopulate(world);
            ensureSecurity(world);
        }, 20L);

        return center;
    }

    public Location getTownCenter(World world) {
        Location center = townCenters.get(world.getUID());
        if (center != null) {
            return center.clone();
        }
        Location loaded = loadCenter(world);
        if (loaded != null) {
            townCenters.put(world.getUID(), loaded);
            return loaded.clone();
        }
        return null;
    }

    public void repopulate(World world) {
        Location center = ensureTown(world);
        if (center == null) {
            return;
        }

        int desiredPerProfession = Math.max(1,
                plugin.getConfig().getInt("town.residents-per-profession", 2));
        List<Villager> existing = getCityVillagers(world);

        for (CityProfession profile : CityProfession.values()) {
            long count = existing.stream()
                    .filter(v -> profile.id().equals(getProfileId(v)))
                    .count();

            for (int i = (int) count; i < desiredPerProfession; i++) {
                Villager villager = spawnResident(world, center, profile, i);
                existing.add(villager);
            }
        }
    }

    private Villager spawnResident(World world, Location center, CityProfession profile, int index) {
        double factor = 0.56;
        int baseX = center.getBlockX() + (int) Math.round(profile.xOffset() * factor);
        int baseZ = center.getBlockZ() + (int) Math.round(profile.zOffset() * factor);
        int x = baseX + (index % 2 == 0 ? -1 : 1);
        int z = baseZ + (index % 3 == 0 ? 1 : 0);
        int y = GroundUtil.groundY(world, x, z) + 1;

        Villager villager = (Villager) world.spawnEntity(
                new Location(world, x + 0.5, y, z + 0.5),
                EntityType.VILLAGER
        );
        villager.setAdult();
        villager.setProfession(profile.profession());
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setCanPickupItems(true);
        villager.customName(net.kyori.adventure.text.Component.text(profile.displayName() + " of Eagler City"));
        villager.setCustomNameVisible(false);

        villager.getPersistentDataContainer().set(residentKey, PersistentDataType.INTEGER, 1);
        villager.getPersistentDataContainer().set(profileKey, PersistentDataType.STRING, profile.id());

        for (Map.Entry<Material, Integer> entry : profile.targets().entrySet()) {
            int amount = Math.max(1, (int) Math.ceil(entry.getValue() * 0.65));
            villager.getInventory().addItem(new ItemStack(entry.getKey(), amount));
        }

        return villager;
    }

    public void ensureSecurity(World world) {
        Location center = getTownCenter(world);
        if (center == null) {
            return;
        }

        int villagers = getCityVillagers(world).size();
        if (villagers <= 0) {
            return;
        }

        int villagersPerGolem = Math.max(1,
                plugin.getConfig().getInt("security.villagers-per-golem", 5));
        int maxGolems = Math.max(1,
                plugin.getConfig().getInt("security.max-golems", 4));
        int desired = Math.min(maxGolems,
                Math.max(1, (int) Math.ceil(villagers / (double) villagersPerGolem)));

        List<IronGolem> golems = getCityGolems(world);
        for (int i = golems.size(); i < desired; i++) {
            spawnSecurityGolem(world, center, i);
        }
    }

    private IronGolem spawnSecurityGolem(World world, Location center, int index) {
        double angle = (Math.PI * 2.0 * index) / 4.0;
        int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * 6.0);
        int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * 6.0);
        int y = GroundUtil.groundY(world, x, z) + 1;

        IronGolem golem = (IronGolem) world.spawnEntity(
                new Location(world, x + 0.5, y, z + 0.5),
                EntityType.IRON_GOLEM
        );
        golem.setPersistent(true);
        golem.setRemoveWhenFarAway(false);
        golem.getPersistentDataContainer().set(golemKey, PersistentDataType.INTEGER, 1);
        golem.customName(net.kyori.adventure.text.Component.text("Eagler City Guard"));
        golem.setCustomNameVisible(false);
        return golem;
    }

    public void alertSecurity(Villager villager, Player attacker) {
        World world = villager.getWorld();
        ensureSecurity(world);

        double range = Math.max(8.0,
                plugin.getConfig().getDouble("security.alert-range", 48.0));
        IronGolem nearest = world.getNearbyEntities(villager.getLocation(), range, range, range).stream()
                .filter(IronGolem.class::isInstance)
                .map(IronGolem.class::cast)
                .filter(this::isCityGolem)
                .min(Comparator.comparingDouble(g -> g.getLocation().distanceSquared(villager.getLocation())))
                .orElse(null);

        panicUntil.put(villager.getUniqueId(), System.currentTimeMillis() + 6000L);

        if (nearest != null) {
            nearest.setTarget(attacker);
            double speed = Math.max(0.5,
                    plugin.getConfig().getDouble("security.panic-speed", 1.35));

            for (long delay : new long[]{0L, 20L, 40L, 60L}) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (villager.isValid() && !villager.isDead()
                            && nearest.isValid() && !nearest.isDead()) {
                        villager.getPathfinder().moveTo(nearest, speed);
                    }
                }, delay);
            }
        }

        world.spawnParticle(Particle.ANGRY_VILLAGER,
                villager.getLocation().add(0, 1.2, 0), 5, 0.4, 0.4, 0.4, 0.0);
        world.playSound(villager.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.1f);
    }

    public boolean isPanicking(Villager villager) {
        long until = panicUntil.getOrDefault(villager.getUniqueId(), 0L);
        if (until <= System.currentTimeMillis()) {
            panicUntil.remove(villager.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean isCityResident(Entity entity) {
        return entity instanceof Villager
                && entity.getPersistentDataContainer().has(residentKey, PersistentDataType.INTEGER);
    }

    public boolean isCityGolem(Entity entity) {
        return entity instanceof IronGolem
                && entity.getPersistentDataContainer().has(golemKey, PersistentDataType.INTEGER);
    }

    public String getProfileId(Villager villager) {
        return villager.getPersistentDataContainer().get(profileKey, PersistentDataType.STRING);
    }

    public CityProfession getProfile(Villager villager) {
        return CityProfession.fromId(getProfileId(villager));
    }

    public List<Villager> getCityVillagers(World world) {
        List<Villager> villagers = new ArrayList<>();
        for (org.bukkit.entity.LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Villager villager && isCityResident(villager)) {
                villagers.add(villager);
            }
        }
        return villagers;
    }

    public List<IronGolem> getCityGolems(World world) {
        List<IronGolem> golems = new ArrayList<>();
        for (org.bukkit.entity.LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof IronGolem golem && isCityGolem(golem)) {
                golems.add(golem);
            }
        }
        return golems;
    }

    public long getLastEconomyDay(World world) {
        return state.getLong(worldPath(world) + ".last-economy-day", Long.MIN_VALUE);
    }

    public void setLastEconomyDay(World world, long day) {
        state.set(worldPath(world) + ".last-economy-day", day);
        saveState();
    }

    public String status(World world) {
        Location center = getTownCenter(world);
        if (center == null) {
            return "No EaglerCity town has been generated in " + world.getName() + ".";
        }
        return "EaglerCity @ "
                + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ()
                + " | residents=" + getCityVillagers(world).size()
                + " | guards=" + getCityGolems(world).size();
    }

    private Location chooseTownSite(World world) {
        Location spawn = world.getSpawnLocation();
        int distance = Math.max(48,
                plugin.getConfig().getInt("town.distance-from-spawn", 96));
        int candidates = Math.max(8,
                plugin.getConfig().getInt("town.search-candidates", 24));

        Location best = null;
        double bestScore = Double.MAX_VALUE;

        for (int i = 0; i < candidates; i++) {
            double angle = Math.PI * 2.0 * i / candidates;
            int radius = distance + ((i % 3) - 1) * 12;
            int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);

            double score = terrainScore(world, x, z);
            if (score < bestScore) {
                int y = GroundUtil.groundY(world, x, z) + 1;
                best = new Location(world, x + 0.5, y, z + 0.5);
                bestScore = score;
            }
        }

        if (best == null) {
            int x = spawn.getBlockX() + distance;
            int z = spawn.getBlockZ();
            int y = GroundUtil.groundY(world, x, z) + 1;
            best = new Location(world, x + 0.5, y, z + 0.5);
        }

        plugin.getLogger().info("Selected EaglerCity site with terrain score "
                + String.format("%.2f", bestScore) + ".");
        return best;
    }

    private double terrainScore(World world, int cx, int cz) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int waterPenalty = 0;
        int solidSamples = 0;

        for (int dx = -22; dx <= 22; dx += 11) {
            for (int dz = -22; dz <= 22; dz += 11) {
                int x = cx + dx;
                int z = cz + dz;
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                Material top = world.getBlockAt(x, y, z).getType();

                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);

                if (top == Material.WATER || top == Material.LAVA
                        || top == Material.KELP || top == Material.SEAGRASS) {
                    waterPenalty += 12;
                } else {
                    solidSamples++;
                }
            }
        }

        if (solidSamples < 18) {
            waterPenalty += 100;
        }
        return (maxY - minY) * 4.0 + waterPenalty;
    }

    private Location loadCenter(World world) {
        String path = worldPath(world);
        if (!state.getBoolean(path + ".generated", false)) {
            return null;
        }
        if (!state.contains(path + ".center-x") || !state.contains(path + ".center-z")) {
            return null;
        }
        return new Location(
                world,
                state.getDouble(path + ".center-x"),
                state.getDouble(path + ".center-y"),
                state.getDouble(path + ".center-z")
        );
    }

    private void saveCenter(World world, Location center) {
        String path = worldPath(world);
        state.set(path + ".generated", true);
        state.set(path + ".center-x", center.getX());
        state.set(path + ".center-y", center.getY());
        state.set(path + ".center-z", center.getZ());
        saveState();
    }

    private String worldPath(World world) {
        return "worlds." + world.getUID();
    }

    private void saveState() {
        try {
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save EaglerCity state.yml: " + e.getMessage());
        }
    }
}
