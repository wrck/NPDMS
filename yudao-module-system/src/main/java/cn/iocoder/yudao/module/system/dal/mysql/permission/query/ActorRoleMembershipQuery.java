package cn.iocoder.yudao.module.system.dal.mysql.permission.query;

/** 当前操作者的有效角色成员资格；锁查询只用于实际办理事务。 */
public record ActorRoleMembershipQuery(Long tenantId, Long actorUserId, boolean locking) {}
