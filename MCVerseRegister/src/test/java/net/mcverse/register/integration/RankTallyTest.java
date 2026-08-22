package net.mcverse.register.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankTallyTest {

    private final RankNameConfig ranks = new RankNameConfig("default", "member", "regular", "citizen");

    @Test
    void primaryBucketsAreExclusive() {
        RankTally tally = new RankTally(ranks);
        tally.accept("default", false);
        tally.accept("member", false);
        tally.accept("regular", false);
        tally.accept("citizen", true);
        tally.accept("mod", true);
        tally.accept("supporter", true);

        assertEquals(1, tally.defaultCount());
        assertEquals(1, tally.memberCount());
        assertEquals(1, tally.regularCount());
        assertEquals(1, tally.citizenCount());
        assertEquals(3, tally.citizenAll());
    }

    @Test
    void citizenAllIsAtLeastPrimaryCitizen() {
        RankTally tally = new RankTally(ranks);
        tally.accept("citizen", true);
        tally.accept("citizen", true);

        assertEquals(2, tally.citizenCount());
        assertEquals(2, tally.citizenAll());
    }

    @Test
    void unknownPrimaryIsIgnoredForRankBuckets() {
        RankTally tally = new RankTally(ranks);
        tally.accept("admin", false);
        tally.accept(null, false);

        assertEquals(0, tally.defaultCount());
        assertEquals(0, tally.memberCount());
        assertEquals(0, tally.regularCount());
        assertEquals(0, tally.citizenCount());
        assertEquals(0, tally.citizenAll());
    }
}
