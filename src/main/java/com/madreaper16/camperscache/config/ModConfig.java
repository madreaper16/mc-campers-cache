package com.madreaper16.camperscache.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ModConfig {
    public static final String[] ORES = {"iron", "coal", "copper", "diamond", "emerald"};

    public static Map<String, Double> defaults() {
        Map<String, Double> result = new LinkedHashMap<>();
        double[] values = {14, 13, 13, 30, 30};
        for (int i = 0; i < ORES.length; i++) result.put(ORES[i], values[i]);
        return result;
    }

    public static Map<String, Double> weights() {
        return new LinkedHashMap<>(SettingsStore.active().storehouseOreWeights);
    }

    public static boolean valid(Map<String, Double> values) {
        if (values == null || values.size() != ORES.length) return false;
        double sum = 0;
        for (String ore : ORES) {
            Double value = values.get(ore);
            if (value == null || !Double.isFinite(value) || value < 0) return false;
            sum += value;
        }
        return Double.isFinite(sum) && sum > 0;
    }

    public static synchronized void save(Map<String, Double> values) throws IOException {
        if (!valid(values))
            throw new IllegalArgumentException(
                    "Use nonnegative ore weights with a positive total.");
        var next = SettingsStore.snapshot();
        next.storehouseOreWeights = new LinkedHashMap<>(values);
        SettingsStore.save(next);
    }

    public static void load() {
        SettingsStore.load();
    }
}
