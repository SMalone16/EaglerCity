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
import java.util.Comparator;
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
    private final Map<UUID, Long> workCooldownUntil = new HashMap<>();
    private final Random random = new Random();

    public EconomyManager(JavaPlugin plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
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

            enforceProfessions(residents);
            processDailyCycleIfNeeded(world, residents);

            long time = world.getTime();
            long start = plugin.getConfig()
                    .getLong("economy.trade-start-time", 8500L);
            long end = plugin.getConfig()
                    .getLong("economy.trade-end-time", 12000L);

            if (time >= start && time <= end) {
                runTradingRound(world, residents);
            }
        }
    }

    public boolean needsWork(Villager villager) {
        CityProfession profile = townManager.getProfile(villager);
        if (profile == null) {
            return false;
        }

        double threshold = Math.max(0.1, Math.min(1.0,
                plugin.getConfig().getDouble("activity.low-stock-ratio", 0.65)));

        for (Map.Entry<Material, Integer> entry : profile.targets().entrySet()) {
            int target = Math.max(1, entry.getValue());
            int current = count(villager.getInventory(), entry.getKey());
            if (current < Math.ceil(target * threshold)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called by the activity controller after a villager physically reaches its
     * workstation. This is intentionally target-capped: villagers cannot stand
     * at work forever and manufacture unlimited loot.
     */
    public boolean performWorkVisit(Villager villager) {
        if (!villager.isValid() || villager.isDead()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (workCooldownUntil.getOrDefault(villager.getUniqueId(), 0L) > now) {
            return false;
        }

        CityProfession profile = townManager.getProfile(villager);
        if (profile == null) {
            return false;
        }

        Inventory inventory = villager.getInventory();
        boolean changed = false;

        // Prefer a real recipe-like production cycle when inputs are available
        // and at least one produced good is currently below its target.
        boolean outputNeeded = profile.produces().entrySet().stream()
                .anyMatch(entry -> count(inventory, entry.getKey())
                        < profile.targets().getOrDefault(
                                entry.getKey(), entry.getValue() * 3));

        if (outputNeeded && hasInputs(inventory, profile.consumes())) {
            for (Map.Entry<Material, Integer> input : profile.consumes().entrySet()) {
                removeMaterial(inventory, input.getKey(), input.getValue());
            }
            for (Map.Entry<Material, Integer> output : profile.produces().entrySet()) {
                int target = profile.targets()
                        .getOrDefault(output.getKey(), output.getValue() * 3);
                int before = count(inventory, output.getKey());
                addUpToCap(
                        inventory,
                        output.getKey(),
                        output.getValue(),
                        target
                );
                changed |= count(inventory, output.getKey()) > before;
            }
        }

        // Workplaces also supply a small amount of profession base stock when
        // the villager is actually deficient. This represents gathering tools,
        // raw material, deliveries, or restocking rather than free daily loot.
        List<Map.Entry<Material, Integer>> baseNeeds =
                new ArrayList<>(profile.dailyStock().entrySet());
        baseNeeds.sort(Comparator.comparingDouble(entry ->
                stockRatio(inventory, profile, entry.getKey())));

        int suppliedKinds = 0;
        for (Map.Entry<Material, Integer> entry : baseNeeds) {
            int target = profile.targets()
                    .getOrDefault(entry.getKey(), entry.getValue() * 4);
            int current = count(inventory, entry.getKey());
            if (current >= target) {
                continue;
            }

            int amount = Math.min(
                    Math.max(1, entry.getValue()),
                    target - current
            );
            if (amount <= 0) {
                continue;
            }

            inventory.addItem(new ItemStack(entry.getKey(), amount));
            changed = true;
            suppliedKinds++;
            if (suppliedKinds >= 2) {
                break;
            }
        }

        long cooldownTicks = Math.max(100L,
                plugin.getConfig().getLong("activity.work-cooldown-ticks", 300L));
        workCooldownUntil.put(
                villager.getUniqueId(),
                now + cooldownTicks * 50L
        );

        if (changed) {
            villager.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    villager.getLocation().add(0, 1.1, 0),
                    4,
                    0.3, 0.35, 0.3,
                    0.0
            );
            villager.getWorld().playSound(
                    villager.getLocation(),
                    Sound.BLOCK_BARREL_OPEN,
                    0.45f,
                    1.25f
            );
        }

        return changed;
    }

    private double stockRatio(
            Inventory inventory,
            CityProfession profile,
            Material material
    ) {
        int target = Math.max(1, profile.targets().getOrDefault(material, 1));
        return count(inventory, material) / (double) target;
    }

    private void enforceProfessions(List<Villager> residents) {
        for (Villager villager : residents) {
            villager.setAI(true);
            CityProfession profile = townManager.getProfile(villager);
            if (profile != null
                    && !profile.profession().equals(villager.getProfession())) {
                villager.setProfession(profile.profession());
            }
        }
    }

    private void processDailyCycleIfNeeded(
            World world,
            List<Villager> residents
    ) {
        long day = world.getFullTime() / 24000L;
        long lastProcessed = townManager.getLastEconomyDay(world);

        if (lastProcessed == Long.MIN_VALUE) {
            townManager.setLastEconomyDay(world, day);
            return;
        }
        if (day <= lastProcessed) {
            return;
        }

        boolean stockEnabled = plugin.getConfig()
                .getBoolean("economy.daily-stock-enabled", true);
        boolean productionEnabled = plugin.getConfig()
                .getBoolean("economy.daily-production-enabled", true);

        for (Villager villager : residents) {
            CityProfession profile = townManager.getProfile(villager);
            if (profile == null) {
                continue;
            }

            removeMaterial(villager.getInventory(), Material.BREAD, 1);

            if (stockEnabled) {
                for (Map.Entry<Material, Integer> entry
                        : profile.dailyStock().entrySet()) {
                    int target = profile.targets().getOrDefault(
                            entry.getKey(), entry.getValue() * 4);
                    addUpToCap(
                            villager.getInventory(),
                            entry.getKey(),
                            entry.getValue(),
                            Math.max(target, (int) Math.ceil(target * 1.25))
                    );
                }
            }

            if (productionEnabled
                    && hasInputs(villager.getInventory(), profile.consumes())) {
                for (Map.Entry<Material, Integer> input
                        : profile.consumes().entrySet()) {
                    removeMaterial(
                            villager.getInventory(),
                            input.getKey(),
                            input.getValue()
                    );
                }
                for (Map.Entry<Material, Integer> output
                        : profile.produces().entrySet()) {
                    int target = profile.targets().getOrDefault(
                            output.getKey(), output.getValue() * 3);
                    addUpToCap(
                            villager.getInventory(),
                            output.getKey(),
                            output.getValue(),
                            Math.max(target, (int) Math.ceil(target * 1.25))
                    );
                }
            }
        }

        townManager.setLastEconomyDay(world, day);
        plugin.getLogger().info(
                "EaglerCity completed daily economy cycle "
                        + day + " in " + world.getName() + ".");
    }

    private void runTradingRound(World world, List<Villager> residents) {
        double radius = Math.max(3.0,
                plugin.getConfig().getDouble("economy.trade-radius", 10.0));
        int maxTransfer = Math.max(1,
                plugin.getConfig().getInt("economy.max-transfer-per-item", 8));
        long cooldownMs = Math.max(1000L,
                plugin.getConfig()
                        .getLong("economy.trade-cooldown-ticks", 400L) * 50L);

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
                        || second.getLocation().distanceSquared(
                                first.getLocation()) > radius * radius) {
                    continue;
                }

                CityProfession secondProfile = townManager.getProfile(second);
                if (secondProfile == null || firstProfile == secondProfile) {
                    continue;
                }

                TradePlan plan = planTrade(
                        first,
                        firstProfile,
                        second,
                        secondProfile,
                        maxTransfer
                );
                if (plan == null) {
                    continue;
                }

                int score = plan.totalItems();
                if (plan.firstToSecond() != null
                        && plan.secondToFirst() != null) {
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
                    .add(bestPartner.getLocation())
                    .multiply(0.5)
                    .add(0, 1.2, 0);
            world.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    midpoint,
                    7,
                    0.5, 0.35, 0.5,
                    0.0
            );
            world.playSound(
                    midpoint,
                    Sound.ENTITY_VILLAGER_YES,
                    0.75f,
                    1.15f
            );
        }
    }

    private boolean eligible(Villager villager, Set<UUID> paired) {
        if (!villager.isValid()
                || villager.isDead()
                || paired.contains(villager.getUniqueId())) {
            return false;
        }
        if (villager.isSleeping()
                || villager.isTrading()
                || townManager.isPanicking(villager)) {
            return false;
        }
        return tradeCooldownUntil.getOrDefault(
                villager.getUniqueId(), 0L) <= System.currentTimeMillis();
    }

    private TradePlan planTrade(
            Villager first,
            CityProfession firstProfile,
            Villager second,
            CityProfession secondProfile,
            int maxTransfer
    ) {
        Transfer firstToSecond = findBestTransfer(
                first.getInventory(),
                firstProfile,
                second.getInventory(),
                secondProfile,
                maxTransfer
        );
        Transfer secondToFirst = findBestTransfer(
                second.getInventory(),
                secondProfile,
                first.getInventory(),
                firstProfile,
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
            if (stack == null
                    || stack.getType().isAir()
                    || stack.getAmount() <= 0) {
                continue;
            }

            Material material = stack.getType();
            int receiverTarget =
                    receiverProfile.targets().getOrDefault(material, 0);
            if (receiverTarget <= 0) {
                continue;
            }

            int donorCount = count(donorInventory, material);
            int receiverCount = count(receiverInventory, material);
            int donorTarget = donorProfile.targets().getOrDefault(material, 0);
            double reserveRatio = Math.max(0.5, Math.min(1.0,
                    plugin.getConfig().getDouble(
                            "economy.trade-reserve-ratio", 0.85)));
            int donorReserve = donorTarget <= 0
                    ? 0
                    : (int) Math.ceil(donorTarget * reserveRatio);

            int excess = donorCount - donorReserve;
            int need = receiverTarget - receiverCount;
            if (excess <= 0 || need <= 0) {
                continue;
            }

            int amount = Math.min(
                    maxTransfer,
                    Math.min(excess, need)
            );
            if (amount <= 0) {
                continue;
            }

            if (best == null || amount > best.amount()) {
                best = new Transfer(material, amount);
            }
        }

        return best;
    }

    private int executeTrade(
            Villager first,
            Villager second,
            TradePlan plan
    ) {
        int moved = 0;
        if (plan.firstToSecond() != null) {
            moved += transfer(
                    first.getInventory(),
                    second.getInventory(),
                    plan.firstToSecond()
            );
        }
        if (plan.secondToFirst() != null) {
            moved += transfer(
                    second.getInventory(),
                    first.getInventory(),
                    plan.secondToFirst()
            );
        }
        return moved;
    }

    private int transfer(
            Inventory donor,
            Inventory receiver,
            Transfer transfer
    ) {
        int available = count(donor, transfer.material());
        int requested = Math.min(available, transfer.amount());
        if (requested <= 0) {
            return 0;
        }

        Map<Integer, ItemStack> leftovers =
                receiver.addItem(new ItemStack(
                        transfer.material(),
                        requested
                ));
        int leftoverCount = leftovers.values().stream()
                .mapToInt(ItemStack::getAmount)
                .sum();
        int accepted = requested - leftoverCount;
        if (accepted > 0) {
            removeMaterial(
                    donor,
                    transfer.material(),
                    accepted
            );
        }
        return accepted;
    }

    private boolean hasInputs(
            Inventory inventory,
            Map<Material, Integer> requirements
    ) {
        for (Map.Entry<Material, Integer> entry : requirements.entrySet()) {
            if (count(inventory, entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void addUpToCap(
            Inventory inventory,
            Material material,
            int amount,
            int cap
    ) {
        int current = count(inventory, material);
        int toAdd = Math.min(amount, Math.max(0, cap - current));
        if (toAdd > 0) {
            inventory.addItem(new ItemStack(material, toAdd));
        }
    }

    private int removeMaterial(
            Inventory inventory,
            Material material,
            int amount
    ) {
        int remaining = amount;
        for (int slot = 0;
                slot < inventory.getSize() && remaining > 0;
                slot++) {
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

    int count(Inventory inventory, Material material) {
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

    private record TradePlan(
            Transfer firstToSecond,
            Transfer secondToFirst
    ) {
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
