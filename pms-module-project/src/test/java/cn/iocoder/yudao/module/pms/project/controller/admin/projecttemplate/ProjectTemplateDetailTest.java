package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTemplateDetailTest {
    @Test void identityAndRevisionListDoNotRequireACompleteDraftOrLegacyProjection() {
        var service = mock(ProjectTemplateService.class);
        var template = new ProjectTemplateDO(); template.setId(91L); template.setCode("NEW_RULE_TEMPLATE");
        var revision = revision(); revision.setStatus("DRAFT"); revision.setRevisionNo(0);
        when(service.getProjectTemplate(91L)).thenReturn(template);
        when(service.getRevisionList(91L)).thenReturn(List.of(revision));
        var controller = new ProjectTemplateController();
        ReflectionTestUtils.setField(controller, "projectTemplateService", service);
        var result = controller.getProjectTemplate(91L).getData();
        assertEquals("NEW_RULE_TEMPLATE", result.getCode());
        assertEquals("DRAFT", result.getRevisions().getFirst().getStatus());
        assertRevisionMetadata(result.getRevisions().getFirst());
        assertFalse(JsonUtils.parseTree(JsonUtils.toJsonString(result)).has("draftContent"));
        verify(service).getProjectTemplate(91L);
        verify(service).getRevisionList(91L);
        verifyNoMoreInteractions(service);
    }

    @Test void revisionDetailInheritsThePublishedSchemaMetadataWithoutSynthesizingSnapshotHash() {
        var service = mock(ProjectTemplateService.class);
        var revision = revision(); revision.setSnapshotHash(null);
        when(service.getRevision(91L, 2)).thenReturn(revision);
        when(service.getRevisionContent(91L, 2)).thenReturn(null);
        var controller = new ProjectTemplateController();
        ReflectionTestUtils.setField(controller, "projectTemplateService", service);

        var detail = controller.getProjectTemplateRevision(91L, 2).getData();

        assertEquals(2, detail.getDesignerSchemaVersion());
        assertEquals(3, detail.getExecutionSchemaVersion());
        assertEquals("template-version-3", detail.getCompilerVersion());
        assertNull(detail.getSnapshotHash());
        verify(service).getRevision(91L, 2);
        verify(service).getRevisionContent(91L, 2);
        verifyNoMoreInteractions(service);
    }

    private ProjectTemplateRevisionDO revision() {
        var revision = new ProjectTemplateRevisionDO();
        revision.setDesignerSchemaVersion(2); revision.setExecutionSchemaVersion(3);
        revision.setCompilerVersion("template-version-3"); revision.setSnapshotHash("frozen-sha256");
        return revision;
    }

    private void assertRevisionMetadata(cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate.vo.ProjectTemplateRevisionRespVO revision) {
        assertEquals(2, revision.getDesignerSchemaVersion());
        assertEquals(3, revision.getExecutionSchemaVersion());
        assertEquals("template-version-3", revision.getCompilerVersion());
        assertEquals("frozen-sha256", revision.getSnapshotHash());
    }
}
