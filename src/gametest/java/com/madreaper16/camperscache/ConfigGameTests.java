package com.madreaper16.camperscache;

import com.madreaper16.camperscache.config.*;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

import java.nio.file.*;
import java.util.*;

public final class ConfigGameTests {
    private static void use(Settings settings) throws Exception {
        SettingsStore.validate(settings);
        var field = SettingsStore.class.getDeclaredField("current");
        field.setAccessible(true);
        field.set(null, SettingsStore.copy(settings));
    }

    private static void rejects(GameTestHelper test, Settings config, String why) {
        boolean rejected = false;
        try {
            SettingsStore.validate(config);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        test.assertTrue(rejected, why);
    }

    @GameTest
    public void presetsValidateAndDraftsStayDetached(GameTestHelper test) {
        var preview = ItemCatalog.previewSizes();
        test.assertTrue(
                preview.get(net.minecraft.resources.Identifier.parse("minecraft:iron_ingot")) == 64
                        && preview.get(
                                        net.minecraft.resources.Identifier.parse(
                                                "minecraft:diamond_chestplate"))
                                == 1,
                "Main-menu preview resolves item capacities without binding components");
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM)
            test.assertTrue(
                    preview.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item))
                            == item.getDefaultMaxStackSize(),
                    "Declaration-time preview matches bound item capacity: "
                            + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item));
        for (String preset : SettingsStore.PRESETS)
            SettingsStore.validate(SettingsStore.preset(preset));
        var original = SettingsStore.snapshot();
        var draft = SettingsStore.snapshot();
        draft.camperRadius = 60;
        test.assertTrue(
                SettingsStore.active().camperRadius == original.camperRadius,
                "Editing a draft does not publish it");
        draft.camperRadius = 61;
        rejects(test, draft, "Radius cannot exceed 60");
        draft.camperRadius = 19;
        rejects(test, draft, "Radius cannot fall below 20");
        draft.camperRadius = 20;
        var enchant =
                draft.loot
                        .get("hidden_storehouse")
                        .pools
                        .getFirst()
                        .entries
                        .get(1)
                        .enchantments
                        .getFirst();
        enchant.max = 5;
        rejects(test, draft, "Normal Protection V is rejected");
        draft = SettingsStore.snapshot();
        draft.loot.get("camp").pools.getFirst().entries.getFirst().item = "minecraft:made_up_item";
        rejects(test, draft, "Unknown item IDs are rejected");
        draft = SettingsStore.snapshot();
        draft.loot.get("camp").pools.getFirst().max = 27;
        rejects(test, draft, "Chest overflow is rejected");
        draft = SettingsStore.snapshot();
        draft.familyChance = Double.NaN;
        rejects(test, draft, "NaN chances are rejected");
        var scarce = SettingsStore.preset("Scarce");
        var abundant = SettingsStore.preset("Abundant");
        test.assertTrue(
                scarce.regionChunks > abundant.regionChunks
                        && scarce.familyChance < abundant.familyChance,
                "Preset tiers change actual generation frequency");
        test.succeed();
    }

    @GameTest
    public void saveReloadProfilesAndLegacyMigration(GameTestHelper test) throws Exception {
        var original = SettingsStore.snapshot();
        byte[] file =
                Files.exists(SettingsStore.FILE) ? Files.readAllBytes(SettingsStore.FILE) : null;
        String profile = "test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            var draft = SettingsStore.snapshot();
            draft.camperRadius = 47;
            SettingsStore.save(draft);
            draft.camperRadius = 59;
            test.assertTrue(
                    SettingsStore.active().camperRadius == 47
                            && SettingsStore.active().preset.equals("Custom"),
                    "Save publishes a detached Custom snapshot");
            SettingsStore.load();
            test.assertTrue(
                    SettingsStore.active().camperRadius == 47, "Saved settings survive reload");
            SettingsStore.exportProfile(profile, SettingsStore.snapshot());
            var imported = SettingsStore.importProfile(profile);
            test.assertTrue(imported.camperRadius == 47, "Profiles round trip");
            imported.camperRadius = 52;
            test.assertTrue(
                    SettingsStore.active().camperRadius == 47,
                    "Imported draft is not applied before Save");
            Files.writeString(
                    SettingsStore.FILE,
                    "{\"storehouseOreWeights\":{\"iron\":1,\"coal\":2,\"copper\":3,\"diamond\":4,\"emerald\":5}}");
            SettingsStore.load();
            test.assertTrue(
                    SettingsStore.active().storehouseOreWeights.get("diamond") == 4,
                    "Legacy ore settings migrate without loss");
            Files.writeString(SettingsStore.FILE, "broken json");
            SettingsStore.load();
            test.assertTrue(
                    SettingsStore.active().storehouseOreWeights.get("diamond") == 4
                            && Files.readString(SettingsStore.FILE).equals("broken json"),
                    "Invalid files preserve the last valid configuration and original bytes");
            boolean escaped = false;
            try {
                SettingsStore.profilePath("../escape");
            } catch (IllegalArgumentException expected) {
                escaped = true;
            }
            test.assertTrue(escaped, "Profiles cannot escape their directory");
        } finally {
            use(original);
            if (file == null) Files.deleteIfExists(SettingsStore.FILE);
            else Files.write(SettingsStore.FILE, file);
            Files.deleteIfExists(SettingsStore.profilePath(profile));
        }
        test.succeed();
    }

    @GameTest
    public void unopenedChestsUseSavedCustomLootAndRespectDisabledPools(GameTestHelper test)
            throws Exception {
        var original = SettingsStore.snapshot();
        var level = test.getLevel();
        try {
            var draft = SettingsStore.snapshot();
            draft.godArmorEnabled = false;
            var loot = draft.loot.get("supply_shack");
            loot.pools.clear();
            var pool = new Settings.Pool();
            var item = new Settings.Entry();
            item.item = "minecraft:apple";
            item.min = 7;
            item.max = 7;
            pool.entries.add(item);
            loot.pools.add(pool);
            test.setBlock(1, 1, 1, Blocks.CHEST);
            var chest =
                    (ChestBlockEntity)
                            level.getBlockEntity(test.absolutePos(new BlockPos(1, 1, 1)));
            chest.setLootTable(
                    ResourceKey.create(
                            Registries.LOOT_TABLE, CampersCache.id("chests/supply_shack")));
            chest.setLootTableSeed(2);
            use(draft);
            test.assertTrue(
                    chest.countItem(Items.APPLE) == 7,
                    "Native chest first access uses the current configured item and amount");
            draft.loot.get("supply_shack").enabled = false;
            use(draft);
            test.assertTrue(
                    chest.countItem(Items.APPLE) == 7, "Already-opened chest is never rewritten");
            test.assertTrue(
                    ConfigLoot.roll("supply_shack", level, RandomSource.create(1)).isEmpty(),
                    "Disabled location produces no new loot");
            draft.loot.get("supply_shack").enabled = true;
            pool.enabled = false;
            use(draft);
            test.assertTrue(
                    ConfigLoot.roll("supply_shack", level, RandomSource.create(1)).isEmpty(),
                    "Disabled pool produces nothing");
            pool.enabled = true;
            item.item = "minecraft:book";
            item.min = item.max = 1;
            var enchant = new Settings.Enchant();
            enchant.id = "minecraft:mending";
            item.enchantments.add(enchant);
            use(draft);
            var book = ConfigLoot.roll("supply_shack", level, RandomSource.create(1)).getFirst();
            test.assertTrue(
                    book.is(Items.ENCHANTED_BOOK)
                            && EnchantmentHelper.getEnchantmentsForCrafting(book).keySet().size()
                                    == 1,
                    "Configured book enchantments use stored book components");
            enchant.chance = 0;
            use(draft);
            test.assertTrue(
                    ConfigLoot.roll("supply_shack", level, RandomSource.create(1))
                            .getFirst()
                            .is(Items.BOOK),
                    "Zero enchantment chance leaves a plain book");
            item.chance = 0;
            use(draft);
            test.assertTrue(
                    ConfigLoot.roll("supply_shack", level, RandomSource.create(1)).isEmpty(),
                    "Zero item chance creates an empty draw");
            draft.godArmorEnabled = true;
            draft.godArmorChance = 1;
            draft.loot.get("hidden_storehouse").pools.clear();
            use(draft);
            var god =
                    ConfigLoot.roll("hidden_storehouse", level, RandomSource.create(1)).getFirst();
            test.assertTrue(
                    EnchantmentHelper.getItemEnchantmentLevel(
                                    level.registryAccess()
                                            .lookupOrThrow(Registries.ENCHANTMENT)
                                            .getOrThrow(Enchantments.PROTECTION),
                                    god)
                            == 5,
                    "Dedicated Easter egg retains Protection V");
        } finally {
            use(original);
        }
        test.succeed();
    }

    @GameTest
    public void configuredTemplateGuardCountsAndCamperToggle(GameTestHelper test) throws Exception {
        var original = SettingsStore.snapshot();
        var level = test.getLevel();
        var origin = test.absolutePos(new BlockPos(90, 8, 90));
        try {
            var config = SettingsStore.snapshot();
            config.shackGuards = 3;
            use(config);
            var template =
                    level.getStructureTemplateManager()
                            .getOrCreate(CampersCache.id("supply_shack"));
            template.placeInWorld(
                    level, origin, origin, new StructurePlaceSettings(), RandomSource.create(4), 2);
            var bounds = new net.minecraft.world.phys.AABB(origin).inflate(18);
            test.assertTrue(
                    level.getEntitiesOfClass(
                                            net.minecraft.world.entity.monster.illager.Pillager
                                                    .class,
                                            bounds)
                                    .size()
                            == 3,
                    "Authored guard markers honor custom counts");
            config.guardsEnabled = false;
            config.camperEnabled = false;
            use(config);
            origin = origin.offset(50, 0, 0);
            template.placeInWorld(
                    level, origin, origin, new StructurePlaceSettings(), RandomSource.create(4), 2);
            test.assertTrue(
                    level.getEntitiesOfClass(
                                    net.minecraft.world.entity.monster.illager.Pillager.class,
                                    new net.minecraft.world.phys.AABB(origin).inflate(18))
                            .isEmpty(),
                    "Disabling guards prevents new guard spawns");
            origin = origin.offset(50, 0, 0);
            level.getStructureTemplateManager()
                    .getOrCreate(CampersCache.id("camp"))
                    .placeInWorld(
                            level,
                            origin,
                            origin,
                            new StructurePlaceSettings(),
                            RandomSource.create(4),
                            2);
            test.assertTrue(
                    level.getEntitiesOfClass(
                                    com.madreaper16.camperscache.entity.Camper.class,
                                    new net.minecraft.world.phys.AABB(origin).inflate(18))
                            .isEmpty(),
                    "Camper toggle prevents new Camper spawns");
        } finally {
            use(original);
        }
        test.succeed();
    }
}
