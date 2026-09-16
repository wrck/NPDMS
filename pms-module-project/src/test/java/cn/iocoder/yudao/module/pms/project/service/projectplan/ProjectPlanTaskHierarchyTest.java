package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectPlanTaskHierarchyTest {
    final TemplateExecutionSnapshot before = new TemplateExecutionSnapshot();
    final List<ProjectTaskInstanceDO> tasks = new ArrayList<>();
    final List<ProjectNodeExecutionDO> rounds = new ArrayList<>();

    ProjectPlanTaskHierarchyTest() {
        for (int i=1;i<=3;i++) {
            var node=new TemplateExecutionSnapshot.TaskContract(); node.setNodeKey("node:"+i); node.setCode("T"+i);
            before.getTasks().add(node);
            var task=new ProjectTaskInstanceDO(); task.setId((long)i); task.setCode(node.getCode()); tasks.add(task);
            var round=new ProjectNodeExecutionDO(); round.setNodeKind("TASK"); round.setNodeKey(node.getNodeKey());
            round.setNodeInstanceId(task.getId()); rounds.add(round);
        }
    }

    @Test void ruleOnlyChangeKeepsRuntimeMoveWithoutChangingEitherFrozenDesign() {
        tasks.getFirst().setParentTaskId(2L);
        var after=copy(); after.setClosureRuleKey("new-rule");
        var original=JsonUtils.toJsonString(after);
        var result=ProjectPlanTaskHierarchy.resolve(before,after,rounds,tasks);
        assertTrue(result.issues().isEmpty()); assertEquals("T2",result.content().getTasks().getFirst().getParentTaskCode());
        assertNull(before.getTasks().getFirst().getParentTaskCode()); assertEquals(original,JsonUtils.toJsonString(after));
        assertEquals(2L,tasks.getFirst().getParentTaskId());
    }

    @Test void explicitDraftMoveWinsOverRuntimeMove() {
        tasks.getFirst().setParentTaskId(2L);
        var after=copy(); after.getTasks().getFirst().setParentTaskCode("T3");
        var result=ProjectPlanTaskHierarchy.resolve(before,after,rounds,tasks);
        assertTrue(result.issues().isEmpty()); assertEquals("T3",result.content().getTasks().getFirst().getParentTaskCode());
    }

    @Test void parentRenamePreservesRuntimeRootInsteadOfReapplyingOldDesignParent() {
        before.getTasks().getFirst().setParentTaskCode("T2");
        var after=copy(); after.getTasks().get(1).setCode("RENAMED"); after.getTasks().getFirst().setParentTaskCode("RENAMED");
        var result=ProjectPlanTaskHierarchy.resolve(before,after,rounds,tasks);
        assertTrue(result.issues().isEmpty()); assertNull(result.content().getTasks().getFirst().getParentTaskCode());
    }

    @Test void runtimeParentRenameUsesCurrentNodeIdentityNotOriginalCode() {
        tasks.getFirst().setParentTaskId(2L);
        var after=copy(); after.getTasks().get(1).setCode("RENAMED");
        var result=ProjectPlanTaskHierarchy.resolve(before,after,rounds,tasks);
        assertTrue(result.issues().isEmpty()); assertEquals("RENAMED",result.content().getTasks().getFirst().getParentTaskCode());
    }

    @Test void removingRuntimeParentNeedsAnExplicitReplacement() {
        tasks.getFirst().setParentTaskId(2L);
        var after=copy(); after.getTasks().remove(1);
        var result=ProjectPlanTaskHierarchy.resolve(before,after,rounds,tasks);
        assertEquals("TASK_HIERARCHY_PARENT_REMOVED",result.issues().getFirst().code());
        after.getTasks().getFirst().setParentTaskCode("T3");
        assertTrue(ProjectPlanTaskHierarchy.resolve(before,after,rounds,tasks).issues().isEmpty());
    }

    @Test void individuallyValidRuntimeAndDraftTreesCannotMergeIntoACycle() {
        tasks.getFirst().setParentTaskId(2L);
        var after=copy(); after.getTasks().get(1).setParentTaskCode("T1");
        var result=ProjectPlanTaskHierarchy.resolve(before,after,rounds,tasks);
        assertEquals("TASK_HIERARCHY_CONFLICT",result.issues().getFirst().code());
    }

    private TemplateExecutionSnapshot copy() {
        return JsonUtils.parseObject(JsonUtils.toJsonString(before),TemplateExecutionSnapshot.class);
    }
}
