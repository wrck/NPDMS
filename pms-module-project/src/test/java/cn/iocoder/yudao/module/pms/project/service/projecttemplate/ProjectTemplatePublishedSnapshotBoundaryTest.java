package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateCopyReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.query.TemplateIdentityQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRules;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryConfigurationCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ObjectNode;

import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TEMPLATE_PUBLISH_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectTemplatePublishedSnapshotBoundaryTest {

    private final ProjectTemplateRevisionMapper revisions = mock(ProjectTemplateRevisionMapper.class);
    private final ProjectTemplateMapper templates = mock(ProjectTemplateMapper.class);
    private final TemplateCompiler compiler = mock(TemplateCompiler.class);
    private final TemplateDefinitionReferenceAssembler legacy = mock(TemplateDefinitionReferenceAssembler.class);
    private final DeliveryConfigurationCommands commands = mock(DeliveryConfigurationCommands.class);
    private final ProjectTemplateV2ServiceImpl service = new ProjectTemplateV2ServiceImpl();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        ReflectionTestUtils.setField(service, "v2RevisionMapper", revisions);
        ReflectionTestUtils.setField(service, "v2TemplateMapper", templates);
        ReflectionTestUtils.setField(service, "templateCompiler", compiler);
        ReflectionTestUtils.setField(service, "v2LegacyAssembler", legacy);
        ReflectionTestUtils.setField(service, "v2ConfigurationCommands", commands);
        ProjectTemplateDO template = new ProjectTemplateDO();
        template.setId(10L);
        template.setTenantId(1L);
        template.setVersion(7);
        when(templates.lockTemplate(any(TemplateIdentityQuery.class))).thenReturn(template);
        when(commands.execute(anyString(), anyString(), any(), eq(Long.class), any()))
                .thenAnswer(call -> call.<Supplier<Long>>getArgument(4).get());
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void missingStoredSchemaCannotBeRestoredByTheModelDefault() {
        ProjectTemplateRevisionDO row = published();
        ObjectNode json = JsonUtils.parseObject(row.getExecutionSnapshot(), ObjectNode.class);
        json.remove("executionSchemaVersion");
        row.setExecutionSnapshot(JsonUtils.toJsonString(json));
        rejectEveryPublishedRead(row);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"2\"", "2.0", "2.5", "2e0", "3", "4294967298"})
    void malformedSchemaCannotPassUsingAHashOfTheCoercedSnapshot(String token) {
        ProjectTemplateRevisionDO row = published();
        ObjectNode json = JsonUtils.parseObject(row.getExecutionSnapshot(), ObjectNode.class);
        json.set("executionSchemaVersion", JsonUtils.parseObject(token, tools.jackson.databind.JsonNode.class));
        row.setExecutionSnapshot(JsonUtils.toJsonString(json));
        rejectEveryPublishedRead(row);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_SNAPSHOT", "BAD_HASH", "UNKNOWN_SCHEMA", "BAD_COMPILER",
            "MISSING_DESIGNER", "NULL_DESIGNER", "UNKNOWN_DESIGNER_SCHEMA", "MISSING_DESIGNER_SCHEMA",
            "COERCED_DESIGNER_SCHEMA", "DESIGNER_METADATA_MISMATCH"})
    void publicCopyRejectsBrokenPublicationBeforeCreatingADraft(String damage) {
        ProjectTemplateRevisionDO row = published();
        switch (damage) {
            case "MISSING_SNAPSHOT" -> row.setExecutionSnapshot(null);
            case "BAD_HASH" -> row.setSnapshotHash("bad-hash");
            case "UNKNOWN_SCHEMA" -> row.setExecutionSchemaVersion(3);
            case "BAD_COMPILER" -> row.setCompilerVersion("different-compiler");
            case "MISSING_DESIGNER" -> row.setDesignerDocument(null);
            case "NULL_DESIGNER" -> row.setDesignerDocument("null");
            case "UNKNOWN_DESIGNER_SCHEMA" -> row.setDesignerDocument("{\"schemaVersion\":3}");
            case "MISSING_DESIGNER_SCHEMA" -> row.setDesignerDocument("{}");
            case "COERCED_DESIGNER_SCHEMA" -> row.setDesignerDocument("{\"schemaVersion\":\"2\"}");
            case "DESIGNER_METADATA_MISMATCH" -> row.setDesignerSchemaVersion(3);
            default -> throw new AssertionError(damage);
        }
        when(revisions.selectByTemplateIdAndRevisionNo(10L, 4)).thenReturn(row);

        ServiceException failure = assertThrows(ServiceException.class, this::copyPublished);

        assertEquals(PROJECT_TEMPLATE_PUBLISH_INVALID.getCode(), failure.getCode());
        verify(revisions, never()).insert(any(ProjectTemplateRevisionDO.class));
        verify(revisions, never()).updateById(any(ProjectTemplateRevisionDO.class));
        verify(templates, never()).insert(any(ProjectTemplateDO.class));
        verifyNoInteractions(compiler, legacy);
    }

    @Test
    void validPublishedCopyReadsFrozenDesignerWithoutCompilingOrWritingHistory() {
        ProjectTemplateRevisionDO row = published();
        String snapshotBefore = row.getExecutionSnapshot();
        String designerBefore = row.getDesignerDocument();
        when(revisions.selectByTemplateIdAndRevisionNo(10L, 4)).thenReturn(row);

        TemplateDesignerDocument first = ReflectionTestUtils.invokeMethod(service, "designerForRevision", 10L, 4);
        TemplateDesignerDocument second = ReflectionTestUtils.invokeMethod(service, "designerForRevision", 10L, 4);

        assertNotNull(first);
        assertNotSame(first, second);
        assertEquals(designerBefore, JsonUtils.toJsonString(first));
        assertEquals(snapshotBefore, row.getExecutionSnapshot());
        assertEquals(designerBefore, row.getDesignerDocument());
        verify(revisions, never()).updateById(any(ProjectTemplateRevisionDO.class));
        verifyNoInteractions(compiler, legacy);
    }

    private void rejectEveryPublishedRead(ProjectTemplateRevisionDO row) {
        when(revisions.selectByTemplateIdAndRevisionNo(10L, 4)).thenReturn(row);
        assertEquals(PROJECT_TEMPLATE_PUBLISH_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.getExecutionSnapshot(10L, 4)).getCode());
        assertEquals(PROJECT_TEMPLATE_PUBLISH_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.getRevisionContent(10L, 4)).getCode());
        assertEquals(PROJECT_TEMPLATE_PUBLISH_INVALID.getCode(), assertThrows(ServiceException.class,
                this::copyPublished).getCode());
        verifyNoInteractions(compiler, legacy);
    }

    private Long copyPublished() {
        ProjectTemplateCopyReqVO body = new ProjectTemplateCopyReqVO();
        body.setSourceRevisionNo(4);
        body.setCode("COPY");
        body.setName("副本");
        return service.copyProjectTemplate(10L, 7, body, "copy-frozen-publication");
    }

    private ProjectTemplateRevisionDO published() {
        TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
        snapshot.setCompilerVersion("historical-compiler");
        ProjectTemplateRevisionDO row = new ProjectTemplateRevisionDO();
        row.setTemplateId(10L);
        row.setRevisionNo(4);
        row.setStatus(TemplateRules.REVISION_STATUS_PUBLISHED);
        row.setDesignerSchemaVersion(TemplateDesignerDocument.SCHEMA_VERSION);
        row.setDesignerDocument(JsonUtils.toJsonString(new TemplateDesignerDocument()));
        row.setExecutionSchemaVersion(TemplateExecutionSnapshot.SCHEMA_VERSION);
        row.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        row.setCompilerVersion(snapshot.getCompilerVersion());
        row.setSnapshotHash(TemplateExecutionSnapshotHasher.hash(snapshot));
        return row;
    }
}
