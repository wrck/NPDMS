package cn.iocoder.yudao.module.pms.engineering.service.stageplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanBatchRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.stageplan.vo.StagePlanItemUpdateReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanRevisionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanBatchDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.stageplan.StagePlanItemDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.ConstructionPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.StagePlanBatchMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.stageplan.StagePlanItemMapper;
import cn.iocoder.yudao.module.pms.project.api.stageplan.ProjectStagePlanApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 阶段施工计划（PLN-01/04）定向测试：占比推算、重叠校验、审批生效回写与驳回重提。
 */
class StagePlanBatchServiceTest {

    private final StagePlanBatchMapper batchMapper = mock(StagePlanBatchMapper.class);
    private final StagePlanItemMapper itemMapper = mock(StagePlanItemMapper.class);
    private final ProjectStagePlanApi stagePlanApi = mock(ProjectStagePlanApi.class);
    private final ConstructionPlanMapper constructionPlanMapper = mock(ConstructionPlanMapper.class);
    private final ConstructionPlanRevisionMapper revisionMapper = mock(ConstructionPlanRevisionMapper.class);
    private final BpmProcessInstanceApi processInstanceApi = mock(BpmProcessInstanceApi.class);
    private final Environment environment = mock(Environment.class);
    private final StagePlanProperties properties = new StagePlanProperties();
    private final StagePlanBatchServiceImpl service = new StagePlanBatchServiceImpl();

