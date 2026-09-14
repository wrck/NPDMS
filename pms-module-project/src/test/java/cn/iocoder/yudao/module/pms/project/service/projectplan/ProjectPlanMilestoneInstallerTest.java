package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMilestoneInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMilestoneInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectPlanMilestoneInstallerTest {
    final ProjectPlanProjectionMapper projections = mock(ProjectPlanProjectionMapper.class);
    final ProjectMilestoneInstanceMapper rows = mock(ProjectMilestoneInstanceMapper.class);
    final ProjectPlanMilestoneInstaller installer = new ProjectPlanMilestoneInstaller(projections, rows);
    final ProjectPlanScopeQuery scope = new ProjectPlanScopeQuery(1L,9L);
    final TemplateExecutionSnapshot before = new TemplateExecutionSnapshot();
    final ProjectMilestoneInstanceDO actual = new ProjectMilestoneInstanceDO();

    ProjectPlanMilestoneInstallerTest() {
        var node = new TemplateExecutionSnapshot.MilestoneContract(); node.setNodeKey("milestone:one"); node.setCode("M1");
        node.setName("baseline name"); node.setStageCode("PREP"); node.setCriteria("baseline criteria"); node.setTiming("before delivery");
        before.getMilestones().add(node);
        actual.setId(10L); actual.setTenantId(1L); actual.setProjectId(9L); actual.setMilestoneCode("M1");
        actual.setName("runtime name"); actual.setStageCode("PREP"); actual.setCriteria("baseline criteria"); actual.setTiming("before delivery");
        actual.setStatus("ACHIEVED"); actual.setVersion(3); actual.setSourceDefinitionId(888L);
        when(projections.selectMilestonesForUpdate(scope)).thenReturn(List.of(actual));
        when(projections.milestoneCodeForRename(any())).thenReturn(1); when(projections.updateMilestoneDefinition(any())).thenReturn(1);
        when(projections.retirePendingMilestone(any())).thenReturn(1); when(rows.insert(any(ProjectMilestoneInstanceDO.class))).thenReturn(1);
    }
    @Test void unchangedDesignKeepsRuntimeMetadataAndDoesNotIssueWrites() {
        var plan = installer.inspect(scope,before,copy()); installer.install(scope,plan,7L);
        assertTrue(plan.issues().isEmpty()); assertTrue(plan.changes().isEmpty());
        verifyNoInteractions(rows); verify(projections,never()).updateMilestoneDefinition(any());
    }
    @Test void renamingAndDefinitionEditsKeepTheAchievedInstanceAndOriginalInputEvidence() {
        var after = copy(); var node = after.getMilestones().getFirst(); node.setCode("CUSTOM"); node.setCriteria("updated criteria"); node.setTiming(null);
        String original = JsonUtils.toJsonString(actual), frozen = JsonUtils.toJsonString(before);
        var plan = installer.inspect(scope,before,after); installer.install(scope,plan,7L);
        var order = inOrder(projections);
        order.verify(projections).milestoneCodeForRename(argThat(q -> q.id()==10L && q.expectedVersion()==3));
        order.verify(projections).updateMilestoneDefinition(argThat(q -> q.id()==10L && q.expectedVersion()==3
                && q.definition().getMilestoneCode().equals("CUSTOM") && q.definition().getName().equals("runtime name")
                && q.definition().getCriteria().equals("updated criteria") && q.definition().getTiming()==null));
        assertEquals(original,JsonUtils.toJsonString(actual)); assertEquals(frozen,JsonUtils.toJsonString(before));
        verify(projections,never()).retirePendingMilestone(any()); verifyNoInteractions(rows);
    }
    @Test void achievedRemovalIsVisibleInPreviewAndRejectedBeforeWrites() {
        var after = copy(); after.setMilestones(List.of()); var plan = installer.inspect(scope,before,after);
        assertEquals("ACHIEVED_MILESTONE_DELETE_FORBIDDEN",plan.issues().getFirst().code());
        assertThrows(RuntimeException.class,() -> installer.install(scope,plan,7L));
        verify(projections,never()).retirePendingMilestone(any()); verifyNoInteractions(rows);
    }
    @Test void unachievedReplacementRetiresBeforeCreatingANewIdentityWithTheSameCode() {
        actual.setStatus("PENDING"); var after = copy(); after.getMilestones().getFirst().setNodeKey("milestone:replacement");
        var plan = installer.inspect(scope,before,after); installer.install(scope,plan,7L);
        assertTrue(plan.issues().isEmpty());
        var order = inOrder(projections,rows);
        order.verify(projections).retirePendingMilestone(argThat(q -> q.id()==10L));
        order.verify(rows).insert(argThat((ProjectMilestoneInstanceDO row) -> row.getId()==null && row.getProjectId()==9L
                && row.getTenantId()==1L && row.getVersion()==0 && "PENDING".equals(row.getStatus()) && row.getSourceDefinitionId()==null));
        assertEquals(10L,actual.getId()); assertEquals(888L,actual.getSourceDefinitionId());
    }
    @Test void staleWriteStopsBeforeCreatingAReplacement() {
        actual.setStatus("PENDING"); var after=copy(); after.getMilestones().getFirst().setNodeKey("milestone:replacement");
        var plan=installer.inspect(scope,before,after); when(projections.retirePendingMilestone(any())).thenReturn(0);
        assertThrows(RuntimeException.class,() -> installer.install(scope,plan,7L)); verifyNoInteractions(rows);
    }
    @Test void missingOrForeignRuntimeCannotBeRebuiltFromAssetProvenance() {
        assertEquals("MILESTONE_RUNTIME_MISSING",installer.plan(scope,before,copy(),List.of()).issues().getFirst().code());
        actual.setTenantId(2L);
        assertEquals("MILESTONE_RUNTIME_CONFLICT",installer.plan(scope,before,copy(),List.of(actual)).issues().getFirst().code());
    }
    private TemplateExecutionSnapshot copy() { return JsonUtils.parseObject(JsonUtils.toJsonString(before),TemplateExecutionSnapshot.class); }
}
