package cn.iocoder.yudao.module.pms.project.domain.template.operation;

import org.junit.jupiter.api.Test;

class ProjectOperationPermissionIndexTest {
    @Test void preservesExactLookupAndDisambiguatesPermissionSelections() {
        ProjectOperationPermissionCases.runAll();
    }
}
