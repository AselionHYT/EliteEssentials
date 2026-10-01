package com.buuz135.simpleclaims.claim;

import com.buuz135.simpleclaims.claim.chunk.ChunkInfo;
import com.buuz135.simpleclaims.claim.party.PartyInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Test double with the method signatures EliteEssentials calls on SimpleClaims 1.0.39's
 * ClaimManager. Chunks are 32 blocks wide, as in Hytale.
 */
public class ClaimManager {
    private static final ClaimManager INSTANCE = new ClaimManager();

    public final Map<String, ChunkInfo> chunks = new HashMap<>();
    public final Map<UUID, PartyInfo> parties = new HashMap<>();
    public final Map<UUID, UUID> playerToParty = new HashMap<>();
    public final Set<UUID> adminOverrides = new HashSet<>();

    public static ClaimManager getInstance() {
        return INSTANCE;
    }

    public void reset() {
        chunks.clear();
        parties.clear();
        playerToParty.clear();
        adminOverrides.clear();
    }

    public void claim(String dimension, int chunkX, int chunkZ, PartyInfo party) {
        parties.put(party.getId(), party);
        chunks.put(dimension + ":" + chunkX + ":" + chunkZ, new ChunkInfo(party.getId(), chunkX, chunkZ));
    }

    public ChunkInfo getChunkRawCoords(String dimension, int blockX, int blockZ) {
        return chunks.get(dimension + ":" + (blockX >> 5) + ":" + (blockZ >> 5));
    }

    public PartyInfo getPartyById(UUID partyId) {
        return parties.get(partyId);
    }

    public PartyInfo getPartyFromPlayer(UUID player) {
        UUID partyId = playerToParty.get(player);
        return partyId == null ? null : parties.get(partyId);
    }

    public Set<UUID> getAdminClaimOverrides() {
        return adminOverrides;
    }
}
