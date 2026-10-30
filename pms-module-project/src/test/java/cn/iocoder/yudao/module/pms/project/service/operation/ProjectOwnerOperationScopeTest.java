package cn.iocoder.yudao.module.pms.project.service.operation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectOwnerOperationScopeTest {
    @Test void ownerDeclarationsAndCallbackTargetsStayIsolated() throws Exception {
        assertEquals(28, ProjectOwnerOperationScopeCases.runAll());
    }
}
