package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectChildDraftFactoryTest {
    @Test void childUsesOwnNameLevelAndOfficeAndOnlyApprovedInheritance() {
        var departments = mock(DeptApi.class);
        var office = new DeptRespDTO(); office.setId(20L); office.setCode("CHILD-OFFICE"); office.setName("子办事处");
        when(departments.getDeptByCode("CHILD-OFFICE")).thenReturn(office);
        var parent = new ProjectMasterDO(); parent.setId(100L); parent.setTenantId(7L);
        parent.setProjectName("父项目"); parent.setBusinessLevelCode("PARENT");
        parent.setDepartmentCode("PARENT-OFFICE"); parent.setCompanyCode("COMPANY");
        parent.setBusinessType("PARENT-ONLY"); parent.setCustomerCode("CUSTOMER");
        parent.setSigningMethod("DIRECT_SIGN"); parent.setProjectType("STANDARD");
        var item = new ProjectSplitItemDO(); item.setProjectName("现场工勘");
        item.setBusinessLevelCode("CHILD"); item.setOfficeDepartmentCode("CHILD-OFFICE");
        var draft = new ProjectChildDraftFactory(departments).create(parent, item);
        var facts = ProjectRuleFields.manualCreationFacts(draft).values();
        assertEquals("现场工勘", facts.get("project.projectName").value());
        assertEquals("CHILD", facts.get("project.businessLevelCode").value());
        assertEquals("CHILD-OFFICE", facts.get("project.departmentCode").value());
        assertEquals("COMPANY", facts.get("project.companyCode").value());
        assertEquals("CUSTOMER", facts.get("project.customerCode").value());
        assertEquals(true, facts.get("project.isChild").value());
        assertNull(facts.get("project.businessType").value());
        assertEquals("父项目", parent.getProjectName()); assertEquals("PARENT-OFFICE", parent.getDepartmentCode());
        assertNull(parent.getParentId()); assertNull(draft.getLifecycleTemplateRevisionId());
    }

    @Test void unavailableOfficeDoesNotFallbackToParent() {
        var parent = new ProjectMasterDO(); parent.setDepartmentCode("PARENT");
        var item = new ProjectSplitItemDO(); item.setOfficeDepartmentCode("MISSING");
        assertThrows(IllegalStateException.class, () -> new ProjectChildDraftFactory(mock(DeptApi.class)).create(parent, item));
    }
}
