package com.madreaper16.camperscache.config;

import java.util.*;

public final class Settings {
    public int version = 2;
    public String preset = "Generous", basis = "Generous";
    public boolean enabled = true,
            camperEnabled = true,
            guardsEnabled = true,
            oresEnabled = true,
            godArmorEnabled = true;
    public int camperRadius = 20,
            regionChunks = 192,
            destinationDistance = 944,
            shackGuards = 4,
            storehouseGuards = 8;
    public double familyChance = 1, godArmorChance = .015;
    public Map<String, Site> sites = new LinkedHashMap<>();
    public Map<String, Double> storehouseOreWeights = new LinkedHashMap<>();
    public Map<String, Loot> loot = new LinkedHashMap<>();

    public static final class Site {
        public boolean enabled = true;
        public List<String> biomes = new ArrayList<>();
        public Map<String, Double> biomeChances = new LinkedHashMap<>();
    }

    public static final class Loot {
        public boolean enabled = true;
        public List<Pool> pools = new ArrayList<>();
    }

    public static final class Pool {
        public String name = "New pool";
        public boolean enabled = true;
        public int min = 1, max = 1;
        public double chance = 1;
        public List<Entry> entries = new ArrayList<>();
    }

    public static final class Entry {
        public boolean enabled = true;
        public String item = "minecraft:iron_ingot";
        public int min = 1, max = 1, weight = 1;
        public double chance = 1;
        public List<Enchant> enchantments = new ArrayList<>();
    }

    public static final class Enchant {
        public String id = "minecraft:unbreaking";
        public int min = 1, max = 1;
        public double chance = 1;
    }
}
