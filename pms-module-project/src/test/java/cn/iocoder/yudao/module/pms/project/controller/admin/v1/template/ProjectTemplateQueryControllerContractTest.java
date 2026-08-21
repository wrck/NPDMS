package cn.iocoder.yudao.module.pms.project.controller.admin.v1.template;

import cn.iocoder.yudao.module.pms.project.controller.admin.v1.template.vo.ProjectTemplatePreviewRespVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProjectTemplateQueryControllerContractTest {

    @Test
    void endpointsUseV1RouteAndProjectCreatePermission() throws Exception {
        assertEquals("/api/v1/pms/project-templates",
                ProjectTemplateQueryController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals("@ss.hasPermission('pms:project:create')",
                ProjectTemplateQueryController.class.getMethod("getCandidates",
                        cn.iocoder.yudao.module.pms.project.controller.admin.v1.template.vo.ProjectTemplateCandidateReqVO.class)
                        .getAnnotation(PreAuthorize.class).value());
        assertEquals("@ss.hasPermission('pms:project:create')",
                ProjectTemplateQueryController.class.getMethod("getPreview", Long.class,
                        cn.iocoder.yudao.module.pms.project.controller.admin.v1.template.vo.ProjectTemplateCandidateReqVO.class)
                        .getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void previewContractDoesNotExposeBindingConfigOrExecutableInternals() {
        Set<String> fields = Arrays.stream(ProjectTemplatePreviewRespVO.class.getRecordComponents())
                .map(RecordComponent::getName).collect(Collectors.toSet());

        assertFalse(fields.contains("bindingConfig"));
        assertFalse(fields.contains("completionRuleConfig"));
        assertFalse(fields.contains("repository"));
        assertFalse(fields.contains("script"));
    }
}
