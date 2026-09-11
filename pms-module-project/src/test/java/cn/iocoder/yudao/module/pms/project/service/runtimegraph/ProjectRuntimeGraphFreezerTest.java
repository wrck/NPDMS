package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectStageExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectRuntimeGraphFreezerTest {
    @Test
    void rejectsTemplateS0TasksWithoutFreezingOrChangingSource() {
        var graphMapper = mock(ProjectRuntimeGraphMapper.class);
        var contractMapper = mock(ProjectStageExecutionContractMapper.class);
        var content = content();
        var task = new TemplateDefinitionContent.TaskDef();
        task.setTaskCode("OLD-S0"); task.setStageCode("S0");
        content.getTasks().add(task);
        var error = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ProjectRuntimeGraphFreezer(graphMapper, contractMapper).validate(content));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("S0不生成任务"));
        assertEquals("S0", content.getTasks().getFirst().getStageCode());
        verifyNoInteractions(graphMapper, contractMapper);
    }

    @Test
    void effectiveFromCannotRoundIntoFutureWhenStoredAsDatetimeZero() {
        var graphMapper = mock(ProjectRuntimeGraphMapper.class);
        var contractMapper = mock(ProjectStageExecutionContractMapper.class);
        when(contractMapper.insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO.class))).thenReturn(1);
        when(graphMapper.insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageTransitionDO.class))).thenReturn(1);
        var content = content();
        var now = LocalDateTime.of(2026, 9, 10, 12, 0, 0, 900_000_000);
        new ProjectRuntimeGraphFreezer(graphMapper, contractMapper).freeze(7L, 9L, 10L, content,
                List.of(new ProjectStageInstanceDO().setId(21L).setProjectId(9L).setStageCode("S0"),
                        new ProjectStageInstanceDO().setId(22L).setProjectId(9L).setStageCode("S4")), now);
        var contracts = org.mockito.ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO.class);
        verify(contractMapper, times(2)).insert(contracts.capture());
        contracts.getAllValues().forEach(contract -> assertEquals(now.withNano(0), contract.getEffectiveFrom()));
    }

    @Test
    void v2AcceptsFrozenPermissionSnapshotWithoutLegacyPolicyRevision() {
        var graphMapper = mock(ProjectRuntimeGraphMapper.class);
        var contractMapper = mock(ProjectStageExecutionContractMapper.class);
        var content = v2Content();

        assertDoesNotThrow(() -> new ProjectRuntimeGraphFreezer(graphMapper, contractMapper).validate(content));
        verifyNoInteractions(graphMapper, contractMapper);
    }

    @Test
    void v2FreezesStableKeysAndSnapshotsWithoutLegacyRevisionIds() {
        var graphMapper = mock(ProjectRuntimeGraphMapper.class);
        var contractMapper = mock(ProjectStageExecutionContractMapper.class);
        when(contractMapper.insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO.class))).thenReturn(1);
        when(graphMapper.insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageTransitionDO.class))).thenReturn(1);
        var content = v2Content();
        var now = LocalDateTime.of(2026, 9, 11, 10, 0);

        new ProjectRuntimeGraphFreezer(graphMapper, contractMapper).freeze(7L, 9L, 10L, content,
                List.of(new ProjectStageInstanceDO().setId(21L).setProjectId(9L).setStageCode("S0"),
                        new ProjectStageInstanceDO().setId(22L).setProjectId(9L).setStageCode("S4")), now);

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

        var edges = org.mockito.ArgumentCaptor.forClass(
                cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageTransitionDO.class);
        verify(graphMapper).insert(edges.capture());
        assertEquals("transition:S0-S4", edges.getValue().getSourceTransitionKey());
        assertNull(edges.getValue().getSourceTransitionId());
        assertNull(edges.getValue().getTransitionRevision());
    }

    private TemplateDefinitionContent content() {
        var content = new TemplateDefinitionContent();
        content.setDefinitionSnapshot(JsonUtils.parseObject("""
                [
                  {"definition":{"id":1,"definitionKind":"STAGE","schemaVersion":1,"payload":{}}},
                  {"definition":{"id":2,"definitionKind":"WORK_BINDING","schemaVersion":1,"payload":{"bindingType":"STAGE_NATIVE"}}},
                  {"definition":{"id":3,"definitionKind":"PERMISSION_POLICY","schemaVersion":1,"payload":{}}},
                  {"definition":{"id":4,"definitionKind":"COMPLETION_RULE","schemaVersion":1,"payload":{"predicate":"STAGE_NATIVE_STATUS","parameters":{"requiredStatus":"DONE"}}}}
                ]
                """, JsonNode.class));
        for (String code : List.of("S0", "S4")) {
            var stage = new TemplateDefinitionContent.StageDef();
            stage.setStageCode(code); stage.setStart("S0".equals(code)); stage.setTerminal("S4".equals(code));
            stage.setDefinitionRevisionId(1L); stage.setWorkBindingRevisionId(2L);
            stage.setPermissionPolicyRevisionId(3L); stage.setCompletionRuleRevisionId(4L);
            content.getStages().add(stage);
        }
        var edge = new TemplateDefinitionContent.TransitionDef();
        edge.setId(5L); edge.setRevisionNo(1L); edge.setTransitionCode("S0-S4");
        edge.setFromStageCode("S0"); edge.setToStageCode("S4"); edge.setPriority(1); edge.setDefaultBranch(false);
        content.getTransitions().add(edge);
        return content;
    }

    private TemplateDefinitionContent v2Content() {
        var content = new TemplateDefinitionContent();
        content.setExecutionSnapshot(JsonUtils.parseObject("{\"executionSchemaVersion\":2,\"compilerVersion\":\"test\"}", JsonNode.class));
        for (String code : List.of("S0", "S4")) {
            var stage = new TemplateDefinitionContent.StageDef();
            stage.setStageCode(code);
            stage.setName(code);
            stage.setStart("S0".equals(code));
            stage.setTerminal("S4".equals(code));
            stage.setSourceNodeKey("stage:" + code);
            stage.setBindingSnapshot(JsonUtils.parseObject("{\"type\":\"STAGE_NATIVE\",\"parameters\":{}}", JsonNode.class));
            stage.setPermissionSnapshot(JsonUtils.parseObject("{\"policySnapshot\":{\"requiredActions\":[\"VIEW\"]}}", JsonNode.class));
            stage.setCompletionRuleSnapshot(JsonUtils.parseObject(
                    "{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}", JsonNode.class));
            content.getStages().add(stage);
        }
        var task = new TemplateDefinitionContent.TaskDef();
        task.setTaskCode("T1");
        task.setName("需求分析");
        task.setStageCode("S4");
        task.setSourceNodeKey("task:T1");
        task.setWorkBindingTypeCode("TASK_NATIVE");
        task.setBindingConfig("{}");
        task.setPermissionPolicyRef(null);
        task.setPermissionSnapshot(JsonUtils.parseObject("{\"requiredActions\":[\"VIEW\"]}", JsonNode.class));
        task.setCompletionRuleTypeCode("TASK_NATIVE_STATUS");
        task.setCompletionRuleConfig("{\"requiredStatus\":\"DONE\"}");
        task.setDefinitionVersion(2);
        content.getTasks().add(task);

        var edge = new TemplateDefinitionContent.TransitionDef();
        edge.setTransitionCode("S0-S4");
        edge.setFromStageCode("S0");
        edge.setToStageCode("S4");
        edge.setPriority(1);
        edge.setDefaultBranch(true);
        edge.setSourceTransitionKey("transition:S0-S4");
        content.getTransitions().add(edge);
        return content;
    }
}
