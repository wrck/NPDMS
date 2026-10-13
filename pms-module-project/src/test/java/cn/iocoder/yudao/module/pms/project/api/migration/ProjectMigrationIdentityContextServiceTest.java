package cn.iocoder.yudao.module.pms.project.api.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectMigrationIdentityContextServiceTest {
    ProjectManualCreationService access=mock(ProjectManualCreationService.class);
    ProjectMasterMapper projects=mock(ProjectMasterMapper.class);
    ProjectLegacyDetailMapper sources=mock(ProjectLegacyDetailMapper.class);
    DppmsProjectBindingApi bindings=mock(DppmsProjectBindingApi.class);
    ProjectMigrationIdentityContextService service=new ProjectMigrationIdentityContextService(access,projects,sources,bindings);
    ProjectMasterDO project=new ProjectMasterDO();
    ProjectMigrationIdentityContextApi.Query query=new ProjectMigrationIdentityContextApi.Query(1L,1L,100L,3L,"101",18L);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);project.setId(100L);project.setTenantId(1L);project.setVersion(3L);
        project.setProjectType("10");project.setProjectCode("P-1");project.setCompanyCode("001");project.setLifecycleStatus("NORMAL_CLOSED");
        when(projects.selectById(100L)).thenReturn(project);when(projects.selectByIdForUpdate(100L)).thenReturn(project);
        when(bindings.resolve(any(DppmsProjectBindingApi.Query.class))).thenReturn(new DppmsProjectBindingApi.ProjectBinding(18L,"101",100L,"10","P-0","P-1"));
    }
    @AfterEach void clear() {TenantContextHolder.clear();}
    @Test void identitySupportsOriginalNullOfficeAndClosedStateWithoutWritingProjectFields() {
        assertNull(project.getDepartmentId());assertNull(project.getDepartmentCode());var context=service.lockAndRevalidate(query);
        assertEquals("P-1",context.projectCode());assertEquals("001",context.companyCode());assertEquals("10",context.projectType());
        assertEquals("NORMAL_CLOSED",project.getLifecycleStatus());verify(projects).selectByIdForUpdate(100L);verify(projects,never()).updateById(any(ProjectMasterDO.class));verifyNoInteractions(sources);
    }
    @Test void accessDenialRunsBeforeAnySourceOrProjectQuery() {
        doThrow(new IllegalArgumentException("DENIED")).when(access).getProjectForManage(any(),any());
        assertThrows(IllegalArgumentException.class,()->service.resolve(query));verifyNoInteractions(projects,sources,bindings);
    }
    @Test void tenantVersionDeletedAndBindingMismatchAllBlock() {
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.resolve(query));verifyNoInteractions(projects);
        TenantContextHolder.setTenantId(1L);project.setVersion(4L);assertThrows(IllegalArgumentException.class,()->service.resolve(query));
        project.setVersion(3L);project.setDeleted(true);assertThrows(IllegalArgumentException.class,()->service.resolve(query));
        project.setDeleted(false);project.setProjectType("OTHER");assertThrows(IllegalArgumentException.class,()->service.resolve(query));
        project.setProjectType("10");project.setProjectCode("CHANGED");assertThrows(IllegalArgumentException.class,()->service.resolve(query));
    }
}
