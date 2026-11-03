package com.madreaper16.camperscache.world;

import com.madreaper16.camperscache.config.SettingsStore;

import net.minecraft.world.level.ChunkPos;

import java.util.Random;

// Keep each camp and its two destinations in the same region.
public record CampLayout(ChunkPos camp, ChunkPos supplyShack, ChunkPos storehouse) {
    public static final int REGION_CHUNKS = 192;
    public static final int DESTINATION_RADIUS = 944;
    public static final String[] SITES = {"camp", "supply_shack", "hidden_storehouse"};

    public static CampLayout atChunk(long seed, int chunkX, int chunkZ) {
        int spacing = SettingsStore.active().regionChunks;
        return atRegion(seed, Math.floorDiv(chunkX, spacing), Math.floorDiv(chunkZ, spacing));
    }

    public static CampLayout atRegion(long seed, int regionX, int regionZ) {
        Random random =
                new Random(
                        seed
                                ^ (regionX * 341873128712L)
                                ^ (regionZ * 132897987541L)
                                ^ 0x43414D504552L);
        int spacing = SettingsStore.active().regionChunks;
        int distance = SettingsStore.active().destinationDistance;
        int x = regionX * spacing + spacing / 2 + random.nextInt(9) - 4;
        int z = regionZ * spacing + spacing / 2 + random.nextInt(9) - 4;
        double angle = random.nextDouble() * Math.PI * 2;
        int dx = (int) Math.round(Math.cos(angle) * distance / 16.0);
        int dz = (int) Math.round(Math.sin(angle) * distance / 16.0);
        return new CampLayout(
                new ChunkPos(x, z), new ChunkPos(x + dx, z + dz), new ChunkPos(x - dx, z - dz));
    }

    public ChunkPos site(String name) {
        return switch (name) {
            case "camp" -> camp;
            case "supply_shack" -> supplyShack;
            case "hidden_storehouse" -> storehouse;
            default -> throw new IllegalArgumentException("Unknown camp site: " + name);
        };
    }
}
