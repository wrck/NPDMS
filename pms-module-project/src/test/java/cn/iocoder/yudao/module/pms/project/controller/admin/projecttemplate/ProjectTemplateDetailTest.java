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
        var revision = new ProjectTemplateRevisionDO(); revision.setStatus("DRAFT"); revision.setRevisionNo(0);
        when(service.getProjectTemplate(91L)).thenReturn(template);
        when(service.getRevisionList(91L)).thenReturn(List.of(revision));
        var controller = new ProjectTemplateController();
        ReflectionTestUtils.setField(controller, "projectTemplateService", service);
        var result = controller.getProjectTemplate(91L).getData();
        assertEquals("NEW_RULE_TEMPLATE", result.getCode());
        assertEquals("DRAFT", result.getRevisions().getFirst().getStatus());
        assertFalse(JsonUtils.parseTree(JsonUtils.toJsonString(result)).has("draftContent"));
        verify(service).getProjectTemplate(91L);
        verify(service).getRevisionList(91L);
        verifyNoMoreInteractions(service);
    }
}
