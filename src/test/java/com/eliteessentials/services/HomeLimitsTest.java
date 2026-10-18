package com.eliteessentials.services;

import com.eliteessentials.model.Home;
import com.eliteessentials.model.Location;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** homes.worldHomeLimits: listed worlds have their own budget, separate from maxHomes. */
class HomeLimitsTest {

    private static final Map<String, Integer> FARM_ONE = Map.of("farm", 1);

    private static Map<String, Home> homes(String... nameWorld) {
        Map<String, Home> homes = new LinkedHashMap<>();
        for (int i = 0; i < nameWorld.length; i += 2) {
            homes.put(nameWorld[i], new Home(nameWorld[i], new Location(nameWorld[i + 1], 0, 64, 0)));
        }
        return homes;
    }

    @Test
    void withoutWorldLimitsEverythingSharesMaxHomes() {
        Map<String, Home> h = homes("a", "default", "b", "farm", "c", "default");
        assertTrue(HomeLimits.wouldExceed(h, "d", "default", Map.of(), 3));
        assertTrue(HomeLimits.wouldExceed(h, "d", "farm", Map.of(), 3));
        assertFalse(HomeLimits.wouldExceed(h, "a", "default", Map.of(), 3), "overwrite is free");
        assertFalse(HomeLimits.wouldExceed(h, "d", "default", Map.of(), 4));
    }

    @Test
    void farmHomeDoesNotCountAgainstMaxHomes() {
        Map<String, Home> h = homes("a", "default", "b", "default", "farm", "farm");
        assertEquals(2, HomeLimits.countIn(h, "default", FARM_ONE));
        assertFalse(HomeLimits.wouldExceed(h, "c", "default", FARM_ONE, 3), "third main-world home still fits");
        assertTrue(HomeLimits.wouldExceed(h, "c", "default", FARM_ONE, 2));
    }

    @Test
    void fullMainBudgetStillAllowsOneFarmHome() {
        Map<String, Home> h = homes("a", "default", "b", "default", "c", "default");
        assertFalse(HomeLimits.wouldExceed(h, "farm", "farm", FARM_ONE, 0));
    }

    @Test
    void secondFarmHomeIsRefusedButOverwritingIsNot() {
        Map<String, Home> h = homes("a", "default", "farm", "farm");
        assertTrue(HomeLimits.wouldExceed(h, "other", "farm", FARM_ONE, 0));
        assertFalse(HomeLimits.wouldExceed(h, "farm", "farm", FARM_ONE, 0));
    }

    @Test
    void movingAHomeIntoAListedWorldCountsAgainstThatWorld() {
        Map<String, Home> h = homes("a", "default", "farm", "farm");
        assertTrue(HomeLimits.wouldExceed(h, "a", "farm", FARM_ONE, 0));
        Map<String, Home> noFarm = homes("a", "default");
        assertFalse(HomeLimits.wouldExceed(noFarm, "a", "farm", FARM_ONE, 0));
    }

    @Test
    void movingAFarmHomeToTheMainWorldCountsAgainstMaxHomes() {
        Map<String, Home> h = homes("a", "default", "b", "default", "farm", "farm");
        assertTrue(HomeLimits.wouldExceed(h, "farm", "default", FARM_ONE, 2));
        assertFalse(HomeLimits.wouldExceed(h, "farm", "default", FARM_ONE, 3));
    }

    @Test
    void limitsAndNegativeMeansUnlimited() {
        assertEquals(1, HomeLimits.limitFor("farm", FARM_ONE, 3));
        assertEquals(3, HomeLimits.limitFor("default", FARM_ONE, 3));
        assertEquals(Integer.MAX_VALUE, HomeLimits.limitFor("farm", Map.of("farm", -1), 3));
        assertFalse(HomeLimits.wouldExceed(homes("x", "farm", "y", "farm"), "z", "farm", Map.of("farm", -1), 0));
    }

    @Test
    void zeroForbidsHomesInThatWorld() {
        assertTrue(HomeLimits.wouldExceed(homes(), "f", "farm", Map.of("farm", 0), 3));
    }

    @Test
    void homeWithoutLocationCountsAsShared() {
        Map<String, Home> h = new LinkedHashMap<>();
        h.put("broken", new Home("broken", null));
        assertEquals(1, HomeLimits.countIn(h, "default", FARM_ONE));
        assertEquals(0, HomeLimits.countIn(h, "farm", FARM_ONE));
    }
}
