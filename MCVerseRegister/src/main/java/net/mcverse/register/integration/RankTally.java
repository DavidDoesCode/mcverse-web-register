package net.mcverse.register.integration;

/**
 * Single-pass rank counters: primary-group buckets are mutually exclusive;
 * {@code citizenAll} counts inherited citizen independently.
 */
public final class RankTally {

    private final RankNameConfig ranks;
    private int defaultCount;
    private int memberCount;
    private int regularCount;
    private int citizenCount;
    private int citizenAll;

    public RankTally(RankNameConfig ranks) {
        this.ranks = ranks;
    }

    public void accept(String primaryGroup, boolean inheritsCitizen) {
        if (primaryGroup != null) {
            if (primaryGroup.equalsIgnoreCase(ranks.defaultRank())) {
                defaultCount++;
            } else if (primaryGroup.equalsIgnoreCase(ranks.memberRank())) {
                memberCount++;
            } else if (primaryGroup.equalsIgnoreCase(ranks.regularRank())) {
                regularCount++;
            } else if (primaryGroup.equalsIgnoreCase(ranks.citizenRank())) {
                citizenCount++;
            }
        }
        if (inheritsCitizen) {
            citizenAll++;
        }
    }

    public int defaultCount() {
        return defaultCount;
    }

    public int memberCount() {
        return memberCount;
    }

    public int regularCount() {
        return regularCount;
    }

    public int citizenCount() {
        return citizenCount;
    }

    public int citizenAll() {
        return citizenAll;
    }
}
