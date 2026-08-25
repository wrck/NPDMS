package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressPolicyItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressPolicyRevisionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectProgressPolicyServiceTest {

    @Test
    void approvalSupersedesCurrentPolicyAndActivatesImmutableRevision() {
        ProjectProgressPolicyRevisionMapper revisionMapper = mock(ProjectProgressPolicyRevisionMapper.class);
        OperationAuditApi auditService = mock(OperationAuditApi.class);
        ProjectProgressMetrics metrics = mock(ProjectProgressMetrics.class);
        ProjectProgressPolicyService service = new ProjectProgressPolicyService(
                mock(ProjectMasterMapper.class), mock(ProjectTreeVersionMapper.class), revisionMapper,
                mock(ProjectProgressPolicyItemMapper.class), mock(ProjectTreeScopeService.class),
                mock(BpmProcessInstanceApi.class), new ProjectProgressProperties(), auditService, metrics);
        ProjectProgressPolicyRevisionDO approved = revision(22L, 7L, "APPROVING", 2);
        approved.setTenantId(0L);
        ProjectProgressPolicyRevisionDO previous = revision(21L, 7L, "ACTIVE", 3);
        when(revisionMapper.selectByProcessInstanceIdForUpdate("process-1")).thenReturn(approved);
        when(revisionMapper.selectActiveByParentForUpdate(7L)).thenReturn(previous);

        service.onApprovalResult("process-1", 2, null);

        assertEquals("SUPERSEDED", previous.getStatus());
        assertNotNull(previous.getEffectiveTo());
        assertEquals("ACTIVE", approved.getStatus());
        assertEquals(21L, approved.getSupersedesRevisionId());
        assertNotNull(approved.getEffectiveFrom());
        assertNotNull(approved.getApprovedAt());
        verify(revisionMapper).updateById(previous);
        verify(revisionMapper).updateById(approved);
        verify(metrics).approvalCallback("approved");
        verify(auditService).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void policyListingRequiresExplicitManageScope() {
        ProjectMasterMapper projectMapper = mock(ProjectMasterMapper.class);
        ProjectTreeVersionMapper versionMapper = mock(ProjectTreeVersionMapper.class);
        ProjectProgressPolicyRevisionMapper revisionMapper = mock(ProjectProgressPolicyRevisionMapper.class);
        ProjectTreeScopeService scopeService = mock(ProjectTreeScopeService.class);
        ProjectProgressPolicyService service = new ProjectProgressPolicyService(
                projectMapper, versionMapper, revisionMapper, mock(ProjectProgressPolicyItemMapper.class),
                scopeService, mock(BpmProcessInstanceApi.class), new ProjectProgressProperties(),
                mock(OperationAuditApi.class), mock(ProjectProgressMetrics.class));
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(7L);
        project.setRootId(7L);
        project.setTenantId(1L);
        when(projectMapper.selectById(7L)).thenReturn(project);
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(4L);
        when(versionMapper.selectLatestActive(7L)).thenReturn(version);

        service.listByParent(7L, new ProjectProgressPolicyService.Actor(1L, 9L, "corr-1"));

        verify(scopeService).assertFullAccess(
                new ProjectScopeQuery(1L, 9L, 7L, "PROJECT_MANAGE", 4L));
    }

    @Test
    void approvalProducerMustWriteStandardProjectIdVariable() {
        ProjectMasterMapper projectMapper = mock(ProjectMasterMapper.class);
        ProjectTreeVersionMapper versionMapper = mock(ProjectTreeVersionMapper.class);
        ProjectProgressPolicyRevisionMapper revisionMapper = mock(ProjectProgressPolicyRevisionMapper.class);
        ProjectTreeScopeService scopeService = mock(ProjectTreeScopeService.class);
        BpmProcessInstanceApi processInstanceApi = mock(BpmProcessInstanceApi.class);
        ProjectProgressProperties properties = new ProjectProgressProperties();
        properties.setProcessDefinitionKey("project-progress-policy");
        ProjectProgressPolicyService service = new ProjectProgressPolicyService(
                projectMapper, versionMapper, revisionMapper, mock(ProjectProgressPolicyItemMapper.class),
                scopeService, processInstanceApi, properties, mock(OperationAuditApi.class),
                mock(ProjectProgressMetrics.class));
        ProjectProgressPolicyRevisionDO draft = revision(22L, 7L, "DRAFT", 3);
        draft.setTenantId(1L);
        draft.setRevisionNo(2);
        when(revisionMapper.selectByIdForUpdate(22L)).thenReturn(draft);
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(7L);
        project.setRootId(7L);
        project.setTenantId(1L);
        when(projectMapper.selectByIdForUpdate(7L)).thenReturn(project);
        ProjectTreeVersionDO treeVersion = new ProjectTreeVersionDO();
        treeVersion.setTreeVersion(4L);
        when(versionMapper.selectLatestActive(7L)).thenReturn(treeVersion);
        when(processInstanceApi.createProcessInstance(any(), any())).thenReturn("process-1");

        service.submitForApproval(22L, 3,
                new ProjectProgressPolicyService.Actor(1L, 9L, "corr-1"));

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> requestCaptor =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(any(), requestCaptor.capture());
        assertEquals(7L, requestCaptor.getValue().getVariables().get("projectId"));
        assertEquals(7L, requestCaptor.getValue().getVariables().get("parentProjectId"));
        assertEquals("22", requestCaptor.getValue().getBusinessKey());
    }

    private static ProjectProgressPolicyRevisionDO revision(Long id, Long parentId, String status, int version) {
        ProjectProgressPolicyRevisionDO value = new ProjectProgressPolicyRevisionDO();
        value.setId(id);
        value.setParentProjectId(parentId);
        value.setStatus(status);
        value.setVersion(version);
        return value;
    }
}