    private final cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi projectScopeApi = mock(cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.class);
    private final cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi participants = mock(cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi.class);
    private StagePlanBatchDO batch;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "completionEvents", mock(cn.iocoder.yudao.module.pms.engineering.service.taskbusiness.EngineeringRuleReevaluationEvents.class));
        ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "stagePlanApi", stagePlanApi);
        ReflectionTestUtils.setField(service, "constructionPlanMapper", constructionPlanMapper);
        ReflectionTestUtils.setField(service, "constructionPlanRevisionMapper", revisionMapper);
        ReflectionTestUtils.setField(service, "processInstanceApi", processInstanceApi);
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "environment", environment);
        ReflectionTestUtils.setField(service, "projectScopeApi", projectScopeApi);
        ReflectionTestUtils.setField(service, "participants", participants);
        when(participants.inspect(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact(
                7L, 99L, java.util.Set.of("SERVICE_MANAGER"), "DIRECT", "ACTIVE", "S2", 1L, 1L));
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(java.util.Set.of(7L));
        TenantContextHolder.setTenantId(1L);
        when(environment.getProperty("yudao.tenant.enable", Boolean.class, true)).thenReturn(true);
        batch = new StagePlanBatchDO();
        batch.setId(100L);
        batch.setProjectId(7L);
        batch.setStatus(StagePlanBatchDO.STATUS_DRAFT);
        batch.setBpmProcessInstanceId("PI-1");
        batch.setVersion(1L);
        when(batchMapper.selectById(100L)).thenReturn(batch);
        when(batchMapper.updateById(any(StagePlanBatchDO.class))).thenReturn(1);
        when(itemMapper.updateById(any(StagePlanItemDO.class))).thenReturn(1);
        when(itemMapper.insert(any(StagePlanItemDO.class))).thenReturn(1);
        when(batchMapper.selectByProcessInstanceId("PI-1")).thenReturn(batch);
        // 无工期基线默认场景
        when(constructionPlanMapper.selectByProjectId(any(), any())).thenReturn(null);
    }

    @Test
    void changedAcceptanceInputsCannotBeSubmitted() {
        frozenInputs();
        when(itemMapper.selectListByBatchId(100L)).thenReturn(nonOverlappingItems());
        when(stagePlanApi.calculateSchedule(any(), any(), any(), any())).thenReturn(new ProjectStagePlanApi.ScheduleCalculation(
                1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11), List.of(), "changed-acceptance"));
        assertThrows(ServiceException.class, () -> service.submit(100L, 99L));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void taskMustFinishBeforeItsPlannedAcceptance() {
        frozenInputs();
        when(itemMapper.selectListByBatchId(100L)).thenReturn(nonOverlappingItems());
        batch.setTaskPlansJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(List.of(
                new ProjectStagePlanApi.TaskPlan(21L, null, "S1", "设备安装", 1,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 4)))));
        var failure = assertThrows(ServiceException.class, () -> service.submit(100L, 99L));
        assertTrue(failure.getMessage().contains("计划验收时间"));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void approverMustBeAnEffectiveProjectServiceManager() {
        when(participants.inspect(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact(
                7L, 99L, java.util.Set.of("PROJECT_MANAGER"), "DIRECT", "ACTIVE", "S2", 1L, 1L));
        assertThrows(ServiceException.class, () -> service.submit(100L, 99L));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void projectOutsideScopeCannotReadBatch() {
        when(projectScopeApi.resolveAllCurrent(any())).thenReturn(java.util.Set.of());
        assertThrows(ServiceException.class, () -> service.getBatch(100L));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void historicalBatchReadsItsOwnDurationRevision() {
        batch.setDurationRevisionId(11L);
        batch.setStatus(StagePlanBatchDO.STATUS_EFFECTIVE);
        ConstructionPlanDO plan = new ConstructionPlanDO();
        plan.setId(10L);
        plan.setCurrentDurationRevisionId(22L);
        when(constructionPlanMapper.selectByProjectId(1L, 7L)).thenReturn(plan);
        ConstructionPlanRevisionDO old = new ConstructionPlanRevisionDO();
        old.setId(11L);
        old.setStartDate(LocalDate.of(2026, 1, 1));
        old.setEndDate(LocalDate.of(2026, 1, 31));
        when(revisionMapper.selectById(new cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionLockQuery(1L, 10L, 11L))).thenReturn(old);
        var result = service.getBatch(100L);
        assertEquals(old.getEndDate(), result.getBaselineEnd());
        assertEquals(11L, result.getDurationRevisionId());
    }

    @Test
    void incompleteDatesCannotEnterApproval() {
        when(itemMapper.selectListByBatchId(100L)).thenReturn(List.of(
                item(1L, "未排期阶段", 1, null, null, null, null)));
        assertThrows(ServiceException.class, () -> service.submit(100L, 99L));
        verifyNoInteractions(processInstanceApi);
    }

    @Test
    void adjustmentRejectsForeignItemsBeforeWriting() {
        when(itemMapper.selectListByBatchId(100L)).thenReturn(List.of(
                item(1L, "阶段", 1, null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3))));
        var request = new StagePlanItemUpdateReqVO();
        request.setId(100L); request.setVersion(1);
        var foreign = new StagePlanItemUpdateReqVO.Item();
        foreign.setId(999L);
        request.setItems(List.of(foreign));
        assertThrows(ServiceException.class, () -> service.updateItems(request));
        verify(itemMapper, never()).updateById(any(StagePlanItemDO.class));
    }

    private StagePlanItemDO item(long id, String name, int sort,
                                 LocalDate suggestedStart, LocalDate suggestedEnd,
                                 LocalDate planStart, LocalDate planEnd) {
        StagePlanItemDO item = new StagePlanItemDO();
        item.setId(id);
        item.setBatchId(100L);
        item.setPhaseId(id);
        item.setPhaseName(name);
        item.setPhaseCode("S" + id);
        item.setSort(sort);
        item.setSuggestedStart(suggestedStart);
        item.setSuggestedEnd(suggestedEnd);
        item.setPlanStart(planStart);
        item.setPlanEnd(planEnd);
        return item;
    }

    @Test
    void autoEstimateWithinBaselineAllocatesProportionally() {
        frozenInputs();
        // 基线窗口 11 天；两阶段建议工期 2 天与 9 天 → 占比分摊 2+9，尾差计入最后阶段
        ConstructionPlanDO plan = new ConstructionPlanDO();
        plan.setId(10L);
        plan.setProjectId(7L);
        plan.setCurrentDurationRevisionId(11L);
        when(constructionPlanMapper.selectByProjectId(any(), any())).thenReturn(plan);
        ConstructionPlanRevisionDO baseline = new ConstructionPlanRevisionDO();
        baseline.setId(11L);
        baseline.setPlanId(10L);
        baseline.setStartDate(LocalDate.of(2026, 1, 1));
        baseline.setEndDate(LocalDate.of(2026, 1, 11));
        when(revisionMapper.selectById(any())).thenReturn(baseline);

        when(itemMapper.selectListByBatchId(100L)).thenReturn(List.of(
                item(1L, "到货签收", 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), null, null),
                item(2L, "硬件实施", 2, LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 11), null, null)));

        StagePlanBatchRespVO resp = service.autoEstimate(100L);

        assertEquals(11L, resp.getDurationRevisionId());
        ArgumentCaptor<StagePlanItemDO> captor = ArgumentCaptor.forClass(StagePlanItemDO.class);
        verify(itemMapper, times(2)).updateById(captor.capture());
        List<StagePlanItemDO> updates = captor.getAllValues();
        assertEquals(LocalDate.of(2026, 1, 1), updates.get(0).getPlanStart());
        assertEquals(LocalDate.of(2026, 1, 2), updates.get(0).getPlanEnd());
        assertEquals(LocalDate.of(2026, 1, 3), updates.get(1).getPlanStart());
        assertEquals(LocalDate.of(2026, 1, 11), updates.get(1).getPlanEnd());
        // Suggested dates describe this calculation, not a prior applied project schedule.
        assertEquals(LocalDate.of(2026, 1, 1), updates.get(0).getSuggestedStart());
        assertEquals(LocalDate.of(2026, 1, 2), updates.get(0).getSuggestedEnd());
        assertEquals(LocalDate.of(2026, 1, 3), updates.get(1).getSuggestedStart());
        assertEquals(LocalDate.of(2026, 1, 11), updates.get(1).getSuggestedEnd());
    }

    @Test
    void autoEstimateWithoutBaselineFailsWithoutWritingDates() {
        when(itemMapper.selectListByBatchId(100L)).thenReturn(nonOverlappingItems());
        assertThrows(ServiceException.class, () -> service.autoEstimate(100L));
        verify(itemMapper, never()).updateById(any(StagePlanItemDO.class));
    }

    private void frozenInputs() {
        batch.setTenantId(1L); batch.setDurationRevisionId(11L); batch.setInputSnapshot("{}");
        ConstructionPlanDO plan = new ConstructionPlanDO(); plan.setId(10L); plan.setCurrentDurationRevisionId(11L);
        plan.setVersion(4L); plan.setPendingChangeId(12L);
        when(constructionPlanMapper.updateVersionIfMatch(any())).thenReturn(1);
        when(constructionPlanMapper.selectByProjectId(any(), any())).thenReturn(plan);
        ConstructionPlanRevisionDO baseline = new ConstructionPlanRevisionDO(); baseline.setId(11L);
        baseline.setStartDate(LocalDate.of(2026, 1, 1)); baseline.setEndDate(LocalDate.of(2026, 1, 11));
        when(revisionMapper.selectById(any())).thenReturn(baseline);
        when(stagePlanApi.calculateSchedule(any(), any(), any(), any())).thenReturn(new ProjectStagePlanApi.ScheduleCalculation(
                1L, baseline.getStartDate(), baseline.getEndDate(), List.of(
                new ProjectStagePlanApi.StagePlanDate(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)),
                new ProjectStagePlanApi.StagePlanDate(2L, LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 11))), "{}"));
    }

    @Test
    void updateItemsRejectsOverlappingStages() {
        when(itemMapper.selectListByBatchId(100L)).thenReturn(List.of(
                item(1L, "到货签收", 1, null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)),
                item(2L, "硬件实施", 2, null, null, LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 10))));
        StagePlanItemUpdateReqVO reqVO = new StagePlanItemUpdateReqVO();
        reqVO.setId(100L);
        reqVO.setVersion(1);
        reqVO.setRemark("调整阶段日期");
        StagePlanItemUpdateReqVO.Item i2 = new StagePlanItemUpdateReqVO.Item();
        i2.setId(2L);
        // 调整为与阶段一在 1/5 重叠
        i2.setPlanStart(LocalDate.of(2026, 1, 5));
        i2.setPlanEnd(LocalDate.of(2026, 1, 10));
        reqVO.setItems(List.of(i2));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateItems(reqVO));
        assertTrue(ex.getMessage().contains("重叠"));
    }

    private List<StagePlanItemDO> nonOverlappingItems() {
        return List.of(
                item(1L, "到货签收", 1, null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)),
                item(2L, "硬件实施", 2, null, null, LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 10)));
    }

    @Test
    void submitCreatesBpmProcessAndGuardsResubmit() {
        frozenInputs();
        properties.setProcessDefinitionKey("pms_stage_plan_approve");
        when(itemMapper.selectListByBatchId(100L)).thenReturn(nonOverlappingItems());
        when(processInstanceApi.createProcessInstance(any(), any(BpmProcessInstanceCreateReqDTO.class)))
                .thenReturn("PI-1");

        service.submit(100L, 99L);

        ArgumentCaptor<StagePlanBatchDO> batchCaptor = ArgumentCaptor.forClass(StagePlanBatchDO.class);
        verify(batchMapper).updateById(batchCaptor.capture());
        StagePlanBatchDO submitted = batchCaptor.getValue();
        assertEquals(StagePlanBatchDO.STATUS_PENDING_APPROVAL, submitted.getStatus());
        assertEquals("PI-1", submitted.getBpmProcessInstanceId());
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> bpmCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(any(), bpmCaptor.capture());
        assertEquals("pms_stage_plan_approve", bpmCaptor.getValue().getProcessDefinitionKey());
        assertEquals("100", bpmCaptor.getValue().getBusinessKey());

        // 审批驳回后可重提（驳回重提）
        clearInvocations(processInstanceApi, batchMapper);
        batch.setStatus(StagePlanBatchDO.STATUS_REJECTED);
        assertDoesNotThrow(() -> service.submit(100L, 99L));

        // 未配置流程定义时提交失败，不伪造审批
        clearInvocations(processInstanceApi);
        batch.setStatus(StagePlanBatchDO.STATUS_DRAFT);
        properties.setProcessDefinitionKey(null);
        assertThrows(ServiceException.class, () -> service.submit(100L, 99L));
        verify(processInstanceApi, never()).createProcessInstance(any(), any());
    }

    @Test
    void shiftedAcceptanceWindowDoesNotMutateCachedDurationDuringSubmission() {
        frozenInputs();
        properties.setProcessDefinitionKey("pms_stage_plan_approve");
        LocalDate durationStart = LocalDate.of(2026, 1, 1);
        LocalDate durationEnd = LocalDate.of(2026, 1, 11);
        ConstructionPlanRevisionDO cachedRevision = revisionMapper.selectById(
                new cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanRevisionLockQuery(1L, 10L, 11L));
        batch.setCalculatedStart(LocalDate.of(2026, 1, 11));
        batch.setCalculatedEnd(LocalDate.of(2026, 1, 21));
        when(itemMapper.selectListByBatchId(100L)).thenReturn(List.of(
                item(1L, "到货签收", 1, null, null, LocalDate.of(2026, 1, 11), LocalDate.of(2026, 1, 15)),
                item(2L, "硬件实施", 2, null, null, LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 21))));
        when(stagePlanApi.calculateSchedule(any(), any(), any(), any())).thenAnswer(invocation ->
                new ProjectStagePlanApi.ScheduleCalculation(1L, batch.getCalculatedStart(), batch.getCalculatedEnd(), List.of(),
                        durationStart.equals(invocation.getArgument(2)) && durationEnd.equals(invocation.getArgument(3))
                                ? "{}" : "changed-duration"));
        when(processInstanceApi.createProcessInstance(any(), any(BpmProcessInstanceCreateReqDTO.class))).thenReturn("PI-1");

        assertDoesNotThrow(() -> service.submit(100L, 99L));
        assertEquals(durationStart, cachedRevision.getStartDate());
        assertEquals(durationEnd, cachedRevision.getEndDate());
        verify(stagePlanApi).calculateSchedule(1L, 7L, durationStart, durationEnd);
    }

    @Test
    void bpmApproveAppliesPlanDatesAndEffectivates() {
        frozenInputs();
        batch.setStatus(StagePlanBatchDO.STATUS_PENDING_APPROVAL);
        when(itemMapper.selectListByBatchId(100L)).thenReturn(nonOverlappingItems());

        service.handleBpmResult("PI-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), null);
        var planCaptor = ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query.ConstructionPlanVersionUpdate.class);
        verify(constructionPlanMapper).updateVersionIfMatch(planCaptor.capture());
        assertEquals(ConstructionPlanDO.RECALCULATED, planCaptor.getValue().planRecalculationStatusCode());
        assertEquals(11L, planCaptor.getValue().planRecalculationSourceRevisionId());
        assertEquals(12L, planCaptor.getValue().pendingChangeId());
        assertEquals(4, planCaptor.getValue().expectedVersion());

        ArgumentCaptor<StagePlanBatchDO> batchCaptor = ArgumentCaptor.forClass(StagePlanBatchDO.class);
        verify(batchMapper).updateById(batchCaptor.capture());
        assertEquals(StagePlanBatchDO.STATUS_EFFECTIVE, batchCaptor.getValue().getStatus());
        assertNotNull(batchCaptor.getValue().getEffectiveAt());
        // 生效基线经受控写入契约回写阶段计划日期
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProjectStagePlanApi.StagePlanDate>> datesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(stagePlanApi).applyPlanDates(any(), eq(7L), datesCaptor.capture());
        assertEquals(2, datesCaptor.getValue().size());
        assertEquals(LocalDate.of(2026, 1, 1), datesCaptor.getValue().get(0).planStartTime());
        assertEquals(LocalDate.of(2026, 1, 10), datesCaptor.getValue().get(1).planEndTime());
    }

    @Test
    void concurrentDurationChangePreventsPlanFromBecomingEffective() {
        frozenInputs();
        batch.setStatus(StagePlanBatchDO.STATUS_PENDING_APPROVAL);
        when(itemMapper.selectListByBatchId(100L)).thenReturn(nonOverlappingItems());
        when(constructionPlanMapper.updateVersionIfMatch(any())).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.handleBpmResult(
                "PI-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), null));
        verify(batchMapper, never()).updateById(any(StagePlanBatchDO.class));
    }

    @Test
    void bpmRejectRecordsReasonAndReplayIsIgnored() {
        batch.setStatus(StagePlanBatchDO.STATUS_PENDING_APPROVAL);

        service.handleBpmResult("PI-1", BpmProcessInstanceStatusEnum.REJECT.getStatus(), "工期不合理");

        ArgumentCaptor<StagePlanBatchDO> batchCaptor = ArgumentCaptor.forClass(StagePlanBatchDO.class);
        verify(batchMapper).updateById(batchCaptor.capture());
        assertEquals(StagePlanBatchDO.STATUS_REJECTED, batchCaptor.getValue().getStatus());
        assertEquals("工期不合理", batchCaptor.getValue().getRejectReason());
        verify(stagePlanApi, never()).applyPlanDates(any(), any(), any());

        // 批次已终态，重复回执幂等忽略
        batch.setStatus(StagePlanBatchDO.STATUS_REJECTED);
        clearInvocations(batchMapper);
        service.handleBpmResult("PI-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), null);
        verify(batchMapper, never()).updateById(any(StagePlanBatchDO.class));
    }

    @Test
    void overdueRowsCountOnlyEffectiveIncompleteStages() {
        LocalDate today = LocalDate.now();
        StagePlanBatchDO effective = new StagePlanBatchDO();
        effective.setId(200L);
        effective.setProjectId(7L);
        effective.setStatus(StagePlanBatchDO.STATUS_EFFECTIVE);
        when(batchMapper.selectListByStatus(StagePlanBatchDO.STATUS_EFFECTIVE))
                .thenReturn(List.of(effective));
        when(itemMapper.selectListByBatchId(200L)).thenReturn(List.of(
                // 超期未完成
                item(1L, "硬件实施", 2, null, null, today.minusDays(9), today.minusDays(2)),
                // 未到期
                item(2L, "业务联通", 3, null, null, today.plusDays(1), today.plusDays(5)),
                // 超期但阶段已完成（phaseId 3 → 状态 2）不构成超期
                item(3L, "初验", 4, null, null, today.minusDays(20), today.minusDays(5))));
        when(stagePlanApi.listStages(any(), eq(7L))).thenReturn(List.of(
                new ProjectStagePlanApi.StagePlanFact(1L, "S3", "硬件实施", 2, null, null, null, null, "1", 1),
                new ProjectStagePlanApi.StagePlanFact(2L, "S4", "业务联通", 3, null, null, null, null, "0", 1),
                new ProjectStagePlanApi.StagePlanFact(3L, "S5", "初验", 4, null, null, null, null, "2", 1)));

        var rows = service.getOverdueStages(null);

        assertEquals(1, rows.size());
        assertEquals(7L, rows.get(0).getProjectId());
        assertEquals(200L, rows.get(0).getBatchId());
        assertEquals(2L, rows.get(0).getOverdueDays());

        var summary = service.getOverdueSummary(7L);
        assertEquals(1L, summary.getOverdueStageCount());
        assertEquals(1L, summary.getOverdueProjectCount());

        // 无生效批次时返回空结果
        when(batchMapper.selectListByStatus(StagePlanBatchDO.STATUS_EFFECTIVE)).thenReturn(List.of());
        assertTrue(service.getOverdueStages(null).isEmpty());
        assertEquals(0L, service.getOverdueSummary(null).getOverdueProjectCount());
    }
}
