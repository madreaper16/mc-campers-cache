package com.madreaper16.camperscache.mixin;

import com.madreaper16.camperscache.config.SettingsStore;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureEntityInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

import java.util.*;

@Mixin(StructureTemplate.class)
public abstract class StructureEntitiesMixin {
    @Redirect(
            method = "placeEntities",
            at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<StructureEntityInfo> cache$entities(List<StructureEntityInfo> entities) {
        var config = SettingsStore.active();
        List<StructureEntityInfo> result = new ArrayList<>();
        List<StructureEntityInfo> guards = new ArrayList<>();
        String site = "";
        for (var entity : entities) {
            String own = entity.nbt.getStringOr("CampersCacheSite", "");
            if (own.isEmpty()) {
                result.add(entity);
                continue;
            }
            site = own;
            if (entity.nbt.getStringOr("id", "").equals("campers_cache:camper")) {
                if (config.enabled && config.camperEnabled) result.add(entity);
            } else guards.add(entity);
        }
        if (config.enabled && config.guardsEnabled && !guards.isEmpty()) {
            int count = site.equals("supply_shack") ? config.shackGuards : config.storehouseGuards;
            for (int i = 0; i < count; i++) {
                var e = guards.get(i % guards.size());
                result.add(new StructureEntityInfo(e.pos, e.blockPos, e.nbt.copy()));
            }
        }
        return result.iterator();
    }
}
