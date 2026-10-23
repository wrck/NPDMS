package cn.iocoder.yudao.module.pms.cutover.controller.business.approval;

import cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.view.CutoverApprovalViews;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCalculator;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CutoverApprovalControllerContractTest {

    @Test
    void rejectsAmbiguousConcurrencyHeaderBeforeExecutingDecision() throws Exception {
        mvc.perform(post("/api/v1/pms/cutover-approvals/100/actions/approve")
                .header("If-Match", "0", "1").header("X-Task-Version", "4")
                .header("Idempotency-Key", "approve-duplicate-header")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(application);
    }
    private CutoverApprovalApplicationService application;
    private CutoverApprovalQueryService query;
    private AdminUserApi users;
    private MockMvc mvc;
    private final cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService checklists = mock(cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService.class);

    @BeforeEach
    void setUp() {
        application = mock(CutoverApprovalApplicationService.class);
        query = mock(CutoverApprovalQueryService.class);
        users = mock(AdminUserApi.class);
        when(query.detailByInstance(any(), anyLong())).thenReturn(detail());
        CutoverApprovalRequestContext context = () -> new CutoverApprovalContext(
                1L, 8L, "corr-approval-1", true, true, true);
        CutoverApprovalController controller = new TestController(application, query, context,
                new CutoverApprovalRequestCodec(), checklists, users);
        mvc = standaloneSetup(controller).setControllerAdvice(controller).build();
    }

    @Test
    void registersProductionControllerAndUsesThreeLockedPermissions() throws Exception {
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverApprovalController.class, RestController.class)).isTrue();
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverApprovalController.class, Component.class)).isTrue();
        assertPermission("myTodos", "pms:cutover-task:query-approval", Integer.class, Integer.class);
        assertPermission("approve", "pms:cutover-task:approve", Long.class, org.springframework.http.HttpHeaders.class, tools.jackson.databind.JsonNode.class);
        assertPermission("reassign", "pms:cutover-task:reassign-approval", Long.class, org.springframework.http.HttpHeaders.class, tools.jackson.databind.JsonNode.class);
        assertPermission("reassignmentServiceManager", "pms:cutover-task:reassign-approval", Long.class);
    }

    @Test
    void servesCurrentManagerCandidateThroughTrustedReassignmentContext() throws Exception {
        when(query.reassignmentServiceManager(argThat(c -> c.tenantId() == 1L && c.actorId() == 8L && c.canReassign()), eq(100L)))
                .thenReturn(44L);
        mvc.perform(get("/api/v1/pms/cutover-approvals/100/reassignment-service-manager"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(44));
    }

    @Test
    void servesSixPositiveRoutesWithExactHeadersAndEpochResponses() throws Exception {
        var detail = detail();
        when(query.detail(any(), anyLong())).thenReturn(detail);
        when(query.decisionResponse(any(), any())).thenReturn(detail);
        var decisionResult = new cn.iocoder.yudao.module.pms.cutover.service.approval.result.CutoverApprovalDecisionResult(
                1L, 100L, 1L, 10L, 5L, 70L, 1, "PENDING", null, 1, 2, "P5", "APPROVING", null);
        when(application.approve(any())).thenReturn(decisionResult);
        when(application.reject(any())).thenReturn(decisionResult);
        when(query.myTodos(argThat(c -> c.tenantId() == 1L && c.actorId() == 8L), argThat(p -> p.getPageNo() == 1 && p.getPageSize() == 20))).thenReturn(new CutoverApprovalViews.Page<>(List.of(
                new CutoverApprovalViews.TodoItem(100L, 0L, 10L, 20L, "CUT-10", "核心割接", "A", 1,
                        "INITIATOR", LocalDateTime.of(2026, 9, 2, 10, 0))), 1, 1, 20));
        when(query.reassignmentCandidates(argThat(c -> c.tenantId() == 1L), argThat(p -> p.getPageNo() == 1 && p.getPageSize() == 20))).thenReturn(new CutoverApprovalViews.Page<>(List.of(
                new CutoverApprovalViews.ReassignmentCandidate(100L, 0L, 10L, 20L, "CUT-10", "核心割接", "A",
                        "PENDING", null, 101L, 1, "INITIATOR", "PENDING", 8L, 0,
                        LocalDateTime.of(2026, 9, 2, 10, 0))), 1, 1, 20));

        mvc.perform(get("/api/v1/pms/cutover-tasks/10/approval"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewMode").value("FULL"))
                .andExpect(jsonPath("$.data.leadTimeCompliance.ruleVersion").value("CUT_LEAD_TIME_R034_V1"))
                .andExpect(jsonPath("$.data.leadTimeCompliance.scheduledTime").isNumber());
        mvc.perform(get("/api/v1/pms/cutover-approvals/todos"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].createdAt").isNumber());
        mvc.perform(get("/api/v1/pms/cutover-approvals/reassignment-candidates"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.list[0].nodeCode").value("INITIATOR"));

        mvc.perform(post("/api/v1/pms/cutover-approvals/100/actions/approve")
                .header("If-Match", "0").header("X-Task-Version", "4").header("Idempotency-Key", "approve-1")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowedActions[0]").value("APPROVE"));
        verify(application).approve(argThat(command -> command.expectedTaskVersion() == 4
                && "corr-approval-1".equals(command.correlationId())));
        mvc.perform(post("/api/v1/pms/cutover-approvals/100/actions/approve")
                .header("If-Match", "0").header("X-Task-Version", "4").header("Idempotency-Key", "approve-1")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isOk());
        verify(application, times(2)).approve(any());
        verify(query, times(2)).decisionResponse(argThat(c -> c.tenantId() == 1L && c.actorId() == 8L), eq(decisionResult));
        verify(query, times(1)).detail(any(), anyLong());

        mvc.perform(post("/api/v1/pms/cutover-approvals/100/actions/reject")
                .header("If-Match", "0").header("X-Task-Version", "4").header("Idempotency-Key", "reject-1")
                .contentType("application/json").content(decision("REJECT", "NO", "回退演练不完整")))
                .andExpect(status().isOk());

        var reassignment = reassignment();
        reset(query);
        when(query.detailByInstance(any(), eq(100L))).thenReturn(reassignment);
        when(query.reassignmentCommandContext(argThat(c -> c.tenantId() == 1L && c.actorId() == 8L), eq(10L)))
                .thenReturn(new CutoverApprovalQueryService.ReassignmentCommandContext(reassignment, 4L));
        when(application.reassign(any())).thenReturn(reassignment);
        mvc.perform(post("/api/v1/pms/cutover-approvals/100/actions/reassign")
                .header("If-Match", "0").header("Idempotency-Key", "reassign-1")
                .contentType("application/json")
                .content("{\"nodeNo\":1,\"newApproverUserId\":9,\"reason\":\"当前审批人请假\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewMode").value("REASSIGNMENT_ONLY"))
                .andExpect(jsonPath("$.data.leadTimeCompliance").doesNotExist());
        verify(application).reassign(argThat(command -> command.expectedTaskVersion() == 4
                && command.approvalInstanceId() == 100L));
    }

    @Test
    void returnsStableRequestAndApplicationErrorEnvelopes() throws Exception {
        mvc.perform(post("/api/v1/pms/cutover-approvals/100/actions/approve")
                .header("X-Task-Version", "4").header("Idempotency-Key", "approve-1")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.reasonCode").value("HEADER_REQUIRED_OR_INVALID"));

        when(query.detail(any(), anyLong())).thenReturn(detail());
        doThrow(new cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException(
                cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.SOURCE_STALE,
                "source changed")).when(application).approve(any());
        mvc.perform(post("/api/v1/pms/cutover-approvals/100/actions/approve")
                .header("If-Match", "0").header("X-Task-Version", "4").header("Idempotency-Key", "approve-1")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.category").value("SOURCE_STALE"))
                .andExpect(jsonPath("$.data.recoveryAction").value("REFRESH_SOURCES"));
    }

    @Test
    void mapsTaskAndApprovalVersionsFromStructuredApplicationErrors() throws Exception {
        when(query.detail(any(), anyLong())).thenReturn(detail());
        when(application.approve(any())).thenThrow(new cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException(
                cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.VERSION_CONFLICT,
                "TASK_VERSION_STALE", null, 3L, 7L, "任务版本变化"));
        mvc.perform(post("/api/v1/pms/cutover-approvals/100/actions/approve")
                .header("If-Match", "0").header("X-Task-Version", "4").header("Idempotency-Key", "approve-1")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.reasonCode").value("TASK_VERSION_STALE"))
                .andExpect(jsonPath("$.data.currentApprovalVersion").value(3))
                .andExpect(jsonPath("$.data.currentTaskVersion").value(7));
    }

    @Test
    void explainsRejectedReassignmentWithoutLosingConflictOrRecoveryCode() throws Exception {
        var view = reassignment();
        when(query.detailByInstance(any(), eq(100L))).thenReturn(view);
        when(query.reassignmentCommandContext(any(), eq(10L)))
                .thenReturn(new CutoverApprovalQueryService.ReassignmentCommandContext(view, 4L));
        when(application.reassign(any())).thenThrow(new cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException(
                cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.STATE_CONFLICT,
                "REASSIGN_TARGET_NOT_OPEN", null, null, null, "改派目标不是当前唯一服务经理"));
        mvc.perform(post("/api/v1/pms/cutover-approvals/100/actions/reassign")
                .header("If-Match", "0").header("Idempotency-Key", "reassign-ineligible-manager")
                .contentType("application/json")
                .content("{\"nodeNo\":2,\"newApproverUserId\":1,\"reason\":\"人员调整\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg").value("改派目标不是当前唯一服务经理"))
                .andExpect(jsonPath("$.data.reasonCode").value("REASSIGN_TARGET_NOT_OPEN"))
                .andExpect(jsonPath("$.data.recoveryAction").value("REFRESH_APPROVAL"));
    }

    private static CutoverApprovalViews.ApprovalDetail detail() {
        return detail(List.of(new CutoverApprovalViews.Node(101L, 1, "INITIATOR", "PENDING", 8L, 8L,
                null, null, List.of(), null, null)));
    }

    private static CutoverApprovalViews.ApprovalDetail detail(List<CutoverApprovalViews.Node> nodes) {
        return new CutoverApprovalViews.ApprovalDetail("FULL", 100L, 0L, 10L, 4L, 70L, 1, "A", "PENDING",
                null, 1, nodes, null, new CutoverLeadTimeCalculator().calculate("A", "VERSION_UPGRADE",
                LocalDateTime.of(2026, 9, 5, 8, 0), LocalDateTime.of(2026, 9, 3, 18, 0)),
                null, null, null, List.of("APPROVE", "REJECT"));
    }

    @Test
    void metadataReadFailureRetainsAuthorizedSnapshotAndLimitedViewsDoNotReadMetadata() throws Exception {
        when(checklists.getApprovalSourcePresentation(any())).thenThrow(new IllegalStateException("unavailable"));
        mvc.perform(get("/api/v1/pms/cutover-approvals/100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewMode").value("FULL"))
                .andExpect(jsonPath("$.data.sourcePresentation.incomplete").value(true));
        verify(checklists).getApprovalSourcePresentation(argThat(q -> q.tenantId() == 1L && q.approval().approvalInstanceId() == 100L));
        clearInvocations(checklists);
        when(query.detailByInstance(any(), anyLong())).thenReturn(reassignment());
        mvc.perform(get("/api/v1/pms/cutover-approvals/100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sourcePresentation").doesNotExist());
        verifyNoInteractions(checklists);
    }

    @Test
    void deniedApprovalDoesNotReadSourcePresentation() throws Exception {
        when(query.detailByInstance(any(), anyLong())).thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        mvc.perform(get("/api/v1/pms/cutover-approvals/100"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data.sourcePresentation").doesNotExist());
        verifyNoInteractions(checklists, users);
    }

    @Test
    void resolvesOnlyAuthorizedParticipantsUsingTheUserOwnerApi() throws Exception {
        var initiator = new AdminUserRespDTO();
        initiator.setId(8L);
        initiator.setNickname("杨凯添");
        var unrelated = new AdminUserRespDTO();
        unrelated.setId(99L);
        unrelated.setNickname("无关人员");
        when(users.getUserList(java.util.Set.of(8L))).thenReturn(List.of(initiator, unrelated));
        mvc.perform(get("/api/v1/pms/cutover-approvals/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].currentApproverName").value("杨凯添"))
                .andExpect(jsonPath("$.data.nodes.length()").value(1))
                .andExpect(jsonPath("$.data.allowedActions[0]").value("APPROVE"));
        verify(users).getUserList(java.util.Set.of(8L));
        when(query.detailByInstance(any(), anyLong())).thenReturn(reassignment());
        mvc.perform(get("/api/v1/pms/cutover-approvals/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].currentApproverName").value("杨凯添"))
                .andExpect(jsonPath("$.data.sourceSnapshot").doesNotExist());
    }

    @Test
    void missingOrUnavailableUserNamesDoNotHideAuthorizedApprovalFacts() throws Exception {
        mvc.perform(get("/api/v1/pms/cutover-approvals/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].currentApproverUserId").value(8))
                .andExpect(jsonPath("$.data.nodes[0].currentApproverName").doesNotExist());
        when(users.getUserList(any())).thenThrow(new IllegalStateException("unavailable"));
        mvc.perform(get("/api/v1/pms/cutover-approvals/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].currentApproverUserId").value(8))
                .andExpect(jsonPath("$.data.nodes[0].currentApproverName").doesNotExist());
    }

    @Test
    void unassignedSharedRoleAndResultOnlyViewsDoNotQueryUsers() throws Exception {
        when(query.detailByInstance(any(), anyLong())).thenReturn(detail(List.of(
                new CutoverApprovalViews.Node(102L, 2, "SECOND_LINE", "PENDING", null, null,
                        null, null, List.of(), null, "probSupporter"))));
        mvc.perform(get("/api/v1/pms/cutover-approvals/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].currentApproverName").doesNotExist())
                .andExpect(jsonPath("$.data.nodes[0].candidateRoleCode").value("probSupporter"));
        when(query.detailByInstance(any(), anyLong())).thenReturn(new CutoverApprovalViews.ApprovalFinalResult(
                "FINAL_RESULT_ONLY", 100L, 10L, 70L, "D", "APPROVED", LocalDateTime.now(), null, List.of()));
        mvc.perform(get("/api/v1/pms/cutover-approvals/100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.nodes").doesNotExist());
        verifyNoInteractions(users);
    }

    private static CutoverApprovalViews.ApprovalReassignmentView reassignment() {
        return new CutoverApprovalViews.ApprovalReassignmentView("REASSIGNMENT_ONLY", 100L, 0L, 10L, 20L,
                "CUT-10", "核心割接", "A", "PENDING", null,
                List.of(new CutoverApprovalViews.ReassignmentNode(101L, 1, "INITIATOR", "PENDING", 8L, 0)),
                List.of("REASSIGN"));
    }

    private static void assertPermission(String methodName, String expected, Class<?>... types) throws Exception {
        Method method = CutoverApprovalController.class.getMethod(methodName, types);
        assertThat(method.getAnnotation(PreAuthorize.class).value()).contains(expected);
    }

    private static String decision(String action, String decision, String reason) {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        return """
                {"action":"%s","reviewItems":[
                  {"itemCode":"PREPARATION","decision":"%s","unreasonableReason":%s},
                  {"itemCode":"BUSINESS_TEST","decision":"%s","unreasonableReason":%s},
                  {"itemCode":"EXECUTION","decision":"%s","unreasonableReason":%s},
                  {"itemCode":"ROLLBACK","decision":"%s","unreasonableReason":%s},
                  {"itemCode":"OTHER","decision":"%s","unreasonableReason":%s}
                ],"assessmentReview":null,"feedback":"已完成本节点评审"}
                """.formatted(action, decision, reasonJson, decision, reasonJson, decision, reasonJson,
                decision, reasonJson, decision, reasonJson);
    }

    @RestController
    private static final class TestController extends CutoverApprovalController {
        private TestController(CutoverApprovalApplicationService application, CutoverApprovalQueryService query,
                               CutoverApprovalRequestContext context, CutoverApprovalRequestCodec codec,
                               cn.iocoder.yudao.module.pms.cutover.service.checklist.CutoverChecklistApplicationService checklists,
                               AdminUserApi users) {
            super(application, query, context, codec, checklists, users);
        }
    }
}
