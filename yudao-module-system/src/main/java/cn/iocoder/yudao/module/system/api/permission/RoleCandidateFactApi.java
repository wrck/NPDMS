package cn.iocoder.yudao.module.system.api.permission;

import java.util.List;

/** 业务流程消费实际系统角色成员，不把超级管理员身份视作业务审批职责。 */
public interface RoleCandidateFactApi {
    List<Candidate> inspect(Query query);
    /** 必须在业务写事务中调用；锁定当前启用的角色、用户及成员关系。 */
    List<Candidate> lockAndInspect(Query query);
    /** 返回角色引用，不解析或冻结整组候选人员。 */
    RoleReference inspectRole(Query query);
    /** 返回操作者当前启用的角色成员资格，用于原生候选组待办。 */
    List<ActorMembership> inspectActor(ActorQuery query);
    /** 决定事务只锁定实际办理人的角色及成员关系，不锁定整组成员。 */
    List<ActorMembership> lockAndInspectActor(ActorQuery query);
    record Query(Long tenantId, String roleCode) {}
    record ActorQuery(Long tenantId, Long actorUserId) {}
    record RoleReference(Long roleId, String roleCode) {}
    record ActorMembership(Long userId, Long roleId, String roleCode,
                           Long membershipWatermark, Long userWatermark) {}
    /** 水位取实际行的最后更新时间；仅描述当前有效资格，不代替业务历史。 */
    record Candidate(Long userId, Long roleId, Long membershipWatermark, Long userWatermark) {}
}
