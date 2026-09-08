package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.project.domain.template.*;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.*;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemplateDraftBindingResolutionTest {
    @Test
    void draftReferencesResolveRequiredColumnsWithoutPublishingOrCompletingTask() {
        var resolver = mock(DeliveryDefinitionResolver.class);
        var assembler = new TemplateDefinitionReferenceAssembler(resolver);
        var binding = revision(2L, DeliveryDefinitionKind.WORK_BINDING, "BIND", """
                {"bindingType":"BUSINESS_COMPONENT","instanceResolutionStrategy":"REFERENCE_EXISTING",
                 "targetContextCode":"SOL","targetObjectType":"REQUIREMENT_ANALYSIS",
                 "targetObjectKey":"PROJECT_REQUIREMENT_ANALYSIS","businessViewRevisionId":"2097373775105802242",
                 "contextMapping":{"project":"project"}}
                """, List.of());
        var permission = revision(3L, DeliveryDefinitionKind.PERMISSION_POLICY, "POLICY", "{\"requiredActions\":[\"VIEW\"]}", List.of());
        var rule = revision(4L, DeliveryDefinitionKind.COMPLETION_RULE, "RULE", "{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}", List.of());
        var taskDefinition = revision(1L, DeliveryDefinitionKind.TASK, "TASK", "{\"name\":\"需求分析\",\"workBinding\":\"work\",\"permissionPolicy\":\"permission\",\"completionRule\":\"completion\"}",
                List.of(new DeliveryDefinitionReference("work", 2L), new DeliveryDefinitionReference("permission", 3L), new DeliveryDefinitionReference("completion", 4L)));
        var view = new BusinessViewRevision(2097373775105802242L, "REQUIREMENT_ANALYSIS", "ANALYSIS", 1L, "SOL",
                BusinessViewComponentProvider.ViewSource.PAGE, "PROJ_REQUIREMENT_ANALYSIS", "1", null,
                JsonUtils.parseTree("{}"), JsonUtils.parseTree("[]"), "QUERY", "COMMAND", "PERMISSION",
                LocalDateTime.now(), null, 1, "PUBLISHED", Set.of());
        Map<Long, Snapshot> closure = Map.of(1L, new Snapshot(taskDefinition, null), 2L, new Snapshot(binding, view),
                3L, new Snapshot(permission, null), 4L, new Snapshot(rule, null));
        when(resolver.resolve(anyList(), isNull(), eq(true))).thenReturn(closure);
        when(resolver.require(anyMap(), anyLong(), any())).thenAnswer(call -> closure.get(call.<Long>getArgument(1)).definition());
        var content = new TemplateDefinitionContent();
        var task = new TemplateDefinitionContent.TaskDef(); task.setTaskCode("ANALYSIS"); task.setName("需求分析");
        task.setStageCode("S1"); task.setDefinitionRevisionId(1L); content.setTasks(List.of(task));
        assembler.resolveDraftTaskBindings(content);
        assertEquals("BUSINESS_COMPONENT", task.getWorkBindingTypeCode());
        assertEquals("SOL", task.getTargetContextCode());
        assertEquals("PROJ_REQUIREMENT_ANALYSIS", task.getComponentKey());
        assertEquals("POLICY", task.getPermissionPolicyRef());
        assertEquals("TASK_NATIVE_STATUS", task.getCompletionRuleTypeCode());
        assertNotNull(task.getBindingConfig()); assertNotNull(task.getCompletionRuleConfig());
        assertNull(content.getDefinitionSnapshot());
        assertEquals(2L, task.getWorkBindingRevisionId());
    }

    @Test
    void unreferencedHistoricalDraftIsNotReinterpreted() {
        var resolver = mock(DeliveryDefinitionResolver.class);
        var content = new TemplateDefinitionContent();
        var task = new TemplateDefinitionContent.TaskDef(); task.setWorkBindingTypeCode("TASK_NATIVE");
        content.setTasks(List.of(task));
        new TemplateDefinitionReferenceAssembler(resolver).resolveDraftTaskBindings(content);
        verifyNoInteractions(resolver);
        assertEquals("TASK_NATIVE", task.getWorkBindingTypeCode());
    }

    private Revision revision(Long id, DeliveryDefinitionKind kind, String code, String payload, List<DeliveryDefinitionReference> refs) {
        return new Revision(id, kind, code, 1L, "PUBLISHED", 1, JsonUtils.parseTree(payload), refs, LocalDateTime.now(), null, 1);
    }
}
