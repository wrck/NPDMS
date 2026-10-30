package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverDashboardQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.dashboard.view.CutoverDashboardKpiView;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationService;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskQueryService;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskListService;
import cn.iocoder.yudao.module.pms.cutover.support.CutoverTestContexts;
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
        var context = CutoverTestContexts.dashboard();
        when(service.statistics(context)).thenReturn(new CutoverDashboardKpiView(
                3, 2, 1, 1, LocalDateTime.of(2026, 9, 2, 10, 0)));
        CutoverTaskController controller = new CutoverTaskController(mock(CutoverTaskApplicationService.class),
                mock(CutoverTaskQueryService.class), context::task, new CutoverTaskRequestCodec(JsonUtils.getObjectMapper()),
                mock(CutoverTaskListService.class), service, () -> context);
        MockMvc mvc = standaloneSetup(controller)
                .setMessageConverters(new JacksonJsonHttpMessageConverter(
                        (JsonMapper) JsonUtils.getObjectMapper()))
                .setControllerAdvice(controller)
                .build();

        mvc.perform(get("/api/v1/pms/cutover-tasks/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todoCount").value(3))
                .andExpect(jsonPath("$.data.archivedCount").value(2))
                .andExpect(jsonPath("$.data.approvingCount").value(1))
                .andExpect(jsonPath("$.data.rejectedPendingModificationCount").value(1))
                .andExpect(jsonPath("$.data.generatedAt").isNumber());
        verify(service).statistics(context);
    }
}
