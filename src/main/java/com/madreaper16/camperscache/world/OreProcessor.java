package com.madreaper16.camperscache.world;

import com.madreaper16.camperscache.config.ModConfig;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public final class OreProcessor implements StructureProcessor {
    public static final OreProcessor INSTANCE = new OreProcessor();
    public static final MapCodec<OreProcessor> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public MapCodec<? extends StructureProcessor> codec() {
        return CODEC;
    }

    @Override
    public StructureBlockInfo processBlock(
            LevelReader level,
            BlockPos origin,
            BlockPos pivot,
            BlockPos local,
            StructureBlockInfo current,
            StructurePlaceSettings settings) {
        if (!BuiltInRegistries.BLOCK
                .getKey(current.state().getBlock())
                .equals(Identifier.parse("minecraft:pink_wool"))) return current;
        if (!com.madreaper16.camperscache.config.SettingsStore.active().enabled
                || !com.madreaper16.camperscache.config.SettingsStore.active().oresEnabled)
            return new StructureBlockInfo(
                    current.pos(),
                    net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState(),
                    current.nbt());
        var weights = ModConfig.weights();
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        double roll = RandomSource.create(Mth.getSeed(current.pos())).nextDouble() * total;
        String chosen = "iron";
        for (String ore : ModConfig.ORES) {
            roll -= weights.get(ore);
            if (roll < 0) {
                chosen = ore;
                break;
            }
        }
        var block =
                BuiltInRegistries.BLOCK.getValue(
                        Identifier.parse("minecraft:deepslate_" + chosen + "_ore"));
        return new StructureBlockInfo(current.pos(), block.defaultBlockState(), current.nbt());
    }
}
