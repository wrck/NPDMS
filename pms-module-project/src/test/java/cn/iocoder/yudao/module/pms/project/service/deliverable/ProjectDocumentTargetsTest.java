package cn.iocoder.yudao.module.pms.project.service.deliverable;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDocumentTargetsTest {
    @Test void targetsComeOnlyFromMatchingFrozenConfigurationAndClosedProjectsAreReadOnly() {
        TenantContextHolder.setTenantId(7L);
        try {
            var projects = mock(ProjectMasterMapper.class);
            var plans = mock(ProjectPlanVersionMapper.class);
            var project = new ProjectMasterDO();
            project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE"); project.setActivePlanVersionId(20L);
            var plan = new ProjectPlanVersionDO();
            plan.setId(20L); plan.setTenantId(7L); plan.setProjectId(9L); plan.setStatus("EFFECTIVE");
            plan.setExecutionSnapshot("""
                {"executionSchemaVersion":2,"deliverables":[
                 {"code":"D1","configuration":{"automaticSources":["SOL.REQUIREMENT_DOCUMENT"]}},
                 {"code":"D2","configuration":{"automaticSources":["ACC.FINAL_REPORT"]}},
                 {"code":"LEGACY"}]}
                """);
            when(projects.selectById(9L)).thenReturn(project);
            when(plans.selectById(20L)).thenReturn(plan);
            var service = new ProjectDeliverableRuleService(projects, plans, null, null, null, null);
            assertEquals(Set.of("D1"), service.documentTargets(9L, "SOL.REQUIREMENT_DOCUMENT"));
            assertTrue(service.documentTargets(9L, "ACC.SATISFACTION_DOCUMENT").isEmpty());
            plan.setTenantId(8L);
            assertThrows(IllegalStateException.class, () -> service.documentTargets(9L, "SOL.REQUIREMENT_DOCUMENT"));
            project.setLifecycleStatus("NORMAL_CLOSED");
            assertTrue(service.documentTargets(9L, "SOL.REQUIREMENT_DOCUMENT").isEmpty());
        } finally { TenantContextHolder.clear(); }
    }
}
