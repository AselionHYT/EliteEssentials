package com.eliteessentials.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtpSafetyTest {

    private static final int HEIGHT = 320;

    /** Sparse fluid map; everything not set is Fluid.EMPTY_ID (0). */
    private final Map<String, Integer> fluids = new HashMap<>();

    private int fluidAt(int x, int y, int z) {
        return fluids.getOrDefault(x + ":" + y + ":" + z, 0);
    }

    private boolean safeAt(int x, int y, int z) {
        return RtpSafety.isFluidFree(this::fluidAt, x, y, z, HEIGHT);
    }

    @Test
    void dryColumnIsSafe() {
        assertTrue(safeAt(10, 100, 10));
    }

    @Test
    void anyNonEmptyFluidIdIsUnsafe() {
        // Ids are asset-map indices; upstream only rejected 6 and 7.
        for (int id : new int[]{1, 2, 3, 6, 7, 42}) {
            fluids.clear();
            fluids.put("10:100:10", id);
            assertFalse(safeAt(10, 100, 10), "fluid id " + id);
        }
    }

    @Test
    void fluidAboveHeadOrUnderFeetIsUnsafe() {
        fluids.put("10:103:10", 2);
        assertFalse(safeAt(10, 100, 10));
        fluids.clear();
        fluids.put("10:98:10", 2);
        assertFalse(safeAt(10, 100, 10));
    }

    @Test
    void fluidOutsideTheCheckedRangeIsIgnored() {
        fluids.put("10:104:10", 2);
        fluids.put("10:97:10", 2);
        fluids.put("11:101:10", 2);
        assertTrue(safeAt(10, 100, 10));
    }

    @Test
    void fluidNextToTheFeetIsUnsafe() {
        fluids.put("10:100:9", 2);
        assertFalse(safeAt(10, 100, 10));
    }

    @Test
    void positionsOutsideTheWorldAreNotQueried() {
        assertTrue(RtpSafety.isFluidFree((x, y, z) -> {
            if (y < 0 || y >= HEIGHT) {
                throw new AssertionError("queried y=" + y);
            }
            return 0;
        }, 0, HEIGHT - 1, 0, HEIGHT));
    }
}
