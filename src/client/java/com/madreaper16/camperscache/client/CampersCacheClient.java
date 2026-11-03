package com.madreaper16.camperscache.client;

import com.madreaper16.camperscache.CampersCache;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.VillagerRenderer;

public final class CampersCacheClient implements ClientModInitializer {
    private static boolean configLoaded;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(CampersCache.CAMPER, VillagerRenderer::new);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!configLoaded
                    && client.gui.screen()
                            instanceof net.minecraft.client.gui.screens.TitleScreen) {
                configLoaded = true;
                com.madreaper16.camperscache.config.ModConfig.load();
            }
        });
    }
}
