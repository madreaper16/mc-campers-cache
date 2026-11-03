package com.madreaper16.camperscache.world;

import com.madreaper16.camperscache.CampersCache;
import com.madreaper16.camperscache.config.SettingsStore;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public final class LinkedSiteStructure extends Structure {
    public static final MapCodec<LinkedSiteStructure> CODEC =
            RecordCodecBuilder.mapCodec(instance ->
                    instance.group(
                                    settingsCodec(instance),
                                    Codec.STRING
                                            .fieldOf("site")
                                            .forGetter((LinkedSiteStructure s) -> s.site))
                            .apply(instance, LinkedSiteStructure::new));
    public static final StructureType<LinkedSiteStructure> TYPE = () -> CODEC;
    private final String site;

    public LinkedSiteStructure(StructureSettings settings, String site) {
        super(settings);
        this.site = site;
    }

    private static int surface(GenerationContext context, ChunkPos chunk) {
        return context.chunkGenerator()
                .getFirstFreeHeight(
                        chunk.getMinBlockX() + 8,
                        chunk.getMinBlockZ() + 8,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(),
                        context.randomState());
    }

    // Reject the whole layout if any enabled site cannot fit.
    public static boolean familyFits(GenerationContext context, CampLayout layout) {
        var config = SettingsStore.active();
        if (!config.enabled
                || new java.util.Random(context.seed() ^ layout.camp().pack() ^ 0x51A7EL)
                                .nextDouble()
                        >= config.familyChance) return false;
        for (String name : CampLayout.SITES) {
            var siteConfig = config.sites.get(name);
            if (!siteConfig.enabled) continue;
            ChunkPos chunk = layout.site(name);
            int y = surface(context, chunk);
            int floor =
                    context.chunkGenerator()
                            .getFirstFreeHeight(
                                    chunk.getMinBlockX() + 8,
                                    chunk.getMinBlockZ() + 8,
                                    Heightmap.Types.OCEAN_FLOOR_WG,
                                    context.heightAccessor(),
                                    context.randomState());
            if (y != floor
                    || y < context.heightAccessor().getMinY() + 16
                    || y > context.heightAccessor().getMaxY() - 16) return false;
            var biome =
                    context.biomeResolver()
                            .getNoiseBiome(
                                    QuartPos.fromBlock(chunk.getMinBlockX() + 8),
                                    QuartPos.fromBlock(y - 1),
                                    QuartPos.fromBlock(chunk.getMinBlockZ() + 8));
            var structure =
                    context.registryAccess()
                            .lookupOrThrow(Registries.STRUCTURE)
                            .getValue(CampersCache.id(name));
            String biomeId = biome.unwrapKey().orElseThrow().identifier().toString();
            if (structure == null || !siteConfig.biomes.contains(biomeId)) return false;
            if (new java.util.Random(context.seed() ^ layout.camp().pack() ^ name.hashCode())
                            .nextDouble()
                    >= siteConfig.biomeChances.getOrDefault(biomeId, 1.0)) return false;
        }
        if (!config.sites.get("hidden_storehouse").enabled) return true;
        var hiddenContext = atChunk(context, layout.storehouse());
        var hidden =
                (LinkedSiteStructure)
                        context.registryAccess()
                                .lookupOrThrow(Registries.STRUCTURE)
                                .getValue(CampersCache.id("hidden_storehouse"));
        return hidden.buriedStorehouse(hiddenContext).isPresent();
    }

    private static GenerationContext atChunk(GenerationContext context, ChunkPos chunk) {
        return new GenerationContext(
                context.registryAccess(),
                context.chunkGenerator(),
                context.biomeSource(),
                context.climateSampler(),
                context.randomState(),
                context.structureTemplateManager(),
                context.seed(),
                chunk,
                context.heightAccessor(),
                biome -> true);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!SettingsStore.active().enabled || !SettingsStore.active().sites.get(site).enabled)
            return Optional.empty();
        CampLayout layout =
                CampLayout.atChunk(context.seed(), context.chunkPos().x(), context.chunkPos().z());
        // /place can preview a site outside its normal layout.
        boolean naturalCandidate = context.chunkPos().equals(layout.site(site));
        if (naturalCandidate && !familyFits(context, layout)) return Optional.empty();
        // Check the surface biome, not the cave biome under the storehouse.
        var placement = atChunk(context, context.chunkPos());
        if (site.equals("hidden_storehouse")) return buriedStorehouse(placement);
        var pool =
                context.registryAccess()
                        .lookupOrThrow(Registries.TEMPLATE_POOL)
                        .getOrThrow(
                                ResourceKey.create(
                                        Registries.TEMPLATE_POOL, CampersCache.id(site)));
        return new JigsawStructure(
                        settings,
                        pool,
                        1,
                        ConstantHeight.of(VerticalAnchor.absolute(0)),
                        false,
                        Heightmap.Types.WORLD_SURFACE_WG)
                .findGenerationPoint(placement);
    }

    private Optional<GenerationStub> buriedStorehouse(GenerationContext context) {
        var pool =
                context.registryAccess()
                        .lookupOrThrow(Registries.TEMPLATE_POOL)
                        .getOrThrow(
                                ResourceKey.create(
                                        Registries.TEMPLATE_POOL,
                                        CampersCache.id("hidden_storehouse")));
        int height = surface(context, context.chunkPos());
        var provisional =
                new JigsawStructure(
                                settings,
                                pool,
                                1,
                                ConstantHeight.of(VerticalAnchor.absolute(height - 7)),
                                false)
                        .findGenerationPoint(context);
        if (provisional.isEmpty()) return Optional.empty();
        var pieces = provisional.get().getPiecesBuilder().build().pieces();
        var root = (PoolElementStructurePiece) pieces.getFirst();
        var bounds = root.getBoundingBox();
        int lowest = Integer.MAX_VALUE;
        // Check ground height across the whole rotated building.
        for (int x = bounds.minX(); x <= bounds.maxX(); x++)
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
                lowest =
                        Math.min(
                                lowest,
                                context.chunkGenerator()
                                        .getFirstFreeHeight(
                                                x,
                                                z,
                                                Heightmap.Types.OCEAN_FLOOR_WG,
                                                context.heightAccessor(),
                                                context.randomState()));
        int baseY = lowest - 11; // Authored roof is local Y=7, with three blocks of earth above it.
        if (baseY <= context.heightAccessor().getMinY()) return Optional.empty();
        root.move(0, baseY - root.getPosition().getY(), 0);
        if (!enclosedEntrance(context, root)) return Optional.empty();
        var point = provisional.get().position();
        return Optional.of(
                new GenerationStub(
                        new BlockPos(point.getX(), baseY + 1, point.getZ()),
                        builder -> pieces.forEach(builder::addPiece)));
    }

    // Leave two blocks of solid ground around the shaft. Check for cave gaps too.
    public static boolean enclosedEntrance(
            GenerationContext context, PoolElementStructurePiece piece) {
        BlockPos ladder = hatchColumn(piece);
        int top =
                context.chunkGenerator()
                        .getFirstFreeHeight(
                                ladder.getX(),
                                ladder.getZ(),
                                Heightmap.Types.OCEAN_FLOOR_WG,
                                context.heightAccessor(),
                                context.randomState());
        int surface =
                context.chunkGenerator()
                        .getFirstFreeHeight(
                                ladder.getX(),
                                ladder.getZ(),
                                Heightmap.Types.WORLD_SURFACE_WG,
                                context.heightAccessor(),
                                context.randomState());
        if (top != surface || top <= piece.getPosition().getY() + 8) return false;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                var column =
                        context.chunkGenerator()
                                .getBaseColumn(
                                        ladder.getX() + dx,
                                        ladder.getZ() + dz,
                                        context.heightAccessor(),
                                        context.randomState());
                for (int y = piece.getPosition().getY() + 8; y < top; y++) {
                    var block = column.getBlock(y);
                    if (!block.isSolidRender() || !block.getFluidState().isEmpty()) return false;
                }
            }
        return true;
    }

    @Override
    public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context) {
        return findGenerationPoint(context);
    }

    public static BlockPos hatchColumn(PoolElementStructurePiece piece) {
        // The original doorway is at X=5..8, Z=1, Y=1..2.
        return StructureTemplate.transform(
                        new BlockPos(6, 1, 1), Mirror.NONE, piece.getRotation(), BlockPos.ZERO)
                .offset(piece.getPosition());
    }

    @Override
    public void afterPlace(
            WorldGenLevel level,
            StructureManager structures,
            ChunkGenerator generator,
            RandomSource random,
            BoundingBox chunkBounds,
            ChunkPos chunk,
            PiecesContainer pieces) {
        if (!site.equals("hidden_storehouse") || pieces.pieces().isEmpty()) return;
        var root = (PoolElementStructurePiece) pieces.pieces().getFirst();
        BlockPos ladder = hatchColumn(root);
        Direction facing = root.getRotation().rotate(Direction.SOUTH);
        BlockPos backing = ladder.relative(facing.getOpposite());
        int top =
                generator.getFirstFreeHeight(
                        ladder.getX(),
                        ladder.getZ(),
                        Heightmap.Types.OCEAN_FLOOR_WG,
                        level,
                        level.getLevel().getChunkSource().randomState());
        var ladderState = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
        for (int y = ladder.getY(); y < top; y++) {
            BlockPos support = new BlockPos(backing.getX(), y, backing.getZ());
            BlockPos rung = new BlockPos(ladder.getX(), y, ladder.getZ());
            if (chunkBounds.isInside(support))
                level.setBlock(support, Blocks.STONE_BRICKS.defaultBlockState(), 2);
            if (chunkBounds.isInside(rung)) level.setBlock(rung, ladderState, 2);
        }
        BlockPos hatch = new BlockPos(ladder.getX(), top, ladder.getZ());
        var trapdoor =
                Blocks.SPRUCE_TRAPDOOR
                        .defaultBlockState()
                        .setValue(TrapDoorBlock.FACING, facing)
                        .setValue(TrapDoorBlock.HALF, Half.BOTTOM);
        if (chunkBounds.isInside(hatch)) level.setBlock(hatch, trapdoor, 2);
    }

    @Override
    public StructureType<?> type() {
        return TYPE;
    }
}
