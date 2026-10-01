package com.buuz135.simpleclaims.claim.party;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Test double for SimpleClaims' PartyInfo. */
public class PartyInfo {
    private final UUID id = UUID.randomUUID();
    public final Set<UUID> members = new HashSet<>();
    public final Set<UUID> playerAllies = new HashSet<>();
    public final Set<UUID> partyAllies = new HashSet<>();

    public UUID getId() {
        return id;
    }

    public boolean isOwnerOrMember(UUID uuid) {
        return members.contains(uuid);
    }

    public boolean isPlayerAllied(UUID uuid) {
        return playerAllies.contains(uuid);
    }

    public boolean isPartyAllied(UUID uuid) {
        return partyAllies.contains(uuid);
    }
}
