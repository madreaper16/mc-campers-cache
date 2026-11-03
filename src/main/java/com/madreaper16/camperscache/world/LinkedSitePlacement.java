package com.madreaper16.camperscache.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

// Use the shared camp layout with vanilla /locate.
public final class LinkedSitePlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<LinkedSitePlacement> LINKED_CODEC =
            Codec.STRING
                    .validate(name ->
                            java.util.Arrays.asList(CampLayout.SITES).contains(name)
                                    ? com.mojang.serialization.DataResult.success(name)
                                    : com.mojang.serialization.DataResult.error(
                                            () -> "Unknown camp site " + name))
                    .fieldOf("site")
                    .xmap(LinkedSitePlacement::new, placement -> placement.site);
    private final String site;

    public LinkedSitePlacement(String site) {
        super(CampLayout.REGION_CHUNKS, 1, RandomSpreadType.LINEAR, 3918200);
        this.site = site;
    }

    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int chunkX, int chunkZ) {
        return CampLayout.atChunk(seed, chunkX, chunkZ).site(site);
    }

    @Override
    public int spacing() {
        return com.madreaper16.camperscache.config.SettingsStore.active().regionChunks;
    }

    // Vanilla requires this codec return type.
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public MapCodec<RandomSpreadStructurePlacement> codec() {
        return (MapCodec) LINKED_CODEC;
    }
}
