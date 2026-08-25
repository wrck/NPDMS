package cn.iocoder.yudao.module.pms.project.service.projectgovernance;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectgovernance.ProjectStageSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.ProjectStageSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query.ProjectGovernanceHistoryPageQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectGovernanceHistoryQueryServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long PROJECT_ID = 11L;
    private ProjectMasterMapper projectMapper;
    private ProjectTreeVersionMapper treeVersionMapper;
    private ProjectTreeScopeService treeScopeService;
    private ProjectStageSnapshotMapper snapshotMapper;
    private ProjectGovernanceHistoryQueryService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        projectMapper = mock(ProjectMasterMapper.class);
        treeVersionMapper = mock(ProjectTreeVersionMapper.class);
        treeScopeService = mock(ProjectTreeScopeService.class);
        snapshotMapper = mock(ProjectStageSnapshotMapper.class);
        service = new ProjectGovernanceHistoryQueryService(
                projectMapper, treeVersionMapper, treeScopeService, snapshotMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void readsOnlyAfterTenantAndViewScopeValidation() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(PROJECT_ID);
        project.setTenantId(TENANT_ID);
        project.setRootId(10L);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project);
        ProjectTreeVersionDO tree = new ProjectTreeVersionDO();
        tree.setTenantId(TENANT_ID);
        tree.setRootProjectId(10L);
        tree.setTreeVersion(5L);
        when(treeVersionMapper.selectLatestActive(10L)).thenReturn(tree);
        ProjectStageSnapshotDO snapshot = new ProjectStageSnapshotDO();
        snapshot.setOperationType("ROLLBACK");
        when(snapshotMapper.selectGovernanceHistoryPage(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult<>(List.of(snapshot), 1L));

        PageResult<ProjectStageSnapshotDO> result = service.page(query(), actor());

        assertEquals(1L, result.getTotal());
        ArgumentCaptor<ProjectScopeQuery> scope = ArgumentCaptor.forClass(ProjectScopeQuery.class);
        verify(treeScopeService).assertFullAccess(scope.capture());
        assertEquals(ACTION_VIEW, scope.getValue().actionCode());
        assertEquals(PROJECT_ID, scope.getValue().anchorProjectId());
        verify(snapshotMapper).selectGovernanceHistoryPage(query());
    }

    @Test
    void crossTenantProjectFailsWithoutReadingHistory() {
        ProjectMasterDO foreign = new ProjectMasterDO();
        foreign.setId(PROJECT_ID);
        foreign.setTenantId(8L);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(foreign);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.page(query(), actor()));

        assertEquals(PROJECT_TREE_SCOPE_FORBIDDEN.getCode(), error.getCode());
        verify(snapshotMapper, never()).selectGovernanceHistoryPage(org.mockito.ArgumentMatchers.any());
    }

    private ProjectGovernanceHistoryPageQuery query() {
        PageParam page = new PageParam();
        page.setPageNo(1);
        page.setPageSize(20);
        return new ProjectGovernanceHistoryPageQuery(TENANT_ID, PROJECT_ID, page);
    }

    private ProjectGovernanceHistoryQueryService.Actor actor() {
        return new ProjectGovernanceHistoryQueryService.Actor(TENANT_ID, 9L);
    }
}
