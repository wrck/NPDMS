package cn.iocoder.yudao.module.pms.project.service.projectsplit;

import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeSliceDTO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectCompanyDepartmentRelationDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitItemDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitRequestDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectsplit.ProjectSplitScopeDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectCompanyDepartmentRelationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitRequestMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectsplit.ProjectSplitScopeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectsplit.ProjectSplitRules;
import cn.iocoder.yudao.module.pms.project.service.platform.ProjectOperationAuditService;
import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitDraftCommand;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectSplitDraftServiceTest {
    @Mock ProjectSplitRequestMapper requestMapper;
    @Mock ProjectSplitItemMapper itemMapper;
    @Mock ProjectSplitScopeMapper scopeMapper;
    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectCompanyDepartmentRelationMapper organizationMapper;
    @Mock ProjectTreeVersionMapper treeVersionMapper;
    @Mock DeliveryScopeApi deliveryScopeApi;
    @Mock OrganizationScopeApi organizationScopeApi;
    @Mock ProjectOperationAuditService auditService;

    private ProjectSplitDraftService service;

    @BeforeEach
    void setUp() {
        service = new ProjectSplitDraftService(requestMapper, itemMapper, scopeMapper, projectMapper,
                organizationMapper, treeVersionMapper, deliveryScopeApi, organizationScopeApi,
                new ProjectSplitRules(), auditService);
    }

    @Test
    void shouldReplaceEditableDetailsWhenUpdatingSameStableItemKey() {
        ProjectMasterDO parent = new ProjectMasterDO();
        parent.setId(100L); parent.setTenantId(1L); parent.setRootId(100L); parent.setVersion(3);
        when(projectMapper.selectById(100L)).thenReturn(parent);
        ProjectCompanyDepartmentRelationDO relation = new ProjectCompanyDepartmentRelationDO();
        relation.setCompanyId(1L); relation.setDepartmentId(2L);
        when(organizationMapper.selectPrimaryOrderOffice(100L)).thenReturn(relation);
        when(organizationScopeApi.hasScope(9L, 1L, 2L)).thenReturn(true);
        when(deliveryScopeApi.getAvailableSlices(100L, null)).thenReturn(List.of(
                new DeliveryScopeSliceDTO(10L, BigDecimal.TEN, "EA", 5L, "CONFIRMED")));
        ProjectTreeVersionDO tree = new ProjectTreeVersionDO(); tree.setTreeVersion(7L);
        when(treeVersionMapper.selectLatestActive(100L)).thenReturn(tree);
        ProjectSplitRequestDO existing = new ProjectSplitRequestDO();
        existing.setId(20L); existing.setTenantId(1L); existing.setParentProjectId(100L);
        existing.setStatus("DRAFT"); existing.setDraftVersion(2);
        when(requestMapper.selectById(20L)).thenReturn(existing);
        when(requestMapper.updateDraftIfMatch(20L, 2, null, 3, 5L, 7L)).thenReturn(1);
        doAnswer(invocation -> { ((ProjectSplitItemDO) invocation.getArgument(0)).setId(30L); return 1; })
                .when(itemMapper).insert(any(ProjectSplitItemDO.class));

        ProjectSplitDraftService.DraftResult result = service.saveDraft(command(),
                new ProjectSplitDraftService.Actor(1L, 9L, "corr-1"));

        assertEquals(3, result.request().getDraftVersion());
        verify(scopeMapper).physicallyDeleteByRequestId(1L, 20L);
        verify(itemMapper).physicallyDeleteByRequestId(1L, 20L);
        verify(itemMapper).insert(any(ProjectSplitItemDO.class));
        verify(scopeMapper).insert(any(ProjectSplitScopeDO.class));
    }

    @Test
    void shouldKeepPreviousScopeVersionWhenCommerceAuthorityIsUnavailable() {
        ProjectMasterDO parent = new ProjectMasterDO();
        parent.setId(100L); parent.setTenantId(1L); parent.setRootId(100L); parent.setVersion(3);
        when(projectMapper.selectById(100L)).thenReturn(parent);
        ProjectCompanyDepartmentRelationDO relation = new ProjectCompanyDepartmentRelationDO();
        relation.setCompanyId(1L); relation.setDepartmentId(2L);
        when(organizationMapper.selectPrimaryOrderOffice(100L)).thenReturn(relation);
        when(organizationScopeApi.hasScope(9L, 1L, 2L)).thenReturn(true);
        when(deliveryScopeApi.getAvailableSlices(100L, null)).thenThrow(new IllegalStateException("unavailable"));
        ProjectTreeVersionDO tree = new ProjectTreeVersionDO(); tree.setTreeVersion(7L);
        when(treeVersionMapper.selectLatestActive(100L)).thenReturn(tree);
        ProjectSplitRequestDO existing = new ProjectSplitRequestDO();
        existing.setId(20L); existing.setTenantId(1L); existing.setParentProjectId(100L);
        existing.setStatus("DRAFT"); existing.setDraftVersion(2); existing.setScopeVersion(5L);
        when(requestMapper.selectById(20L)).thenReturn(existing);
        when(requestMapper.updateDraftIfMatch(20L, 2, null, 3, 5L, 7L)).thenReturn(1);
        doAnswer(invocation -> { ((ProjectSplitItemDO) invocation.getArgument(0)).setId(30L); return 1; })
                .when(itemMapper).insert(any(ProjectSplitItemDO.class));

        ProjectSplitDraftService.DraftResult result = service.saveDraft(command(),
                new ProjectSplitDraftService.Actor(1L, 9L, "corr-2"));

        assertEquals(5L, result.request().getScopeVersion());
        verify(requestMapper).updateDraftIfMatch(20L, 2, null, 3, 5L, 7L);
    }

    private ProjectSplitDraftCommand command() {
        return new ProjectSplitDraftCommand(20L, 2, 100L, null, List.of(
                new ProjectSplitDraftCommand.Item("A", "子项目A", null, 0, null, List.of(
                        new ProjectSplitDraftCommand.Scope(10L, BigDecimal.ONE, null, List.of())))));
    }
}
