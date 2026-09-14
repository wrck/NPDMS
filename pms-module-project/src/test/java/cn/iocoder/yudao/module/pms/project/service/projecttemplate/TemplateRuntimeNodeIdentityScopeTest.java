package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class TemplateRuntimeNodeIdentityScopeTest {

    @Test
    void nodeKeyNeverBecomesAFabricatedLegacyDefinitionForeignKey() {
        TemplateExecutionSnapshot.TaskContract first = new TemplateExecutionSnapshot.TaskContract();
        first.setNodeKey("task:T1");
        first.setCode("T1");
        TemplateExecutionSnapshot.TaskContract second = new TemplateExecutionSnapshot.TaskContract();
        second.setNodeKey("task:T1");

        assertEquals(first.getNodeKey(), second.getNodeKey());

        TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
        snapshot.getTasks().add(first);
        var runtimeTask = snapshot.toRuntimeContent().getTasks().getFirst();

        assertEquals("task:T1", runtimeTask.getSourceNodeKey());
        assertNull(runtimeTask.getId());
    }

    @Test
    void workBindingRuntimeHasNoGlobalLookupByCompatibilityDefinitionId() {
        assertFalse(Arrays.stream(ProjectWorkBindingFactMapper.class.getDeclaredMethods())
                .anyMatch(method -> "selectTemplateRevisionFact".equals(method.getName())),
                "templateTaskDefinitionId must not be used as a cross-template global lookup key");
    }

    @Test
    void ignoringTheObsoleteDerivedNumberDoesNotChangeFrozenSemanticIdentity() {
        var snapshot=new TemplateExecutionSnapshot();
        var task=new TemplateExecutionSnapshot.TaskContract(); task.setNodeKey("task:T1"); task.setCode("T1");
        snapshot.getTasks().add(task);
        var json=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseTree(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(snapshot));
        ((tools.jackson.databind.node.ObjectNode) json.path("tasks").get(0)).put("runtimeNodeId",123456789L);
        var loaded=cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(json.toString(),TemplateExecutionSnapshot.class);
        assertEquals(TemplateExecutionSnapshotHasher.hash(snapshot),TemplateExecutionSnapshotHasher.hash(loaded));
        assertNull(loaded.toRuntimeContent().getTasks().getFirst().getId());
    }
}
