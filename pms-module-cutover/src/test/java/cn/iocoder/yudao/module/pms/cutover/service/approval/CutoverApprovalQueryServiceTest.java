package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.projection.ApprovalTodoPageRow;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalSourceSnapshotCodec;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCalculator;
import cn.iocoder.yudao.module.pms.cutover.service.approval.view.CutoverApprovalViews;
import cn.iocoder.yudao.module.pms.cutover.service.spare.CutoverSpareQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.spare.view.CutoverSpareViews;
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
        when(f.scopes.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(1L)
                .projectId(20L)
                .subjectUserId(33L)
                .requiredAction("ACTION_VIEW")
                .build())).thenReturn(scope(33L, "ACTION_VIEW"));

        var view = f.service.detail(new cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext(1L, 33L, "query-test", true, true, false), 10L);

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

        var commandContext = f.service.reassignmentCommandContext(new cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext(1L, 99L, "query-test", false, false, true), 10L);
        var view = commandContext.view();

        assertThat(view).isInstanceOfSatisfying(CutoverApprovalViews.ApprovalReassignmentView.class, result -> {
            assertThat(result.viewMode()).isEqualTo("REASSIGNMENT_ONLY");
            assertThat(result.allowedActions()).containsExactly("REASSIGN");
            assertThat(result.nodes()).singleElement().extracting(CutoverApprovalViews.ReassignmentNode::nodeCode)
                    .isEqualTo("INITIATOR");
        });
        assertThat(commandContext.taskVersion()).isEqualTo(3);
        verifyNoInteractions(f.scopes, f.managers, f.candidates);
    }

    @Test
    void returnsQualifiedInitiatorTodoFromControlledOwnerFact() {
        Fixture f = new Fixture();
        CutoverApprovalNodeDO node = node();
        ApprovalTodoPageRow row = new ApprovalTodoPageRow();
        row.setNodeId(101L);
        row.setApprovalInstanceId(100L); row.setApprovalVersion(0L); row.setTaskId(10L); row.setProjectId(20L);
        row.setTaskCode("CUT-10"); row.setTaskName("割接任务"); row.setGrade("A"); row.setNodeNo(1);
        row.setNodeCode("INITIATOR"); row.setCreatedAt(LocalDateTime.now());
        when(f.nodes.selectTodoPage(any())).thenReturn(List.of(node));
        when(f.nodes.selectTodoProjectionPage(any())).thenReturn(List.of(row));
        when(f.instances.selectById(100L)).thenReturn(root("PENDING"));
        when(f.scopes.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(1L)
                .projectId(20L)
                .subjectUserId(11L)
                .requiredAction("ACTION_EDIT")
                .build())).thenReturn(scope(11L, "ACTION_EDIT"));

        var page = f.service.myTodos(new cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext(1L, 11L, "query-test", true, true, false), new cn.iocoder.yudao.framework.common.pojo.PageParam());

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.list()).singleElement().extracting(CutoverApprovalViews.TodoItem::taskCode)
                .isEqualTo("CUT-10");
    }

    @Test
    void returnsSecondLineTodoByCurrentSelectedPersonProjectScope() {
        Fixture f = new Fixture();
        CutoverApprovalNodeDO node = node(); node.setNodeCode("SECOND_LINE"); node.setCurrentApproverUserId(22L);
        ApprovalTodoPageRow row = new ApprovalTodoPageRow();
        row.setNodeId(101L); row.setApprovalInstanceId(100L); row.setApprovalVersion(0L);
        row.setTaskId(10L); row.setProjectId(20L); row.setTaskCode("CUT-10"); row.setTaskName("割接任务");
        row.setGrade("A"); row.setNodeNo(1); row.setNodeCode("SECOND_LINE"); row.setCreatedAt(LocalDateTime.now());
        when(f.nodes.selectTodoPage(any())).thenReturn(List.of(node));
        when(f.nodes.selectTodoProjectionPage(any())).thenReturn(List.of(row));
        when(f.instances.selectById(100L)).thenReturn(root("PENDING"));
        cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalNodeFacts.apply(node,
                cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalNodeFacts.personalRole(
                cn.iocoder.yudao.module.pms.cutover.service.approval.domain.CutoverApprovalNodeFacts.PersonalRole.builder()
                .role(new CutoverApprovalRoleCandidatePort.RoleReference(1L, 2L, "CUT_SECOND_LINE_APPROVER"))
                .scope(scope(22L, "ACTION_VIEW")).build()));
        when(f.candidates.inspectActor(any())).thenAnswer(invocation -> {
            CutoverApprovalRoleCandidatePort.ActorQuery query = invocation.getArgument(0);
            return List.of(new CutoverApprovalRoleCandidatePort.ActorMembership(
                    new CutoverApprovalRoleCandidatePort.RoleReference(query.tenantId(), 2L, "CUT_SECOND_LINE_APPROVER"),
                    new CutoverApprovalRoleCandidatePort.Candidate(query.actorId(), 2L, 1L, 1L)));
        });
        when(f.scopes.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(1L)
                .projectId(20L)
                .subjectUserId(22L)
                .requiredAction("ACTION_VIEW")
                .build())).thenReturn(scope(22L, "ACTION_VIEW"));
        when(f.scopes.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(1L)
                .projectId(20L)
                .subjectUserId(23L)
                .requiredAction("ACTION_VIEW")
                .build())).thenReturn(
                new CutoverApprovalProjectScopePort.ProjectScopeFact(1L, 20L, 23L, "ACTION_VIEW", false, 7L));

        var page = f.service.myTodos(new cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext(1L, 22L, "query-test", true, true, false), new cn.iocoder.yudao.framework.common.pojo.PageParam());

        assertThat(page.list()).singleElement().extracting(CutoverApprovalViews.TodoItem::nodeCode)
                .isEqualTo("SECOND_LINE");
        verify(f.scopes).inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(1L).projectId(20L).subjectUserId(22L).requiredAction("ACTION_VIEW").build());
        verify(f.scopes, never()).inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(1L)
                .projectId(20L)
                .subjectUserId(23L)
                .requiredAction("ACTION_VIEW")
                .build());
    }

    @Test
    void returnsFrozenDecisionResponseAfterCurrentNodeAdvances() {
        Fixture f = new Fixture();
        CutoverApprovalInstanceDO root = root("PENDING"); root.setCurrentNodeNo(2);
        CutoverApprovalNodeDO decided = node(); decided.setStatusCode("APPROVED"); decided.setDecisionAt(LocalDateTime.now());
        CutoverApprovalNodeDO next = node(); next.setId(102L); next.setNodeNo(2); next.setNodeCode("SERVICE_MANAGER");
        next.setOriginalApproverUserId(22L); next.setCurrentApproverUserId(22L);
        when(f.instances.selectById(100L)).thenReturn(root);
        when(f.tasks.selectById(10L)).thenReturn(task());
        when(f.nodes.selectList(any())).thenReturn(List.of(decided, next));
        when(f.reviews.selectList(any())).thenReturn(List.of());

        var response = f.service.decisionResponse(new cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext(1L, 11L, "query-test", true, true, false), new cn.iocoder.yudao.module.pms.cutover.service.approval.result.CutoverApprovalDecisionResult(1L, 100L, 1L, 10L, 5L, 70L, 1, "PENDING", null, 1, 2, "P5", "APPROVING", null));

        assertThat(response.approvalInstanceId()).isEqualTo(100L);
        assertThat(response.currentNodeNo()).isEqualTo(2);
        assertThat(response.leadTimeCompliance()).isNull();
        assertThat(response.allowedActions()).isEmpty();
        verifyNoInteractions(f.scopes, f.managers, f.candidates);
    }

    @Test
    void returnsFrozenLeadTimeOnlyFromFullProjection() {
        Fixture f = new Fixture();
        CutoverApprovalInstanceDO root = root("PENDING");
        root.setGradeCode("A"); root.setSourceCutoverTypeCode("SIGNATURE_UPGRADE");
        root.setSourceScheduleTime(LocalDateTime.of(2026, 9, 2, 8, 0));
        CutoverApprovalStorage.applyLeadTime(root, new CutoverLeadTimeCalculator().calculate(
                "A", "SIGNATURE_UPGRADE", LocalDateTime.of(2026, 9, 2, 8, 0),
                LocalDateTime.of(2026, 9, 2, 18, 0)));
        when(f.instances.selectCurrentByTask(any())).thenReturn(root);
        when(f.tasks.selectById(10L)).thenReturn(task());
        when(f.nodes.selectList(any())).thenReturn(List.of());
        when(f.reviews.selectList(any())).thenReturn(List.of());
        when(f.scopes.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(1L)
                .projectId(20L)
                .subjectUserId(11L)
                .requiredAction("ACTION_VIEW")
                .build())).thenReturn(scope(11L, "ACTION_VIEW"));

        var view = f.service.detail(new cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext(1L, 11L, "query-test", true, true, false), 10L);

        assertThat(view).isInstanceOfSatisfying(CutoverApprovalViews.ApprovalDetail.class, detail -> {
            assertThat(detail.leadTimeCompliance()).isNotNull();
            assertThat(detail.leadTimeCompliance().lateSubmission()).isTrue();
            assertThat(detail.leadTimeCompliance().requiredDays()).isEqualTo(1);
        });
    }

    @Test
    void addsSafeSpareSummaryOnlyToFullProjection() {
        Fixture f = new Fixture();
        CutoverApprovalInstanceDO root = root("PENDING");
        when(f.instances.selectCurrentByTask(any())).thenReturn(root);
        when(f.tasks.selectById(10L)).thenReturn(task());
        when(f.nodes.selectList(any())).thenReturn(List.of());
        when(f.reviews.selectList(any())).thenReturn(List.of());
        when(f.scopes.inspect(CutoverApprovalProjectScopePort.ScopeQuery.builder()
                .tenantId(1L)
                .projectId(20L)
                .subjectUserId(11L)
                .requiredAction("ACTION_VIEW")
                .build())).thenReturn(scope(11L, "ACTION_VIEW"));
        var summary = new CutoverSpareViews.ApprovalSummary(true,
                List.of(new CutoverSpareViews.ApplicationApprovalSummary("EXTERNAL_REFERENCED", "SPARE", "APP-1",
                        "PROCESSING", LocalDateTime.of(2026, 9, 2, 8, 0), null)),
                List.of(new CutoverSpareViews.EvidenceApprovalSummary("签收单.pdf", "现场签收",
                        LocalDateTime.of(2026, 9, 2, 8, 1))));
        when(f.spare.approvalSummary(1L, 10L, 11L)).thenReturn(summary);

        var view = f.service.detail(new cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext(1L, 11L, "query-test", true, true, false), 10L);

        assertThat(view).isInstanceOfSatisfying(CutoverApprovalViews.ApprovalDetail.class,
                detail -> assertThat(detail.spareSupport()).isEqualTo(summary));
    }

    private static CutoverApprovalInstanceDO root(String status) {
        CutoverApprovalInstanceDO row = new CutoverApprovalInstanceDO();
        row.setId(100L); row.setTenantId(1L); row.setTaskId(10L); row.setProjectId(20L); row.setPlanRevisionId(200L);
        row.setPlanRevisionNo(1); row.setGradeCode("A"); row.setInitiatorUserId(11L); row.setStatusCode(status);
        row.setLeadTimeEnabled(false); row.setCurrentNodeNo(1); row.setVersion(0L); return row;
    }
    private static CutoverTaskDO task() {
        CutoverTaskDO row = new CutoverTaskDO(); row.setId(10L); row.setTenantId(1L); row.setTaskNo("CUT-10");
        row.setTaskName("割接任务"); row.setVersion(3L); return row;
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
        final CutoverApprovalSourceSnapshotCodec codec = mock(CutoverApprovalSourceSnapshotCodec.class);
        final CutoverSpareQueryService spare = mock(CutoverSpareQueryService.class);
        final CutoverApprovalQueryService service = new CutoverApprovalQueryService(instances, nodes, reviews, tasks,
                managers, candidates, scopes, codec, spare);
        Fixture() {
            var storage = mock(CutoverApprovalStorage.class);
            when(storage.sourceView(any())).thenReturn(cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(CutoverApprovalControlledPorts.approvalSource("A")));
            org.springframework.test.util.ReflectionTestUtils.setField(service, "storage", storage);
        }
    }
}
