package com.madreaper16.camperscache.mixin;

import com.madreaper16.camperscache.config.StackSizePreview;

import net.minecraft.core.component.*;
import net.minecraft.world.item.Item;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Save stack sizes before world data loads.
@Mixin(Item.Properties.class)
public abstract class ItemPropertiesMixin implements StackSizePreview {
    @Unique private int cache$capacity = 64;

    @Inject(
            method =
                    "component(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Lnet/minecraft/world/item/Item$Properties;",
            at = @At("HEAD"))
    private <T> void cache$component(
            DataComponentType<T> type, T value, CallbackInfoReturnable<Item.Properties> ci) {
        if (type == DataComponents.MAX_STACK_SIZE && value instanceof Integer size)
            cache$capacity = size;
    }

    @Override
    public int cache$stackSize() {
        return cache$capacity;
    }
}
