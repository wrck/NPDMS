package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.ProjectTemplateController;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDefinitionContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemplateExecutionConfigurationPersistenceTest {
    final ProjectTemplateV2ServiceImpl service = new ProjectTemplateV2ServiceImpl();
    final ProjectTemplateController controller = new ProjectTemplateController();
    final ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
    final ProjectTemplateMapper templates = mock(ProjectTemplateMapper.class);
    final TemplateDefinitionReferenceAssembler legacy = mock(TemplateDefinitionReferenceAssembler.class);
    final ProjectTemplateRevisionDO draft = new ProjectTemplateRevisionDO();

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(7L);
        ReflectionTestUtils.setField(service, "v2RevisionMapper", revisions);
        ReflectionTestUtils.setField(service, "v2TemplateMapper", templates);
        ReflectionTestUtils.setField(service, "v2LegacyAssembler", legacy);
        ReflectionTestUtils.setField(controller, "projectTemplateService", service);
        var template = new ProjectTemplateDO(); template.setId(10L); template.setTenantId(7L); template.setStatus("ACTIVE");
        when(templates.lockTemplate(any())).thenReturn(template); when(templates.incrementVersion(any())).thenReturn(1);
        draft.setId(11L); draft.setTemplateId(10L); draft.setTenantId(7L); draft.setRevisionNo(0); draft.setStatus("DRAFT");
        when(revisions.selectDraftByTemplateId(10L)).thenReturn(draft);
        when(revisions.updateById(any(ProjectTemplateRevisionDO.class))).thenAnswer(call -> {
            ProjectTemplateRevisionDO update = call.getArgument(0);
            draft.setDesignerDocument(update.getDesignerDocument()); return 1;
        });
    }
    @AfterEach
    void clear() { TenantContextHolder.clear(); }

    @Test
    void publicDesignerSaveReopenPreservesIndependentConfigurationWithoutLiveCatalog() {
        var source = TemplateVersionSnapshotTest.designer();
        var value = JsonUtils.parseTree(TemplateExecutionConfigurationTest.SUBSCRIPTION);
        source.getStages().getFirst().setExecution(value);
        source.getStages().getFirst().setWorkBinding(null);
        assertTrue(controller.updateDesignerDraft(10L, source).getData());
        var reopened = controller.getDesignerDraft(10L).getData();
        assertEquals(value, reopened.getStages().getFirst().getExecution());
        assertNotSame(value, reopened.getStages().getFirst().getExecution());
        assertNull(reopened.getStages().getFirst().getWorkBinding());
        verifyNoInteractions(legacy);
    }

    @Test
    void legacyContentWriteCannotEraseNewConfiguration() {
        var source = TemplateVersionSnapshotTest.designer();
        source.getTasks().getFirst().setExecution(JsonUtils.parseTree(TemplateExecutionConfigurationTest.SUBSCRIPTION));
        service.updateProjectTemplateDesigner(10L, source);
        String saved = draft.getDesignerDocument();
        clearInvocations(revisions, templates);
        assertThrows(ServiceException.class, () -> service.updateProjectTemplateDraftContent(10L, new TemplateDefinitionContent()));
        assertEquals(saved, draft.getDesignerDocument());
        verify(revisions, never()).updateById(any(ProjectTemplateRevisionDO.class));
        verify(templates, never()).incrementVersion(any());
        verifyNoInteractions(legacy);
    }

    @Test
    void invalidConfigurationNeverReachesTheDraftWriter() {
        var source = TemplateVersionSnapshotTest.designer();
        source.getStages().getFirst().setExecution(JsonUtils.parseTree("{\"subscriptions\":null}"));
        assertThrows(IllegalArgumentException.class, () -> controller.updateDesignerDraft(10L, source));
        verify(revisions, never()).updateById(any(ProjectTemplateRevisionDO.class));
        verify(templates, never()).incrementVersion(any());
    }
    @Test
    void explicitNullExecutionIsNotSilentlyConvertedToAbsence() {
        var source = TemplateVersionSnapshotTest.designer();
        source.getStages().getFirst().setExecution(JsonUtils.parseTree("null"));
        assertThrows(IllegalArgumentException.class, () -> service.updateProjectTemplateDesigner(10L, source));
        verify(revisions, never()).updateById(any(ProjectTemplateRevisionDO.class));
    }

    @Test
    void savedPermissionShorthandCanPublishAndReadWithoutModifyingTheDraft() {
        var compiler = TemplateExecutionConfigurationCompilationTest.compiler("OK");
        ReflectionTestUtils.setField(service, "templateCompiler", compiler);
        var dependencies = mock(TemplateDesignerDependencyValidator.class);
        var rules = mock(cn.iocoder.yudao.module.pms.project.service.rule.ProjectRulePublicationValidator.class);
        ReflectionTestUtils.setField(service, "dependencyValidator", dependencies);
        ReflectionTestUtils.setField(service, "rulePublicationValidator", rules);
        when(dependencies.validate(any(), anyBoolean())).thenReturn(java.util.List.of());
        when(rules.validate(any())).thenReturn(java.util.List.of());
        when(templates.updateById(any(ProjectTemplateDO.class))).thenReturn(1);
        when(revisions.selectPublishedListByTemplateId(10L)).thenReturn(java.util.List.of());
        when(revisions.insert(any(ProjectTemplateRevisionDO.class))).thenAnswer(call -> {
            ProjectTemplateRevisionDO row = call.getArgument(0); row.setId(12L);
            when(revisions.selectByTemplateIdAndRevisionNo(10L, 1)).thenReturn(row); return 1;
        });
        service.updateProjectTemplateDesigner(10L, TemplateExecutionConfigurationCompilationTest.source());
        String before = draft.getDesignerDocument();
        service.publishProjectTemplate(10L);
        var frozen = service.getExecutionSnapshot(10L, 1);
        assertEquals(TemplateExecutionConfigurationCompilationTest.OP,
                frozen.getTasks().getFirst().getExecution().path("operations").get(0).path("operationCode").asText());
        assertEquals(before, draft.getDesignerDocument());
        assertFalse(service.getDraftDesigner(10L).getTasks().getFirst().getExecution().path("operations").get(0).has("operationCode"));
    }

}
