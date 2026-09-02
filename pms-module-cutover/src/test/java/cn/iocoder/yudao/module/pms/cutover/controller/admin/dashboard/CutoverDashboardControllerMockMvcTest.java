package cn.iocoder.yudao.module.pms.cutover.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverDashboardQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.model.CutoverDashboardActionFacts.PermissionFacts;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.view.CutoverDashboardKpiView;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CutoverDashboardControllerMockMvcTest {

    @Test
    void returnsPositiveKpiProjectionWithEpochGeneratedAt() throws Exception {
        CutoverDashboardQueryService service = mock(CutoverDashboardQueryService.class);
        PermissionFacts permissions = new PermissionFacts(true, true, true, true, true, true, true,
                true, true, true, true, true, true, true);
        CutoverDashboardRequestContext context = () ->
                new CutoverDashboardRequestContext.TrustedContext(1L, 9L, permissions);
        when(service.inspect(1L, 9L, permissions)).thenReturn(new CutoverDashboardKpiView(
                3, 2, 1, 1, LocalDateTime.of(2026, 9, 2, 10, 0)));
        CutoverDashboardController controller = new TestController(service, context);
        MockMvc mvc = standaloneSetup(controller)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(
                        (JsonMapper) JsonUtils.getObjectMapper()))
                .setControllerAdvice(controller)
                .build();

        mvc.perform(get("/api/v1/pms/cutover-dashboard/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoCount").value(3))
                .andExpect(jsonPath("$.data.archivedCount").value(2))
                .andExpect(jsonPath("$.data.approvingCount").value(1))
                .andExpect(jsonPath("$.data.rejectedPendingModificationCount").value(1))
                .andExpect(jsonPath("$.data.generatedAt").isNumber());
        verify(service).inspect(1L, 9L, permissions);
    }

    @RestController
    private static final class TestController extends CutoverDashboardController {
        private TestController(CutoverDashboardQueryService service, CutoverDashboardRequestContext context) {
            super(service, context);
        }
    }
}
