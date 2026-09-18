package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRulePublicationValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TemplateVersionedPublicationTest {
    private final ProjectTemplateV2ServiceImpl service = new ProjectTemplateV2ServiceImpl();
    private final ProjectTemplateMapper templates = mock(ProjectTemplateMapper.class);
    private final ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
    private final TemplateCompiler compiler = spy(new TemplateCompiler());
    private final TemplateDesignerDependencyValidator dependencies = mock(TemplateDesignerDependencyValidator.class);
    private final ProjectRulePublicationValidator rules = mock(ProjectRulePublicationValidator.class);
    private final List<ProjectTemplateRevisionDO> history = new ArrayList<>();
    private final ProjectTemplateRevisionDO draft = new ProjectTemplateRevisionDO();

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(7L);
        ReflectionTestUtils.setField(service, "v2TemplateMapper", templates);
        ReflectionTestUtils.setField(service, "v2RevisionMapper", revisions);
        ReflectionTestUtils.setField(service, "templateCompiler", compiler);
        ReflectionTestUtils.setField(service, "dependencyValidator", dependencies);
        ReflectionTestUtils.setField(service, "rulePublicationValidator", rules);
        var template = new ProjectTemplateDO();
        template.setId(10L); template.setTenantId(7L); template.setStatus("ACTIVE"); template.setVersion(7);
        when(templates.lockTemplate(any())).thenReturn(template);
        when(templates.incrementVersion(any())).thenReturn(1);
        when(templates.updateById(any(ProjectTemplateDO.class))).thenReturn(1);
        when(revisions.updateById(any(ProjectTemplateRevisionDO.class))).thenReturn(1);
        draft.setId(11L); draft.setTenantId(7L); draft.setTemplateId(10L); draft.setRevisionNo(0); draft.setStatus("DRAFT");
        draft.setDesignerSchemaVersion(2);
        draft.setDesignerDocument(JsonUtils.toJsonString(
                cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection.forEditing(TemplateVersionSnapshotTest.designer())));
        when(revisions.selectDraftByTemplateId(10L)).thenReturn(draft);
        when(revisions.selectPublishedListByTemplateId(10L)).thenAnswer(call -> history.stream()
                .sorted(Comparator.comparing(ProjectTemplateRevisionDO::getRevisionNo).reversed()).toList());
        when(revisions.insert(any(ProjectTemplateRevisionDO.class))).thenAnswer(call -> {
            ProjectTemplateRevisionDO row = call.getArgument(0);
            row.setId(100L + history.size()); history.add(row); return 1;
        });
        when(revisions.selectByTemplateIdAndRevisionNo(eq(10L), anyInt())).thenAnswer(call -> history.stream()
                .filter(row -> row.getRevisionNo().equals(call.getArgument(1))).findFirst().orElse(null));
        when(dependencies.validate(any(), anyBoolean())).thenReturn(List.of());
        when(rules.validate(any())).thenReturn(List.of());
    }

    @AfterEach
    void cleanup() { TenantContextHolder.clear(); }

    @Test
    void appendsCompleteVersionsAndNeverUpdatesPublishedOrDraftContent() {
        String draftBefore = draft.getDesignerDocument();
        service.publishProjectTemplate(10L);
        var first = history.getFirst();
        String firstBefore = JsonUtils.toJsonString(first);
        assertEquals(3, first.getExecutionSchemaVersion());
        assertNull(first.getSnapshotHash());
        assertEquals(7L, first.getTenantId());
        assertEquals(1, first.getRevisionNo());
        assertEquals(draftBefore, first.getDesignerDocument());
        var frozen = service.getExecutionSnapshot(10L, 1);
        assertEquals(frozen.toRuntimeContent(), service.getRevisionContent(10L, 1));

        var changed = TemplateVersionSnapshotTest.designer();
        changed.getStages().getFirst().setName("新版本阶段");
        draft.setDesignerDocument(JsonUtils.toJsonString(changed));
        service.publishProjectTemplate(10L);

        assertEquals(2, history.size());
        assertEquals(2, history.get(1).getRevisionNo());
        assertEquals(firstBefore, JsonUtils.toJsonString(first));
        assertEquals(frozen, service.getExecutionSnapshot(10L, 1));
        assertNotEquals(frozen, service.getExecutionSnapshot(10L, 2));
        verify(revisions, never()).updateById(any(ProjectTemplateRevisionDO.class));
        verify(compiler, never()).compile(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"STATUS", "TENANT", "TEMPLATE", "VERSION", "ID"})
    void rejectsNonDraftOrMismatchedRowsBeforeAnyWrite(String damage) {
        switch (damage) {
            case "STATUS" -> draft.setStatus("PUBLISHED");
            case "TENANT" -> draft.setTenantId(8L);
            case "TEMPLATE" -> draft.setTemplateId(20L);
            case "VERSION" -> draft.setRevisionNo(1);
            case "ID" -> draft.setId(null);
            default -> throw new AssertionError(damage);
        }
        assertThrows(ServiceException.class, () -> service.publishProjectTemplate(10L));
        assertThrows(ServiceException.class, () -> service.updateProjectTemplateDesigner(10L, TemplateVersionSnapshotTest.designer()));
        verifyNoInteractions(compiler, dependencies, rules);
        verify(revisions, never()).insert(any(ProjectTemplateRevisionDO.class));
        verify(revisions, never()).updateById(any(ProjectTemplateRevisionDO.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"INSERT", "STATUS", "CAS"})
    void zeroAffectedRowsAreFailuresRatherThanSuccessfulPublication(String phase) {
        switch (phase) {
            case "INSERT" -> when(revisions.insert(any(ProjectTemplateRevisionDO.class))).thenReturn(0);
            case "STATUS" -> when(templates.updateById(any(ProjectTemplateDO.class))).thenReturn(0);
            case "CAS" -> when(templates.incrementVersion(any())).thenReturn(0);
            default -> throw new AssertionError(phase);
        }
        assertThrows(ServiceException.class, () -> service.publishProjectTemplate(10L));
        if (phase.equals("INSERT")) verify(templates, never()).updateById(any(ProjectTemplateDO.class));
        if (!phase.equals("CAS")) verify(templates, never()).incrementVersion(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ID", "TENANT", "TEMPLATE", "VERSION", "STATUS", "HASH", "COMPILER", "SNAPSHOT", "DESIGNER",
            "DESIGNER_FORMAT", "UNKNOWN_DESIGNER", "DUPLICATE_DESIGNER", "COERCED_DESIGNER"})
    void refusesBrokenVersionedPublicationOnReadAndCopy(String damage) {
        service.publishProjectTemplate(10L);
        var row = history.getFirst();
        switch (damage) {
            case "ID" -> row.setId(null);
            case "TENANT" -> row.setTenantId(8L);
            case "TEMPLATE" -> row.setTemplateId(20L);
            case "VERSION" -> row.setRevisionNo(0);
            case "STATUS" -> row.setStatus("DRAFT");
            case "HASH" -> row.setSnapshotHash("not-a-version-identity");
            case "COMPILER" -> row.setCompilerVersion("different");
            case "SNAPSHOT" -> row.setExecutionSnapshot(null);
            case "DESIGNER" -> row.setDesignerDocument(null);
            case "DESIGNER_FORMAT" -> row.setDesignerSchemaVersion(3);
            case "UNKNOWN_DESIGNER" -> row.setDesignerDocument("{\"schemaVersion\":2,\"unknown\":true}");
            case "DUPLICATE_DESIGNER" -> row.setDesignerDocument("{\"schemaVersion\":2,\"schemaVersion\":2}");
            case "COERCED_DESIGNER" -> row.setDesignerDocument("{\"schemaVersion\":\"2\"}");
            default -> throw new AssertionError(damage);
        }
        doReturn(row).when(revisions).selectByTemplateIdAndRevisionNo(10L, 1);
        clearInvocations(compiler);
        assertThrows(ServiceException.class, () -> service.getExecutionSnapshot(10L, 1));
        assertThrows(ServiceException.class, () -> ReflectionTestUtils.invokeMethod(service, "designerForRevision", 10L, 1));
        verifyNoInteractions(compiler);
    }

    @Test
    void newestValidVersionIsMatchedWithoutRecompilationAndCorruptionDoesNotSelectAnOlderOne() {
        service.publishProjectTemplate(10L);
        service.publishProjectTemplate(10L);
        var template = templates.lockTemplate(null);
        template.setCode("TEMPLATE"); template.setName("模板"); template.setMatchPriority(1);
        when(templates.selectListByStatusOrderByPriority("ACTIVE")).thenReturn(List.of(template));
        var evaluator = mock(ProjectTemplateMatchRuleEvaluator.class);
        ReflectionTestUtils.setField(service, "matchRuleEvaluator", evaluator);
        when(evaluator.evaluate(eq(7L), anyLong(), any(), any())).thenReturn(
                new cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation("test",
                        cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation.Outcome.MATCHED, "MATCHED",
                        List.of(), List.of(), List.of()));
        var facts = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchFacts(java.util.Map.of());
        clearInvocations(compiler);
        var result = service.matchPreview(facts);
        assertEquals(2, result.getMatched().getLatestRevisionNo());
        assertEquals(history.get(1).getId(), result.getMatched().getTemplateRevisionId());
        history.get(1).setDesignerDocument(null);
        clearInvocations(evaluator);
        assertTrue(service.matchPreview(facts).getCandidates().isEmpty());
        verifyNoInteractions(compiler, evaluator);
    }

    @Test
    void publicCopyCreatesOnlyANewDraftAndPreservesBothFrozenDocuments() {
        service.publishProjectTemplate(10L);
        var original = history.getFirst();
        String before = JsonUtils.toJsonString(original);
        var command = mock(cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationCommands.class);
        when(command.execute(anyString(), anyString(), any(), eq(Long.class), any()))
                .thenAnswer(call -> call.<java.util.function.Supplier<Long>>getArgument(4).get());
        ReflectionTestUtils.setField(service, "v2ConfigurationCommands", command);
        ReflectionTestUtils.setField(service, "projectTemplateMapper", templates);
        ReflectionTestUtils.setField(service, "revisionMapper", revisions);
        var copy = new ProjectTemplateDO();
        copy.setId(20L); copy.setTenantId(7L); copy.setStatus("DRAFT"); copy.setVersion(0);
        doReturn(copy).when(templates).lockTemplate(new cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.query.TemplateIdentityQuery(7L,20L));
        when(templates.insert(any(ProjectTemplateDO.class))).thenAnswer(call -> {
            ProjectTemplateDO inserted = call.getArgument(0);
            inserted.setId(20L); inserted.setTenantId(7L); return 1;
        });
        var copyDraft = new ProjectTemplateRevisionDO();
        copyDraft.setId(21L); copyDraft.setTenantId(7L); copyDraft.setTemplateId(20L);
        copyDraft.setStatus("DRAFT"); copyDraft.setRevisionNo(0);
        doReturn(copyDraft).when(revisions).selectDraftByTemplateId(20L);
        when(revisions.insert(any(ProjectTemplateRevisionDO.class))).thenAnswer(call -> {
            ProjectTemplateRevisionDO inserted = call.getArgument(0);
            assertEquals(20L, inserted.getTemplateId()); assertEquals("DRAFT", inserted.getStatus());
            inserted.setId(21L); inserted.setTenantId(7L); return 1;
        });
        var body = new cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateCopyReqVO();
        body.setCode("COPY"); body.setName("副本"); body.setSourceRevisionNo(1);
        clearInvocations(compiler, revisions);
        assertEquals(20L, service.copyProjectTemplate(10L, 7, body, "copy-versioned"));
        var saved = org.mockito.ArgumentCaptor.forClass(ProjectTemplateRevisionDO.class);
        verify(revisions).updateById(saved.capture());
        assertEquals(21L, saved.getValue().getId());
        assertEquals(original.getDesignerDocument(), saved.getValue().getDesignerDocument());
        assertEquals(before, JsonUtils.toJsonString(original));
        assertEquals(1, history.size());
        verifyNoInteractions(compiler);
    }

    @Test
    void validCopyReturnsIndependentFrozenDesignerWithoutRecompilation() {
        service.publishProjectTemplate(10L);
        var row = history.getFirst();
        String before = JsonUtils.toJsonString(row);
        clearInvocations(compiler);
        var first = TemplateVersionPublication.read(row, 7L, 10L, 1);
        var second = TemplateVersionPublication.read(row, 7L, 10L, 1);
        first.designer().getStages().getFirst().setName("只修改副本");
        assertNotEquals(first.designer(), second.designer());
        assertEquals(before, JsonUtils.toJsonString(row));
        verifyNoInteractions(compiler);
    }
}
