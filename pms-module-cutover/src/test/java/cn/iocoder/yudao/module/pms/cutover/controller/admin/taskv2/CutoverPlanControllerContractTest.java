package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.plan.result.*;
import cn.iocoder.yudao.module.pms.cutover.service.plan.view.CutoverPlanView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CutoverPlanControllerContractTest {
    private CutoverPlanApplicationService application;
    private CutoverPlanQueryService query;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        application = mock(CutoverPlanApplicationService.class);
        query = mock(CutoverPlanQueryService.class);
        CutoverPlanRequestContext context = () -> new CutoverPlanRequestContext.TrustedContext(
                1L, 8L, "corr-1", true, true, true);
        CutoverPlanController controller = new TestController(application, query, context,
                new CutoverPlanRequestCodec());
        when(query.detail(any(), any(), any(), any())).thenReturn(new CutoverPlanView(
                50L, "P4", 4, 60L, 1, 0, "NEW_PLATFORM", "DRAFT",
                null, null, null, "INITIAL", null, null, null, List.of("SAVE_DRAFT")));
        mvc = standaloneSetup(controller).setControllerAdvice(controller).build();
    }

    @Test
    void keepsCandidateOutsideProductionRegistrationAndServesSevenPositiveRoutes() throws Exception {
        org.assertj.core.api.Assertions.assertThat(AnnotatedElementUtils.hasAnnotation(
                CutoverPlanController.class, RestController.class)).isFalse();
        org.assertj.core.api.Assertions.assertThat(AnnotatedElementUtils.hasAnnotation(
                CutoverPlanController.class, Component.class)).isFalse();

        when(application.createDraft(any())).thenReturn(new CutoverPlanCommandResult(50L,4,60L,1,0,"DRAFT",false));
        when(application.saveDraft(any())).thenReturn(new CutoverPlanCommandResult(50L,4,60L,1,1,"DRAFT",false));
        when(application.downloadDraft(any())).thenReturn(new DownloadCutoverPlanDraftResult(60L,1,
                new cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort.FileFact(
                        70L,1,"ref-1",new cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort.FileFactVersion(1,1,1),1L,
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),1788220800000L));
        when(application.submit(any())).thenReturn(new SubmitCutoverPlanResult(50L,"P5",5,60L,1,2,80L,0,"PENDING"));
        when(application.patchApprovedContact(any())).thenReturn(mock(PatchApprovedContactResult.class));
        when(application.revise(any())).thenReturn(new CutoverPlanCommandResult(50L,6,61L,2,0,"DRAFT",false));

        mvc.perform(get("/api/v1/pms/cutover-tasks/50/plan")).andExpect(status().isOk()).andExpect(jsonPath("$.data.taskId").value(50));
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/plan/actions/create-draft")
                .header("X-Task-Version","4").header("Idempotency-Key","i1")
                .contentType("application/json").content("{\"editMode\":\"ONLINE_TEMPLATE_STANDARD\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/pms/cutover-tasks/50/plan").header("If-Match","0")
                .header("X-Task-Version","4").header("Idempotency-Key","i2")
                .contentType("application/json").content("{\"editMode\":\"ONLINE_TEMPLATE_SIMPLE_D\",\"steps\":[]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/plan/actions/download-draft")
                .header("If-Match","1").header("Idempotency-Key","i3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.planRevisionId").value(60));
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/plan/actions/submit").header("If-Match","1")
                .header("X-Task-Version","4").header("Idempotency-Key","i4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.taskStage").value("P5"));
        mvc.perform(patch("/api/v1/pms/cutover-tasks/50/plan/support-arrangements/90")
                .header("If-Match","2").header("Idempotency-Key","i5").contentType("application/json")
                .content("{\"personName\":\"张三\",\"phone\":\"13800000000\",\"arrivalTime\":1788220800000}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/plan/actions/revise")
                .header("X-Task-Version","5").header("Idempotency-Key","i6").contentType("application/json")
                .content("{\"sourcePlanRevisionId\":60,\"reason\":\"APPROVAL_REJECTED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void returnsStableHttpErrorsForHeadersBindingAndStructuredApplicationFailures() throws Exception {
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/plan/actions/create-draft")
                .header("Idempotency-Key", "i1").contentType("application/json")
                .content("{\"editMode\":\"ONLINE_TEMPLATE_STANDARD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.reasonCode").value("HEADER_REQUIRED_OR_INVALID"))
                .andExpect(jsonPath("$.data.recoveryAction").value("FIX_REQUEST"));

        mvc.perform(post("/api/v1/pms/cutover-tasks/50/plan/actions/create-draft")
                .header("X-Task-Version", "4").header("Idempotency-Key", "i1")
                .contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.reasonCode").value("REQUEST_SCHEMA_INVALID"));

        when(application.createDraft(any())).thenThrow(new CutoverPlanApplicationException(
                CutoverPlanApplicationException.Code.VERSION_CONFLICT, "TASK_VERSION_STALE", null,
                7, null, null, "任务版本已变化"));
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/plan/actions/create-draft")
                .header("X-Task-Version", "4").header("Idempotency-Key", "i1")
                .contentType("application/json").content("{\"editMode\":\"ONLINE_TEMPLATE_STANDARD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.reasonCode").value("TASK_VERSION_STALE"))
                .andExpect(jsonPath("$.data.recoveryAction").value("REFRESH_AGGREGATE"))
                .andExpect(jsonPath("$.data.currentTaskVersion").value(7));

        doThrow(new CutoverPlanApplicationException(
                CutoverPlanApplicationException.Code.OWNER_PROVIDER_UNAVAILABLE, "PLT_PROVIDER_UNAVAILABLE", "PLT",
                null, null, null, "PLT文件Provider不可用")).when(application).createDraft(any());
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/plan/actions/create-draft")
                .header("X-Task-Version", "4").header("Idempotency-Key", "i2")
                .contentType("application/json").content("{\"editMode\":\"ONLINE_TEMPLATE_STANDARD\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.reasonCode").value("PLT_PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.ownerContext").value("PLT"))
                .andExpect(jsonPath("$.data.recoveryAction").value("RETRY_SAME_KEY"));
    }

    @Test
    void mapsPermissionAndFileOwnershipFailuresWithoutMessageInspection() throws Exception {
        when(query.detail(any(), any(), any(), any())).thenThrow(new org.springframework.security.access.AccessDeniedException(
                "denied"));
        mvc.perform(get("/api/v1/pms/cutover-tasks/50/plan"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.data.reasonCode").value("PROJECT_OR_TASK_SCOPE_DENIED"))
                .andExpect(jsonPath("$.data.recoveryAction").value("CONTACT_ADMIN"));

        when(application.submit(any())).thenThrow(new CutoverPlanApplicationException(
                CutoverPlanApplicationException.Code.FILE_FACT_STALE, "FILE_OWNERSHIP_NOT_CONFIRMED", "PLT",
                null, 3, null, "完整文件未确认归属"));
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/plan/actions/submit")
                .header("If-Match", "3").header("X-Task-Version", "4").header("Idempotency-Key", "i4"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.reasonCode").value("FILE_OWNERSHIP_NOT_CONFIRMED"))
                .andExpect(jsonPath("$.data.recoveryAction").value("FIX_REQUEST"));
    }

    @RestController
    private static final class TestController extends CutoverPlanController {
        private TestController(CutoverPlanApplicationService application, CutoverPlanQueryService query,
                               CutoverPlanRequestContext context, CutoverPlanRequestCodec codec) {
            super(application, query, context, codec);
        }
    }
}
