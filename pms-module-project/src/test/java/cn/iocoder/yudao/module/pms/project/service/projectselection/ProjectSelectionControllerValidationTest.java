package cn.iocoder.yudao.module.pms.project.service.projectselection;

import cn.iocoder.yudao.module.pms.project.controller.admin.projects.ProjectSelectionController;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSelectionControllerValidationTest {
    @Test void rejectsOversizedKeywordsInvalidProjectsAndUnboundedPages() throws Exception {
        var controller = new ProjectSelectionController(mock(ProjectSelectionQueryService.class));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new ProjectSelectionController.DevicePageRequest();
            assertTrue(validator.validate(request).isEmpty());
            request.setPageSize(101); assertFalse(validator.validate(request).isEmpty());
            request.setPageSize(100); request.setPageNo(0); assertFalse(validator.validate(request).isEmpty());
            request.setPageNo(1); request.setKeyword("x".repeat(129)); assertFalse(validator.validate(request).isEmpty());
            request.setKeyword("SN"); assertTrue(validator.validate(request).isEmpty());
            var method = ProjectSelectionController.class.getMethod("devices", Long.class, ProjectSelectionController.DevicePageRequest.class);
            assertFalse(validator.forExecutables().validateParameters(controller, method, new Object[] { -1L, request }).isEmpty());
        }
    }
}
