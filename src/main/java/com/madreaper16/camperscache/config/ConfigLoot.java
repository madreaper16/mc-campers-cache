package com.madreaper16.camperscache.config;

import com.madreaper16.camperscache.CampersCache;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;

import java.util.*;

public final class ConfigLoot {
    private static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    public static List<ItemStack> roll(String name, ServerLevel level, RandomSource random) {
        var config = SettingsStore.active();
        var loot = config.loot.get(name);
        List<ItemStack> result = new ArrayList<>();
        if (!config.enabled || loot == null || !loot.enabled) return result;
        var enchants = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var pool : loot.pools) {
            if (!pool.enabled || random.nextDouble() >= pool.chance) continue;
            var entries = pool.entries.stream().filter(e -> e.enabled).toList();
            int total = entries.stream().mapToInt(e -> e.weight).sum();
            if (total == 0) continue;
            int rolls = between(random, pool.min, pool.max);
            for (int n = 0; n < rolls; n++) {
                int pick = random.nextInt(total);
                Settings.Entry chosen = entries.getLast();
                for (var entry : entries) {
                    pick -= entry.weight;
                    if (pick < 0) {
                        chosen = entry;
                        break;
                    }
                }
                // Pick by weight, then check the item chance.
                if (random.nextDouble() >= chosen.chance) continue;
                ItemStack stack =
                        new ItemStack(
                                BuiltInRegistries.ITEM.getValue(Identifier.parse(chosen.item)),
                                between(random, chosen.min, chosen.max));
                for (var e : chosen.enchantments) {
                    if (random.nextDouble() >= e.chance) continue;
                    var holder =
                            enchants.getOrThrow(
                                    ResourceKey.create(
                                            Registries.ENCHANTMENT, Identifier.parse(e.id)));
                    if (!stack.is(Items.BOOK)
                            && !stack.is(Items.ENCHANTED_BOOK)
                            && !holder.value().canEnchant(stack)) continue;
                    boolean compatible =
                            EnchantmentHelper.getEnchantmentsForCrafting(stack).keySet().stream()
                                    .allMatch(old -> Enchantment.areCompatible(old, holder));
                    if (!compatible) continue;
                    if (stack.is(Items.BOOK)) stack = stack.transmuteCopy(Items.ENCHANTED_BOOK);
                    stack.enchant(holder, between(random, e.min, e.max));
                }
                while (stack.getCount() > stack.getMaxStackSize())
                    result.add(stack.split(stack.getMaxStackSize()));
                if (!stack.isEmpty()) result.add(stack);
            }
        }
        if (name.equals("hidden_storehouse")
                && config.godArmorEnabled
                && random.nextDouble() < config.godArmorChance) {
            ItemStack god = new ItemStack(Items.DIAMOND_CHESTPLATE);
            god.enchant(enchants.getOrThrow(Enchantments.UNBREAKING), 3);
            god.enchant(enchants.getOrThrow(Enchantments.MENDING), 1);
            god.enchant(enchants.getOrThrow(Enchantments.PROTECTION), 5);
            god.set(
                    DataComponents.ITEM_NAME,
                    Component.literal("God Armor").withStyle(net.minecraft.ChatFormatting.GOLD));
            result.add(god);
        }
        return result;
    }

    public static void fill(String name, Container container, ServerLevel level, long seed) {
        RandomSource random = seed == 0 ? RandomSource.create() : RandomSource.create(seed);
        var loot = roll(name, level, random);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++)
            if (container.getItem(i).isEmpty()) slots.add(i);
        // Shuffle empty slots without replacing existing items.
        for (int i = slots.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(slots, i, j);
        }
        if (loot.size() > slots.size())
            CampersCache.LOGGER.warn(
                    "Not enough empty slots for {} loot; existing contents preserved", name);
        for (int i = 0; i < Math.min(loot.size(), slots.size()); i++)
            container.setItem(slots.get(i), loot.get(i));
        container.setChanged();
    }
}
