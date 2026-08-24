package net.mcverse.register.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NicknameSnapshotTest {

    @Test
    void normalizeTurnsBlankAndNullIntoNull() {
        assertNull(NicknameSnapshot.normalize(null));
        assertNull(NicknameSnapshot.normalize(""));
        assertNull(NicknameSnapshot.normalize("   "));
    }

    @Test
    void normalizeKeepsRawEssentialsFormatting() {
        assertEquals("&cSteve&fThe&bBest", NicknameSnapshot.normalize("&cSteve&fThe&bBest"));
        assertEquals("§cSteve", NicknameSnapshot.normalize("§cSteve"));
    }
}
