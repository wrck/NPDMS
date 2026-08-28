package cn.iocoder.yudao.module.pms.project.service.projectsplit;

import cn.iocoder.yudao.module.pms.asset.api.device.AssetDeviceScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopePreviewCommand;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitRequestDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitScopeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitRequestMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitPreviewCommand;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectSplitPreviewServiceTest {
    @Mock ProjectSplitDraftService draftService;
    @Mock ProjectSplitRequestMapper requestMapper;
    @Mock ProjectSplitItemMapper itemMapper;
    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectTreeVersionMapper treeVersionMapper;
    @Mock DeliveryScopeApi deliveryScopeApi;
    @Mock AssetDeviceScopeApi assetDeviceScopeApi;
    @Mock DeptApi deptApi;
    @Mock OperationAuditApi auditService;
    @Mock ProjectSplitMetrics metrics;

    private ProjectSplitPreviewService service;

    @BeforeEach
    void setUp() {
        service = new ProjectSplitPreviewService(draftService, requestMapper, itemMapper, projectMapper,
                treeVersionMapper, deliveryScopeApi, assetDeviceScopeApi, deptApi, auditService, metrics);
    }

    @Test
    void shouldPersistServerValidatedPreviewWithoutCreatingProject() {
        ProjectSplitDraftService.Actor actor = new ProjectSplitDraftService.Actor(1L, 9L, "corr-1");
        when(draftService.getDraft(20L, actor)).thenReturn(draft());
        when(projectMapper.selectById(100L)).thenReturn(parent());
        when(treeVersionMapper.selectLatestActive(100L)).thenReturn(tree());
        when(deliveryScopeApi.previewSplit(any())).thenReturn(
                new SplitScopeApplyResult(true, false, 5L, List.of(), List.of()));

        ProjectSplitPreviewService.PreviewResult result = service.preview(
                new ProjectSplitPreviewCommand(20L, 2), actor);

        assertTrue(result.valid());
        assertNotNull(result.previewHash());
        ArgumentCaptor<ProjectSplitRequestDO> update = ArgumentCaptor.forClass(ProjectSplitRequestDO.class);
        verify(requestMapper).updateById(update.capture());
        assertEquals("VALID", update.getValue().getValidationStatus());
        verify(deliveryScopeApi).previewSplit(argThat(command -> command.expectedScopeVersion() == 5L));
        verify(auditService).record(eq(1L), eq(9L), eq("corr-1"), eq("PROJECT_SPLIT_PREVIEW"),
                eq(20L), eq("SUCCESS"), anyMap());
    }

    @Test
    void shouldKeepDraftAndPersistValidationFailure() {
        ProjectSplitDraftService.Actor actor = new ProjectSplitDraftService.Actor(1L, 9L, "corr-2");
        when(draftService.getDraft(20L, actor)).thenReturn(draft());
        when(projectMapper.selectById(100L)).thenReturn(parent());
        when(treeVersionMapper.selectLatestActive(100L)).thenReturn(tree());
        when(deliveryScopeApi.previewSplit(any())).thenReturn(
                new SplitScopeApplyResult(false, false, null, List.of(), List.of("OVER_ALLOCATION:10")));

        ProjectSplitPreviewService.PreviewResult result = service.preview(
                new ProjectSplitPreviewCommand(20L, 2), actor);

        assertFalse(result.valid());
        assertFalse(result.items().getFirst().valid());
        assertEquals(List.of("OVER_ALLOCATION:10"), result.items().getFirst().errors());
        assertEquals("DRAFT", draft().request().getStatus());
        verify(requestMapper).updateById(argThat((ProjectSplitRequestDO update) ->
                "INVALID".equals(update.getValidationStatus())));
        verify(metrics).preview(eq(false), eq("OVER_ALLOCATION"), anyLong());
    }

    @Test
    void shouldReturnControlledFailureWhenCommerceAuthorityIsUnavailable() {
        ProjectSplitDraftService.Actor actor = new ProjectSplitDraftService.Actor(1L, 9L, "corr-3");
        when(draftService.getDraft(20L, actor)).thenReturn(draft());
        when(projectMapper.selectById(100L)).thenReturn(parent());
        when(treeVersionMapper.selectLatestActive(100L)).thenReturn(tree());
        when(deliveryScopeApi.previewSplit(any())).thenThrow(new IllegalStateException("unavailable"));

        ProjectSplitPreviewService.PreviewResult result = service.preview(
                new ProjectSplitPreviewCommand(20L, 2), actor);

        assertFalse(result.valid());
        assertEquals(List.of("COMMERCE_SCOPE_UNAVAILABLE"), result.errors());
        verify(requestMapper).updateById(argThat((ProjectSplitRequestDO update) ->
                "INVALID".equals(update.getValidationStatus())));
        verify(auditService).record(eq(1L), eq(9L), eq("corr-3"), eq("PROJECT_SPLIT_PREVIEW"),
                eq(20L), eq("VALIDATION_FAILED"), anyMap());
    }

    private ProjectSplitDraftService.DraftResult draft() {
        ProjectSplitRequestDO request = new ProjectSplitRequestDO();
        request.setId(20L); request.setTenantId(1L); request.setParentProjectId(100L);
        request.setStatus("DRAFT"); request.setDraftVersion(2); request.setParentVersion(3);
        request.setScopeVersion(5L); request.setTreeVersion(7L);
        ProjectSplitItemDO item = new ProjectSplitItemDO();
        item.setId(30L); item.setClientItemKey("A"); item.setProjectName("子项目A");
        ProjectSplitScopeDO scope = new ProjectSplitScopeDO();
        scope.setId(40L); scope.setSplitItemId(30L); scope.setOrderLineId(10L);
        scope.setAllocatedQty(BigDecimal.ONE); scope.setSourceScopeVersion(5L);
        return new ProjectSplitDraftService.DraftResult(request, List.of(item), List.of(scope));
    }

    private ProjectMasterDO parent() {
        ProjectMasterDO parent = new ProjectMasterDO();
        parent.setId(100L); parent.setRootId(100L); parent.setVersion(3);
        return parent;
    }

    private ProjectTreeVersionDO tree() {
        ProjectTreeVersionDO tree = new ProjectTreeVersionDO();
        tree.setTreeVersion(7L);
        return tree;
    }
}
