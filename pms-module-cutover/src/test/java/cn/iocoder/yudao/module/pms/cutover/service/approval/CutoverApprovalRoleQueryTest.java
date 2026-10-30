package cn.iocoder.yudao.module.pms.cutover.service.approval;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.task.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.approval.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.service.approval.context.CutoverApprovalContext;
import cn.iocoder.yudao.module.pms.cutover.service.approval.domain.*;
import cn.iocoder.yudao.module.pms.cutover.service.approval.port.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CutoverApprovalRoleQueryTest {
    @ParameterizedTest
    @ValueSource(strings = {"FUTURE_SECOND_LINE", "FUTURE_RND", "PAST_ROLE", "APPROVED", "REJECTED", "CANCELLED", "HOLD", "REPLACED", "CURRENT_NODE_MISMATCH"})
    void expiredOrNoncurrentRoleAccessStopsBeforeFrozenSourceDecode(String scenario) {
        var instances = mock(CutoverApprovalInstanceMapper.class);
        var nodes = mock(CutoverApprovalNodeMapper.class);
        var reviews = mock(CutoverApprovalReviewItemMapper.class);
        var tasks = mock(CutoverTaskMapper.class);
        var managers = mock(ProjectCutoverServiceManagerPort.class);
        var roles = mock(CutoverApprovalRoleCandidatePort.class);
        var scopes = mock(CutoverApprovalProjectScopePort.class);
        var codec = mock(CutoverApprovalSourceSnapshotCodec.class);
        var service = new CutoverApprovalQueryService(instances, nodes, reviews, tasks, managers, roles, scopes, codec);
        var root = new CutoverApprovalInstanceDO();
        root.setId(100L); root.setTenantId(1L); root.setTaskId(10L); root.setProjectId(20L);
        root.setStatusCode("PENDING"); root.setCurrentNodeNo(3); root.setInitiatorUserId(8L);
        root.setSourceSnapshot("must-not-decode");
        var task = new CutoverTaskDO(); task.setId(10L); task.setTenantId(1L); task.setProjectId(20L);
        var roleNode = new CutoverApprovalNodeDO();
        roleNode.setId(103L); roleNode.setTenantId(1L); roleNode.setApprovalInstanceId(100L);
        roleNode.setNodeNo(3); roleNode.setNodeCode("FUTURE_RND".equals(scenario) ? "RND" : "SECOND_LINE");
        roleNode.setStatusCode("PENDING");
        String roleCode = "RND".equals(roleNode.getNodeCode()) ? "probRD" : "probSupporter";
        CutoverApprovalNodeFacts.apply(roleNode, CutoverApprovalNodeFacts.role(
                new CutoverApprovalRoleCandidatePort.RoleReference(1L, 900L, roleCode)));
        var initiator = new CutoverApprovalNodeDO();
        initiator.setId(101L); initiator.setTenantId(1L); initiator.setApprovalInstanceId(100L);
        initiator.setNodeNo(1); initiator.setNodeCode("INITIATOR"); initiator.setStatusCode("PENDING");
        initiator.setCurrentApproverUserId(8L);
        if (scenario.startsWith("FUTURE")) root.setCurrentNodeNo(1);
        else if ("PAST_ROLE".equals(scenario)) { roleNode.setStatusCode("APPROVED"); root.setCurrentNodeNo(4); }
        else if (List.of("APPROVED", "REJECTED", "CANCELLED").contains(scenario)) root.setStatusCode(scenario);
        else if ("HOLD".equals(scenario)) root.setHoldReasonCode("ROLE_UNAVAILABLE");
        else if ("REPLACED".equals(scenario)) root.setReplacementApprovalInstanceId(200L);
        else if ("CURRENT_NODE_MISMATCH".equals(scenario)) root.setCurrentNodeNo(4);
        when(instances.selectCurrentByTask(any())).thenReturn(root);
        when(tasks.selectById(10L)).thenReturn(task);
        when(nodes.all(any())).thenReturn(List.of(initiator, roleNode));
        when(scopes.inspect(any())).thenReturn(new CutoverApprovalProjectScopePort.ProjectScopeFact(
                1L, 20L, 33L, "ACTION_VIEW", false, 7L));

        var failure = assertThrows(CutoverApprovalApplicationException.class,
                () -> service.detail(new CutoverApprovalContext(1L, 33L, "expired-role-access", true, false, false), 10L));
        assertEquals(CutoverApprovalApplicationException.Code.STATE_CONFLICT, failure.code());
        verifyNoInteractions(codec, reviews, managers, roles);
    }
}
