package com.eliteessentials.util;

import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;

/**
 * Fluid check for random-teleport landing spots.
 *
 * <p>Fluid ids are indices into the server's fluid asset map, assigned when assets load, so
 * they are not stable numbers. Only {@link Fluid#EMPTY_ID} is fixed. Any other id (water,
 * lava, tar, slime, poison, ...) makes a spot unsafe. Upstream compared against the ids 6 and
 * 7, which did not match water or lava (EliteScouter/EliteEssentials#69).
 */
public final class RtpSafety {

    /** Reads the fluid id at a block position. */
    @FunctionalInterface
    public interface FluidLookup {
        int fluidId(int x, int y, int z);
    }

    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private RtpSafety() {
    }

    /**
     * Whether a player standing at {@code y} has no fluid from two blocks below to three
     * blocks above, and none in the four blocks next to them at foot height.
     *
     * @param worldHeight exclusive upper bound for y
     */
    public static boolean isFluidFree(FluidLookup fluids, int x, int y, int z, int worldHeight) {
        for (int yOffset = -2; yOffset <= 3; yOffset++) {
            int checkY = y + yOffset;
            if (checkY < 0 || checkY >= worldHeight) {
                continue;
            }
            if (fluids.fluidId(x, checkY, z) != Fluid.EMPTY_ID) {
                return false;
            }
        }
        for (int[] offset : NEIGHBOURS) {
            if (fluids.fluidId(x + offset[0], y, z + offset[1]) != Fluid.EMPTY_ID) {
                return false;
            }
        }
        return true;
    }
}
