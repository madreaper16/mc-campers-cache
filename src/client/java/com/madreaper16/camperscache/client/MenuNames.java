package com.madreaper16.camperscache.client;

import java.util.regex.Pattern;

// Use readable labels. Keep IDs in saved settings.
final class MenuNames {
    private static final Pattern REGISTRY_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    static String title(String text) {
        String spaced = text.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (char c : spaced.toCharArray()) {
            result.append(capitalize ? Character.toTitleCase(c) : c);
            capitalize = Character.isWhitespace(c) || c == '-' || c == '/';
        }
        return result.toString();
    }

    static String registry(String id) {
        int colon = id.indexOf(':');
        return title(colon >= 0 ? id.substring(colon + 1) : id);
    }

    static String message(String message) {
        if (message == null) return "Could not save settings. Check the game log.";
        return REGISTRY_ID.matcher(message).replaceAll(match -> registry(match.group()));
    }
}
