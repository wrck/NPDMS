package cn.iocoder.yudao.module.pms.platform.service.collection;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.api.collection.dto.CollectionTaskDTO;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionRequestDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTaskDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionRequestMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.CollectionTaskMapper;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CollectionBusinessResultDeliveryServiceTest {
    PlatformOutboxDeliveryApi outbox = mock(PlatformOutboxDeliveryApi.class);
    CollectionRequestMapper requests = mock(CollectionRequestMapper.class);
    CollectionTaskMapper taskMapper = mock(CollectionTaskMapper.class);
    CollectionTaskApi tasks = mock(CollectionTaskApi.class);
    CollectionCallbackApi callbacks = mock(CollectionCallbackApi.class);
    CollectionBusinessResultReceiver receiver = mock(CollectionBusinessResultReceiver.class);
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    CollectionBusinessResultDeliveryService service;
    CollectionRequestDO row;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(receiver.entries()).thenReturn(Set.of("configuration"));
        row = new CollectionRequestDO(); row.setId(41L); row.setTenantId(1L); row.setObjectId(11L);
        row.setEntry("configuration"); row.setActorId(7L); row.setRequestKey("request-key-123456"); row.setCommandText("show run");
        when(requests.findByTask(1L, "task")).thenReturn(row);
        when(requests.updateById(any(CollectionRequestDO.class))).thenReturn(1);
        when(tasks.getTask(1L, "task")).thenReturn(task("RESULT_AVAILABLE", 51L));
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(1L,"task")).thenAnswer(call -> {
            var current = tasks.getTask(1L,"task"); var row = new CollectionTaskDO();
            row.setStatus(current.status()); row.setResultVersion(current.resultVersion()); row.setFileVersionId(current.fileVersionId()); return row;
        });
        service = new CollectionBusinessResultDeliveryService(outbox, requests, taskMapper, tasks, callbacks, List.of(receiver), tx);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void ownerMustPersistBeforeSuccessfulConsumption() {
        service.deliver(1L, "task");
        var order = inOrder(receiver, callbacks, requests, tx);
        order.verify(receiver).receive(argThat(r -> r.objectId().equals(11L) && r.fileVersionId().equals(51L) && r.commandText().equals("show run")));
        order.verify(callbacks).confirmConsumption(any());
        order.verify(requests).updateById(any(CollectionRequestDO.class));
        order.verify(tx).commit(any());
        assertEquals(1L, row.getConsumedResultVersion());
    }
    @Test void failedBusinessReceiptRollsBackAndDoesNotConsume() {
        doThrow(new IllegalStateException("owner unavailable")).when(receiver).receive(any());
        assertThrows(IllegalStateException.class, () -> service.deliver(1L, "task"));
        verifyNoInteractions(callbacks); verify(requests, never()).updateById(any(CollectionRequestDO.class)); verify(tx).rollback(any());
    }
    @Test void concurrentDeliveryAlreadyAcknowledgedIsNotConsumedTwice() {
        var completed = new CollectionTaskDO(); completed.setStatus("COMPLETED"); completed.setResultVersion(1L); completed.setFileVersionId(51L);
        when(taskMapper.selectByTenantAndPlatformTaskIdForUpdate(1L,"task")).thenReturn(completed);
        service.deliver(1L,"task"); verify(receiver).receive(any()); verifyNoInteractions(callbacks);
        verify(requests,never()).updateById(any(CollectionRequestDO.class));
    }
    @Test void failedAndCancelledPartialLogsReturnWithoutSuccessAcknowledgement() {
        for (String status : List.of("FAILED", "CANCELLED")) {
            when(tasks.getTask(1L, "task")).thenReturn(task(status, 51L)); service.deliver(1L, "task");
        }
        verify(receiver, times(2)).receive(any()); verifyNoInteractions(callbacks);
    }
    @Test void quarantineAndTenantMismatchNeverReachBusinessOwner() {
        when(tasks.getTask(1L, "task")).thenReturn(task("SECURITY_EXCEPTION", 51L));
        assertThrows(IllegalStateException.class, () -> service.deliver(1L, "task"));
        assertThrows(IllegalStateException.class, () -> service.deliver(2L, "task"));
        verify(receiver, never()).receive(any()); verifyNoInteractions(callbacks);
    }
    @Test void outboxFailureRemainsRetryableAndRestartCanComplete() {
        var event = new PlatformOutboxMessageDTO("event", "CollectionResultAvailable", "{\"platformTaskId\":\"task\",\"resultVersion\":1,\"fileVersionId\":51}", 0, 1L, LocalDateTime.now());
        when(outbox.claimDue(any())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("owner unavailable")).doNothing().when(receiver).receive(any());
        assertTrue(service.deliverDue().contains("待重试 1"));
        verify(outbox).scheduleRetry(eq("event"), eq(0), any()); verify(outbox, never()).markDelivered(anyString(), anyInt());
        assertTrue(service.deliverDue().contains("回传 1")); verify(outbox).markDelivered("event", 0);
    }
    @Test void mismatchedOutboxFileDoesNotDeliver() {
        when(outbox.claimDue(any())).thenReturn(List.of(new PlatformOutboxMessageDTO("event", "CollectionResultAvailable",
                "{\"platformTaskId\":\"task\",\"resultVersion\":1,\"fileVersionId\":99}", 0, 1L, LocalDateTime.now())));
        service.deliverDue(); verify(receiver, never()).receive(any()); verify(outbox).scheduleRetry(eq("event"), eq(0), any());
    }
    private CollectionTaskDTO task(String status, Long file) {
        return new CollectionTaskDTO(1L, 2L, "task", "IMP", "Configuration", "11", "21", "31", "device", "host", 22,
                "SSH", "template", "1", "hash", "TEMPORARY_SECRET", null, null, "key", "BUSINESS_CONSUMPTION", status,
                "RESULT_RECEIVED", 1L, file, "IMP", "Configuration", "11", status.equals("RESULT_AVAILABLE") ? "SUCCEEDED" : status, null);
    }
}
