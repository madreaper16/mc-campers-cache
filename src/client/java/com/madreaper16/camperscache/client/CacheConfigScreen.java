package com.madreaper16.camperscache.client;

import com.google.gson.*;
import com.madreaper16.camperscache.CampersCache;
import com.madreaper16.camperscache.config.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;

import java.util.*;
import java.util.function.Consumer;

// Keep edits until Save, even when changing pages.
public final class CacheConfigScreen extends Screen {
    private final Screen parent;
    private JsonObject draft = json(SettingsStore.snapshot()), saved = draft.deepCopy();
    private final JsonObject defaults = json(SettingsStore.preset("Generous"));
    private final Deque<Route> history = new ArrayDeque<>();
    private Route route = new Route(List.of(), "Home");
    private String query = "",
            status = "Yellow: unsaved. Green: saved customization. White: default.",
            profile = "my_setup";
    private int page;
    private boolean editable;
    private final Map<String, String> invalid = new HashMap<>(), raw = new HashMap<>();
    private List<String> choices;
    private Consumer<String> pick;
    private final List<Runnable> colors = new ArrayList<>();

    private record Route(List<String> path, String group) {}

    private record Row(String label, List<String> path) {}

    public CacheConfigScreen(Screen parent) {
        super(Component.literal("Camper's Cache — Configuration"));
        this.parent = parent;
    }

    private static JsonObject json(Settings settings) {
        return CampersCache.GSON.toJsonTree(settings).getAsJsonObject();
    }

    private static Settings settings(JsonObject json) {
        return CampersCache.GSON.fromJson(json, Settings.class);
    }

    private static List<String> child(List<String> path, String key) {
        var p = new ArrayList<>(path);
        p.add(key);
        return p;
    }

    private static String id(List<String> path) {
        return String.join("/", path);
    }

