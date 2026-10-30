package cn.iocoder.yudao.module.infra.controller.admin.file;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FileReceiptRouteRegistrationTest {
    @Test
    void registersFileControllersWithoutDuplicateRoutes() throws Exception {
        try (var context = new StaticWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
            for (var candidate : scanner.findCandidateComponents(getClass().getPackageName())) {
                var type = Class.forName(candidate.getBeanClassName());
                context.getBeanFactory().registerSingleton(type.getName(), mock(type));
            }
            context.refresh();
            var mapping = new RequestMappingHandlerMapping();
            mapping.setApplicationContext(context);
            assertDoesNotThrow(mapping::afterPropertiesSet);
            assertEquals(1, mapping.getHandlerMethods().keySet().stream()
                    .filter(info -> info.getPatternValues().contains("/infra/file-storage-receipts/content")).count());
        }
    }
}
