package com.eliteessentials.integration;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Optional SimpleClaims (Buuz135) support: lets RTP avoid claimed chunks and /sethome
 * refuse homes inside another party's claim.
 *
 * <p>SimpleClaims is a soft dependency. It is looked up by reflection on first use, so the
 * plugin loads and behaves exactly as before when SimpleClaims is not installed. The calls
 * mirror the membership rules of {@code ClaimManager.isAllowedToInteract}: the claiming
 * party's owner and members, players allied to that party, members of allied parties and
 * players with a SimpleClaims admin override count as "not foreign".
 */
public final class ClaimsIntegration {

    private static final Logger LOGGER = Logger.getLogger("EliteEssentials");

    /** Answers about claims; {@link #NONE} when SimpleClaims is absent. */
    public interface ClaimLookup {
        /** Whether the chunk holding this block column is claimed by an existing party. */
        boolean isClaimed(String world, int blockX, int blockZ);

        /** Whether the chunk is claimed by a party the player does not belong to or is not allied with. */
        boolean isForeignClaim(UUID playerId, String world, int blockX, int blockZ);
    }

    public static final ClaimLookup NONE = new ClaimLookup() {
        @Override
        public boolean isClaimed(String world, int blockX, int blockZ) {
            return false;
        }

        @Override
        public boolean isForeignClaim(UUID playerId, String world, int blockX, int blockZ) {
            return false;
        }
    };

    private static volatile ClaimLookup lookup;

    private ClaimsIntegration() {
    }

    /** The claim lookup, resolved once on first use. */
    public static ClaimLookup get() {
        ClaimLookup current = lookup;
        if (current == null) {
            synchronized (ClaimsIntegration.class) {
                current = lookup;
                if (current == null) {
                    current = SimpleClaimsLookup.tryCreate(ClaimsIntegration.class.getClassLoader());
                    if (current == null) {
                        current = NONE;
                        LOGGER.info("[SimpleClaims] Not found, RTP and /sethome ignore claims.");
                    } else {
                        LOGGER.info("[SimpleClaims] Found, RTP skips claimed chunks and /sethome respects foreign claims.");
                    }
                    lookup = current;
                }
            }
        }
        return current;
    }

    /**
     * Reflection binding to SimpleClaims' {@code ClaimManager}. Package-private for tests.
     */
    static final class SimpleClaimsLookup implements ClaimLookup {
        private static final String CLAIM_MANAGER = "com.buuz135.simpleclaims.claim.ClaimManager";
        private static final String CHUNK_INFO = "com.buuz135.simpleclaims.claim.chunk.ChunkInfo";
        private static final String PARTY_INFO = "com.buuz135.simpleclaims.claim.party.PartyInfo";

        private final MethodHandle getInstance;
        private final MethodHandle getChunkRawCoords;
        private final MethodHandle getPartyById;
        private final MethodHandle getPartyFromPlayer;
        private final MethodHandle getAdminClaimOverrides;
        private final MethodHandle chunkPartyOwner;
        private final MethodHandle partyId;
        private final MethodHandle partyIsOwnerOrMember;
        private final MethodHandle partyIsPlayerAllied;
        private final MethodHandle partyIsPartyAllied;
        private volatile boolean failureLogged;

        private SimpleClaimsLookup(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> manager = Class.forName(CLAIM_MANAGER, true, loader);
            Class<?> chunk = Class.forName(CHUNK_INFO, true, loader);
            Class<?> party = Class.forName(PARTY_INFO, true, loader);
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            getInstance = l.findStatic(manager, "getInstance", MethodType.methodType(manager));
            getChunkRawCoords = l.findVirtual(manager, "getChunkRawCoords",
                MethodType.methodType(chunk, String.class, int.class, int.class));
            getPartyById = l.findVirtual(manager, "getPartyById", MethodType.methodType(party, UUID.class));
            getPartyFromPlayer = l.findVirtual(manager, "getPartyFromPlayer", MethodType.methodType(party, UUID.class));
            getAdminClaimOverrides = l.findVirtual(manager, "getAdminClaimOverrides", MethodType.methodType(Set.class));
            chunkPartyOwner = l.findVirtual(chunk, "getPartyOwner", MethodType.methodType(UUID.class));
            partyId = l.findVirtual(party, "getId", MethodType.methodType(UUID.class));
            partyIsOwnerOrMember = l.findVirtual(party, "isOwnerOrMember", MethodType.methodType(boolean.class, UUID.class));
            partyIsPlayerAllied = l.findVirtual(party, "isPlayerAllied", MethodType.methodType(boolean.class, UUID.class));
            partyIsPartyAllied = l.findVirtual(party, "isPartyAllied", MethodType.methodType(boolean.class, UUID.class));
        }

        /** Returns null when SimpleClaims is not on the class path or its API does not match. */
        static SimpleClaimsLookup tryCreate(ClassLoader loader) {
            try {
                return new SimpleClaimsLookup(loader);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                return null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOGGER.warning("[SimpleClaims] Installed, but its API did not match (" + e
                    + "). RTP and /sethome ignore claims.");
                return null;
            }
        }

        @Override
        public boolean isClaimed(String world, int blockX, int blockZ) {
            try {
                return claimingParty(world, blockX, blockZ) != null;
            } catch (Throwable t) {
                logFailure(t);
                return false;
            }
        }

        @Override
        public boolean isForeignClaim(UUID playerId, String world, int blockX, int blockZ) {
            try {
                Object manager = getInstance.invoke();
                Set<?> overrides = (Set<?>) getAdminClaimOverrides.invoke(manager);
                if (overrides != null && overrides.contains(playerId)) {
                    return false;
                }
                Object party = claimingParty(world, blockX, blockZ);
                if (party == null) {
                    return false;
                }
                if ((boolean) partyIsOwnerOrMember.invoke(party, playerId)
                        || (boolean) partyIsPlayerAllied.invoke(party, playerId)) {
                    return false;
                }
                Object ownParty = getPartyFromPlayer.invoke(manager, playerId);
                if (ownParty != null && (boolean) partyIsPartyAllied.invoke(party, (UUID) partyId.invoke(ownParty))) {
                    return false;
                }
                return true;
            } catch (Throwable t) {
                logFailure(t);
                return false;
            }
        }

        private Object claimingParty(String world, int blockX, int blockZ) throws Throwable {
            Object manager = getInstance.invoke();
            Object chunk = getChunkRawCoords.invoke(manager, world, blockX, blockZ);
            if (chunk == null) {
                return null;
            }
            UUID owner = (UUID) chunkPartyOwner.invoke(chunk);
            // A claim whose party no longer exists protects nothing in SimpleClaims either.
            return owner == null ? null : getPartyById.invoke(manager, owner);
        }

        private void logFailure(Throwable t) {
            if (!failureLogged) {
                failureLogged = true;
                LOGGER.warning("[SimpleClaims] Claim lookup failed, treating the location as unclaimed: " + t);
            }
        }
    }
}