    private static JsonElement get(JsonElement root, List<String> path) {
        try {
            for (String key : path) {
                if (root == null) return null;
                root =
                        root.isJsonArray()
                                ? root.getAsJsonArray().get(Integer.parseInt(key))
                                : root.getAsJsonObject().get(key);
            }
            return root;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void set(List<String> path, JsonElement value) {
        if (path.isEmpty()) {
            draft = value.getAsJsonObject();
            return;
        }
        var owner = get(draft, path.subList(0, path.size() - 1));
        String key = path.getLast();
        if (owner.isJsonArray()) owner.getAsJsonArray().set(Integer.parseInt(key), value);
        else owner.getAsJsonObject().add(key, value);
    }

    private boolean dirty() {
        return !draft.equals(saved) || !invalid.isEmpty();
    }

    private int color(List<String> path) {
        if (invalid.containsKey(id(path)) || !Objects.equals(get(draft, path), get(saved, path)))
            return 0xFFFFFF55;
        if (!Objects.equals(get(saved, path), get(defaults, path))) return 0xFF55FF55;
        return 0xFFFFFFFF;
    }

    private Component label(String text, List<String> path) {
        return Component.literal(text).withColor(color(path) & 0xFFFFFF);
    }

    private void changed() {
        var base = json(SettingsStore.preset(draft.get("basis").getAsString()));
        var value = draft.deepCopy();
        value.remove("preset");
        base.remove("preset");
        draft.addProperty(
                "preset", value.equals(base) ? draft.get("basis").getAsString() : "Custom");
    }

    private void refresh() {
        rebuildWidgets();
    }

    private void replaceDraft(JsonObject replacement) {
        draft = replacement;
        raw.clear();
        invalid.clear();
        choices = null;
        pick = null;
        query = "";
        page = 0;
        history.clear();
        route = new Route(List.of(), "Home");
        refresh();
    }

    private void open(List<String> path, String group) {
        history.push(route);
        route = new Route(path, group);
        query = "";
        page = 0;
        refresh();
    }

    private void back() {
        choices = null;
        pick = null;
        if (!history.isEmpty()) route = history.pop();
        else route = new Route(List.of(), "Home");
        query = "";
        page = 0;
        refresh();
    }

    private boolean isLootLocation() {
        return route.path.size() == 2 && route.path.getFirst().equals("loot");
    }

    private boolean isLootGroup() {
        return route.path.size() == 3
                && route.path.getFirst().equals("loot")
                && route.path.getLast().startsWith("@");
    }

    private List<String> chests(String location) {
        return location.equals("hidden_storehouse")
                ? List.of("hidden_storehouse", "storehouse_supplies")
                : List.of(location);
    }

    private boolean gearPool(JsonObject pool) {
        return pool.get("name").getAsString().toLowerCase(Locale.ROOT).contains("gear");
    }

    private JsonArray rewardData(JsonObject root, String location, Boolean gear) {
        JsonArray data = new JsonArray();
        for (String chest : chests(location)) {
            var source = root.getAsJsonObject("loot").getAsJsonObject(chest);
            if (gear == null) {
                data.add(source);
                continue;
            }
            JsonObject group = new JsonObject();
            group.add("enabled", source.get("enabled"));
            JsonArray pools = new JsonArray();
            for (var value : source.getAsJsonArray("pools"))
                if (gearPool(value.getAsJsonObject()) == gear) pools.add(value);
            group.add("pools", pools);
            data.add(group);
        }
        return data;
    }

    private int rewardColor(String location, Boolean gear) {
        if (!rewardData(draft, location, gear).equals(rewardData(saved, location, gear)))
            return 0xFFFFFF55;
        if (!rewardData(saved, location, gear).equals(rewardData(defaults, location, gear)))
            return 0xFF55FF55;
        return 0xFFFFFFFF;
    }

    private void lootLocation(int x, int w) {
        String location = route.path.get(1);
        int y = 75;
        if ("Enabled".toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
            var toggle =
                    button(
                            "Enabled",
                            x,
                            y,
                            w,
                            () -> {
                                boolean all =
                                        chests(location).stream()
                                                .allMatch(name ->
                                                        draft.getAsJsonObject("loot")
                                                                .getAsJsonObject(name)
                                                                .get("enabled")
                                                                .getAsBoolean());
                                for (String name : chests(location))
                                    draft.getAsJsonObject("loot")
                                            .getAsJsonObject(name)
                                            .addProperty("enabled", !all);
                                changed();
                                refresh();
                            });
            toggle.active = editable;
            colors.add(() -> {
                long enabled =
                        chests(location).stream()
                                .filter(name ->
                                        draft.getAsJsonObject("loot")
                                                .getAsJsonObject(name)
                                                .get("enabled")
                                                .getAsBoolean())
                                .count();
                toggle.setMessage(
                        Component.literal(
                                        "Enabled: "
                                                + (enabled == 0
                                                        ? "OFF"
                                                        : enabled == chests(location).size()
                                                                ? "ON"
                                                                : "Mixed"))
                                .withColor(rewardColor(location, null) & 0xFFFFFF));
            });
            y += 24;
        }
        for (String name : List.of("Items", "Gear")) {
            if (!name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
            boolean gear = name.equals("Gear");
            var b =
                    button(
                            name + "  ›",
                            x,
                            y,
                            w,
                            () -> open(child(route.path, gear ? "@gear" : "@items"), name));
            colors.add(() ->
                    b.setMessage(
                            Component.literal(name + "  ›")
                                    .withColor(rewardColor(location, gear) & 0xFFFFFF)));
            y += 24;
        }
    }

    private void lootGroup(int x, int w) {
        String location = route.path.get(1);
        boolean gear = route.path.getLast().equals("@gear");
        List<Row> rows = new ArrayList<>();
        for (String chest : chests(location)) {
            var pools =
                    draft.getAsJsonObject("loot").getAsJsonObject(chest).getAsJsonArray("pools");
            for (int p = 0; p < pools.size(); p++) {
                var pool = pools.get(p).getAsJsonObject();
                if (gearPool(pool) != gear) continue;
                var entries = pool.getAsJsonArray("entries");
                for (int i = 0; i < entries.size(); i++) {
                    var entry = entries.get(i).getAsJsonObject();
                    String title = entryName(entry);
                    if ((title + " " + entry)
                            .toLowerCase(Locale.ROOT)
                            .contains(query.toLowerCase(Locale.ROOT)))
                        rows.add(
                                new Row(
                                        title,
                                        List.of(
                                                "loot",
                                                chest,
                                                "pools",
                                                Integer.toString(p),
                                                "entries",
                                                Integer.toString(i))));
                }
            }
        }
        int count = Math.max(1, (height - 158) / 24);
        page = Math.min(page, Math.max(0, (rows.size() - 1) / count));
        int start = page * count;
        for (int i = start; i < Math.min(rows.size(), start + count); i++)
            row(rows.get(i), x, 75 + (i - start) * 24, w);
        var add =
                button(
                        "+ Add Item",
                        x + w - 100,
                        height - 77,
                        100,
                        () ->
                                picker(
                                        items(),
                                        selected -> {
                                            var pools =
                                                    draft.getAsJsonObject("loot")
                                                            .getAsJsonObject(location)
                                                            .getAsJsonArray("pools");
                                            JsonObject pool = null;
                                            if (gear)
                                                for (var value : pools)
                                                    if (gearPool(value.getAsJsonObject())
                                                            && value.getAsJsonObject()
                                                                    .get("enabled")
                                                                    .getAsBoolean()) {
                                                        pool = value.getAsJsonObject();
                                                        break;
                                                    }
                                            if (pool == null) {
                                                var added = new Settings.Pool();
                                                added.name = gear ? "Gear" : "Items";
                                                pool =
                                                        CampersCache.GSON
                                                                .toJsonTree(added)
                                                                .getAsJsonObject();
                                                pools.add(pool);
                                            }
                                            var entry = new Settings.Entry();
                                            entry.item = selected;
                                            pool.getAsJsonArray("entries")
                                                    .add(CampersCache.GSON.toJsonTree(entry));
                                            changed();
                                            refresh();
                                        }));
        add.active = editable;
        pages(x, w, rows.size(), count);
    }

    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(
                Button.builder(Component.literal(text), b -> action.run())
                        .bounds(x, y, w, 20)
                        .build());
    }

    private void confirm(String title, String message, Runnable action) {
        minecraft.gui.setScreen(
                new ConfirmScreen(
                        yes -> {
                            minecraft.gui.setScreen(this);
                            if (yes) action.run();
                        },
                        Component.literal(title),
                        Component.literal(message)));
    }

    @Override
    protected void init() {
        editable = minecraft.level == null || minecraft.hasSingleplayerServer();
        colors.clear();
        rowLabels.clear();
        int left = Math.max(12, width / 2 - 280), wide = Math.min(560, width - 24);
        EditBox search =
                addRenderableWidget(
                        new EditBox(
                                font, left, 45, wide, 20, Component.literal("Search This Page")));
        search.setMaxLength(160);
        search.setValue(query);
        search.setHint(Component.literal("Search…"));
        search.setResponder(value -> {
            query = value;
            page = 0;
            refresh();
            setFocused(
                    children().stream().filter(c -> c instanceof EditBox).findFirst().orElse(null));
        });
        if (choices != null) {
            renderChoices(left, wide);
            footer(left, wide);
            return;
        }
        if (route.group.equals("Home")) {
            String[] groups =
                    java.util.stream.Stream.of(
                                    "Features And Camper",
                                    "Generation",
                                    "Loot",
                                    "Ores",
                                    "God Armor",
                                    "Presets",
                                    "Profiles")
                            .filter(group ->
                                    group.toLowerCase(Locale.ROOT)
                                            .contains(query.toLowerCase(Locale.ROOT)))
                            .toArray(String[]::new);
            int count = Math.max(1, (height - 158) / 24), start = page * count;
            for (int i = start; i < Math.min(groups.length, start + count); i++) {
                String group = groups[i];
                button(
                        group,
                        left,
                        75 + (i - start) * 24,
                        wide,
                        () ->
                                open(
                                        group.equals("Loot")
                                                ? List.of("loot")
                                                : group.equals("Ores")
                                                        ? List.of("storehouseOreWeights")
                                                        : List.of(),
                                        group));
            }
            pages(left, wide, groups.length, count);
            footer(left, wide);
            return;
        }
        if (route.group.equals("Presets")) {
            var names =
                    SettingsStore.PRESETS.stream()
                            .filter(name ->
                                    name.toLowerCase(Locale.ROOT)
                                            .contains(query.toLowerCase(Locale.ROOT)))
                            .toList();
            int count = Math.max(1, (height - 158) / 24);
            int start = page * count;
            for (int i = start; i < Math.min(names.size(), start + count); i++) {
                String name = names.get(i);
                var b =
                        button(
                                name
                                        + (draft.get("preset").getAsString().equals(name)
                                                ? " (selected)"
                                                : ""),
                                left,
                                75 + (i - start) * 24,
                                wide,
                                () ->
                                        confirm(
                                                "Replace configuration?",
                                                "This replaces every setting and custom loot list in your draft. Save is still required.",
                                                () -> {
                                                    status =
                                                            "Preset loaded into draft. Review, then Save.";
                                                    replaceDraft(json(SettingsStore.preset(name)));
                                                }));
                b.active = editable;
            }
            pages(left, wide, names.size(), count);
            footer(left, wide);
            return;
        }
        if (route.group.equals("Profiles")) {
            profiles(left, wide);
            footer(left, wide);
            return;
        }
        if (isLootLocation()) {
            lootLocation(left, wide);
            footer(left, wide);
            return;
        }
        if (isLootGroup()) {
            lootGroup(left, wide);
            footer(left, wide);
            return;
        }
        var rows =
                rows().stream()
                        .filter(row ->
                                (row.label + " " + get(draft, row.path))
                                        .toLowerCase(Locale.ROOT)
                                        .contains(query.toLowerCase(Locale.ROOT)))
                        .toList();
        int count = Math.max(1, (height - 158) / 24);
        page = Math.min(page, Math.max(0, (rows.size() - 1) / count));
        int start = page * count;
        for (int i = start; i < Math.min(rows.size(), start + count); i++)
            row(rows.get(i), left, 75 + (i - start) * 24, wide);
        JsonElement node = get(draft, route.path);
        if (node != null && node.isJsonArray() && editable) {
            String field = route.path.getLast();
            if (List.of("enchantments", "biomes").contains(field))
                button("+ Add", left + wide - 65, height - 77, 65, () -> add(field));
        }
        if (route.path.size() > 0 && route.path.getLast().equals("biomeChances") && editable)
            button(
                    "+ Biome",
                    left + wide - 80,
                    height - 77,
                    80,
                    () ->
                            picker(
                                    biomes(),
                                    value -> {
                                        get(draft, route.path)
                                                .getAsJsonObject()
                                                .addProperty(value, 1.0);
                                        changed();
                                        refresh();
                                    }));
        pages(left, wide, rows.size(), count);
        footer(left, wide);
    }

    private List<Row> rows() {
        List<Row> result = new ArrayList<>();
        List<String> keys = null;
        if (route.path.isEmpty())
            keys =
                    switch (route.group) {
                        case "Features And Camper" ->
                                List.of(
                                        "enabled",
                                        "camperEnabled",
                                        "guardsEnabled",
                                        "oresEnabled",
                                        "camperRadius");
                        case "Generation" ->
                                List.of(
                                        "regionChunks",
                                        "destinationDistance",
                                        "familyChance",
                                        "shackGuards",
                                        "storehouseGuards",
                                        "sites");
                        case "God Armor" -> List.of("godArmorEnabled", "godArmorChance");
                        default -> List.of();
                    };
        if (keys != null) {
            for (String key : keys) result.add(new Row(pretty(key), List.of(key)));
            return result;
        }
        if (route.path.equals(List.of("loot"))) {
            for (String name : List.of("camp", "supply_shack", "hidden_storehouse"))
                result.add(new Row(pretty(name), List.of("loot", name)));
            return result;
        }
        var node = get(draft, route.path);
        if (node == null) return result;
        if (node.isJsonObject())
            for (var pair : node.getAsJsonObject().entrySet()) {
                if (pair.getKey().equals("weight") && route.path.contains("entries")) continue;
                String label = pretty(pair.getKey());
                if (route.path.contains("entries") && !route.path.contains("enchantments")) {
                    if (pair.getKey().equals("min")) label = "Minimum Quantity";
                    if (pair.getKey().equals("max")) label = "Maximum Quantity";
                }
                result.add(new Row(label, child(route.path, pair.getKey())));
            }
        else if (node.isJsonArray()) {
            int index = 0;
            for (var value : node.getAsJsonArray()) {
                String title = "#" + (index + 1);
                if (value.isJsonPrimitive()) title = MenuNames.registry(value.getAsString());
                else if (value.isJsonObject()) {
                    var object = value.getAsJsonObject();
                    title =
                            object.has("name")
                                    ? pretty(object.get("name").getAsString())
                                    : object.has("item")
                                            ? entryName(object)
                                            : object.has("id")
                                                    ? MenuNames.registry(
                                                            object.get("id").getAsString())
                                                    : title;
                }
                result.add(new Row(title, child(route.path, Integer.toString(index++))));
            }
        }
        return result;
    }

    private static String pretty(String name) {
        return switch (name) {
            case "camp" -> "Camp";
            case "supply_shack" -> "Supply House";
            case "hidden_storehouse", "storehouse_supplies" -> "Storehouse";
            case "sites" -> "Locations";
            case "pools" -> "Rewards";
            case "entries" -> "Items";
            case "id" -> "Enchantment";
            case "name" -> "Name";
            case "min" -> "Minimum";
            case "max" -> "Maximum";
            case "chance" -> "Chance (%)";
            case "familyChance" -> "Camp Frequency (%)";
            case "godArmorChance" -> "God Armor Chance (%)";
            case "regionChunks" -> "Region Spacing (Chunks)";
            case "destinationDistance" -> "Destination Distance (Blocks)";
            case "camperRadius" -> "Wander / Chase Radius (20–60)";
            case "biomeChances" -> "Per-Biome Frequency (%)";
            case "biomes" -> "Allowed Biomes";
            case "Diamond gear", "Diamond Gear" -> "Gear";
            default -> name.contains(":") ? MenuNames.registry(name) : MenuNames.title(name);
        };
    }

    private boolean percent(List<String> path) {
        String key = path.getLast();
        return key.equals("chance") || key.endsWith("Chance") || path.contains("biomeChances");
    }

    private void row(Row row, int x, int y, int w) {
        var path = row.path;
        var value = get(draft, path);
        boolean removable =
                path.size() > 0
                        && (get(draft, path.subList(0, path.size() - 1)).isJsonArray()
                                || (path.size() > 1
                                        && path.get(path.size() - 2).equals("biomeChances")));
        int controls = removable ? 48 : 22;
        Button reset =
                button(
                        "↶",
                        x + w - 20,
                        y,
                        20,
                        () -> {
                            var original = get(defaults, path);
                            if (original != null) {
                                set(path, original.deepCopy());
                                invalid.remove(id(path));
                                raw.remove(id(path));
                                changed();
                                refresh();
                            } else status = "No default for this custom entry; use Remove.";
                        });
        reset.active = editable;
        reset.setTooltip(
                Tooltip.create(Component.literal("Restore this field from Generous defaults")));
        if (removable) {
            var remove =
                    button(
                            "−",
                            x + w - 46,
                            y,
                            22,
                            () ->
                                    confirm(
                                            "Remove entry?",
                                            "Remove this entry from the draft?",
                                            () -> {
                                                var owner =
                                                        get(
                                                                draft,
                                                                path.subList(0, path.size() - 1));
                                                if (owner.isJsonArray())
                                                    owner.getAsJsonArray()
                                                            .remove(
                                                                    Integer.parseInt(
                                                                            path.getLast()));
                                                else owner.getAsJsonObject().remove(path.getLast());
                                                raw.clear();
                                                invalid.clear();
                                                changed();
                                                refresh();
                                            }));
            remove.active = editable;
        }
        if (value.isJsonObject() || value.isJsonArray()) {
            var b = button(row.label + "  ›", x, y, w - controls - 4, () -> open(path, row.label));
            colors.add(() -> b.setMessage(label(row.label + "  ›", path)));
            return;
        }
        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            var b =
                    button(
                            row.label + ": " + (primitive.getAsBoolean() ? "ON" : "OFF"),
                            x,
                            y,
                            w - controls - 4,
                            () -> {
                                set(path, new JsonPrimitive(!get(draft, path).getAsBoolean()));
                                changed();
                                refresh();
                            });
            b.active = editable;
            colors.add(() ->
                    b.setMessage(
                            label(
                                    row.label
                                            + ": "
                                            + (get(draft, path).getAsBoolean() ? "ON" : "OFF"),
                                    path)));
            return;
        }
        boolean select =
                path.getLast().equals("item")
                        || path.getLast().equals("id")
                        || path.contains("biomes");
        if (select) {
            var b =
                    button(
                            selectionName(path, primitive.getAsString()),
                            x,
                            y,
                            w - controls - 4,
                            () ->
                                    picker(
                                            path.getLast().equals("item")
                                                    ? items()
                                                    : path.getLast().equals("id")
                                                            ? new ArrayList<>(
                                                                    SettingsStore.ENCHANTMENTS
                                                                            .keySet())
                                                            : biomes(),
                                            selected -> {
                                                set(path, new JsonPrimitive(selected));
                                                if (path.getLast().equals("id")) {
                                                    var obj =
                                                            get(
                                                                            draft,
                                                                            path.subList(
                                                                                    0,
                                                                                    path.size()
                                                                                            - 1))
                                                                    .getAsJsonObject();
                                                    obj.addProperty("min", 1);
                                                    obj.addProperty("max", 1);
                                                }
                                                changed();
                                                refresh();
                                            }));
            b.active = editable;
            colors.add(() ->
                    b.setMessage(
                            label(
                                    font.plainSubstrByWidth(
                                            selectionName(path, get(draft, path).getAsString()),
                                            w - controls - 14),
                                    path)));
            return;
        }
        int fieldWidth = Math.min(145, w / 3);
        var box =
                addRenderableWidget(
                        new EditBox(
                                font,
                                x + w - controls - fieldWidth - 4,
                                y,
                                fieldWidth,
                                20,
                                Component.literal(row.label)));
        box.setMaxLength(primitive.isNumber() ? 16 : 80);
        String text =
                primitive.isNumber() && percent(path)
                        ? Double.toString(primitive.getAsDouble() * 100)
                        : primitive.getAsString();
        box.setValue(raw.getOrDefault(id(path), text));
        box.setEditable(editable);
        box.setResponder(input -> {
            raw.put(id(path), input);
            try {
                JsonElement next;
                if (primitive.isNumber()) {
                    double n = Double.parseDouble(input);
                    if (!Double.isFinite(n)) throw new NumberFormatException();
                    boolean fractional = percent(path) || path.contains("storehouseOreWeights");
                    if (!fractional && n != Math.rint(n)) throw new NumberFormatException();
                    next = new JsonPrimitive(percent(path) ? n / 100 : n);
                } else next = new JsonPrimitive(input);
                set(path, next);
                invalid.remove(id(path));
                changed();
            } catch (RuntimeException e) {
                invalid.put(id(path), row.label + ": enter a valid number.");
            }
        });
        box.setTooltip(Tooltip.create(Component.literal(help(path))));
        colors.add(() -> box.setTextColor(color(path)));
        // Match label colors to their fields.
        rowLabels.add(
                new DrawLabel(
                        font.plainSubstrByWidth(row.label, w - controls - fieldWidth - 12),
                        x,
                        y + 6,
                        path));
    }

    private record DrawLabel(String text, int x, int y, List<String> path) {}

    private final List<DrawLabel> rowLabels = new ArrayList<>();

    @Override
    protected void rebuildWidgets() {
        rowLabels.clear();
        super.rebuildWidgets();
    }

    private String help(List<String> path) {
        String key = path.getLast();
        return switch (key) {
            case "chance", "familyChance", "godArmorChance" ->
                    "0–100%. Item and enchantment chances are independent.";
            case "weight" ->
                    "Relative selection weight among enabled items. A failed item chance leaves that draw empty.";
            case "regionChunks" ->
                    "144–1024 chunks. New terrain only; lower numbers mean more camps.";
            case "destinationDistance" ->
                    "256–944 blocks from camp. Destinations stay on opposite sides; terrain safety checks always apply.";
            case "min", "max" ->
                    path.contains("enchantments")
                            ? "Vanilla enchantment levels only. God Armor is the sole exception."
                            : "Quantity: 1–64 items. Maximum must be at least minimum.";
            default ->
                    "Changes stay in the draft until Save. Yellow is unsaved; green is a saved customization.";
        };
    }

    private void add(String field) {
        var array = get(draft, route.path).getAsJsonArray();
        switch (field) {
            case "entries" -> {
                picker(
                        items(),
                        value -> {
                            var e = new Settings.Entry();
                            e.item = value;
                            array.add(CampersCache.GSON.toJsonTree(e));
                            changed();
                            refresh();
                        });
                return;
            }
            case "enchantments" -> {
                picker(
                        new ArrayList<>(SettingsStore.ENCHANTMENTS.keySet()),
                        value -> {
                            var e = new Settings.Enchant();
                            e.id = value;
                            array.add(CampersCache.GSON.toJsonTree(e));
                            changed();
                            refresh();
                        });
                return;
            }
            case "biomes" -> {
                picker(
                        biomes(),
                        value -> {
                            if (array.asList().stream()
                                    .noneMatch(e -> e.getAsString().equals(value)))
                                array.add(value);
                            changed();
                            refresh();
                        });
                return;
            }
        }
        changed();
        refresh();
    }

    private List<String> items() {
        List<String> result = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM)
            if (item != Items.AIR) result.add(BuiltInRegistries.ITEM.getKey(item).toString());
        Collections.sort(result);
        return result;
    }

