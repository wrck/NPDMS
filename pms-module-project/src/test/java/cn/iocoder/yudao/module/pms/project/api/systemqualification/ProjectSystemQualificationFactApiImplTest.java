package cn.iocoder.yudao.module.pms.project.api.systemqualification;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.systemqualification.dto.ProjectSystemQualificationLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectSystemQualificationFactApiImplTest {

    @Mock private ProjectMasterMapper projectMapper;
    @Mock private ProjectTreeVersionMapper treeVersionMapper;
    private ProjectSystemQualificationFactApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        api = new ProjectSystemQualificationFactApiImpl(projectMapper, treeVersionMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void locksRootThenProjectThenCurrentTreeAndReturnsCurrentFact() {
        ProjectMasterDO observed = project(20L, 10L, 7L, 200L, "ACTIVE", "S4", 8);
        ProjectMasterDO root = project(10L, 10L, 7L, 100L, "ACTIVE", "S4", 3);
        ProjectMasterDO locked = project(20L, 10L, 7L, 201L, "ACTIVE", "S4", 9);
        ProjectTreeVersionDO tree = treeVersion(7L, 10L, 15L);
        when(projectMapper.selectById(20L)).thenReturn(observed);
        when(projectMapper.selectByIdForUpdate(10L)).thenReturn(root);
        when(projectMapper.selectByIdForUpdate(20L)).thenReturn(locked);
        when(treeVersionMapper.selectLatestActiveForUpdate(10L)).thenReturn(tree);

        var fact = api.lockCurrentForSystem(new ProjectSystemQualificationLockQuery(20L, "ACTIVE", "S4"));

        assertEquals(20L, fact.projectId());
        assertEquals(201L, fact.currentManagerUserId());
        assertEquals(9, fact.currentProjectVersion());
        assertEquals(9L, fact.currentParticipantFactVersion());
        assertEquals(15L, fact.currentTreeVersion());
        InOrder order = inOrder(projectMapper, treeVersionMapper);
        order.verify(projectMapper).selectById(20L);
        order.verify(projectMapper).selectByIdForUpdate(10L);
        order.verify(projectMapper).selectByIdForUpdate(20L);
        order.verify(treeVersionMapper).selectLatestActiveForUpdate(10L);
    }

    @Test
    void rejectsAnythingOtherThanFixedActiveS4BeforeRead() {
        assertThrows(ServiceException.class, () -> api.lockCurrentForSystem(
                new ProjectSystemQualificationLockQuery(20L, "ACTIVE", "S3")));
        assertThrows(ServiceException.class, () -> api.lockCurrentForSystem(
                new ProjectSystemQualificationLockQuery(20L, "NORMAL_CLOSED", "S4")));
        verify(projectMapper, never()).selectById(20L);
    }

    @Test
    void rejectsMissingManagerOrUnavailableTreeFact() {
        when(projectMapper.selectById(20L)).thenReturn(project(20L, 20L, 7L, null, "ACTIVE", "S4", 8));
        when(projectMapper.selectByIdForUpdate(20L))
                .thenReturn(project(20L, 20L, 7L, null, "ACTIVE", "S4", 8));
        assertThrows(ServiceException.class, () -> api.lockCurrentForSystem(
                new ProjectSystemQualificationLockQuery(20L, "ACTIVE", "S4")));
        verify(treeVersionMapper, never()).selectLatestActiveForUpdate(20L);

        when(projectMapper.selectById(21L)).thenReturn(project(21L, 21L, 7L, 201L, "ACTIVE", "S4", 8));
        when(projectMapper.selectByIdForUpdate(21L))
                .thenReturn(project(21L, 21L, 7L, 201L, "ACTIVE", "S4", 8));
        when(treeVersionMapper.selectLatestActiveForUpdate(21L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> api.lockCurrentForSystem(
                new ProjectSystemQualificationLockQuery(21L, "ACTIVE", "S4")));
    }

    @Test
    void rejectsTenantOrRootIdentityChangeUnderLock() {
        when(projectMapper.selectById(20L)).thenReturn(project(20L, 10L, 7L, 200L, "ACTIVE", "S4", 8));
        when(projectMapper.selectByIdForUpdate(10L))
                .thenReturn(project(10L, 10L, 8L, 100L, "ACTIVE", "S4", 3));
        assertThrows(ServiceException.class, () -> api.lockCurrentForSystem(
                new ProjectSystemQualificationLockQuery(20L, "ACTIVE", "S4")));

        when(projectMapper.selectById(22L)).thenReturn(project(22L, 10L, 7L, 200L, "ACTIVE", "S4", 8));
        when(projectMapper.selectByIdForUpdate(10L))
                .thenReturn(project(10L, 10L, 7L, 100L, "ACTIVE", "S4", 3));
        when(projectMapper.selectByIdForUpdate(22L))
                .thenReturn(project(22L, 11L, 7L, 200L, "ACTIVE", "S4", 8));
        assertThrows(ServiceException.class, () -> api.lockCurrentForSystem(
                new ProjectSystemQualificationLockQuery(22L, "ACTIVE", "S4")));
    }

    private static ProjectMasterDO project(Long id, Long rootId, Long tenantId, Long managerId,
                                           String lifecycle, String stage, Integer version) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(id);
        project.setRootId(rootId);
        project.setTenantId(tenantId);
        project.setManagerId(managerId);
        project.setLifecycleStatus(lifecycle);
        project.setCurrentStage(stage);
        project.setVersion(version);
        return project;
    }

    private static ProjectTreeVersionDO treeVersion(Long tenantId, Long rootId, Long version) {
        ProjectTreeVersionDO tree = new ProjectTreeVersionDO();
        tree.setTenantId(tenantId);
        tree.setRootProjectId(rootId);
        tree.setTreeVersion(version);
        tree.setStatus("ACTIVE");
        return tree;
    }

}
