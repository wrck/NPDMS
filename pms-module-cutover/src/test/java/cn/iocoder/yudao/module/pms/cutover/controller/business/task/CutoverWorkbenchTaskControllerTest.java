package cn.iocoder.yudao.module.pms.cutover.controller.business.task;

import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskListService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CutoverWorkbenchTaskControllerTest {
    @Test
    void unauthorizedProjectReturnsScopeErrorInsteadOfInternalServerError() throws Exception {
        var context = mock(CutoverTaskRequestContext.class);
        var service = mock(CutoverTaskListService.class);
        when(service.page(any(), any())).thenThrow(new CutoverTaskApplicationException(
                CutoverTaskApplicationException.Code.DATA_SCOPE_FORBIDDEN, "项目不在授权范围内"));
        var controller = new CutoverTaskController(
                mock(cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationService.class),
                mock(cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskQueryService.class), context,
                new CutoverTaskRequestCodec(cn.iocoder.yudao.framework.common.util.json.JsonUtils.getObjectMapper()),
                service, mock(cn.iocoder.yudao.module.pms.cutover.service.dashboard.CutoverDashboardQueryService.class),
                cn.iocoder.yudao.module.pms.cutover.support.CutoverTestContexts::dashboard);
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/api/v1/pms/cutover-tasks").param("listKind", "TODO").param("projectId", "31"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1_011_005_103))
                .andExpect(jsonPath("$.data.category").value("DATA_SCOPE_FORBIDDEN"))
                .andExpect(jsonPath("$.data.ownerContext").value("PROJ"));
    }
}
