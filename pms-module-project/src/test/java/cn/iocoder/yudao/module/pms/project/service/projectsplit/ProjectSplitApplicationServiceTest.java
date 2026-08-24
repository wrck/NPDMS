package cn.iocoder.yudao.module.pms.project.service.projectsplit;

import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.SplitScopeApplyResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitRequestDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitScopeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeChangeDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitRequestMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeChangeMapper;
import cn.iocoder.yudao.module.pms.project.service.platform.ProjectCommandExecutionService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectChildCreationService;
import cn.iocoder.yudao.module.pms.project.service.projecttree.ProjectTreeProjectionService;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ApplyProjectSplitCommand;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitPreviewCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectSplitApplicationServiceTest {
    @Mock ProjectCommandExecutionService commandExecutionService;
    @Mock ProjectSplitDraftService draftService;
    @Mock ProjectSplitPreviewService previewService;
    @Mock ProjectSplitRequestMapper requestMapper;
    @Mock ProjectSplitItemMapper itemMapper;
    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectChildCreationService childCreationService;
    @Mock DeliveryScopeApi deliveryScopeApi;
    @Mock ProjectTreeProjectionService treeProjectionService;
    @Mock ProjectTreeChangeMapper treeChangeMapper;
    @Mock ProjectSplitMetrics metrics;
    @Mock ProjectTreeScopeService treeScopeService;

    private ProjectSplitApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProjectSplitApplicationService(commandExecutionService, draftService, previewService,
                requestMapper, itemMapper, projectMapper, childCreationService, deliveryScopeApi,
                treeProjectionService, treeChangeMapper, metrics, treeScopeService);
    }

    @Test
    void shouldApplyWholeBatchThroughOneCommandExecution() {
        ProjectSplitDraftService.Actor actor = new ProjectSplitDraftService.Actor(1L, 9L, "corr-1");
        ProjectSplitDraftService.DraftResult draft = draft();
        when(draftService.getDraft(20L, actor)).thenReturn(draft);
        doAnswer(invocation -> new ProjectCommandExecutionService.ExecutionResult<>(
                ProjectCommandExecutionService.Decision.NEW,
                invocation.<java.util.function.Supplier<?>>getArgument(3).get()))
                .when(commandExecutionService).execute(any(), anyString(), eq(cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ApplyProjectSplitResult.class), any(), any());
        when(requestMapper.selectByIdForUpdate(20L)).thenReturn(draft.request());
        ProjectMasterDO parent = parent();
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(parent);
        when(previewService.preview(new ProjectSplitPreviewCommand(20L, 2), actor)).thenReturn(
                new ProjectSplitPreviewService.PreviewResult(20L, 2, true, "h", LocalDateTime.now(),
                        3, 5L, 7L, List.of(), List.of(
                        new ProjectSplitPreviewService.ItemResult("A", true, List.of()))));
        ProjectMasterDO child = new ProjectMasterDO();
        child.setId(200L); child.setProjectCode("P-001-001"); child.setParentId(100L);
        child.setRootId(100L); child.setTreeDepth(1); child.setTreeSort(0);
        when(childCreationService.create(parent, draft.items().getFirst(), 1L, 20L)).thenReturn(child);
        when(deliveryScopeApi.applySplit(any())).thenReturn(
                new SplitScopeApplyResult(true, false, 6L, List.of(), List.of()));
        when(itemMapper.markApplied(1L, 20L, 30L, 200L)).thenReturn(1);
        when(treeProjectionService.publish(eq(100L), eq(8L), anyString())).thenReturn(
                new ProjectTreeProjectionService.ProjectionResult(100L, 8L, 2, 3));
        when(requestMapper.markAppliedIfMatch(eq(1L), eq(20L), eq(2), anyString())).thenReturn(1);

        var result = service.apply(command(), actor);

        assertFalse(result.replayed());
        assertEquals(200L, result.projects().getFirst().projectId());
        assertEquals(8L, result.treeVersion());
        verify(treeScopeService).assertFullAccess(9L, 100L, 7L);
        verify(deliveryScopeApi).applySplit(argThat(value -> value.projectIdsByClientItemKey().get("A") == 200L));
        verify(treeProjectionService).publish(eq(100L), eq(8L), anyString());
        verify(treeChangeMapper).insert(any(ProjectTreeChangeDO.class));
    }

    @Test
    void shouldStopBeforeCompletionFactsWhenCommerceRejectsAllocation() {
        ProjectSplitDraftService.Actor actor = new ProjectSplitDraftService.Actor(1L, 9L, "corr-2");
        ProjectSplitDraftService.DraftResult draft = draft();
        when(draftService.getDraft(20L, actor)).thenReturn(draft);
        doAnswer(invocation -> new ProjectCommandExecutionService.ExecutionResult<>(
                ProjectCommandExecutionService.Decision.NEW,
                invocation.<java.util.function.Supplier<?>>getArgument(3).get()))
                .when(commandExecutionService).execute(any(), anyString(), eq(cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ApplyProjectSplitResult.class), any(), any());
        when(requestMapper.selectByIdForUpdate(20L)).thenReturn(draft.request());
        ProjectMasterDO parent = parent();
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(parent);
        when(previewService.preview(new ProjectSplitPreviewCommand(20L, 2), actor)).thenReturn(
                new ProjectSplitPreviewService.PreviewResult(20L, 2, true, "h", LocalDateTime.now(),
                        3, 5L, 7L, List.of(), List.of(
                        new ProjectSplitPreviewService.ItemResult("A", true, List.of()))));
        ProjectMasterDO child = new ProjectMasterDO(); child.setId(200L); child.setProjectCode("P-001-001");
        when(childCreationService.create(parent, draft.items().getFirst(), 1L, 20L)).thenReturn(child);
        when(deliveryScopeApi.applySplit(any())).thenReturn(
                new SplitScopeApplyResult(false, false, null, List.of(), List.of("OVER_ALLOCATION:10")));

        assertThrows(RuntimeException.class, () -> service.apply(command(), actor));

        verify(itemMapper, never()).markApplied(anyLong(), anyLong(), anyLong(), anyLong());
        verify(treeProjectionService, never()).publish(anyLong(), anyLong(), anyString());
        verify(requestMapper, never()).markAppliedIfMatch(anyLong(), anyLong(), anyInt(), anyString());
    }

    @Test
    void shouldStopBatchWhenChildCreationFails() {
        ProjectSplitDraftService.Actor actor = new ProjectSplitDraftService.Actor(1L, 9L, "corr-3");
        ProjectSplitDraftService.DraftResult draft = draft();
        when(draftService.getDraft(20L, actor)).thenReturn(draft);
        doAnswer(invocation -> new ProjectCommandExecutionService.ExecutionResult<>(
                ProjectCommandExecutionService.Decision.NEW,
                invocation.<java.util.function.Supplier<?>>getArgument(3).get()))
                .when(commandExecutionService).execute(any(), anyString(), eq(cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ApplyProjectSplitResult.class), any(), any());
        when(requestMapper.selectByIdForUpdate(20L)).thenReturn(draft.request());
        ProjectMasterDO parent = parent();
        when(projectMapper.selectByIdForUpdate(100L)).thenReturn(parent);
        when(previewService.preview(new ProjectSplitPreviewCommand(20L, 2), actor)).thenReturn(
                new ProjectSplitPreviewService.PreviewResult(20L, 2, true, "h", LocalDateTime.now(),
                        3, 5L, 7L, List.of(), List.of(
                        new ProjectSplitPreviewService.ItemResult("A", true, List.of()))));
        when(childCreationService.create(parent, draft.items().getFirst(), 1L, 20L))
                .thenThrow(new IllegalStateException("template instantiation failed"));

        assertThrows(IllegalStateException.class, () -> service.apply(command(), actor));

        verify(deliveryScopeApi, never()).applySplit(any());
        verify(requestMapper, never()).markAppliedIfMatch(anyLong(), anyLong(), anyInt(), anyString());
    }

    private ApplyProjectSplitCommand command() {
        return new ApplyProjectSplitCommand(20L, 2, 3, 5L, 7L, "idem-1", "a".repeat(64));
    }

    private ProjectSplitDraftService.DraftResult draft() {
        ProjectSplitRequestDO request = new ProjectSplitRequestDO();
        request.setId(20L); request.setTenantId(1L); request.setParentProjectId(100L);
        request.setStatus("DRAFT"); request.setDraftVersion(2); request.setParentVersion(3);
        request.setScopeVersion(5L); request.setTreeVersion(7L);
        ProjectSplitItemDO item = new ProjectSplitItemDO();
        item.setId(30L); item.setClientItemKey("A"); item.setProjectName("子项目A"); item.setItemStatus("VALID");
        ProjectSplitScopeDO scope = new ProjectSplitScopeDO();
        scope.setId(40L); scope.setSplitItemId(30L); scope.setOrderLineId(10L);
        scope.setAllocatedQty(BigDecimal.ONE); scope.setSourceScopeVersion(5L);
        return new ProjectSplitDraftService.DraftResult(request, List.of(item), List.of(scope));
    }

    private ProjectMasterDO parent() {
        ProjectMasterDO parent = new ProjectMasterDO();
        parent.setId(100L); parent.setTenantId(1L); parent.setRootId(100L);
        parent.setTreeDepth(0); parent.setTreeSort(0); parent.setVersion(3);
        return parent;
    }
}
