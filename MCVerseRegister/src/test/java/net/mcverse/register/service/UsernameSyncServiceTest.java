package net.mcverse.register.service;

import net.mcverse.register.MCVerseRegister;
import net.mcverse.register.api.ApiResponse;
import net.mcverse.register.api.MCVerseApiClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsernameSyncServiceTest {

    private MCVerseRegister plugin;
    private MCVerseApiClient apiClient;
    private List<Long> sleepCalls;
    private UsernameSyncService service;

    @BeforeEach
    void setUp() {
        plugin = mock(MCVerseRegister.class);
        apiClient = mock(MCVerseApiClient.class);
        sleepCalls = new ArrayList<>();

        when(plugin.getLogger()).thenReturn(Logger.getLogger("UsernameSyncServiceTest"));

        service = new UsernameSyncService(
                plugin,
                apiClient,
                sleepCalls::add,
                () -> 0L
        );
    }

    @Test
    void skipsWhenUuidIsUnregistered() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(apiClient.getPlayer(uuid)).thenReturn(new ApiResponse(404, "{\"success\":false,\"registered\":false}"));

        UsernameSyncResult result = service.syncIfNeeded(uuid, "CurrentName");

        assertTrue(result.success());
        assertFalse(result.registered());
        verify(apiClient, never()).syncUsername(eq(uuid), any());
    }

    @Test
    void noSyncCallWhenUsernameUnchanged() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(apiClient.getPlayer(uuid)).thenReturn(new ApiResponse(
                200,
                "{\"success\":true,\"registered\":true,\"player\":{\"minecraftUsername\":\"SameName\",\"minecraftUuid\":\"" + uuid + "\"}}"
        ));

        UsernameSyncResult result = service.syncIfNeeded(uuid, "SameName");

        assertTrue(result.success());
        assertTrue(result.registered());
        assertFalse(result.changed());
        verify(apiClient, never()).syncUsername(eq(uuid), any());
    }

    @Test
    void syncCalledOnceWhenUsernameChanged() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(apiClient.getPlayer(uuid)).thenReturn(new ApiResponse(
                200,
                "{\"success\":true,\"registered\":true,\"player\":{\"minecraftUsername\":\"OldName\",\"minecraftUuid\":\"" + uuid + "\"}}"
        ));
        when(apiClient.syncUsername(eq(uuid), any())).thenReturn(new ApiResponse(200, "{\"success\":true,\"changed\":true}"));

        UsernameSyncResult result = service.syncIfNeeded(uuid, "NewName");

        assertTrue(result.success());
        assertTrue(result.registered());
        assertTrue(result.changed());
        verify(apiClient, times(1)).syncUsername(eq(uuid), any());
    }

    @Test
    void repeatedRunIsIdempotent() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(apiClient.getPlayer(uuid))
                .thenReturn(new ApiResponse(
                        200,
                        "{\"success\":true,\"registered\":true,\"player\":{\"minecraftUsername\":\"OldName\",\"minecraftUuid\":\"" + uuid + "\"}}"
                ))
                .thenReturn(new ApiResponse(
                        200,
                        "{\"success\":true,\"registered\":true,\"player\":{\"minecraftUsername\":\"NewName\",\"minecraftUuid\":\"" + uuid + "\"}}"
                ));
        when(apiClient.syncUsername(eq(uuid), any())).thenReturn(new ApiResponse(200, "{\"success\":true,\"changed\":true}"));

        UsernameSyncResult first = service.syncIfNeeded(uuid, "NewName");
        UsernameSyncResult second = service.syncIfNeeded(uuid, "NewName");

        assertTrue(first.changed());
        assertFalse(second.changed());
        verify(apiClient, times(1)).syncUsername(eq(uuid), any());
    }

    @Test
    void retriesTransientFailureWithBackoff() throws Exception {
        UUID uuid = UUID.randomUUID();
        when(apiClient.getPlayer(uuid))
                .thenThrow(new IOException("temporary network issue"))
                .thenReturn(new ApiResponse(
                        200,
                        "{\"success\":true,\"registered\":true,\"player\":{\"minecraftUsername\":\"SameName\",\"minecraftUuid\":\"" + uuid + "\"}}"
                ));

        UsernameSyncResult result = service.syncIfNeeded(uuid, "SameName");

        assertTrue(result.success());
        assertEquals(1, sleepCalls.size());
        assertEquals(250L, sleepCalls.get(0));
        verify(apiClient, times(2)).getPlayer(uuid);
    }
}
