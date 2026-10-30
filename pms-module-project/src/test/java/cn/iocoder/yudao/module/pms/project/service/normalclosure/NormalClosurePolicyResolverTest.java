package cn.iocoder.yudao.module.pms.project.service.normalclosure;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NormalClosurePolicyResolverTest {
    private final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    private final NormalClosurePolicyResolver resolver = new NormalClosurePolicyResolver(plans);

    @Test void legacyWithoutPlanRetainsCreationSnapshot() {
        var project = project();
        assertEquals("{\"reviewerUserId\":2}", resolver.resolve(project).policyJson());
        verifyNoInteractions(plans);
    }

    @Test void effectiveFrozenPlanSupersedesCreationWithoutOverwritingHistory() {
        var project = project();
        project.setActivePlanVersionId(20L);
        var plan = plan();
        when(plans.selectById(20L)).thenReturn(plan);
        var result = resolver.resolve(project);
        assertEquals("{\"reviewerUserId\":3}", result.policyJson());
        assertEquals(20L, result.planVersionId());
        assertEquals(2, result.revisionNo());
        assertEquals("{\"reviewerUserId\":2}", project.getClosurePolicySnapshot());
        verify(plans).selectById(20L);
        verifyNoMoreInteractions(plans);
    }

    @Test void absentCurrentPolicyDoesNotFallBackToOldReviewer() {
        var project = project(); project.setActivePlanVersionId(20L);
        var plan = plan(); plan.setExecutionSnapshot("{\"executionSchemaVersion\":2}");
        when(plans.selectById(20L)).thenReturn(plan);
        assertNull(resolver.resolve(project).policyJson());
    }

    @Test void invalidPlanIdentityOrStatusFailsClosed() {
        var project = project(); project.setActivePlanVersionId(20L);
        assertThrows(RuntimeException.class, () -> resolver.resolve(project));
        var plan = plan(); when(plans.selectById(20L)).thenReturn(plan);
        plan.setProjectId(99L);
        assertThrows(RuntimeException.class, () -> resolver.resolve(project));
        plan.setProjectId(9L); plan.setTenantId(99L);
        assertThrows(RuntimeException.class, () -> resolver.resolve(project));
        plan.setTenantId(7L); plan.setStatus("DRAFT");
        assertThrows(RuntimeException.class, () -> resolver.resolve(project));
        plan.setStatus("EFFECTIVE"); plan.setExecutionSnapshot("{}");
        assertThrows(RuntimeException.class, () -> resolver.resolve(project));
    }

    private ProjectMasterDO project() {
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L);
        project.setClosurePolicySnapshot("{\"reviewerUserId\":2}"); return project;
    }
    private ProjectPlanVersionDO plan() {
        var plan = new ProjectPlanVersionDO(); plan.setId(20L); plan.setProjectId(9L); plan.setTenantId(7L);
        plan.setStatus("EFFECTIVE"); plan.setRevisionNo(2);
        plan.setExecutionSnapshot("{\"executionSchemaVersion\":2,\"closurePolicy\":{\"reviewerUserId\":3}}");
        return plan;
    }
}
