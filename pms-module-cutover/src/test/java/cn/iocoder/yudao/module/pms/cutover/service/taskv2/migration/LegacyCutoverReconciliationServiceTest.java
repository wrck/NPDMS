package cn.iocoder.yudao.module.pms.cutover.service.taskv2.migration;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.CompleteReconciliationCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchClaimResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationBatchStatus;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordFact;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordPage;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFact;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyCutoverReconciliationServiceTest {

    @Test
    void completesOneQualifiedLegacyTaskAsReadOnlyProjection() {
        PlatformMigrationEvidenceApi migrationApi = mock(PlatformMigrationEvidenceApi.class);
        ProjectOrganizationFactApi projectApi = mock(ProjectOrganizationFactApi.class);
        CutTaskMapper sourceMapper = mock(CutTaskMapper.class);
        CutoverTaskMapper targetMapper = mock(CutoverTaskMapper.class);
        MigrationBatchFact batch = new MigrationBatchFact(71L, 1L, "CUT",
                "CUTOVER_TASK_CURRENT_FORWARD", "release-1", "NPDMS_LEGACY", "pms_cut_task",
                MigrationBatchStatus.RECONCILING, 1, 0, 0, 0, null, 3,
                LocalDateTime.of(2026, 8, 31, 4, 0));
        MigrationSourceRecordFact source = new MigrationSourceRecordFact(81L, 1L, 71L,
                "NPDMS_LEGACY", "pms_cut_task", "91", "CUT-91", "{}", "0".repeat(64),
                LocalDateTime.of(2026, 8, 31, 4, 1), null);
        CutTaskDO legacy = legacyTask();
        ProjectOrganizationFact project = new ProjectOrganizationFact(100L, 5, 200L, 300L, "OFFICE-300");

        when(migrationApi.claimStagedBatch(any())).thenReturn(new MigrationBatchClaimResult(true, batch));
        when(migrationApi.pageSourceRecords(any())).thenReturn(new MigrationSourceRecordPage(List.of(source), null));
        when(sourceMapper.selectLegacySourceForUpdate(any())).thenReturn(legacy);
        when(projectApi.inspect(any())).thenReturn(project);
        when(projectApi.lockAndRevalidate(any())).thenReturn(project);
        when(targetMapper.countLegacyIdentityConflicts(any())).thenReturn(0L);
        when(targetMapper.insert(any(CutoverTaskDO.class))).thenReturn(1);
        when(migrationApi.appendExternalMapping(any())).thenAnswer(invocation -> null);
        when(migrationApi.completeReconciliation(any())).thenReturn(new MigrationBatchFact(71L, 1L, "CUT",
                "CUTOVER_TASK_CURRENT_FORWARD", "release-1", "NPDMS_LEGACY", "pms_cut_task",
                MigrationBatchStatus.COMPLETED, 1, 1, 0, 0, null, 4,
                LocalDateTime.of(2026, 8, 31, 4, 0)));

        LegacyCutoverReconciliationResult result = new LegacyCutoverReconciliationService(migrationApi, projectApi,
                sourceMapper, targetMapper, new LegacyCutoverRowConverter()).reconcileNext(1L, "corr-cut-legacy");

        assertThat(result).isEqualTo(new LegacyCutoverReconciliationResult(true, 71L, 1));
        ArgumentCaptor<CutoverTaskDO> target = ArgumentCaptor.forClass(CutoverTaskDO.class);
        verify(targetMapper).insert(target.capture());
        assertThat(target.getValue().getLegacyTaskId()).isEqualTo(91L);
        assertThat(target.getValue().getTaskOrigin()).isEqualTo("LEGACY_FORWARD");
        assertThat(target.getValue().getTaskStatus()).isEqualTo("LEGACY_UNKNOWN");
        assertThat(target.getValue().getCurrentStage()).isNull();
        verify(migrationApi).appendExternalMapping(any());
        ArgumentCaptor<CompleteReconciliationCommand> complete =
                ArgumentCaptor.forClass(CompleteReconciliationCommand.class);
        verify(migrationApi).completeReconciliation(complete.capture());
        assertThat(complete.getValue().expectedMappedCount()).isEqualTo(1);
        assertThat(complete.getValue().expectedIssueCount()).isZero();
        assertThat(complete.getValue().expectedRetainedCount()).isZero();
    }

    private static CutTaskDO legacyTask() {
        CutTaskDO source = new CutTaskDO();
        source.setId(91L);
        source.setTenantId(1L);
        source.setProjectId(100L);
        source.setCode("CUT-91");
        source.setName("历史割接任务");
        source.setCutoverType("REPLACE");
        source.setNetworkMode("DUAL");
        source.setScheduledTime(LocalDateTime.of(2026, 9, 1, 1, 0));
        source.setStatus(2);
        source.setVersion(6);
        source.setCreator("10");
        source.setUpdater("11");
        source.setCreateTime(LocalDateTime.of(2026, 8, 1, 1, 0));
        source.setUpdateTime(LocalDateTime.of(2026, 8, 2, 1, 0));
        source.setDeleted(false);
        return source;
    }
}
