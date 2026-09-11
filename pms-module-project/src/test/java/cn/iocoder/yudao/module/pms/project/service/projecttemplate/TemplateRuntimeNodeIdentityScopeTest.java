package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectWorkBindingFactMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TemplateRuntimeNodeIdentityScopeTest {

    @Test
    void nodeKeyRemainsCanonicalWhenLongCompatibilityIdentityIsProjected() {
        TemplateExecutionSnapshot.TaskContract first = new TemplateExecutionSnapshot.TaskContract();
        first.setNodeKey("task:T1");
        first.setCode("T1");
        TemplateExecutionSnapshot.TaskContract second = new TemplateExecutionSnapshot.TaskContract();
        second.setNodeKey("task:T1");

        assertEquals(first.getRuntimeNodeId(), second.getRuntimeNodeId());

        TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
        snapshot.getTasks().add(first);
        var runtimeTask = snapshot.toRuntimeContent().getTasks().getFirst();

        assertEquals("task:T1", runtimeTask.getSourceNodeKey());
        assertEquals(first.getRuntimeNodeId(), runtimeTask.getId());
    }

    @Test
    void workBindingRuntimeHasNoGlobalLookupByCompatibilityDefinitionId() {
        assertFalse(Arrays.stream(ProjectWorkBindingFactMapper.class.getDeclaredMethods())
                .anyMatch(method -> "selectTemplateRevisionFact".equals(method.getName())),
                "templateTaskDefinitionId must not be used as a cross-template global lookup key");
    }
}
