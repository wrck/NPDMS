package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo.DurationChangeSubmitRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanChangeDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanChangeMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanChangeVersionUpdate;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.command.SubmitDurationChangeCommand;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.DURATION_CHANGE_FILE_ARTIFACT_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DurationChangeSubmitServiceTest {

    @Mock ConstructionPlanMapper planMapper;
    @Mock ConstructionPlanRevisionMapper revisionMapper;
    @Mock ConstructionPlanChangeMapper changeMapper;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectParticipantFactApi participantFactApi;
    @Mock DictDataApi dictDataApi;
    @Mock ConfigApi configApi;
    @Mock BpmProcessInstanceApi processInstanceApi;
    @Mock TransactionTemplate transactionTemplate;

    private DurationChangeApplicationService service;

    @BeforeEach
    void setUp() {
        DurationChangeProperties properties = new DurationChangeProperties();
        properties.setProcessDefinitionKey("pms-sol-duration-change");
        service = new DurationChangeApplicationService(planMapper, revisionMapper, changeMapper,
                commandExecutionApi, operationAuditApi, permissionApi, projectScopeApi,
                participantFactApi, dictDataApi, configApi, processInstanceApi, properties,
                transactionTemplate);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(org.mockito.Mockito.mock(TransactionStatus.class));
        });
    }

    @Test
    void submitsNonEvidenceChangeWithFrozenApproverAndStandardVariables() {
        stubCommandExecution();
        stubAuthorizedFacts(10L);
        stubRows(change("INTERNAL_ADJUSTMENT"), plan());
        stubReasonConfiguration();
        when(processInstanceApi.createProcessInstance(any(), any())).thenReturn("bpm-801");
        when(revisionMapper.freezeForSubmitIfMatch(any())).thenReturn(1);
        when(changeMapper.updateVersionIfMatch(any())).thenReturn(1);
        when(planMapper.updateVersionIfMatch(any())).thenReturn(1);

        DurationChangeSubmitRespVO response = service.submit(command(), actor());

        assertEquals("bpm-801", response.getProcessInstanceId());
        assertEquals(ConstructionPlanChangeDO.STATUS_PENDING_APPROVAL, response.getStatus());
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpm = ArgumentCaptor.forClass(
                BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(org.mockito.ArgumentMatchers.eq(9L), bpm.capture());
        assertEquals("801", bpm.getValue().getBusinessKey());
        assertEquals(100L, bpm.getValue().getVariables().get("projectId"));
        assertEquals(501L, bpm.getValue().getVariables().get("constructionPlanId"));
        assertEquals(801L, bpm.getValue().getVariables().get("durationChangeId"));
        assertEquals(List.of(10L), bpm.getValue().getStartUserSelectAssignees()
                .get("serviceManagerApprove"));
        ArgumentCaptor<ConstructionPlanChangeVersionUpdate> update = ArgumentCaptor.forClass(
                ConstructionPlanChangeVersionUpdate.class);
        verify(changeMapper).updateVersionIfMatch(update.capture());
        assertEquals(false, update.getValue().customerEvidenceRequired());
        assertEquals(null, update.getValue().customerEvidenceFileId());
        assertEquals(10L, update.getValue().approverUserId());
    }

    @Test
    void replaysCompletedSubmissionWithoutCreatingAnotherProcess() {
        DurationChangeSubmitRespVO replay = new DurationChangeSubmitRespVO();
        replay.setChangeId(801L);
        replay.setProcessInstanceId("bpm-801");
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, replay));

        DurationChangeSubmitRespVO response = service.submit(command(), actor());

        assertEquals("bpm-801", response.getProcessInstanceId());
        verify(processInstanceApi, never()).createProcessInstance(any(), any());
        verify(planMapper, never()).selectForUpdate(any());
    }

    @Test
    void failsClosedWhenRequiredFileArtifactProviderIsUnavailable() {
        stubCommandExecution();
        stubAuthorizedFacts(10L);
        stubRows(change("CUSTOMER_DELAY"), plan());
        stubReasonConfiguration();

        ServiceException failure = assertThrows(ServiceException.class,
                () -> service.submit(command(), actor()));

        assertEquals(DURATION_CHANGE_FILE_ARTIFACT_UNAVAILABLE.getCode(), failure.getCode());
        verify(processInstanceApi, never()).createProcessInstance(any(), any());
        verify(changeMapper, never()).updateVersionIfMatch(any());
    }

    @Test
    void rejectsApplicantAsTheFrozenServiceManager() {
        stubCommandExecution();
        stubAuthorizedFacts(9L);

        assertThrows(ServiceException.class, () -> service.submit(command(), actor()));

        verify(planMapper, never()).selectForUpdate(any());
        verify(processInstanceApi, never()).createProcessInstance(any(), any());
    }

    @Test
    void rollsBackBusinessWritesWhenBpmReturnsBlankInstanceId() {
        stubCommandExecution();
        stubAuthorizedFacts(10L);
        stubRows(change("INTERNAL_ADJUSTMENT"), plan());
        stubReasonConfiguration();
        when(processInstanceApi.createProcessInstance(any(), any())).thenReturn(" ");

        assertThrows(ServiceException.class, () -> service.submit(command(), actor()));

        verify(revisionMapper, never()).freezeForSubmitIfMatch(any());
        verify(changeMapper, never()).updateVersionIfMatch(any());
        verify(planMapper, never()).updateVersionIfMatch(any());
    }

    @Test
    void rejectsExistingPendingChangeBeforeCreatingBpm() {
        stubCommandExecution();
        stubAuthorizedFacts(10L);
        ConstructionPlanDO plan = plan();
        plan.setPendingChangeId(800L);
        when(planMapper.selectForUpdate(any())).thenReturn(plan);
        when(changeMapper.selectForUpdate(any())).thenReturn(change("INTERNAL_ADJUSTMENT"));

        assertThrows(ServiceException.class, () -> service.submit(command(), actor()));

        verify(processInstanceApi, never()).createProcessInstance(any(), any());
    }

    private void stubCommandExecution() {
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(3);
            Object response = operation.get();
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, response);
        });
    }

    private void stubAuthorizedFacts(Long approverId) {
        when(permissionApi.hasAnyPermissions(9L,
                ConstructionPlanApplicationService.PERMISSION_MANAGE)).thenReturn(true);
        when(planMapper.selectById(any())).thenReturn(plan());
        when(projectScopeApi.resolveCurrent(any())).thenReturn(
                new ProjectScopeResult(100L, 7L, Set.of(100L), Set.of()));
        when(participantFactApi.inspect(any())).thenReturn(fact(approverId,
                Set.of(ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1)));
        when(participantFactApi.lockAndRevalidate(any())).thenAnswer(invocation -> {
            ProjectParticipantFactRevalidationQuery query = invocation.getArgument(0);
            return Objects.equals(query.userId(), 9L)
                    ? fact(9L, Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER))
                    : fact(approverId, Set.of(ProjectParticipantFactApi.ROLE_SERVICE_MANAGER_L1));
        });
    }

    private void stubRows(ConstructionPlanChangeDO change, ConstructionPlanDO plan) {
        when(planMapper.selectForUpdate(any())).thenReturn(plan);
        when(changeMapper.selectForUpdate(any())).thenReturn(change);
        when(revisionMapper.selectForUpdate(any())).thenReturn(candidate());
    }

    private void stubReasonConfiguration() {
        when(dictDataApi.getDictDataList("pms_duration_change_reason_type")).thenReturn(List.of(
                dict("CUSTOMER_DELAY"), dict("INTERNAL_ADJUSTMENT")));
        when(configApi.getConfigValueByKey(
                "pms.sol.duration-change.customer-evidence-required-reason-codes"))
                .thenReturn("CUSTOMER_DELAY");
    }

    private DictDataRespDTO dict(String value) {
        DictDataRespDTO row = new DictDataRespDTO();
        row.setDictType("pms_duration_change_reason_type");
        row.setValue(value);
        row.setStatus(0);
        return row;
    }

    private ProjectParticipantFact fact(Long userId, Set<String> roles) {
        return new ProjectParticipantFact(100L, userId, roles, "PRIMARY",
                "ACTIVE", "S1", 3, 3L);
    }

    private ConstructionPlanDO plan() {
        ConstructionPlanDO row = new ConstructionPlanDO();
        row.setId(501L);
        row.setProjectId(100L);
        row.setCurrentDurationRevisionId(701L);
        row.setPlanRecalculationStatusCode(ConstructionPlanDO.RECALCULATION_PENDING);
        row.setPlanRecalculationSourceRevisionId(701L);
        row.setVersion(1);
        row.setTenantId(0L);
        return row;
    }

    private ConstructionPlanChangeDO change(String reasonType) {
        ConstructionPlanChangeDO row = new ConstructionPlanChangeDO();
        row.setId(801L);
        row.setPlanId(501L);
        row.setBaseRevisionId(701L);
        row.setCandidateRevisionId(702L);
        row.setStatusCode(ConstructionPlanChangeDO.STATUS_DRAFT);
        row.setReasonTypeCode(reasonType);
        row.setReasonDetail("reason");
        row.setCustomerEvidenceFileId(901L);
        row.setCustomerEvidenceFileVersion(2);
        row.setApplicantUserId(9L);
        row.setVersion(0);
        row.setTenantId(0L);
        return row;
    }

    private ConstructionPlanRevisionDO candidate() {
        ConstructionPlanRevisionDO row = new ConstructionPlanRevisionDO();
        row.setId(702L);
        row.setPlanId(501L);
        row.setRevisionNo(2);
        row.setCalculationBasisCode("DATE_RANGE");
        row.setStartDate(LocalDate.of(2026, 9, 1));
        row.setEndDate(LocalDate.of(2026, 9, 5));
        row.setDurationDays(5);
        row.setSourceChangeId(801L);
        row.setCreatedAt(LocalDateTime.of(2026, 8, 26, 16, 0));
        row.setVersion(0);
        row.setTenantId(0L);
        return row;
    }

    private SubmitDurationChangeCommand command() {
        return new SubmitDurationChangeCommand(501L, 801L, 0, 3,
                "submit-801", "a".repeat(64));
    }

    private ConstructionPlanApplicationService.Actor actor() {
        return new ConstructionPlanApplicationService.Actor(0L, 9L, "corr-task6");
    }

}
