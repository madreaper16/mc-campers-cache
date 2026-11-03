package com.madreaper16.camperscache.mixin;

import com.madreaper16.camperscache.data.ChestHooks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestBlockEntity.class)
public abstract class ChestBlockEntityMixin {
    @Inject(method = "startOpen", at = @At("HEAD"))
    private void campersCache$opened(ContainerUser user, CallbackInfo ci) {
        if (user instanceof ServerPlayer player)
            ChestHooks.opened(player, (ChestBlockEntity) (Object) this);
    }
}
