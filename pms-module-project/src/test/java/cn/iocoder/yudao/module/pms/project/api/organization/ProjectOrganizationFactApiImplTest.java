package cn.iocoder.yudao.module.pms.project.api.organization;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactQuery;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectOrganizationFactApiImplTest {

    @Mock private ProjectMasterMapper projectMapper;
    private ProjectOrganizationFactApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        api = new ProjectOrganizationFactApiImpl(projectMapper);
    }

    @AfterEach
    void tearDown() { TenantContextHolder.clear(); }

    @Test
    void inspectReturnsTrustedProjectOrganization() {
        when(projectMapper.selectById(10L)).thenReturn(project(0L, 3, 20L, 30L, " D-30 "));

        var fact = api.inspect(new ProjectOrganizationFactQuery(10L));

        assertEquals(10L, fact.projectId());
        assertEquals(3, fact.projectVersion());
        assertEquals(20L, fact.companyId());
        assertEquals(30L, fact.departmentId());
        assertEquals("D-30", fact.departmentCode());
    }

    @Test
    void lockAndRevalidateRejectsChangedVersion() {
        when(projectMapper.selectByIdForUpdate(10L)).thenReturn(project(0L, 4, 20L, 30L, "D-30"));

        assertThrows(RuntimeException.class, () -> api.lockAndRevalidate(
                new ProjectOrganizationFactRevalidationQuery(10L, 3)));
    }

    @Test
    void rejectsCrossTenantOrIncompleteOrganization() {
        when(projectMapper.selectById(10L)).thenReturn(project(1L, 3, 20L, 30L, "D-30"));
        assertThrows(RuntimeException.class, () -> api.inspect(new ProjectOrganizationFactQuery(10L)));

        when(projectMapper.selectById(11L)).thenReturn(project(0L, 3, 20L, null, "D-30"));
        assertThrows(RuntimeException.class, () -> api.inspect(new ProjectOrganizationFactQuery(11L)));
    }

    private ProjectMasterDO project(Long tenantId, Integer version, Long companyId,
                                    Long departmentId, String departmentCode) {
        ProjectMasterDO row = new ProjectMasterDO();
        row.setId(10L);
        row.setTenantId(tenantId);
        row.setVersion(version);
        row.setCompanyId(companyId);
        row.setDepartmentId(departmentId);
        row.setDepartmentCode(departmentCode);
        return row;
    }
}
