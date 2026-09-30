package com.dp.deviceops.adapter.web.parser;

import com.dp.deviceops.parser.runtime.model.ParseTaskState;
import com.dp.deviceops.parser.runtime.port.ParseResultQueryPort;
import com.dp.deviceops.parser.runtime.port.ParseTaskRepository.SubmitOutcome;
import com.dp.deviceops.parser.runtime.port.ParserPayloadStore;
import com.dp.deviceops.parser.runtime.service.ParseTaskService;
import com.dp.deviceops.parser.runtime.service.ParserRuntimeError;
import com.dp.deviceops.parser.semantic.ParserCoordinate;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParseTaskControllerTest {

    @Test
    void derivesNamespaceAndReturnsExactPinnedCoordinate() throws Exception {
        ParseTaskService service = mock(ParseTaskService.class);
        ParserPayloadStore payloads = mock(ParserPayloadStore.class);
        ParserControlProperties properties = new ParserControlProperties();
        when(payloads.putScoped(any(), any(), any())).thenReturn("payload-1");
        ParserCoordinate coordinate = new ParserCoordinate("show-tech", "1.0.0", "1.0.0",
                "1.0.0", "1.0.0", null, null);
        when(service.submit(any())).thenReturn(new SubmitOutcome(
                "task-1", ParseTaskState.QUEUED, "release-1", coordinate, null));
        ParseTaskController controller = new ParseTaskController(service,
                mock(ParseResultQueryPort.class), payloads, properties);

        ParseTaskController.SubmissionResponse response = controller.submit(jwt("client-1", "npdp"),
                "request-1", new ParseTaskController.SubmitRequest("show-tech", null,
                        "command-output-block/v1", null, "{}", "application/json",
                        Map.of("projectKey", "P-001"), "npdp"));

        assertEquals("task-1", response.taskId());
        assertEquals(coordinate, response.coordinate());
        verify(service).submit(new ParseTaskService.SubmitCommand("request-1", "npdp", "show-tech", null,
                "command-output-block/v1", "payload-1", Map.of("projectKey", "P-001"), null, "npdp"));
    }

    @Test
    void rejectsAmbiguousPayloadAndPropagatesUnknownConsumerCode() throws Exception {
        ParseTaskService service = mock(ParseTaskService.class);
        ParseTaskController controller = new ParseTaskController(service,
                mock(ParseResultQueryPort.class), mock(ParserPayloadStore.class), new ParserControlProperties());
        assertThrows(IllegalArgumentException.class, () -> controller.submit(jwt("client-1", null), "request-1",
                new ParseTaskController.SubmitRequest("show-tech", null, "line-log/v1",
                        "payload-1", "raw", "text/plain", Map.of(), null)));
        when(service.submit(any())).thenThrow(new ParserRuntimeError("RESULT_CONSUMER_NOT_FOUND"));
        ParserRuntimeError error = assertThrows(ParserRuntimeError.class, () -> controller.submit(
                jwt("client-1", null), "request-2", new ParseTaskController.SubmitRequest("show-tech", null,
                        "line-log/v1", null, "raw", "text/plain", Map.of(), "unknown")));
        assertEquals("RESULT_CONSUMER_NOT_FOUND", error.code());
    }

    @Test
    void rejectsUnownedInputBeforeSubmittingATask() {
        ParseTaskService service = mock(ParseTaskService.class);
        ParseTaskController controller = new ParseTaskController(service,
                mock(ParseResultQueryPort.class), mock(ParserPayloadStore.class), new ParserControlProperties());
        assertThrows(ParserApiNotFound.class, () -> controller.submit(jwt("client-1", "ns"), "key",
                new ParseTaskController.SubmitRequest("show-tech", null, "line-log/v1",
                        "foreign-payload", null, null, Map.of(), null)));
        org.mockito.Mockito.verifyNoInteractions(service);
    }

    @Test
    void rejectsOversizedUtf8InputBeforeStoringIt() {
        ParserPayloadStore payloads = mock(ParserPayloadStore.class);
        ParseTaskController controller = new ParseTaskController(mock(ParseTaskService.class),
                mock(ParseResultQueryPort.class), payloads, new ParserControlProperties());
        ParserRuntimeError error = assertThrows(ParserRuntimeError.class, () -> controller.submit(jwt("client-1", "ns"), "key",
                new ParseTaskController.SubmitRequest("show-tech", null, "line-log/v1",
                        null, "中".repeat(3_000_000), null, Map.of(), null)));
        assertEquals("INPUT_TOO_LARGE", error.code());
        org.mockito.Mockito.verifyNoInteractions(payloads);
    }

    @Test
    void rejectsTokenWithoutNamespaceOrSubject() {
        ParseTaskController controller = new ParseTaskController(mock(ParseTaskService.class),
                mock(ParseResultQueryPort.class), mock(ParserPayloadStore.class), new ParserControlProperties());
        Jwt anonymousClient = new Jwt("token", Instant.EPOCH, Instant.EPOCH.plusSeconds(60),
                Map.of("alg", "none"), Map.of("scope", "parser:task:read"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.listTasks(anonymousClient, 50, null, null));
    }

    @Test
    void taskResultDistinguishesMissingTaskFromResultNotReady() {
        ParseResultQueryPort queries = mock(ParseResultQueryPort.class);
        ParseTaskController controller = new ParseTaskController(mock(ParseTaskService.class), queries,
                mock(ParserPayloadStore.class), new ParserControlProperties());
        var coordinate = new ParserCoordinate("show-tech", "1.0.0", "1.0.0", "1.0.0", "1.0.0", null, null);
        var queued = new com.dp.deviceops.parser.runtime.model.ParseTask("task-1", "key", "ns", "show-tech",
                "release-1", coordinate, "line-log/v1", "payload-1", Map.of(), null, null, null,
                ParseTaskState.QUEUED, null, 0, Instant.EPOCH, null, 0, null, null, Instant.EPOCH, Instant.EPOCH,
                null, null);
        when(queries.findTask("ns", "task-1")).thenReturn(java.util.Optional.of(queued));
        ParserRuntimeError error = assertThrows(ParserRuntimeError.class,
                () -> controller.getTaskResult(jwt("subject", "ns"), "task-1"));
        assertEquals("RESULT_NOT_READY", error.code());
        assertThrows(ParserApiNotFound.class, () -> controller.getTaskResult(jwt("subject", "ns"), "missing"));
        verify(queries, org.mockito.Mockito.never()).findResult(any(), any());
    }

    private static Jwt jwt(String subject, String namespace) {
        Map<String, Object> claims = namespace == null ? Map.of("sub", subject)
                : Map.of("sub", subject, "client_namespace", namespace);
        return new Jwt("token", Instant.EPOCH, Instant.EPOCH.plusSeconds(60), Map.of("alg", "none"), claims);
    }
}
