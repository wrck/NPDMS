package cn.iocoder.yudao.module.pms.cutover.service.taskbusiness;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class CutoverBusinessViewRoutesTest {
    @Test
    void approvalResultDoesNotShadowExistingCompletionOrWorkbenchRoutes() {
        var permissions = mock(PermissionApi.class);
        var historical = new CutoverCompletionBusinessViewProvider(permissions);
        var providers = List.of(new CutoverApprovalBusinessViewProvider(permissions),
                historical, new CutoverWorkbenchBusinessViewProvider(historical));
        var paths = new HashSet<String>();
        for (var provider : providers) {
            for (String path : provider.pagePaths()) {
                assertTrue(paths.add(path), () -> "Duplicate deployed Owner route: " + path);
            }
        }
    }
}
