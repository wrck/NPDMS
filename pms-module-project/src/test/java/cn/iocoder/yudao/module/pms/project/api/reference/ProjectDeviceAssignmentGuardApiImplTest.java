package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectDeviceAssignmentGuardApiImplTest {

    @Mock
    private ProjectMasterMapper projectMasterMapper;
    @Mock
    private ProjectTreeVersionMapper projectTreeVersionMapper;
    @Mock
    private ProjectTreePathMapper projectTreePathMapper;
    @Mock
    private ProjectScopeApi projectScopeApi;

    private ProjectDeviceAssignmentGuardApiImpl guardApi;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        guardApi = new ProjectDeviceAssignmentGuardApiImpl(
                projectMasterMapper, projectTreeVersionMapper, projectTreePathMapper, projectScopeApi);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldAllowManageableLeafProjectRegardlessOfLifecycleStatus() {
        ProjectMasterDO project = project(10L, 1L, 20L, 10L, "NORMAL_CLOSED");
        when(projectMasterMapper.selectById(10L)).thenReturn(project);
        when(projectTreeVersionMapper.selectLatestActive(10L)).thenReturn(treeVersion(7L));
        when(projectTreePathMapper.selectParentsWithChildren(10L, 7L, Set.of(10L))).thenReturn(Set.of());
        when(projectScopeApi.resolve(any())).thenReturn(new ProjectScopeResult(10L, 7L, Set.of(10L), Set.of()));

        ProjectDeviceAssignmentGuardResult result = guardApi.validate(
                new ProjectDeviceAssignmentGuardQuery(1L, 10L, 99L));

        assertTrue(result.assignable());
        assertNull(result.rejectionCode());
        assertEquals(20L, result.customerId());
        assertEquals(7L, result.treeVersion());
    }

    @Test
    void shouldRejectProjectWithChildren() {
        ProjectMasterDO project = project(10L, 1L, 20L, 10L, "ACTIVE");
        when(projectMasterMapper.selectById(10L)).thenReturn(project);
        when(projectTreeVersionMapper.selectLatestActive(10L)).thenReturn(treeVersion(7L));
        when(projectTreePathMapper.selectParentsWithChildren(10L, 7L, Set.of(10L))).thenReturn(Set.of(10L));

        ProjectDeviceAssignmentGuardResult result = guardApi.validate(
                new ProjectDeviceAssignmentGuardQuery(1L, 10L, 99L));

        assertFalse(result.assignable());
        assertEquals("PROJECT_NOT_ACTUAL_NODE", result.rejectionCode());
    }

    @Test
    void shouldRejectProjectOutsideManageScope() {
        ProjectMasterDO project = project(10L, 1L, 20L, 10L, "ACTIVE");
        when(projectMasterMapper.selectById(10L)).thenReturn(project);
        when(projectTreeVersionMapper.selectLatestActive(10L)).thenReturn(treeVersion(7L));
        when(projectTreePathMapper.selectParentsWithChildren(10L, 7L, Set.of(10L))).thenReturn(Set.of());
        when(projectScopeApi.resolve(any())).thenReturn(new ProjectScopeResult(10L, 7L, Set.of(), Set.of(10L)));

        ProjectDeviceAssignmentGuardResult result = guardApi.validate(
                new ProjectDeviceAssignmentGuardQuery(1L, 10L, 99L));

        assertFalse(result.assignable());
        assertEquals("PROJECT_MANAGE_FORBIDDEN", result.rejectionCode());
    }

    private static ProjectMasterDO project(Long id, Long tenantId, Long customerId, Long rootId,
                                           String lifecycleStatus) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(id);
        project.setTenantId(tenantId);
        project.setCustomerId(customerId);
        project.setRootId(rootId);
        project.setLifecycleStatus(lifecycleStatus);
        return project;
    }

    private static ProjectTreeVersionDO treeVersion(Long version) {
        ProjectTreeVersionDO treeVersion = new ProjectTreeVersionDO();
        treeVersion.setTreeVersion(version);
        return treeVersion;
    }
}
