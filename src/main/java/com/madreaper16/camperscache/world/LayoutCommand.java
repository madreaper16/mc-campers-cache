package com.madreaper16.camperscache.world;

import com.madreaper16.camperscache.CampersCache;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;

public final class LayoutCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(
                        Commands.literal("camperscache")
                                .requires(source ->
                                        source.permissions()
                                                .hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                .then(
                                        Commands.literal("layout")
                                                .executes(command -> {
                                                    var source = command.getSource();
                                                    var level = source.getLevel();
                                                    var registry =
                                                            level.registryAccess()
                                                                    .lookupOrThrow(
                                                                            Registries.STRUCTURE);
                                                    var camp =
                                                            registry.getOrThrow(
                                                                    ResourceKey.create(
                                                                            Registries.STRUCTURE,
                                                                            CampersCache.id(
                                                                                    "camp")));
                                                    var nearest =
                                                            level.getChunkSource()
                                                                    .getGenerator()
                                                                    .findNearestMapStructure(
                                                                            level,
                                                                            HolderSet.direct(camp),
                                                                            BlockPos.containing(
                                                                                    source
                                                                                            .getPosition()),
                                                                            100,
                                                                            false);
                                                    if (nearest == null) {
                                                        source.sendFailure(
                                                                Component.literal(
                                                                        "No generated camp found nearby."));
                                                        return 0;
                                                    }
                                                    var campChunk =
                                                            ChunkPos.containing(nearest.getFirst());
                                                    var layout =
                                                            CampLayout.atChunk(
                                                                    level.getSeed(),
                                                                    campChunk.x(),
                                                                    campChunk.z());
                                                    source.sendSuccess(
                                                            () ->
                                                                    Component.literal(
                                                                            "Nearest generated camp and its linked destinations:"),
                                                            false);
                                                    for (String name : CampLayout.SITES) {
                                                        var site = layout.site(name);
                                                        var structure =
                                                                registry.getValue(
                                                                        CampersCache.id(name));
                                                        var data =
                                                                level.getChunk(
                                                                        site.x(),
                                                                        site.z(),
                                                                        ChunkStatus
                                                                                .STRUCTURE_STARTS);
                                                        var start =
                                                                level.structureManager()
                                                                        .getStartForStructure(
                                                                                structure, data);
                                                        if (start == null || !start.isValid()) {
                                                            source.sendFailure(
                                                                    Component.literal(
                                                                            name
                                                                                    + ": no generated structure at its assigned site (possibly older terrain)."));
                                                            continue;
                                                        }
                                                        var box = start.getBoundingBox();
                                                        BlockPos target =
                                                                new BlockPos(
                                                                        (box.minX() + box.maxX())
                                                                                / 2,
                                                                        0,
                                                                        (box.minZ() + box.maxZ())
                                                                                / 2);
                                                        if (name.equals("hidden_storehouse")
                                                                && start.getPieces().getFirst()
                                                                        instanceof
                                                                        PoolElementStructurePiece
                                                                                piece)
                                                            target =
                                                                    LinkedSiteStructure.hatchColumn(
                                                                            piece);
                                                        int x = target.getX(), z = target.getZ();
                                                        int y =
                                                                level.getChunkSource()
                                                                                .getGenerator()
                                                                                .getFirstFreeHeight(
                                                                                        x,
                                                                                        z,
                                                                                        Heightmap
                                                                                                .Types
                                                                                                .WORLD_SURFACE_WG,
                                                                                        level,
                                                                                        level.getChunkSource()
                                                                                                .randomState())
                                                                        + 2;
                                                        String teleport =
                                                                "/tp @s " + x + " " + y + " " + z;
                                                        source.sendSuccess(
                                                                () ->
                                                                        Component.literal(
                                                                                        name + ": ["
                                                                                                + x
                                                                                                + ", "
                                                                                                + y
                                                                                                + ", "
                                                                                                + z
                                                                                                + "]")
                                                                                .withStyle(style ->
                                                                                        style
                                                                                                .withClickEvent(
                                                                                                        new ClickEvent
                                                                                                                .SuggestCommand(
                                                                                                                teleport))),
                                                                false);
                                                    }
                                                    return 1;
                                                }))));
    }
}
