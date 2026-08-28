package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanVersionUpdate;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.CreateInitialDurationCommand;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConstructionPlanApplicationServiceTest {

    @Mock ConstructionPlanMapper planMapper;
    @Mock ConstructionPlanRevisionMapper revisionMapper;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;
    @Mock OperationAuditApi operationAuditApi;
    @Mock TransactionTemplate transactionTemplate;
    private ConstructionPlanApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ConstructionPlanApplicationService(planMapper, revisionMapper,
                commandExecutionApi, permissionApi, projectScopeApi, participantFactApi,
                operationAuditApi, transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
    }

    @Test
    void createsDateRangeBaselineWithPointersAndSafeAudit() {
        stubAuthorizedCommandExecution();

        var response = service.createInitial(command("DATE_RANGE",
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), 5), actor());

        assertEquals(501L, response.getPlanId());
        assertEquals(701L, response.getCurrentRevision().getRevisionId());
        assertEquals(5, response.getCurrentRevision().getDurationDays());
        assertEquals(ConstructionPlanDO.RECALCULATION_PENDING,
                response.getPlanRecalculationStatus());
        assertEquals(1, response.getPlanVersion());
        ArgumentCaptor<ConstructionPlanVersionUpdate> update =
                ArgumentCaptor.forClass(ConstructionPlanVersionUpdate.class);
        verify(planMapper).updateVersionIfMatch(update.capture());
        assertEquals(701L, update.getValue().currentDurationRevisionId());
        assertEquals(701L, update.getValue().planRecalculationSourceRevisionId());
    }

    @Test
    void derivesEndDateForDurationFromStart() {
        stubAuthorizedCommandExecution();

        var response = service.createInitial(command("DURATION_FROM_START",
                        LocalDate.of(2026, 9, 1), null, 3), actor());

        assertEquals(LocalDate.of(2026, 9, 3), response.getCurrentRevision().getEndDate());
    }

    @Test
    void rejectsWithoutManagePermissionBeforeAnyWrite() {
        when(permissionApi.hasAnyPermissions(9L,
                ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(false);

        assertThrows(ServiceException.class, () -> service.createInitial(command("DATE_RANGE",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), 5), actor()));

        verify(planMapper, never()).insert(any());
        verify(commandExecutionApi, never()).execute(any(), any(), any(), any(), any());
        verify(operationAuditApi).record(eq(0L), eq(9L), eq("corr-1"),
                eq("CONSTRUCTION_PLAN_INITIAL_DURATION_CREATE"), eq("ConstructionPlan"),
                eq("100"), eq("REJECTED"), any());
    }

    @Test
    void rejectsProjectOutsideManageScopeBeforeParticipantLockAndWrite() {
        when(permissionApi.hasAnyPermissions(9L,
                ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                0L, 9L, 100L, ProjectScopeApi.ACTION_MANAGE)))
                .thenReturn(new ProjectScopeResult(100L, 7L, Set.of(), Set.of()));

        assertThrows(ServiceException.class, () -> service.createInitial(command("DATE_RANGE",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), 5), actor()));

        verify(participantFactApi, never()).lockAndRevalidate(any());
        verify(commandExecutionApi, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsIdempotencyPayloadConflictWithoutBusinessWrite() {
        stubScopeAndParticipant();
        when(commandExecutionApi.execute(any(), any(), eq(cn.iocoder.yudao.module.pms.engineering
                        .controller.admin.constructionplan.vo.ConstructionPlanRespVO.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        assertThrows(ServiceException.class, () -> service.createInitial(command("DATE_RANGE",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), 5), actor()));

        verify(planMapper, never()).insert(any());
    }

    private void stubAuthorizedCommandExecution() {
        stubScopeAndParticipant();
        when(planMapper.selectByProjectId(0L, 100L)).thenReturn(null);
        when(planMapper.insert(any())).thenAnswer(invocation -> {
            ((ConstructionPlanDO) invocation.getArgument(0)).setId(501L);
            return 1;
        });
        when(revisionMapper.insert(any())).thenAnswer(invocation -> {
            ((ConstructionPlanRevisionDO) invocation.getArgument(0)).setId(701L);
            return 1;
        });
        when(planMapper.updateVersionIfMatch(any())).thenReturn(1);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(3);
            Object response = operation.get();
            Function<Object, PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            PlatformCommandExecutionApi.SuccessFacts audit = facts.apply(response);
            assertEquals("CONSTRUCTION_PLAN_INITIAL_DURATION_CREATE", audit.operationCode());
            assertNotNull(audit.detailSnapshot());
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, response);
        });
    }

    private void stubScopeAndParticipant() {
        when(permissionApi.hasAnyPermissions(9L,
                ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(true);
        when(projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                0L, 9L, 100L, ProjectScopeApi.ACTION_MANAGE)))
                .thenReturn(new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of()));
        when(participantFactApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                100L, 9L, 3, "ACTIVE", "S1", Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER))))
                .thenReturn(new ProjectParticipantFact(100L, 9L,
                        Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER), "PRIMARY",
                        "ACTIVE", "S1", 3, 3L));
    }

    private CreateInitialDurationCommand command(String basis, LocalDate start,
                                                  LocalDate end, Integer days) {
        return new CreateInitialDurationCommand(100L, basis, start, end, days, 3,
                "idem-1", "a".repeat(64));
    }

    private ConstructionPlanApplicationService.Actor actor() {
        return new ConstructionPlanApplicationService.Actor(0L, 9L, "corr-1");
    }
}
