package com.madreaper16.camperscache.data;

import java.util.LinkedHashMap;
import java.util.Map;

// Save player IDs and item counts, not entity references.
public final class CampState {
    public int[] center, chest, bed, sign;
    public boolean suppliesGenerated;
    public Map<String, Offense> offenders = new LinkedHashMap<>();

    public static final class Offense {
        public boolean apologyPaid;
        public Map<String, Integer> owed = new LinkedHashMap<>();
    }

    public Offense open(String player) {
        return offenders.computeIfAbsent(player, ignored -> new Offense());
    }

    public void stolen(String player, String item, int quantity) {
        if (quantity > 0) open(player).owed.merge(item, quantity, Math::addExact);
    }

    // Charge one emerald per dispute.
    public boolean apologize(String player) {
        Offense offense = offenders.get(player);
        if (offense == null || offense.apologyPaid) return false;
        offense.apologyPaid = true;
        if (offense.owed.isEmpty()) offenders.remove(player);
        return true;
    }

    public int returnGoods(String player, String item, int offered) {
        Offense offense = offenders.get(player);
        if (offense == null || !offense.apologyPaid || offered <= 0) return 0;
        int required = offense.owed.getOrDefault(item, 0);
        int accepted = Math.min(offered, required);
        if (accepted == 0) return 0;
        if (required == accepted) offense.owed.remove(item);
        else offense.owed.put(item, required - accepted);
        if (offense.owed.isEmpty()) offenders.remove(player);
        return accepted;
    }

    public boolean shouldAttack(String player) {
        Offense offense = offenders.get(player);
        return offense != null && !offense.apologyPaid;
    }
}
