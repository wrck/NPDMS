package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.port.CollectionQueryPort;
import com.dp.deviceops.parser.runtime.model.*;
import com.dp.deviceops.parser.runtime.port.*;
import com.dp.deviceops.parser.runtime.service.ParseTaskService;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceLogParseControllerTest {
    @Test void mixedDeviceSerialsRequireSplittingBeforeAnyParserPayloadIsStored() {
        var mixed=request("tenant-a","project-a",null,"sw-a#show version\nSerial Number: SN-A\nsw-b#show version\nSerial Number: SN-B\n");
        var failure=assertThrows(IllegalArgumentException.class,() -> controller.submit(jwt,mixed));
        assertEquals("MULTI_DEVICE_LOG_REQUIRES_SPLIT",failure.getMessage());
        verifyNoInteractions(payloads,service);
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    static class SecurityConfig { }
    ParseTaskService service = mock(ParseTaskService.class);
    ParseResultQueryPort results = mock(ParseResultQueryPort.class);
    ParserPayloadStore payloads = mock(ParserPayloadStore.class);
    CollectionQueryPort collections = mock(CollectionQueryPort.class);
    DeviceLogParseController controller = new DeviceLogParseController(service, results, payloads,
            new ParserControlProperties(), new ProjectClaimAuthorizer(), collections);
    Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("caller")
            .claim("device_ops_namespaces", List.of("tenant-a"))
            .claim("device_ops_projects", List.of("project-a")).build();

    @Test void namespaceAndProjectAreCheckedBeforeReadingOrPersistingInput() {
        assertThrows(AccessDeniedException.class, () -> controller.submit(jwt, request("tenant-b", "project-a", null, "log")));
        assertThrows(AccessDeniedException.class, () -> controller.submit(jwt, request("tenant-a", "project-b", null, "log")));
        verifyNoInteractions(service, results, payloads, collections);
    }
    @Test void methodSecurityAcceptsUploadAndRequiresReadScopeForCollectedLogs() throws Exception {
        var security = org.springframework.security.core.context.SecurityContextHolder.getContext();
        security.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "caller", "unused", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_parser:task:create"))));
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.register(SecurityConfig.class);
            context.registerBean("logParser", DeviceLogParseController.class, () -> controller);
            context.refresh();
            var secured = context.getBean(DeviceLogParseController.class);
            assertThrows(AccessDeniedException.class, () -> secured.submit(jwt, request("tenant-a", "project-a", "collection-1", null)));
            when(payloads.putScoped(any(), any(), any())).thenReturn("safe-payload");
            var sentinel = new com.dp.deviceops.parser.runtime.service.ParserRuntimeError("TEST_REACHED_SERVICE");
            when(service.submit(any())).thenThrow(sentinel);
            assertSame(sentinel, assertThrows(com.dp.deviceops.parser.runtime.service.ParserRuntimeError.class,
                    () -> secured.submit(jwt, request("tenant-a", "project-a", null, "show run\noutput"))));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
    @Test void collectedSourceUsesGenericCollectionIdentityAndCannotOverrideText() {
        assertThrows(IllegalArgumentException.class, () -> controller.submit(jwt, request("tenant-a", "project-a", "collection-1", "replacement")));
        assertThrows(ParserApiNotFound.class, () -> controller.submit(jwt, request("tenant-a", "project-a", "collection-1", null)));
        verify(collections).find("tenant-a", "project-a", "collection-1");
        verifyNoInteractions(payloads, service);
    }
    @Test void onlySanitizedInputIsPersistedAndUploadExecutionIsExplicitlyUnverified() throws Exception {
        StringBuilder persisted = new StringBuilder();
        when(payloads.putScoped(eq("tenant-a"), eq("application/json"), any())).thenAnswer(call -> {
            persisted.append(new String(call.<InputStream>getArgument(2).readAllBytes(), StandardCharsets.UTF_8));
            return "safe-payload";
        });
        var coordinate = new ParserCoordinate("device-command-output", "1.4.0", "1.4.0", "1.0.0", "1.0.0", null, null);
        when(service.submit(any())).thenReturn(new ParseTaskRepository.SubmitOutcome("task-1", ParseTaskState.QUEUED, "release-1", coordinate, null));
        ParseTask task = mock(ParseTask.class);
        when(task.taskId()).thenReturn("task-1"); when(task.requestId()).thenReturn("device-log:0123456789abcdef");
        when(task.callerNamespace()).thenReturn("tenant-a"); when(task.coordinate()).thenReturn(coordinate);
        when(task.state()).thenReturn(ParseTaskState.QUEUED);
        when(task.contextSnapshot()).thenReturn(Map.of("projectKey", "project-a"));
        when(results.findTask("tenant-a", "task-1")).thenReturn(Optional.of(task));
        controller.submit(jwt, request("tenant-a", "project-a", null, "show run\npassword synthetic-secret"));
        assertFalse(persisted.toString().contains("synthetic-secret"));
        assertTrue(persisted.toString().contains("[REDACTED]"));
        var sent = ArgumentCaptor.forClass(ParseTaskService.SubmitCommand.class);
        verify(service).submit(sent.capture());
        assertEquals(false, sent.getValue().contextSnapshot().get("executionVerified"));
        assertNotNull(sent.getValue().contextSnapshot().get("originalCommandHintSha256"));
        verifyNoInteractions(collections);
    }
    private DeviceLogParseController.Submit request(String namespace, String project, String source, String text) {
        return new DeviceLogParseController.Submit(namespace, project, "0123456789abcdef", source, text, null);
    }
}
