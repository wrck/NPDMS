package cn.iocoder.yudao.module.pms.cutover.controller.business.plan;

import cn.iocoder.yudao.module.pms.cutover.controller.business.task.CutoverTaskRequestContext;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverSupportPersonPolicy;
import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class CutoverSupportPersonChoicesControllerTest {
    final CutoverTaskMapper tasks=mock(CutoverTaskMapper.class);
    final CutoverProjectScopePort scopes=mock(CutoverProjectScopePort.class);
    final CutoverSupportPersonPolicy people=mock(CutoverSupportPersonPolicy.class);
    final CutoverTaskRequestContext context=()->new CutoverTaskContext(1L,2L,"choices",false,false,false,false,false);
    MockMvc mvc;
    CutoverTaskDO task;
    @BeforeEach void setup(){
        var controller=new CutoverSupportPersonChoicesController(context,tasks,scopes,people);
        mvc=standaloneSetup(controller).build();
        task=new CutoverTaskDO();task.setTenantId(1L);task.setProjectId(7L);task.setTaskOrigin("NEW_PLATFORM");
        when(tasks.selectById(50L)).thenReturn(task);
        when(scopes.inspect(2L,7L,"ACTION_VIEW")).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(7L,1L,true));
    }
    @Test void deniesForeignTenantAndEmptyProjectScopeBeforeReadingCandidates() throws Exception {
        task.setTenantId(9L);
        mvc.perform(get("/api/v1/pms/cutover-tasks/50/support-person-choices").param("roleCode","CUSTOMER"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data.reasonCode").value("PROJECT_OR_TASK_SCOPE_DENIED"));
        verifyNoInteractions(scopes,people);
        task.setTenantId(1L);
        when(scopes.inspect(any(),any(),any())).thenReturn(new CutoverProjectScopePort.ProjectScopeFact(7L,1L,false));
        mvc.perform(get("/api/v1/pms/cutover-tasks/50/support-person-choices").param("roleCode","CUSTOMER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(people);
    }
    @Test void rejectsUnknownRoleAndPreservesLargeReferenceIds() throws Exception {
        mvc.perform(get("/api/v1/pms/cutover-tasks/50/support-person-choices").param("roleCode","ADMIN"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(people);
        when(people.choices(any())).thenReturn(List.of(new CutoverSupportPersonPolicy.Choice(
                new CutoverSupportPersonPolicy.Reference("PROJECT_MEMBER",2111084153208291328L,3L),"项目成员",null,null,null)));
        mvc.perform(get("/api/v1/pms/cutover-tasks/50/support-person-choices").param("roleCode","CUSTOMER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].reference.id").value("2111084153208291328"));
    }
    @Test void returnsSystemIdentityFieldsWithoutChangingReferenceOrName() throws Exception {
        when(people.choices(any())).thenReturn(List.of(new CutoverSupportPersonPolicy.Choice(
                new CutoverSupportPersonPolicy.Reference("SYSTEM_USER",2111084153208291328L,3L),
                "工程师", "13800000001", "engineer.test", "engineer@example.test")));
        mvc.perform(get("/api/v1/pms/cutover-tasks/50/support-person-choices").param("roleCode","DP_SECOND_LINE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].reference.id").value("2111084153208291328"))
                .andExpect(jsonPath("$.data[0].name").value("工程师"))
                .andExpect(jsonPath("$.data[0].username").value("engineer.test"))
                .andExpect(jsonPath("$.data[0].email").value("engineer@example.test"));
    }
}
