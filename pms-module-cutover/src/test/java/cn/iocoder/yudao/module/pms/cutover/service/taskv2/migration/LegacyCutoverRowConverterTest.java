package cn.iocoder.yudao.module.pms.cutover.service.taskv2.migration;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyCutoverRowConverterTest {

    @Test
    void convertsQualifiedLegacyRowToReadOnlyProjection() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 1, 9, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 8, 2, 10, 30);
        CutTaskDO source = new CutTaskDO();
        source.setId(101L);
        source.setTenantId(1L);
        source.setProjectId(201L);
        source.setCode(" CUT-LEGACY-001 ");
        source.setName(" 旧平台割接任务 ");
        source.setCutoverType(" REPLACE ");
        source.setNetworkMode(" DUAL ");
        source.setScheduledTime(LocalDateTime.of(2026, 9, 1, 1, 0));
        source.setStatus(6);
        source.setVersion(3);
        source.setCreator("legacy-user");
        source.setCreateTime(createdAt);
        source.setUpdater("legacy-auditor");
        source.setUpdateTime(updatedAt);
        source.setDeleted(false);

        CutoverTaskDO target = new LegacyCutoverRowConverter().convert(301L, 1L, source);

        assertThat(target.getId()).isEqualTo(301L);
        assertThat(target.getTenantId()).isEqualTo(1L);
        assertThat(target.getProjectId()).isEqualTo(201L);
        assertThat(target.getTaskNo()).isEqualTo("CUT-LEGACY-001");
        assertThat(target.getTaskName()).isEqualTo("旧平台割接任务");
        assertThat(target.getTaskOrigin()).isEqualTo("LEGACY_FORWARD");
        assertThat(target.getIntakeSourceType()).isEqualTo("LEGACY_FORWARD");
        assertThat(target.getTaskStatus()).isEqualTo("LEGACY_UNKNOWN");
        assertThat(target.getLegacyTaskId()).isEqualTo(101L);
        assertThat(target.getLegacyCutoverTypeRaw()).isEqualTo("REPLACE");
        assertThat(target.getLegacyNetworkModeRaw()).isEqualTo("DUAL");
        assertThat(target.getLegacyStatusValue()).isEqualTo(6);
        assertThat(target.getLegacySourceVersion()).isEqualTo(3);
        assertThat(target.getLegacyMappingVersion()).isEqualTo(LegacyCutoverRowConverter.MAPPING_VERSION);
        assertThat(target.getVersion()).isZero();
        assertThat(target.getCreator()).isEqualTo("legacy-user");
        assertThat(target.getCreateTime()).isEqualTo(createdAt);
        assertThat(target.getUpdater()).isEqualTo("legacy-auditor");
        assertThat(target.getUpdateTime()).isEqualTo(updatedAt);
        assertThat(target.getCutoverType()).isNull();
        assertThat(target.getNetworkMode()).isNull();
        assertThat(target.getOwnerUserId()).isNull();
        assertThat(target.getCustomerId()).isNull();
        assertThat(target.getCurrentStage()).isNull();
        assertThat(target.getManualGrade()).isNull();
        assertThat(target.getCurrentAssessmentId()).isNull();
    }
}
