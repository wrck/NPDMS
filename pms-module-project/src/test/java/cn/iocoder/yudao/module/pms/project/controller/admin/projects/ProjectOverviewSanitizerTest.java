package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectRespVO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectOverviewSanitizerTest {
    @Test void summaryAllowlistRemovesPrivateAndEnrichedFields() {
        var row = new ProjectRespVO(); row.setVisibility("SUMMARY"); row.setId(9L); row.setProjectCode("P9");
        row.setProjectName("公开概况"); row.setLifecycleStatus("ACTIVE"); row.setCurrentStage("S2");
        row.setContractNo("PRIVATE"); row.setCustomerName("PRIVATE"); row.setManagerId(123L);
        row.setCreationReason("PRIVATE"); row.setProcessDefinitionKey("PRIVATE"); row.setVersion(3L);
        var safe = ProjectOverviewSanitizer.sanitize(row);
        assertEquals("P9",safe.getProjectCode()); assertEquals("S2",safe.getCurrentStage());
        assertNull(safe.getContractNo()); assertNull(safe.getCustomerName()); assertNull(safe.getManagerId());
        assertNull(safe.getCreationReason()); assertNull(safe.getProcessDefinitionKey()); assertNull(safe.getVersion());
    }
    @Test void fullProjectResponseKeepsExistingFields() {
        var row = new ProjectRespVO(); row.setVisibility("FULL"); row.setContractNo("AUTHORIZED");
        assertSame(row,ProjectOverviewSanitizer.sanitize(row));
    }
}
