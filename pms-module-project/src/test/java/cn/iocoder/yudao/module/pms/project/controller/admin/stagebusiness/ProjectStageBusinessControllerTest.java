package cn.iocoder.yudao.module.pms.project.controller.admin.stagebusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.service.stagebusiness.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ProjectStageBusinessControllerTest {
    final ProjectStageApprovalCommandService approvals = mock(ProjectStageApprovalCommandService.class);
    MockMvc mvc;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(1L),new MockHttpServletRequest());
        mvc = standaloneSetup(new ProjectStageBusinessController(mock(ProjectStageBusinessQueryService.class),approvals)).build();
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }

    @Test void postsExactStageContextAndApprovalInputToTheAuthorizedCommand() throws Exception {
        var execution = new ProjectStageExecutionContext(9L,1,11L,1,41L,1,21L,31L,2,3,true);
        var form = new ProjectNodeApprovalApi.Submission(Map.of("note","private-value"),Map.of("review",List.of(12L)));
        when(approvals.start(any(),eq(1L),eq("intent"))).thenReturn(new ProjectNodeApprovalApi.Fact(
                ProjectNodeApprovalApi.Outcome.NOT_SATISFIED,"RUNNING","pi","review:1",null));
        mvc.perform(post("/api/v1/pms/projects/9/stages/PREP/business/approvals").header("Idempotency-Key","intent")
                .contentType(MediaType.APPLICATION_JSON).content(JsonUtils.toJsonString(new ProjectStageBusinessController.StartRequest(execution,form))))
                .andExpect(status().isOk());
        verify(approvals).start(new ProjectStageApprovalCommandService.Command(9L,"PREP",execution,form),1L,"intent");
        var method = ProjectStageBusinessController.class.getMethod("startApproval",Long.class,String.class,String.class,ProjectStageBusinessController.StartRequest.class);
        assertEquals("@ss.hasPermission('pms:project:update')",method.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class).value());
    }

    @Test void missingIdempotencyHeaderOrExecutionContextDoesNotInvokeCommand() throws Exception {
        mvc.perform(post("/api/v1/pms/projects/9/stages/PREP/business/approvals").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/pms/projects/9/stages/PREP/business/approvals").header("Idempotency-Key","intent")
                .contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isBadRequest());
        verifyNoInteractions(approvals);
    }
}
