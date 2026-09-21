package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.constructionplan.ConstructionPlanDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.AssociationContext;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConstructionPlanAssociationTest {
    final ConstructionPlanMapper plans = mock(ConstructionPlanMapper.class);
    final ConstructionPlanRevisionMapper revisions = mock(ConstructionPlanRevisionMapper.class);
    final ConstructionPlanTaskBusinessObjectProvider provider = new ConstructionPlanTaskBusinessObjectProvider(plans, revisions,
            mock(ProjectScopeApi.class), mock(PermissionApi.class), mock(ProjectNodeExecutionApi.class));
    @BeforeEach void setUp() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void designerProjectKeyAndLegacyKeyResolveTheSameActualProjectPlan() {
        var plan = new ConstructionPlanDO(); plan.setId(30L); plan.setProjectId(80L); plan.setTenantId(7L); plan.setVersion(1);
        when(plans.selectByProjectId(7L, 80L)).thenReturn(plan);
        var modern = new AssociationContext(7L, 80L, "PROJECT_CONSTRUCTION_PLAN", "{}");
        var legacy = new AssociationContext(7L, 80L, "CONSTRUCTION_PLAN", "{}");
        assertEquals(provider.associationCandidates(legacy, null, 100), provider.associationCandidates(modern, null, 100));
        assertEquals("30", provider.associationCandidates(modern, null, 100).getFirst().objectId());
        assertTrue(provider.associationCandidates(modern, "30", 100).isEmpty());
    }
    @Test void unknownKeysAndForeignTenantCannotExpandAssociationScope() {
        assertThrows(RuntimeException.class, () -> provider.associationCandidates(new AssociationContext(8L, 80L, "PROJECT_CONSTRUCTION_PLAN", "{}"), null, 100));
        assertThrows(RuntimeException.class, () -> provider.associationCandidates(new AssociationContext(7L, 80L, "OTHER", "{}"), null, 100));
        verifyNoInteractions(plans, revisions);
    }
}
