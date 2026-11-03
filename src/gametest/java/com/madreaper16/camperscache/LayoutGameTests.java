package com.madreaper16.camperscache;

import com.madreaper16.camperscache.world.CampLayout;
import com.madreaper16.camperscache.world.LinkedSitePlacement;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public final class LayoutGameTests {
    private static double distance(ChunkPos a, ChunkPos b) {
        return Math.hypot((a.x() - b.x()) * 16.0, (a.z() - b.z()) * 16.0);
    }

    @GameTest
    public void familiesStayOppositeAndNeighborsStayDistant(GameTestHelper test) {
        for (long seed = 0; seed < 256; seed++) {
            for (int rx = -3; rx <= 3; rx++)
                for (int rz = -3; rz <= 3; rz++) {
                    var family = CampLayout.atRegion(seed, rx, rz);
                    test.assertTrue(
                            distance(family.camp(), family.supplyShack()) >= 930
                                    && distance(family.camp(), family.supplyShack()) <= 960,
                            "Shack is near the distance limit");
                    test.assertTrue(
                            distance(family.camp(), family.storehouse()) <= 960,
                            "Storehouse stays within 1000 including template margin");
                    test.assertTrue(
                            family.supplyShack().x() + family.storehouse().x()
                                            == family.camp().x() * 2
                                    && family.supplyShack().z() + family.storehouse().z()
                                            == family.camp().z() * 2,
                            "Destinations are diametrically opposite");
                    test.assertTrue(
                            distance(family.supplyShack(), family.storehouse()) >= 1860,
                            "Sibling caches cannot cluster");
                    for (String role : CampLayout.SITES) {
                        var candidate = family.site(role);
                        test.assertTrue(
                                new LinkedSitePlacement(role)
                                        .getPotentialStructureChunk(
                                                seed, candidate.x(), candidate.z())
                                        .equals(candidate),
                                "Candidate lookup is stable at negative coordinates too");
                    }
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            var neighbor = CampLayout.atRegion(seed, rx + dx, rz + dz);
                            for (String a : CampLayout.SITES)
                                for (String b : CampLayout.SITES)
                                    test.assertTrue(
                                            distance(family.site(a), neighbor.site(b)) >= 1000,
                                            "Neighboring families also keep structures apart");
                        }
                }
        }
        test.succeed();
    }

    @GameTest(maxTicks = 200)
    public void realGenerationAndLocateUseTheSameFamily(GameTestHelper test)
            throws ReflectiveOperationException {
        var level = test.getLevel();
        var generator = level.getChunkSource().getGenerator();
        // The test world needs deeper ground for the storehouse.
        var flat = (net.minecraft.world.level.levelgen.FlatLevelSource) generator;
        var originalLayers = java.util.List.copyOf(flat.settings().getLayersInfo());
        flat.settings().getLayersInfo().clear();
        flat.settings()
                .getLayersInfo()
                .add(
                        new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(
                                1, net.minecraft.world.level.block.Blocks.BEDROCK));
        flat.settings()
                .getLayersInfo()
                .add(
                        new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(
                                30, net.minecraft.world.level.block.Blocks.DIRT));
        flat.settings()
                .getLayersInfo()
                .add(
                        new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(
                                1, net.minecraft.world.level.block.Blocks.GRASS_BLOCK));
        flat.settings().updateLayers();
        // Enable structures so /locate works in this test.
        var options = level.getServer().getWorldGenSettings().options();
        boolean originalStructures = options.generateStructures();
        var structuresField =
                net.minecraft.world.level.levelgen.WorldOptions.class.getDeclaredField(
                        "generateStructures");
        structuresField.setAccessible(true);
        structuresField.setBoolean(options, true);
        var configField =
                com.madreaper16.camperscache.config.SettingsStore.class.getDeclaredField("current");
        configField.setAccessible(true);
        var originalConfig = com.madreaper16.camperscache.config.SettingsStore.snapshot();
        var fixtureConfig = com.madreaper16.camperscache.config.SettingsStore.copy(originalConfig);
        fixtureConfig.sites.get("camp").biomes.add("minecraft:desert");
        configField.set(null, fixtureConfig);
        try {
            var random = level.getChunkSource().randomState();
            var sampler = random.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
            var origin = ChunkPos.containing(test.absolutePos(BlockPos.ZERO));
            // Use new chunks that have the deeper ground.
            var family =
                    CampLayout.atRegion(
                            level.getSeed(),
                            Math.floorDiv(origin.x(), CampLayout.REGION_CHUNKS) + 7,
                            Math.floorDiv(origin.z(), CampLayout.REGION_CHUNKS) + 7);
            var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            BlockPos home = null;
            for (String role : CampLayout.SITES) {
                var holder =
                        registry.getOrThrow(
                                ResourceKey.create(Registries.STRUCTURE, CampersCache.id(role)));
                var chunk = family.site(role);
                var context =
                        new Structure.GenerationContext(
                                level.registryAccess(),
                                generator,
                                generator.getBiomeSource(),
                                sampler,
                                random,
                                level.getStructureTemplateManager(),
                                level.getSeed(),
                                chunk,
                                level,
                                biome -> holder.value().biomes().contains(biome));
                var stub =
                        holder.value()
                                .findValidGenerationPoint(context)
                                .orElseThrow(() ->
                                        new AssertionError("Family site must generate: " + role));
                var pieces = stub.getPiecesBuilder().build().pieces();
                test.assertTrue(pieces.size() == 1, "Exactly one building at " + role);
                var piece = (PoolElementStructurePiece) pieces.getFirst();
                if (role.equals("camp"))
                    home =
                            StructureTemplate.transform(
                                            new BlockPos(4, 1, 5),
                                            Mirror.NONE,
                                            piece.getRotation(),
                                            BlockPos.ZERO)
                                    .offset(piece.getPosition());
                else {
                    var box = piece.getBoundingBox();
                    for (int x : new int[] {box.minX(), box.maxX()})
                        for (int z : new int[] {box.minZ(), box.maxZ()})
                            test.assertTrue(
                                    Math.hypot(x - home.getX(), z - home.getZ()) <= 1000,
                                    "Entire cache footprint is within 1000 of the actual campfire");
                    if (role.equals("hidden_storehouse")) {
                        int surface =
                                generator.getFirstFreeHeight(
                                        chunk.getMinBlockX() + 8,
                                        chunk.getMinBlockZ() + 8,
                                        Heightmap.Types.WORLD_SURFACE_WG,
                                        level,
                                        random);
                        test.assertTrue(
                                piece.getPosition().getY() + 7 <= surface - 4,
                                "Storehouse roof has three blocks of ground cover");
                    }
                }
                var found =
                        generator.findNearestMapStructure(
                                level,
                                HolderSet.direct(holder),
                                family.camp().getWorldPosition(),
                                10,
                                false);
                test.assertTrue(
                        found != null && ChunkPos.containing(found.getFirst()).equals(chunk),
                        "Native /locate finds the assigned "
                                + role
                                + "; found="
                                + found
                                + "; placements="
                                + level.getChunkSource()
                                        .getGeneratorState()
                                        .getPlacementsForStructure(holder)
                                + "; expected="
                                + chunk);
                if (role.equals("hidden_storehouse")) {
                    var column =
                            com.madreaper16.camperscache.world.LinkedSiteStructure.hatchColumn(
                                    piece);
                    int top =
                            generator.getFirstFreeHeight(
                                    column.getX(),
                                    column.getZ(),
                                    Heightmap.Types.OCEAN_FLOOR_WG,
                                    level,
                                    random);
                    var hatch = new BlockPos(column.getX(), top, column.getZ());
                    test.assertTrue(
                            level.getBlockState(hatch)
                                    .is(net.minecraft.world.level.block.Blocks.SPRUCE_TRAPDOOR),
                            "Actual natural placement creates the surface hatch");
                    for (int y = column.getY(); y < top; y++)
                        test.assertTrue(
                                level.getBlockState(new BlockPos(column.getX(), y, column.getZ()))
                                        .is(net.minecraft.world.level.block.Blocks.LADDER),
                                "Continuous ladder from hatch into the building");
                    var doorway =
                            StructureTemplate.transform(
                                            new BlockPos(6, 1, 1),
                                            Mirror.NONE,
                                            piece.getRotation(),
                                            BlockPos.ZERO)
                                    .offset(piece.getPosition());
                    test.assertTrue(
                            column.equals(doorway),
                            "Ladder lands in the authored doorway, not an interior side wall");
                    var facing = piece.getRotation().rotate(net.minecraft.core.Direction.SOUTH);
                    test.assertTrue(
                            level.getBlockState(column)
                                            .getValue(
                                                    net.minecraft.world.level.block.LadderBlock
                                                            .FACING)
                                    == facing,
                            "Ladder faces inward through the doorway");
                    test.assertTrue(
                            level.getBlockState(column.relative(facing)).isAir()
                                    && level.getBlockState(column.relative(facing).above()).isAir(),
                            "Player can step off the bottom into the original entrance");
                }
            }
            java.util.List<String> output = new java.util.ArrayList<>();
            var sink =
                    new net.minecraft.commands.CommandSource() {
                        public void sendSystemMessage(
                                net.minecraft.network.chat.Component message) {
                            output.add(message.getString());
                        }

                        public boolean acceptsSuccess() {
                            return true;
                        }

                        public boolean acceptsFailure() {
                            return true;
                        }

                        public boolean shouldInformAdmins() {
                            return false;
                        }
                    };
            var source =
                    level.getServer()
                            .createCommandSourceStack()
                            .withLevel(level)
                            .withPosition(
                                    net.minecraft.world.phys.Vec3.atCenterOf(
                                            family.camp().getWorldPosition()))
                            .withPermission(
                                    net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS)
                            .withSource(sink);
            try {
                level.getServer()
                        .getCommands()
                        .getDispatcher()
                        .execute("camperscache layout", source);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new AssertionError(e);
            }
            test.assertTrue(
                    output.size() == 4 && output.getFirst().startsWith("Nearest generated camp"),
                    "Layout command resolves an actual generated camp and all three sites: "
                            + output);
            test.assertTrue(
                    output.stream().noneMatch(s -> s.contains("no generated")),
                    "Command never offers invented coordinates for missing sites");
            test.succeed();
        } finally {
            configField.set(null, originalConfig);
            structuresField.setBoolean(options, originalStructures);
            flat.settings().getLayersInfo().clear();
            flat.settings().getLayersInfo().addAll(originalLayers);
            flat.settings().updateLayers();
        }
    }

    @GameTest(maxTicks = 200)
    public void reportedSlopesRejectExposedShaftsAndOtherFamiliesStillGenerate(
            GameTestHelper test) {
        var level = test.getLevel();
        var registries = level.registryAccess();
        var parameters =
                registries
                        .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(
                                net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists
                                        .OVERWORLD);
        var settings =
                registries
                        .lookupOrThrow(Registries.NOISE_SETTINGS)
                        .getOrThrow(
                                net.minecraft.world.level.levelgen.NoiseGeneratorSettings
                                        .OVERWORLD);
        var generator =
                new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                        net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(
                                parameters),
                        settings);
        long seed = -6427129376600366161L;
        var random =
                net.minecraft.world.level.levelgen.RandomState.create(
                        registries.lookupOrThrow(Registries.NOISE), seed, settings.value());
        var sampler = random.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
        var structure =
                registries
                        .lookupOrThrow(Registries.STRUCTURE)
                        .getValue(CampersCache.id("hidden_storehouse"));
        for (var chunk : java.util.List.of(new ChunkPos(-243, -252), new ChunkPos(-501, -36))) {
            var context =
                    new Structure.GenerationContext(
                            registries,
                            generator,
                            generator.getBiomeSource(),
                            sampler,
                            random,
                            level.getStructureTemplateManager(),
                            seed,
                            chunk,
                            level,
                            biome -> true);
            test.assertTrue(
                    structure.findValidGenerationPoint(context).isEmpty(),
                    "Reported slope is rejected instead of exposing a shaft: " + chunk);
        }
        var rejectedFamily = CampLayout.atChunk(seed, -501, -36);
        for (String role : CampLayout.SITES) {
            var linked =
                    registries.lookupOrThrow(Registries.STRUCTURE).getValue(CampersCache.id(role));
            var context =
                    new Structure.GenerationContext(
                            registries,
                            generator,
                            generator.getBiomeSource(),
                            sampler,
                            random,
                            level.getStructureTemplateManager(),
                            seed,
                            rejectedFamily.site(role),
                            level,
                            biome -> true);
            test.assertTrue(
                    linked.findValidGenerationPoint(context).isEmpty(),
                    "Unsafe entrance rejects the whole family, including " + role);
        }
        int accepted = 0;
        for (int rx = -3; rx <= 3; rx++)
            for (int rz = -3; rz <= 3; rz++) {
                var family = CampLayout.atRegion(seed, rx, rz);
                var context =
                        new Structure.GenerationContext(
                                registries,
                                generator,
                                generator.getBiomeSource(),
                                sampler,
                                random,
                                level.getStructureTemplateManager(),
                                seed,
                                family.storehouse(),
                                level,
                                biome -> true);
                var stub = structure.findValidGenerationPoint(context);
                if (stub.isEmpty()) continue;
                accepted++;
                var piece =
                        (PoolElementStructurePiece)
                                stub.get().getPiecesBuilder().build().pieces().getFirst();
                var box = piece.getBoundingBox();
                for (int x = box.minX(); x <= box.maxX(); x++)
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        int ground =
                                generator.getFirstFreeHeight(
                                        x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                        test.assertTrue(
                                piece.getPosition().getY() + 7 <= ground - 4,
                                "Accepted storehouses keep their complete rotated roof buried");
                    }
                var hatch =
                        com.madreaper16.camperscache.world.LinkedSiteStructure.hatchColumn(piece);
                int top =
                        generator.getFirstFreeHeight(
                                hatch.getX(),
                                hatch.getZ(),
                                Heightmap.Types.OCEAN_FLOOR_WG,
                                level,
                                random);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++) {
                        var column =
                                generator.getBaseColumn(
                                        hatch.getX() + dx, hatch.getZ() + dz, level, random);
                        for (int y = piece.getPosition().getY() + 8; y < top; y++)
                            test.assertTrue(
                                    column.getBlock(y).isSolidRender()
                                            && column.getBlock(y).getFluidState().isEmpty(),
                                    "Natural terrain encloses the entire shaft above the roof");
                    }
                System.out.println(
                        "Safe natural storehouse: "
                                + family.storehouse()
                                + " hatch="
                                + hatch
                                + " surface="
                                + top);
            }
        test.assertTrue(
                accepted > 0,
                "Normal noise terrain still produces complete families with enclosed entrances");
        var invalid = CampLayout.atRegion(seed, 0, -1);
        var camp = registries.lookupOrThrow(Registries.STRUCTURE).getValue(CampersCache.id("camp"));
        var oceanContext =
                new Structure.GenerationContext(
                        registries,
                        generator,
                        generator.getBiomeSource(),
                        sampler,
                        random,
                        level.getStructureTemplateManager(),
                        seed,
                        invalid.camp(),
                        level,
                        biome -> true);
        test.assertTrue(
                camp.findValidGenerationPoint(oceanContext).isEmpty(),
                "The ocean candidate reported by alpha 2 is rejected, not offered as a real camp");
        test.succeed();
    }
}
