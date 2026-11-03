package com.madreaper16.camperscache.mixin;

import com.madreaper16.camperscache.CampersCache;
import com.madreaper16.camperscache.config.ConfigLoot;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.level.storage.loot.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LootTable.class)
public abstract class LootTableMixin {
    @Inject(method = "fill", at = @At("HEAD"), cancellable = true)
    private void cache$fill(Container container, LootParams params, long seed, CallbackInfo ci) {
        var level = params.getLevel();
        var registry = level.getServer().reloadableRegistries();
        for (String name :
                new String[] {"supply_shack", "hidden_storehouse", "storehouse_supplies"})
            if (registry.getLootTable(
                            ResourceKey.create(
                                    Registries.LOOT_TABLE, CampersCache.id("chests/" + name)))
                    == (Object) this) {
                ConfigLoot.fill(name, container, level, seed);
                ci.cancel();
                return;
            }
    }
}
