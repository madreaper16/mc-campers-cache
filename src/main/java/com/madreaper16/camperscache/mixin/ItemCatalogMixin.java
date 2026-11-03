package com.madreaper16.camperscache.mixin;

import com.madreaper16.camperscache.config.*;

import net.minecraft.world.item.Item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public abstract class ItemCatalogMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void cache$preview(Item.Properties properties, CallbackInfo ci) {
        ItemCatalog.record((Item) (Object) this, ((StackSizePreview) properties).cache$stackSize());
    }
}
