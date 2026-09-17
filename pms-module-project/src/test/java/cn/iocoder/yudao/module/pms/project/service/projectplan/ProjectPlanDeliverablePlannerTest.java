package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectPlanDeliverablePlannerTest {
    final ProjectPlanDeliverablePlanner planner = new ProjectPlanDeliverablePlanner();
    final TemplateExecutionSnapshot before = new TemplateExecutionSnapshot();
    final DeliverableView actual = new DeliverableView(10L,9L,"D1","Owner current name","PREP","T1",true,500L,"SUBMITTED",7);

    ProjectPlanDeliverablePlannerTest() {
        var node = new TemplateExecutionSnapshot.DeliverableContract(); node.setNodeKey("deliverable:one");
        node.setCode("D1"); node.setName("old name"); node.setStageCode("PREP"); node.setTaskCode("T1"); node.setRequired(true);
        before.getDeliverables().add(node);
    }
    @Test void unchangedDefinitionPreservesOwnerEditsWithoutIssuingWrites() {
        var result = plan(copy(),Set.of());
        assertTrue(result.issues().isEmpty()); assertTrue(result.changes().isEmpty()); assertTrue(result.writes().isEmpty());
    }
    @Test void codeRenameKeepsInstanceVersionAndSourceIdentity() {
        var after = copy(); after.getDeliverables().getFirst().setCode("RENAMED");
        var result = plan(after,Set.of());
        assertTrue(result.issues().isEmpty());
        var write = result.writes().getFirst(); assertEquals(10L,write.id()); assertEquals(7,write.expectedVersion());
        assertEquals("RENAMED",write.definition().deliverableCode()); assertEquals("Owner current name",write.definition().name());
        assertEquals(500L,write.definition().sourceDefinitionId()); assertEquals("SUBMITTED",actual.status());
    }
    @Test void explicitMetadataChangesAreCarriedToOwnerWithoutBusinessResultFields() {
        var after = copy(); var node = after.getDeliverables().getFirst();
        node.setName("new name"); node.setRequired(false); node.setStageCode("CUSTOM"); node.setTaskCode(null);
        var desired = plan(after,Set.of()).writes().getFirst().definition();
        assertEquals("new name",desired.name()); assertFalse(desired.required()); assertEquals("CUSTOM",desired.stageCode()); assertNull(desired.taskCode());
    }
    @Test void removalUsesOwnerHistoryEligibilityNotJustPendingStatus() {
        var after = copy(); after.setDeliverables(List.of());
        var pending = new DeliverableView(10L,9L,"D1","name","PREP","T1",true,500L,"PENDING",8);
        var blocked = planner.plan(9L,before,after,new DeliverablePlanState(List.of(pending),Set.of()));
        assertEquals("DELIVERABLE_HANDLING_HISTORY_PROTECTED",blocked.issues().getFirst().code());
        var allowed = planner.plan(9L,before,after,new DeliverablePlanState(List.of(pending),Set.of(10L)));
        assertTrue(allowed.issues().isEmpty()); assertNull(allowed.writes().getFirst().definition());
        assertEquals(8,allowed.changes().getFirst().expectedVersion());
    }
    @Test void replacingAnUnhandledNodeCreatesAnIndependentInstanceEvenWhenCodeIsReused() {
        var after = copy(); after.getDeliverables().getFirst().setNodeKey("deliverable:replacement");
        var result = plan(after,Set.of(10L));
        assertTrue(result.issues().isEmpty()); assertEquals(List.of("REMOVE","ADD"),result.changes().stream().map(ProjectPlanDeliverablePlanner.Change::action).toList());
        assertNull(result.writes().getFirst().definition()); assertNull(result.writes().getLast().id());
        assertNull(result.writes().getLast().definition().sourceDefinitionId());
    }
    @Test void missingRuntimeAndForeignProjectCannotBeResolvedFromSourceAssets() {
        var missing = planner.plan(9L,before,copy(),new DeliverablePlanState(List.of(),Set.of()));
        assertEquals("DELIVERABLE_RUNTIME_MISSING",missing.issues().getFirst().code());
        var foreign = planner.plan(99L,before,copy(),new DeliverablePlanState(List.of(actual),Set.of()));
        assertEquals("DELIVERABLE_RUNTIME_IDENTITY_CONFLICT",foreign.issues().getFirst().code()); assertTrue(foreign.writes().isEmpty());
    }
    @Test void independentOwnerDeliverablesRemainUntouchedButCannotHaveTheirCodeOverwritten() {
        var outside = new DeliverableView(11L,9L,"OUTSIDE","independent","PREP",null,false,null,"PENDING",0);
        var state = new DeliverablePlanState(List.of(actual,outside),Set.of(11L));
        assertTrue(planner.plan(9L,before,copy(),state).issues().isEmpty());
        var after = copy(); after.getDeliverables().getFirst().setCode("OUTSIDE");
        assertEquals("DELIVERABLE_CODE_OCCUPIED",planner.plan(9L,before,after,state).issues().getFirst().code());
    }
    private ProjectPlanDeliverablePlanner.Plan plan(TemplateExecutionSnapshot after,Set<Long> retirable) {
        return planner.plan(9L,before,after,new DeliverablePlanState(List.of(actual),retirable));
    }
    private TemplateExecutionSnapshot copy() { return JsonUtils.parseObject(JsonUtils.toJsonString(before),TemplateExecutionSnapshot.class); }
}
