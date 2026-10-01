package com.buuz135.simpleclaims.claim.chunk;

import java.util.UUID;

/** Test double for SimpleClaims' ChunkInfo. */
public class ChunkInfo {
    private final UUID partyOwner;

    public ChunkInfo(UUID partyOwner, int chunkX, int chunkZ) {
        this.partyOwner = partyOwner;
    }

    public UUID getPartyOwner() {
        return partyOwner;
    }
}
