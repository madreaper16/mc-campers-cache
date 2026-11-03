package com.madreaper16.camperscache.config;

import com.google.gson.*;
import com.madreaper16.camperscache.CampersCache;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SettingsStore {
    public static final List<String> PRESETS = List.of("Scarce", "Average", "Generous", "Abundant");
    public static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("campers_cache.json");
    private static final Settings DEFAULT = resource("config_defaults.json", Settings.class);
    public static final Map<String, JsonObject> ENCHANTMENTS = readEnchantments();
    private static volatile Settings current = copy(DEFAULT);

    private static <T> T resource(String name, Class<T> type) {
        try (var stream =
                SettingsStore.class.getResourceAsStream("/assets/campers_cache/" + name)) {
            if (stream == null) throw new IOException("Missing resource " + name);
            return CampersCache.GSON.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, JsonObject> readEnchantments() {
        Map<String, JsonObject> result = new TreeMap<>();
        resource("enchantments.json", JsonObject.class)
                .entrySet()
                .forEach(e -> result.put(e.getKey(), e.getValue().getAsJsonObject()));
        return Collections.unmodifiableMap(result);
    }

    public static Settings copy(Settings s) {
        Settings result = CampersCache.GSON.fromJson(CampersCache.GSON.toJson(s), Settings.class);
        normalizePoolNames(result);
        return result;
    }

    private static void normalizePoolNames(Settings s) {
        if (s == null || s.loot == null) return;
        for (var entry : s.loot.entrySet()) {
            var loot = entry.getValue();
            var baseline = DEFAULT.loot.get(entry.getKey());
            if (loot == null || loot.pools == null || baseline == null) continue;
            for (var pool : loot.pools) {
                if (pool == null || pool.name == null) continue;
                if (pool.name.equalsIgnoreCase("Diamond gear")) pool.name = "Gear";
                for (var original : baseline.pools)
                    if (pool.name.equalsIgnoreCase(original.name)) {
                        pool.name = original.name;
                        break;
                    }
            }
        }
    }

    public static Settings snapshot() {
        return copy(current);
    }

    // Edit copies. Gameplay reads the saved settings.
    public static Settings active() {
        return current;
    }

    public static Settings preset(String name) {
        require(PRESETS.contains(name), "Unknown preset");
        Settings s = copy(DEFAULT);
        s.preset = name;
        s.basis = name;
        int tier = PRESETS.indexOf(name);
        s.regionChunks = new int[] {320, 240, 192, 160}[tier];
        s.familyChance = new double[] {.45, .75, 1, 1}[tier];
        double multiplier = new double[] {.4, .7, 1, 1.5}[tier];
        for (var pair : s.loot.entrySet())
            for (var pool : pair.getValue().pools) {
                if (tier < 2) {
                    pool.min = Math.max(1, (int) Math.floor(pool.min * multiplier));
                    pool.max = Math.max(pool.min, (int) Math.ceil(pool.max * multiplier));
                }
                if (tier == 3 && pool.name.equals("Gear") && !pair.getKey().equals("camp")) {
                    pool.min++;
                    pool.max++;
                }
                for (var item : pool.entries) {
                    if (item.max > 1) {
                        item.min = Math.max(1, (int) Math.floor(item.min * multiplier));
                        item.max =
                                Math.min(
                                        64,
                                        Math.max(item.min, (int) Math.ceil(item.max * multiplier)));
                    }
                    for (var e : item.enchantments)
                        if (!e.id.endsWith("curse")) {
                            if (tier < 2) {
                                e.min = Math.max(1, e.min - (2 - tier));
                                e.max = Math.max(1, e.max - (2 - tier));
                            }
                            if (tier == 3) {
                                int cap = ENCHANTMENTS.get(e.id).get("max").getAsInt();
                                e.min = Math.min(cap, e.min + 1);
                                e.max = Math.min(cap, e.max + 1);
                            }
                        }
                }
            }
        s.godArmorChance = new double[] {.003, .008, .015, .025}[tier];
        s.storehouseOreWeights.put("diamond", new double[] {5, 15, 30, 40}[tier]);
        s.storehouseOreWeights.put("emerald", new double[] {5, 15, 30, 40}[tier]);
        return s;
    }

    private static void require(boolean c, String message) {
        if (!c) throw new IllegalArgumentException(message);
    }

    private static void probability(double v, String where) {
        require(Double.isFinite(v) && v >= 0 && v <= 1, where + ": chance must be 0–100%.");
    }

    public static void validate(Settings s) {
        require(s != null && s.version == 2, "Unsupported config version.");
        require(
                s.preset != null
                        && (s.preset.equals("Custom") || PRESETS.contains(s.preset))
                        && PRESETS.contains(s.basis),
                "Unknown preset.");
        require(
                s.camperRadius >= 20 && s.camperRadius <= 60,
                "Camper radius must be 20–60 blocks.");
        require(
                s.destinationDistance >= 256 && s.destinationDistance <= 944,
                "Destination distance must be 256–944 blocks; template margins preserve the 1000-block limit.");
        require(
                s.regionChunks >= 144
                        && s.regionChunks <= 1024
                        && s.regionChunks * 16 >= 2 * s.destinationDistance + 384,
                "Region spacing must be 144–1024 chunks, with room for both destinations.");
        probability(s.familyChance, "Camp frequency");
        probability(s.godArmorChance, "God Armor");
        require(
                s.shackGuards >= 0
                        && s.shackGuards <= 32
                        && s.storehouseGuards >= 0
                        && s.storehouseGuards <= 32,
                "Guard counts must be 0–32.");
        require(
                ModConfig.valid(s.storehouseOreWeights),
                "Ore weights must be nonnegative, with a positive total.");
        require(
                s.sites != null && s.sites.keySet().equals(DEFAULT.sites.keySet()),
                "Missing structure settings.");
        for (var pair : s.sites.entrySet()) {
            var site = pair.getValue();
            require(
                    site != null && site.biomes != null && site.biomeChances != null,
                    "Missing biome settings.");
            for (String biome : site.biomes)
                require(
                        biome != null && Identifier.tryParse(biome) != null,
                        "Invalid biome ID: " + biome);
            site.biomeChances.forEach((id, chance) -> {
                require(Identifier.tryParse(id) != null && chance != null, "Invalid biome chance");
                probability(chance, id);
            });
        }
        require(
                s.loot != null && s.loot.keySet().equals(DEFAULT.loot.keySet()),
                "Missing loot locations.");
        for (var pair : s.loot.entrySet()) {
            var loot = pair.getValue();
            require(
                    loot != null && loot.pools != null && loot.pools.size() <= 64,
                    "Each location supports up to 64 pools.");
            int slots =
                    pair.getKey().equals("hidden_storehouse")
                                    && s.godArmorEnabled
                                    && s.godArmorChance > 0
                            ? 1
                            : 0;
            for (var pool : loot.pools) {
                require(
                        pool != null
                                && pool.name != null
                                && pool.name.length() <= 80
                                && pool.entries != null,
                        "Invalid loot pool.");
                require(
                        pool.min >= 0 && pool.max >= pool.min && pool.max <= 27,
                        "Pool rolls must be 0–27, minimum ≤ maximum.");
                probability(pool.chance, pool.name);
                require(pool.entries.size() <= 512, "A pool supports up to 512 items.");
                int maxSlots = 0;
                for (var item : pool.entries) {
                    require(item != null && item.item != null, "Missing item.");
                    var id = Identifier.tryParse(item.item);
                    require(
                            id != null
                                    && BuiltInRegistries.ITEM.containsKey(id)
                                    && BuiltInRegistries.ITEM.getValue(id) != Items.AIR,
                            "Unknown item: " + item.item);
                    require(
                            item.min >= 1 && item.max >= item.min && item.max <= 64,
                            "Item quantity must be 1–64, minimum ≤ maximum: " + item.item);
                    require(
                            item.weight >= 1 && item.weight <= 10000,
                            "Item weight must be 1–10000.");
                    probability(item.chance, item.item);
                    require(
                            item.enchantments != null && item.enchantments.size() <= 32,
                            "Too many enchantments.");
                    Set<String> seen = new HashSet<>();
                    for (var e : item.enchantments) {
                        require(
                                e != null && ENCHANTMENTS.containsKey(e.id) && seen.add(e.id),
                                "Unknown or duplicate enchantment.");
                        var definition = ENCHANTMENTS.get(e.id);
                        int max = definition.get("max").getAsInt();
                        require(
                                e.min >= 1 && e.max >= e.min && e.max <= max,
                                "Use vanilla levels 1–"
                                        + max
                                        + " for "
                                        + e.id
                                        + ". Protection V is reserved for God Armor.");
                        probability(e.chance, e.id);
                        if (id.getNamespace().equals("minecraft")
                                && !item.item.equals("minecraft:book")
                                && !item.item.equals("minecraft:enchanted_book"))
                            require(
                                    definition.getAsJsonArray("items").asList().stream()
                                            .anyMatch(v -> v.getAsString().equals(item.item)),
                                    e.id + " cannot be applied to " + item.item + " in vanilla.");
                        for (String previous : seen)
                            if (!previous.equals(e.id))
                                require(
                                        definition.getAsJsonArray("exclusive").asList().stream()
                                                        .noneMatch(v ->
                                                                v.getAsString().equals(previous))
                                                && ENCHANTMENTS
                                                        .get(previous)
                                                        .getAsJsonArray("exclusive")
                                                        .asList()
                                                        .stream()
                                                        .noneMatch(
                                                                v -> v.getAsString().equals(e.id)),
                                        "Incompatible enchantments: " + previous + " and " + e.id);
                    }
                    if (item.enabled && item.chance > 0) {
                        int size =
                                item.item.equals("minecraft:book") && !item.enchantments.isEmpty()
                                        ? 1
                                        : ItemCatalog.stackSize(
                                                BuiltInRegistries.ITEM.getValue(id));
                        maxSlots = Math.max(maxSlots, (item.max + size - 1) / size);
                    }
                }
                if (pool.enabled && pool.chance > 0) slots += pool.max * maxSlots;
            }
            require(
                    !loot.enabled || slots <= 27,
                    pair.getKey()
                            + ": potential loot needs "
                            + slots
                            + " slots; each chest half has 27. Reduce rolls or quantities.");
        }
    }

    public static synchronized void save(Settings draft) throws IOException {
        Settings next = copy(draft);
        validate(next);
        classify(next);
        atomicWrite(FILE, CampersCache.GSON.toJson(next));
        current = next;
    }

    private static void classify(Settings next) {
        var a = CampersCache.GSON.toJsonTree(next).getAsJsonObject();
        var b = CampersCache.GSON.toJsonTree(preset(next.basis)).getAsJsonObject();
        a.remove("preset");
        b.remove("preset");
        next.preset = a.equals(b) ? next.basis : "Custom";
    }

    private static void atomicWrite(Path path, String json) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = Files.createTempFile(path.getParent(), "cache-", ".tmp");
        try {
            Files.writeString(temp, json);
            try {
                Files.move(
                        temp,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void load() {
        try {
            if (!Files.exists(FILE)) {
                save(copy(DEFAULT));
                return;
            }
            JsonObject json = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            Settings s;
            if (!json.has("version")) {
                s = copy(DEFAULT);
                if (json.has("storehouseOreWeights"))
                    for (String ore : ModConfig.ORES)
                        s.storehouseOreWeights.put(
                                ore,
                                json.getAsJsonObject("storehouseOreWeights")
                                        .get(ore)
                                        .getAsDouble());
            } else s = CampersCache.GSON.fromJson(json, Settings.class);
            s = copy(s);
            validate(s);
            classify(s);
            current = copy(s);
        } catch (Exception e) {
            CampersCache.LOGGER.error(
                    "Invalid Camper's Cache config; retaining last valid settings. Original file preserved.",
                    e);
        }
    }

    public static Path profilePath(String name) {
        require(
                name != null && name.matches("[a-zA-Z0-9_-]{1,64}"),
                "Profile name: use 1–64 letters, digits, hyphens or underscores.");
        return FILE.getParent().resolve("campers_cache_profiles").resolve(name + ".json");
    }

    public static void exportProfile(String name, Settings s) throws IOException {
        validate(s);
        Path path = profilePath(name);
        require(!Files.exists(path), "Profile already exists; choose a new name.");
        atomicWrite(path, CampersCache.GSON.toJson(s));
    }

    public static Settings importProfile(String name) throws IOException {
        Settings s =
                copy(
                        CampersCache.GSON.fromJson(
                                Files.readString(profilePath(name)), Settings.class));
        validate(s);
        return s;
    }
}
