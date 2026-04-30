package net.mcverse.register.api;

import java.time.Instant;
import java.util.List;

public record GriefPreventionClaimsSyncRequest(
        int claimCount,
        int accruedClaimBlocks,
        int bonusClaimBlocks,
        int remainingClaimBlocks,
        List<ClaimLocation> claims,
        Instant observedAt
) {

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"claimCount\":").append(claimCount);
        json.append(",\"accruedClaimBlocks\":").append(accruedClaimBlocks);
        json.append(",\"bonusClaimBlocks\":").append(bonusClaimBlocks);
        json.append(",\"remainingClaimBlocks\":").append(remainingClaimBlocks);
        json.append(",\"claims\":[");
        List<ClaimLocation> safeClaims = claims == null ? List.of() : claims;
        for (int i = 0; i < safeClaims.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append(safeClaims.get(i).toJson());
        }
        json.append("]");
        if (observedAt != null) {
            json.append(",\"observedAt\":\"").append(observedAt).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    public String summary() {
        int size = claims == null ? 0 : claims.size();
        return "claimCount=" + claimCount + ",remainingClaimBlocks=" + remainingClaimBlocks + ",claimsSize=" + size;
    }
}
