package com.madreaper16.camperscache.data;

import com.madreaper16.camperscache.CampersCache;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashMap;
import java.util.Map;

// Keep debts when the Camper's chunk unloads.
public final class CampLedger extends SavedData {
    public Map<String, CampState> camps = new LinkedHashMap<>();
    public static final Codec<CampLedger> CODEC =
            Codec.STRING
                    .xmap(
                            json -> CampersCache.GSON.fromJson(json, CampLedger.class),
                            CampersCache.GSON::toJson)
                    .fieldOf("state")
                    .codec();
    public static final SavedDataType<CampLedger> TYPE =
            new SavedDataType<>(CampersCache.id("camps"), CampLedger::new, CODEC, null);

    public static CampLedger get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public CampState ownerOf(BlockPos pos) {
        for (CampState state : camps.values()) {
            if (state.chest != null
                    && state.chest[0] == pos.getX()
                    && state.chest[1] == pos.getY()
                    && state.chest[2] == pos.getZ()) return state;
        }
        return null;
    }
}
