package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.persistence.jdbc.JdbcDesktopTaskStore;
import com.dp.plat.deviceops.adapter.web.concurrency.KeyedCollectionDispatcher;
import com.dp.plat.deviceops.core.port.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DesktopSynchronizationTest {
    @Test void separateDesktopDatabasesScopeCollidingEventsAndRetryTheSameCallback() throws Exception {
        String destination = "http://npdms.example/desktop/callbacks";
        // Both independent databases start with target 1, attempt 1, SUCCEEDED.
        String localEventId = "0febd4673f30e9c7f16db339f4753eea161a36119cc3e25e876785ba81240c0f";
        var first = new CallbackOutboxPort.Event(localEventId, destination,
                "{\"eventId\":\"" + localEventId + "\",\"namespace\":\"npdms-1\",\"externalRequestId\":\"task-one\",\"collectionId\":\"collection-one\",\"status\":\"SUCCEEDED\",\"stdout\":\"first original log\"}", 0);
        var second = new CallbackOutboxPort.Event(localEventId, destination,
                "{\"eventId\":\"" + localEventId + "\",\"namespace\":\"npdms-1\",\"externalRequestId\":\"task-two\",\"collectionId\":\"collection-two\",\"status\":\"SUCCEEDED\",\"stdout\":\"second original log\"}", 0);
        var builder = RestClient.builder();
        var upstream = MockRestServiceServer.bindTo(builder).build();
        var callbackIds = new ArrayList<String>();
        for (var event : List.of(first, second, first)) {
            upstream.expect(requestTo(destination)).andExpect(request -> {
                String callbackId = request.getHeaders().getFirst("X-DAC-Nonce");
                assertNotNull(callbackId);
                assertTrue(callbackId.matches("[a-f0-9]{64}"));
                assertNotEquals(localEventId, callbackId, "a local event ID can already belong to another DAC");
                String body = ((MockClientHttpRequest) request).getBodyAsString();
                assertTrue(body.contains(event.payload()), "the original event bytes must stay unchanged");
                assertTrue(body.contains("\"callbackId\":\"" + callbackId + "\""));
                callbackIds.add(callbackId);
            }).andRespond(request -> {
                // An acknowledgement for the old colliding ID must not mark the first event delivered.
                String ack = callbackIds.size() == 1 ? localEventId : request.getHeaders().getFirst("X-DAC-Nonce");
                return withSuccess("{\"status\":\"ACKNOWLEDGED\",\"receiptId\":12,\"callbackId\":\"" + ack + "\"}",
                        MediaType.APPLICATION_JSON).createResponse(request);
            });
        }
        var client = builder.build();
        var firstDesktop = deliveryFixture(first, client);
        var secondDesktop = deliveryFixture(second, client);
        firstDesktop.synchronization().synchronize();
        verify(firstDesktop.outbox()).reschedule(eq(localEventId), anyString(), eq(1), any(), eq("acknowledgement pending"));
        verify(firstDesktop.outbox(), never()).markDelivered(any(), any(), any());
        secondDesktop.synchronization().synchronize();
        firstDesktop.synchronization().synchronize();
        upstream.verify();
        assertNotEquals(callbackIds.get(0), callbackIds.get(1));
        assertEquals(callbackIds.get(0), callbackIds.get(2), "a retry keeps its scoped callback identity");
        verify(firstDesktop.outbox()).markDelivered(eq(localEventId), anyString(), any());
        verify(secondDesktop.outbox()).markDelivered(eq(localEventId), anyString(), any());
        verifyNoInteractions(firstDesktop.dispatcher(), secondDesktop.dispatcher());
    }

    private DeliveryFixture deliveryFixture(CallbackOutboxPort.Event event, RestClient client) throws Exception {
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(event.payload());
        var task = new JdbcDesktopTaskStore.Task("npdms-1", payload.path("externalRequestId").asText(), 1, "42", "7",
                UUID.randomUUID().toString(), new byte[0], new byte[0], "v1", payload.path("collectionId").asText(), false);
        var tasks = mock(JdbcDesktopTaskStore.class);
        var repository = mock(CollectionRepository.class);
        var queries = mock(CollectionQueryPort.class);
        var outbox = mock(CallbackOutboxPort.class);
        var dispatcher = mock(KeyedCollectionDispatcher.class);
        var server = mock(DesktopServerClient.class);
        when(tasks.pending()).thenReturn(List.of());
        when(tasks.claimDue(any())).thenReturn(List.of(event), List.of(), List.of(event), List.of());
        when(tasks.find(task.key())).thenReturn(Optional.of(task));
        when(tasks.token(task)).thenAnswer(invocation -> "fixture-task-callback-key-0123456789".toCharArray());
        when(repository.findByIdempotencyKey(task.namespace(), task.platformTaskId())).thenReturn(Optional.empty());
        when(server.control(any())).thenReturn(new DesktopServerClient.Control(false, false));
        when(server.callbackUrl()).thenReturn(event.destination());
        when(server.callbackClient(anyString())).thenReturn(client);
        return new DeliveryFixture(new DesktopSynchronization(tasks, repository, queries, outbox, dispatcher, server), outbox, dispatcher);
    }

    private record DeliveryFixture(DesktopSynchronization synchronization, CallbackOutboxPort outbox,
                                   KeyedCollectionDispatcher dispatcher) { }

    @Test void repeatedOfflineDeliveryPreservesOriginalEventAndNeverExhaustsOrRecollects() {
        var tasks = mock(JdbcDesktopTaskStore.class);
        var repository = mock(CollectionRepository.class);
        var queries = mock(CollectionQueryPort.class);
        var outbox = mock(CallbackOutboxPort.class);
        var dispatcher = mock(KeyedCollectionDispatcher.class);
        var server = mock(DesktopServerClient.class);
        when(server.callbackUrl()).thenReturn("https://npdms.example/api/callbacks");
        var task = new JdbcDesktopTaskStore.Task("npdms-1", "task-1", 1, "42", "7", UUID.randomUUID().toString(),
                new byte[0], new byte[0], "v1", "collection-1", false);
        var event = new CallbackOutboxPort.Event("event-1", server.callbackUrl(),
                "{\"namespace\":\"npdms-1\",\"externalRequestId\":\"task-1\",\"collectionId\":\"collection-1\"}", 100);
        when(tasks.pending()).thenReturn(List.of());
        when(tasks.claimDue(any())).thenReturn(List.of(event));
        when(tasks.find(task.key())).thenReturn(Optional.of(task));
        when(tasks.token(task)).thenAnswer(invocation -> "test-capability".toCharArray());
        when(repository.findByIdempotencyKey("npdms-1", "task-1")).thenReturn(Optional.empty());
        when(server.control(any())).thenThrow(new org.springframework.web.client.ResourceAccessException("offline"));
        var synchronization = new DesktopSynchronization(tasks, repository, queries, outbox, dispatcher, server);
        synchronization.synchronize();
        synchronization.synchronize();
        verify(outbox, times(2)).reschedule(eq("event-1"), anyString(), eq(101), any(), eq("delivery pending"));
        verify(outbox, never()).markDeadLetter(any(), any(), anyInt(), any(), any());
        verify(outbox, never()).markDelivered(any(), any(), any());
        verify(tasks, never()).acknowledge(any());
        verifyNoInteractions(dispatcher);
        assertTrue(event.payload().contains("collection-1"));
    }
}
