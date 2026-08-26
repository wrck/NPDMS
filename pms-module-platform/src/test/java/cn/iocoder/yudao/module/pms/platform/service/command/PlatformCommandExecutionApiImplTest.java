package cn.iocoder.yudao.module.pms.platform.service.command;

import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformIdempotencyRecordDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOperationAuditDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.command.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOperationAuditMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.PlatformOutboxEventMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.command.query.IdempotencyScopeQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class PlatformCommandExecutionApiImplTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    @Mock
    private PlatformIdempotencyRecordMapper idempotencyMapper;
    @Mock
    private PlatformOperationAuditMapper auditMapper;
    @Mock
    private PlatformOutboxEventMapper outboxMapper;

    @InjectMocks
    private PlatformCommandExecutionApiImpl service;

    @Test
    void newExecutionPersistsAllSuccessFacts() {
        doAnswer(invocation -> {
            PlatformIdempotencyRecordDO row = invocation.getArgument(0);
            row.setId(99L);
            return 1;
        }).when(idempotencyMapper).insertIfAbsent(any(PlatformIdempotencyRecordDO.class));
        when(idempotencyMapper.updateById(any(PlatformIdempotencyRecordDO.class))).thenReturn(1);
        when(auditMapper.insert(any(PlatformOperationAuditDO.class))).thenReturn(1);
        when(outboxMapper.insert(any(PlatformOutboxEventDO.class))).thenReturn(1);
        SampleResponse response = response(100L);

        var result = service.execute(scope(), DIGEST_A, SampleResponse.class,
                () -> response, ignored -> facts("100"));

        assertEquals(PlatformCommandExecutionApi.Decision.NEW, result.decision());
        assertEquals(100L, result.response().id());
        verify(auditMapper).insert(any(PlatformOperationAuditDO.class));
        verify(outboxMapper).insert(any(PlatformOutboxEventDO.class));
    }

    @Test
    void newExecutionWithoutBusinessEventSkipsOutbox() {
        doAnswer(invocation -> {
            PlatformIdempotencyRecordDO row = invocation.getArgument(0);
            row.setId(100L);
            return 1;
        }).when(idempotencyMapper).insertIfAbsent(any(PlatformIdempotencyRecordDO.class));
        when(idempotencyMapper.updateById(any(PlatformIdempotencyRecordDO.class))).thenReturn(1);
        when(auditMapper.insert(any(PlatformOperationAuditDO.class))).thenReturn(1);

        var result = service.execute(scope(), DIGEST_A, SampleResponse.class,
                () -> response(101L), ignored -> new PlatformCommandExecutionApi.SuccessFacts(
                        "AUTHORIZATION_GRANT_CREATE", "AuthorizationGrant", "101", "correlation-1",
                        "{}", null, null));

        assertEquals(PlatformCommandExecutionApi.Decision.NEW, result.decision());
        verify(auditMapper).insert(any(PlatformOperationAuditDO.class));
        verifyNoInteractions(outboxMapper);
    }

    @Test
    void newExecutionPersistsMultipleEventsWithProducerEventIds() {
        doAnswer(invocation -> {
            PlatformIdempotencyRecordDO row = invocation.getArgument(0);
            row.setId(102L);
            return 1;
        }).when(idempotencyMapper).insertIfAbsent(any(PlatformIdempotencyRecordDO.class));
        when(idempotencyMapper.updateById(any(PlatformIdempotencyRecordDO.class))).thenReturn(1);
        when(auditMapper.insert(any(PlatformOperationAuditDO.class))).thenReturn(1);
        when(outboxMapper.insert(any(PlatformOutboxEventDO.class))).thenReturn(1);

        service.execute(scope(), DIGEST_A, SampleResponse.class, () -> response(102L), ignored ->
                new PlatformCommandExecutionApi.SuccessFacts("FILE_UPLOAD_COMPLETE", "FileArtifact", "102",
                        "correlation-1", "{}", List.of(
                        new PlatformCommandExecutionApi.BusinessEvent("evt-1", "FileVersionCommitted",
                                "{\"eventId\":\"evt-1\"}"),
                        new PlatformCommandExecutionApi.BusinessEvent("evt-2", "FileReferenceAttached",
                                "{\"eventId\":\"evt-2\"}"))));

        var captor = org.mockito.ArgumentCaptor.forClass(PlatformOutboxEventDO.class);
        verify(outboxMapper, times(2)).insert(captor.capture());
        assertEquals(List.of("evt-1", "evt-2"),
                captor.getAllValues().stream().map(PlatformOutboxEventDO::getEventId).toList());
    }

    @Test
    void rejectsDuplicateOrPayloadMismatchedProducerEventIds() {
        doAnswer(invocation -> {
            PlatformIdempotencyRecordDO row = invocation.getArgument(0);
            row.setId(103L);
            return 1;
        }).when(idempotencyMapper).insertIfAbsent(any(PlatformIdempotencyRecordDO.class));

        assertThrows(IllegalArgumentException.class, () -> service.execute(scope(), DIGEST_A,
                SampleResponse.class, () -> response(103L), ignored ->
                        new PlatformCommandExecutionApi.SuccessFacts("FILE_UPLOAD_COMPLETE", "FileArtifact", "103",
                                "correlation-1", "{}", List.of(
                                new PlatformCommandExecutionApi.BusinessEvent("evt-1", "A",
                                        "{\"eventId\":\"evt-other\"}")))));
        verify(auditMapper, never()).insert(any(PlatformOperationAuditDO.class));
        verify(outboxMapper, never()).insert(any(PlatformOutboxEventDO.class));
    }

    @Test
    void completedSameDigestReplaysWithoutExecutingOperation() {
        when(idempotencyMapper.insertIfAbsent(any(PlatformIdempotencyRecordDO.class))).thenReturn(0);
        PlatformIdempotencyRecordDO existing = existing(DIGEST_A,
                PlatformCommandExecutionApiImpl.STATUS_COMPLETED);
        existing.setResponsePayload("{\"id\":100}");
        when(idempotencyMapper.selectByScope(any(IdempotencyScopeQuery.class)))
                .thenReturn(existing);
        AtomicInteger executions = new AtomicInteger();

        var result = service.execute(scope(), DIGEST_A, SampleResponse.class,
                () -> {
                    executions.incrementAndGet();
                    return response(200L);
                }, ignored -> facts("200"));

        assertEquals(PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, result.decision());
        assertEquals(100L, result.response().id());
        assertEquals(0, executions.get());
        verify(auditMapper, never()).insert(any(PlatformOperationAuditDO.class));
    }

    @Test
    void sameKeyDifferentDigestConflictsWithoutExecutingOperation() {
        when(idempotencyMapper.insertIfAbsent(any(PlatformIdempotencyRecordDO.class))).thenReturn(0);
        when(idempotencyMapper.selectByScope(any(IdempotencyScopeQuery.class)))
                .thenReturn(existing(DIGEST_A, PlatformCommandExecutionApiImpl.STATUS_COMPLETED));
        AtomicInteger executions = new AtomicInteger();

        var result = service.execute(scope(), DIGEST_B, SampleResponse.class,
                () -> {
                    executions.incrementAndGet();
                    return response(200L);
                }, ignored -> facts("200"));

        assertEquals(PlatformCommandExecutionApi.Decision.CONFLICT, result.decision());
        assertEquals(0, executions.get());
    }

    @Test
    void auditFailurePropagatesAndPreventsOutboxWrite() {
        doAnswer(invocation -> {
            PlatformIdempotencyRecordDO row = invocation.getArgument(0);
            row.setId(99L);
            return 1;
        }).when(idempotencyMapper).insertIfAbsent(any(PlatformIdempotencyRecordDO.class));
        when(idempotencyMapper.updateById(any(PlatformIdempotencyRecordDO.class))).thenReturn(1);
        doThrow(new IllegalStateException("audit failed"))
                .when(auditMapper).insert(any(PlatformOperationAuditDO.class));

        assertThrows(IllegalStateException.class, () -> service.execute(scope(), DIGEST_A,
                SampleResponse.class, () -> response(100L), ignored -> facts("100")));

        verify(outboxMapper, never()).insert(any(PlatformOutboxEventDO.class));
    }

    @Test
    void outboxFailurePropagates() {
        doAnswer(invocation -> {
            PlatformIdempotencyRecordDO row = invocation.getArgument(0);
            row.setId(99L);
            return 1;
        }).when(idempotencyMapper).insertIfAbsent(any(PlatformIdempotencyRecordDO.class));
        when(idempotencyMapper.updateById(any(PlatformIdempotencyRecordDO.class))).thenReturn(1);
        when(auditMapper.insert(any(PlatformOperationAuditDO.class))).thenReturn(1);
        doThrow(new IllegalStateException("outbox failed"))
                .when(outboxMapper).insert(any(PlatformOutboxEventDO.class));

        assertThrows(IllegalStateException.class, () -> service.execute(scope(), DIGEST_A,
                SampleResponse.class, () -> response(100L), ignored -> facts("100")));
    }

    private PlatformCommandExecutionApi.IdempotencyScope scope() {
        return new PlatformCommandExecutionApi.IdempotencyScope(
                1L, "POST:/pms/projects", 7L, "key-1");
    }

    private PlatformCommandExecutionApi.SuccessFacts facts(String resourceKey) {
        return new PlatformCommandExecutionApi.SuccessFacts(
                "PROJECT_CREATE", "Project", resourceKey, "correlation-1",
                "{\"templateRevisionId\":10,\"stageCount\":7}",
                "ProjectCreated", "{\"projectId\":" + resourceKey + "}");
    }

    private PlatformIdempotencyRecordDO existing(String digest, String status) {
        PlatformIdempotencyRecordDO row = new PlatformIdempotencyRecordDO();
        row.setRequestDigest(digest);
        row.setStatus(status);
        return row;
    }

    private SampleResponse response(Long id) {
        return new SampleResponse(id);
    }

    private record SampleResponse(Long id) {
    }
}
