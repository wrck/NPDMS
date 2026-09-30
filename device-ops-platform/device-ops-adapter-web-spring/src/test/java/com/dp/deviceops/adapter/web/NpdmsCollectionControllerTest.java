package com.dp.deviceops.adapter.web;

import com.dp.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import com.dp.deviceops.adapter.web.parser.CollectionSemanticResultController;
import com.dp.deviceops.adapter.web.security.ProjectClaimAuthorizer;
import com.dp.deviceops.core.port.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NpdmsCollectionControllerTest {
    @Test void missingCancellationRequiresNamespaceAndAllProjectAuthorityAndReturnsDurableProof() {
        var repo = mock(CollectionRepository.class);
        var dispatcher = mock(KeyedCollectionDispatcher.class);
        var tested = new NpdmsCollectionController(repo, mock(CollectionQueryPort.class), new ProjectClaimAuthorizer(),
                dispatcher, mock(CallbackOutboxPort.class), submissions,
                mock(CollectionSemanticResultController.class), KEY);
        var caller = Jwt.withTokenValue("test").header("alg", "none").subject("npdms")
                .claim("device_ops_namespaces", java.util.List.of("npdms-7"))
                .claim("device_ops_projects", java.util.List.of("*")).build();
        when(repo.cancelBeforeSubmission("npdms-7", "missing")).thenReturn(true);
        tested.cancel(caller, "missing", "npdms-7");
        verify(repo).cancelBeforeSubmission("npdms-7", "missing");
        when(repo.isCancelledBeforeSubmission("npdms-7", "missing")).thenReturn(true);
        var gone = assertThrows(ResponseStatusException.class, () -> tested.get(caller, "missing", "npdms-7"));
        assertEquals(410, gone.getStatusCode().value());
        assertEquals("BEFORE_DISPATCH", gone.getHeaders().getFirst("X-DAC-Cancellation"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tested.cancel(caller, "missing", "npdms-8"));
        var restricted = Jwt.withTokenValue("test").header("alg", "none").subject("npdms-7")
                .claim("device_ops_projects", java.util.List.of("10")).build();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tested.cancel(restricted, "another", "npdms-7"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> tested.get(restricted, "missing", "npdms-7"));
        verify(repo, never()).cancelBeforeSubmission("npdms-7", "another");
        verifyNoInteractions(dispatcher);
    }
    private static final String KEY = "test-only-request-grant-key-32-bytes";
    private final GenericCollectionController submissions = mock(GenericCollectionController.class);
    private final NpdmsCollectionController controller = new NpdmsCollectionController(
            mock(CollectionRepository.class), mock(CollectionQueryPort.class), new ProjectClaimAuthorizer(),
            mock(KeyedCollectionDispatcher.class), mock(CallbackOutboxPort.class), submissions,
            mock(CollectionSemanticResultController.class), KEY);

    @Test void authenticatedGrantDelegatesToExistingExecutionAndClearsSecrets() throws Exception {
        var request = request();
        String now = Long.toString(Instant.now().getEpochSecond());
        var result = new CollectionSubmissionCoordinator.Result("dac", false);
        when(submissions.submit(any(), eq("task"), same(request))).thenReturn(result);
        assertEquals(result, controller.submit(jwt(), "task", now, sign(now), request));
        assertArrayEquals(new char[6], request.connection().password());
    }

    @Test void alteredTaskAndExpiredGrantNeverReachTheExecutor() throws Exception {
        var request = request();
        String now = Long.toString(Instant.now().getEpochSecond());
        assertThrows(ResponseStatusException.class, () -> controller.submit(jwt(), "other-task", now, sign(now), request));
        assertArrayEquals(new char[6], request.connection().password());
        String expired = Long.toString(Instant.now().getEpochSecond() - 61);
        assertThrows(ResponseStatusException.class, () -> controller.submit(jwt(), "task", expired, sign(expired), request()));
        verifyNoInteractions(submissions);
    }

    private GenericCollectionController.Request request() throws Exception {
        return new ObjectMapper().readValue("""
          {"namespace":"npdms-7","externalRequestId":"task",
           "context":{"project":{"namespace":"npdms-7","projectKey":"10"},"device":{"deviceKey":"20"}},
           "connection":{"protocol":"SSH2","host":"device.example","port":22,"username":"operator",
             "authenticationType":"PASSWORD","executionMode":"SHELL","connectTimeoutSeconds":10,"password":"secret"},
           "script":{"source":"EXTERNAL_DELIVERED","key":"plt-template","version":"1","content":"show version",
             "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","policy":"EXECUTION_ONLY","parserType":"NONE"},
           "commandTimeoutSeconds":30,"parseTimeoutSeconds":30,"leaseGraceSeconds":0}
          """, GenericCollectionController.Request.class);
    }
    private String sign(String timestamp) throws Exception {
        String binding = java.util.stream.Stream.of(timestamp, "npdms-7", "task", "10", "20", "device.example", "22",
                "SSH2", "operator", "plt-template", "1", "a".repeat(64), "30", "")
                .map(value -> value.length() + ":" + value).collect(java.util.stream.Collectors.joining());
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(binding.getBytes(StandardCharsets.UTF_8)));
    }
    private Jwt jwt() { return Jwt.withTokenValue("test").header("alg", "none").subject("npdms").build(); }
}
