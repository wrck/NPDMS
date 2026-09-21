package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.port.CollectionQueryPort;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CollectionBoundaryTest {
    @Test
    void namespaceEntitlementsFailClosedAndRemainDistinctFromProjectClaims() {
        var authorizer = new ProjectClaimAuthorizer();
        authorizer.requireNamespace(jwt("service", Map.of("client_namespace", "owned")), "owned");
        assertThrows(AccessDeniedException.class,
                () -> authorizer.requireNamespace(jwt("service", Map.of("client_namespace", "owned")), "foreign"));
        authorizer.requireNamespace(jwt("web", Map.of("device_ops_namespaces", List.of("standalone", "project-source"))), "standalone");
        authorizer.requireNamespace(jwt("local", Map.of("device_ops_namespaces", List.of("*"))), "anything");
        authorizer.requireNamespace(jwt("service", Map.of()), "service");
        assertThrows(AccessDeniedException.class,
                () -> authorizer.requireNamespace(jwt("service", Map.of("device_ops_projects", List.of("*"))), "foreign"));
        assertThrows(AccessDeniedException.class,
                () -> authorizer.requireNamespace(jwt(null, Map.of("client_namespace", "owned")), "owned"));
        assertThrows(AccessDeniedException.class,
                () -> authorizer.requireNamespace(jwt("service", Map.of("device_ops_namespaces", "*")), "owned"));
        assertThrows(AccessDeniedException.class,
                () -> authorizer.requireNamespace(jwt("service", Map.of("device_ops_namespaces", List.of(),
                        "client_namespace", "owned")), "owned"));
    }

    @Test
    void genericDetailsMustApplyPersistedProjectAuthorization() {
        var queries = mock(CollectionQueryPort.class);
        var details = new CollectionQueryPort.CollectionDetails("task", "owned", "project-a", null, null,
                com.dp.deviceops.core.model.CollectionStatus.SUCCEEDED, null, List.of());
        when(queries.find("owned", "task")).thenReturn(java.util.Optional.of(details));
        var controller = new GenericCollectionController(mock(ConnectionRequestMapper.class),
                mock(CollectionSubmissionCoordinator.class), queries, new ProjectClaimAuthorizer(),
                mock(CollectionOutputStreamService.class));
        assertThrows(AccessDeniedException.class,
                () -> controller.get(jwt("service", Map.of("client_namespace", "owned")), "task", "owned"));
        assertSame(details, controller.get(jwt("service", Map.of("client_namespace", "owned",
                "device_ops_projects", List.of("project-a"))), "task", "owned"));
    }

    @Test
    void replayDoesNotLoadSavedConnectionAndStillClearsSubmittedCredentials() {
        var connections = mock(ConnectionRequestMapper.class);
        var submissions = mock(CollectionSubmissionCoordinator.class);
        when(submissions.findExisting(anyString(), anyString(), anyString()))
                .thenReturn(java.util.Optional.of(new CollectionSubmissionCoordinator.Result("existing", true)));
        var controller = new GenericCollectionController(connections, submissions, mock(CollectionQueryPort.class),
                new ProjectClaimAuthorizer(), mock(CollectionOutputStreamService.class));
        char[] secret = "not-persisted".toCharArray();
        var saved = new ConnectionRequestMapper.Connection(null, null, null, null, null, null,
                null, null, null, null, "saved-connection", null, secret, null, null);
        var request = new GenericCollectionController.Request("owned", null, saved,
                new GenericCollectionController.Script("ADHOC_INLINE", "script", "1", "show version", "0".repeat(64),
                        "EXECUTION_ONLY", "NONE", null), null, null, null, 30, 5, 10, null);
        var response = controller.submit(jwt("service", Map.of("client_namespace", "owned")), "replay", request);
        assertEquals("existing", response.collectionId());
        assertTrue(response.existing());
        assertArrayEquals(new char[secret.length], secret);
        verifyNoInteractions(connections);
    }

    static Jwt jwt(String subject, Map<String, Object> additional) {
        var claims = new java.util.LinkedHashMap<String, Object>(additional);
        if (subject != null) claims.put("sub", subject);
        return new Jwt("token", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Map.of("alg", "none"), claims);
    }
}
