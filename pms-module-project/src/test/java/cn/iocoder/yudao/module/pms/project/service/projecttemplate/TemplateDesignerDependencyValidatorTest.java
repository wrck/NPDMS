package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionFact;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateProcessDefinitionQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemplateDesignerDependencyValidatorTest {
    final ProjectStageGateProcessOwnerApi processes = mock(ProjectStageGateProcessOwnerApi.class);
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void processGateUsesItsExactPinAndOnlyChangedProjectReferencesAreRevalidated() {
        TenantContextHolder.setTenantId(7L);
        var api = mock(BusinessViewQueryApi.class);
        var validator = new TemplateDesignerDependencyValidator(api, processes);
        var document = new TemplateDesignerDocument();
        var gate = new TemplateDesignerDocument.GateNode(); gate.setNodeKey("gate:approval");
        gate.setStageCode("PREP"); gate.setGateType("EXIT");
        var ref = new TemplateDesignerDocument.GateReference(); ref.setRefType("APPROVAL"); ref.setRefCode("approval");
        gate.setReferences(new java.util.ArrayList<>(List.of(ref))); document.getGates().add(gate);
        assertTrue(validator.validate(document, true).stream().anyMatch(issue -> "GATE_PROCESS_DEFINITION_REQUIRED".equals(issue.code())));
        verifyNoInteractions(processes);
        ref.setRefVersion("approval:1:101");
        when(processes.inspectDefinitionKey(any())).thenReturn(new ProjectStageGateProcessDefinitionFact("approval:1:101", "approval", "审批", true));
        assertTrue(validator.validate(document, true).isEmpty());
        verify(processes).inspectDefinitionKey(new ProjectStageGateProcessDefinitionQuery(7L, "approval", "approval:1:101"));
        clearInvocations(processes);
        var submitted = JsonUtils.parseObject(JsonUtils.toJsonString(document), TemplateDesignerDocument.class);
        submitted.getGates().getFirst().setName("仅改名");
        assertTrue(validator.validateProjectChanges(document, submitted, true).isEmpty());
        verifyNoInteractions(processes);
        assertEquals(1, submitted.getGates().getFirst().getReferences().size());
        submitted.getGates().getFirst().getReferences().getFirst().setRefVersion("approval:2:202");
        var failures = validator.validateProjectChanges(document, submitted, true);
        assertEquals("gates[0].references[0]", failures.getFirst().field());
        assertEquals("APPROVAL_DEFINITION_UNAVAILABLE", failures.getFirst().code());
        assertEquals("approval:1:101", document.getGates().getFirst().getReferences().getFirst().getRefVersion());
        verifyNoInteractions(api);
    }

    @Test
    void projectRuleOnlyChangeKeepsTheExistingBindingWithoutRevalidatingAsNewReference() {
        BusinessViewQueryApi api = mock(BusinessViewQueryApi.class);
        var active = designer(); var submitted = designer(); submitted.getTasks().getFirst().setName("计划侧改名");
        var validator = new TemplateDesignerDependencyValidator(api, processes);
        assertTrue(validator.validateProjectChanges(active,submitted,true).isEmpty());
        verifyNoInteractions(api);
        assertEquals("BUSINESS_COMPONENT",submitted.getTasks().getFirst().getWorkBinding().getType());
        assertEquals("BUSINESS_COMPONENT",active.getTasks().getFirst().getWorkBinding().getType());
    }

    @Test
    void newProjectBindingStillRequiresAnAvailableExactPublishedView() {
        BusinessViewQueryApi api = mock(BusinessViewQueryApi.class);
        var active = designer(); var submitted = designer(); submitted.getTasks().getFirst().setNodeKey("task:new");
        var validator = new TemplateDesignerDependencyValidator(api, processes);
        assertTrue(validator.validateProjectChanges(active,submitted,false).stream()
                .anyMatch(issue -> issue.code().equals("BUSINESS_VIEW_UNAVAILABLE")));
        verify(api).getRevision(new BusinessViewQueryApi.Query(91L,BusinessViewQueryApi.Purpose.NEW_REFERENCE,7));
    }

    @Test
    void validateAcceptsExactPublishedBusinessView() {
        BusinessViewQueryApi api = mock(BusinessViewQueryApi.class);
        when(api.getRevision(any())).thenReturn(revision());
        var validator = new TemplateDesignerDependencyValidator(api, processes);

        assertTrue(validator.validate(designer(), false).isEmpty());
        verify(api).getRevision(new BusinessViewQueryApi.Query(91L,
                BusinessViewQueryApi.Purpose.NEW_REFERENCE, 7));
        verify(api, never()).lockAndRevalidateAll(any());
    }

    @Test
    void publishUsesBatchLockAndRejectsSnapshotDrift() {
        BusinessViewQueryApi api = mock(BusinessViewQueryApi.class);
        BusinessViewRevision changed = new BusinessViewRevision(91L, "SITE_SURVEY", "VIEW", 3L,
                "SOL", BusinessViewComponentProvider.ViewSource.PAGE,
                "SOL_SITE_SURVEY_V2", "2", null,
                JsonUtils.parseTree("{\"required\":[\"project\"]}"), JsonUtils.parseTree("[\"VIEW\"]"),
                "Q", "C", "P", LocalDateTime.now(), null, 7, "PUBLISHED", Set.of());
        when(api.lockAndRevalidateAll(any())).thenReturn(List.of(changed));
        var validator = new TemplateDesignerDependencyValidator(api, processes);

        var issues = validator.validate(designer(), true);

        assertTrue(issues.stream().anyMatch(issue -> "BUSINESS_VIEW_SNAPSHOT_MISMATCH".equals(issue.code())));
        verify(api).lockAndRevalidateAll(List.of(new BusinessViewQueryApi.Query(91L,
                BusinessViewQueryApi.Purpose.NEW_REFERENCE, 7)));
        verify(api, never()).getRevision(any());
    }

    @Test
    void unavailableViewFailsClosedAtExactBinding() {
        BusinessViewQueryApi api = mock(BusinessViewQueryApi.class);
        when(api.getRevision(any())).thenThrow(new IllegalArgumentException("disabled"));
        var validator = new TemplateDesignerDependencyValidator(api, processes);

        var issues = validator.validate(designer(), false);

        assertEquals(1, issues.size());
        assertEquals("tasks[0].workBinding", issues.getFirst().field());
        assertEquals("BUSINESS_VIEW_UNAVAILABLE", issues.getFirst().code());
    }

    @Test void approvalBindingsValidateTheExactDefinitionForTasksAndStagesWithoutChangingTheDraft() {
        TenantContextHolder.setTenantId(7L);
        var api = mock(BusinessViewQueryApi.class);
        var validator = new TemplateDesignerDependencyValidator(api, processes);
        var document = approvalDesigner();
        var stage = new TemplateDesignerDocument.StageNode(); stage.setNodeKey("stage:approval");
        stage.setWorkBinding(document.getTasks().getFirst().getWorkBinding()); document.getStages().add(stage);
        String before = JsonUtils.toJsonString(document);
        when(processes.inspectDefinitionKey(any())).thenReturn(new ProjectStageGateProcessDefinitionFact("approval:1:101", "approval", "审批", true));
        assertTrue(validator.validate(document, false).isEmpty());
        assertTrue(validator.validate(document, true).isEmpty());
        verify(processes, times(4)).inspectDefinitionKey(new ProjectStageGateProcessDefinitionQuery(7L, "approval", "approval:1:101"));
        verifyNoInteractions(api); assertEquals(before, JsonUtils.toJsonString(document));
    }

    @Test void missingUnavailableAndMismatchedApprovalDefinitionsCannotPublish() {
        TenantContextHolder.setTenantId(7L);
        var validator = new TemplateDesignerDependencyValidator(mock(BusinessViewQueryApi.class), processes);
        var document = approvalDesigner(); var binding = document.getTasks().getFirst().getWorkBinding();
        binding.setParameters(JsonUtils.parseTree("{}"));
        assertEquals("APPROVAL_DEFINITION_REQUIRED", validator.validate(document, true).getFirst().code());
        verifyNoInteractions(processes);
        binding.setParameters(JsonUtils.parseTree("{\"processDefinitionId\":\"approval:1:101\"}"));
        for (var actual : List.of(
                new ProjectStageGateProcessDefinitionFact("approval:2:202", "approval", "新版", true),
                new ProjectStageGateProcessDefinitionFact("approval:1:101", "other", "错误key", true),
                new ProjectStageGateProcessDefinitionFact("approval:1:101", "approval", "不可选", false))) {
            when(processes.inspectDefinitionKey(any())).thenReturn(actual);
            var issue = validator.validate(document, true).getFirst();
            assertEquals("APPROVAL_DEFINITION_UNAVAILABLE", issue.code()); assertEquals("tasks[0].workBinding", issue.field());
        }
        when(processes.inspectDefinitionKey(any())).thenThrow(new IllegalStateException("private engine information"));
        var issue = validator.validate(document, false).getFirst();
        assertEquals("APPROVAL_DEFINITION_UNAVAILABLE", issue.code());
        org.junit.jupiter.api.Assertions.assertFalse(issue.message().contains("private"));
    }

    @Test void unchangedProjectApprovalBindingKeepsItsPinButReplacementMustBeRevalidated() {
        TenantContextHolder.setTenantId(7L);
        var validator = new TemplateDesignerDependencyValidator(mock(BusinessViewQueryApi.class), processes);
        var effective = approvalDesigner(); var changed = approvalDesigner(); changed.getTasks().getFirst().setName("改名");
        assertTrue(validator.validateProjectChanges(effective, changed, true).isEmpty());
        verifyNoInteractions(processes);
        changed.getTasks().getFirst().getWorkBinding().setParameters(JsonUtils.parseTree("{\"processDefinitionId\":\"approval:2:202\"}"));
        assertEquals("APPROVAL_DEFINITION_UNAVAILABLE", validator.validateProjectChanges(effective, changed, true).getFirst().code());
        verify(processes).inspectDefinitionKey(new ProjectStageGateProcessDefinitionQuery(7L, "approval", "approval:2:202"));
        assertEquals("approval:1:101", effective.getTasks().getFirst().getWorkBinding().getParameters().path("processDefinitionId").asText());
    }

    private TemplateDesignerDocument approvalDesigner() {
        var document = designer(); var binding = new TemplateDesignerDocument.WorkBindingSpec();
        binding.setType("APPROVAL"); binding.setApprovalDefinitionKey("approval");
        binding.setParameters(JsonUtils.parseTree("{\"processDefinitionId\":\"approval:1:101\"}"));
        document.getTasks().getFirst().setWorkBinding(binding); return document;
    }

    private TemplateDesignerDocument designer() {
        var designer = new TemplateDesignerDocument();
        var task = new TemplateDesignerDocument.TaskNode();
        task.setNodeKey("task:T1");
        task.setCode("T1");
        task.setStageCode("S1");
        var binding = new TemplateDesignerDocument.WorkBindingSpec();
        binding.setType("BUSINESS_COMPONENT");
        binding.setTargetContextCode("SOL");
        binding.setTargetObjectType("SITE_SURVEY");
        binding.setTargetObjectKey("PROJECT_SITE_SURVEY");
        binding.setComponentKey("SOL_SITE_SURVEY");
        binding.setParameters(JsonUtils.parseTree("{\"businessViewRevisionId\":91}"));
        binding.setBusinessViewSnapshot(JsonUtils.parseTree("""
                {"id":91,"revisionNo":3,"version":7,"status":"PUBLISHED","ownerContext":"SOL","entityType":"SITE_SURVEY",
                 "componentKey":"SOL_SITE_SURVEY","componentVersion":"1","viewSource":"PAGE"}
                """));
        task.setWorkBinding(binding);
        designer.getTasks().add(task);
        return designer;
    }

    private BusinessViewRevision revision() {
        return new BusinessViewRevision(91L, "SITE_SURVEY", "VIEW", 3L,
                "SOL", BusinessViewComponentProvider.ViewSource.PAGE,
                "SOL_SITE_SURVEY", "1", null,
                JsonUtils.parseTree("{\"required\":[\"project\"]}"), JsonUtils.parseTree("[\"VIEW\"]"),
                "Q", "C", "P", LocalDateTime.now(), null, 7, "PUBLISHED", Set.of());
    }
}
