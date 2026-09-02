package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.approval.view.CutoverApprovalViews;
import cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime.CutoverLeadTimeCalculator;
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
    private CutoverApprovalApplicationService application;
    private CutoverApprovalQueryService query;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        application = mock(CutoverApprovalApplicationService.class);
        query = mock(CutoverApprovalQueryService.class);
        CutoverApprovalRequestContext context = () -> new CutoverApprovalRequestContext.TrustedContext(
                1L, 8L, "corr-approval-1", true, true, true);
        CutoverApprovalController controller = new TestController(application, query, context,
                new CutoverApprovalRequestCodec());
        mvc = standaloneSetup(controller).setControllerAdvice(controller).build();
    }

    @Test
    void keepsCandidateOutsideProductionRegistrationAndUsesThreeLockedPermissions() throws Exception {
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverApprovalController.class, RestController.class)).isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(CutoverApprovalController.class, Component.class)).isFalse();
        assertPermission("myTodos", "pms:cutover-task:query-approval", Integer.class, Integer.class);
        assertPermission("approve", "pms:cutover-task:approve", Long.class, String.class, String.class,
                String.class, tools.jackson.databind.JsonNode.class);
        assertPermission("reassign", "pms:cutover-task:reassign-approval", Long.class, String.class,
                String.class, tools.jackson.databind.JsonNode.class);
    }

    @Test
    void servesSixPositiveRoutesWithExactHeadersAndEpochResponses() throws Exception {
        var detail = detail();
        when(query.detail(anyLong(), anyLong(), anyLong(), anyBoolean(), anyBoolean())).thenReturn(detail);
        when(query.decisionResponse(anyLong(), anyLong(), anyLong(), anyInt(), anyLong())).thenReturn(detail);
        var decisionResult = new cn.iocoder.yudao.module.pms.cutover.service.approval.result.CutoverApprovalDecisionResult(
                1L, 100L, 1, 10L, 5, 70L, 1, "PENDING", null, 1, 2, "P5", "APPROVING", null);
        when(application.approve(any())).thenReturn(decisionResult);
        when(application.reject(any())).thenReturn(decisionResult);
        when(query.myTodos(1L, 8L, 1, 20)).thenReturn(new CutoverApprovalViews.Page<>(List.of(
                new CutoverApprovalViews.TodoItem(100L, 0, 10L, 20L, "CUT-10", "核心割接", "A", 1,
                        "INITIATOR", LocalDateTime.of(2026, 9, 2, 10, 0))), 1, 1, 20));
        when(query.reassignmentCandidates(1L, 1, 20)).thenReturn(new CutoverApprovalViews.Page<>(List.of(
                new CutoverApprovalViews.ReassignmentCandidate(100L, 0, 10L, 20L, "CUT-10", "核心割接", "A",
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

        mvc.perform(post("/api/v1/pms/cutover-tasks/10/approval-actions/approve")
                .header("If-Match", "0").header("X-Task-Version", "4").header("Idempotency-Key", "approve-1")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowedActions[0]").value("APPROVE"));
        verify(application).approve(argThat(command -> command.expectedTaskVersion() == 4
                && "corr-approval-1".equals(command.correlationId())));
        mvc.perform(post("/api/v1/pms/cutover-tasks/10/approval-actions/approve")
                .header("If-Match", "0").header("X-Task-Version", "4").header("Idempotency-Key", "approve-1")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isOk());
        verify(application, times(2)).approve(any());
        verify(query, times(2)).decisionResponse(1L, 10L, 100L, 1, 8L);
        verify(query, times(1)).detail(anyLong(), anyLong(), anyLong(), anyBoolean(), anyBoolean());

        mvc.perform(post("/api/v1/pms/cutover-tasks/10/approval-actions/reject")
                .header("If-Match", "0").header("X-Task-Version", "4").header("Idempotency-Key", "reject-1")
                .contentType("application/json").content(decision("REJECT", "NO", "回退演练不完整")))
                .andExpect(status().isOk());

        var reassignment = reassignment();
        reset(query);
        when(query.reassignmentCommandContext(1L, 10L, 8L))
                .thenReturn(new CutoverApprovalQueryService.ReassignmentCommandContext(reassignment, 4));
        when(application.reassign(any())).thenReturn(reassignment);
        mvc.perform(post("/api/v1/pms/cutover-tasks/10/approval-actions/reassign")
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
        mvc.perform(post("/api/v1/pms/cutover-tasks/10/approval-actions/approve")
                .header("X-Task-Version", "4").header("Idempotency-Key", "approve-1")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.reasonCode").value("HEADER_REQUIRED_OR_INVALID"));

        when(query.detail(anyLong(), anyLong(), anyLong(), anyBoolean(), anyBoolean())).thenReturn(detail());
        doThrow(new cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException(
                cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.SOURCE_STALE,
                "source changed")).when(application).approve(any());
        mvc.perform(post("/api/v1/pms/cutover-tasks/10/approval-actions/approve")
                .header("If-Match", "0").header("X-Task-Version", "4").header("Idempotency-Key", "approve-1")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.category").value("SOURCE_STALE"))
                .andExpect(jsonPath("$.data.recoveryAction").value("REFRESH_SOURCES"));
    }

    @Test
    void mapsTaskAndApprovalVersionsFromStructuredApplicationErrors() throws Exception {
        when(query.detail(anyLong(), anyLong(), anyLong(), anyBoolean(), anyBoolean())).thenReturn(detail());
        when(application.approve(any())).thenThrow(new cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException(
                cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverApprovalApplicationException.Code.VERSION_CONFLICT,
                "TASK_VERSION_STALE", null, 3, 7, "任务版本变化"));
        mvc.perform(post("/api/v1/pms/cutover-tasks/10/approval-actions/approve")
                .header("If-Match", "0").header("X-Task-Version", "4").header("Idempotency-Key", "approve-1")
                .contentType("application/json").content(decision("APPROVE", "YES", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.reasonCode").value("TASK_VERSION_STALE"))
                .andExpect(jsonPath("$.data.currentApprovalVersion").value(3))
                .andExpect(jsonPath("$.data.currentTaskVersion").value(7));
    }

    private static CutoverApprovalViews.ApprovalDetail detail() {
        return new CutoverApprovalViews.ApprovalDetail("FULL", 100L, 0, 10L, 4, 70L, 1, "A", "PENDING",
                null, 1, List.of(new CutoverApprovalViews.Node(101L, 1, "INITIATOR", "PENDING", 8L, 8L,
                null, null, List.of(), null)), null, new CutoverLeadTimeCalculator().calculate("A", "VERSION_UPGRADE",
                LocalDateTime.of(2026, 9, 5, 8, 0), LocalDateTime.of(2026, 9, 3, 18, 0)),
                null, null, null, List.of("APPROVE", "REJECT"));
    }

    private static CutoverApprovalViews.ApprovalReassignmentView reassignment() {
        return new CutoverApprovalViews.ApprovalReassignmentView("REASSIGNMENT_ONLY", 100L, 0, 10L, 20L,
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
                               CutoverApprovalRequestContext context, CutoverApprovalRequestCodec codec) {
            super(application, query, context, codec);
        }
    }
}
