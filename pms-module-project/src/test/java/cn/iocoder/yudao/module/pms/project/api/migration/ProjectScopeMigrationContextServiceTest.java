package cn.iocoder.yudao.module.pms.project.api.migration;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.legacydetail.ProjectLegacySourceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.legacydetail.ProjectLegacyDetailMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectScopeMigrationContextServiceTest {
    ProjectManualCreationService access=mock(ProjectManualCreationService.class);
    ProjectMasterMapper projects=mock(ProjectMasterMapper.class);
    ProjectLegacyDetailMapper sources=mock(ProjectLegacyDetailMapper.class);
    DeptApi departments=mock(DeptApi.class);
    DppmsProjectBindingApi bindings=mock(DppmsProjectBindingApi.class);
    ProjectScopeMigrationContextService service=new ProjectScopeMigrationContextService(access,projects,sources,departments,bindings);
    ProjectScopeMigrationContextApi.Query query=new ProjectScopeMigrationContextApi.Query(1L,1L,100L,3L,"101");
    ProjectMasterDO project=new ProjectMasterDO();DeptRespDTO dept=new DeptRespDTO();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);project.setId(100L);project.setTenantId(1L);project.setVersion(3L);project.setProjectCode("P");
        project.setLifecycleStatus("NORMAL_CLOSED");project.setDepartmentId(20L);project.setDepartmentCode("162007");
        var source=new ProjectLegacySourceDO();source.setProjectId(100L);when(sources.selectSource(any())).thenReturn(source);
        when(projects.selectById(100L)).thenReturn(project);when(projects.selectByIdForUpdate(100L)).thenReturn(project);
        dept.setId(20L);dept.setCode("162007");dept.setName("Office");dept.setVersion(7);when(departments.getDept(20L)).thenReturn(dept);
    }
    @AfterEach void cleanup() {TenantContextHolder.clear();}
    @Test void historicalContextSupportsClosedProjectsWithoutChangingTheirState() {
        var result=service.lockAndRevalidate(query);assertEquals("162007",result.departmentCode());assertEquals(7,result.departmentVersion());
        assertEquals("NORMAL_CLOSED",project.getLifecycleStatus());verify(projects,never()).updateById(any(ProjectMasterDO.class));
    }
    @Test void staleProjectOrWrongOfficeCannotBeUsed() {
        project.setVersion(4L);assertThrows(RuntimeException.class,()->service.resolve(query));
        project.setVersion(3L);dept.setCode("OTHER");assertThrows(RuntimeException.class,()->service.resolve(query));
    }
    @Test void projectAccessAndExactSourceBindingAreRequired() {
        doThrow(new IllegalArgumentException("DENIED")).when(access).getProjectForManage(any(),any());
        assertThrows(RuntimeException.class,()->service.resolve(query));verifyNoInteractions(sources);
    }
    @Test void wrongTenantCannotLookUpAProjectOrDepartment() {
        TenantContextHolder.setTenantId(2L);assertThrows(RuntimeException.class,()->service.resolve(query));
        verifyNoInteractions(access,projects,sources,departments);
    }
    @Test void wholeTableContextUsesFormalMigrationBindingWithoutSourceSnapshots() {
        project.setProjectType("10");project.setProjectCode("P-1");
        when(bindings.resolve(any(DppmsProjectBindingApi.Query.class))).thenReturn(new DppmsProjectBindingApi.ProjectBinding(18L,"101",100L,"10","P-0","P-1"));
        var result=service.resolve(new ProjectScopeMigrationContextApi.Query(1L,1L,100L,3L,"101",18L));
        assertEquals("P-1",result.projectCode());verifyNoInteractions(sources);
        project.setProjectCode("changed");assertThrows(IllegalArgumentException.class,()->service.resolve(new ProjectScopeMigrationContextApi.Query(1L,1L,100L,3L,"101",18L)));
        verify(projects,never()).updateById(any(ProjectMasterDO.class));
    }
}
