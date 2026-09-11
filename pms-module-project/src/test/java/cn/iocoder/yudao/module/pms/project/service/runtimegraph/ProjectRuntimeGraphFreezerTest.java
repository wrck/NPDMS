package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectStageExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectRuntimeGraphFreezerTest {

    @Test
    void rejectsLegacyContentWithoutPersistedExecutionSnapshot() {
        var graphMapper = mock(ProjectRuntimeGraphMapper.class);
        var contractMapper = mock(ProjectStageExecutionContractMapper.class);
        var legacy = new TemplateDefinitionContent();

        var error = assertThrows(IllegalArgumentException.class,
                () -> new ProjectRuntimeGraphFreezer(graphMapper, contractMapper).validate(legacy));

        assertEquals("EXECUTION_SNAPSHOT_V2_REQUIRED", error.getMessage());
        verifyNoInteractions(graphMapper, contractMapper);
    }

    @Test
    void effectiveFromCannotRoundIntoFutureWhenStoredAsDatetimeZero() {
        var graphMapper = mock(ProjectRuntimeGraphMapper.class);
        var contractMapper = mock(ProjectStageExecutionContractMapper.class);
        when(contractMapper.insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO.class)))
                .thenReturn(1);
        when(graphMapper.insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageTransitionDO.class)))
                .thenReturn(1);
        var snapshot = snapshot();
        var now = LocalDateTime.of(2026, 9, 10, 12, 0, 0, 900_000_000);

        new ProjectRuntimeGraphFreezer(graphMapper, contractMapper).freeze(7L, 9L, 10L, snapshot,
                stages(), now);

        var contracts = org.mockito.ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO.class);
        verify(contractMapper, times(2)).insert(contracts.capture());
        contracts.getAllValues().forEach(contract -> assertEquals(now.withNano(0), contract.getEffectiveFrom()));
    }

    @Test
    void acceptsCompiledPermissionWithoutLegacyPolicyRevision() {
        var graphMapper = mock(ProjectRuntimeGraphMapper.class);
        var contractMapper = mock(ProjectStageExecutionContractMapper.class);

        assertDoesNotThrow(() -> new ProjectRuntimeGraphFreezer(graphMapper, contractMapper).validate(snapshot()));
        verifyNoInteractions(graphMapper, contractMapper);
    }

    @Test
    void rejectsLegacyAcceptanceTaskCodeWithoutDefinitionProvenance() {
        var graphMapper = mock(ProjectRuntimeGraphMapper.class);
        var contractMapper = mock(ProjectStageExecutionContractMapper.class);
        var snapshot = snapshot();
        snapshot.getTasks().getFirst().setCode("T-FINAL-ACCEPT");

        var error = assertThrows(IllegalArgumentException.class,
                () -> new ProjectRuntimeGraphFreezer(graphMapper, contractMapper).validate(snapshot));

        assertTrue(error.getMessage().contains("旧验收任务保留码"));
        verifyNoInteractions(graphMapper, contractMapper);
    }

    @Test
    void freezesStableKeysAndSnapshotsWithoutLegacyRevisionIds() {
        var graphMapper = mock(ProjectRuntimeGraphMapper.class);
        var contractMapper = mock(ProjectStageExecutionContractMapper.class);
        when(contractMapper.insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO.class)))
                .thenReturn(1);
        when(graphMapper.insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageTransitionDO.class)))
                .thenReturn(1);
        var snapshot = snapshot();

        new ProjectRuntimeGraphFreezer(graphMapper, contractMapper).freeze(7L, 9L, 10L, snapshot,
                stages(), LocalDateTime.of(2026, 9, 11, 10, 0));

        var contracts = org.mockito.ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO.class);
        verify(contractMapper, times(2)).insert(contracts.capture());
        var s0 = contracts.getAllValues().getFirst();
        assertEquals("stage:S0", s0.getSourceNodeKey());
        assertNull(s0.getDefinitionRevisionId());
        assertNull(s0.getWorkBindingRevisionId());
        assertNull(s0.getPermissionPolicyRevisionId());
        assertNull(s0.getCompletionRuleRevisionId());
        assertNotNull(s0.getBindingSnapshot());
        assertNotNull(s0.getPermissionSnapshot());
        assertNotNull(s0.getCompletionRuleSnapshot());
        assertTrue(s0.getDefinitionSnapshot().contains("executionSchemaVersion"));

        var edges = org.mockito.ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageTransitionDO.class);
        verify(graphMapper).insert(edges.capture());
        assertEquals("transition:S0-S4", edges.getValue().getSourceTransitionKey());
        assertNull(edges.getValue().getSourceTransitionId());
        assertNull(edges.getValue().getTransitionRevision());
    }

    private List<ProjectStageInstanceDO> stages() {
        return List.of(new ProjectStageInstanceDO().setId(21L).setProjectId(9L).setStageCode("S0"),
                new ProjectStageInstanceDO().setId(22L).setProjectId(9L).setStageCode("S4"));
    }

    private TemplateExecutionSnapshot snapshot() {
        var snapshot = new TemplateExecutionSnapshot();
        snapshot.setCompilerVersion("test-compiler");
        for (String code : List.of("S0", "S4")) {
            var stage = new TemplateExecutionSnapshot.StageContract();
            stage.setNodeKey("stage:" + code);
            stage.setCode(code);
            stage.setName(code);
            stage.setStart("S0".equals(code));
            stage.setTerminal("S4".equals(code));
            var binding = new TemplateExecutionSnapshot.BindingContract();
            binding.setType("STAGE_NATIVE");
            binding.setParameters(JsonUtils.parseObject("{}", tools.jackson.databind.JsonNode.class));
            stage.setBinding(binding);
            var permission = new TemplateExecutionSnapshot.PermissionContract();
            permission.setPolicyRef("PROJECT_STAGE_DEFAULT");
            permission.setPolicySnapshot(JsonUtils.parseObject(
                    "{\"requiredActions\":[\"VIEW\"]}", tools.jackson.databind.JsonNode.class));
            stage.setPermission(permission);
            stage.setCompletionRule(JsonUtils.parseObject(
                    "{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}",
                    tools.jackson.databind.JsonNode.class));
            snapshot.getStages().add(stage);
        }

        var task = new TemplateExecutionSnapshot.TaskContract();
        task.setNodeKey("task:T1");
        task.setCode("T1");
        task.setName("需求分析");
        task.setStageCode("S4");
        var binding = new TemplateExecutionSnapshot.BindingContract();
        binding.setType("TASK_NATIVE");
        binding.setParameters(JsonUtils.parseObject("{}", tools.jackson.databind.JsonNode.class));
        task.setBinding(binding);
        var permission = new TemplateExecutionSnapshot.PermissionContract();
        permission.setPolicyRef("PROJECT_TASK_NATIVE_DEFAULT");
        task.setPermission(permission);
        task.setCompletionRule(JsonUtils.parseObject(
                "{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"COMPLETED\"}}",
                tools.jackson.databind.JsonNode.class));
        snapshot.getTasks().add(task);

        var edge = new TemplateExecutionSnapshot.TransitionContract();
        edge.setEdgeKey("transition:S0-S4");
        edge.setCode("S0-S4");
        edge.setFromStageCode("S0");
        edge.setToStageCode("S4");
        edge.setPriority(1);
        edge.setDefaultBranch(true);
        snapshot.getTransitions().add(edge);
        return snapshot;
    }
}
