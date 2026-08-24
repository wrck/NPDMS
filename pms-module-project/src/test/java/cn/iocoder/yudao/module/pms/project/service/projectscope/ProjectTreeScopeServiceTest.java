package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTreeScopeServiceTest {
    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectMemberAssignmentMapper memberMapper;
    @Mock ProjectTreePathMapper pathMapper;
    private ProjectTreeScopeService service;

    @BeforeEach
    void setUp() {
        service = new ProjectTreeScopeService(projectMapper, memberMapper, pathMapper);
    }

    private void stubRootProjection() {
        ProjectMasterDO anchor = new ProjectMasterDO(); anchor.setId(3L); anchor.setRootId(1L);
        when(projectMapper.selectById(3L)).thenReturn(anchor);
        when(pathMapper.selectByAncestor(1L, 7L, 1L, null)).thenReturn(List.of(
                path(1L, 1L), path(1L, 2L), path(1L, 3L), path(1L, 4L)));
    }

    @Test
    void managerGetsDescendantsAndSameRootSummaryWithoutSiblingDetail() {
        stubRootProjection();
        when(memberMapper.selectActiveByUser(eq(9L), any(LocalDateTime.class)))
                .thenReturn(List.of(assignment(2L, "PROJECT_MANAGER")));
        when(pathMapper.selectByAncestors(1L, 7L, Set.of(2L)))
                .thenReturn(List.of(path(2L, 2L), path(2L, 3L)));
        when(pathMapper.selectByDescendants(1L, 7L, Set.of(2L, 3L)))
                .thenReturn(List.of(path(1L, 2L), path(2L, 2L), path(1L, 3L), path(2L, 3L)));

        var scope = service.resolve(9L, 3L, 7L);

        assertEquals(ProjectTreeScopeService.Visibility.ROOT_SUMMARY, scope.visibility(1L));
        assertEquals(ProjectTreeScopeService.Visibility.FULL, scope.visibility(2L));
        assertEquals(ProjectTreeScopeService.Visibility.FULL, scope.visibility(3L));
        assertEquals(ProjectTreeScopeService.Visibility.ROOT_SUMMARY, scope.visibility(4L));
    }

    @Test
    void ordinaryMemberGetsOnlyDirectNodeAndNecessaryPath() {
        stubRootProjection();
        when(memberMapper.selectActiveByUser(eq(9L), any(LocalDateTime.class)))
                .thenReturn(List.of(assignment(3L, "ENGINEER")));
        when(pathMapper.selectByDescendants(1L, 7L, Set.of(3L)))
                .thenReturn(List.of(path(1L, 3L), path(2L, 3L), path(3L, 3L)));

        var scope = service.resolve(9L, 3L, 7L);

        assertEquals(ProjectTreeScopeService.Visibility.PATH_PLACEHOLDER, scope.visibility(1L));
        assertEquals(ProjectTreeScopeService.Visibility.PATH_PLACEHOLDER, scope.visibility(2L));
        assertEquals(ProjectTreeScopeService.Visibility.FULL, scope.visibility(3L));
        assertEquals(ProjectTreeScopeService.Visibility.NONE, scope.visibility(4L));
    }

    @Test
    void sanitizerReturnsOnlyStableIdForPathPlaceholder() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(4L); project.setProjectName("不可泄露名称"); project.setCustomerName("敏感客户");

        var view = new ProjectTreeViewSanitizer().sanitize(
                project, ProjectTreeScopeService.Visibility.PATH_PLACEHOLDER);

        assertEquals(4L, view.projectId());
        assertNull(view.projectName());
        assertNull(view.lifecycleStatus());
        assertNull(view.currentStage());
        assertNull(view.milestoneProgress());
    }

    private ProjectMemberAssignmentDO assignment(Long projectId, String role) {
        ProjectMemberAssignmentDO value = new ProjectMemberAssignmentDO();
        value.setProjectId(projectId); value.setMemberRole(role); return value;
    }

    private ProjectTreePathDO path(Long ancestorId, Long descendantId) {
        ProjectTreePathDO value = new ProjectTreePathDO();
        value.setAncestorProjectId(ancestorId); value.setDescendantProjectId(descendantId); return value;
    }
}
