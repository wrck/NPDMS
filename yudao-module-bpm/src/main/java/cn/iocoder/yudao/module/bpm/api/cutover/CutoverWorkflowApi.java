package cn.iocoder.yudao.module.bpm.api.cutover;

import java.util.List;

/** BPM运行与CUT冻结的业务审批路由衔接；CUT负责五项评审、资格重验和业务状态机。 */
public interface CutoverWorkflowApi {
    String DEFINITION_KEY = "PMS_CUTOVER_APPROVAL";
    String provision(Long tenantId, Long modelManagerId);
    String definition(Long tenantId);
    String start(Start command);
    void decide(Decision command);
    void reassign(Reassignment command);
    void cancel(Cancellation command);

    record RoleReference(Long roleId, String roleCode) {
        public RoleReference {
            if (roleId == null || roleId <= 0 || !List.of("probSupporter", "probRD").contains(roleCode))
                throw new IllegalArgumentException("割接角色引用无效");
        }
        public String groupKey() { return "ROLE:" + roleId; }
    }
    record Node(String code, Long userId, RoleReference role) {
        public Node(String code, Long userId) { this(code, userId, null); }
        public Node {
            if (role != null && (userId != null || !("SECOND_LINE".equals(code) && "probSupporter".equals(role.roleCode())
                    || "RND".equals(code) && "probRD".equals(role.roleCode()))))
                throw new IllegalArgumentException("割接角色节点身份不匹配");
            if (role == null && !List.of("INITIATOR", "SERVICE_MANAGER").contains(code)
                    || userId != null && userId <= 0)
                throw new IllegalArgumentException("割接角色候选身份无效");
        }
    }
    record Start(Long tenantId, Long actorId, Long projectId, Long taskId, Long approvalId, String grade, List<Node> nodes) {}
    record Decision(Long tenantId, Long actorId, String processInstanceId, String nodeCode, boolean approved, String feedback) {}
    record Reassignment(Long tenantId, Long actorId, String processInstanceId, String nodeCode, Long userId, String reason) {}
    record Cancellation(Long tenantId, Long actorId, String processInstanceId, String reason) {}
}
