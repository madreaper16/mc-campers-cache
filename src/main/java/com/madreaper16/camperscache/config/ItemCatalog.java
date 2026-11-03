package com.madreaper16.camperscache.config;

import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.*;

// Item stacks are not ready at the main menu. Use saved stack sizes.
public final class ItemCatalog {
    private static final Map<Item, Integer> declaredSizes =
            Collections.synchronizedMap(new IdentityHashMap<>());

    public static void record(Item item, int capacity) {
        declaredSizes.put(item, capacity);
    }

    public static int stackSize(Item item) {
        try {
            return item.getDefaultMaxStackSize();
        } catch (NullPointerException unbound) {
            return declaredSizes.getOrDefault(item, 1);
        }
    }

    public static synchronized Map<Identifier, Integer> previewSizes() {
        Map<Identifier, Integer> preview = new HashMap<>();
        for (var item : BuiltInRegistries.ITEM)
            preview.put(BuiltInRegistries.ITEM.getKey(item), declaredSizes.getOrDefault(item, 1));
        return Map.copyOf(preview);
    }
}
