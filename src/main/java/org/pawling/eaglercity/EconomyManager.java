package org.pawling.eaglercity;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class EconomyManager {
    private final JavaPlugin plugin;
    private final TownManager townManager;
    private final Map<UUID, Long> tradeCooldownUntil = new HashMap<>();
    private final Random random = new Random();

    public EconomyManager(JavaPlugin plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
    }

    public void heartbeat() {
        for (World world : plugin.getServer().getWorlds()) {
            if (!townManager.isEnabledWorld(world) || townManager.getTownCenter(world) == null) {
                continue;
            }

            List<Villager> residents = townManager.getCityVillagers(world);
            if (residents.isEmpty()) {
                continue;
            }

            enforceProfessions(residents);
            processDailyCycleIfNeeded(world, residents);

            long time = world.getTime();
            long start = plugin.getConfig().getLong("economy.trade-start-time", 8500L);
            long end = plugin.getConfig().getLong("economy.trade-end-time", 12000L);

            if (time >= start && time <= end) {
                runTradingRound(world, residents);
            }
        }
    }

    private void enforceProfessions(List<Villager> residents) {
        for (Villager villager : residents) {
            CityProfession profile = townManager.getProfile(villager);
            if (profile != null && !profile.profession().equals(villager.getProfession())) {
                villager.setProfession(profile.profession());
            }
        }
    }

    private void processDailyCycleIfNeeded(World world, List<Villager> residents) {
        long day = world.getFullTime() / 24000L;
        long lastProcessed = townManager.getLastEconomyDay(world);

        // First startup establishes a baseline instead of granting restart loot.
        if (lastProcessed == Long.MIN_VALUE) {
            townManager.setLastEconomyDay(world, day);
            return;
        }
        if (day <= lastProcessed) {
            return;
        }

        boolean stockEnabled = plugin.getConfig().getBoolean("economy.daily-stock-enabled", true);
        boolean productionEnabled = plugin.getConfig().getBoolean("economy.daily-production-enabled", true);

        for (Villager villager : residents) {
            CityProfession profile = townManager.getProfile(villager);
            if (profile == null) {
                continue;
            }

            // Everyone consumes a little food. This is intentionally small, but it
            // creates a real sink so the economy does not only inflate forever.
            removeMaterial(villager.getInventory(), Material.BREAD, 1);

            if (stockEnabled) {
                for (Map.Entry<Material, Integer> entry : profile.dailyStock().entrySet()) {
                    int target = profile.targets().getOrDefault(entry.getKey(), entry.getValue() * 4);
                    addUpToCap(villager.getInventory(), entry.getKey(), entry.getValue(),
                            Math.max(target, (int) Math.ceil(target * 1.5)));
                }
            }

            if (productionEnabled && hasInputs(villager.getInventory(), profile.consumes())) {
                for (Map.Entry<Material, Integer> input : profile.consumes().entrySet()) {
                    removeMaterial(villager.getInventory(), input.getKey(), input.getValue());
                }
                for (Map.Entry<Material, Integer> output : profile.produces().entrySet()) {
                    int target = profile.targets().getOrDefault(output.getKey(), output.getValue() * 3);
                    addUpToCap(villager.getInventory(), output.getKey(), output.getValue(),
                            Math.max(target, (int) Math.ceil(target * 1.5)));
                }
            }
        }

        townManager.setLastEconomyDay(world, day);
        plugin.getLogger().info("EaglerCity completed daily economy cycle " + day
                + " in " + world.getName() + ".");
    }

    private void runTradingRound(World world, List<Villager> residents) {
        double radius = Math.max(3.0,
                plugin.getConfig().getDouble("economy.trade-radius", 10.0));
        int maxTransfer = Math.max(1,
                plugin.getConfig().getInt("economy.max-transfer-per-item", 8));
        long cooldownMs = Math.max(1000L,
                plugin.getConfig().getLong("economy.trade-cooldown-ticks", 400L) * 50L);

        List<Villager> shuffled = new ArrayList<>(residents);
        Collections.shuffle(shuffled, random);
        Set<UUID> paired = new HashSet<>();

        for (Villager first : shuffled) {
            if (!eligible(first, paired)) {
                continue;
            }

            CityProfession firstProfile = townManager.getProfile(first);
            if (firstProfile == null) {
                continue;
            }

            Villager bestPartner = null;
            TradePlan bestPlan = null;
            int bestScore = 0;

            for (Villager second : shuffled) {
                if (second == first || !eligible(second, paired)) {
                    continue;
                }
                if (!second.getWorld().equals(first.getWorld())
                        || second.getLocation().distanceSquared(first.getLocation()) > radius * radius) {
                    continue;
                }

                CityProfession secondProfile = townManager.getProfile(second);
                if (secondProfile == null || firstProfile == secondProfile) {
                    continue;
                }

                TradePlan plan = planTrade(first, firstProfile, second, secondProfile, maxTransfer);
                if (plan == null) {
                    continue;
                }

                int score = plan.totalItems();
                // Bilateral exchanges get a large preference because they read as
                // a true handshake rather than charity.
                if (plan.firstToSecond() != null && plan.secondToFirst() != null) {
                    score += 20;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestPartner = second;
                    bestPlan = plan;
                }
            }

            if (bestPartner == null || bestPlan == null) {
                continue;
            }

            int moved = executeTrade(first, bestPartner, bestPlan);
            if (moved <= 0) {
                continue;
            }

            paired.add(first.getUniqueId());
            paired.add(bestPartner.getUniqueId());
            long until = System.currentTimeMillis() + cooldownMs;
            tradeCooldownUntil.put(first.getUniqueId(), until);
            tradeCooldownUntil.put(bestPartner.getUniqueId(), until);

            first.lookAt(bestPartner.getLocation());
            bestPartner.lookAt(first.getLocation());

            org.bukkit.Location midpoint = first.getLocation().clone()
                    .add(bestPartner.getLocation()).multiply(0.5).add(0, 1.2, 0);
            world.spawnParticle(Particle.HAPPY_VILLAGER, midpoint,
                    7, 0.5, 0.35, 0.5, 0.0);
            world.playSound(midpoint, Sound.ENTITY_VILLAGER_YES, 0.75f, 1.15f);
        }
    }

    private boolean eligible(Villager villager, Set<UUID> paired) {
        if (!villager.isValid() || villager.isDead() || paired.contains(villager.getUniqueId())) {
            return false;
        }
        if (villager.isSleeping() || villager.isTrading() || townManager.isPanicking(villager)) {
            return false;
        }
        return tradeCooldownUntil.getOrDefault(villager.getUniqueId(), 0L)
                <= System.currentTimeMillis();
    }

    private TradePlan planTrade(
            Villager first,
            CityProfession firstProfile,
            Villager second,
            CityProfession secondProfile,
            int maxTransfer
    ) {
        Transfer firstToSecond = findBestTransfer(
                first.getInventory(), firstProfile,
                second.getInventory(), secondProfile,
                maxTransfer
        );
        Transfer secondToFirst = findBestTransfer(
                second.getInventory(), secondProfile,
                first.getInventory(), firstProfile,
                maxTransfer
        );

        if (firstToSecond == null && secondToFirst == null) {
            return null;
        }
        return new TradePlan(firstToSecond, secondToFirst);
    }

    private Transfer findBestTransfer(
            Inventory donorInventory,
            CityProfession donorProfile,
            Inventory receiverInventory,
            CityProfession receiverProfile,
            int maxTransfer
    ) {
        Transfer best = null;

        for (ItemStack stack : donorInventory.getContents()) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }

            Material material = stack.getType();
            int receiverTarget = receiverProfile.targets().getOrDefault(material, 0);
            if (receiverTarget <= 0) {
                continue;
            }

            int donorCount = count(donorInventory, material);
            int receiverCount = count(receiverInventory, material);
            int donorTarget = donorProfile.targets().getOrDefault(material, 0);

            int excess = donorCount - donorTarget;
            int need = receiverTarget - receiverCount;
            if (excess <= 0 || need <= 0) {
                continue;
            }

            int amount = Math.min(maxTransfer, Math.min(excess, need));
            if (amount <= 0) {
                continue;
            }

            if (best == null || amount > best.amount()) {
                best = new Transfer(material, amount);
            }
        }

        return best;
    }

    private int executeTrade(Villager first, Villager second, TradePlan plan) {
        int moved = 0;
        if (plan.firstToSecond() != null) {
            moved += transfer(first.getInventory(), second.getInventory(), plan.firstToSecond());
        }
        if (plan.secondToFirst() != null) {
            moved += transfer(second.getInventory(), first.getInventory(), plan.secondToFirst());
        }
        return moved;
    }

    private int transfer(Inventory donor, Inventory receiver, Transfer transfer) {
        int available = count(donor, transfer.material());
        int requested = Math.min(available, transfer.amount());
        if (requested <= 0) {
            return 0;
        }

        Map<Integer, ItemStack> leftovers =
                receiver.addItem(new ItemStack(transfer.material(), requested));
        int leftoverCount = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        int accepted = requested - leftoverCount;
        if (accepted > 0) {
            removeMaterial(donor, transfer.material(), accepted);
        }
        return accepted;
    }

    private boolean hasInputs(Inventory inventory, Map<Material, Integer> requirements) {
        for (Map.Entry<Material, Integer> entry : requirements.entrySet()) {
            if (count(inventory, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void addUpToCap(Inventory inventory, Material material, int amount, int cap) {
        int current = count(inventory, material);
        int toAdd = Math.min(amount, Math.max(0, cap - current));
        if (toAdd > 0) {
            inventory.addItem(new ItemStack(material, toAdd));
        }
    }

    private int removeMaterial(Inventory inventory, Material material, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() != material) {
                continue;
            }

            int take = Math.min(stack.getAmount(), remaining);
            int newAmount = stack.getAmount() - take;
            remaining -= take;

            if (newAmount <= 0) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(newAmount);
                inventory.setItem(slot, stack);
            }
        }
        return amount - remaining;
    }

    private int count(Inventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private record Transfer(Material material, int amount) {
    }

    private record TradePlan(Transfer firstToSecond, Transfer secondToFirst) {
        int totalItems() {
            int total = 0;
            if (firstToSecond != null) {
                total += firstToSecond.amount();
            }
            if (secondToFirst != null) {
                total += secondToFirst.amount();
            }
            return total;
        }
    }
}
