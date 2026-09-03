package cn.iocoder.yudao.module.pms.cutover.api.approval;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.api.approval.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.CutoverApprovalInstanceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.CutoverApprovalInstanceMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalFactApiImplTest {
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test
    void inspectsAndLocksCurrentApprovalFact() {
        TenantContextHolder.setTenantId(1L);
        CutoverApprovalApplicationService application = mock(CutoverApprovalApplicationService.class);
        CutoverApprovalInstanceMapper mapper = mock(CutoverApprovalInstanceMapper.class);
        CutoverApprovalInstanceDO row = row();
        when(mapper.selectOne(any())).thenReturn(row);
        when(mapper.selectByIdForUpdate(any())).thenReturn(row);
        CutoverApprovalFactApiImpl api = new CutoverApprovalFactApiImpl(
                new CutoverApprovalFactTransactionExecutor(application, mapper));

        CutoverApprovalInspectResult inspected = api.inspect(new CutoverApprovalFactQuery(1L, 100L, 900L));
        CutoverApprovalFact fact = inspected.fact();
        CutoverApprovalRevalidationResult locked = api.lockAndRevalidate(new CutoverApprovalRevalidationQuery(1L,
                new ExpectedCutoverApprovalFact(fact.approvalInstanceId(), fact.approvalVersion(), fact.taskId(),
                        fact.planRevisionId(), fact.planRevisionNo(), fact.status(), fact.sourceSnapshotVersion(),
                        fact.replacementApprovalInstanceId(), fact.decisionAt(), fact.rejectionReason())));

        assertEquals(InspectStatus.FOUND, inspected.status());
        assertEquals(RevalidationStatus.VALID, locked.status());
    }

    @Test
    void delegatesSourcePauseAndReturnsFrozenFact() {
        TenantContextHolder.setTenantId(1L);
        CutoverApprovalApplicationService application = mock(CutoverApprovalApplicationService.class);
        CutoverApprovalInstanceMapper mapper = mock(CutoverApprovalInstanceMapper.class);
        CutoverApprovalCommandResult expected = new CutoverApprovalCommandResult(CommandOutcome.APPLIED,
                new CutoverApprovalFact(500L, 4, 100L, 900L, 1,
                        ApprovalStatus.PAUSED_SOURCE_INVALIDATED, 1, null, 1000L, null));
        when(application.pause(any())).thenReturn(expected);
        CutoverApprovalFactApiImpl api = new CutoverApprovalFactApiImpl(
                new CutoverApprovalFactTransactionExecutor(application, mapper));

        CutoverApprovalCommandResult actual = api.pauseForSourceInvalidation(new CutoverApprovalPauseCommand(
                1L, 500L, 3, 900L, 1, "SOURCE_FACT_INVALIDATED", "pause-1", "corr-1"));

        assertEquals(expected, actual);
        verify(application).pause(any());
    }

    @Test
    void preservesStableIdempotencyConflictFromApplicationBoundary() {
        TenantContextHolder.setTenantId(1L);
        CutoverApprovalFactTransactionExecutor transactions = mock(CutoverApprovalFactTransactionExecutor.class);
        when(transactions.start(any())).thenThrow(new CutoverApprovalApplicationException(
                CutoverApprovalApplicationException.Code.IDEMPOTENCY_CONFLICT, "幂等载荷冲突"));
        CutoverApprovalFactApiImpl api = new CutoverApprovalFactApiImpl(transactions);

        CutoverApprovalFactException conflict = assertThrows(CutoverApprovalFactException.class,
                () -> api.start(new CutoverApprovalStartCommand(1L, 100L, 5, 900L, 1,
                        "A", 600L, 2, 700L, 3, 1, java.time.LocalDateTime.of(2026, 9, 3, 18, 0),
                        null, "start-1", "corr-1")));

        assertEquals(CutoverApprovalFactException.Code.IDEMPOTENCY_CONFLICT, conflict.code());
    }

    private static CutoverApprovalInstanceDO row() {
        CutoverApprovalInstanceDO row = new CutoverApprovalInstanceDO();
        row.setId(500L); row.setTenantId(1L); row.setTaskId(100L); row.setPlanRevisionId(900L);
        row.setPlanRevisionNo(1); row.setStatusCode("PENDING"); row.setSourceSnapshotVersion(1); row.setVersion(3);
        return row;
    }
}
