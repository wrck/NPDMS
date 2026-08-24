package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressPolicyRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressPolicyItemMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressPolicyRevisionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import org.junit.jupiter.api.Test;

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

    private static ProjectProgressPolicyRevisionDO revision(Long id, Long parentId, String status, int version) {
        ProjectProgressPolicyRevisionDO value = new ProjectProgressPolicyRevisionDO();
        value.setId(id);
        value.setParentProjectId(parentId);
        value.setStatus(status);
        value.setVersion(version);
        return value;
    }
}
