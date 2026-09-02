package cn.iocoder.yudao.module.pms.project.api.commerce;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectFactOutcome;
import cn.iocoder.yudao.module.pms.project.api.commerce.dto.ProjectOfficeFactQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectOfficeFactApiImplTest {

    @Mock private ProjectMasterMapper projectMapper;
    @Mock private DeptApi deptApi;
    private ProjectOfficeFactApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        api = new ProjectOfficeFactApiImpl(projectMapper, deptApi);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void resolvesCurrentSystemOfficeFact() {
        when(projectMapper.selectById(10L)).thenReturn(project(1L, 3, "ACTIVE", 20L, "OFF-20"));
        when(deptApi.getDept(20L)).thenReturn(dept(20L, "OFF-20", "杭州办事处", 7,
                CommonStatusEnum.ENABLE.getStatus()));

        var fact = api.resolve(new ProjectOfficeFactQuery(1L, 10L, 3));

        assertEquals(ProjectFactOutcome.FOUND, fact.outcome());
        assertEquals(20L, fact.officeDepartmentId());
        assertEquals("OFF-20", fact.officeDepartmentCode());
        assertEquals("杭州办事处", fact.officeDepartmentName());
        assertEquals(7, fact.officeDepartmentVersion());
    }

    @Test
    void lockedReadReturnsVersionConflictWithoutUsingDepartmentFact() {
        when(projectMapper.selectByIdForUpdate(10L)).thenReturn(project(1L, 4, "ACTIVE", 20L, "OFF-20"));

        var fact = api.lockAndRevalidate(new ProjectOfficeFactQuery(1L, 10L, 3));

        assertEquals(ProjectFactOutcome.VERSION_CONFLICT, fact.outcome());
    }

    @Test
    void disabledDepartmentFailsClosedAsInactive() {
        when(projectMapper.selectById(10L)).thenReturn(project(1L, 3, "ACTIVE", 20L, "OFF-20"));
        when(deptApi.getDept(20L)).thenReturn(dept(20L, "OFF-20", "杭州办事处", 7,
                CommonStatusEnum.DISABLE.getStatus()));

        var fact = api.resolve(new ProjectOfficeFactQuery(1L, 10L, 3));

        assertEquals(ProjectFactOutcome.INACTIVE, fact.outcome());
    }

    @Test
    void rejectsUntrustedTenant() {
        assertThrows(RuntimeException.class,
                () -> api.resolve(new ProjectOfficeFactQuery(2L, 10L, 3)));
    }

    @Test
    void lockedReadRequiresExistingTransaction() throws Exception {
        Transactional transactional = ProjectOfficeFactApiImpl.class
                .getMethod("lockAndRevalidate", ProjectOfficeFactQuery.class)
                .getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }

    private ProjectMasterDO project(Long tenantId, Integer version, String lifecycleStatus,
                                    Long departmentId, String departmentCode) {
        ProjectMasterDO row = new ProjectMasterDO();
        row.setId(10L);
        row.setTenantId(tenantId);
        row.setVersion(version);
        row.setLifecycleStatus(lifecycleStatus);
        row.setDepartmentId(departmentId);
        row.setDepartmentCode(departmentCode);
        return row;
    }

    private DeptRespDTO dept(Long id, String code, String name, Integer version, Integer status) {
        DeptRespDTO row = new DeptRespDTO();
        row.setId(id);
        row.setCode(code);
        row.setName(name);
        row.setVersion(version);
        row.setStatus(status);
        return row;
    }
}
