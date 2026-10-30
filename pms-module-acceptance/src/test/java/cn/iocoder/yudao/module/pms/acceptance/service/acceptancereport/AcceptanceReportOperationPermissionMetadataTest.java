package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.module.pms.acceptance.controller.admin.acceptancereport.AcceptanceReportController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceReportOperationPermissionMetadataTest {
    @Test void reportMappingMatchesExistingWriteEndpointsWithoutIncludingFilePermission() {
        var provider = new AcceptanceReportOperationProvider();
        assertEquals(4, provider.operations().size()); assertEquals(4, provider.permissionCodes().size());
        for (var descriptor : provider.operations()) {
            var methods = Arrays.stream(AcceptanceReportController.class.getDeclaredMethods())
                    .filter(method -> descriptor.methodName().equals(method.getName())).toList();
            assertEquals(1, methods.size());
            var authorization = methods.getFirst().getAnnotation(PreAuthorize.class);
            assertNotNull(authorization);
            var permission = provider.permissionCodes().get(descriptor.operationCode());
            assertEquals("pms:acceptance:report:write", permission);
            assertEquals("@ss.hasPermission('" + permission + "')", authorization.value());
        }
    }
}
