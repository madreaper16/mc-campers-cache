package com.madreaper16.camperscache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.madreaper16.camperscache.config.ModConfig;
import com.madreaper16.camperscache.data.CampLedger;
import com.madreaper16.camperscache.data.ChestHooks;
import com.madreaper16.camperscache.entity.Camper;
import com.madreaper16.camperscache.world.LayoutCommand;
import com.madreaper16.camperscache.world.LinkedSitePlacement;
import com.madreaper16.camperscache.world.LinkedSiteStructure;
import com.madreaper16.camperscache.world.OreProcessor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.Villager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CampersCache implements ModInitializer {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Logger LOGGER = LoggerFactory.getLogger("campers_cache");
    public static final EntityType<Camper> CAMPER =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    id("camper"),
                    EntityType.Builder.of(Camper::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE, id("camper"))));

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("campers_cache", path);
    }

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(CAMPER, Villager.createAttributes());
        Registry.register(BuiltInRegistries.STRUCTURE_PROCESSOR, id("ores"), OreProcessor.CODEC);
        Registry.register(
                BuiltInRegistries.STRUCTURE_PLACEMENT,
                id("linked_site"),
                LinkedSitePlacement.LINKED_CODEC);
        Registry.register(
                BuiltInRegistries.STRUCTURE_TYPE, id("linked_site"), LinkedSiteStructure.TYPE);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(
                server -> ModConfig.load());
        LayoutCommand.register();
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> {
            if (!com.madreaper16.camperscache.config.SettingsStore.active().enabled
                    || !com.madreaper16.camperscache.config.SettingsStore.active().camperEnabled)
                return true;
            if (level instanceof net.minecraft.server.level.ServerLevel server
                    && !player.isSpectator()
                    && entity
                            instanceof
                            net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                var ledger = CampLedger.get(server);
                var owner = ledger.ownerOf(pos);
                if (owner != null) {
                    owner.open(player.getStringUUID());
                    ChestHooks.contents(chest)
                            .forEach((item, count) ->
                                    owner.stolen(player.getStringUUID(), item, count));
                    ledger.setDirty();
                }
            }
            return true;
        });
        LOGGER.info("Camper's Cache loaded.");
    }
}
