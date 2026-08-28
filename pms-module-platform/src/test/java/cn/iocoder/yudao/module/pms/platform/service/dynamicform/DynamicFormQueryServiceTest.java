package cn.iocoder.yudao.module.pms.platform.service.dynamicform;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetCollectionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileReferenceSetKey;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.PlatformDynamicFormInstanceDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.PlatformDynamicFormInstanceMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormTemplatePageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.FILE_PROVIDER_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicFormQueryServiceTest {

    private static final DynamicFormCommands.Actor ACTOR = new DynamicFormCommands.Actor(0L, 9L);

    @Mock DynamicFormTemplateMapper templateMapper;
    @Mock DynamicFormTemplateRevisionMapper revisionMapper;
    @Mock PlatformDynamicFormInstanceMapper instanceMapper;
    @Mock DynamicFormActionProjection actionProjection;
    @Mock FileArtifactApi fileArtifactApi;

    private DynamicFormQueryService service;

    @BeforeEach
    void setUp() {
        service = new DynamicFormQueryService(templateMapper, revisionMapper, instanceMapper,
                new DynamicFormSchemaService(), actionProjection, fileArtifactApi);
    }

    @Test
    void templatePagePreservesStableMapperOrderAndProjectsCurrentDraftAndPublishedSummaries() {
        List<DynamicFormTemplateDO> templates = IntStream.range(0, 200)
                .mapToObj(index -> template(11L + index, "Template-" + index,
                        index == 0 ? 21L : null, 22L + index))
                .toList();
        when(templateMapper.selectCountPage(any())).thenReturn(200L);
        when(templateMapper.selectPage(any())).thenReturn(templates);
        when(actionProjection.templateActions(any(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(Set.of("PATCH_TEMPLATE"));

        var result = service.pageTemplates(ACTOR, 1, 200);

        assertEquals(200L, result.total());
        assertEquals("Template-0", result.list().getFirst().templateName());
        assertEquals("Template-199", result.list().getLast().templateName());
        assertEquals(21L, result.list().getFirst().currentDraft().revisionId());
        assertEquals(22L, result.list().getFirst().currentPublished().revisionId());
        assertEquals("PUBLISHED", result.list().get(1).currentPublished().status());
        ArgumentCaptor<DynamicFormTemplatePageQuery> query = ArgumentCaptor.forClass(DynamicFormTemplatePageQuery.class);
        verify(templateMapper).selectPage(query.capture());
        verify(templateMapper).selectCountPage(any());
        assertEquals(0L, query.getValue().offset());
        assertEquals(200, query.getValue().limit());
        assertFalse(query.getValue().selectionOnly());
        verify(revisionMapper, never()).selectByRow(any());
    }

    @Test
    void selectionContainsOnlyEnabledCurrentPublishedTemplatesAndReportsTheSameTotal() {
        List<DynamicFormTemplateDO> eligible = IntStream.range(0, 200)
                .mapToObj(index -> template(11L + index, "Eligible-" + index, null, 22L + index))
                .toList();
        when(templateMapper.selectCountPage(any())).thenReturn(200L);
        when(templateMapper.selectPage(any())).thenReturn(eligible);
        when(actionProjection.selectionActions(9L, "ENABLED", true)).thenReturn(Set.of("CREATE_INSTANCE"));

        var result = service.pageSelection(ACTOR, 1, 200);

        assertEquals(200L, result.total());
        assertEquals("Eligible-0", result.list().getFirst().templateName());
        assertEquals("Eligible-199", result.list().getLast().templateName());
        assertEquals(22L, result.list().getFirst().currentPublishedRevisionId());
        assertEquals(Set.of("CREATE_INSTANCE"), result.list().getFirst().allowedActions());
        ArgumentCaptor<DynamicFormTemplatePageQuery> query = ArgumentCaptor.forClass(DynamicFormTemplatePageQuery.class);
        verify(templateMapper).selectCountPage(query.capture());
        verify(templateMapper).selectPage(any());
        assertEquals("ENABLED", query.getValue().availabilityCode());
        assertTrue(query.getValue().selectionOnly());
        verify(revisionMapper, never()).selectByRow(any());
    }

    @Test
    void instanceWithoutControlledFieldsNeverCallsFileArtifact() {
        stubInstance("[{\"type\":\"input\",\"field\":\"name\"}]");
        when(actionProjection.instanceActions(9L, 9L)).thenReturn(Set.of("PATCH_INSTANCE"));

        var result = service.getInstance(ACTOR, 31L);

        assertTrue(result.controlledFiles().isEmpty());
        verifyNoInteractions(fileArtifactApi);
    }

    @Test
    void multipleControlledFieldsUseOneBatchAndPreserveExplicitEmptySet() {
        stubInstance("""
                [{"type":"PmsFileArtifact","field":"drawings"},
                 {"type":"PmsFileArtifact","field":"photos"}]
                """);
        when(actionProjection.instanceActions(9L, 9L)).thenReturn(Set.of("PATCH_INSTANCE"));
        FileReferenceSetKey drawings = key("drawings");
        FileReferenceSetKey photos = key("photos");
        when(fileArtifactApi.inspectReferenceSets(any())).thenReturn(List.of(
                new FileReferenceSetFact(drawings, 21L, List.of(fileFact())),
                new FileReferenceSetFact(photos, 21L, List.of())));

        var result = service.getInstance(ACTOR, 31L);

        assertEquals(1, result.controlledFiles().get("drawings").size());
        assertTrue(result.controlledFiles().get("photos").isEmpty());
        ArgumentCaptor<FileReferenceSetCollectionQuery> query =
                ArgumentCaptor.forClass(FileReferenceSetCollectionQuery.class);
        verify(fileArtifactApi).inspectReferenceSets(query.capture());
        assertEquals(List.of(drawings, photos), query.getValue().collectionKeys());
    }

    @Test
    void businessOwnedInstanceAcceptsOwnerScopeDifferentFromFrozenTemplateRevision() {
        stubInstance("[{\"type\":\"PmsFileArtifact\",\"field\":\"drawings\"}]");
        PlatformDynamicFormInstanceDO business = instance();
        business.setOwnerContext("SOL");
        business.setObjectType("REQUIREMENT_ANALYSIS");
        business.setObjectId("501");
        when(instanceMapper.selectByRow(any())).thenReturn(business);
        when(actionProjection.instanceActions(9L, 9L)).thenReturn(Set.of("PATCH_INSTANCE"));
        FileArtifactVersionFact ownerScopedFact = new FileArtifactVersionFact(
                101L, 2, "2fce3d44-109d-47be-b15a-5ea09fda1a0f",
                "DYNAMIC_FORM_ATTACHMENT", "drawing.pdf", 10L, "application/pdf", "sha", "AVAILABLE",
                "ACTIVE", new FileFactVersion(1, 2, 3), 1L);
        when(fileArtifactApi.inspectReferenceSets(any())).thenReturn(List.of(
                new FileReferenceSetFact(key("drawings"), 1L, List.of(ownerScopedFact))));

        var result = service.getInstance(ACTOR, 31L);

        assertEquals(1L, result.controlledFiles().get("drawings").getFirst().scopeVersion());
    }

    @Test
    void missingSetOrProviderFailureFailsClosed() {
        stubInstance("""
                [{"type":"PmsFileArtifact","field":"drawings"},
                 {"type":"PmsFileArtifact","field":"photos"}]
                """);
        when(fileArtifactApi.inspectReferenceSets(any())).thenReturn(List.of(
                new FileReferenceSetFact(key("drawings"), 21L, List.of())));

        ServiceException missing = assertThrows(ServiceException.class,
                () -> service.getInstance(ACTOR, 31L));
        assertEquals(FILE_PROVIDER_UNAVAILABLE.getCode(), missing.getCode());

        when(fileArtifactApi.inspectReferenceSets(any())).thenThrow(new IllegalStateException("provider down"));
        ServiceException unavailable = assertThrows(ServiceException.class,
                () -> service.getInstance(ACTOR, 31L));
        assertEquals(FILE_PROVIDER_UNAVAILABLE.getCode(), unavailable.getCode());
        verify(fileArtifactApi, never()).lockAndRevalidateReferenceSets(any());
    }

    private void stubInstance(String rules) {
        when(instanceMapper.selectByRow(any())).thenReturn(instance());
        when(templateMapper.selectByRow(any())).thenReturn(template(11L, "Example", null, 21L));
        when(revisionMapper.selectByRow(any())).thenReturn(revision(21L, "PUBLISHED", rules));
    }

    private DynamicFormTemplateDO template(Long id, String name, Long draftId, Long publishedId) {
        DynamicFormTemplateDO row = new DynamicFormTemplateDO();
        row.setId(id);
        row.setTenantId(0L);
        row.setTemplateCode("TPL-" + id);
        row.setTemplateName(name);
        row.setCategoryCode("GENERAL");
        row.setAvailabilityCode("ENABLED");
        row.setCurrentDraftRevisionId(draftId);
        row.setCurrentPublishedRevisionId(publishedId);
        if (draftId != null) {
            row.setCurrentDraftRevisionNo(1);
            row.setCurrentDraftVersion(2);
            row.setCurrentDraftEngineCode(DynamicFormSchemaService.ENGINE_CODE);
            row.setCurrentDraftDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
            row.setCurrentDraftRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        }
        if (publishedId != null) {
            row.setCurrentPublishedRevisionNo(2);
            row.setCurrentPublishedRevisionVersion(2);
            row.setCurrentPublishedEngineCode(DynamicFormSchemaService.ENGINE_CODE);
            row.setCurrentPublishedDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
            row.setCurrentPublishedRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        }
        row.setVersion(3);
        return row;
    }

    private DynamicFormTemplateRevisionDO revision(Long id, String status) {
        return revision(id, status, "[]");
    }

    private DynamicFormTemplateRevisionDO revision(Long id, String status, String rules) {
        DynamicFormTemplateRevisionDO row = new DynamicFormTemplateRevisionDO();
        row.setId(id);
        row.setTenantId(0L);
        row.setTemplateId(11L);
        row.setRevisionNo(Math.toIntExact(id - 20));
        row.setStatusCode(status);
        row.setFormConfJson("{}");
        row.setFormRulesJson(rules);
        row.setEngineCode(DynamicFormSchemaService.ENGINE_CODE);
        row.setDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
        row.setRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        row.setVersion(2);
        return row;
    }

    private PlatformDynamicFormInstanceDO instance() {
        PlatformDynamicFormInstanceDO row = new PlatformDynamicFormInstanceDO();
        row.setId(31L);
        row.setTenantId(0L);
        row.setInstanceCode("DFI-31");
        row.setInstanceName("Example instance");
        row.setTemplateId(11L);
        row.setTemplateRevisionId(21L);
        row.setTemplateRevisionNo(1);
        row.setEngineCode(DynamicFormSchemaService.ENGINE_CODE);
        row.setDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
        row.setRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        row.setValueJson("{\"name\":\"saved\"}");
        row.setCreatedBy(9L);
        row.setVersion(4);
        return row;
    }

    private FileReferenceSetKey key(String field) {
        return new FileReferenceSetKey("PLATFORM", "DYNAMIC_FORM_INSTANCE", "31",
                DynamicFormSchemaService.FILE_PURPOSE_PREFIX + field);
    }

    private FileArtifactVersionFact fileFact() {
        return new FileArtifactVersionFact(101L, 2, "2fce3d44-109d-47be-b15a-5ea09fda1a0f",
                "DYNAMIC_FORM_ATTACHMENT", "drawing.pdf", 10L, "application/pdf", "sha", "AVAILABLE",
                "ACTIVE", new FileFactVersion(1, 2, 3), 21L);
    }
}
