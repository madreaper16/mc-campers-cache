package com.madreaper16.camperscache.data;

import com.madreaper16.camperscache.entity.Camper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ChestHooks {
    private ChestHooks() {}

    public static void opened(ServerPlayer player, ChestBlockEntity chest) {
        if (!com.madreaper16.camperscache.config.SettingsStore.active().enabled
                || !com.madreaper16.camperscache.config.SettingsStore.active().camperEnabled)
            return;
        if (player.isSpectator() || !(chest.getLevel() instanceof ServerLevel level)) return;
        CampLedger ledger = CampLedger.get(level);
        CampState state = ledger.ownerOf(chest.getBlockPos());
        if (state == null) return;
        state.open(player.getStringUUID());
        ledger.setDirty();
        for (var entry : ledger.camps.entrySet()) {
            if (entry.getValue() == state
                    && level.getEntity(UUID.fromString(entry.getKey())) instanceof Camper camper) {
                if (camper.isSleeping()) camper.stopSleeping();
                camper.angryParticles(level);
                break;
            }
        }
    }

    public static CampState owner(ServerLevel level, ChestMenu menu) {
        if (!com.madreaper16.camperscache.config.SettingsStore.active().enabled
                || !com.madreaper16.camperscache.config.SettingsStore.active().camperEnabled)
            return null;
        Container container = menu.getContainer();
        CampLedger ledger = CampLedger.get(level);
        if (container instanceof ChestBlockEntity chest) return ledger.ownerOf(chest.getBlockPos());
        if (container instanceof CompoundContainer combined) {
            for (CampState state : ledger.camps.values()) {
                if (state.chest == null) continue;
                var entity =
                        level.getBlockEntity(
                                new BlockPos(state.chest[0], state.chest[1], state.chest[2]));
                if (entity instanceof Container part && combined.contains(part)) return state;
            }
        }
        return null;
    }

    public static Map<String, Integer> contents(Container inventory) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (!stack.isEmpty())
                result.merge(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                        stack.getCount(),
                        Integer::sum);
        }
        return result;
    }

    public static void recordRemoval(
            CampState owner,
            String player,
            Map<String, Integer> before,
            Map<String, Integer> after) {
        before.forEach((item, count) ->
                owner.stolen(player, item, Math.max(0, count - after.getOrDefault(item, 0))));
    }
}
