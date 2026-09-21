package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.schedule.ScheduleController;
import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.port.*;
import com.dp.deviceops.core.service.SavedConnectionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagementNamespaceBoundaryTest {
    private final org.springframework.security.oauth2.jwt.Jwt jwt = CollectionBoundaryTest.jwt("owner",
            Map.of("device_ops_namespaces", List.of("owned"), "device_ops_projects", List.of("project")));

    @Test void scheduleReadsAndDeletesRequireNamespaceInAdditionToProject() {
        var store = mock(InspectionSchedulePort.class);
        var controller = new ScheduleController(store, new ProjectClaimAuthorizer());
        assertThrows(AccessDeniedException.class, () -> controller.list(jwt, "project", "foreign"));
        assertThrows(AccessDeniedException.class, () -> controller.get(jwt, "project", "key", "foreign"));
        assertThrows(AccessDeniedException.class, () -> controller.disable(jwt, "project", "key", "foreign"));
        verifyNoInteractions(store);
    }

    @Test void credentialReadsAndDeletesRequireNamespaceBeforeStorage() throws Exception {
        var store = mock(CredentialStore.class);
        var controller = construct(CredentialController.class, store, new ProjectClaimAuthorizer());
        assertThrows(AccessDeniedException.class, () -> controller.list(jwt, "foreign"));
        assertThrows(AccessDeniedException.class, () -> controller.get(jwt, "id", "foreign"));
        assertThrows(AccessDeniedException.class, () -> controller.delete(jwt, "id", "foreign"));
        verifyNoInteractions(store);
    }

    @Test void savedConnectionReadsAndDeletesRequireNamespaceBeforeStorage() throws Exception {
        var store = mock(SavedConnectionStore.class);
        var controller = construct(SavedConnectionController.class, store, mock(SavedConnectionService.class),
                mock(ConnectionRequestMapper.class), new ProjectClaimAuthorizer());
        assertThrows(AccessDeniedException.class, () -> controller.list(jwt, "foreign"));
        assertThrows(AccessDeniedException.class, () -> controller.get(jwt, "id", "foreign"));
        assertThrows(AccessDeniedException.class, () -> controller.delete(jwt, "id", "foreign", 0L));
        verifyNoInteractions(store);
    }

    @Test void secretWritesDenyNamespaceAndClearSecrets() throws Exception {
        var store = mock(CredentialStore.class);
        when(store.available()).thenReturn(true);
        var controller = construct(CredentialController.class, store, new ProjectClaimAuthorizer());
        char[] secret = "secret".toCharArray();
        var request = new CredentialController.Request("foreign", "name", CommandExecutionPort.AuthenticationType.PASSWORD,
                secret, null, null);
        assertThrows(AccessDeniedException.class, () -> controller.create(jwt, request));
        assertArrayEquals(new char[6], secret);
        verify(store, never()).save(any(), any(), any(), any(), any(), any());
    }

    @Test void managementScopeUsesTheSameClaimSemanticsAsRequire() throws Exception {
        var authorizer = new ProjectClaimAuthorizer();
        var method = java.util.Arrays.stream(ProjectClaimAuthorizer.class.getMethods())
                .filter(m -> m.getName().equals("visibleScope")).findFirst();
        assertTrue(method.isPresent(), "authorizer must expose its own scope semantics");
        var token = CollectionBoundaryTest.jwt("owner", Map.of("client_namespace", "owned", "device_ops_projects", List.of(42, "p")));
        var scope = (com.dp.deviceops.core.port.ManagementQueryPort.Scope) method.orElseThrow().invoke(authorizer, token);
        assertEquals(List.of("owned"), scope.namespaces());
        assertEquals(List.of("42", "p"), scope.projects());
        assertDoesNotThrow(() -> authorizer.require(token, "42"));
    }

    @Test void fallbackAsteriskIsNotAnExplicitNamespaceWildcard() throws Exception {
        var authorizer = new ProjectClaimAuthorizer();
        var fallback = authorizer.visibleScope(CollectionBoundaryTest.jwt("owner", Map.of("client_namespace", "*")));
        var explicit = authorizer.visibleScope(CollectionBoundaryTest.jwt("owner", Map.of("device_ops_namespaces", List.of("*"))));
        var accessor = java.util.Arrays.stream(fallback.getClass().getMethods()).filter(m -> m.getName().equals("allNamespaces")).findFirst();
        assertTrue(accessor.isPresent(), "wildcard provenance must survive projection");
        assertEquals(false, accessor.orElseThrow().invoke(fallback));
        assertEquals(true, accessor.orElseThrow().invoke(explicit));
        assertEquals(false, accessor.orElseThrow().invoke(authorizer.visibleScope(CollectionBoundaryTest.jwt("*", Map.of()))));
    }

    @Test void scheduleAndSavedConnectionWritesDenyForeignNamespaceBeforeWork() throws Exception {
        var schedules = mock(InspectionSchedulePort.class);
        var schedule = new ScheduleController(schedules, new ProjectClaimAuthorizer());
        var request = new ScheduleController.Request("foreign", "project", "hint", List.of("device"), "script", "1", "0 * * * * *", "UTC", "https://invalid.test", false);
        assertThrows(AccessDeniedException.class, () -> schedule.upsert(jwt, "project", "key", request));
        verifyNoInteractions(schedules);
        var store = mock(SavedConnectionStore.class);
        var service = mock(SavedConnectionService.class);
        var controller = construct(SavedConnectionController.class, store, service, mock(ConnectionRequestMapper.class), new ProjectClaimAuthorizer());
        assertThrows(AccessDeniedException.class, () -> controller.verifyAndCreate(jwt, new SavedConnectionController.CreateRequest("foreign", "name", null, null)));
        assertThrows(AccessDeniedException.class, () -> controller.verifyAndReplace(jwt, "id", new SavedConnectionController.ReplaceRequest("foreign", 0L, "name", null, null)));
        assertThrows(AccessDeniedException.class, () -> controller.rename(jwt, "id", new SavedConnectionController.RenameRequest("foreign", 0L, "name", null)));
        verifyNoInteractions(store, service);
        var credentials = mock(CredentialStore.class);
        var credentialController = construct(CredentialController.class, credentials, new ProjectClaimAuthorizer());
        char[] secret = "secret".toCharArray();
        assertThrows(AccessDeniedException.class, () -> credentialController.replace(jwt, "id", new CredentialController.Request("foreign", "name", CommandExecutionPort.AuthenticationType.PASSWORD, secret, null, null)));
        assertArrayEquals(new char[6], secret);
        verifyNoInteractions(credentials);
    }

    // Supports the pre-fix constructor during RED and authorizer injection during GREEN.
    private static <T> T construct(Class<T> type, Object... dependencies) throws Exception {
        var constructor = type.getConstructors()[0];
        return type.cast(constructor.newInstance(java.util.Arrays.copyOf(dependencies, constructor.getParameterCount())));
    }
}
