package cn.iocoder.yudao.module.pms.cutover.api.approval;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.CutoverApprovalRoleCandidatePort;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CutoverApprovalReadinessAccessApiImplTest {
    final CutoverTaskMapper tasks = mock(CutoverTaskMapper.class);
    final CutoverApprovalInstanceMapper instances = mock(CutoverApprovalInstanceMapper.class);
    final CutoverApprovalNodeMapper nodes = mock(CutoverApprovalNodeMapper.class);
    final CutoverFrozenDeviceMapper devices = mock(CutoverFrozenDeviceMapper.class);
    final CutoverApprovalRoleCandidatePort roles = mock(CutoverApprovalRoleCandidatePort.class);
    final CutoverApprovalReadinessAccessApiImpl api = new CutoverApprovalReadinessAccessApiImpl(tasks, instances, nodes, devices, roles);
    final CutoverApprovalReadinessAccessApi.Query query = new CutoverApprovalReadinessAccessApi.Query(7L, 10L, 2L, 40L, 3L, 4, 20L, 100L, 0L, List.of(30L));
    final CutoverTaskDO task = new CutoverTaskDO();
    final CutoverApprovalInstanceDO root = new CutoverApprovalInstanceDO();
    final CutoverApprovalNodeDO node = new CutoverApprovalNodeDO();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(13L).setTenantId(7L), new MockHttpServletRequest());
        task.setId(10L); task.setTenantId(7L); task.setVersion(2L); task.setProjectId(20L);
        task.setIntakeSourceType("PROJECT_EVENT"); task.setCurrentStage("P5"); task.setTaskStatus("APPROVING");
        task.setReadinessDecisionCode("READY"); task.setImplementationReadinessSnapshotId(100L); task.setImplementationReadinessSnapshotVersion(0L);
        root.setId(40L); root.setTenantId(7L); root.setTaskId(10L); root.setProjectId(20L); root.setVersion(3L);
        root.setCurrentNodeNo(4); root.setStatusCode("PENDING");
        node.setTenantId(7L); node.setApprovalInstanceId(40L); node.setNodeNo(4); node.setNodeCode("RND");
        node.setStatusCode("PENDING"); node.setApprovalModeCode("ROLE"); node.setCandidateRoleId(50L);
        node.setCandidateRoleCode("probRD"); node.setCandidateGroupKey("ROLE:50");
        when(tasks.selectForUpdate(any())).thenReturn(task);
        when(instances.selectCurrentByTask(any())).thenReturn(root); when(instances.selectByIdForUpdate(any())).thenReturn(root);
        when(nodes.selectByInstanceAndNodeForUpdate(any())).thenReturn(node);
        var device = new CutoverTaskDeviceScopeDO(); device.setDeviceId(30L);
        when(devices.byTask(any())).thenReturn(List.of(device));
        when(roles.lockAndInspectActor(any())).thenReturn(List.of(new CutoverApprovalRoleCandidatePort.ActorMembership(
                new CutoverApprovalRoleCandidatePort.RoleReference(7L, 50L, "probRD"), new CutoverApprovalRoleCandidatePort.Candidate(13L, 50L, 1L, 1L))));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void exactCurrentQualifiedRoleCanAuthorizeInternalRevalidation() { assertDoesNotThrow(() -> api.requireCurrent(query)); }
    @Test void lostRoleIsRejected() { when(roles.lockAndInspectActor(any())).thenReturn(List.of()); reject(); }
    @Test void pausedApprovalIsRejected() { root.setHoldReasonCode("APPROVER_UNAVAILABLE"); reject(); }
    @Test void replacedApprovalIsRejected() { root.setReplacementApprovalInstanceId(41L); reject(); }
    @Test void completedNodeIsRejected() { node.setStatusCode("APPROVED"); reject(); }
    @Test void wrongCurrentNodeIsRejected() { root.setCurrentNodeNo(3); reject(); }
    @Test void changedTaskVersionIsRejected() { task.setVersion(3L); reject(); }
    @Test void changedApprovalVersionIsRejected() { root.setVersion(4L); reject(); }
    @Test void changedProjectOrSnapshotIsRejected() { task.setProjectId(21L); reject(); task.setProjectId(20L); task.setImplementationReadinessSnapshotId(101L); reject(); }
    @Test void differentDeviceScopeIsRejected() { when(devices.byTask(any())).thenReturn(List.of()); reject(); }
    @Test void crossTenantIsRejectedBeforeOwnerReads() { TenantContextHolder.setTenantId(8L); reject(); verifyNoInteractions(tasks, instances, nodes, devices, roles); }
    void reject() { assertThrows(SecurityException.class, () -> api.requireCurrent(query)); }
}
