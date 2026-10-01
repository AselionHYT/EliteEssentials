package com.eliteessentials.integration;

import com.buuz135.simpleclaims.claim.ClaimManager;
import com.buuz135.simpleclaims.claim.party.PartyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the reflection binding against SimpleClaims test doubles with the real signatures. */
class ClaimsIntegrationTest {

    private static final String WORLD = "default";

    private final ClaimManager claims = ClaimManager.getInstance();
    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private PartyInfo party;
    private ClaimsIntegration.ClaimLookup lookup;

    @BeforeEach
    void setUp() {
        claims.reset();
        party = new PartyInfo();
        party.members.add(owner);
        claims.playerToParty.put(owner, party.getId());
        // Chunk (0, 0) covers blocks 0..31 on both axes.
        claims.claim(WORLD, 0, 0, party);
        lookup = ClaimsIntegration.SimpleClaimsLookup.tryCreate(getClass().getClassLoader());
        assertNotNull(lookup);
    }

    @Test
    void absentSimpleClaimsYieldsNoLookup() {
        ClassLoader bootstrapOnly = new ClassLoader(null) { };
        assertNull(ClaimsIntegration.SimpleClaimsLookup.tryCreate(bootstrapOnly));
        assertFalse(ClaimsIntegration.NONE.isClaimed(WORLD, 5, 5));
        assertFalse(ClaimsIntegration.NONE.isForeignClaim(stranger, WORLD, 5, 5));
    }

    @Test
    void claimedChunkIsDetectedAcrossItsWholeArea() {
        assertTrue(lookup.isClaimed(WORLD, 0, 0));
        assertTrue(lookup.isClaimed(WORLD, 31, 31));
        assertFalse(lookup.isClaimed(WORLD, 32, 0));
        assertFalse(lookup.isClaimed(WORLD, -1, 0));
        assertFalse(lookup.isClaimed("other_world", 5, 5));
    }

    @Test
    void claimOfADeletedPartyCountsAsUnclaimed() {
        claims.parties.clear();
        assertFalse(lookup.isClaimed(WORLD, 5, 5));
        assertFalse(lookup.isForeignClaim(stranger, WORLD, 5, 5));
    }

    @Test
    void strangerIsInAForeignClaim() {
        assertTrue(lookup.isForeignClaim(stranger, WORLD, 5, 5));
        assertFalse(lookup.isForeignClaim(stranger, WORLD, 40, 5), "unclaimed chunk is never foreign");
    }

    @Test
    void ownerMemberIsNotInAForeignClaim() {
        assertFalse(lookup.isForeignClaim(owner, WORLD, 5, 5));
    }

    @Test
    void alliedPlayerIsNotInAForeignClaim() {
        party.playerAllies.add(stranger);
        assertFalse(lookup.isForeignClaim(stranger, WORLD, 5, 5));
    }

    @Test
    void memberOfAnAlliedPartyIsNotInAForeignClaim() {
        PartyInfo friends = new PartyInfo();
        friends.members.add(stranger);
        claims.parties.put(friends.getId(), friends);
        claims.playerToParty.put(stranger, friends.getId());
        assertTrue(lookup.isForeignClaim(stranger, WORLD, 5, 5));

        party.partyAllies.add(friends.getId());
        assertFalse(lookup.isForeignClaim(stranger, WORLD, 5, 5));
    }

    @Test
    void adminOverrideIsNotInAForeignClaim() {
        claims.adminOverrides.add(stranger);
        assertFalse(lookup.isForeignClaim(stranger, WORLD, 5, 5));
    }
}
