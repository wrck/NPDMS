package cn.iocoder.yudao.module.pms.cutover.controller.admin.taskv2;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverTaskApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverWorkbenchTaskQueryService;
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
        var service = mock(CutoverWorkbenchTaskQueryService.class);
        when(service.page(any(), any())).thenThrow(new CutoverTaskApplicationException(
                CutoverTaskApplicationException.Code.DATA_SCOPE_FORBIDDEN, "项目不在授权范围内"));
        MockMvcBuilders.standaloneSetup(new CutoverWorkbenchTaskController(context, service)).build()
                .perform(get("/api/v1/pms/cutover-workbench/tasks").param("listKind", "TODO").param("projectId", "31"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(1_011_005_103))
                .andExpect(jsonPath("$.data.category").value("DATA_SCOPE_FORBIDDEN"))
                .andExpect(jsonPath("$.data.ownerContext").value("PROJ"));
    }
}
