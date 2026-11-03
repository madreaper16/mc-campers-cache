package com.madreaper16.camperscache.mixin;

import com.madreaper16.camperscache.data.CampLedger;
import com.madreaper16.camperscache.data.CampState;
import com.madreaper16.camperscache.data.ChestHooks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {
    @Unique private CampState campersCache$owner;
    @Unique private Map<String, Integer> campersCache$before;

    @Inject(method = "clicked", at = @At("HEAD"))
    private void campersCache$before(
            int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        campersCache$owner = null;
        campersCache$before = null;
        if (!player.isSpectator()
                && player.level() instanceof ServerLevel level
                && (Object) this instanceof ChestMenu menu) {
            campersCache$owner = ChestHooks.owner(level, menu);
            if (campersCache$owner != null)
                campersCache$before = ChestHooks.contents(menu.getContainer());
        }
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void campersCache$after(
            int slot, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (campersCache$owner != null
                && campersCache$before != null
                && player.level() instanceof ServerLevel level
                && (Object) this instanceof ChestMenu menu) {
            ChestHooks.recordRemoval(
                    campersCache$owner,
                    player.getStringUUID(),
                    campersCache$before,
                    ChestHooks.contents(menu.getContainer()));
            CampLedger.get(level).setDirty();
        }
        campersCache$owner = null;
        campersCache$before = null;
    }
}