    private List<String> biomes() {
        Set<String> result = new TreeSet<>();
        for (var site : SettingsStore.preset("Generous").sites.values()) result.addAll(site.biomes);
        result.addAll(
                List.of(
                        "minecraft:swamp",
                        "minecraft:mangrove_swamp",
                        "minecraft:mushroom_fields",
                        "minecraft:ocean",
                        "minecraft:river",
                        "minecraft:deep_ocean",
                        "minecraft:warm_ocean",
                        "minecraft:lukewarm_ocean",
                        "minecraft:cold_ocean",
                        "minecraft:frozen_ocean",
                        "minecraft:frozen_river"));
        if (minecraft.level != null)
            minecraft
                    .level
                    .registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .keySet()
                    .forEach(key -> result.add(key.toString()));
        return new ArrayList<>(result);
    }

    private String itemName(String id) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (item == null) return MenuNames.registry(id);
        String key = item.getDescriptionId();
        String translated = Component.translatable(key).getString();
        return translated.equals(key) ? MenuNames.registry(id) : translated;
    }

    private String selectionName(List<String> path, String value) {
        return path.getLast().equals("item") ? itemName(value) : MenuNames.registry(value);
    }

    private String entryName(JsonObject entry) {
        String name = itemName(entry.get("item").getAsString());
        if (entry.get("item").getAsString().endsWith(":book")
                || entry.get("item").getAsString().endsWith(":enchanted_book")) {
            var enchants = entry.getAsJsonArray("enchantments");
            if (enchants != null && !enchants.isEmpty())
                name +=
                        " — "
                                + MenuNames.registry(
                                        enchants.get(0).getAsJsonObject().get("id").getAsString());
        }
        return name;
    }

    private String choiceName(String key) {
        return route.path.contains("biomes")
                        || route.path.contains("biomeChances")
                        || SettingsStore.ENCHANTMENTS.containsKey(key)
                ? MenuNames.registry(key)
                : itemName(key);
    }

    private void picker(List<String> options, Consumer<String> action) {
        choices = options;
        pick = action;
        query = "";
        page = 0;
        refresh();
    }

    private void renderChoices(int x, int w) {
        var matches =
                choices.stream()
                        .filter(key ->
                                (key + " " + choiceName(key))
                                        .toLowerCase(Locale.ROOT)
                                        .contains(query.toLowerCase(Locale.ROOT)))
                        .toList();
        int count = Math.max(1, (height - 158) / 24);
        page = Math.min(page, Math.max(0, (matches.size() - 1) / count));
        int start = page * count;
        for (int i = start; i < Math.min(matches.size(), start + count); i++) {
            String key = matches.get(i);
            String name = choiceName(key);
            if (SettingsStore.ENCHANTMENTS.containsKey(key))
                name += " (Max " + SettingsStore.ENCHANTMENTS.get(key).get("max").getAsInt() + ")";
            button(
                    font.plainSubstrByWidth(name, w - 12),
                    x,
                    75 + (i - start) * 24,
                    w,
                    () -> {
                        var action = pick;
                        choices = null;
                        pick = null;
                        query = "";
                        page = 0;
                        action.accept(key);
                    });
        }
        // Allow custom biome IDs before a world is loaded.
        if (choices != null
                && !query.isBlank()
                && Identifier.tryParse(query) != null
                && !matches.contains(query)
                && (route.path.contains("biomes") || route.path.contains("biomeChances")))
            button(
                    "Add Custom Biome",
                    x + w - 220,
                    height - 77,
                    220,
                    () -> {
                        String key = query;
                        var action = pick;
                        choices = null;
                        pick = null;
                        query = "";
                        page = 0;
                        action.accept(key);
                    });
        pages(x, w, matches.size(), count);
    }

    private void profiles(int x, int w) {
        var field =
                addRenderableWidget(
                        new EditBox(font, x, 80, w, 20, Component.literal("Profile Name")));
        field.setValue(profile);
        field.setMaxLength(64);
        field.setResponder(value -> profile = value);
        field.setHint(Component.literal("Profile name (letters, digits, _ and -)"));
        button(
                "Export Draft",
                x,
                108,
                w / 2 - 2,
                () -> {
                    try {
                        if (!invalid.isEmpty())
                            throw new IllegalArgumentException("Fix invalid fields first.");
                        SettingsStore.exportProfile(profile, settings(draft));
                        status = "Exported config/campers_cache_profiles/" + profile + ".json";
                    } catch (Exception e) {
                        status = MenuNames.message(e.getMessage());
                    }
                });
        var b =
                button(
                        "Import Into Draft",
                        x + w / 2 + 2,
                        108,
                        w / 2 - 2,
                        () -> {
                            try {
                                var loaded = SettingsStore.importProfile(profile);
                                confirm(
                                        "Replace configuration?",
                                        "Import replaces the complete draft, including custom loot. Save is required to apply it.",
                                        () -> {
                                            status = "Imported into draft. Review, then Save.";
                                            replaceDraft(json(loaded));
                                            changed();
                                        });
                            } catch (Exception e) {
                                status = "Import failed: " + MenuNames.message(e.getMessage());
                            }
                        });
        b.active = editable;
    }

    private void pages(int x, int w, int size, int count) {
        button(
                "‹",
                x,
                height - 77,
                28,
                () -> {
                    page = Math.max(0, page - 1);
                    refresh();
                });
        button(
                "›",
                x + 32,
                height - 77,
                28,
                () -> {
                    page = Math.min(Math.max(0, (size - 1) / count), page + 1);
                    refresh();
                });
    }

    private void footer(int x, int w) {
        int bw = (w - 16) / 5, y = height - 28;
        var save =
                button(
                        "Save",
                        x,
                        y,
                        bw,
                        () -> {
                            try {
                                if (!invalid.isEmpty())
                                    throw new IllegalArgumentException(
                                            invalid.values().iterator().next());
                                SettingsStore.save(settings(draft));
                                draft = json(SettingsStore.snapshot());
                                saved = draft.deepCopy();
                                raw.clear();
                                if (choices != null) {
                                    choices = null;
                                    pick = null;
                                    query = "";
                                    page = 0;
                                }
                                status =
                                        "Saved. Green: customized. Generation: new terrain. Loot: unopened chests.";
                                refresh();
                            } catch (Exception e) {
                                status = MenuNames.message(e.getMessage());
                            }
                        });
        save.active = editable;
        var undo =
                button(
                        "Undo Edits",
                        x + bw + 4,
                        y,
                        bw,
                        () -> {
                            status = "Unsaved edits discarded.";
                            replaceDraft(saved.deepCopy());
                        });
        undo.active = editable;
        var reset =
                button(
                        "Defaults",
                        x + 2 * (bw + 4),
                        y,
                        bw,
                        () ->
                                confirm(
                                        "Reset configuration?",
                                        "Replace the complete draft with Generous defaults? Save is required.",
                                        () -> {
                                            status = "Generous defaults loaded. Save to apply.";
                                            replaceDraft(defaults.deepCopy());
                                        }));
        reset.active = editable;
        button(
                "Back",
                x + 3 * (bw + 4),
                y,
                bw,
                () -> {
                    if (choices != null) {
                        choices = null;
                        pick = null;
                        query = "";
                        page = 0;
                        refresh();
                    } else back();
                });
        button("Cancel", x + 4 * (bw + 4), y, bw, this::onClose);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        colors.forEach(Runnable::run);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.centeredText(
                font,
                font.plainSubstrByWidth(
                        route.group
                                + "  •  Preset: "
                                + draft.get("preset").getAsString()
                                + (dirty() ? "  •  Unsaved" : ""),
                        width - 24),
                width / 2,
                28,
                dirty() ? 0xFFFFFF55 : 0xFF55FF55);
        for (var label : rowLabels) {
            String text = label.text;
            if (label.path.contains("storehouseOreWeights")) {
                double total = 0;
                for (var weight : draft.getAsJsonObject("storehouseOreWeights").entrySet())
                    total += weight.getValue().getAsDouble();
                text +=
                        " ("
                                + (total > 0
                                        ? String.format(
                                                Locale.ROOT,
                                                "%.1f%%",
                                                get(draft, label.path).getAsDouble() / total * 100)
                                        : "—")
                                + ")";
            }
            graphics.text(font, text, label.x, label.y, color(label.path));
        }
        String message =
                editable ? status : "Read-only local profile. This server controls gameplay.";
        String first = font.plainSubstrByWidth(message, width - 24);
        if (first.length() < message.length() && first.lastIndexOf(' ') > 0)
            first = first.substring(0, first.lastIndexOf(' '));
        graphics.centeredText(font, first, width / 2, height - 53, 0xFFCCCCCC);
        if (first.length() < message.length())
            graphics.centeredText(
                    font,
                    font.plainSubstrByWidth(
                            message.substring(first.length()).stripLeading(), width - 24),
                    width / 2,
                    height - 43,
                    0xFFCCCCCC);
        graphics.text(
                font,
                "Page " + (page + 1),
                Math.max(12, width / 2 - 280) + 70,
                height - 71,
                0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        if (dirty())
            confirm(
                    "Discard unsaved changes?",
                    "Your saved configuration will be kept.",
                    () -> minecraft.gui.setScreen(parent));
        else minecraft.gui.setScreen(parent);
    }
}
