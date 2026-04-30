package net.mcverse.register.integration;

import net.mcverse.register.api.ClaimLocation;

import java.util.List;

public record ClaimsSnapshot(
        int claimCount,
        int accruedClaimBlocks,
        int bonusClaimBlocks,
        int remainingClaimBlocks,
        List<ClaimLocation> claims
) {
}
