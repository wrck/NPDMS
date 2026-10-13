package cn.iocoder.yudao.module.pms.project.service.projectmanual;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.model.ProjectBusinessModelContributor;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ProjectEntityFieldProviderTest {
    ProjectManualCreationService access=mock(ProjectManualCreationService.class);ProjectMasterMapper projects=mock(ProjectMasterMapper.class);
    ProjectBusinessModelContributor model=mock(ProjectBusinessModelContributor.class);
    ProjectEntityFieldProvider provider=new ProjectEntityFieldProvider(access,projects,model);
    EntityDataRef target=EntityDataRef.current(new EntityRef(1L,"PRJ","project",19L));
    EntityActor actor=new EntityActor(1L,7L,"test");
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void close(){TenantContextHolder.clear();}
    @Test void readPermissionFailureIsNotBypassedByPublicExtensions() {
        when(access.getProject(eq(19L),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->provider.requireReadable(target,actor));
        verifyNoInteractions(projects);
    }
    @Test void writePermissionFailureCannotAcquireOrModifyProject() {
        when(access.getProjectForManage(eq(19L),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("denied"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->provider.lockForWrite(target,actor,3L));
        verifyNoInteractions(projects);
    }
    @Test void currentProjectVersionAndTenantAreRecheckedUnderOwnerLock() {
        var row=new ProjectMasterDO();row.setId(19L);row.setTenantId(1L);row.setDeleted(false);row.setVersion(3L);
        when(projects.selectByIdForUpdate(19L)).thenReturn(row);
        assertDoesNotThrow(()->provider.lockForWrite(target,actor,3L));
        assertThrows(IllegalArgumentException.class,()->provider.lockForWrite(target,actor,4L));
        row.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->provider.lockForWrite(target,actor,3L));
    }
    @Test void crossTenantAndRevisionIdentitiesAreRejectedBeforeOwnerRead() {
        assertThrows(IllegalArgumentException.class,()->provider.requireReadable(target,new EntityActor(2L,7L,"test")));
        assertThrows(IllegalArgumentException.class,()->provider.requireReadable(new EntityDataRef(target.entity(),21L),actor));
        verifyNoInteractions(access,projects);
    }
}
