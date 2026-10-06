package org.pawling.eaglercity;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.List;
import java.util.Locale;

public final class EaglerCityPlugin extends JavaPlugin implements Listener, TabExecutor {
    private TownManager townManager;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        NamespacedKey residentKey = new NamespacedKey(this, "city_resident");
        NamespacedKey golemKey = new NamespacedKey(this, "city_guard");
        NamespacedKey profileKey = new NamespacedKey(this, "city_profession");

        townManager = new TownManager(this, residentKey, golemKey, profileKey);
        economyManager = new EconomyManager(this, townManager);

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("eaglercity") != null) {
            getCommand("eaglercity").setExecutor(this);
            getCommand("eaglercity").setTabCompleter(this);
        }

        getServer().getScheduler().runTask(this, townManager::initialize);

        long heartbeat = Math.max(40L,
                getConfig().getLong("economy.heartbeat-ticks", 100L));
        getServer().getScheduler().runTaskTimer(
                this, economyManager::heartbeat, heartbeat, heartbeat);

        long securityInterval = Math.max(200L,
                getConfig().getLong("security.check-interval-ticks", 600L));
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (World world : getServer().getWorlds()) {
                if (townManager.isEnabledWorld(world)
                        && townManager.getTownCenter(world) != null) {
                    townManager.ensureSecurity(world);
                }
            }
        }, securityInterval, securityInterval);

        getLogger().info("EaglerCity 1.0.0 enabled: town economy and security online.");
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        if (townManager.isEnabledWorld(event.getWorld())) {
            getServer().getScheduler().runTask(
                    this, () -> townManager.ensureTown(event.getWorld()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onResidentDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !townManager.isCityResident(villager)) {
            return;
        }

        // Vanilla villagers do not expose their carried harvest/work inventory as
        // ordinary loot. EaglerCity makes that inventory physical and recoverable.
        for (ItemStack item : villager.getInventory().getContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                event.getDrops().add(item.clone());
            }
        }
        villager.getInventory().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuardDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof IronGolem golem
                && townManager.isCityGolem(golem)) {
            double multiplier = Math.max(1.0,
                    getConfig().getDouble("security.golem-damage-multiplier", 5.0));
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResidentAttacked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !townManager.isCityResident(villager)) {
            return;
        }

        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker != null) {
            townManager.alertSecurity(villager, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResidentCareerChange(VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();
        if (!townManager.isCityResident(villager)) {
            return;
        }

        CityProfession profile = townManager.getProfile(villager);
        if (profile != null && !profile.profession().equals(event.getProfession())) {
            event.setProfession(profile.profession());
        }
    }

    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!sender.hasPermission("eaglercity.admin")) {
            sender.sendMessage("You do not have permission to manage EaglerCity.");
            return true;
        }

        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        World world = resolveWorld(sender);
        if (world == null) {
            sender.sendMessage("No enabled Overworld is currently loaded.");
            return true;
        }

        switch (sub) {
            case "status" -> sender.sendMessage(townManager.status(world));
            case "generate" -> {
                townManager.ensureTown(world);
                sender.sendMessage("EaglerCity town is ready in " + world.getName() + ".");
            }
            case "repopulate" -> {
                townManager.repopulate(world);
                townManager.ensureSecurity(world);
                sender.sendMessage("EaglerCity restored missing profession residents.");
            }
            case "security" -> {
                townManager.ensureSecurity(world);
                sender.sendMessage("EaglerCity security quota checked. "
                        + townManager.status(world));
            }
            default -> sender.sendMessage(
                    "Usage: /" + label + " [status|generate|repopulate|security]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("status", "generate", "repopulate", "security").stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private World resolveWorld(CommandSender sender) {
        if (sender instanceof Player player && townManager.isEnabledWorld(player.getWorld())) {
            return player.getWorld();
        }
        return getServer().getWorlds().stream()
                .filter(townManager::isEnabledWorld)
                .findFirst()
                .orElse(null);
    }
}
