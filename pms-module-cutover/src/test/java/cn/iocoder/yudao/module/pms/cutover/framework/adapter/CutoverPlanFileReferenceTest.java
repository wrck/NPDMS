package cn.iocoder.yudao.module.pms.cutover.framework.adapter;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverPlanFileReferenceTest {
    @Test void resolvesTheExactPlanBusinessReferenceInsteadOfTheArtifactsFirstReference() {
        var api = mock(FileArtifactApi.class);
        var evidence = mock(FileEvidenceApi.class);
        var tasks = mock(CutoverTaskMapper.class);
        var task = new CutoverTaskDO(); task.setId(50L); task.setTenantId(1L); task.setProjectId(70L);
        when(tasks.selectById(50L)).thenReturn(task);
        var port = new CutoverFileAdapters().cutoverPlanFilePort(api, evidence, mock(NativeGeneratedFileApi.class), tasks,
                null, null, null, null, null, null);
        var handle = CutoverPlanFilePort.FileHandle.builder().artifactId(901L).versionNo(2).referenceKey("plan-slot")
                .fileFactVersion(new CutoverPlanFilePort.FileFactVersion(1, 0, 3)).scopeVersion(30L).build();
        var exact = new FileArtifactVersionQuery(901L, 2, "CUT", "CUTOVER_PLAN", "50", "FULL_PLAN", "plan-slot", FileActionCodes.READ);
        when(api.inspect(exact)).thenReturn(new FileArtifactVersionFact(901L, 2, "plan-slot", "CUTOVER_CHECKLIST_MANUAL_EVIDENCE",
                "拓扑.png", 100L, "image/png", "a".repeat(64), "AVAILABLE", "ACTIVE", new FileFactVersion(1, 0, 3), 30L));
        TenantContextHolder.setTenantId(1L);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(8L);
            var result = port.inspect(CutoverPlanFilePort.FileQuery.builder().tenantId(1L).actorId(8L).projectId(70L).taskId(50L).handle(handle).build());
            assertThat(result.referenceKey()).isEqualTo("plan-slot");
            verify(api).inspect(exact);
            verifyNoInteractions(evidence);
            assertThatThrownBy(() -> port.inspect(CutoverPlanFilePort.FileQuery.builder().tenantId(1L).actorId(8L)
                    .projectId(99L).taskId(50L).handle(handle).build())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> port.inspect(CutoverPlanFilePort.FileQuery.builder().tenantId(1L).actorId(8L)
                    .projectId(70L).taskId(999L).handle(handle).build())).isInstanceOf(IllegalArgumentException.class);
        } finally { TenantContextHolder.clear(); }
    }
}
