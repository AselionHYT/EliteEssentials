package com.eliteessentials.services;

import com.eliteessentials.model.Home;
import com.eliteessentials.model.Location;

import java.util.Map;

/**
 * Per-world home limits ({@code homes.worldHomeLimits}).
 *
 * <p>A world listed in the map has its own budget: homes in it are counted only against that
 * world's limit and not against {@code maxHomes}. Homes in every other world share the normal
 * limit, which ignores homes in listed worlds. Example: {@code {"farm": 1}} gives every player
 * one home in the world {@code farm} on top of their normal homes.
 */
public final class HomeLimits {

    private HomeLimits() {
    }

    /** The listed world a location belongs to, or {@code null} for the shared budget. */
    public static String bucketOf(String world, Map<String, Integer> worldLimits) {
        if (world == null || worldLimits == null || worldLimits.isEmpty()) {
            return null;
        }
        return worldLimits.containsKey(world) ? world : null;
    }

    /** The limit that applies to homes in {@code world}: its own entry, else {@code globalMax}. */
    public static int limitFor(String world, Map<String, Integer> worldLimits, int globalMax) {
        String bucket = bucketOf(world, worldLimits);
        if (bucket == null) {
            return globalMax;
        }
        Integer limit = worldLimits.get(bucket);
        return limit == null || limit < 0 ? Integer.MAX_VALUE : limit;
    }

    /** Number of homes that count against the budget of {@code world}. */
    public static int countIn(Map<String, Home> homes, String world, Map<String, Integer> worldLimits) {
        String bucket = bucketOf(world, worldLimits);
        int count = 0;
        for (Home home : homes.values()) {
            Location loc = home.getLocation();
            String homeBucket = bucketOf(loc == null ? null : loc.getWorld(), worldLimits);
            if (java.util.Objects.equals(bucket, homeBucket)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether setting home {@code name} in {@code world} would exceed the budget of that world.
     * Overwriting a home that already counts against the same budget never exceeds it; moving a
     * home from one budget to another counts against the new one.
     */
    public static boolean wouldExceed(Map<String, Home> homes, String name, String world,
                                      Map<String, Integer> worldLimits, int globalMax) {
        String bucket = bucketOf(world, worldLimits);
        Home existing = homes.get(name);
        if (existing != null) {
            Location loc = existing.getLocation();
            if (java.util.Objects.equals(bucket, bucketOf(loc == null ? null : loc.getWorld(), worldLimits))) {
                return false;
            }
        }
        return countIn(homes, world, worldLimits) >= limitFor(world, worldLimits, globalMax);
    }
}
