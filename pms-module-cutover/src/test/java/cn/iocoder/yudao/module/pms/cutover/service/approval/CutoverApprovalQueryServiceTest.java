package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.projection.ApprovalTodoPageRow;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.view.CutoverApprovalViews;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalQueryServiceTest {

    @Test
    void returnsTerminalFinalResultToCurrentProjectViewer() {
        Fixture f = new Fixture();
        CutoverApprovalInstanceDO root = root("APPROVED"); root.setDecisionAt(LocalDateTime.now());
        when(f.instances.selectCurrentByTask(any())).thenReturn(root);
        when(f.tasks.selectById(10L)).thenReturn(task());
        when(f.nodes.selectList(any())).thenReturn(List.of());
        when(f.scopes.inspect(1L, 20L, 33L, "ACTION_VIEW")).thenReturn(scope(33L, "ACTION_VIEW"));

        var view = f.service.detail(1L, 10L, 33L, true, false);

        assertThat(view).isInstanceOfSatisfying(CutoverApprovalViews.ApprovalFinalResult.class,
                result -> assertThat(result.viewMode()).isEqualTo("FINAL_RESULT_ONLY"));
    }

    @Test
    void returnsMetadataOnlyReassignmentProjectionWithoutOwnerLookup() {
        Fixture f = new Fixture();
        CutoverApprovalInstanceDO root = root("PENDING");
        when(f.instances.selectCurrentByTask(any())).thenReturn(root);
        when(f.tasks.selectById(10L)).thenReturn(task());
        when(f.nodes.selectList(any())).thenReturn(List.of(node()));

        var view = f.service.detail(1L, 10L, 99L, false, true);

        assertThat(view).isInstanceOfSatisfying(CutoverApprovalViews.ApprovalReassignmentView.class, result -> {
            assertThat(result.viewMode()).isEqualTo("REASSIGNMENT_ONLY");
            assertThat(result.allowedActions()).containsExactly("REASSIGN");
            assertThat(result.nodes()).singleElement().extracting(CutoverApprovalViews.ReassignmentNode::nodeCode)
                    .isEqualTo("INITIATOR");
        });
        verifyNoInteractions(f.scopes, f.managers, f.candidates);
    }

    @Test
    void returnsQualifiedInitiatorTodoFromControlledOwnerFact() {
        Fixture f = new Fixture();
        CutoverApprovalNodeDO node = node();
        ApprovalTodoPageRow row = new ApprovalTodoPageRow();
        row.setApprovalInstanceId(100L); row.setApprovalVersion(0); row.setTaskId(10L); row.setProjectId(20L);
        row.setTaskCode("CUT-10"); row.setTaskName("割接任务"); row.setGrade("A"); row.setNodeNo(1);
        row.setNodeCode("INITIATOR"); row.setCreatedAt(LocalDateTime.now());
        when(f.nodes.selectTodoPage(any())).thenReturn(List.of(node));
        when(f.nodes.selectTodoProjectionPage(any())).thenReturn(List.of(row));
        when(f.instances.selectById(100L)).thenReturn(root("PENDING"));
        when(f.scopes.inspect(1L, 20L, 11L, "ACTION_EDIT")).thenReturn(scope(11L, "ACTION_EDIT"));

        var page = f.service.myTodos(1L, 11L, 1, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.list()).singleElement().extracting(CutoverApprovalViews.TodoItem::taskCode)
                .isEqualTo("CUT-10");
    }

    private static CutoverApprovalInstanceDO root(String status) {
        CutoverApprovalInstanceDO row = new CutoverApprovalInstanceDO();
        row.setId(100L); row.setTenantId(1L); row.setTaskId(10L); row.setProjectId(20L); row.setPlanRevisionId(200L);
        row.setPlanRevisionNo(1); row.setGradeCode("A"); row.setInitiatorUserId(11L); row.setStatusCode(status);
        row.setCurrentNodeNo(1); row.setVersion(0); return row;
    }
    private static CutoverTaskDO task() {
        CutoverTaskDO row = new CutoverTaskDO(); row.setId(10L); row.setTenantId(1L); row.setTaskNo("CUT-10");
        row.setTaskName("割接任务"); row.setVersion(3); return row;
    }
    private static CutoverApprovalNodeDO node() {
        CutoverApprovalNodeDO row = new CutoverApprovalNodeDO(); row.setId(101L); row.setTenantId(1L);
        row.setApprovalInstanceId(100L); row.setNodeNo(1); row.setNodeCode("INITIATOR"); row.setStatusCode("PENDING");
        row.setOriginalApproverUserId(11L); row.setCurrentApproverUserId(11L); row.setVersion(0); return row;
    }
    private static CutoverApprovalProjectScopePort.ProjectScopeFact scope(long userId, String action) {
        return new CutoverApprovalProjectScopePort.ProjectScopeFact(1L, 20L, userId, action, true, 7L);
    }

    private static final class Fixture {
        final CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
        final CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
        final CutoverApprovalReviewItemMapper reviews = mock(CutoverApprovalReviewItemMapper.class);
        final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
        final ProjectCutoverServiceManagerPort managers = mock(ProjectCutoverServiceManagerPort.class);
        final CutoverApprovalRoleCandidatePort candidates = mock(CutoverApprovalRoleCandidatePort.class);
        final CutoverApprovalProjectScopePort scopes = mock(CutoverApprovalProjectScopePort.class);
        final CutoverApprovalQueryService service = new CutoverApprovalQueryService(instances, nodes, reviews, tasks,
                managers, candidates, scopes, new CutoverApprovalSourceSnapshotCodec());
    }
}
