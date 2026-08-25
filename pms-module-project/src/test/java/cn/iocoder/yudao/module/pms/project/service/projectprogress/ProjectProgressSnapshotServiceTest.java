package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressFactDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressFactMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressPolicyItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressSnapshotDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.ProjectProgressResult;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectProgressSnapshotServiceTest {

    @Test
    void missingDirectChildFactProducesPendingSnapshotWithoutStaleFallback() {
        ProjectMasterMapper projectMapper = mock(ProjectMasterMapper.class);
        ProjectTreeVersionMapper treeVersionMapper = mock(ProjectTreeVersionMapper.class);
        ProjectTreePathMapper pathMapper = mock(ProjectTreePathMapper.class);
        ProjectProgressPolicyService policyService = mock(ProjectProgressPolicyService.class);
        ProjectProgressPolicyItemMapper policyItemMapper = mock(ProjectProgressPolicyItemMapper.class);
        ProjectProgressFactMapper factMapper = mock(ProjectProgressFactMapper.class);
        ProjectProgressSnapshotMapper snapshotMapper = mock(ProjectProgressSnapshotMapper.class);
        ProjectProgressSnapshotDetailMapper detailMapper = mock(ProjectProgressSnapshotDetailMapper.class);
        ProjectTreeScopeService scopeService = mock(ProjectTreeScopeService.class);
        ProjectProgressMetrics metrics = mock(ProjectProgressMetrics.class);
        ProjectProgressSnapshotService service = new ProjectProgressSnapshotService(projectMapper, treeVersionMapper,
                pathMapper, policyService, policyItemMapper, factMapper, snapshotMapper, detailMapper,
                scopeService, metrics);
        ProjectMasterDO parent = project(10L, 10L);
        ProjectMasterDO first = project(11L, 10L);
        ProjectMasterDO second = project(12L, 10L);
        ProjectTreeVersionDO tree = new ProjectTreeVersionDO();
        tree.setTreeVersion(4L);
        ProjectProgressPolicyRevisionDO policy = new ProjectProgressPolicyRevisionDO();
        policy.setId(21L);
        when(projectMapper.selectByIdForUpdate(10L)).thenReturn(parent);
        when(treeVersionMapper.selectLatestActive(10L)).thenReturn(tree);
        when(projectMapper.selectChildren(10L)).thenReturn(List.of(first, second));
        when(policyService.requireActiveOrCreateDefault(10L, new ProjectProgressPolicyService.Actor(0L, 9L, "c-1")))
                .thenReturn(policy);
        when(policyItemMapper.selectByRevisionId(21L)).thenReturn(List.of(item(11L), item(12L)));
        when(pathMapper.selectParentsWithChildren(10L, 4L, Set.of(11L, 12L))).thenReturn(Set.of());
        when(factMapper.selectLatestByProjects(0L, Set.of(11L, 12L))).thenReturn(List.of(fact(11L)));
        when(snapshotMapper.selectByIdentity(any(), any(), any(), any())).thenReturn(null);
        doAnswer(invocation -> {
            ((ProjectProgressSnapshotDO) invocation.getArgument(0)).setId(31L);
            return 1;
        }).when(snapshotMapper).insert(any(ProjectProgressSnapshotDO.class));
        when(detailMapper.insertBatch(any())).thenReturn(true);

        ProjectProgressResult result = service.calculate(10L,
                new ProjectProgressPolicyService.Actor(0L, 9L, "c-1"));

        assertEquals("PENDING", result.status());
        assertNull(result.progress());
        assertEquals("PROGRESS_FACT_MISSING", result.items().stream()
                .filter(item -> item.childProjectId().equals(12L)).findFirst().orElseThrow().missingReason());
        verify(metrics).snapshot(org.mockito.ArgumentMatchers.eq("PENDING"),
                org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.anyLong());
        verify(scopeService).assertFullAccess(
                new ProjectScopeQuery(0L, 9L, 10L, "PROJECT_VIEW", 4L));
    }

    private static ProjectMasterDO project(Long id, Long rootId) {
        ProjectMasterDO value = new ProjectMasterDO();
        value.setId(id);
        value.setRootId(rootId);
        value.setTenantId(0L);
        value.setLifecycleStatus("S2");
        return value;
    }

    private static ProjectProgressPolicyItemDO item(Long childId) {
        ProjectProgressPolicyItemDO value = new ProjectProgressPolicyItemDO();
        value.setChildProjectId(childId);
        value.setWeight(new BigDecimal("50.0000"));
        value.setIncludeStatusSnapshot(JsonUtils.toJsonString(List.of()));
        return value;
    }

    private static ProjectProgressFactDO fact(Long projectId) {
        ProjectProgressFactDO value = new ProjectProgressFactDO();
        value.setProjectId(projectId);
        value.setFactVersion(1L);
        value.setProgress(new BigDecimal("40.0000"));
        value.setSourceWatermark("fact-1");
        return value;
    }
}
