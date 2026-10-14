package cn.iocoder.yudao.module.pms.cutover.controller.business.closure;

import cn.iocoder.yudao.module.pms.cutover.service.closure.context.CutoverClosureContext;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.closure.command.SaveCutoverClosureCommand.ClosureContent;
import cn.iocoder.yudao.module.pms.cutover.service.closure.result.CutoverClosureCommandResult;
import cn.iocoder.yudao.module.pms.cutover.service.closure.view.CutoverClosureView;
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

class CutoverClosureControllerContractTest {
    private CutoverClosureApplicationService application;
    private CutoverClosureQueryService query;
    private cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureExecutionLinkService links;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        application = mock(CutoverClosureApplicationService.class);
        query = mock(CutoverClosureQueryService.class);
        links = mock(cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureExecutionLinkService.class);
        CutoverClosureRequestContext context = () -> new CutoverClosureContext(
                1L, 8L, "corr-closure-1", true, true, true);
        CutoverClosureController controller = new CutoverClosureController(application, query, context,
                new CutoverClosureRequestCodec(), links);
        when(query.detail(any(), any())).thenReturn(view());
        mvc = standaloneSetup(controller).setControllerAdvice(controller).build();
    }

    @Test
    void registersProductionControllerAndServesFivePositiveRoutes() throws Exception {
        org.assertj.core.api.Assertions.assertThat(AnnotatedElementUtils.hasAnnotation(
                CutoverClosureController.class, RestController.class)).isTrue();
        org.assertj.core.api.Assertions.assertThat(AnnotatedElementUtils.hasAnnotation(
                CutoverClosureController.class, Component.class)).isTrue();
        when(application.save(any())).thenReturn(result(2));
        when(application.linkManualResult(any())).thenReturn(result(4));
        when(application.submit(any())).thenReturn(new CutoverClosureCommandResult(50L, 8L, 70L, 5L,
                "SUBMITTED", false));

        mvc.perform(get("/api/v1/pms/cutover-tasks/50/closure"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.taskId").value(50))
                .andExpect(jsonPath("$.data.closureId").value(70));
        mvc.perform(put("/api/v1/pms/cutover-tasks/50/closure")
                .header("If-Match", "1").header("X-Task-Version", "7").header("Idempotency-Key", "save-1")
                .contentType("application/json").content(content()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.closureStatus").value("DRAFT"));
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/closure/actions/link-collection-execution")
                .header("If-Match", "1").header("X-Task-Version", "7").header("Idempotency-Key", "collect-1")
                .contentType("application/json").content("""
                        {"entry":"cutover-post-collection","executionId":21}
                        """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/closure/actions/link-manual-result")
                .header("If-Match", "1").header("X-Task-Version", "7").header("Idempotency-Key", "manual-1")
                .contentType("application/json").content(manual()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/pms/cutover-tasks/50/closure/actions/submit")
                .header("If-Match", "1").header("X-Task-Version", "7").header("Idempotency-Key", "submit-1")
                .contentType("application/json").content("{\"finalResult\":\"SUCCESS\"}"))
                .andExpect(status().isOk());

        verify(application).save(any());
        verify(links).link(any());
        verify(application).linkManualResult(any());
        verify(application).submit(any());
    }

    private static CutoverClosureView view() {
        return new CutoverClosureView(50L, "P6", "CLOSURE_IN_PROGRESS", 7L, 70L, 1L, "DRAFT",
                80L, 2L, 90L, 1, 3L, new ClosureContent(true, null, true, null, true, null,
                false, null, null, "无", null, List.of()), List.of(), null, null, null, null,
                List.of("SAVE_CLOSURE", "REQUEST_COLLECTION", "SUBMIT_CLOSURE"));
    }

    private static CutoverClosureCommandResult result(long version) {
        return new CutoverClosureCommandResult(50L, 7L, 70L, version, "DRAFT", false);
    }

    private static String content() {
        return """
                {"preCheckNormal":true,"preCheckDetail":null,"executionNormal":true,"executionDetail":null,
                 "testNormal":true,"testDetail":null,"rollbackOccurred":false,"rollbackSuccessful":null,
                 "rollbackReason":null,"legacyItems":"无","finalResult":null,"attachments":[]}
                """;
    }

    private static String manual() {
        return """
                {"originalFailedCollectionTaskId":"collect-failed-1","deviceId":11,"collectionStage":"TEST",
                 "file":{"purposeCode":"MANUAL_COLLECTION_RESULT","artifactId":71,"versionNo":1,
                 "referenceKey":"manual-ref","fileFactVersion":{"artifactVersion":1,"referenceVersion":1,
                 "availabilityVersion":1},"scopeVersion":1,
                 "sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}}
                """;
    }

}
