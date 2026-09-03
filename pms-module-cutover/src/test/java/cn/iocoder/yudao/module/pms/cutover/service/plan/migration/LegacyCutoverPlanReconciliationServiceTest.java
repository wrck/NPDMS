package cn.iocoder.yudao.module.pms.cutover.service.plan.migration;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanStepDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanRevisionMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverPlanStepMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.migration.LegacyCutoverPlanReconciliationMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.migration.query.LegacyCutoverPlanTargetQuery;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.AppendExternalMappingCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CompleteReconciliationCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchClaimResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchStatus;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordPage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyCutoverPlanReconciliationServiceTest {

    @Test
    void completesQualifiedStagedSourceAsLegacyForwardPlan() {
        PlatformMigrationEvidenceApi migrationApi = mock(PlatformMigrationEvidenceApi.class);
        LegacyCutoverPlanReconciliationMapper reconciliationMapper = mock(LegacyCutoverPlanReconciliationMapper.class);
        LegacyCutoverTaskMappingPort taskMappingPort = mock(LegacyCutoverTaskMappingPort.class);
        CutoverPlanRevisionMapper planMapper = mock(CutoverPlanRevisionMapper.class);
        CutoverPlanStepMapper stepMapper = mock(CutoverPlanStepMapper.class);
        MigrationBatchFact batch = new MigrationBatchFact(71L, 1L, "CUT", "CUTOVER_PLAN_CURRENT_FORWARD",
                "release-plan-1", "NPDMS_LEGACY", "pms_cut_plan", MigrationBatchStatus.RECONCILING,
                1, 0, 0, 0, null, 3, LocalDateTime.of(2026, 9, 1, 10, 0));
        var source = LegacyCutoverPlanRowConverterTest.source(81L, 71L,
                LegacyCutoverPlanRowConverterTest.legacyPayload(false));
        CutoverTaskDO target = new CutoverTaskDO();
        target.setId(501L);
        target.setTenantId(1L);
        target.setLegacyTaskId(41L);
        target.setTaskOrigin("LEGACY_FORWARD");

        when(migrationApi.claimStagedBatch(any())).thenReturn(new MigrationBatchClaimResult(true, batch));
        when(migrationApi.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(List.of(source), null));
        when(taskMappingPort.resolveTargetTaskId(1L, 41L)).thenReturn(501L);
        when(reconciliationMapper.selectQualifiedTargetForUpdate(any())).thenReturn(target);
        when(planMapper.insert(any(CutoverPlanRevisionDO.class))).thenReturn(1);
        when(stepMapper.insert(any(CutoverPlanStepDO.class))).thenReturn(1);

        var service = new LegacyCutoverPlanReconciliationService(migrationApi, taskMappingPort, reconciliationMapper,
                planMapper, stepMapper, new LegacyCutoverPlanRowConverter());
        var result = service.reconcileNext(1L, "corr-plan-migration");

        assertThat(result).isEqualTo(new LegacyCutoverPlanReconciliationResult(true, 71L, 1, 0, 0));
        ArgumentCaptor<LegacyCutoverPlanTargetQuery> targetQuery =
                ArgumentCaptor.forClass(LegacyCutoverPlanTargetQuery.class);
        verify(reconciliationMapper).selectQualifiedTargetForUpdate(targetQuery.capture());
        assertThat(targetQuery.getValue().targetTaskId()).isEqualTo(501L);
        assertThat(targetQuery.getValue().legacyTaskId()).isEqualTo(41L);
        ArgumentCaptor<CutoverPlanRevisionDO> plan = ArgumentCaptor.forClass(CutoverPlanRevisionDO.class);
        verify(planMapper).insert(plan.capture());
        assertThat(plan.getValue().getCutoverTaskId()).isEqualTo(501L);
        assertThat(plan.getValue().getOriginCode()).isEqualTo("LEGACY_FORWARD");
        assertThat(plan.getValue().getLegacyPlanId()).isEqualTo(91L);
        assertThat(plan.getValue().getStatusCode()).isNull();
        ArgumentCaptor<CutoverPlanStepDO> steps = ArgumentCaptor.forClass(CutoverPlanStepDO.class);
        verify(stepMapper, org.mockito.Mockito.times(4)).insert(steps.capture());
        assertThat(steps.getAllValues()).extracting(CutoverPlanStepDO::getSectionCode)
                .containsExactly("PRE_OPERATION", "OPERATION", "POST_BUSINESS_TEST", "ROLLBACK");
        ArgumentCaptor<AppendExternalMappingCommand> mapping =
                ArgumentCaptor.forClass(AppendExternalMappingCommand.class);
        verify(migrationApi).appendExternalMapping(mapping.capture());
        assertThat(mapping.getValue().targets()).hasSize(5);
        ArgumentCaptor<CompleteReconciliationCommand> complete =
                ArgumentCaptor.forClass(CompleteReconciliationCommand.class);
        verify(migrationApi).completeReconciliation(complete.capture());
        assertThat(complete.getValue().expectedMappedCount()).isEqualTo(1);
    }
}
