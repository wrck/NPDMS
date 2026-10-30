package cn.iocoder.yudao.module.pms.project.service.rule;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class ProjectRuleFieldConfigurationTest {
    @Test void publicEntityFieldsAppearWithRealTypesWithoutFieldRegistration() {
        var fields = new ProjectRuleFields(key -> null).catalog();
        assertTrue(fields.stream().anyMatch(f -> f.code().equals("project.managerId") && f.valueType().equals("NUMBER")));
        assertTrue(fields.stream().anyMatch(f -> f.code().equals("project.contractNo") && f.label().equals("手工登记合同号")));
        assertFalse(fields.stream().anyMatch(f -> f.code().equals("project.tenantId") || f.code().equals("project.treePath")));
        assertFalse(fields.stream().anyMatch(f -> f.code().equals("project.serviceManagerAssigned")));
    }
    @Test void configurationChangesLabelsAndVisibilityWithoutRemappingPublishedFacts() {
        AtomicReference<String> config = new AtomicReference<>("{}");
        var fields = new ProjectRuleFields(key -> config.get());
        var project = new ProjectMasterDO(); project.setManagerId(7L);
        config.set("{\"project.managerId\":{\"label\":\"交付负责人\",\"availableAtCreation\":true}}");
        assertTrue(fields.catalog().stream().anyMatch(f -> f.code().equals("project.managerId")
                && f.label().equals("交付负责人") && f.availableAtCreation()));
        config.set("{\"project.managerId\":{\"enabled\":false,\"availableAtCreation\":false}}");
        assertFalse(fields.codes().contains("project.managerId"));
        assertEquals(7L, fields.read(project,"project.managerId").value());
        assertEquals(7L, fields.creationFacts(project).values().get("project.managerId").value());
    }
    @Test void aliasesKeepTheirExistingMeaningAndInvalidConfigurationFailsClosed() {
        var fields = new ProjectRuleFields(key -> null);
        var project = new ProjectMasterDO(); project.setImplementationMode("REMOTE"); project.setParentId(9L);
        assertEquals("REMOTE", fields.read(project,"project.implementationMethod").value());
        assertEquals(true, fields.read(project,"project.isChild").value());
        assertFalse(fields.read(project,"project.password").available());
        assertThrows(IllegalArgumentException.class, () -> new ProjectRuleFields(key -> "{\"project.password\":{}}").catalog());
        assertThrows(IllegalArgumentException.class, () -> new ProjectRuleFields(key ->
                "{\"project.projectName\":{\"property\":\"managerId\"}}").catalog());
    }
}
