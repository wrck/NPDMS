package cn.iocoder.yudao.module.pms.project.service.platform;

import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectCreateRespVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.platform.PlatformIdempotencyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.platform.PlatformOperationAuditDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.platform.PlatformOutboxEventDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.platform.PlatformIdempotencyRecordMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.platform.PlatformOperationAuditMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.platform.PlatformOutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCommandExecutionServiceTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    @Mock
    private PlatformIdempotencyRecordMapper idempotencyMapper;
    @Mock
    private PlatformOperationAuditMapper auditMapper;
    @Mock
    private PlatformOutboxEventMapper outboxMapper;

    @InjectMocks
    private ProjectCommandExecutionService service;

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
        ProjectCreateRespVO response = response(100L);

        var result = service.execute(scope(), DIGEST_A, ProjectCreateRespVO.class,
                () -> response, ignored -> facts("100"));

        assertEquals(ProjectCommandExecutionService.Decision.NEW, result.decision());
        assertEquals(100L, result.response().getId());
        verify(auditMapper).insert(any(PlatformOperationAuditDO.class));
        verify(outboxMapper).insert(any(PlatformOutboxEventDO.class));
    }

    @Test
    void completedSameDigestReplaysWithoutExecutingOperation() {
        when(idempotencyMapper.insertIfAbsent(any(PlatformIdempotencyRecordDO.class))).thenReturn(0);
        PlatformIdempotencyRecordDO existing = existing(DIGEST_A,
                ProjectCommandExecutionService.STATUS_COMPLETED);
        existing.setResponsePayload("{\"id\":100}");
        when(idempotencyMapper.selectByScope(1L, "POST:/pms/projects", 7L, "key-1"))
                .thenReturn(existing);
        AtomicInteger executions = new AtomicInteger();

        var result = service.execute(scope(), DIGEST_A, ProjectCreateRespVO.class,
                () -> {
                    executions.incrementAndGet();
                    return response(200L);
                }, ignored -> facts("200"));

        assertEquals(ProjectCommandExecutionService.Decision.REPLAY_COMPLETED, result.decision());
        assertEquals(100L, result.response().getId());
        assertEquals(0, executions.get());
        verify(auditMapper, never()).insert(any(PlatformOperationAuditDO.class));
    }

    @Test
    void sameKeyDifferentDigestConflictsWithoutExecutingOperation() {
        when(idempotencyMapper.insertIfAbsent(any(PlatformIdempotencyRecordDO.class))).thenReturn(0);
        when(idempotencyMapper.selectByScope(1L, "POST:/pms/projects", 7L, "key-1"))
                .thenReturn(existing(DIGEST_A, ProjectCommandExecutionService.STATUS_COMPLETED));
        AtomicInteger executions = new AtomicInteger();

        var result = service.execute(scope(), DIGEST_B, ProjectCreateRespVO.class,
                () -> {
                    executions.incrementAndGet();
                    return response(200L);
                }, ignored -> facts("200"));

        assertEquals(ProjectCommandExecutionService.Decision.CONFLICT, result.decision());
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
                ProjectCreateRespVO.class, () -> response(100L), ignored -> facts("100")));

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
                ProjectCreateRespVO.class, () -> response(100L), ignored -> facts("100")));
    }

    private ProjectCommandExecutionService.IdempotencyScope scope() {
        return new ProjectCommandExecutionService.IdempotencyScope(
                1L, "POST:/pms/projects", 7L, "key-1");
    }

    private ProjectCommandExecutionService.SuccessFacts facts(String resourceKey) {
        return new ProjectCommandExecutionService.SuccessFacts(
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

    private ProjectCreateRespVO response(Long id) {
        ProjectCreateRespVO response = new ProjectCreateRespVO();
        response.setId(id);
        return response;
    }
}
