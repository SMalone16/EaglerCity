package org.pawling.eaglercity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Comparator;
import java.util.List;

/**
 * Gives EaglerCity residents a small explicit schedule on top of vanilla villager
 * AI. We refresh destinations every few seconds instead of replacing Minecraft's
 * brain or running pathfinding every tick.
 */
public final class ActivityManager {
    private final JavaPlugin plugin;
    private final TownManager townManager;
    private final EconomyManager economyManager;

    public ActivityManager(
            JavaPlugin plugin,
            TownManager townManager,
            EconomyManager economyManager
    ) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.economyManager = economyManager;
    }

    public void heartbeat() {
        for (World world : plugin.getServer().getWorlds()) {
            if (!townManager.isEnabledWorld(world)
                    || townManager.getTownCenter(world) == null) {
                continue;
            }

            List<Villager> residents = townManager.getCityVillagers(world);
            if (residents.isEmpty()) {
                continue;
            }

            for (Villager villager : residents) {
                tickResident(world, villager, residents);
            }
        }
    }

    private void tickResident(
            World world,
            Villager villager,
            List<Villager> residents
    ) {
        if (!villager.isValid() || villager.isDead()) {
            return;
        }

        villager.setAI(true);

        if (townManager.isPanicking(villager)
                || villager.isTrading()
                || villager.isSleeping()) {
            return;
        }

        long time = world.getTime();
        long workStart = plugin.getConfig()
                .getLong("activity.work-start-time", 1000L);
        long socialStart = plugin.getConfig()
                .getLong("economy.trade-start-time", 8500L);
        long socialEnd = plugin.getConfig()
                .getLong("economy.trade-end-time", 12000L);
        long homeStart = plugin.getConfig()
                .getLong("activity.home-start-time", 12500L);

        if (time < workStart || time >= homeStart) {
            goHome(villager);
            return;
        }

        if (time >= socialStart && time <= socialEnd) {
            socialize(villager, residents);
            return;
        }

        if (time >= workStart && time < socialStart
                && economyManager.needsWork(villager)) {
            goToWork(villager);
            return;
        }

        wander(villager);
    }

    private void goToWork(Villager villager) {
        Location destination = townManager.getWorkLocation(villager);
        double arrival = Math.max(1.5,
                plugin.getConfig()
                        .getDouble("activity.work-arrival-radius", 2.75));
        double distanceSquared =
                villager.getLocation().distanceSquared(destination);

        if (distanceSquared <= arrival * arrival) {
            economyManager.performWorkVisit(villager);
            return;
        }

        moveTo(
                villager,
                destination,
                plugin.getConfig()
                        .getDouble("activity.work-speed", 0.9)
        );
    }

    private void socialize(
            Villager villager,
            List<Villager> residents
    ) {
        Location plaza = townManager.getPlazaLocation(villager);

        // First bring everybody into the same walkable neighborhood. This is the
        // missing link in 1.0: the economy could trade nearby villagers, but the
        // villagers were never intentionally brought near one another.
        if (villager.getLocation().distanceSquared(plaza) > 20.25) {
            moveTo(
                    villager,
                    plaza,
                    plugin.getConfig()
                            .getDouble("activity.social-speed", 0.85)
            );
            return;
        }

        Villager partner = residents.stream()
                .filter(other -> other != villager)
                .filter(Villager::isValid)
                .filter(other -> !other.isDead())
                .filter(other -> !townManager.isPanicking(other))
                .filter(other -> other.getWorld().equals(villager.getWorld()))
                .min(Comparator.comparingDouble(other ->
                        other.getLocation().distanceSquared(
                                villager.getLocation())))
                .orElse(null);

        if (partner != null) {
            double distance =
                    partner.getLocation().distanceSquared(villager.getLocation());
            if (distance > 6.25 && distance < 100.0) {
                moveTo(
                        villager,
                        partner.getLocation(),
                        plugin.getConfig()
                                .getDouble("activity.social-speed", 0.85)
                );
                return;
            }
        }

        // Already clustered: gently circulate around the square rather than
        // repeatedly issuing paths to the exact same block.
        wander(villager);
    }

    private void goHome(Villager villager) {
        Location destination = townManager.getHomeLocation(villager);
        if (villager.getLocation().distanceSquared(destination) <= 6.25) {
            return;
        }

        moveTo(
                villager,
                destination,
                plugin.getConfig()
                        .getDouble("activity.home-speed", 0.82)
        );
    }

    private void wander(Villager villager) {
        long changeTicks = Math.max(100L,
                plugin.getConfig()
                        .getLong("activity.wander-change-ticks", 300L));
        long phase = villager.getWorld().getFullTime() / changeTicks;
        Location destination =
                townManager.getWanderLocation(villager, phase);

        if (villager.getLocation().distanceSquared(destination) <= 4.0) {
            return;
        }

        moveTo(
                villager,
                destination,
                plugin.getConfig()
                        .getDouble("activity.wander-speed", 0.72)
        );
    }

    private void moveTo(
            Villager villager,
            Location destination,
            double configuredSpeed
    ) {
        if (destination == null
                || destination.getWorld() == null
                || !destination.getWorld().equals(villager.getWorld())) {
            return;
        }

        double speed = Math.max(0.4, Math.min(1.5, configuredSpeed));
        villager.getPathfinder().moveTo(destination, speed);
    }
}
