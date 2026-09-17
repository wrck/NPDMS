package cn.iocoder.yudao.module.pms.engineering.service.taskbusiness;

import cn.iocoder.yudao.module.pms.engineering.controller.admin.requirement.RequirementAnalysisEntityController;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.SiteSurveyEntityController;
import cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisOperationProvider;
import cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity.SiteSurveyOperationProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Metadata is checked against the actual existing Controller declarations, not a guessed name convention. */
class EngineeringOperationPermissionMetadataTest {
    @Test void surveyMappingsMatchAllSixExistingWriteEndpoints() {
        verify(new SiteSurveyOperationProvider(), SiteSurveyEntityController.class, 6);
    }
    @Test void requirementMappingsMatchAllFourExistingWriteEndpoints() {
        verify(new RequirementAnalysisOperationProvider(), RequirementAnalysisEntityController.class, 4);
    }
    private void verify(ProjectBusinessOperationProvider provider, Class<?> controller, int count) {
        assertEquals(count, provider.operations().size()); assertEquals(count, provider.permissionCodes().size());
        for (var descriptor : provider.operations()) {
            var methods = Arrays.stream(controller.getDeclaredMethods())
                    .filter(method -> descriptor.methodName().equals(method.getName())).toList();
            assertEquals(1, methods.size(), descriptor.operationCode());
            var authorization = methods.getFirst().getAnnotation(PreAuthorize.class);
            assertNotNull(authorization);
            var permission = provider.permissionCodes().get(descriptor.operationCode());
            assertNotNull(permission);
            assertEquals("@ss.hasPermission('" + permission + "')", authorization.value(), descriptor.operationCode());
        }
    }
}
