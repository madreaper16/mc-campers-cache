package com.madreaper16.camperscache;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LootGameTests {
    private static ResourceKey<LootTable> key(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, CampersCache.id("chests/" + name));
    }

    private static int count(
            List<ItemStack> items, java.util.function.Predicate<ItemStack> predicate) {
        return items.stream().filter(predicate).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean gear(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("diamond_");
    }

    private static boolean god(ItemStack stack) {
        return stack.is(Items.DIAMOND_CHESTPLATE)
                && stack.getItemName().getString().equals("God Armor");
    }

    private static void assertRewards(GameTestHelper test, List<ItemStack> items, boolean strong) {
        int pieces = count(items, s -> gear(s) && !god(s));
        test.assertTrue(
                pieces >= (strong ? 3 : 1) && pieces <= (strong ? 4 : 2),
                "Guaranteed diamond gear count for " + (strong ? "storehouse" : "shack"));
        test.assertTrue(
                count(items, s -> s.is(Items.ENCHANTED_BOOK)) >= (strong ? 2 : 1),
                "Useful enchanted books are guaranteed");
        var allowed =
                Set.of(
                        "protection",
                        "unbreaking",
                        "efficiency",
                        "sharpness",
                        "fortune",
                        "looting",
                        "feather_falling",
                        "power",
                        "mending");
        var enchantments = test.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (var stack : items) {
            for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet())
                test.assertTrue(
                        allowed.contains(
                                entry.getKey().unwrapKey().orElseThrow().identifier().getPath()),
                        "Cache enchantments are useful and curse-free");
            if (gear(stack)) {
                test.assertTrue(
                        EnchantmentHelper.getItemEnchantmentLevel(
                                        enchantments.getOrThrow(Enchantments.UNBREAKING), stack)
                                == (strong ? 3 : 2),
                        "Gear has tier-appropriate Unbreaking");
                if (god(stack)) {
                    test.assertTrue(
                            EnchantmentHelper.getItemEnchantmentLevel(
                                            enchantments.getOrThrow(Enchantments.PROTECTION), stack)
                                    == 5,
                            "God Armor preserves over-level Protection V");
                    test.assertTrue(
                            EnchantmentHelper.getItemEnchantmentLevel(
                                            enchantments.getOrThrow(Enchantments.MENDING), stack)
                                    == 1,
                            "God Armor includes Mending");
                }
            }
        }
    }

    @GameTest(maxTicks = 200)
    public void actualLootTablesGuaranteeRewardsAndRareOdds(GameTestHelper test) {
        var level = test.getLevel();
        var params =
                new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                        .create(LootContextParamSets.CHEST);
        var registry = level.getServer().reloadableRegistries();
        var shack = registry.getLootTable(key("supply_shack"));
        var store = registry.getLootTable(key("hidden_storehouse"));
        var supplies = registry.getLootTable(key("storehouse_supplies"));
        test.assertTrue(
                shack != LootTable.EMPTY && store != LootTable.EMPTY && supplies != LootTable.EMPTY,
                "All loot tables decode and load");
        int gods = 0, scraps = 0;
        var random = RandomSource.create(0xCA5E2026L);
        for (int i = 0; i < 20000; i++) {
            var loot = store.getRandomItems(params, random);
            // Check every rare drop and a sample of normal rewards.
            if (i < 500 || loot.stream().anyMatch(LootGameTests::god))
                assertRewards(test, loot, true);
            int godCount = count(loot, LootGameTests::god);
            test.assertTrue(godCount <= 1, "At most one God Armor per storehouse");
            gods += godCount;
            int scrapCount = count(loot, s -> s.is(Items.NETHERITE_SCRAP));
            test.assertTrue(scrapCount <= 2, "Scrap reward is at most two pieces");
            if (scrapCount > 0) scraps++;
            test.assertTrue(
                    loot.size() <= 27,
                    "Guaranteed rewards and bonuses fit in the primary chest half");
            if (i < 500) {
                assertRewards(test, shack.getRandomItems(params, random), false);
                var extra = supplies.getRandomItems(params, random);
                test.assertTrue(
                        extra.stream().noneMatch(s -> gear(s) || s.is(Items.NETHERITE_SCRAP)),
                        "Second chest half cannot duplicate rare rolls or gear");
            }
        }
        test.assertTrue(
                gods > 200 && gods < 400,
                "God Armor is approximately 1.5% across 20,000 actual rolls: " + gods);
        test.assertTrue(scraps > 4500 && scraps < 5500, "Scraps are approximately 25%: " + scraps);
        System.out.println("Cache loot: God Armor=" + gods + "/20000, scraps=" + scraps + "/20000");
        test.succeed();
    }

    @GameTest(maxTicks = 100)
    public void authoredChestNbtGeneratesLootOnlyOnce(GameTestHelper test) {
        var level = test.getLevel();
        var origin = test.absolutePos(new BlockPos(20, 4, 20));
        for (String site : List.of("supply_shack", "hidden_storehouse")) {
            var template = level.getStructureTemplateManager().getOrCreate(CampersCache.id(site));
            test.assertTrue(
                    template.placeInWorld(
                            level,
                            origin,
                            origin,
                            new StructurePlaceSettings().setIgnoreEntities(true),
                            RandomSource.create(8),
                            2),
                    "Authored template places");
            var positions =
                    site.equals("supply_shack")
                            ? List.of(origin.offset(3, 2, 10))
                            : List.of(origin.offset(11, 5, 11), origin.offset(11, 5, 10));
            List<ItemStack> found = new ArrayList<>();
            for (var position : positions) {
                var chest = (ChestBlockEntity) level.getBlockEntity(position);
                String name =
                        site.equals("supply_shack")
                                ? site
                                : position.equals(origin.offset(11, 5, 10))
                                        ? "hidden_storehouse"
                                        : "storehouse_supplies";
                test.assertTrue(
                        chest != null && key(name).equals(chest.getLootTable()),
                        "Generated chest retains its correct loot table, including both double-chest halves");
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    var stack = chest.getItem(slot);
                    if (!stack.isEmpty()) found.add(stack.copy());
                }
                test.assertTrue(chest.getLootTable() == null, "Opening consumes the table once");
                chest.clearContent();
                test.assertTrue(chest.isEmpty(), "Emptying/reopening never rerolls loot");
            }
            assertRewards(test, found, site.equals("hidden_storehouse"));
            origin = origin.offset(24, 0, 0);
        }
        test.succeed();
    }
}
